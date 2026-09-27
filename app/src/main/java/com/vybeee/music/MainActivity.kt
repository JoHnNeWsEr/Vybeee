package com.vybeee.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vybeee.music.data.AudioSong
import com.vybeee.music.data.MusicLibraryViewModel
import com.vybeee.music.ui.theme.VybeeeTheme

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) permissionLauncher.launch(permission)
        setContent { VybeeeTheme { VybeeeApp() } }
    }
}

@Composable
private fun VybeeeApp(vm: MusicLibraryViewModel = viewModel()) {
    var selected by remember { mutableIntStateOf(0) }
    val songs by vm.songs.collectAsState()
    Scaffold(bottomBar = {
        NavigationBar {
            listOf("Home" to Icons.Default.Home, "Songs" to Icons.Default.MusicNote, "Settings" to Icons.Default.Settings).forEachIndexed { index, item ->
                NavigationBarItem(selected == index, { selected = index }, icon = { Icon(item.second, item.first) }, label = { Text(item.first) })
            }
        }
    }) { padding ->
        when (selected) {
            0 -> HomeScreen(songs, padding)
            1 -> SongsScreen(songs, vm, padding)
            else -> SettingsScreen(padding)
        }
    }
}

@Composable
private fun HomeScreen(songs: List<AudioSong>, padding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 24.dp)) {
        item {
            Text("Good evening", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp)); Text("Your music. Your device. Your vibe.")
            Spacer(Modifier.height(28.dp)); Text("Your Library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) { Text("${songs.size} songs", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(6.dp)); Text(if (songs.isEmpty()) "Add music to your device, then refresh your library." else "Your local music is ready to play.") } }
        }
    }
}

@Composable
private fun SongsScreen(songs: List<AudioSong>, vm: MusicLibraryViewModel, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Songs", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Refresh") }
        }
        if (songs.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No music found yet.") }
        else LazyColumn { items(songs, key = { it.id }) { song -> ListItem(headlineContent = { Text(song.title) }, supportingContent = { Text("${song.artist} • ${song.album}") }, leadingContent = { Icon(Icons.Default.MusicNote, null) }) } }
    }
}

@Composable
private fun SettingsScreen(padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        ListItem(headlineContent = { Text("Theme") }, supportingContent = { Text("System default") }, leadingContent = { Icon(Icons.Default.DarkMode, null) })
        ListItem(headlineContent = { Text("Library") }, supportingContent = { Text("Music is read locally from your device") }, leadingContent = { Icon(Icons.Default.LibraryMusic, null) })
        Spacer(Modifier.height(20.dp)); Text("Vybeee v1.0.0")
    }
}
