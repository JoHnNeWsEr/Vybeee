package com.vybeee.music
import android.provider.MediaStore

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.vybeee.music.data.AudioSong
import com.vybeee.music.data.MusicLibraryViewModel
import com.vybeee.music.data.SongSort
import com.vybeee.music.player.VybeeePlaybackService
import com.vybeee.music.ui.theme.VybeeeTheme
import kotlin.math.max

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var audioPermissionGranted by mutableStateOf(false)

    private val audioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        audioPermissionGranted = granted
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val audioPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        audioPermissionGranted = ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED

        controllerFuture = MediaController.Builder(
            this,
            SessionToken(this, ComponentName(this, VybeeePlaybackService::class.java))
        ).buildAsync()

        val prefs = getSharedPreferences("vybeee_settings", MODE_PRIVATE)
        val onboardingCompleted = prefs.getBoolean("onboarding_completed", false)

        fun requestPermissionsIfNeeded() {
            if (!audioPermissionGranted) {
                audioPermissionLauncher.launch(audioPermission)
            } else if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") }
            var showOnboarding by remember { mutableStateOf(!onboardingCompleted) }
            var showBrandSplash by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(1100L)
                showBrandSplash = false
            }

            VybeeeTheme(themeMode = themeMode) {
                if (showBrandSplash) {
                    BrandSplashScreen()
                } else {
                    VybeeeApp(
                    controllerFuture = controllerFuture,
                    audioPermissionGranted = audioPermissionGranted,
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        prefs.edit().putString("theme_mode", mode).apply()
                    },
                    showOnboarding = showOnboarding,
                    onOnboardingFinished = {
                        prefs.edit().putBoolean("onboarding_completed", true).apply()
                        showOnboarding = false
                        requestPermissionsIfNeeded()
                    },
                    onShowIntro = { showOnboarding = true }
                    )
                }
            }
        }

        if (onboardingCompleted) {
            requestPermissionsIfNeeded()
        }
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
    GENRES("Genres", Icons.Default.MusicNote),
    HISTORY("History", Icons.Default.History),
    SETTINGS("Settings", Icons.Default.Settings)
}

private data class PlayerUiState(
    val index: Int = 0,
    val position: Long = 0L,
    val duration: Long = 0L,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val itemCount: Int = 0
)

@Composable
private fun VybeeeApp(
    controllerFuture: ListenableFuture<MediaController>?,
    audioPermissionGranted: Boolean,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    showOnboarding: Boolean,
    onOnboardingFinished: () -> Unit,
    onShowIntro: () -> Unit,
    vm: MusicLibraryViewModel = viewModel()
) {
    var selected by remember { mutableStateOf(Tab.HOME) }
    var showFullPlayer by remember { mutableStateOf(false) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var sleepTimerEnd by rememberSaveable { mutableStateOf<Long?>(null) }
    var showSleepTimer by rememberSaveable { mutableStateOf(false) }
    var showClearQueue by rememberSaveable { mutableStateOf(false) }
    val songs by vm.songs.collectAsState()
    val query by vm.query.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val nowPlaying by vm.nowPlaying.collectAsState()
    val sort by vm.sort.collectAsState()
    val filtered = remember(songs, query, sort) { vm.filteredSongs(songs) }
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }

    if (showOnboarding) {
        OnboardingScreen(onFinished = onOnboardingFinished)
        return
    }

    LaunchedEffect(audioPermissionGranted) {
        if (audioPermissionGranted) {
            vm.refresh()
        }
    }

    DisposableEffect(controllerFuture) {
        val future = controllerFuture ?: return@DisposableEffect onDispose { }
        val executor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            if (!future.isCancelled) {
                controller = runCatching { future.get() }.getOrNull()
                controller?.currentMediaItem?.mediaId?.toLongOrNull()?.let(vm::syncNowPlaying)
            }
        }
        future.addListener(listener, executor)
        onDispose { }
    }

    DisposableEffect(controller) {
        val player = controller ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaId?.toLongOrNull()?.let(vm::onMediaItemChanged)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    player.currentMediaItem?.mediaId?.toLongOrNull()?.let(vm::onMediaItemChanged)
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val playerState = rememberPlayerUiState(controller)

    LaunchedEffect(controller, sleepTimerEnd) {
        val end = sleepTimerEnd ?: return@LaunchedEffect
        while (true) {
            val remaining = end - System.currentTimeMillis()
            if (remaining <= 0L) break
            kotlinx.coroutines.delay(remaining.coerceAtMost(1000L))
        }
        if (sleepTimerEnd == end) {
            controller?.pause()
            sleepTimerEnd = null
        }
    }

    Scaffold(
        bottomBar = {
            if (!showFullPlayer) {
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
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (showFullPlayer && nowPlaying != null) {
                FullPlayerScreen(
                    song = nowPlaying!!,
                    controller = controller,
                    playerState = playerState,
                    songs = songs,
                    vm = vm,
                    sleepTimerEnd = sleepTimerEnd,
                    onSleepTimerClick = { showSleepTimer = true },
                    onClearQueueClick = { showClearQueue = true },
                    onClose = { showFullPlayer = false }
                )
            } else {
                BrandHeader()
                if (selectedAlbum != null) {
                    val album = selectedAlbum!!
                    val tracks = songs.filter { it.album == album }
                    AlbumDetailScreen(
                        album = album,
                        tracks = tracks,
                        favorites = favorites,
                        vm = vm,
                        controller = controller,
                        onBack = { selectedAlbum = null }
                    )
                } else if (selectedArtist != null) {
                    val artist = selectedArtist!!
                    val tracks = songs.filter { it.artist == artist }
                    ArtistDetailScreen(
                        artist = artist,
                        tracks = tracks,
                        favorites = favorites,
                        vm = vm,
                        controller = controller,
                        onBack = { selectedArtist = null }
                    )
                } else when (selected) {
                    Tab.HOME -> HomeScreen(songs, favorites, vm, controller) { selected = it }
                    Tab.SONGS -> SongsScreen(filtered, query, vm, controller, favorites)
                    Tab.ALBUMS -> AlbumsScreen(songs, controller, vm) { selectedAlbum = it }
                    Tab.ARTISTS -> ArtistsScreen(songs, controller, vm) { selectedArtist = it }
                    Tab.FAVORITES -> SongsScreen(
                        songs.filter { it.id in favorites },
                        query,
                        vm,
                        controller,
                        favorites,
                        "Favorites"
                    )
                    Tab.PLAYLISTS -> PlaylistsScreen(songs, vm, controller)
                    Tab.MORE -> MoreScreen { selected = it }
                    Tab.FOLDERS -> FoldersScreen(songs, vm, controller)
                    Tab.GENRES -> GenresScreen(songs, controller, vm)
                    Tab.HISTORY -> HistoryScreen(songs, favorites, vm, controller)
                    Tab.SETTINGS -> SettingsScreen(
                        vm,
                        themeMode,
                        onThemeModeChange,
                        onShowIntro = onShowIntro
                    )
                }
                if (nowPlaying != null) {
                    MiniPlayer(nowPlaying!!, controller) { showFullPlayer = true }
                }
            }
        }
    }
    if (showSleepTimer) {
        SleepTimerDialog(
            currentEnd = sleepTimerEnd,
            onDismiss = { showSleepTimer = false },
            onSetMinutes = { minutes ->
                sleepTimerEnd = System.currentTimeMillis() + minutes * 60_000L
                showSleepTimer = false
            },
            onCancel = {
                sleepTimerEnd = null
                showSleepTimer = false
            }
        )
    }
    if (showClearQueue) {
        ClearQueueDialog(
            onDismiss = { showClearQueue = false },
            onConfirm = {
                controller?.stop()
                controller?.clearMediaItems()
                sleepTimerEnd = null
                showClearQueue = false
                showFullPlayer = false
            }
        )
    }
}

@Composable
private fun rememberPlayerUiState(controller: MediaController?): PlayerUiState {
    var state by remember { mutableStateOf(PlayerUiState()) }

    DisposableEffect(controller) {
        val player = controller ?: return@DisposableEffect onDispose { }
        fun update() {
            state = PlayerUiState(
                index = player.currentMediaItemIndex.coerceAtLeast(0),
                position = player.currentPosition.coerceAtLeast(0L),
                duration = player.duration.takeIf { it > 0 } ?: 0L,
                isPlaying = player.isPlaying,
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                itemCount = player.mediaItemCount
            )
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = update()
        }
        player.addListener(listener)
        update()
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(controller, state.isPlaying, state.position) {
        while (controller?.isPlaying == true) {
            kotlinx.coroutines.delay(500)
            val player = controller ?: break
            state = state.copy(
                position = player.currentPosition.coerceAtLeast(0L),
                duration = player.duration.takeIf { it > 0 } ?: state.duration,
                index = player.currentMediaItemIndex.coerceAtLeast(0)
            )
        }
    }

    return state
}

@Composable
private fun BrandSplashScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = androidx.compose.ui.graphics.Color(0xFF14101E)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.vybeee_note_icon),
                contentDescription = "Vybeee",
                modifier = Modifier.size(150.dp),
                contentScale = ContentScale.FillBounds
            )
            Spacer(Modifier.height(32.dp))
            Image(
                painter = painterResource(R.drawable.vybeee_wordmark),
                contentDescription = "Vybeee",
                modifier = Modifier.width(190.dp).height(56.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Your music. Your vibe.",
                fontSize = 22.sp,
                color = androidx.compose.ui.graphics.Color(0xFFA29AB8),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BrandHeader() {
    val useLightWordmark = MaterialTheme.colorScheme.onBackground.red > 0.8f
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(if (useLightWordmark) R.drawable.vybeee_brand_lockup else R.drawable.vybeee_brand_lockup_dark),
            contentDescription = "Vybeee",
            modifier = Modifier.width(150.dp).height(44.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun OnboardingScreen(onFinished: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(0) }
    val titles = listOf("Welcome to Vybeee", "Your music stays yours", "Ready to vibe")
    val bodies = listOf(
        "A clean offline music player built for your local library.",
        "Vybeee plays music stored on your device. Your library, favorites, playlists, and settings stay on your phone.",
        "Scan your music, pick a song, and enjoy background playback, queues, favorites, themes, and a sleep timer."
    )

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.vybeee_note_icon),
                contentDescription = "Vybeee",
                modifier = Modifier.size(96.dp),
                contentScale = ContentScale.FillBounds
            )
            Spacer(Modifier.height(28.dp))
            Text(
                titles[page],
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                bodies[page],
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { index ->
                    Surface(
                        Modifier.size(if (index == page) 24.dp else 8.dp, 8.dp),
                        shape = MaterialTheme.shapes.small,
                        color = if (index == page) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ) {}
                }
            }
            Spacer(Modifier.height(32.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (page > 0) {
                    OutlinedButton(
                        onClick = { page-- },
                        modifier = Modifier.weight(1f)
                    ) { Text("Back") }
                }
                Button(
                    onClick = {
                        if (page == 2) onFinished() else page++
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (page == 2) "Get started" else "Next")
                }
            }
            if (page < 2) {
                TextButton(onClick = onFinished) { Text("Skip") }
            }
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
    val mostPlayed = vm.mostPlayed(5)
    val recentlyAdded = songs.sortedByDescending { it.dateAdded }.take(5)
    val nowPlaying by vm.nowPlaying.collectAsState()

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
            if (nowPlaying != null) {
                Spacer(Modifier.height(22.dp))
                Text("Continue listening", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Card(Modifier.fillMaxWidth()) {
                    ListItem(
                        modifier = Modifier.clickable {
                            if (controller?.isPlaying == true) controller.pause() else controller?.play()
                        },
                        headlineContent = { Text(nowPlaying!!.title, maxLines = 1, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("${nowPlaying!!.artist} • ${nowPlaying!!.album}", maxLines = 1) },
                        leadingContent = {
                            Icon(Icons.Default.GraphicEq, null, Modifier.size(34.dp))
                        },
                        trailingContent = {
                            FilledIconButton(onClick = {
                                if (controller?.isPlaying == true) controller.pause() else controller?.play()
                            }) {
                                Icon(
                                    if (controller?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    "Play"
                                )
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("Quick access", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { go(Tab.SONGS) },
                    label = { Text("Songs") },
                    leadingIcon = { Icon(Icons.Default.MusicNote, null) }
                )
                AssistChip(
                    onClick = { go(Tab.FAVORITES) },
                    label = { Text("Favorites") },
                    leadingIcon = { Icon(Icons.Default.Favorite, null) }
                )
                AssistChip(
                    onClick = { go(Tab.PLAYLISTS) },
                    label = { Text("Playlists") },
                    leadingIcon = { Icon(Icons.Default.QueueMusic, null) }
                )
            }
            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Text("Recently played", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }

        items(recent, key = { "recent_${it.id}" }) { song ->
            SongRow(song, favorites.contains(song.id), { vm.toggleFavorite(song.id) }, vm.playCount(song.id)) {
                playSong(controller, song, songs)
                vm.recordPlayed(song.id)
            }
        }

        if (mostPlayed.isNotEmpty()) {
            item {
                Spacer(Modifier.height(22.dp))
                Text("Most played", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            items(mostPlayed, key = { "most_${it.id}" }) { song ->
                SongRow(song, favorites.contains(song.id), { vm.toggleFavorite(song.id) }, vm.playCount(song.id)) {
                    playSong(controller, song, songs)
                    vm.recordPlayed(song.id)
                }
            }
        }

        if (recentlyAdded.isNotEmpty()) {
            item {
                Spacer(Modifier.height(22.dp))
                Text("Recently added", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            items(recentlyAdded, key = { "added_${it.id}" }) { song ->
                SongRow(song, favorites.contains(song.id), { vm.toggleFavorite(song.id) }, vm.playCount(song.id)) {
                    playSong(controller, song, songs)
                    vm.recordPlayed(song.id)
                }
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
    val sort by vm.sort.collectAsState()
    var showSort by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { showSort = true }) { Icon(Icons.Default.Sort, "Sort") }
                DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                    SongSort.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(sortLabel(mode)) },
                            onClick = {
                                vm.setSort(mode)
                                showSort = false
                            },
                            trailingIcon = {
                                if (sort == mode) Icon(Icons.Default.Check, "Selected")
                            }
                        )
                    }
                }
            }
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
                    SongRow(song, favorites.contains(song.id), { vm.toggleFavorite(song.id) }, vm.playCount(song.id)) {
                        playSong(controller, song, songs)
                        vm.recordPlayed(song.id)
                    }
                }
            }
        }
    }
}

private fun sortLabel(mode: SongSort): String = when (mode) {
    SongSort.TITLE -> "Title"
    SongSort.ARTIST -> "Artist"
    SongSort.ALBUM -> "Album"
    SongSort.NEWEST -> "Newest added"
    SongSort.MOST_PLAYED -> "Most played"
}


@Composable
private fun SongArtwork(
    song: AudioSong,
    size: androidx.compose.ui.unit.Dp = 52.dp
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = song.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(song.uri)
                if (Build.VERSION.SDK_INT >= 29) {
                    context.contentResolver.loadThumbnail(
                        uri,
                        Size(256, 256),
                        CancellationSignal()
                    )
                } else {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        retriever.embeddedPicture?.let {
                            BitmapFactory.decodeByteArray(it, 0, it.size)
                        }
                    } finally {
                        retriever.release()
                    }
                }
            }.getOrNull()
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "${song.title} artwork",
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}

@Composable
private fun SongRow(
    song: AudioSong,
    favorite: Boolean,
    onFavorite: () -> Unit,
    playCount: Int = 0,
    onPlay: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onPlay),
        headlineContent = { Text(song.title, maxLines = 1) },
        supportingContent = {
            Text(
                if (playCount > 0) "${song.artist} • ${song.album} • $playCount plays"
                else "${song.artist} • ${song.album}",
                maxLines = 1
            )
        },
        leadingContent = { SongArtwork(song = song, size = 52.dp) },
        trailingContent = {
            IconButton(onClick = onFavorite) {
                Icon(
                    if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "Favorite"
                )
            }
        }
    )
}

@Composable
private fun AlbumsScreen(
    songs: List<AudioSong>,
    controller: MediaController?,
    vm: MusicLibraryViewModel,
    onOpenAlbum: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val albums = songs.groupBy { it.album }
        .toList()
        .sortedBy { it.first.lowercase() }
    val filteredAlbums = albums.filter { (album, tracks) ->
        query.isBlank() ||
            album.contains(query, ignoreCase = true) ||
            tracks.any { it.artist.contains(query, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Albums", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Browse your local music by album",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            singleLine = true,
            placeholder = { Text("Search albums or artists…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, "Clear search")
                    }
                }
            }
        )
        if (albums.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No albums yet.")
            }
        } else if (filteredAlbums.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No matching albums.")
            }
        } else {
            LazyColumn {
                items(filteredAlbums, key = { it.first }) { (album, tracks) ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clickable { onOpenAlbum(album) }
                    ) {
                        ListItem(
                            headlineContent = { Text(album, maxLines = 1) },
                            supportingContent = { Text("${tracks.first().artist} • ${tracks.size} songs") },
                            leadingContent = {
                                SongArtwork(song = tracks.first(), size = 64.dp)
                            },
                            trailingContent = {
                                IconButton(onClick = {
                                    playSong(controller, tracks.first(), tracks)
                                    vm.recordPlayed(tracks.first().id)
                                }) {
                                    Icon(Icons.Default.PlayArrow, "Play album")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistsScreen(
    songs: List<AudioSong>,
    controller: MediaController?,
    vm: MusicLibraryViewModel,
    onOpenArtist: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val artists = songs.groupBy { it.artist }
        .toList()
        .sortedBy { it.first.lowercase() }
    val filteredArtists = artists.filter { (artist, tracks) ->
        query.isBlank() ||
            artist.contains(query, ignoreCase = true) ||
            tracks.any { it.album.contains(query, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Artists", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Browse your local music by artist",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            singleLine = true,
            placeholder = { Text("Search artists or albums…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, "Clear search")
                    }
                }
            }
        )
        if (artists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No artists yet.")
            }
        } else if (filteredArtists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No matching artists.")
            }
        } else {
            LazyColumn {
                items(filteredArtists, key = { it.first }) { (artist, tracks) ->
                    ListItem(
                        modifier = Modifier.clickable { onOpenArtist(artist) },
                        headlineContent = { Text(artist, maxLines = 1) },
                        supportingContent = { Text("${tracks.size} songs") },
                        leadingContent = {
                            SongArtwork(song = tracks.first(), size = 64.dp)
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                playSong(controller, tracks.first(), tracks)
                                vm.recordPlayed(tracks.first().id)
                            }) {
                                Icon(Icons.Default.PlayArrow, "Play artist")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumDetailScreen(
    album: String,
    tracks: List<AudioSong>,
    favorites: Set<Long>,
    vm: MusicLibraryViewModel,
    controller: MediaController?,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("Album", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (tracks.isNotEmpty()) {
                        SongArtwork(song = tracks.first(), modifier = Modifier.size(190.dp), cornerRadius = 24.dp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(album, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (tracks.isNotEmpty()) {
                        Text(
                            "${tracks.first().artist} • ${tracks.size} songs",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            enabled = tracks.isNotEmpty(),
                            onClick = {
                                if (tracks.isNotEmpty()) {
                                    playSong(controller, tracks.first(), tracks)
                                    vm.recordPlayed(tracks.first().id)
                                }
                            }
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Play")
                        }
                        OutlinedButton(
                            enabled = tracks.isNotEmpty(),
                            onClick = {
                                if (tracks.isNotEmpty()) {
                                    val shuffled = tracks.shuffled()
                                    playSong(controller, shuffled.first(), shuffled)
                                    vm.recordPlayed(shuffled.first().id)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Shuffle, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Shuffle")
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
            items(tracks, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    favorite = favorites.contains(song.id),
                    onFavorite = { vm.toggleFavorite(song.id) },
                    playCount = vm.playCount(song.id)
                ) {
                    playSong(controller, song, tracks)
                    vm.recordPlayed(song.id)
                }
            }
        }
    }
}

@Composable
private fun ArtistDetailScreen(
    artist: String,
    tracks: List<AudioSong>,
    favorites: Set<Long>,
    vm: MusicLibraryViewModel,
    controller: MediaController?,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("Artist", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (tracks.isNotEmpty()) {
                        SongArtwork(song = tracks.first(), modifier = Modifier.size(190.dp), cornerRadius = 95.dp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(artist, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(
                        "${tracks.size} songs",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            enabled = tracks.isNotEmpty(),
                            onClick = {
                                if (tracks.isNotEmpty()) {
                                    playSong(controller, tracks.first(), tracks)
                                    vm.recordPlayed(tracks.first().id)
                                }
                            }
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Play")
                        }
                        OutlinedButton(
                            enabled = tracks.isNotEmpty(),
                            onClick = {
                                if (tracks.isNotEmpty()) {
                                    val shuffled = tracks.shuffled()
                                    playSong(controller, shuffled.first(), shuffled)
                                    vm.recordPlayed(shuffled.first().id)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Shuffle, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Shuffle")
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
            items(tracks, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    favorite = favorites.contains(song.id),
                    onFavorite = { vm.toggleFavorite(song.id) },
                    playCount = vm.playCount(song.id)
                ) {
                    playSong(controller, song, tracks)
                    vm.recordPlayed(song.id)
                }
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
            modifier = Modifier.clickable { go(Tab.GENRES) },
            headlineContent = { Text("Genres") },
            supportingContent = { Text("Browse local music by genre") },
            leadingContent = { Icon(Icons.Default.MusicNote, null) }
        )
        ListItem(
            modifier = Modifier.clickable { go(Tab.HISTORY) },
            headlineContent = { Text("History") },
            supportingContent = { Text("See your recently played songs") },
            leadingContent = { Icon(Icons.Default.History, null) }
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
private fun HistoryScreen(
    songs: List<AudioSong>,
    favorites: Set<Long>,
    vm: MusicLibraryViewModel,
    controller: MediaController?
) {
    val recentIds by vm.recent.collectAsState()
    val recentSongs = recentIds.mapNotNull { id -> songs.find { it.id == id } }
    var showClearDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("History", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Your recently played songs",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (recentSongs.isNotEmpty()) {
                TextButton(onClick = { showClearDialog = true }) {
                    Text("Clear")
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (recentSongs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null, Modifier.size(48.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("No listening history yet.")
                    Text(
                        "Songs you play will appear here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn {
                items(recentSongs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        favorite = favorites.contains(song.id),
                        onFavorite = { vm.toggleFavorite(song.id) },
                        playCount = vm.playCount(song.id)
                    ) {
                        playSong(controller, song, recentSongs)
                        vm.recordPlayed(song.id)
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear listening history?") },
            text = {
                Text("This removes your recently played list. Your favorites, playlists, and play counts will stay unchanged.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearRecentlyPlayed()
                    showClearDialog = false
                }) {
                    Text("Clear history")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private data class GenreGroup(
    val name: String,
    val songs: List<AudioSong>
)

@Composable
private fun GenresScreen(
    songs: List<AudioSong>,
    controller: MediaController?,
    vm: MusicLibraryViewModel
) {
    val context = LocalContext.current
    var groups by remember { mutableStateOf<List<GenreGroup>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(songs) {
        loading = true
        groups = withContext(Dispatchers.IO) {
            loadGenreGroups(context, songs)
        }
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Genres", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Browse your local music by genre",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            groups.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No genre information found in your local music.",
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                LazyColumn {
                    items(groups, key = { it.name }) { group ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clickable {
                                    val first = group.songs.firstOrNull() ?: return@clickable
                                    playSong(controller, first, group.songs)
                                    vm.recordPlayed(first.id)
                                }
                        ) {
                            ListItem(
                                headlineContent = { Text(group.name) },
                                supportingContent = {
                                    Text("${group.songs.size} ${if (group.songs.size == 1) "song" else "songs"}")
                                },
                                leadingContent = {
                                    Icon(Icons.Default.MusicNote, null)
                                },
                                trailingContent = {
                                    Icon(Icons.Default.PlayArrow, "Play ${group.name}")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun loadGenreGroups(
    context: android.content.Context,
    songs: List<AudioSong>
): List<GenreGroup> {
    if (songs.isEmpty()) return emptyList()

    val songsById = songs.associateBy { it.id }
    val resolver = context.contentResolver
    val groups = linkedMapOf<String, MutableList<AudioSong>>()
    val genresUri = MediaStore.Audio.Genres.getContentUri("external")

    resolver.query(
        genresUri,
        arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
        null,
        null,
        "${MediaStore.Audio.Genres.NAME} COLLATE NOCASE ASC"
    )?.use { genreCursor ->
        val idCol = genreCursor.getColumnIndex(MediaStore.Audio.Genres._ID)
        val nameCol = genreCursor.getColumnIndex(MediaStore.Audio.Genres.NAME)

        if (idCol >= 0 && nameCol >= 0) {
            while (genreCursor.moveToNext()) {
                val genreId = genreCursor.getLong(idCol)
                val name = genreCursor.getString(nameCol).orEmpty().trim()
                if (name.isBlank()) continue

                val membersUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                resolver.query(
                    membersUri,
                    arrayOf("audio_id"),
                    null,
                    null,
                    null
                )?.use { memberCursor ->
                    val audioIdCol = memberCursor.getColumnIndex("audio_id")
                    if (audioIdCol >= 0) {
                        while (memberCursor.moveToNext()) {
                            val audioId = memberCursor.getLong(audioIdCol)
                            songsById[audioId]?.let { song ->
                                groups.getOrPut(name) { mutableListOf() }.add(song)
                            }
                        }
                    }
                }
            }
        }
    }

    return groups.map { (name, tracks) ->
        GenreGroup(name, tracks.distinctBy { it.id })
    }.filter { it.songs.isNotEmpty() }
        .sortedBy { it.name.lowercase() }
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
    var showAddSongs by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val ids = remember(refresh, name, songs) { vm.playlistSongs(name) }
    val playlistSongs = ids.mapNotNull { id -> songs.find { it.id == id } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showAddSongs = true }) { Icon(Icons.Default.Add, "Add songs") }
            if (name != "My Playlist") {
                IconButton(onClick = { showRename = true }) { Icon(Icons.Default.Edit, "Rename playlist") }
            }
            if (playlistSongs.isNotEmpty()) {
                IconButton(onClick = {
                    val shuffled = playlistSongs.shuffled()
                    playSong(controller, shuffled.first(), shuffled)
                    vm.recordPlayed(shuffled.first().id)
                }) { Icon(Icons.Default.Shuffle, "Shuffle playlist") }
                IconButton(onClick = {
                    playSong(controller, playlistSongs.first(), playlistSongs)
                    vm.recordPlayed(playlistSongs.first().id)
                }) { Icon(Icons.Default.PlayArrow, "Play playlist") }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${playlistSongs.size} songs", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            if (playlistSongs.isNotEmpty()) {
                TextButton(onClick = { showClearConfirm = true }) { Text("Clear") }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (playlistSongs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("This playlist is empty.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showAddSongs = true }) { Text("Add songs") }
                }
            }
        } else {
            LazyColumn {
                items(playlistSongs, key = { it.id }) { song ->
                    val index = playlistSongs.indexOfFirst { it.id == song.id }
                    ListItem(
                        modifier = Modifier.clickable {
                            playSong(controller, song, playlistSongs)
                            vm.recordPlayed(song.id)
                        },
                        headlineContent = { Text(song.title, maxLines = 1) },
                        supportingContent = { Text("${song.artist} • ${song.album}", maxLines = 1) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    enabled = index > 0,
                                    onClick = {
                                        vm.movePlaylistSong(name, index, index - 1)
                                        refresh++
                                    }
                                ) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                                IconButton(
                                    enabled = index < playlistSongs.lastIndex,
                                    onClick = {
                                        vm.movePlaylistSong(name, index, index + 1)
                                        refresh++
                                    }
                                ) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                                IconButton(onClick = { vm.togglePlaylistSong(name, song.id); refresh++ }) {
                                    Icon(Icons.Default.RemoveCircleOutline, "Remove")
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddSongs) {
        AddSongsToPlaylistDialog(
            playlistName = name,
            songs = songs,
            vm = vm,
            onDismiss = { showAddSongs = false; refresh++ }
        )
    }
    if (showRename) {
        RenamePlaylistDialog(
            currentName = name,
            onDismiss = { showRename = false },
            onRename = { newName ->
                val renamed = vm.renamePlaylist(name, newName)
                if (renamed) showRename = false
                renamed
            }
        )
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear playlist?") },
            text = { Text("Remove all songs from $name? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearPlaylist(name)
                    showClearConfirm = false
                    refresh++
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AddSongsToPlaylistDialog(
    playlistName: String,
    songs: List<AudioSong>,
    vm: MusicLibraryViewModel,
    onDismiss: () -> Unit
) {
    var selected by remember(playlistName) { mutableStateOf(vm.playlistSongs(playlistName).toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add songs") },
        text = {
            if (songs.isEmpty()) {
                Text("No songs are available.")
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(songs, key = { it.id }) { song ->
                        val checked = song.id in selected
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (checked) {
                                    vm.togglePlaylistSong(playlistName, song.id)
                                    selected = selected - song.id
                                } else {
                                    vm.togglePlaylistSong(playlistName, song.id)
                                    selected = selected + song.id
                                }
                            }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Column(Modifier.weight(1f)) {
                                Text(song.title, maxLines = 1)
                                Text(song.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun RenamePlaylistDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Boolean
) {
    var name by remember { mutableStateOf(currentName) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename playlist") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = false },
                    singleLine = true,
                    label = { Text("Playlist name") },
                    isError = error
                )
                if (error) {
                    Text("Name is empty or already in use.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { error = !onRename(name) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun themeModeLabel(mode: String): String = when (mode) {
    "light" -> "Light"
    "dark" -> "Dark"
    else -> "System"
}

@Composable
private fun SettingsScreen(
    vm: MusicLibraryViewModel,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    onShowIntro: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        ListItem(
            headlineContent = { Text("Appearance") },
            supportingContent = { Text("${themeModeLabel(themeMode)} theme") },
            leadingContent = { Icon(Icons.Default.DarkMode, null) },
            trailingContent = {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { expanded = true }) {
                        Text("Change")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("system", "light", "dark").forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(themeModeLabel(mode)) },
                                onClick = {
                                    onThemeModeChange(mode)
                                    expanded = false
                                },
                                trailingIcon = {
                                    if (themeMode == mode) Icon(Icons.Default.Check, "Selected")
                                }
                            )
                        }
                    }
                }
            }
        )
        ListItem(
            headlineContent = { Text("Welcome to Vybeee") },
            supportingContent = { Text("Replay the quick introduction and learn the main features") },
            leadingContent = { Icon(Icons.Default.Info, null) },
            trailingContent = {
                TextButton(onClick = onShowIntro) { Text("Show") }
            }
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
        Text("Vybeee v1.14.0", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Offline music player", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MiniPlayer(song: AudioSong, controller: MediaController?, onOpen: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onOpen),
        shadowElevation = 8.dp,
        tonalElevation = 3.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SongArtwork(
                song = song,
                modifier = Modifier.size(48.dp),
                cornerRadius = 12.dp
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, maxLines = 1, fontWeight = FontWeight.SemiBold)
                Text(song.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { controller?.seekToPreviousMediaItem() }) {
                Icon(Icons.Default.SkipPrevious, "Previous")
            }
            IconButton(onClick = {
                if (controller?.isPlaying == true) controller.pause() else controller?.play()
            }) {
                Icon(
                    if (controller?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Play"
                )
            }
            IconButton(onClick = { controller?.seekToNextMediaItem() }) {
                Icon(Icons.Default.SkipNext, "Next")
            }
        }
    }
}

@Composable
private fun SongArtwork(
    song: AudioSong,
    modifier: Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp
) {
    val context = LocalContext.current
    var bitmap by remember(song.uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(song.uri) {
        bitmap = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(song.uri))
                retriever.embeddedPicture?.let { bytes ->
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            } catch (_: Exception) {
                null
            } finally {
                try { retriever.release() } catch (_: Exception) { }
            }
        }
    }

    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius),
        tonalElevation = 6.dp
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "Album artwork for ${song.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(R.drawable.vybeee_note_icon),
                contentDescription = "Vybeee artwork fallback",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }
    }
}

@Composable
private fun FullPlayerScreen(
    song: AudioSong,
    controller: MediaController?,
    playerState: PlayerUiState,
    songs: List<AudioSong>,
    vm: MusicLibraryViewModel,
    sleepTimerEnd: Long?,
    onSleepTimerClick: () -> Unit,
    onClearQueueClick: () -> Unit,
    onClose: () -> Unit
) {
    val favorite = vm.favorites.collectAsState().value.contains(song.id)
    val position = playerState.position.coerceIn(0L, max(1L, playerState.duration))
    val duration = max(1L, playerState.duration)
    val queueCount = playerState.itemCount

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close player") }
            Text("Now Playing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSleepTimerClick) {
                Icon(
                    Icons.Default.Timer,
                    if (sleepTimerEnd != null) "Sleep timer set" else "Sleep timer",
                    tint = if (sleepTimerEnd != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = {
                controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
            }) {
                Icon(
                    Icons.Default.Shuffle,
                    "Shuffle",
                    tint = if (playerState.shuffle) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(30.dp))
        SongArtwork(
            song = song,
            modifier = Modifier.size(250.dp),
            cornerRadius = 28.dp
        )

        Spacer(Modifier.height(28.dp))
        Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2)
        Text(song.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(song.album, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(18.dp))
        Slider(
            value = position.toFloat(),
            onValueChange = { controller?.seekTo(it.toLong()) },
            valueRange = 0f..duration.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(position))
            Text(formatDuration(playerState.duration))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                controller?.let {
                    it.repeatMode = when (it.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }
            }) {
                Icon(
                    if (playerState.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    "Repeat",
                    tint = if (playerState.repeatMode != Player.REPEAT_MODE_OFF)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { controller?.seekToPreviousMediaItem() }) {
                Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(42.dp))
            }
            FilledIconButton(
                onClick = {
                    if (playerState.isPlaying) controller?.pause() else controller?.play()
                },
                modifier = Modifier.size(68.dp)
            ) {
                Icon(
                    if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Play",
                    Modifier.size(34.dp)
                )
            }
            IconButton(onClick = { controller?.seekToNextMediaItem() }) {
                Icon(Icons.Default.SkipNext, "Next", Modifier.size(42.dp))
            }
            IconButton(onClick = { vm.toggleFavorite(song.id) }) {
                Icon(
                    if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "Favorite"
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        if (queueCount > 0) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Queue • ${playerState.index + 1}/$queueCount",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClearQueueClick) {
                    Text("Clear queue")
                }
            }
            Spacer(Modifier.height(6.dp))
            QueueList(controller, playerState.index)
        } else {
            Text(
                "No queue loaded",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ClearQueueDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear queue?") },
        text = {
            Text("This will stop playback and remove all songs from the current queue.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Clear queue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SleepTimerDialog(
    currentEnd: Long?,
    onDismiss: () -> Unit,
    onSetMinutes: (Long) -> Unit,
    onCancel: () -> Unit
) {
    val remainingMinutes = currentEnd?.let {
        ((it - System.currentTimeMillis()).coerceAtLeast(0L) + 59_999L) / 60_000L
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (remainingMinutes != null) {
                    Text("Current timer: $remainingMinutes min remaining")
                    HorizontalDivider()
                }
                listOf(15L, 30L, 45L, 60L, 90L).forEach { minutes ->
                    TextButton(
                        onClick = { onSetMinutes(minutes) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Stop after $minutes minutes")
                    }
                }
                if (currentEnd != null) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel timer")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun QueueList(controller: MediaController?, currentIndex: Int) {
    val count = controller?.mediaItemCount ?: 0
    if (count == 0) return

    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 260.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        items((0 until count).toList(), key = { it }) { index ->
            val item = controller?.getMediaItemAt(index)
            val title = item?.mediaMetadata?.title?.toString() ?: "Unknown title"
            val artist = item?.mediaMetadata?.artist?.toString() ?: "Unknown artist"
            ListItem(
                modifier = Modifier.clickable {
                    controller?.seekTo(index, 0L)
                    controller?.play()
                },
                headlineContent = {
                    Text(
                        title,
                        maxLines = 1,
                        fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal
                    )
                },
                supportingContent = { Text(artist, maxLines = 1) },
                leadingContent = {
                    Icon(
                        if (index == currentIndex) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                        null
                    )
                },
                trailingContent = {
                    Row {
                        IconButton(
                            enabled = index > 0,
                            onClick = { controller?.moveMediaItem(index, index - 1) }
                        ) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                        IconButton(
                            enabled = index < count - 1,
                            onClick = { controller?.moveMediaItem(index, index + 1) }
                        ) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                    }
                }
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun playSong(controller: MediaController?, song: AudioSong, queue: List<AudioSong>) {
    controller ?: return
    if (queue.isEmpty()) return

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
