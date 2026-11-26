# Android app changelog

Newest first. The backend changelog lives in the main repository.

## v4.5.6

Works with backend v4.5.5 (older backends still work, but deleted/edited messages need the new one).

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
