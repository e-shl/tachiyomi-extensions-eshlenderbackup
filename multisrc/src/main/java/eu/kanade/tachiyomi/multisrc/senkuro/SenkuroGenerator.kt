package eu.kanade.tachiyomi.multisrc.senkuro

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class SenkuroGenerator : ThemeSourceGenerator {

    override val themePkg = "senkuro"

    override val themeClass = "Senkuro"

    override val baseVersionCode = 1

    override val sources = listOf(
        SingleLang("Senkuro", "https://api.senkuro.com/graphql", "ru", overrideVersionCode = 0),
        SingleLang("Senkognito", "https://api.senkuro.com/graphql", "ru", overrideVersionCode = 0),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SenkuroGenerator().createAll()
        }
    }
}
