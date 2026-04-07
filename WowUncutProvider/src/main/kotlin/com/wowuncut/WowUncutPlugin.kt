package com.wowuncut

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class WowUncutPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(WowUncutProvider())
    }
}
