package com.wowuncut

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class WowUncutProvider : MainAPI() {

    override var mainUrl = "https://wowuncut.com"
    override var name = "WowUncut"
    override var lang = "hi"
    override val hasMainPage = true

    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie,
        TvType.Others,
    )

    private val ua = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/category/hindi-web-series/"           to "Hindi Web Series",
        "$mainUrl/category/bengali-web-series/"         to "Bengali Web Series",
        "$mainUrl/category/tamil-web-series/"           to "Tamil Web Series",
        "$mainUrl/category/telugu-web-series/"          to "Telugu Web Series",
        "$mainUrl/category/hindi-short-film/"           to "Hindi Short Films",
        "$mainUrl/category/unrated-web-series/"         to "Unrated Web Series",
        "$mainUrl/"                                     to "Latest",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data
                  else request.data.trimEnd('/') + "/page/$page/"
        val doc = app.get(url, headers = ua).document
        val items = doc.select("article.post, div.post-thumbnail, div.item").mapNotNull { el ->
            val a = el.selectFirst("a[href]") ?: return@mapNotNull null
            val href = a.attr("abs:href").ifBlank { return@mapNotNull null }
            val title = (el.selectFirst("h2, h3, .entry-title, .post-title")?.text()
                ?: a.attr("title")).trim().ifBlank { return@mapNotNull null }
            val poster = el.selectFirst("img")?.let {
                it.attr("data-src").ifBlank { it.attr("src") }
            }
            val isSeries = title.contains("series", true) || title.contains("episode", true) ||
                    href.contains("episode", true) || href.contains("web-series", true)
            if (isSeries)
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) { posterUrl = poster }
            else
                newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = poster }
        }
        val hasNext = doc.selectFirst("a.next, .pagination .next, a[rel=next]") != null
        return newHomePageResponse(request.name, items, hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val doc = app.get("$mainUrl/?s=$encoded", headers = ua).document
        return doc.select("article.post, div.post-thumbnail").mapNotNull { el ->
            val a = el.selectFirst("a[href]") ?: return@mapNotNull null
            val href = a.attr("abs:href").ifBlank { return@mapNotNull null }
            val title = (el.selectFirst("h2, h3, .entry-title")?.text()
                ?: a.attr("title")).trim().ifBlank { return@mapNotNull null }
            val poster = el.selectFirst("img")?.let {
                it.attr("data-src").ifBlank { it.attr("src") }
            }
            val isSeries = title.contains("series", true) || title.contains("episode", true)
            if (isSeries)
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) { posterUrl = poster }
            else
                newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = poster }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = ua).document

        val title = doc.selectFirst("h1.entry-title, h1.post-title, h1")?.text()?.trim()
            ?: doc.title().trim()

        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?: doc.selectFirst("img[itemprop=thumbnailUrl], img.wp-post-image")?.attr("src")

        val description = doc.selectFirst("meta[name=description], meta[property=og:description]")
            ?.attr("content")

        // Direct video link from schema markup
        val directVideoUrl = doc.selectFirst("meta[itemprop=contentURL]")?.attr("content")
            ?: doc.selectFirst("[itemprop=contentURL]")?.attr("content") ?: ""

        val isSeries = title.contains("episode", true) || title.contains("series", true) ||
                url.contains("episode", true) || url.contains("web-series", true)

        return if (isSeries && directVideoUrl.isNotBlank()) {
            val epNum = Regex("""episode[-\s]?(\d+)""", RegexOption.IGNORE_CASE)
                .find(title + url)?.groupValues?.get(1)?.toIntOrNull() ?: 1

            val episodes = listOf(
                newEpisode(directVideoUrl) {
                    name = "Episode $epNum"
                    episode = epNum
                    season = 1
                    this.posterUrl = poster
                }
            )
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, directVideoUrl) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false
        val videoUrl = data.trim()
        if (!videoUrl.startsWith("http")) return false

        val quality = when {
            videoUrl.contains("1080") -> Qualities.P1080.value
            videoUrl.contains("720")  -> Qualities.P720.value
            videoUrl.contains("480")  -> Qualities.P480.value
            else                      -> Qualities.Unknown.value
        }
        val qualityName = when (quality) {
            Qualities.P1080.value -> "1080p"
            Qualities.P720.value  -> "720p"
            Qualities.P480.value  -> "480p"
            else                  -> "HD"
        }

        callback(
            newExtractorLink(name, "$name [$qualityName]", videoUrl, ExtractorLinkType.VIDEO) {
                this.quality = quality
                this.headers = ua + mapOf("Referer" to mainUrl)
            }
        )
        return true
    }
}
