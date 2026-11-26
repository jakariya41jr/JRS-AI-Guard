# JRS AI Guard

**Package Name:** `com.JRS.AI.Guard`  
**Developer:** Md Jakariya Hasan  

**JRS AI Guard** is a dual-mode (Rooted & Non-Rooted) Android security, system watchdog, and AI monitoring suite built with Kotlin and Jetpack Compose.

## Key Capabilities
- **Dual-Mode Architecture:** Real `su` / Magisk / KernelSU detection and root command execution with an automatic Non-Root Safety Net fallback (`UsageStatsManager`, `CameraManager`, `AudioManager`, `ClipboardManager`, `NetworkStatsManager`, `PackageManager`).
- **Exact Timestamp & Duration Timelines:** Tracks permission sessions, background CPU/RAM/battery spikes, root behavior, gallery/data exposure, sensor leases (with screen-off Camera/Mic alerts), clipboard/overlay access, and install/update diffs.
- **Free API Malware & APK Scanner:** Computes local SHA-256 hashes, enforces 24-hour local cache quota conservation, performs static DEX/archive heuristics, and checks MalwareBazaar free threat intelligence.
- **Encrypted Local Vault & Snapshots:** Uses Android Keystore AES-256-GCM (`CryptoVault`) to store user-configured Gemini API keys and point-in-time suspicious activity snapshots, with JSON/CSV/Text export.
- **Restricted In-App AI Assistant & Scheduled Digests:** WorkManager-scheduled Daily/Weekly/Event-Triggered digests using compact privacy-filtered telemetry and a strictly scoped JRS AI Guard assistant.
- **GitHub Releases APK Updater:** Built-in GitHub Releases API check and `DownloadManager` APK release downloader.

## Building & Releasing via GitHub
1. Push this repository to GitHub.
2. Tag a release (e.g., `v1.0.0`) or run the GitHub Actions workflow `.github/workflows/android-release.yml` to build and attach the APK to GitHub Releases.
3. Configure your own Gemini API key directly inside **AI Guard → Vault & Schedule** after installing the APK.
