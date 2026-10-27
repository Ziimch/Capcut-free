package com.freeeditor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val inMs: Long,
    val outMs: Long,
    val sourceMs: Long,
) { val durationMs: Long get() = outMs - inMs }

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val clips: List<Clip> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
)

class ProjectStore(context: Context) {
    private val dir = File(context.filesDir, "projects").apply { mkdirs() }

    fun save(p: Project) {
        val clips = JSONArray()
        p.clips.forEach {
            clips.put(JSONObject().put("id", it.id).put("uri", it.uri)
                .put("in", it.inMs).put("out", it.outMs).put("src", it.sourceMs))
        }
        val o = JSONObject().put("id", p.id).put("name", p.name)
            .put("updated", System.currentTimeMillis()).put("clips", clips)
        val tmp = File(dir, "${p.id}.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(File(dir, "${p.id}.json")) // atomic-ish write
    }

    fun loadAll(): List<Project> = (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
        .mapNotNull { runCatching { parse(it.readText()) }.getOrNull() }
        .sortedByDescending { it.updatedAt }

    fun delete(id: String) { File(dir, "$id.json").delete() }

    private fun parse(s: String): Project {
        val o = JSONObject(s)
        val a = o.getJSONArray("clips")
        val clips = (0 until a.length()).map {
            val c = a.getJSONObject(it)
            Clip(c.getString("id"), c.getString("uri"), c.getLong("in"), c.getLong("out"), c.getLong("src"))
        }
        return Project(o.getString("id"), o.getString("name"), clips, o.optLong("updated"))
    }
}
