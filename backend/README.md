# Backend

## Local development

Install Node.js 24 and JDK 21. From this folder:

```sh
npm ci
npm run emulators
```

In another terminal, set these variables (the API does not automatically load `.env` files):

```sh
export GCLOUD_PROJECT=demo-car-mileage
export FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099
export FIRESTORE_EMULATOR_HOST=127.0.0.1:8081
npm run seed
npm run dev
```

API: `http://localhost:8080`; emulator UI: `http://localhost:4000`. The seed creates emulator-only users `driver-one@example.test` and `driver-two@example.test` with password `local-demo-only`, and a shared `family-car`. This password flow is for exercising the emulator API; the Android app uses real Google sign-in and needs a configured Firebase project. Do not set emulator variables in production. The server rejects emulator configuration when running on Cloud Run.

To obtain a local emulator ID token:

```sh
curl -s 'http://localhost:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key' \
  -H 'Content-Type: application/json' \
  -d '{"email":"driver-one@example.test","password":"local-demo-only","returnSecureToken":true}'
```

Use the returned `idToken` as `Authorization: Bearer TOKEN`. See `../docs/openapi.yaml` for request bodies. `/health` is public; all `/v1` routes require a verified token. Authorization uses server-side `users/{uid}.enabled` and `cars/{id}.memberUids`.

```sh
npm run check
npm test
npm run test:integration
npm run format:check
```

Integration tests use the Firestore emulator to check duplicate retries, identity/membership, chronological insertion and simultaneous date conflicts. They skip during ordinary `npm test` if the emulator host is unset. Transactions read the user/car and history, then create the entry and update the car revision atomically to serialize car writes.

## Real Firebase setup and manual provisioning

1. Add Firebase to the existing Google Cloud project `car-mileage-tracker-514210` and enable Google as an Authentication provider. Create a Firestore Standard/Native database in a suitable European region. Its location should match the future API region where possible.
2. Register Android package `com.pandre.mileage`, add the debug/release signing fingerprints, and copy Firebase API key, app ID, project ID and OAuth **web/server client ID** into the Android build configuration described in `../android/README.md`.
3. Each user signs in once through the Android app. First access may be denied until provisioning. In Firebase Console > Authentication > Users, copy each Firebase UID.
4. In Firestore, create `users/UID` for each person: `enabled` boolean true, `displayName` string, `email` string.
5. Create `cars/family-car` (or another simple ID) with `displayName` string, `memberUids` array containing the two exact UIDs, and `revision` integer 0. Repeat for each shared car.
6. Deploy the deny-all mobile/web rules in `firestore.rules`: `npx firebase deploy --only firestore:rules --project car-mileage-tracker-514210`. The API server SDK uses IAM and performs its own authorization; rules do not restrict the server SDK.
7. Confirm both users see the car, while a third unprovisioned account receives HTTP 403. Set a user's `enabled` to false to disable future API access. Manual console corrections appear on the next complete history refresh.

Never commit personal UID lists, tokens, service-account keys or downloaded configuration files. No membership API is exposed.

## Cloud Run deployment

This step provisions billable-capable resources. Check the project's billing settings and current free allowances first. Substitute your real project ID and region below; these are templates, not automatically executed scripts.

```sh
gcloud config set project car-mileage-tracker-514210
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com firestore.googleapis.com
gcloud iam service-accounts create mileage-api --display-name='Mileage API'
gcloud projects add-iam-policy-binding car-mileage-tracker-514210 \
  --member='serviceAccount:mileage-api@car-mileage-tracker-514210.iam.gserviceaccount.com' \
  --role='roles/datastore.user'
gcloud run deploy mileage-api --source . --region europe-west1 \
  --service-account mileage-api@car-mileage-tracker-514210.iam.gserviceaccount.com \
  --allow-unauthenticated --min-instances 0 --max-instances 2 \
  --cpu 1 --memory 512Mi --set-env-vars GCLOUD_PROJECT=car-mileage-tracker-514210
```

Deploy from `backend/`. The deployer needs the appropriate build/deploy permissions; the runtime account needs Firestore access. Public Cloud Run invocation allows Express to receive Firebase-token requests; all data routes still verify Firebase identity. Set the returned HTTPS service URL as Android `API_BASE_URL`, with a trailing slash. Do not put a service-account key in the container.

A manual GitHub Actions deployment workflow is included. Configure repository variables `GCP_PROJECT_ID`, `GCP_REGION`, `GCP_WORKLOAD_IDENTITY_PROVIDER`, `GCP_DEPLOY_SERVICE_ACCOUNT`, and `GCP_RUNTIME_SERVICE_ACCOUNT`. Set up GitHub OIDC workload identity federation restricted to this repository/main branch; grant the deployer Cloud Run/build permissions and permission to act as the runtime service account. Run the workflow manually after checks pass.

## Manual export and restore

Use the Admin SDK with your local Application Default Credentials (or the emulator environment). Backups contain private account details and must stay outside Git:

```sh
GCLOUD_PROJECT=car-mileage-tracker-514210 npm run backup -- export /secure/path/mileage.json
GCLOUD_PROJECT=EMPTY_TARGET_PROJECT npm run backup -- restore /secure/path/mileage.json
```

Export creates a new file with restricted permissions and refuses to overwrite it. Restore validates the file and requires empty user/car collections; it never replaces existing records. Stop the API while restoring. Restoration is sequential, so an interrupted restore may leave partial data; inspect the target before retrying. Firebase Authentication accounts are separate from Firestore and must exist with the same UIDs. Cloud Firestore managed backup/export features can incur charges.
