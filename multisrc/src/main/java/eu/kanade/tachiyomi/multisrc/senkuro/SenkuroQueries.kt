package eu.kanade.tachiyomi.multisrc.senkuro

import kotlinx.serialization.Serializable

@Serializable
data class GraphQL<T>(
    val query: String,
    val variables: T,
)

private fun buildQuery(queryAction: () -> String): String {
    return queryAction()
        .trimIndent()
        .replace("%", "$")
}

@Serializable
data class SearchVariables(
    val query: String? = null,
)

val SEARCH_QUERY: String = buildQuery {
    """
       query searchTachiyomiManga(
            %query: String,
            %type: MangaTachiyomiSearchTypeFilter,
            %status: MangaTachiyomiSearchStatusFilter,
            %translationStatus: MangaTachiyomiSearchTranslationStatusFilter,
            %genre: MangaTachiyomiSearchGenreFilter,
            %tag: MangaTachiyomiSearchTagFilter,
        ) {
            mangaTachiyomiSearch(
                 query:%query,
                 type: %type,
                 status: %status,
                 translationStatus: %translationStatus,
                 genre: %genre,
                 tag: %tag,
        ) {
            mangas {
                id
                slug
                originalName {
                    lang
                    content
                }
                titles {
                    lang
                    content
                }
                alternativeNames {
                    lang
                    content
                }
                cover {
                    original {
                        url
                    }
                }
            }
        }
    }
    """
}

@Serializable
data class FetchDetailsVariables(
    val mangaId: String? = null,
)

val DETAILS_QUERY: String = buildQuery {
    """
        query fetchTachiyomiManga(%mangaId: ID!) {
                mangaTachiyomiInfo(mangaId: %mangaId) {
                        id
                        slug
 	 	                originalName {
 	 	 	                    lang
                                content
                        }
                        titles {
                                 lang
                                 content
                        }
                        alternativeNames {
                                 lang
                                 content
                        }
                        localizations {
                                 lang
                                 description
                        }
                        type
                        status
                        formats
                        translationStatus
                        cover {
                                original {
                                       url
                                }
                        }
                }
        }
    """
}

val CHAPTERS_QUERY: String = buildQuery {
    """
        query fetchTachiyomiChapters(%mangaId: ID!) {
                mangaTachiyomiChapters(mangaId: %mangaId) {
                        message
                        chapters {
                            id
                            branchId
                            name
                            number
                            volume
                            updatedAt
                        }
                   }
            }
    """
}
