# Nocturnum

Open-source Minecraft launcher (Tauri 2 + Rust + React) with its own Fabric HUD mod. GPL-3.0.

**Status: work in progress (early alpha).**

- `src-tauri/` — Rust core: versions 1.12.2 → 26.3, Fabric/Quilt/NeoForge/Forge, Java auto-download, Modrinth, .mrpack, Microsoft/offline accounts, Discord RPC.
- `src/` — launcher UI (RU/EN), skin editor.
- `mod/` — Fabric HUD mod (1.21.11 working; 26.3 port pending): HUD elements, ClickGUI, layout editor, own loading screen.
- `config/links.json` — remote links for Discord buttons.

Build: `npm install && npm run tauri build` (needs Rust + MSVC). Mod: `mod/tools/sync-hud.sh` (JDK 25).
Microsoft sign-in needs your own Azure Client ID approved by Mojang (Settings → Discord/advanced).
