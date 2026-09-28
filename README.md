# Vybeee v1.2.0

Vybeee is an offline-first Android music player. Your music stays on your device; there is no account, server, streaming service, or cloud upload.

## v1.2.0
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
