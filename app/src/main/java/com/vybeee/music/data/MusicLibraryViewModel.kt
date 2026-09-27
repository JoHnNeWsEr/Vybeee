package com.vybeee.music.data

import android.app.Application
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicLibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val _songs = MutableStateFlow<List<AudioSong>>(emptyList())
    val songs = _songs.asStateFlow()

    init { refresh() }

    fun refresh() {
        val resolver = getApplication<Application>().contentResolver
        val result = mutableListOf<AudioSong>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION)
        resolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                result += AudioSong(id, cursor.getString(titleCol).orEmpty().ifBlank { "Unknown title" }, cursor.getString(artistCol).orEmpty().ifBlank { "Unknown artist" }, cursor.getString(albumCol).orEmpty().ifBlank { "Unknown album" }, cursor.getLong(durationCol), "${MediaStore.Audio.Media.EXTERNAL_CONTENT_URI}/$id")
            }
        }
        _songs.value = result
    }
}
