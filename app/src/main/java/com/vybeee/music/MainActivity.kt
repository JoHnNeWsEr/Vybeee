package com.vybeee.music

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.vybeee.music.data.AudioSong
import com.vybeee.music.data.MusicLibraryViewModel
import com.vybeee.music.player.VybeeePlaybackService
import com.vybeee.music.ui.theme.VybeeeTheme

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) permissionLauncher.launch(permission)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        controllerFuture = MediaController.Builder(
            this,
            SessionToken(this, ComponentName(this, VybeeePlaybackService::class.java))
        ).buildAsync()
        setContent { VybeeeTheme { VybeeeApp(controllerFuture) } }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}

private enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("Home", Icons.Default.Home),
    SONGS("Songs", Icons.Default.MusicNote),
    ALBUMS("Albums", Icons.Default.Album),
    ARTISTS("Artists", Icons.Default.Person),
    MORE("More", Icons.Default.MoreHoriz),
    FAVORITES("Favorites", Icons.Default.Favorite),
    PLAYLISTS("Playlists", Icons.Default.QueueMusic),
    FOLDERS("Folders", Icons.Default.Folder),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
private fun VybeeeApp(
    controllerFuture: ListenableFuture<MediaController>?,
    vm: MusicLibraryViewModel = viewModel()
) {
    var selected by remember { mutableStateOf(Tab.HOME) }
    val songs by vm.songs.collectAsState()
    val query by vm.query.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val nowPlaying by vm.nowPlaying.collectAsState()
    val filtered = remember(songs, query) { vm.filteredSongs(songs) }
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }

    LaunchedEffect(controllerFuture) {
        controllerFuture?.let { future ->
            if (future.isDone) controller = future.get()
            else future.addListener({ controller = future.get() }, ContextCompat.getMainExecutor(context))
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val tabs = listOf(Tab.HOME, Tab.SONGS, Tab.ALBUMS, Tab.ARTISTS, Tab.MORE)
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab },
                        icon = { Icon(tab.icon, tab.label) },
                        label = { Text(tab.label, maxLines = 1) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                Tab.HOME -> HomeScreen(songs, favorites, vm, controller) { selected = it }
                Tab.SONGS -> SongsScreen(filtered, query, vm, controller, favorites)
                Tab.ALBUMS -> AlbumsScreen(songs, controller, vm)
                Tab.ARTISTS -> ArtistsScreen(songs, controller, vm)
                Tab.FAVORITES -> SongsScreen(songs.filter { it.id in favorites }, query, vm, controller, favorites, "Favorites")
                Tab.PLAYLISTS -> PlaylistsScreen(songs, vm, controller)
                Tab.MORE -> MoreScreen { selected = it }
                Tab.FOLDERS -> FoldersScreen(songs, vm, controller)
                Tab.SETTINGS -> SettingsScreen(vm)
            }
            if (nowPlaying != null) MiniPlayer(nowPlaying!!, controller)
        }
    }
}

@Composable
private fun HomeScreen(
    songs: List<AudioSong>,
    favorites: Set<Long>,
    vm: MusicLibraryViewModel,
    controller: MediaController?,
    go: (Tab) -> Unit
) {
    val recentIds by vm.recent.collectAsState()
    val recent = recentIds.mapNotNull { id -> songs.find { it.id == id } }.take(5)
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 24.dp)
    ) {
        item {
            Text("Good evening", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Your music. Your device. Your vibe.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(22.dp))
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LibraryMusic, null, Modifier.size(38.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Your Library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${songs.size} songs • ${favorites.size} favorites • ${songs.map { it.album }.distinct().size} albums")
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("Quick access", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { go(Tab.SONGS) }, label = { Text("Songs") }, leadingIcon = { Icon(Icons.Default.MusicNote, null) })
                AssistChip(onClick = { go(Tab.MORE) }, label = { Text("More") }, leadingIcon = { Icon(Icons.Default.MoreHoriz, null) })
            }
            Spacer(Modifier.height(22.dp))
            if (recent.isNotEmpty()) Text("Recently played", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        items(recent, key = { it.id }) { song ->
            SongRow(song, favorites.contains(song.id), { vm.toggleFavorite(song.id) }) {
                playSong(controller, song, songs)
                vm.recordPlayed(song.id)
            }
        }
    }
}

@Composable
private fun SongsScreen(
    songs: List<AudioSong>,
    query: String,
    vm: MusicLibraryViewModel,
    controller: MediaController?,
    favorites: Set<Long>,
    title: String = "Songs"
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Refresh") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = vm::setQuery,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            singleLine = true,
            placeholder = { Text("Search songs, artists, albums, folders…") },
            leadingIcon = { Icon(Icons.Default.Search, null) }
        )
        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No music found yet. Add local music and refresh.")
            }
        } else {
            LazyColumn {
                items(songs, key = { it.id }) { song ->
                    SongRow(song, favorites.contains(song.id), { vm.toggleFavorite(song.id) }) {
                        playSong(controller, song, songs)
                        vm.recordPlayed(song.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun SongRow(song: AudioSong, favorite: Boolean, onFavorite: () -> Unit, onPlay: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onPlay),
        headlineContent = { Text(song.title, maxLines = 1) },
        supportingContent = { Text("${song.artist} • ${song.album}", maxLines = 1) },
        leadingContent = { Icon(Icons.Default.MusicNote, null) },
        trailingContent = {
            IconButton(onClick = onFavorite) {
                Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite")
            }
        }
    )
}

@Composable
private fun AlbumsScreen(songs: List<AudioSong>, controller: MediaController?, vm: MusicLibraryViewModel) {
    val albums = songs.groupBy { it.album }.toList().sortedBy { it.first.lowercase() }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Albums", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (albums.isEmpty()) Text("No albums yet.")
        else LazyColumn {
            items(albums, key = { it.first }) { (album, tracks) ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable {
                        playSong(controller, tracks.first(), tracks)
                        vm.recordPlayed(tracks.first().id)
                    }
                ) {
                    ListItem(
                        headlineContent = { Text(album) },
                        supportingContent = { Text("${tracks.first().artist} • ${tracks.size} songs") },
                        leadingContent = { Icon(Icons.Default.Album, null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistsScreen(songs: List<AudioSong>, controller: MediaController?, vm: MusicLibraryViewModel) {
    val artists = songs.groupBy { it.artist }.toList().sortedBy { it.first.lowercase() }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Artists", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (artists.isEmpty()) Text("No artists yet.")
        else LazyColumn {
            items(artists, key = { it.first }) { (artist, tracks) ->
                ListItem(
                    modifier = Modifier.clickable {
                        playSong(controller, tracks.first(), tracks)
                        vm.recordPlayed(tracks.first().id)
                    },
                    headlineContent = { Text(artist) },
                    supportingContent = { Text("${tracks.size} songs") },
                    leadingContent = { Icon(Icons.Default.Person, null) }
                )
            }
        }
    }
}

@Composable
private fun MoreScreen(go: (Tab) -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("More", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        ListItem(
            modifier = Modifier.clickable { go(Tab.FAVORITES) },
            headlineContent = { Text("Favorites") },
            supportingContent = { Text("Your saved songs") },
            leadingContent = { Icon(Icons.Default.Favorite, null) }
        )
        ListItem(
            modifier = Modifier.clickable { go(Tab.PLAYLISTS) },
            headlineContent = { Text("Playlists") },
            supportingContent = { Text("Create and organize local playlists") },
            leadingContent = { Icon(Icons.Default.QueueMusic, null) }
        )
        ListItem(
            modifier = Modifier.clickable { go(Tab.FOLDERS) },
            headlineContent = { Text("Folders") },
            supportingContent = { Text("Browse music by device folder") },
            leadingContent = { Icon(Icons.Default.Folder, null) }
        )
        ListItem(
            modifier = Modifier.clickable { go(Tab.SETTINGS) },
            headlineContent = { Text("Settings") },
            supportingContent = { Text("Library, appearance, and privacy") },
            leadingContent = { Icon(Icons.Default.Settings, null) }
        )
    }
}

@Composable
private fun FoldersScreen(songs: List<AudioSong>, vm: MusicLibraryViewModel, controller: MediaController?) {
    val folders = songs.groupBy { it.folder }.toList().sortedBy { it.first.lowercase() }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Folders", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (folders.isEmpty()) Text("No music folders found.")
        else LazyColumn {
            items(folders, key = { it.first }) { (folder, tracks) ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable {
                        playSong(controller, tracks.first(), tracks)
                        vm.recordPlayed(tracks.first().id)
                    }
                ) {
                    ListItem(
                        headlineContent = { Text(folder) },
                        supportingContent = { Text("${tracks.size} songs") },
                        leadingContent = { Icon(Icons.Default.Folder, null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistsScreen(songs: List<AudioSong>, vm: MusicLibraryViewModel, controller: MediaController?) {
    var playlists by remember { mutableStateOf(vm.playlists()) }
    var showCreate by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<String?>(null) }

    if (selectedPlaylist != null) {
        PlaylistDetailScreen(
            name = selectedPlaylist!!,
            songs = songs,
            vm = vm,
            controller = controller,
            onBack = { selectedPlaylist = null; playlists = vm.playlists() }
        )
        return
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Playlists", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, "Create playlist") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn {
            items(playlists, key = { it }) { name ->
                ListItem(
                    modifier = Modifier.clickable { selectedPlaylist = name },
                    headlineContent = { Text(name) },
                    supportingContent = { Text("${vm.playlistSongs(name).size} songs") },
                    leadingContent = { Icon(Icons.Default.QueueMusic, null) },
                    trailingContent = {
                        if (name != "My Playlist") {
                            IconButton(onClick = { vm.deletePlaylist(name); playlists = vm.playlists() }) {
                                Icon(Icons.Default.DeleteOutline, "Delete playlist")
                            }
                        }
                    }
                )
            }
        }
    }

    if (showCreate) {
        CreatePlaylistDialog(
            onDismiss = { showCreate = false },
            onCreate = {
                vm.createPlaylist(it)
                playlists = vm.playlists()
                showCreate = false
            }
        )
    }
}

@Composable
private fun PlaylistDetailScreen(
    name: String,
    songs: List<AudioSong>,
    vm: MusicLibraryViewModel,
    controller: MediaController?,
    onBack: () -> Unit
) {
    var refresh by remember { mutableIntStateOf(0) }
    val ids = remember(refresh, name, songs) { vm.playlistSongs(name) }
    val playlistSongs = ids.mapNotNull { id -> songs.find { it.id == id } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (playlistSongs.isNotEmpty()) {
                IconButton(onClick = {
                    playSong(controller, playlistSongs.first(), playlistSongs)
                    vm.recordPlayed(playlistSongs.first().id)
                }) { Icon(Icons.Default.PlayArrow, "Play playlist") }
            }
        }
        Text("${playlistSongs.size} songs", modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (playlistSongs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("This playlist is empty.") }
        } else {
            LazyColumn {
                items(playlistSongs, key = { it.id }) { song ->
                    ListItem(
                        modifier = Modifier.clickable {
                            playSong(controller, song, playlistSongs)
                            vm.recordPlayed(song.id)
                        },
                        headlineContent = { Text(song.title, maxLines = 1) },
                        supportingContent = { Text("${song.artist} • ${song.album}", maxLines = 1) },
                        trailingContent = {
                            IconButton(onClick = { vm.togglePlaylistSong(name, song.id); refresh++ }) {
                                Icon(Icons.Default.RemoveCircleOutline, "Remove")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Playlist name") }
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onCreate(name.trim()) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SettingsScreen(vm: MusicLibraryViewModel) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        ListItem(
            headlineContent = { Text("Appearance") },
            supportingContent = { Text("System theme for now") },
            leadingContent = { Icon(Icons.Default.DarkMode, null) }
        )
        ListItem(
            headlineContent = { Text("Library") },
            supportingContent = { Text("Refresh scans music stored on this device") },
            leadingContent = { Icon(Icons.Default.LibraryMusic, null) },
            trailingContent = { IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Refresh") } }
        )
        ListItem(
            headlineContent = { Text("Privacy") },
            supportingContent = { Text("Vybeee keeps music data and preferences on your device.") },
            leadingContent = { Icon(Icons.Default.Lock, null) }
        )
        Spacer(Modifier.height(18.dp))
        Text("Vybeee v1.2.0", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Offline music player", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MiniPlayer(song: AudioSong, controller: MediaController?) {
    Surface(shadowElevation = 8.dp, tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.MusicNote, null, Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, maxLines = 1, fontWeight = FontWeight.SemiBold)
                Text(song.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { controller?.seekToPreviousMediaItem() }) { Icon(Icons.Default.SkipPrevious, "Previous") }
            IconButton(onClick = {
                if (controller?.isPlaying == true) controller.pause() else controller?.play()
            }) {
                Icon(if (controller?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow, "Play")
            }
            IconButton(onClick = { controller?.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, "Next") }
        }
    }
}

private fun playSong(controller: MediaController?, song: AudioSong, queue: List<AudioSong>) {
    controller ?: return
    val items = queue.map { s ->
        MediaItem.Builder()
            .setMediaId(s.id.toString())
            .setUri(s.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(s.title)
                    .setArtist(s.artist)
                    .setAlbumTitle(s.album)
                    .build()
            )
            .build()
    }
    val index = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
    controller.setMediaItems(items, index, 0L)
    controller.prepare()
    controller.play()
}
