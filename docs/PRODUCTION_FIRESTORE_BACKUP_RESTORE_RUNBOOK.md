# Production Firestore Backup and Restore Drill

Status: runbook prepared; **not executed**. No production database was read, exported, imported, or modified by this change.

## Scope and safety rules

- Source database: `(default)` only.
- The Android Google Drive backup is a per-user Room/local-data archive. It is not a server-side Firestore export.
- Export the full Firestore database to a dedicated, private Cloud Storage bucket.
- Restore only into a separate temporary named Firestore database for the drill. Never import a drill export into `(default)`.
- Do not delete, rename, migrate, or retire any existing Firestore database during this drill.
- Keep the export bucket private; do not create public object access or paste credentials into GitHub/chat.

## Required values to confirm in Google Cloud Console

Set these after checking the actual Firebase/Google Cloud project. Do not guess them:

```bash
export PROJECT_ID="REPLACE_WITH_FIREBASE_PROJECT_ID"
export BACKUP_BUCKET="REPLACE_WITH_PRIVATE_GCS_BUCKET"
export FIRESTORE_LOCATION="REPLACE_WITH_DEFAULT_DATABASE_LOCATION"
export RUN_ID="akts-default-$(date -u +%Y%m%dT%H%M%SZ)"
export STAGING_DB_ID="akts-restore-drill-$(date -u +%Y%m%d)"
```

Before running commands, verify that the default database is Native mode and note its exact location:

```bash
gcloud config set project "$PROJECT_ID"
gcloud firestore databases list --project="$PROJECT_ID"
gcloud firestore databases describe --database="(default)" --project="$PROJECT_ID"
```

Confirm that `BACKUP_BUCKET` exists in the intended project, is private, has appropriate retention/versioning policy, and is accessible to the Firestore managed export/import service agent. Ensure billing and Firestore/Cloud Storage APIs are enabled.

## 1. Export the entire production database

This is a read/export operation. It does not change Firestore documents.

```bash
gcloud firestore export "gs://$BACKUP_BUCKET/firestore-exports/$RUN_ID" \
  --database="(default)" \
  --project="$PROJECT_ID"
```

Wait for completion and capture the operation name and final state:

```bash
gcloud firestore operations list --project="$PROJECT_ID"
gcloud firestore operations describe OPERATION_NAME --project="$PROJECT_ID"
```

Do not proceed until the operation reports completion without error and the export files are visible under the exact prefix in Cloud Storage. Record export time, operation name, bucket URI, and any reported document/byte counts.

Note: a normal export is not a point-in-time snapshot at the start of the export. If a transactionally consistent historical point is required, PITR must be enabled and a supported whole-minute snapshot timestamp must be selected.

## 2. Create an isolated restore target

Use the same Firestore location and Native mode as the source. The staging database ID must be new and must not match any existing database.

```bash
gcloud firestore databases create \
  --database="$STAGING_DB_ID" \
  --location="$FIRESTORE_LOCATION" \
  --type=firestore-native \
  --edition=standard \
  --delete-protection \
  --project="$PROJECT_ID"
```

If the database ID already exists, stop and choose a new unique ID. Do not reuse a database containing user or production data.

## 3. Restore into staging only

```bash
gcloud firestore import "gs://$BACKUP_BUCKET/firestore-exports/$RUN_ID" \
  --database="$STAGING_DB_ID" \
  --project="$PROJECT_ID"
```

Wait for the import operation to complete. Record the operation name, final state, and reported counts. Never change the database argument to `(default)` for this drill.

## 4. Validate restored data

Use a restricted, temporary read-only validation identity and a short-lived local environment. Compare the staging database with the source export manifest/known inventory. Validate at minimum:

- Expected top-level collections and representative document IDs.
- Nested collections/subcollections, especially test questions and user Attempts.
- Required fields and document ownership/UID fields.
- Representative test, question, user, attempt, leaderboard, and community records.
- Document counts and sample hashes/field values where available.
- No writes or deletions were performed against `(default)`.

Record pass/fail evidence. A successful import operation alone is not sufficient proof of application-level restore correctness.

## 5. Close out the drill

- Keep the export in the private bucket according to the approved retention policy.
- Keep the staging database protected until validation evidence has been reviewed.
- Do not delete the staging database or backup objects automatically. Cleanup requires a separate explicit approval after the drill.
- Only after the backup and restore evidence is reviewed may a production deployment plan be considered.

## Stop conditions

Stop immediately if the project ID, default database location, bucket ownership, source database ID, operation destination, or restore target is uncertain. Do not run an import against `(default)` as a shortcut.

## Official references

- Firestore export/import: https://firebase.google.com/docs/firestore/manage-data/export-import
- gcloud export: https://cloud.google.com/sdk/gcloud/reference/firestore/export
- Firestore database management: https://firebase.google.com/docs/firestore/manage-databases
