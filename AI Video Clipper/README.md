# Gemini Video Editing App

---
An Android application demonstrating automated video highlight generation and intelligent cinematic editing suggestion using Vertex AI for Firebase (Gemini 2.5 Pro) and Firebase Cloud Storage.
Users can select multiple videos, specify editing goals in natural language (e.g., "create a fast-paced 15-second action reel"), upload them to Cloud Storage, and let Gemini analyze the content to recommend and preview video trims and edits.

## Prerequisites & Project Setup
This project uses **Firebase Cloud Storage** to host video files and **Vertex AI for Firebase** (Gemini 2.5 Pro) for advanced media analysis and editing recommendations. Follow the setup steps below to configure your Firebase and Google Cloud project.
### 1. Firebase Project Configuration
1. Go to the [Firebase Console](https://console.firebase.google.com/).
2. Click **Add project** and create a new project (or select an existing one).
3. Vertex AI for Firebase requires the project to be on the pay-as-you-go **Blaze plan**. In the Firebase Console, upgrade your project by clicking **Upgrade** in the bottom-left corner.
4. Register your Android App:
   - Click the Android icon to add a new app.
   - Enter the Android package name: `com.example.videoediting`.
   - Click **Register app**.
5. Download the `google-services.json` configuration file and place it in the `app/` directory of this project:
   ```path
   /app/google-services.json
   ```
---
### 2. Enable Firebase Storage
1. In the Firebase Console sidebar, go to **Build** > **Storage**.
2. Click **Get Started**.
3. Select a starting rule configuration (e.g., test mode) and choose your Storage location (e.g., `us-central1`).
4. Once created, note your bucket URI (formatted as `gs://YOUR_PROJECT_ID.firebasestorage.app` or `gs://YOUR_PROJECT_ID.appspot.com`).
5. **Create a `videos` folder**:
   - Within the Storage console dashboard, click the **New folder** icon.
   - Name the folder `videos`.
   - *Note: While the app programmatically creates this folder upon upload, creating it manually ensures it is initialized for your project.*
6. Set the **Storage Rules** to allow read/write access for uploads. For example, during development:
   ```javascript
   rules_version = '2';
   service firebase.storage {
     match /b/{bucket}/o {
       match /videos/{allPaths=**} {
         allow read, write: if true;
       }
     }
   }
   ```
---
### 3. Enable Vertex AI for Firebase (AI Logic)
1. In the Firebase Console sidebar, go to **Build** > **Vertex AI** (or **Build with Gemini**).
2. Click **Get Started** and follow the prompts.
3. This activates the necessary Vertex AI APIs on the underlying Google Cloud project and sets up billing integration.
---
### 4. Configure Google Cloud Storage Permissions for Vertex AI
Since Gemini processes video files directly from Google Cloud Storage, the Google Cloud Vertex AI service agent requires permission to read objects from your Storage bucket.
1. Find your **Google Cloud Project Number**:
   - In the Firebase Console, click the Gear icon next to **Project Overview** and select **Project Settings**.
   - Copy the **Project number** (e.g., `123456789012`).
2. Open the [Google Cloud Console IAM Page](https://console.cloud.google.com/iam-admin/iam).
3. Ensure you are in the correct project.
4. Check the **Include Google-provided role grants** box in the top-right corner of the IAM principal list.
5. Search for the **Vertex AI Service Agent** service account:
   ```text
   service-PROJECT_NUMBER@gcp-sa-aiplatform.iam.gserviceaccount.com
   ```
   *(Replace `PROJECT_NUMBER` with your actual project number).*
6. Click the edit icon (pencil) next to this service account principal to modify its roles.
7. Click **Add another role** and select **Storage Object Viewer** (`roles/storage.objectViewer`).
8. Save the permissions.
