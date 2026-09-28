package com.vybeee.music.data

import android.app.Application
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicLibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LibraryStore(application)
    private val _songs = MutableStateFlow<List<AudioSong>>(emptyList())
    val songs = _songs.asStateFlow()
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    private val _favorites = MutableStateFlow(store.favorites())
    val favorites = _favorites.asStateFlow()
    private val _recent = MutableStateFlow(store.recentlyPlayed())
    val recent = _recent.asStateFlow()
    private val _nowPlaying = MutableStateFlow<AudioSong?>(null)
    val nowPlaying = _nowPlaying.asStateFlow()

    init { refresh() }

    fun refresh() {
        val resolver = getApplication<Application>().contentResolver
        val result = mutableListOf<AudioSong>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )
        if (Build.VERSION.SDK_INT >= 29) projection += MediaStore.Audio.Media.RELATIVE_PATH
        else projection += MediaStore.Audio.Media.DATA

        resolver.query(
            collection,
            projection.toTypedArray(),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val folderCol = if (Build.VERSION.SDK_INT >= 29)
                cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            else
                cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val rawFolder = if (folderCol >= 0) cursor.getString(folderCol).orEmpty() else ""
                val folder = rawFolder
                    .trimEnd('/')
                    .substringAfterLast('/')
                    .ifBlank { "Music" }

                result += AudioSong(
                    id = id,
                    title = cursor.getString(titleCol).orEmpty().ifBlank { "Unknown title" },
                    artist = cursor.getString(artistCol).orEmpty().ifBlank { "Unknown artist" },
                    album = cursor.getString(albumCol).orEmpty().ifBlank { "Unknown album" },
                    duration = cursor.getLong(durationCol),
                    uri = "${MediaStore.Audio.Media.EXTERNAL_CONTENT_URI}/$id",
                    folder = folder
                )
            }
        }
        _songs.value = result
    }

    fun setQuery(value: String) { _query.value = value }

    fun filteredSongs(source: List<AudioSong>): List<AudioSong> {
        val q = _query.value.trim().lowercase()
        if (q.isBlank()) return source
        return source.filter {
            it.title.lowercase().contains(q) ||
            it.artist.lowercase().contains(q) ||
            it.album.lowercase().contains(q) ||
            it.folder.lowercase().contains(q)
        }
    }

    fun toggleFavorite(id: Long) {
        store.toggleFavorite(id)
        _favorites.value = store.favorites()
    }

    fun recordPlayed(id: Long) {
        store.recordPlayed(id)
        _recent.value = store.recentlyPlayed()
        _nowPlaying.value = _songs.value.find { it.id == id }
    }

    fun playlists(): List<String> = store.playlists()
    fun playlistSongs(name: String): List<Long> = store.playlistSongs(name)
    fun createPlaylist(name: String) { store.createPlaylist(name) }
    fun deletePlaylist(name: String) { store.deletePlaylist(name) }
    fun togglePlaylistSong(name: String, id: Long) { store.togglePlaylistSong(name, id) }
}
