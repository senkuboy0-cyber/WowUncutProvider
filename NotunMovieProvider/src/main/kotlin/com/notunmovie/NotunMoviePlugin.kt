package com.notunmovie

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class NotunMoviePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(NotunMovieProvider())
    }
}