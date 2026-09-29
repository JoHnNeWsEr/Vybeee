# Vybeee v1.7.0

Vybeee is an offline-first Android music player. Your music stays on your device; there is no account, server, streaming service, or cloud upload.

## v1.5.0
- Songs library with local MediaStore scanning and refresh
- Search songs by title, artist, album, or folder
- Albums and artists library views
- Favorites stored locally
- Recently played history stored locally
- Folder-based music browsing
- Local playlists: create, open, play, add/remove songs, and delete
- Media3 background playback
- Queue playback when starting a song, album, artist, folder, or playlist
- Mini-player with previous / play-pause / next controls
- Android media-session foundation for lock-screen / notification controls
- Local privacy-focused settings
- Empty-library and permission-friendly states

Planned next phases include richer artwork, a full player screen, queue controls, sorting/filtering, sleep timer, onboarding polish, and advanced audio features where reliable.

## Release signing
GitHub Actions builds a release APK using a persistent private keystore stored in GitHub Actions secrets. The keystore itself is never committed to the repository.


## v1.5.0
- Full now-playing screen with seek, previous/next and queue.
- Shuffle and repeat off/all/one.
- Recently played and persistent play counts.
- Most Played home section.
- Song sorting by title, artist, album, newest, and most played.
- Improved mini-player and playback state updates.


## v1.5.0
- Playlist add/remove songs from a dedicated picker
- Playlist rename and clear
- Playlist song reordering
- Shuffle playlists
- Queue item reordering in the full player
- Updated automatic updater and release version


## v1.5.0
- Added a sleep timer with 15, 30, 45, 60, and 90 minute options.
- Timer can be cancelled and shows its active state in the full player.

## v1.7.0
- Added persistent Appearance settings: System, Light, and Dark themes.
- Theme changes apply immediately and remain after restarting Vybeee.


## v1.10.0
- Added a Continue Listening card on Home for the current song.
- Added a Recently Added section on Home using local MediaStore date information.
- Added quick play controls for Continue Listening.
- Kept all v1.7.0 onboarding, theme, playback, playlist, favorite, queue, and sleep timer features intact.


## 1.10.0 — Vybeee Branding
- Added the Vybeee note-swirl visual identity to the launcher and app header.
- Added a branded launch splash with the tagline “Your music. Your vibe.”
- Added Fredoka SemiBold for the Vybeee wordmark.
- Updated the dark palette around the brand background `#14101E`.
- Existing music-library and playback features are preserved.


## v1.18.0
- Added a dedicated History screen for recently played songs.
- Added a Clear History action with confirmation.
- Clearing history does not remove favorites, playlists, or play counts.
- Added History to the More section.
- Updated the project updater default and in-app version label to 1.18.0.


## 1.18.0
- Album and artist detail browsing
- Album/artist artwork and quick play
- Play and shuffle controls on detail screens


## v1.19.0
- Added folder search across folder names, song titles, artists, and albums.
- Added clear-search control and empty-result state for Folders.
- Updated Home tagline to “Your music. Your vibe.”


## v1.20.0
- Added playlist search by playlist name.
- Added clear-search control and matching-playlist count.
- Added an empty-result state for playlist search.
- Preserved playlist playback, editing, reordering, and persistence.


## v1.25.0
- Added dedicated Favorites search by song title, artist, and album.
- Added filtered Favorites count and empty states.
- Added clear-search control and shuffle favorites.
- Preserved offline playback and existing library features.


## v1.26.0
- Added Queue Preset search by preset name.
- Added matching-preset counts and empty/no-match states.
- Added clear-search control.
- Added confirmation before deleting a queue preset.
- Preserved queue preset creation, loading, shuffle, rename, and local persistence.
- Began the Phase 1–2 combined roadmap approach while keeping updates incremental and testable.
