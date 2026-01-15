package eu.kanade.tachiyomi.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class AndroidCookieJar : CookieJar {

    private val manager = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()

        cookies.forEach { manager.setCookie(urlString, it.toString()) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return get(url)
    }

    fun get(url: HttpUrl): List<Cookie> {
        val cookies = manager.getCookie(url.toString())

        return if (cookies != null && cookies.isNotEmpty()) {
            cookies.split(";").mapNotNull { Cookie.parse(url, it) }
        } else {
            emptyList()
        }
    }

    fun remove(url: HttpUrl, cookieNames: List<String>? = null, maxAge: Int = -1): Int {
        val urlString = url.toString()
        val cookies = manager.getCookie(urlString) ?: return 0

        fun List<String>.filterNames(): List<String> {
            return if (cookieNames != null) {
                this.filter { it in cookieNames }
            } else {
                this
            }
        }

        return cookies.split(";")
            .map { it.substringBefore("=") }
            .filterNames()
            .onEach { manager.setCookie(urlString, "$it=;Max-Age=$maxAge") }
            .count()
    }

    /**
     * Removes all cookies managed by the Android CookieManager.
     */
    fun removeAll() {
        manager.removeAllCookies {}
    }

    /**
     * Merges the provided cookies into the CookieManager for the given URL, overwriting any existing cookies with the same names.
     *
     * Existing cookies for the URL are preserved unless a cookie in `cookies` has the same name, in which case it is replaced.
     *
     * @param url The request URL whose cookie store will be updated.
     * @param cookies List of cookies to add or update for the given URL.
     */
    fun addAll(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()

        // Get existing cookies for the URL
        val existingCookies = manager.getCookie(urlString)?.split("; ")?.mapNotNull { cookie ->
            val parts = cookie.split('=', limit = 2)
            if (parts.size == 2) {
                parts[0] to parts[1]
            } else {
                null // Skip malformed cookies
            }
        }?.toMap()?.toMutableMap() ?: mutableMapOf()

        // Add or update the cookies
        cookies.forEach { newCookie ->
            existingCookies[newCookie.name] = newCookie.value
        }

        // Convert the map back to a string and set it in the cookie manager
        val finalCookiesString = existingCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        manager.setCookie(urlString, finalCookiesString)
    }
}
