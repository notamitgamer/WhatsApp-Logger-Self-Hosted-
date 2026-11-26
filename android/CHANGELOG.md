# Android app changelog

Newest first. The backend changelog lives in the main repository.

## v4.5.7

Hotfix for WhatsApp device linking (both QR code and phone number).

### Fixed
- **QR code and phone number linking failing after pairing.** When WhatsApp asked the backend to restart right after a scan or code entry, the server reconnected before the 20-second auth save debounce had written the new session to Firestore—causing the fresh credentials to be lost and WhatsApp to time out with *"Couldn't log in, check your internet connection"*. The backend now flushes the auth snapshot to Firestore before restarting the socket, and ensures any concurrent credential updates finish saving first.
- **Phone number pairing rejected with *"Couldn't link device"*.** Switched the WhatsApp Web client identifier from a custom label to a standard browser profile (`Ubuntu / Chrome`), which WhatsApp's pairing servers require for 8-character pairing codes.

### Upgrade notes
1. Redeploy the backend.
2. If a previous failed attempt left a partial session behind, click **Relink device** on the status page (or clear `auth_store` in Firestore) and link again.

## v4.5.6

Works with backend v4.5.5 and newer (older backends still work, but deleted/edited messages need the new one).

### Added
- Deleted messages show in red with a "Deleted for everyone" label, plus a Deleted filter on the chat list.
- Every edit is shown under the original message, in order.
- Photos, videos, documents, voice notes, polls and similar messages show as italic placeholders.
- Settings -> About: live changelog, contributing, security policy, MIT license and bug report. Changelog is read live from GitHub.
- Settings -> About -> **Backend version** shows which backend the app is connected to.
- Rename chats, jump to date, pull to refresh, "Last synced" label and read more on long messages.
- Forgot PIN reset and a polished lock screen.

### Changed
- Sync is incremental: opening the app no longer re-downloads everything.
- Friendlier chat names (custom name, saved name, then number).
- Local database upgrades keep your data (Room migrations).

### Fixed
- Edits reach the app even if the original message was already synced.
- Several layout fixes (gesture bar overlap, haptics when selecting messages).

### Upgrade notes
Sign in again once. Install over the previous version only if it was signed with the same key; otherwise uninstall first.

## v4.3.1
Previous release.
