# Admin Gemini Secret Connector

## Scope
This backend-only step moves the Admin Gemini credential boundary out of the Android application. The secret name is `AKTS_ADMIN_GEMINI_API_KEY`. It is read only by the callable Cloud Function `verifyAdminGeminiConnector`, which is restricted to the verified Admin claim or the verified owner account already recognized by the app's existing server authorization policy.

## Configure before deployment
From the Firebase project root, an authorized operator must set the secret using the Firebase CLI:

```bash
firebase functions:secrets:set AKTS_ADMIN_GEMINI_API_KEY
```

Enter the real key only in the CLI's secure prompt. Never commit it, put it in `.env`, `local.properties`, Gradle, BuildConfig, Firestore, or the Android app. Then deploy the Functions codebase through the project's normal reviewed deployment process.

## Verification contract
The callable checks the configured key against Gemini's model-list endpoint. It returns only provider name, verification status, supported model identifiers, and check time. It never returns the secret or provider response body. Student accounts cannot call it.

## Current boundary
This is the Admin Gemini secret and verification foundation only. It does not yet generate questions, route Student fallback requests, or implement Groq/Cerebras. Those paths must be connected only after server-side quota, idempotency, usage accounting, and provider response validation are in place.
