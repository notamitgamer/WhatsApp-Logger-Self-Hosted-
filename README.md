<p align="center">
  <img src="https://raw.cdn.amit.is-a.dev/uploads/c44b5507-icon.svg" height="100" width="100">
</p>

<h1 align="center">WhatsApp Logger (Self-Hosted)</h1>

<b><p align="center">A privacy-focused, self-hosted WhatsApp archiving tool. It captures messages (including deleted ones) via a linked device connection and stores them in your own Firebase Firestore database.</p></b>

> [!IMPORTANT]
> Using this logger is completely safe and will not get your WhatsApp account banned. Here is why:
> 
> * **100% Passive (Read-Only):** WhatsApp bans accounts for *sending* spam, bulk messages, or unauthorized automated replies. This logger does not send messages; it acts strictly as a passive listener, which does not trigger WhatsApp's anti-spam algorithms.
> * **Standard Linked Device:** The tool connects to WhatsApp using the official Multi-Device WebSocket protocol. To WhatsApp's servers, this connection looks exactly like you logging into standard WhatsApp Web on a secondary browser. 
> * **No User Reports:** The number one cause of bans is other users reporting an account. Since this logger works silently in the background and does not interact with anyone, there is zero risk of being reported.

> [!TIP]
> Check <a href="https://docs.amit.is-a.dev/whatsapp-logger/">guide</a> for detailed installation process.

## Features

* **Anti-Delete**: Logs messages instantly, preserving them even if the sender deletes them.
* **Privacy First**: You host the backend and database. No third-party servers access your data.
* **Secure Access**: Frontend is protected by a password validated against your backend.
* **Media Support**: Captures text messages (Images/Media support depends on Baileys implementation, primarily text-focused).
* **Search & Filter**: Search by content or filter by date.
* **Export**: Export chat logs to `.txt` files.
* **Live Server Logs**: See backend logs in the app under `Settings` -> `System Diagnostics` -> `Live server logs`, or visit `https://your-app.onrender.com/logs` after login.
* **Offline Ready**: Uses IndexedDB caching so you can read your logs even without an internet connection.

> [!NOTE]
> **Upgrading from an older version?** Check the [CHANGELOG](CHANGELOG.md) first. v4.3.0 moves your Render URL to `js/config.js`.

---

## Prerequisites

1.  A **GitHub** Account.
2.  A **Render** Account (Free tier works).
3.  A **Firebase** Account (Free Spark plan works).
4.  A **WhatsApp** account on your phone.
5.  An **UptimeRobot** Account (Free).

---

## Step 1: Firebase Setup (The Database)

1.  Go to the [Firebase Console](https://console.firebase.google.com/) and create a new project.
2.  **Create Database**:
    * Navigate to **Firestore Database** in the sidebar.
    * Click **Create Database**.
    * Select a location (e.g., `nam5` or `eur3`).
    * Start in **Production Mode**.
3.  **Set Security Rules**:
    * Go to the **Rules** tab in Firestore.
    * Replace the rules with the following. As of v4.2.1, the frontend never talks to Firestore directly — only your Render backend does, via the Admin SDK, which bypasses these rules entirely regardless of what they say. So there's no reason to allow any direct client access:
        ```javascript
        rules_version = '2';
        service cloud.firestore {
          match /databases/{database}/documents {
            match /{document=**} {
              // Deny all direct client access. The Admin SDK (your Render backend)
              // bypasses these rules entirely, so this only blocks browsers/apps
              // that try to read or write Firestore directly with a client SDK.
              allow read, write: if false;
            }
          }
        }
        ```
4.  **Get Backend Credentials (Service Account)**:
    * Go to **Project Settings** (Gear icon) -> **Service accounts**.
    * Click **Generate new private key**.
    * This will download a `.json` file. **Keep this safe.** You will need its content for Render.

That's everything you need from Firebase — the frontend doesn't need any Firebase configuration at all.

---

## Step 2: Deploy Backend (The Listener)

1.  **Fork this Repository** to your own GitHub account.
2.  Log in to [Render](https://render.com/).
3.  Click **New +** -> **Web Service**.
4.  Connect your forked repository.
5.  **Runtime**: Select **Docker**.
6.  **Environment Variables** (Critical Step):
    Add the following variables under "Advanced":
    * `FIREBASE_SERVICE_ACCOUNT`: Paste the **entire content** of the JSON file you downloaded in Step 1.
    * `AUTH_USER`: Set a username (e.g., `admin`).
    * `AUTH_PASS`: Set a strong password. This creates the lock for your logger.
7.  Click **Create Web Service**.
8.  Wait for the deployment to finish. Render will give you a URL like `https://your-app.onrender.com`.

### Excluding specific chats from logging

If there are chats you don't want logged at all — a bot, a broadcast/status JID, a business account — edit `EXCLUDED_JIDS` in `src/config.js` on your fork before deploying:

```javascript
// --- CONFIGURATION ---
const PORT = process.env.PORT || 3000;
const AUTH_USER = process.env.AUTH_USER;
const AUTH_PASS = process.env.AUTH_PASS;
const MAX_LOGS = 500;
const MAX_CONNECTIONS_PER_TOKEN = 15;
const VERSION = '4.x.x';
const EXCLUDED_JIDS = new Set(['']);

module.exports = {
    PORT,
    AUTH_USER,
    AUTH_PASS,
    MAX_LOGS,
    MAX_CONNECTIONS_PER_TOKEN,
    VERSION,
    EXCLUDED_JIDS
};
```

`EXCLUDED_JIDS` is a set of full JIDs (the `...@s.whatsapp.net` / `...@g.us` / `...@lid` identifiers, not just a phone number) that are skipped entirely — they won't be cached, streamed, or included in exports. It ships empty by default. You can find a chat's exact JID from the live server logs (`Settings` -> `System Diagnostics`) the first time it logs a message, then add it here, e.g. `new Set(['917278779512@s.whatsapp.net', '201554426618024@lid'])`, and redeploy.

---

## Step 3: Connect WhatsApp

1.  Open your Render URL (`https://your-app.onrender.com`) in a browser.
2.  You will be prompted for a login. Use the `AUTH_USER` and `AUTH_PASS` you set in Render.
3.  You will see a **QR Code**.
4.  Open **WhatsApp** on your phone:
    * iOS: Settings -> Linked Devices
    * Android: Three dots -> Linked Devices
5.  Tap **Link a Device** and scan the QR code.
6.  The page should refresh and say **"System Operational"**. Your backend is now listening!

---

## Step 4: Setup Frontend (The Viewer)

Pick the option that matches how you host the frontend.

### Option A: Fork + auto-deploy (most common)

1.  Open your **fork** on GitHub and go to **`js/config.js`**. Click the pencil icon (**Edit this file**). It's a tiny file with a big `EDIT THIS ONE LINE` banner, so it's hard to miss.
2.  Paste your Render URL between the quotes (e.g., `https://your-app.onrender.com` - **No trailing slash**).
3.  Click **Commit changes**. If your fork is connected to GitHub Pages, Netlify, Vercel or Firebase, it redeploys on its own and there's nothing to upload. Leave the file as it is afterwards.

### Option B: Self-host manually

1.  Download this repository (green **Code** button -> **Download ZIP**) or clone it. The frontend is made of `index.html` plus the `css/` and `js/` folders. Keep them together.
2.  Open **`js/config.js`** in a text editor (Notepad, VS Code, etc.). It is a tiny file with a big `EDIT THIS ONE LINE` banner, so it's hard to miss.
3.  **Fill in the details**:
    * `RENDER_BACKEND_URL`: Your Render URL (e.g., `https://your-app.onrender.com` - **No trailing slash**).
4.  **Deploy the Frontend**:
    * Host the whole frontend folder (`index.html`, `css/`, `js/`, plus `sw.js` and `manifest.json` for the installable PWA) on any static host:
        * **Firebase Hosting** (Recommended): `firebase init` -> Hosting -> Select `public` directory -> Copy `index.html`, `css/` and `js/` there -> `firebase deploy`.
        * **GitHub Pages**: Enable Pages in your repo settings.
        * **Netlify/Vercel**: Drag and drop the folder containing `index.html`.

### The setting (both options)

**It should look like this before you edit it:**
```javascript
const RENDER_BACKEND_URL = ""; // <-- PUT YOUR RENDER URL HERE
```

That's the only setting needed. As of v4.2.1, the frontend authenticates against your Render backend (`/api/verify`) and gets a session token back, used for every chat/message request over Server-Sent Events. Firebase credentials only ever live on the backend, set in Step 2.

### Frontend file layout

```
index.html        page markup (sidebar, chat area, modals)
css/              themes, layout, modals (loaded in the order listed in index.html)
js/config.js      <-- the only file you need to edit
js/*.js           app code, split by feature (boot, lock screen, SSE sync, chat UI, calendar, ...)
```

> **Note:** the scripts are plain `<script>` files that share one global scope, so the load order in `index.html` matters. If you add a file, add it to the list at the bottom of `index.html` and to `ASSETS` in `sw.js`.

---

## Step 5: Usage

1.  Navigate to your hosted frontend URL.
2.  You will see a Login Screen.
3.  Enter the same `AUTH_USER` and `AUTH_PASS` you configured on Render.
4.  Once unlocked, your chats will load from Firebase.
    * **Sidebar**: Shows chat list sorted by newest activity.
    * **Search**: Filter contacts by name or phone number.
    * **Export**: Download chat history as a `.txt` file.

> [!TIP]
> Install the **web app (PWA)** after publishing the page for better security and a native feel, and turn on **PIN** or **Biometric** lock in `Settings`.

---

## Step 6: Keep it Alive (UptimeRobot)

Render's free tier spins down after inactivity. To keep your logger running 24/7:

1.  Create a free account on [UptimeRobot](https://uptimerobot.com/).
2.  Click **Add New Monitor**.
3.  **Monitor Type**: HTTP(s).
4.  **Friendly Name**: WhatsApp Logger.
5.  **URL (or IP)**: `https://your-app.onrender.com/ping` (Make sure to add `/ping` at the end).
6.  **Monitoring Interval**: 5 minutes.
7.  Click **Create Monitor**.

---

## Troubleshooting

* **"No chats found"**: Send a message to the linked WhatsApp account to trigger the first log.
* **"Incorrect Credentials"**: Ensure your Render backend is running and you are using the exact Username/Password defined in Render Environment Variables.
* **Proof/Phone Numbers**: If a chat shows a long ID (e.g., `1155...@lid`), wait a few minutes. The backend automatically syncs contacts and updates the record with the real phone number.

## Disclaimer

This tool is for personal archiving purposes. Using it to log conversations without consent may violate privacy laws in your jurisdiction. The author is not responsible for misuse.
