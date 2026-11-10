# Analog TV Launcher

Android TV / Android box ke liye launcher: Analog TV PIP (live stream), weather + analog clock widgets,
5 glass theme colours, PIP Size (−/+), YouTube / Play Store / Player / Settings / All Apps tiles.

## GitHub par APK banane ke steps
1. GitHub par naya repository banayein (e.g. `AnalogTV`).
2. Is poore folder ki files upload/push karein (folder structure same rahe: `app/`, `.github/`, `build.gradle.kts` ...).
3. Repo me **Actions** tab kholein → **Build APK** → **Run workflow** (ya `main` branch par push karte hi khud chalega).
4. 4-6 minute baad run ke andar **Artifacts → AnalogTV-apk** download karein (zip ke andar `app-debug.apk`).
5. APK ko TV/box me install karein, phir Home dabane par "Always" select karke default launcher banayein
   (ya app ke Info/Settings se "Default launcher").

## Customize
* Live channel: PIP par long-press (OK button dabaye rakhein) → stream link (HLS `.m3u8` / mp4) paste karein,
  ya `Config.kt` me `DEFAULT_STREAM_URL` badlein.
* Weather city: weather card par click. Cities `WeatherRepo.kt` me add kar sakte hain.
* Awaaz: top-right gear → "PIP awaaz".
