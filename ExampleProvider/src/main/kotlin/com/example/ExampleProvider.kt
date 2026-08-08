package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class HindiAnimeZone : MainAPI() {

    override var mainUrl = "https://hindianimezone.com"
    override var name = "HindiAnimeZone"
    override val hasMainPage = true
    override var lang = "hi"

    // 🔥 HOMEPAGE
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(mainUrl).document

        val list = doc.select(".td-module-title a").map {
            val title = it.text()
            val link = it.attr("href")

            newAnimeSearchResponse(title, link) {
                this.posterUrl = it.parent()?.parent()?.select("img")?.attr("src")
            }
        }

        return newHomePageResponse(
            listOf(HomePageList("Anime", list))
        )
    }

    // 🔥 DETAIL PAGE
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.select("h1.entry-title").text()
        val poster = doc.select(".td-post-featured-image img").attr("src")
        val desc = doc.select(".td-post-content").text()

        // Episode = same page (WordPress post)
        val episode = Episode(
            name = title,
            data = url
        )

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot = desc
            this.episodes = listOf(episode)
        }
    }

    // 🔥 VIDEO SCRAPER (MOST IMPORTANT)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val doc = app.get(data).document

        // STEP 1: find player iframe page (tumhari file se)
        val script = doc.html()

        val playerUrl = Regex("""https:\/\/hindianimezone\.p2pplay\.online\/#\w+""")
            .find(script)?.value

        if (playerUrl != null) {

            callback.invoke(
                ExtractorLink(
                    source = "HindiAnimeZone",
                    name = "Server 1",
                    url = playerUrl,
                    referer = data,
                    quality = Qualities.P720.value,
                    isM3u8 = false
                )
            )
        }

        return true
    }
}
