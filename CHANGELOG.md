# Changelog

All notable changes to this project are listed here, newest first.
Upgrading? Read the **Upgrade notes** under each version.

## v4.5.7

Hotfix for WhatsApp device linking (both QR code and phone number).

### Fixed
- **QR code and phone number linking failing after pairing.** When WhatsApp asked the backend to restart right after a scan or code entry, the server reconnected before the 20-second auth save debounce had written the new session to Firestore—causing the fresh credentials to be lost and WhatsApp to time out with *"Couldn't log in, check your internet connection"*. The backend now flushes the auth snapshot to Firestore before restarting the socket, and ensures any concurrent credential updates finish saving first.
- **Phone number pairing rejected with *"Couldn't link device"*.** Switched the WhatsApp Web client identifier from a custom label to a standard browser profile (`Ubuntu / Chrome`), which WhatsApp's pairing servers require for 8-character pairing codes.

### Upgrade notes
1. Redeploy the backend.
2. If a previous failed attempt left a partial session behind, click **Relink device** on the status page (or clear `auth_store` in Firestore) and link again.

## v4.5.6

The big one: deleted and edited messages are now logged, and the backend no longer eats your Firestore quota.

### Added
- **Deleted messages.** When someone deletes a message for everyone, the logger keeps the original text and marks it as deleted. The Android app shows it in red with a "Deleted for everyone" label and has a Deleted filter.
- **Edit history.** The original message and every later edit are kept, in order. WhatsApp's encrypted edits are decrypted on the server.
- **Non-text messages.** Photos, videos, documents, voice notes, stickers, polls, contacts and locations are logged as readable placeholders such as `[--- Image ---]` or `[--- Document: report.pdf ---]`, plus the caption if there is one. View-once and disappearing messages are unwrapped.
- **Link with a phone number.** Besides the QR code you can now link WhatsApp with an 8-character pairing code. The QR page auto-refreshes and redirects when linking finishes.
- **Relink device** button on the status page, and a **Delete old whatsapp_auth data** button for people upgrading from an older version.
- Group names are fetched and shown. Chats can be renamed (`POST /api/rename`).
- Formal login, status, health and logs pages. `/health` shows cache, queue, snapshot, quota and memory use. `/api/health` returns the same as JSON and includes the backend `version`.
- Android: **Settings → About → Backend version** shows which backend version the app is talking to.

### Changed
- **Far fewer Firestore reads and writes.** Chats and messages are kept in memory and saved as a compressed snapshot, so a restart costs about 2 reads instead of tens of thousands. Writes are queued, de-duplicated and kept on disk, so nothing is lost while Firestore is unreachable or over quota.
- **WhatsApp login is stored as one compressed document** (`auth_store/session`) instead of thousands of tiny documents. A fresh link takes seconds because the server restarts straight after pairing.
- Security: signed login tokens, rate-limited login, security headers, optional `ALLOWED_ORIGINS` and `TOKEN_SECRET` variables. The server refuses to run without `AUTH_USER` and `AUTH_PASS`.

### Fixed
- Deleting a message that was edited first is now recorded.
- Edits reach the app even if it already synced the original message.
- A failed relog or logout no longer leaves the server without a WhatsApp connection.

### Upgrade notes (action required)
1. Redeploy the backend. Optionally add `TOKEN_SECRET` (any long random string) and `ALLOWED_ORIGINS` in Render.
2. **Scan the QR code (or use a phone number) once more.** The login moved to a new place, so the old one is not picked up.
3. Everyone has to **sign in again**, including the app, because the login token format changed.
4. Open `/logs`. If you see **"Catch-up skipped"** with a link, open the link and click **Create index** in Firebase. This is a one-time step.
5. Optional: on the status page click **Delete old whatsapp_auth data**. Run it when your Firestore quota is healthy, because it uses about one read and one write per old document.
6. Keep the [UptimeRobot ping](README.md#step-6-keep-it-alive-uptimerobot) running so Render's free tier doesn't sleep.

## v4.3.0

### Changed
- The frontend `index.html` was split into `index.html` + `css/` + `js/` files. The code itself is unchanged; it was only moved into smaller files.
- The Render URL setting (`RENDER_BACKEND_URL`) now lives in `js/config.js`, marked with a highlighted banner.
- The service worker now caches the new files and uses new cache names so installed PWAs update.
- Removed redundant comments from the frontend code.
- `package.json` version synced to 4.3.0 (it was still `1.0.0`).
- README and docs updated for the new frontend layout.

### Fixed
- The logo, web app manifest and service worker are now loaded from the files that ship in the repo (`public/icon.svg`, `manifest.json`, `sw.js`). The page used to ask for `/assets/...` paths that only existed on one host, so installing as an app and offline mode did not work on plain forks.
- `manifest.json` `start_url` and the service worker file list no longer contain placeholder text that had to be edited by hand.

### Upgrade notes (action required)
1. In your fork, click **Sync fork** -> **Update branch**.
2. If GitHub reports a conflict on `index.html` (because you edited your Render URL there before), discard your fork's change and continue. That edit only contained your URL. If you also made other edits to `index.html`, copy them somewhere first.
3. Open `js/config.js` in your fork, paste your Render URL, and commit. Your host redeploys on its own.
4. Future updates won't conflict, because `js/config.js` is the only file you edit.

Not using a fork? Upload `index.html`, `css/`, `js/`, `sw.js` and `manifest.json` to your host and set the URL in `js/config.js`.

See [Step 4](README.md#step-4-setup-frontend-the-viewer) in the README.

## v4.2.3

### Fixed
- Chat list order is now stable when chats have the same activity time.
- A chat no longer renders blank when the local cache is empty.
- Minor theme color tweaks for error text.

## v4.2.2

### Added
- Real-time sync now covers every chat, not just the one you have open.
- `Hard Reset` (`Settings` -> refresh icon) now does a full chat-by-chat resync with real progress.

## v4.2.1

### Security
- Removed the public Firebase Web SDK config from the frontend. The frontend now only talks to your Render backend.

### Upgrade notes (action required)
- Update your Firestore rules to deny all direct client access. See [Step 1](README.md#step-1-firebase-setup-the-database).

## Earlier

- UI refresh: three selectable themes (Catppuccin Latte, Catppuccin Mocha, true-black AMOLED) under `Settings` -> `Appearance` -> `Theme`, replacing the old dark-mode toggle and chat-background picker.
