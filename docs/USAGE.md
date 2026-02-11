# Usage

## Desktop App
1. Launch with `./gradlew :desktop-app:run`.
2. Accept the disclaimer.
3. Paste a video or playlist URL.
4. Choose quality and format.
5. Download video or playlist.

### Desktop Release Builds
- macOS: `./gradlew packageMacReleaseArtifacts`
- Windows: `./gradlew packageWindowsReleaseArtifacts`
- Linux: `./gradlew packageLinuxReleaseArtifacts`

Run each command on the matching host OS. Gradle stages the DMG/MSI/DEB installer plus the CLI ZIP inside `build/publish/<os>/`, so you can upload those artifacts directly to a release page.

## CLI
```
./gradlew :cli:run --args="--url https://www.youtube.com/watch?v=... --format mp3"
./gradlew :cli:run --args="--url https://www.youtube.com/playlist?list=... --playlist"
./gradlew :cli:run --args="--engine ktor --url https://www.youtube.com/watch?v=... --quality sd_360 --format mp4"
./gradlew :cli:run --args="--engine ktor --url https://www.youtube.com/playlist?list=... --playlist --playlist-max 3 --quality sd_360 --format mp4"
```

## Output
Downloads are saved to `~/Downloads/YouTubeDownloader` by default. Use `--output` to override.
