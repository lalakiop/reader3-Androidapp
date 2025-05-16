package com.example.myapplication

import okhttp3.*
import java.util.concurrent.ConcurrentHashMap

object HttpClientSingleton {
    private val cookieStore: MutableMap<String, List<Cookie>> = ConcurrentHashMap()

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host] = cookies
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: emptyList()
            }
        })
        .build()
}
