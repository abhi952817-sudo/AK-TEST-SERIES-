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
The Admin Panel calls `verifyAdminGeminiConnector`. It checks the configured key against Gemini's model-list endpoint and returns only provider name, verification status, supported model identifiers, and check time. It never returns the secret or provider response body. Student accounts cannot call it.

## Admin generation contract
The Admin Panel and Admin PDF pipeline call `generateAdminGeminiContent`. The callable accepts a bounded prompt, optional system instruction, and a validated Gemini model identifier. It reads the API key only from Secret Manager, applies a request timeout, returns generated text plus token usage, and never returns the key or raw provider error body. Only a verified Admin can call it. Android no longer reads `BuildConfig.GEMINI_API_KEY`, and the Admin's old local secondary-key storage is cleared at app initialization.

The Admin Panel shows the last request's input, output, and total token counts. These are in-memory display counters, not yet persistent billing analytics.

## Current boundary
Admin Gemini verification and Admin-only text generation are implemented in source but still require the Secret Manager value to be configured and the Cloud Functions codebase to be deployed. Student-side Admin fallback, server-enforced daily quota, persistent usage/cost accounting, Groq, and Cerebras are not implemented yet. Do not mark these connectors as live until their own server adapters and tests are deployed.
