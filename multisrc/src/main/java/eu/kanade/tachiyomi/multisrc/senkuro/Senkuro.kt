package eu.kanade.tachiyomi.multisrc.senkuro

import android.annotation.TargetApi
import android.os.Build
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
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

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Tachiyomi (+https://github.com/tachiyomiorg/tachiyomi)")
        .add("Content-Type", "application/json")

    override val client: OkHttpClient =
        network.client.newBuilder()
            .rateLimit(5)
            .build()

    private val count = 30

    override fun popularMangaRequest(page: Int): Request {
        val payload = GraphQL(
            SEARCH_QUERY,
            SearchVariables(),
        )

        val requestBody = payload.toJsonRequestBody()

        return POST(baseUrl, headers, requestBody)
    }

    private inline fun <reified T : Any> T.toJsonRequestBody(): RequestBody =
        json.encodeToString(this)
            .toRequestBody(JSON_MEDIA_TYPE)

    override fun popularMangaParse(response: Response) = searchMangaParse(response)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/projects/updates?only_bookmarks=false&size=$count&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaParse(response: Response): MangasPage {
        val page = json.decodeFromString<PageWrapperDto<mangaTachiyomiSearchDto<mangaTachiyomiInfoDto>>>(response.body.string())
        val mangasList = page.data.mangaTachiyomiSearch.mangas.map {
            it.toSearchManga()
        }
        return MangasPage(mangasList, mangasList.isNotEmpty())
    }

    private fun mangaTachiyomiInfoDto.toSearchManga(): SManga {
        return eu.kanade.tachiyomi.source.model.SManga.create().apply {
            title = titles[0].content
            url = id
            thumbnail_url = cover?.original?.url
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return POST(
            "https://neo.newmanga.org/catalogue",
            body = """""".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()),
            headers = headers,
        )
    }

    private fun mangaTachiyomiInfoDto.toSManga(): SManga {
        return eu.kanade.tachiyomi.source.model.SManga.create().apply {
            title = titles[0].content
            url = id
            thumbnail_url = cover?.original?.url
        }
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val payload = GraphQL(
            DETAILS_QUERY,
            FetchDetailsVariables(manga.url),
        )

        val requestBody = payload.toJsonRequestBody()

        return POST(baseUrl, headers, requestBody)
    }

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
    override fun chapterListParse(response: Response): List<SChapter> {
        var chaptersList = json.decodeFromString<PageWrapperDto<mangaTachiyomiChaptersDto>>(response.body.string())
        return chaptersList.data.mangaTachiyomiChapters.chapters.map { chapter ->
            SChapter.create().apply {
                chapter_number = chapter.number.toFloatOrNull()  ?: -2F
                name = "${chapter.volume}. Глава ${chapter.number} " + (chapter.name ?: "")
                url = chapter.id
                date_upload = parseDate(chapter.updatedAt)
            }
        }
    }
    override fun chapterListRequest(manga: SManga): Request {
        val payload = GraphQL(
            CHAPTERS_QUERY,
            FetchDetailsVariables(manga.url),
        )

        val requestBody = payload.toJsonRequestBody()

        return POST(baseUrl, headers, requestBody)
    }

    @TargetApi(Build.VERSION_CODES.N)
    override fun pageListRequest(chapter: SChapter): Request {
        return GET(baseUrl + "/chapters/${chapter.url.substringAfterLast("/")}/pages", headers)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return baseUrl + chapter.url
    }

    override fun pageListParse(response: Response): List<Page> = throw Exception("Not used")

    override fun fetchImageUrl(page: Page): Observable<String> {
        return Observable.just(page.url)
    }

    override fun imageUrlRequest(page: Page): Request = throw NotImplementedError("Unused")

    override fun imageUrlParse(response: Response): String = throw NotImplementedError("Unused")

    override fun imageRequest(page: Page): Request {
        val refererHeaders = headersBuilder().build()
        return GET(page.imageUrl!!, refererHeaders)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
    }

    private val json: Json by injectLazy()

}
