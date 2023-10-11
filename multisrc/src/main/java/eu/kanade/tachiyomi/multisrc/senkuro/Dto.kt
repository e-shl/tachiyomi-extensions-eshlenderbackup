package eu.kanade.tachiyomi.multisrc.senkuro

import kotlinx.serialization.Serializable
@Serializable
data class PageWrapperDto<T>(
    val data: T,
)

// Library Container
@Serializable
data class mangaTachiyomiSearchDto<T>(
    val mangaTachiyomiSearch: mangasDto<T>,
){
    @Serializable
    data class mangasDto<T>(
        val mangas: List<T>,
    )
}


// Manga Details
@Serializable
data class SubInfoDto(
    val mangaTachiyomiInfo: mangaTachiyomiInfoDto,
)

@Serializable
data class mangaTachiyomiInfoDto(
    val id: String,
    val slug: String,
    val cover: SubImgDto? = null,
    val titles: List<TitleDto>,
) {
    @Serializable
    data class SubImgDto(
        val original: ImgDto,
    ) {
        @Serializable
        data class ImgDto(
            val url: String? = null,
        )
    }

    @Serializable
    data class TitleDto(
        val lang: String,
        val content: String,
    )
}

// Chapters
@Serializable
data class mangaTachiyomiChaptersDto(
    val mangaTachiyomiChapters: ChaptersMessage,
){
    @Serializable
    data class ChaptersMessage(
        val message: String? = null,
        val chapters: List<BookDto>,
    ){
        @Serializable
        data class BookDto(
            val id: String,
            val branchId: String,
            val name: String? = null,
            val number: String,
            val volume: String,
            val updatedAt: String,
        )
    }
}

// Chapter Pages
@Serializable
data class mangaTachiyomiChapterPages(
    val mangaTachiyomiChapterPages: ChaptersPages,
){
    @Serializable
    data class ChaptersPages(
        val pages: List<UrlDto>,
    ){
        @Serializable
        data class UrlDto(
            val url: String,
        )
    }
}




