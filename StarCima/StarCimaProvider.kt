package com.lagradost.cloudstream3.plugins

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class StarCimaProvider : MainAPI() {
    override var mainUrl = "https://starcima.com"
    override var name = "StarCima"
    override val hasMainPage = true
    override var lang = "ar"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // 1. جلب محتوى الصفحة الرئيسية للموقع
    override suspend fun getMainPage(page: Int, request: HomePageRequest): HomePageResponse? {
        val url = if (page <= 1) mainUrl else "$mainUrl/page/$page/"
        val document = app.get(url).document
        
        // استهداف الفئة (Class) التي تحتوي على بطاقة الفيلم أو المسلسل في قالب الموقع
        val items = document.select("div.media-card, div.movie-item, li.video-item") 
        val homeResults = items.mapNotNull { it.toSearchResponse() }
        
        return newHomePageResponse(request.name, homeResults)
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val title = this.select("h3, h2, .title").text() ?: return null
        val href = this.select("a").attr("href") ?: return null
        val posterUrl = this.select("img").attr("data-src").ifEmpty { this.select("img").attr("src") }
        
        // تحديد ما إذا كان المحتوى فيلماً أم مسلسلاً بناءً على الرابط أو النص
        val type = if (href.contains("type=movie") || title.contains("فيلم")) TvType.Movie else TvType.TvSeries

        return newMovieSearchResponse(title, fixUrl(href), type) {
            this.posterUrl = fixUrl(posterUrl)
        }
    }

    // 2. البحث داخل الموقع
    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=$query"
        val document = app.get(searchUrl).document
        return document.select("div.media-card, div.movie-item").mapNotNull {
            it.toSearchResponse()
        }
    }

    // 3. تحميل صفحة الفيلم/الحلقة واستخراج تفاصيلها
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.select("h1.entry-title, h1.title").text()
        val poster = document.select("div.poster img, .media-poster img").attr("src")
        
        val type = if (url.contains("type=movie")) TvType.Movie else TvType.TvSeries

        return if (type == TvType.Movie) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = fixUrl(poster)
            }
        } else {
            // كود افتراضي للمسلسلات لجلب الحلقات إذا وُجدت
            val episodes = document.select("div.episodes-list a, ul.episodes a").map {
                val epHref = it.attr("href")
                val epName = it.text()
                Episode(epHref, epName)
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = fixUrl(poster)
            }
        }
    }

    // 4. استخراج روابط التشغيل (السيرفرات)
    override suspend fun loadLinks(
        data: String,
        isCinephase: Boolean,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // البحث عن وسوم iframe أو السيرفرات المدمجة داخل صفحة ستار سيما
        document.select("iframe").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotEmpty()) {
                // استخدام الـ Extractor التلقائي في تطبيق Cloudstream لتشغيل السيرفرات المشهورة
                loadExtractor(src, data, callback)
            }
        }
        return true
    }
}
