package eu.kanade.tachiyomi.multisrc.senkuro

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

abstract class Senkuro(
    override val name: String,
    final override val baseUrl: String,
    final override val lang: String,
) : HttpSource() {

    override val supportsLatest = false

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Tachiyomi (+https://github.com/tachiyomiorg/tachiyomi)")
        .add("Content-Type", "application/json")

    override val client: OkHttpClient =
        network.client.newBuilder()
            .rateLimit(5)
            .build()

    private val offsetCount = 20

    override fun popularMangaRequest(page: Int): Request {
        val payload = GraphQL(
            SEARCH_QUERY,
            SearchVariables(offset = offsetCount * (page - 1)),
        )

        val requestBody = payload.toJsonRequestBody()

        return POST(baseUrl, headers, requestBody)
    }

    private inline fun <reified T : Any> T.toJsonRequestBody(): RequestBody =
        json.encodeToString(this)
            .toRequestBody(JSON_MEDIA_TYPE)

    override fun popularMangaParse(response: Response) = searchMangaParse(response)
    override fun latestUpdatesRequest(page: Int): Request = throw NotImplementedError("Unused")

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaParse(response: Response): MangasPage {
        val page = json.decodeFromString<PageWrapperDto<MangaTachiyomiSearchDto<MangaTachiyomiInfoDto>>>(response.body.string())
        val mangasList = page.data.mangaTachiyomiSearch.mangas.map {
            it.toSManga()
        }
        return MangasPage(mangasList, mangasList.isNotEmpty())
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val payload = GraphQL(
            SEARCH_QUERY,
            SearchVariables(query = query,offset = offsetCount * (page - 1)),
        )

        val requestBody = payload.toJsonRequestBody()

        return POST(baseUrl, headers, requestBody)
    }

    private fun parseStatus(status: String?): Int {
        return when (status) {
            "FINISHED" -> SManga.COMPLETED
            "ONGOING"-> SManga.ONGOING
            "HIATUS" -> SManga.ON_HIATUS
            "ANNOUNCE" -> SManga.ONGOING
            "CANCELLED" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    private fun MangaTachiyomiInfoDto.toSManga(): SManga {
        val o = this
        return SManga.create().apply {
            title = titles.find { it.lang == "RU" }?.content ?: titles.find { it.lang == "EN" }?.content ?: titles[0].content
            url = "$id,,$slug"
            thumbnail_url = cover?.original?.url
            description = localizations?.find { it.lang == "RU" }?.description
            status = parseStatus(o.status)
        }
    }

    override fun mangaDetailsRequest(manga: SManga): Request     {
        val payload = GraphQL(
            DETAILS_QUERY,
            FetchDetailsVariables(mangaId = manga.url.split(",,")[0]),
        )

        val requestBody = payload.toJsonRequestBody()

        return POST(baseUrl, headers, requestBody)
    }

    override fun getMangaUrl(manga: SManga) = baseUrl.replace("api.", "").replace("/graphql", "/manga/") + manga.url.split(",,")[1]

    override fun mangaDetailsParse(response: Response): SManga {
        val series = json.decodeFromString<PageWrapperDto<SubInfoDto>>(response.body.string())
        return series.data.mangaTachiyomiInfo.toSManga()
    }

    private val simpleDateFormat by lazy { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S", Locale.ROOT) }

    private fun parseDate(date: String?): Long {
        date ?: return 0L
        return try {
            simpleDateFormat.parse(date)!!.time
        } catch (_: Exception) {
            Date().time
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .map { response ->
                chapterListParse(response, manga)
            }
    }
    override fun chapterListParse(response: Response) = throw UnsupportedOperationException("chapterListParse(response: Response, manga: SManga)")
    private fun chapterListParse(response: Response, manga: SManga): List<SChapter> {
        val chaptersList = json.decodeFromString<PageWrapperDto<MangaTachiyomiChaptersDto>>(response.body.string())
        return chaptersList.data.mangaTachiyomiChapters.chapters.map { chapter ->
            SChapter.create().apply {
                chapter_number = chapter.number.toFloatOrNull()  ?: -2F
                name = "${chapter.volume}. Глава ${chapter.number} " + (chapter.name ?: "")
                url = "${manga.url.split(",,")[0]},,${chapter.id}"
                date_upload = parseDate(chapter.updatedAt)
            }
        }
    }
    override fun chapterListRequest(manga: SManga): Request {
        val payload = GraphQL(
            CHAPTERS_QUERY,
            FetchDetailsVariables(mangaId = manga.url.split(",,")[0]),
        )

        val requestBody = payload.toJsonRequestBody()

        return POST(baseUrl, headers, requestBody)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val mangaChapterId=chapter.url.split(",,")
        val payload = GraphQL(
            CHAPTERS_PAGES_QUERY,
            FetchChapterPagesVariables(mangaId = mangaChapterId[0],chapterId = mangaChapterId[1]),
        )

        val requestBody = payload.toJsonRequestBody()

        return POST(baseUrl, headers, requestBody)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return baseUrl + chapter.url
    }

    override fun pageListParse(response: Response): List<Page> {
        val imageList = json.decodeFromString<PageWrapperDto<MangaTachiyomiChapterPages>>(response.body.string())
        return imageList.data.mangaTachiyomiChapterPages.pages.mapIndexed{index,page->
            Page(index, "", page.url)
        }
    }

    override fun imageUrlRequest(page: Page): Request = throw NotImplementedError("Unused")

    override fun imageUrlParse(response: Response): String = throw NotImplementedError("Unused")

    override fun fetchImageUrl(page: Page): Observable<String> {
        return Observable.just(page.url)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
    }

    private val json: Json by injectLazy()

}
