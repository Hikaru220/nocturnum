# Nocturnum

Open-source Minecraft launcher with its own Fabric HUD mod. **Early alpha** — it works end to end, but several items of the
specification are still open (see the roadmap below). GPL-3.0-or-later.

<p align="center"><img src="brand/nocturnum-icon-256.png" width="128" alt="Nocturnum"></p>

## What is inside

| Part | Stack | Where |
|---|---|---|
| Launcher | Tauri 2, Rust, React + TypeScript + Tailwind, skinview3d | `src-tauri/`, `src/` |
| HUD mod | Java, Fabric, Mixin, shared sources + per-version platform layer | `mod/` |
| Remote config | Discord button links, server blacklist for the *Anarchy* category | `config/` |

### Launcher
- Versions **1.12.2 → 26.3** (release / snapshot / legacy), loaders **Vanilla, Fabric, Quilt, NeoForge, Forge**; SHA-1 verified parallel downloads, shared library/asset cache, automatic Temurin Java.
- Modrinth catalogue (search with facets, install with required dependencies, update by hash), `.mrpack` import/export, CurseForge zip import, protected files on modpack update.
- Microsoft (device code) and offline accounts, tokens in the OS credential store — see [docs/MICROSOFT_AUTH.md](docs/MICROSOFT_AUTH.md).
- Settings preset written to `options.txt` before the first start (Russian, GUI scale 2, music off, graphics tier by hardware), smart memory, G1 flags, discrete GPU option, high process priority.
- Crash helper (out of memory, wrong Java, mod conflicts…), game log tab, Discord Rich Presence, tray, Ctrl+K search, drag & drop, skin editor (2D + painting directly on the 3D model, mirror, layers, undo, PNG export, upload).
- Russian and English UI, dark/light theme, accent colour, keyboard navigation, reduced-motion support.

### HUD mod (`mod/`, Minecraft **1.21.11**, **26.2** and **26.3**)
- ClickGUI (Right Shift): columns per category, instant apply, search, favourites, profiles (share code), themes, Ctrl+Z, tooltips in Russian with the English name.
- HUD layout editor (drag with the mouse, snapping, wheel = scale), 25+ HUD elements, notifications, own loading screen, main menu, pause-menu button.
- Effects: Jump Circles, Trails, China Hat, BlockOverlay, hit effects, custom crosshair, zoom, sprint-FOV, low fire, no hurt shake, Freelook, local weather, colour grade and motion blur (vanilla post-processing, off with Iris shader packs), leave protection, chat timestamps, scoreboard/tab editors, screenshot plate, TNT timer, waypoints, friends, media player, UI/hit/kill sounds, toggle sprint, boost mode, background FPS limit.
- Category **Anarchy** (off by default, per-server profiles, blacklist downloaded by the launcher): Fullbright, ESP, Target ESP, hitboxes, minimap. The mod never hides itself from moderators.
- Rule of the mod: it displays information and decorates *your* screen; it never presses anything for you. Command binds send chat commands only when you press the key (max 1/s, confirmation for dangerous commands).

## Build

```bash
npm install
npm run tauri build            # needs Rust (MSVC on Windows)
```
HUD mod (JDK 25 runs Gradle; targets Java 21 / 25):
```bash
cd mod && ./gradlew buildAll      # jars in mod/v*/build/libs
python tools/collect_jars.py      # copy into src-tauri/resources/hud for the launcher
```
Developer tools: `mod/tools/shot.sh <mc> <flags> [hold]` launches a throw-away instance and prints the newest in-game screenshot
(`world,clickgui,shot`, `world,editor,shot`, `world,fx,shot`).

## Roadmap (not done yet)
- Render-type features: glass hands, enchantment glint colour, 3D second skin layer, Ambience (sky colour / time of day).
- Scoreboard "hide red numbers", tab list ping digits and heads, long chat history / duplicate folding, screenshot upload to our own storage (needs a server).
- Media player still needs a live check with Yandex Music / Spotify / VK; Linux (MPRIS) and macOS are not implemented.
- Signed installer and auto-update (needs a code-signing certificate and an update key); CurseForge downloads need `CURSEFORGE_API_KEY`.
- Mod ports to the versions between 1.12.2 and 1.21.10 (the launcher already runs them; the HUD mod exists for 1.21.11, 26.2 and 26.3).
- Server blacklist for the Anarchy category only contains an example entry; fill it from each server's official rules.

Measured on a dev PC: installer 3.3 MB, window appears in ~0.25 s, launcher ~40 MB + WebView2 ~115 MB.

## Legal
Minecraft is a trademark of Mojang/Microsoft; this project is not affiliated with them. Optimisation mods (Sodium, Lithium, …) are
**downloaded from Modrinth** on installation and never redistributed here; check their licences. Fonts: Manrope (SIL OFL) — `mod/common/src/main/resources/assets/nocturnum/font/OFL.txt`.
