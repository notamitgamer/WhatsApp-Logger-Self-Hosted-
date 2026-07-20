# WhatsApp Logger: Media Upgrade & Security Enhancement Plan

This plan outlines the architecture and implementation steps to upgrade the WhatsApp Logger. The primary objective is to introduce media support (images, videos, voice messages) by offloading large blobs to a Hugging Face dataset while retaining Firebase for lightweight text and metadata storage, alongside targeted security improvements.

## Phase 1: Architecture & Infrastructure

To bypass Firebase's 1GB free-tier limitation, media storage will utilize a distributed approach:

1.  **Media Interception:** The Baileys instance in `index.js` will be updated to detect `imageMessage`, `videoMessage`, and `audioMessage` types.
2.  **Blob Extraction:** Use Baileys' `downloadMediaMessage` to extract the binary buffers of incoming media.
3.  **Hugging Face Storage:** Push the extracted buffers directly to a designated Hugging Face dataset repository using the HF Hub API.
4.  **Asset Delivery:** Route the media delivery through a custom Cloudflare Pages and Workers setup to act as a CDN. This allows dynamic serving of the raw media files stored in the Hugging Face dataset directly to the frontend without exposing HF API tokens.
5.  **Database Linking:** Save the generated Cloudflare CDN URL, along with the `mimeType` and message metadata, into the existing Firebase Firestore `Messages` collection.

## Phase 2: Backend Implementation (`index.js`)

1.  **Dependency Updates:** Add required packages for API requests (e.g., `axios` or standard `fetch`) to handle Hugging Face Hub uploads.
2.  **Message Parsing Logic:**
    *   Expand the `messages.upsert` event listener.
    *   Implement conditional checks for media attachments.
    *   Create an async upload utility function to push the buffer to Hugging Face and construct the corresponding Cloudflare CDN endpoint URL.
3.  **Firestore Schema Update:** 
    *   Modify the `set` operation in Firestore to include `mediaUrl`, `mediaType`, and `caption` (falling back to the standard `text` field for captions).

## Phase 3: Frontend & UI/UX Updates (`index.html`)

1.  **Message Rendering:** 
    *   Update the `renderMsgs` function to check for the `mediaUrl` property.
    *   Implement conditional HTML generation to inject `<img loading="lazy">`, `<video controls>`, or `<audio controls>` elements within the `.msg-text` container.
2.  **Material Design 3 Polish:** Ensure media containers utilize strict M3 specifications, maintaining consistent border radii (`border-radius: 12px` inside bubbles), proper padding, and surface container alignments to match the existing brutally clean aesthetic.
3.  **Offline Caching (`sw.js` & IndexedDB):** 
    *   Update the IndexedDB schema if necessary to store media URLs.
    *   *Note:* Raw media files should rely on standard browser caching via the Cloudflare CDN headers rather than bloating the local IndexedDB or Service Worker cache.

## Phase 4: Security Enhancements

1.  **Firebase Rules:** Review the existing Firestore security rules to ensure that the addition of media metadata does not open any injection vulnerabilities. The current read-only/update-only structure is solid, but validate that backend service accounts are securely handling the new data shape.
2.  **Frontend Protection:**
    *   Ensure the HTML sanitizer (`escapeHTML`) correctly handles incoming media URLs to prevent XSS. 
    *   Implement Content Security Policy (CSP) headers in the frontend deployment to whitelist the specific Cloudflare custom subdomains used for media delivery.

## Phase 5: Testing & Deployment

1.  **Local Testing:** Run the Docker container locally to verify Baileys correctly downloads and buffers media without crashing on large video files.
2.  **CDN Verification:** Ensure the Cloudflare Worker script successfully proxies the Hugging Face dataset URLs with appropriate CORS headers to allow the frontend to render them.
3.  **Render Deployment:** Push changes to the GitHub repository and monitor the Render build logs for the updated backend.
