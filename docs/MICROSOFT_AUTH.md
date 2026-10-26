# Microsoft sign-in (licensed accounts)

Nocturnum signs in with the standard device-code flow: Microsoft → Xbox Live → XSTS → Minecraft Services.
Refresh tokens are stored in the OS credential store (Windows Credential Manager / Keychain), never in JSON files.

## You need your own Azure app

Minecraft Services rejects unknown apps with `Invalid app registration`, so the launcher ships **without** a client ID.

1. Open the [Azure portal → App registrations](https://portal.azure.com/#view/Microsoft_AAD_RegisteredApps/ApplicationsListBlade) and create an app.
   - Supported account types: *Personal Microsoft accounts only* (or "any tenant + personal").
   - Authentication → *Allow public client flows*: **Yes**.
2. Copy the *Application (client) ID*.
3. Ask Mojang to approve the app: <https://aka.ms/AppRegInfo> (form "Minecraft API access"). Approval can take days.
4. In the launcher: **Settings → Discord → Azure Client ID** — paste the ID. Sign in from **Accounts**.

Until the app is approved, sign-in ends with the message "Microsoft app is not approved by Mojang".
Offline profiles (singleplayer / LAN only) require at least one licensed account, like in Prism Launcher.

## Discord Rich Presence

Create an application in the [Discord Developer Portal](https://discord.com/developers/applications), upload the images
`nocturnum` (launcher logo) and paste the Application ID into **Settings → Discord**. The two profile buttons
("Скачать Nocturnum", "Discord-сервер") come from [`config/links.json`](../config/links.json) in this repository, so they
can change without a launcher update. Discord only shows your own buttons to *other* people — test with a second account.
