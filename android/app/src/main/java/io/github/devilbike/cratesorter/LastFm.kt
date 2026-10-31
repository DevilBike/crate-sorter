package io.github.devilbike.cratesorter

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Same requests as the web app: artist.getInfo (tags plus similar artists) and track.getTopTags.
// Similar artists are stored after the tags with a "~" in front, so they fit the genre store's list format.
object LastFm {
    @Volatile var key: String = ""

    fun lookup(id: String, query: String): MusicBrainz.Result {
        if (key.isBlank()) return MusicBrainz.Result.Failed("no Last.fm key")
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
        val isArtist = id.startsWith("lfa:")
        val params = if (isArtist) "method=artist.getinfo&artist=${enc(query)}" else {
            val parts = query.split('\t', limit = 2)
            "method=track.gettoptags&artist=${enc(parts[0])}&track=${enc(parts.getOrElse(1) { "" })}"
        }
        val conn = URL("https://ws.audioscrobbler.com/2.0/?format=json&autocorrect=1&$params&api_key=${enc(key)}").openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "CrateSorter/1.0 ( https://github.com/DevilBike/crate-sorter )")
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() ?: ""
            val j = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
            val err = j.optInt("error", 0)
            GenreStore.log("Last.fm \"${query.replace('\t', ' ')}\" → $code${if (err != 0) " error $err" else ""}")
            when {
                err == 10 || err == 26 || (code == 403 && err != 29) -> MusicBrainz.Result.BadKey
                err == 29 || err == 16 || err == 11 || code == 429 || code >= 500 -> MusicBrainz.Result.SlowDown
                code != 200 || err != 0 -> MusicBrainz.Result.Found(emptyList())
                isArtist -> MusicBrainz.Result.Found(artist(j))
                else -> MusicBrainz.Result.Found(tags(j.optJSONObject("toptags")?.opt("tag"), 10))
            }
        } catch (e: Exception) {
            MusicBrainz.Result.Failed(e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""))
        } finally {
            conn.disconnect()
        }
    }

    private fun list(v: Any?): List<JSONObject> = when (v) {
        is JSONArray -> (0 until v.length()).mapNotNull { v.optJSONObject(it) }
        is JSONObject -> listOf(v)
        else -> emptyList()
    }

    private fun tags(v: Any?, min: Int): List<String> =
        list(v).filter { it.optInt("count", 100) >= min }.take(8).map { it.optString("name").lowercase().trim() }.filter { it.isNotEmpty() }

    private fun artist(j: JSONObject): List<String> {
        val a = j.optJSONObject("artist") ?: return emptyList()
        val t = tags(a.optJSONObject("tags")?.opt("tag"), 0)
        val sim = list(a.optJSONObject("similar")?.opt("artist")).map { it.optString("name") }.filter { it.isNotEmpty() }.map { "~$it" }
        return t + sim
    }
}
