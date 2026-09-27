package com.vybeee.music.data

import android.content.Context
import org.json.JSONArray

class LibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences("vybeee_library", Context.MODE_PRIVATE)

    fun favorites(): Set<Long> = prefs.getStringSet("favorites", emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()

    fun toggleFavorite(id: Long): Boolean {
        val set = favorites().toMutableSet()
        if (!set.add(id)) set.remove(id)
        prefs.edit().putStringSet("favorites", set.map(Long::toString).toSet()).apply()
        return id in set
    }

    fun isFavorite(id: Long) = id in favorites()

    fun recentlyPlayed(): List<Long> = prefs.getString("recent", "")?.split(',')?.mapNotNull { it.toLongOrNull() } ?: emptyList()

    fun recordPlayed(id: Long) {
        val updated = (listOf(id) + recentlyPlayed().filterNot { it == id }).take(30)
        prefs.edit().putString("recent", updated.joinToString(",")).apply()
    }

    fun playlists(): List<String> = prefs.getStringSet("playlists", setOf("My Playlist"))?.toList()?.sorted() ?: listOf("My Playlist")

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        val names = playlists().toMutableSet()
        names.add(name.trim())
        prefs.edit().putStringSet("playlists", names).apply()
    }

    fun deletePlaylist(name: String) {
        if (name == "My Playlist") return
        val names = playlists().toMutableSet()
        names.remove(name)
        prefs.edit().putStringSet("playlists", names).apply()
        prefs.edit().remove("playlist_${name}").apply()
    }

    fun playlistSongs(name: String): List<Long> = prefs.getString("playlist_$name", "")?.split(',')?.mapNotNull { it.toLongOrNull() } ?: emptyList()

    fun togglePlaylistSong(name: String, id: Long) {
        val songs = playlistSongs(name).toMutableList()
        if (id in songs) songs.remove(id) else songs.add(id)
        prefs.edit().putString("playlist_$name", songs.joinToString(",")).apply()
    }
}
