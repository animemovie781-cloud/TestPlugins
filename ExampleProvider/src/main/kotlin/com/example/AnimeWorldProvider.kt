package com.example

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.regex.Pattern

class AnimeWorldProvider : MainAPI() {
    override var mainUrl = "https://watchanimeworld.top"
    override var name = "AnimeWorld"
    override val hasMainPage = true
    override var lang = "en"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(mainUrl).document
        
        val items = doc.select("article.post.dfx.fcl.movies").map { item ->
            val title = item.select("h2.entry-title").text()
            val url = item.select("a.lnk-blk").attr("href")
            val image = fixUrl(item.select("figure > img").attr("src"))
            
            when {
                url.contains("/movies/") -> newMovieSearchResponse(title, url) {
                    posterUrl = image
                }
                else -> newAnimeSearchResponse(title, url) {
                    posterUrl = image
                }
            }
        }
        
        return newHomePageResponse(listOf(HomePageList("Latest Updates", items)))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=${URLEncoder.encode(query, "UTF-8")}").document
        
        return doc.select("article.post.dfx.fcl.movies").map { item ->
            val title = item.select("h2.entry-title").text()
            val url = item.select("a.lnk-blk").attr("href")
            val image = fixUrl(item.select("figure > img").attr("src"))
            
            when {
                url.contains("/movies/") -> newMovieSearchResponse(title, url) {
                    posterUrl = image
                }
                else -> newAnimeSearchResponse(title, url) {
                    posterUrl = image
                }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        
        val title = doc.select("h1.entry-title").text()
        val poster = fixUrl(doc.select("article.post.single > div > div.text-align-center img").attr("src"))
        val desc = doc.select("div.description p").text()
        
        return when {
            url.contains("/movies/") -> {
                newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = poster
                    this.plot = desc
                }
            }
            url.contains("/series/") -> {
                val postId = extractPostId(doc)
                val episodes = mutableListOf<Episode>()
                
                if (postId != null) {
                    val seasons = doc.select(".aa-drp a[data-season]").map { 
                        it.attr("data-season").toIntOrNull() ?: 1 
                    }.distinct().sorted()
                    
                    for (season in seasons) {
                        try {
                            val seasonDoc = app.get("$mainUrl/wp-admin/admin-ajax.php?action=action_select_season&post=$postId&season=$season").document
                            
                            seasonDoc.select("article.post.dfx.fcl.episodes").forEach { ep ->
                                val epTitle = ep.select("h2.entry-title").text()
                                val epLink = ep.select("a.lnk-blk").attr("href")
                                val epNum = ep.select(".num-epi").text()
                                
                                episodes.add(
                                    newEpisode(
                                        data = epLink,
                                        name = if (epTitle.isNotEmpty()) "$epTitle $epNum" else epNum
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            // Skip failed seasons
                        }
                    }
                }
                
                newAnimeLoadResponse(title, url, TvType.Anime) {
                    this.posterUrl = poster
                    this.plot = desc
                    this.episodes = mutableMapOf(DubStatus.Dubbed to episodes)
                }
            }
            url.contains("/episode/") -> {
                newAnimeLoadResponse(title, url, TvType.Anime) {
                    this.posterUrl = poster
                    this.plot = desc
                }
            }
            else -> {
                newAnimeLoadResponse(title, url, TvType.Anime) {
                    this.posterUrl = poster
                    this.plot = desc
                }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        
        doc.select("div.video.aa-tb.hdd > iframe").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotEmpty() && src.contains("play.zephyrix.top")) {
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "Server 1",
                        url = src,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.P720.value
                    }
                )
            }
        }
        
        doc.select("div.video.aa-tb.hdd > iframe[data-src]").forEach { iframe ->
            val dataSrc = iframe.attr("data-src")
            if (dataSrc.contains("player1.php?data=")) {
                val base64Data = dataSrc.substringAfter("data=")
                try {
                    val json = String(Base64.decode(base64Data, Base64.DEFAULT))
                    val languages = mutableListOf<String>()
                    val links = mutableListOf<String>()
                    
                    val langPattern = Pattern.compile("\"language\"\\s*:\\s*\"([^\"]+)\"")
                    val linkPattern = Pattern.compile("\"link\"\\s*:\\s*\"([^\"]+)\"")
                    
                    val langMatcher = langPattern.matcher(json)
                    val linkMatcher = linkPattern.matcher(json)
                    
                    while (langMatcher.find()) languages.add(langMatcher.group(1))
                    while (linkMatcher.find()) links.add(linkMatcher.group(1))
                    
                    languages.zip(links).forEach { (lang, link) ->
                        callback.invoke(
                            newExtractorLink(
                                source = name,
                                name = "Abyss $lang",
                                url = link,
                                type = ExtractorLinkType.VIDEO
                            ) {
                                this.referer = mainUrl
                                this.quality = Qualities.P720.value
                            }
                        )
                    }
                } catch (e: Exception) {
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = "Server 2",
                            url = dataSrc,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.referer = mainUrl
                            this.quality = Qualities.P720.value
                        }
                    )
                }
            }
        }
        
        return true
    }
    
    private fun extractPostId(doc: Document): Int? {
        val bodyClass = doc.select("body").attr("class")
        return bodyClass.substringAfter("postid-").substringBefore(" ").toIntOrNull()
    }
}
