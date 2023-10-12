package eu.kanade.tachiyomi.multisrc.senkuro

import kotlinx.serialization.Serializable
@Serializable
data class PageWrapperDto<T>(
    val data: T,
)

// Library Container
@Serializable
data class MangaTachiyomiSearchDto<T>(
    val mangaTachiyomiSearch: MangasDto<T>,
){
    @Serializable
    data class MangasDto<T>(
        val mangas: List<T>,
    )
}


// Manga Details
@Serializable
data class SubInfoDto(
    val mangaTachiyomiInfo: MangaTachiyomiInfoDto,
)

@Serializable
data class MangaTachiyomiInfoDto(
    val id: String,
    val slug: String,
    val cover: SubImgDto? = null,
    val status: String?  = null,
    val titles: List<TitleDto>,
    val localizations: List<LocalizationsDto>? = null,
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

    @Serializable
    data class LocalizationsDto(
        val lang: String,
        val description: String,
    )
}

// Chapters
@Serializable
data class MangaTachiyomiChaptersDto(
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
data class MangaTachiyomiChapterPages(
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




