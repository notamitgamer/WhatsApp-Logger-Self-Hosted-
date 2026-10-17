# Changelog

All notable changes to this project are listed here, newest first.
Upgrading? Read the **Upgrade notes** under each version.

## v4.3.0

### Changed
- The frontend `index.html` was split into `index.html` + `css/` + `js/` files. The code itself is unchanged; it was only moved into smaller files.
- The Render URL setting (`RENDER_BACKEND_URL`) now lives in `js/config.js`, marked with a highlighted banner.
- The service worker now caches the new files and uses new cache names so installed PWAs update.
- Removed redundant comments from the frontend code.
- `package.json` version synced to 4.3.0 (it was still `1.0.0`).
- README and docs updated for the new frontend layout.

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
