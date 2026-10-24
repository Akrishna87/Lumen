package io.github.akrishna87.lumen

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** An album the person made: a name and a list of photos, like iOS albums. No files are copied. */
data class UserAlbum(val id: String, val name: String, val ids: List<Long>)

/** The person's own albums, kept in the app's preferences. */
class UserAlbums(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("albums", Context.MODE_PRIVATE)
    private val _albums = MutableStateFlow(read())
    val albums: StateFlow<List<UserAlbum>> = _albums.asStateFlow()

    fun create(name: String): UserAlbum {
        val album = UserAlbum(UUID.randomUUID().toString(), name.trim(), emptyList())
        save(_albums.value + album)
        return album
    }

    fun rename(id: String, name: String) = update(id) { it.copy(name = name.trim()) }

    fun delete(id: String) = save(_albums.value.filterNot { it.id == id })

    /** Adds photos to the end of the album, skipping any already in it. */
    fun add(id: String, photos: List<Long>) = update(id) { album -> album.copy(ids = album.ids + photos.filterNot { it in album.ids }) }

    fun remove(id: String, photos: List<Long>) = update(id) { album -> album.copy(ids = album.ids - photos.toSet()) }

    private fun update(id: String, change: (UserAlbum) -> UserAlbum) = save(_albums.value.map { if (it.id == id) change(it) else it })

    private fun save(albums: List<UserAlbum>) {
        _albums.value = albums
        val json = JSONArray()
        albums.forEach { a ->
            json.put(JSONObject().put("id", a.id).put("name", a.name).put("ids", JSONArray(a.ids)))
        }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private fun read(): List<UserAlbum> = runCatching {
        val json = JSONArray(prefs.getString(KEY, "[]"))
        (0 until json.length()).map { i ->
            val o = json.getJSONObject(i)
            val ids = o.getJSONArray("ids")
            UserAlbum(o.getString("id"), o.getString("name"), (0 until ids.length()).map { ids.getLong(it) })
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY = "albums"
    }
}

/**
 * Photos hidden from Lumen's Library, albums and Search. They stay where they are on the phone and
 * still show in other apps; only Lumen keeps them in its locked Hidden album.
 */
class HiddenPhotos(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("hidden", Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet())
    val ids: StateFlow<Set<Long>> = _ids.asStateFlow()

    fun set(photos: List<Long>, hidden: Boolean) {
        val next = if (hidden) _ids.value + photos else _ids.value - photos.toSet()
        _ids.value = next
        prefs.edit().putStringSet(KEY, next.map { it.toString() }.toSet()).apply()
    }

    private companion object {
        const val KEY = "ids"
    }
}
