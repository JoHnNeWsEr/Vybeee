package com.vybeee.music.data

import android.content.Context

class LibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences("vybeee_library", Context.MODE_PRIVATE)

    fun favorites(): Set<Long> =
        prefs.getStringSet("favorites", emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet() ?: emptySet()

    fun toggleFavorite(id: Long): Boolean {
        val set = favorites().toMutableSet()
        if (!set.add(id)) set.remove(id)
        prefs.edit().putStringSet("favorites", set.map(Long::toString).toSet()).apply()
        return id in set
    }

    fun isFavorite(id: Long) = id in favorites()

    fun recentlyPlayed(): List<Long> =
        prefs.getString("recent", "")
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            ?: emptyList()

    fun recordPlayed(id: Long) {
        val updated = (listOf(id) + recentlyPlayed().filterNot { it == id }).take(30)
        prefs.edit().putString("recent", updated.joinToString(",")).apply()
        incrementPlayCount(id)
    }

    fun clearRecentlyPlayed() {
        prefs.edit().remove("recent").apply()
    }

    private fun playCounts(): Map<Long, Int> =
        prefs.getString("play_counts", "")
            ?.split(',')
            ?.mapNotNull { entry ->
                val parts = entry.split(':')
                if (parts.size == 2) {
                    val id = parts[0].toLongOrNull()
                    val count = parts[1].toIntOrNull()
                    if (id != null && count != null) id to count else null
                } else null
            }
            ?.toMap()
            ?: emptyMap()

    private fun incrementPlayCount(id: Long) {
        val counts = playCounts().toMutableMap()
        counts[id] = (counts[id] ?: 0) + 1
        prefs.edit()
            .putString("play_counts", counts.entries.joinToString(",") { "${it.key}:${it.value}" })
            .apply()
    }

    fun playCount(id: Long): Int = playCounts()[id] ?: 0

    fun mostPlayed(ids: List<Long>, limit: Int = 10): List<Long> =
        ids.distinct().sortedWith(
            compareByDescending<Long> { playCount(it) }.thenBy { it }
        ).take(limit)

    fun playlists(): List<String> =
        prefs.getStringSet("playlists", setOf("My Playlist"))
            ?.toList()
            ?.sorted()
            ?: listOf("My Playlist")

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

    fun renamePlaylist(oldName: String, newName: String): Boolean {
        val clean = newName.trim()
        if (oldName == "My Playlist" || clean.isBlank() || clean == oldName) return false
        val names = playlists().toMutableSet()
        if (clean in names) return false
        val songs = playlistSongs(oldName)
        names.remove(oldName)
        names.add(clean)
        prefs.edit()
            .putStringSet("playlists", names)
            .remove("playlist_${oldName}")
            .putString("playlist_$clean", songs.joinToString(","))
            .apply()
        return true
    }

    fun playlistSongs(name: String): List<Long> =
        prefs.getString("playlist_$name", "")
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            ?: emptyList()

    fun togglePlaylistSong(name: String, id: Long) {
        val songs = playlistSongs(name).toMutableList()
        if (id in songs) songs.remove(id) else songs.add(id)
        prefs.edit().putString("playlist_$name", songs.joinToString(",")).apply()
    }

    fun movePlaylistSong(name: String, fromIndex: Int, toIndex: Int) {
        val songs = playlistSongs(name).toMutableList()
        if (fromIndex !in songs.indices || toIndex !in songs.indices) return
        val item = songs.removeAt(fromIndex)
        songs.add(toIndex, item)
        prefs.edit().putString("playlist_$name", songs.joinToString(",")).apply()
    }

    fun queuePresets(): List<String> =
        prefs.getStringSet("queue_presets", emptySet())
            ?.toList()?.sorted() ?: emptyList()

    fun createQueuePreset(name: String, ids: List<Long>): String {
        val base = name.trim().ifBlank { "My Queue" }
        val existing = queuePresets().toSet()
        var clean = base
        var number = 2
        while (clean in existing) {
            clean = "$base $number"
            number++
        }
        val names = existing.toMutableSet()
        names.add(clean)
        prefs.edit()
            .putStringSet("queue_presets", names)
            .putString("queue_preset_$clean", ids.distinct().joinToString(","))
            .apply()
        return clean
    }

    fun queuePresetSongs(name: String): List<Long> =
        prefs.getString("queue_preset_$name", "")
            ?.split(',')?.mapNotNull { it.toLongOrNull() } ?: emptyList()

    fun renameQueuePreset(oldName: String, newName: String): Boolean {
        val clean = newName.trim()
        if (clean.isBlank() || clean == oldName) return false
        val names = queuePresets().toMutableSet()
        if (clean in names) return false
        val ids = queuePresetSongs(oldName)
        names.remove(oldName); names.add(clean)
        prefs.edit().putStringSet("queue_presets", names)
            .remove("queue_preset_$oldName")
            .putString("queue_preset_$clean", ids.joinToString(","))
            .apply()
        return true
    }

    fun deleteQueuePreset(name: String) {
        val names = queuePresets().toMutableSet()
        names.remove(name)
        prefs.edit().putStringSet("queue_presets", names)
            .remove("queue_preset_$name").apply()
    }

    fun clearPlaylist(name: String) {
        prefs.edit().putString("playlist_$name", "").apply()
    }
}
