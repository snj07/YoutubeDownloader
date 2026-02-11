
# YouTube Downloader

Small Kotlin Multiplatform tool (Compose Desktop + CLI) to download YouTube videos and playlists.


## Features
- Desktop app (Compose Desktop) and CLI
- Shared Kotlin Multiplatform core (domain/data/engine/parser)
- Downloads YouTube videos and playlists using a fast, native Kotlin (ktor) engine by default
- Optionally supports `yt-dlp` as a fallback/secondary mode if the native engine fails due to YouTube changes
- Audio extraction and media conversion require `ffmpeg` (mandatory for all modes)


## Download

Prebuilt binaries are available on the [GitHub Releases page](https://github.com/snj07/YoutubeDownloader/releases).

- macOS: `.dmg`
- Windows: `.msi`
- Linux: `.deb`

### Install
1. Download the appropriate file for your operating system.
2. Install it:
   - macOS: open the `.dmg` and drag the app to Applications.
   - Windows: run the `.msi` and follow the setup wizard.
   - Linux: install the `.deb` with your package manager.
3. Launch the app and start downloading.

## Optional tools

The app works without `yt-dlp`. If you install it, you can enable the `yt-dlp` engine in Settings as a fallback if the default engine fails. 

**Note:** `ffmpeg` is required for all downloads and features, regardless of the engine you use.

### macOS (Homebrew)

```bash
# Install what you need
brew update
brew install yt-dlp ffmpeg
```

**Note for macOS users:**
If you see a warning that the app "cannot be opened because Apple cannot check it for malicious software":

- Right‑click the app → Open → Open (bypasses the warning once), **or**
- Go to System Settings → Privacy & Security → "Open Anyway" (after attempting to open the app).



### macOS (pip fallback for `yt-dlp`)

```bash
python3 -m pip install -U yt-dlp
which yt-dlp
```



### Windows

Use one of the common package managers.

```powershell
# Scoop (user install)
scoop install yt-dlp ffmpeg
```

```powershell
# Chocolatey (admin)
choco install yt-dlp ffmpeg
```

![App Screenshot](screenshots/image0.png)

### Linux (Debian/Ubuntu) (NOT TESTED)

```bash
# Install what you need
sudo apt update
sudo apt install -y ffmpeg

# yt-dlp via pip (optional) or your distro package
python3 -m pip install -U yt-dlp
```


### Other Linux distributions

- Use the native package manager (dnf, pacman, zypper) to install `ffmpeg` if available.
- Install `yt-dlp` via `pip` or your distro's package if provided.


## Binaries and paths

You can use system `yt-dlp`/`ffmpeg` or point the app to custom binaries in Settings. If you do not install `yt-dlp`, the app will use the built-in engine.





## Quickstart

### Desktop
1. Run the desktop app:

```bash
./gradlew :desktop-app:run
```
2. Accept the disclaimer, paste a video or playlist URL, choose quality/format, and download.

### CLI

```bash
./gradlew :cli:run --args="--url https://www.youtube.com/watch?v=... --format mp3"
```

Preview a playlist:

```bash
./gradlew :cli:run --args="--url https://www.youtube.com/playlist?list=... --playlist"
```


## Build

```bash
./gradlew clean build
```



## Packaging

Run the packaging tasks on the matching OS to generate native installers/binaries.


### macOS (DMG)

```bash
./gradlew :desktop-app:packageDmg
```



### Windows (Installer)

```bash
./gradlew :desktop-app:packageWindowsInstaller
```


### Linux (AppImage / package)

```bash
./gradlew :desktop-app:packageLinuxAppImage
# or packageLinuxDeb / packageLinuxTar depending on your nativeDistributions config
```



## Outputs and defaults
- Default download folder: `~/Downloads/YouTubeDownloader`

## CLI paths
- Configure paths in the Settings dialog in the desktop app or pass CLI flags `--yt-dlp-path` / `--ffmpeg-path`.


## Contributing
See `CONTRIBUTING.md` for development, testing, and PR guidance.


## Security
- Do not commit API keys or secrets. The project accepts an optional innertube API key via configuration; prefer environment variables or CI secrets.


## License
This project is licensed under the Apache License 2.0 — see `LICENSE`.

