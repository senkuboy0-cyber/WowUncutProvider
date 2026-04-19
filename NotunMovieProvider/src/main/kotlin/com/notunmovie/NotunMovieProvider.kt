package com.notunmovie

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class NotunMovieProvider : MainAPI() {
    override var mainUrl = "https://notunmovie.com"
    override var name = "NotunMovie"
    override var lang = "bn"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Others)

    private val ua = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/category/bangla-movie/" to "বাংলা মুভি",
        "$mainUrl/category/bangla-web-series/" to "বাংলা ওয়েব সিরিজ",
        "$mainUrl/category/bangla-natok/" to "বাংলা নাটক",
        "$mainUrl/category/hindi-movie/" to "হিন্দি মুভি",
        "$mainUrl/category/hindi-dubbed-movie/" to "হিন্দি ডাবড মুভি",
        "$mainUrl/category/hindi-web-series/" to "হিন্দি ওয়েব সিরিজ",
        "$mainUrl/category/bangla-dubbing-movie/" to "বাংলা ডাবিং মুভি",
        "$mainUrl/" to "নতুন আপলোড",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) {
            request.data
        } else {
            request.data.trimEnd('/') + "/page/$page/"
        }
        
        val doc = app.get(url, headers = ua).document
        val items = doc.select("article.post, div.post, .item").mapNotNull { el ->
            val a = el.selectFirst("a[href]") ?: return@mapNotNull null
            val href = a.attr("abs:href").ifBlank { return@mapNotNull null }
            val title = (el.selectFirst("h2, h3, .entry-title, .post-title")?.text() ?: a.attr("title")).trim().ifBlank { return@mapNotNull null }
            val poster = el.selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            
            val isSeries = title.contains("web series", true) || 
                          title.contains("season", true) || 
                          title.contains("episode", true) ||
                          title.contains("natok", true)
            
            if (isSeries) {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) { posterUrl = poster }
            } else {
                newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = poster }
            }
        }
        return newHomePageResponse(request.name, items, page < 5)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=${java.net.URLEncoder.encode(query, "UTF-8")}", headers = ua).document
        return doc.select("article.post").mapNotNull { el ->
            val a = el.selectFirst("a[href]") ?: return@mapNotNull null
            val href = a.attr("abs:href").ifBlank { return@mapNotNull null }
            val title = (el.selectFirst("h2, h3")?.text() ?: a.attr("title")).trim().ifBlank { return@mapNotNull null }
            val poster = el.selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            
            val isSeries = title.contains("web series", true) || title.contains("natok", true)
            
            if (isSeries) {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) { posterUrl = poster }
            } else {
                newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = poster }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = ua).document
        val title = doc.selectFirst("h1.entry-title, h1")?.text()?.trim() ?: doc.title().trim()
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val description = doc.selectFirst("meta[property=og:description]")?.attr("content")
        
        // Try to find download links
        val downloadLinks = mutableListOf<String>()
        doc.select("a[href*='drive.google'], a[href*='mediafire'], a[href*='gdtot']").forEach { link ->
            val href = link.attr("href")
            if (href.isNotBlank() && !downloadLinks.contains(href)) {
                downloadLinks.add(href)
            }
        }
        
        val isSeries = title.contains("web series", true) || 
                       title.contains("natok", true) || 
                       url.contains("web-series", true) ||
                       url.contains("natok", true)
        
        return if (isSeries && downloadLinks.isNotEmpty()) {
            // For series, create episodes from download links
            val episodes = downloadLinks.mapIndexed { index, link ->
                newEpisode(link) {
                    name = "Episode ${index + 1}"
                    episode = index + 1
                    this.posterUrl = poster
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
            }
        } else if (downloadLinks.isNotEmpty()) {
            // For movies, use first download link
            newMovieLoadResponse(title, url, TvType.Movie, downloadLinks.first()) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            // No download links found, return basic response
            if (isSeries) {
                newTvSeriesLoadResponse(title, url, TvType.TvSeries, emptyList()) {
                    this.posterUrl = poster
                    this.plot = description
                }
            } else {
                newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = poster
                    this.plot = description
                }
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        if (data.isBlank()) return false
        
        // Check if it's a Google Drive link
        if (data.contains("drive.google.com")) {
            callback(newExtractorLink(name, "$name [GDrive]", data, ExtractorLinkType.VIDEO) {
                this.headers = ua + mapOf("Referer" to mainUrl)
            })
            return true
        }
        
        // Check if it's a direct video URL
        if (data.startsWith("http") && (data.contains(".mp4") || data.contains(".mkv") || data.contains(".webm"))) {
            val quality = when {
                data.contains("1080") -> Qualities.P1080.value
                data.contains("720") -> Qualities.P720.value
                data.contains("480") -> Qualities.P480.value
                else -> Qualities.Unknown.value
            }
            callback(newExtractorLink(name, "$name [HD]", data, ExtractorLinkType.VIDEO) {
                this.quality = quality
                this.headers = ua + mapOf("Referer" to mainUrl)
            })
            return true
        }
        
        // For other URLs (like redirect pages), return the URL itself
        callback(newExtractorLink(name, "$name", data, ExtractorLinkType.VIDEO) {
            this.headers = ua + mapOf("Referer" to mainUrl)
        })
        return true
    }
}