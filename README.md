# WhatsApp Logger (Self-Hosted)

A privacy-focused, self-hosted WhatsApp archiving tool. It captures messages (including deleted ones) via a linked device connection and stores them in your own Firebase Firestore database.

> [!WARNING]
> Kindly upgrade to v4.1.7, issues fixed:
> - **Authentication Loop (Status 428):** Fixed an issue where new installations would get stuck in a continuous reconnection loop due to a deprecated `printQRInTerminal` setting in the Baileys library.
> - **QR Code Rendering:** Removed the broken terminal QR generation and ensured the QR code is smoothly routed and rendered directly on the Express web interface.

> [!IMPORTANT]
> **Update Notice**
>
> I'm trying to build a feature to save media like images, videos, and voice messages — but Firebase's free tier caps storage at 1GB, so it's not viable for that. I'm planning to add this as an optional feature using Hugging Face for storage instead. Messages will continue to be stored in Firebase as before. Frontend and Firebase security are also being upgraded as part of this effort.
>
> Okay, so, I made the [plan](plan.md).

> [!IMPORTANT]
> Using this logger is completely safe and will not get your WhatsApp account banned. Here is why:
> 
> * **100% Passive (Read-Only):** WhatsApp bans accounts for *sending* spam, bulk messages, or unauthorized automated replies. This logger does not send messages; it acts strictly as a passive listener, which does not trigger WhatsApp's anti-spam algorithms.
> * **Standard Linked Device:** The tool connects to WhatsApp using the official Multi-Device WebSocket protocol. To WhatsApp's servers, this connection looks exactly like you logging into standard WhatsApp Web on a secondary browser. 
> * **No User Reports:** The number one cause of bans is other users reporting an account. Since this logger works silently in the background and does not interact with anyone, there is zero risk of being reported.

### Check <a href="https://amit.is-a.dev/logger">guide</a> for detailed installation process. 
> *i forgot to upgrade the docs to 4.1.7, i need some time to do that.*

### Important notes:
 * It is recommended to download the **web app (PWA)** after the publication of the webpage for better security and native experience. 
 * It is recommended to use **PIN** or **Biometric authentication** inside the web app. Find the authentication options in Settings.

## Features

* **Anti-Delete**: Logs messages instantly, preserving them even if the sender deletes them.
* **Privacy First**: You host the backend and database. No third-party servers access your data.
* **Secure Access**: Frontend is protected by a password validated against your backend.
* **Media Support**: Captures text messages (Images/Media support depends on Baileys implementation, primarily text-focused).
* **Search & Filter**: Search by content or filter by date.
* **Export**: Export chat logs to `.txt` files.
* **Offline Ready**: Uses IndexedDB caching so you can read your logs even without an internet connection.

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
    * Replace the rules with the following (allows anyone to read, but only backend with Admin SDK can write):
        ```javascript
        rules_version = '2';
        service cloud.firestore {
          match /databases/{database}/documents {
            match /{document=**} {
              // 1. Allow Read: Essential for your HTML page to fetch chats.
              allow read: if true;
         
              // 2. Allow Update: Enables the "Rename Chat" feature from the frontend.
              // This allows updating existing documents (like changing the name)
              // but prevents creating NEW documents or Deleting them.
              allow update: if true;
         
              // 3. Block Create/Delete: Only the Backend (Render) can create new messages
              // or delete them. This prevents random people from injecting fake chats.
              allow create, delete: if false;
            }
          }
        }
        ```
4.  **Get Backend Credentials (Service Account)**:
    * Go to **Project Settings** (Gear icon) -> **Service accounts**.
    * Click **Generate new private key**.
    * This will download a `.json` file. **Keep this safe.** You will need its content for Render.
5.  **Get Frontend Configuration**:
    * Go to **Project Settings** -> **General**.
    * Scroll down to "Your apps" and click the **Web (</>)** icon.
    * Register app (nickname: "Logger Frontend").
    * Copy the `firebaseConfig` object (API Key, Project ID, etc.). You will need this for `index.html`.

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

1.  Download the `index.html` file from this repository.
2.  Open `index.html` in a text editor (Notepad, VS Code, etc.).
3.  Locate the Configuration section (around line 675).
4.  **Fill in the details**:
    * `RENDER_BACKEND_URL`: Your Render URL (e.g., `https://your-app.onrender.com` - **No trailing slash**).
    * `firebaseConfig`: The keys you copied in Step 1.5.

    **It should look like this before you edit it:**
    ```javascript
    const RENDER_BACKEND_URL = ""; 

    // Firebase Config
    const firebaseConfig = {
        apiKey: "",
        authDomain: "",
        projectId: "",
        storageBucket: "",
        messagingSenderId: "",
        appId: ""
    };
    ```

5.  **Deploy the Frontend**:
    * You can host this single file anywhere:
        * **Firebase Hosting** (Recommended): `firebase init` -> Hosting -> Select `public` directory -> Put `index.html` there -> `firebase deploy`.
        * **GitHub Pages**: Enable Pages in your repo settings.
        * **Netlify/Vercel**: Drag and drop the folder containing `index.html`.

---

## Step 5: Usage

1.  Navigate to your hosted frontend URL.
2.  You will see a Login Screen.
3.  Enter the same `AUTH_USER` and `AUTH_PASS` you configured on Render.
4.  Once unlocked, your chats will load from Firebase.
    * **Sidebar**: Shows chat list sorted by newest activity.
    * **Search**: Filter contacts by name or phone number.
    * **Export**: Download chat history as a `.txt` file.

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
