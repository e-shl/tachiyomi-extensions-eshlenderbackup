package eu.kanade.tachiyomi.multisrc.senkuro

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
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
import org.jsoup.nodes.Document
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Collections.list
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
    private inline fun <reified T : Any> T.toJsonRequestBody(): RequestBody =
        json.encodeToString(this)
            .toRequestBody(JSON_MEDIA_TYPE)

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        val requestBody = GraphQL(
            SEARCH_QUERY,
            SearchVariables(offset = offsetCount * (page - 1)),
        ).toJsonRequestBody()

        if (page==1) {
            fetchTachiyomiSearchFilters()
        }

        return POST(baseUrl, headers, requestBody)
    }
    override fun popularMangaParse(response: Response) = searchMangaParse(response)
    // Latest
    override fun latestUpdatesRequest(page: Int): Request = throw NotImplementedError("Unused")

    override fun latestUpdatesParse(response: Response): MangasPage = throw NotImplementedError("Unused")

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val requestBody = GraphQL(
            SEARCH_QUERY,
            SearchVariables(query = query,offset = offsetCount * (page - 1)),
        ).toJsonRequestBody()

        return POST(baseUrl, headers, requestBody)
    }
    override fun searchMangaParse(response: Response): MangasPage {
        val page = json.decodeFromString<PageWrapperDto<MangaTachiyomiSearchDto<MangaTachiyomiInfoDto>>>(response.body.string())
        val mangasList = page.data.mangaTachiyomiSearch.mangas.map {
            it.toSManga()
        }
        return MangasPage(mangasList, mangasList.isNotEmpty())
    }

    // Details
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
            url = "$id,,$slug" //mangaId[0],,mangaSlug[1]
            thumbnail_url = cover?.original?.url
            description = localizations?.find { it.lang == "RU" }?.description
            status = parseStatus(o.status)
        }
    }

    override fun mangaDetailsRequest(manga: SManga): Request     {
        val requestBody = GraphQL(
            DETAILS_QUERY,
            FetchDetailsVariables(mangaId = manga.url.split(",,")[0]),
        ).toJsonRequestBody()

        return POST(baseUrl, headers, requestBody)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val series = json.decodeFromString<PageWrapperDto<SubInfoDto>>(response.body.string())
        return series.data.mangaTachiyomiInfo.toSManga()
    }

    override fun getMangaUrl(manga: SManga) = baseUrl.replace("api.", "").replace("/graphql", "/manga/") + manga.url.split(",,")[1]

    // Chapters
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
        val teamsList = chaptersList.data.mangaTachiyomiChapters.teams
        return chaptersList.data.mangaTachiyomiChapters.chapters.map { chapter ->
            SChapter.create().apply {
                chapter_number = chapter.number.toFloatOrNull()  ?: -2F
                name = "${chapter.volume}. Глава ${chapter.number} " + (chapter.name ?: "")
                url = "${manga.url},,${chapter.id},,${chapter.slug}" //mangaId[0],,mangaSlug[1],,chapterId[2],,chapterSlug[3]
                date_upload = parseDate(chapter.updatedAt)
                scanlator = teamsList.find{it.id == chapter.teamIds[0]}!!.name
            }
        }
    }
    override fun chapterListRequest(manga: SManga): Request {
        val requestBody = GraphQL(
            CHAPTERS_QUERY,
            FetchDetailsVariables(mangaId = manga.url.split(",,")[0]),
        ).toJsonRequestBody()

        return POST(baseUrl, headers, requestBody)
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request {
        val mangaChapterId=chapter.url.split(",,")
        val requestBody = GraphQL(
            CHAPTERS_PAGES_QUERY,
            FetchChapterPagesVariables(mangaId = mangaChapterId[0],chapterId = mangaChapterId[2]),
        ).toJsonRequestBody()

        return POST(baseUrl, headers, requestBody)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val mangaChapterSlug = chapter.url.split(",,")
        return baseUrl.replace("api.", "").replace("/graphql", "/manga/") + mangaChapterSlug[1] + "/chapters/" + mangaChapterSlug[3]
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

    // Filters
    private fun fetchTachiyomiSearchFilters() {
        val responseBody = client.newCall(POST(baseUrl,headers,GraphQL(
            FILTERS_QUERY,
            SearchVariables(),
        ).toJsonRequestBody())).execute().body.string()

        val filterDto = json.decodeFromString<PageWrapperDto<MangaTachiyomiSearchFilters>>(responseBody).data.mangaTachiyomiSearchFilters

        genresList = filterDto.genres.map { genre->
            Genre(genre.titles.find { it.lang == "RU" }!!.content, genre.slug)
        }

        tagsList = filterDto.tags.map { tag->
            Genre(tag.titles.find { it.lang == "RU" }!!.content, tag.slug)
        }
    }

    override fun getFilterList() = FilterList(
        GenreFilter(getGenreList()),
        TagFilter(getTagList()),
    )

    private class Genre(name: String, val id: String) : Filter.CheckBox(name)
    private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Жанры", genres)
    private class TagFilter(tags: List<Genre>) : Filter.Group<Genre>("Тэги", tags)

    private var genresList: List<Genre>? = null
    private var tagsList: List<Genre>? = null
    private fun getGenreList(): List<Genre> {
        return genresList ?: listOf(Genre("Нажмите сброс, чтобы загрузить Жанры.", ""))
    }
    private fun getTagList(): List<Genre> {
        return tagsList ?: listOf(Genre("Нажмите сброс, чтобы загрузить Тэги.", ""))
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
    }


    private val json: Json by injectLazy()

}
