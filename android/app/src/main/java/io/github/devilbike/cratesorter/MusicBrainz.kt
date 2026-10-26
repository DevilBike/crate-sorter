package io.github.devilbike.cratesorter

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Same matching as the web app: exact name with a good score, or a near-certain match.
object MusicBrainz {
    sealed class Result {
        data class Found(val tags: List<String>) : Result()
        object SlowDown : Result()
        data class Failed(val why: String) : Result()
    }

    fun lookup(name: String): Result {
        val query = URLEncoder.encode("artist:\"${name.replace("\"", "")}\"", "UTF-8")
        val conn = URL("https://musicbrainz.org/ws/2/artist/?fmt=json&limit=3&query=$query").openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "CrateSorter/1.0 ( https://github.com/DevilBike/crate-sorter )")
            val code = conn.responseCode
            GenreStore.log("\"$name\" → $code")
            when {
                code == 503 || code == 429 -> Result.SlowDown
                code != 200 -> Result.Found(emptyList())
                else -> Result.Found(parse(conn.inputStream.bufferedReader().readText(), name))
            }
        } catch (e: Exception) {
            Result.Failed(e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""))
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(body: String, name: String): List<String> {
        val artists = JSONObject(body).optJSONArray("artists") ?: return emptyList()
        var hit: JSONObject? = null
        for (i in 0 until artists.length()) {
            val a = artists.getJSONObject(i)
            if (a.optString("name").equals(name, ignoreCase = true) && a.optInt("score", 0) >= 80) { hit = a; break }
        }
        if (hit == null) {
            for (i in 0 until artists.length()) {
                val a = artists.getJSONObject(i)
                if (a.optInt("score", 0) >= 95) { hit = a; break }
            }
        }
        val tags = hit?.optJSONArray("tags") ?: return emptyList()
        return (0 until tags.length())
            .map { tags.getJSONObject(it) }
            .filter { it.optInt("count", 1) > 0 }
            .sortedByDescending { it.optInt("count", 0) }
            .take(8)
            .map { it.optString("name").lowercase() }
    }
}
