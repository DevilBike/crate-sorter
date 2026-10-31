package io.github.devilbike.cratesorter

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// The genre queue and results, kept in SharedPreferences so nothing is lost if Android
// kills the app. The page reads results back through the bridge.
object GenreStore {
    private const val PREFS = "genres"
    private val logLines = ArrayDeque<String>()

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun queue(c: Context) = JSONArray(prefs(c).getString("queue", "[]"))
    private fun results(c: Context) = JSONObject(prefs(c).getString("results", "{}") ?: "{}")

    // items: JSON array of [id, query]: a Spotify artist id and name for MusicBrainz,
    // or "lfa:<artist id>" / "lft:<track id>" with the artist name (and "\t" track name) for Last.fm.
    @Synchronized
    fun enqueue(c: Context, items: String): Int {
        val q = queue(c)
        val have = results(c)
        val queued = HashSet<String>()
        for (i in 0 until q.length()) queued.add(q.getJSONArray(i).getString(0))
        val add = JSONArray(items)
        for (i in 0 until add.length()) {
            val pair = add.getJSONArray(i)
            val id = pair.getString(0)
            if (!have.has(id) && queued.add(id)) q.put(pair)
        }
        prefs(c).edit().putString("queue", q.toString()).apply()
        return q.length()
    }

    @Synchronized
    fun peek(c: Context): Pair<String, String>? {
        val q = queue(c)
        if (q.length() == 0) return null
        val p = q.getJSONArray(0)
        return p.getString(0) to p.getString(1)
    }

    @Synchronized
    fun put(c: Context, id: String, tags: List<String>) {
        val r = results(c).put(id, JSONArray(tags))
        prefs(c).edit().putString("results", r.toString()).putString("queue", drop(queue(c), id).toString()).apply()
    }

    @Synchronized
    fun skip(c: Context, id: String) {
        log("skipped $id after repeated failures")
        prefs(c).edit().putString("queue", drop(queue(c), id).toString()).apply()
    }

    @Synchronized
    fun dropWhere(c: Context, test: (String) -> Boolean) {
        val q = queue(c)
        val out = JSONArray()
        for (i in 0 until q.length()) if (!test(q.getJSONArray(i).getString(0))) out.put(q.getJSONArray(i))
        prefs(c).edit().putString("queue", out.toString()).apply()
    }

    @Synchronized
    fun left(c: Context): Int = queue(c).length()

    @Synchronized
    fun poll(c: Context): String {
        val lines = JSONArray()
        while (logLines.isNotEmpty()) lines.put(logLines.removeFirst())
        return JSONObject()
            .put("running", WorkService.running)
            .put("left", queue(c).length())
            .put("results", results(c))
            .put("log", lines)
            .toString()
    }

    @Synchronized
    fun log(line: String) {
        logLines.addLast(line)
        while (logLines.size > 200) logLines.removeFirst()
    }

    private fun drop(q: JSONArray, id: String): JSONArray {
        val out = JSONArray()
        for (i in 0 until q.length()) if (q.getJSONArray(i).getString(0) != id) out.put(q.getJSONArray(i))
        return out
    }
}
