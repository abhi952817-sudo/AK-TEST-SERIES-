# Student Universal AI Connector — Implementation Checkpoint

**Repository:** `abhi952817-sudo/AK-TEST-SERIES-`  
**Development branch:** `feature/student-universal-ai-connectors-v1`  
**Integration target:** `feature/universal-gemini-ai-10-phase-2026-11-08`  
**Protected branch:** `main`  
**Checkpoint date:** 2026-11-16

## 1. Branch rules

- Develop Student-owned provider functionality only on this branch.
- Keep Main unchanged until reviewed integration is explicitly approved.
- The existing AI Connector branch remains the integration target for Student + Admin.
- Do not merge this branch wholesale into Main.
- Compare the Student branch against the old AI branch before integration. Reuse the old branch's existing Gemini settings and ViewModel call sites; replace them with the universal Student connector instead of keeping two implementations.
- Preserve Admin credential custody in Secret Manager / Cloud Functions. Student BYOK keys must never be sent to Admin endpoints or stored in Firestore.
- Community Doubts remain manual-only; do not connect an AI model to that workflow.

## 2. Student-side implementation present on this branch

| Component | Current behavior |
|---|---|
| `StudentAIKeyStore.kt` | One UID/provider-scoped AES-GCM key store backed by non-exportable AndroidKeyStore keys. Providers: Gemini, OpenAI, Groq, Anthropic, Cerebras. |
| Backup exclusions | Student API-key ciphertext and local usage preferences are excluded from cloud backup and device transfer. |
| `StudentAIProviderClient.kt` | Direct HTTPS text requests to each provider. Keys are sent in provider-specific headers, never in URL query parameters. Checks the active Firebase UID before generation. |
| Model discovery | Provider model catalogue requests for supported provider endpoints; model IDs are selected by the student. |
| Preferred provider/model | Saved locally per authenticated UID. |
| `StudentAIProviderSettingsCard.kt` | Existing Settings screen gains one Student provider card; Admin accounts are hidden from this BYOK UI. Supports key verification, encrypted save, model browsing, model selection and disconnect. |
| `StudentAIUsageStore.kt` | Stores successful request counts and the latest token usage only when returned by the provider. No fabricated token counts or cost estimates. |
| Account lifecycle | Student key material and local usage data are cleared on logout and account switch. |
| Tests added | Provider request/header contracts, response usage parsing, UID-scoped preferences and local usage records. |
| CI | Dedicated Student AI workflow runs Android unit tests and debug APK assembly on each push to this branch. |

## 3. Provider transport contract

- Gemini: native `generateContent` request and response format; `x-goog-api-key` header.
- OpenAI: Chat Completions request/response format; Bearer authorization header.
- Groq: OpenAI-compatible Chat Completions format; Bearer authorization header.
- Cerebras: OpenAI-compatible Chat Completions format; Bearer authorization header.
- Anthropic: Messages API format; `x-api-key` and `anthropic-version` headers.
- Provider model names are discovered from the provider catalogue and are not hardcoded as guaranteed-available model IDs.
- Network errors are mapped to safe user-facing messages. Raw response bodies and API keys are not logged.
- Usage/cost: provider-reported token metadata is retained locally. Monetary cost is not estimated because price depends on the actual model, account plan and current provider pricing.

## 4. Required integration with the old AI Connector branch

The old branch already contains a Gemini-only Student implementation:
- `StudentGeminiKeyStore.kt`
- Gemini key dialog/card in `SettingsScreen.kt`
- `refreshStudentGeminiKeyState()`
- `connectStudentGeminiApiKey()`
- `disconnectStudentGeminiApiKey()`
- `getGeminiApiKeys()` and Gemini direct-call paths in `NEETViewModel.kt`

These are overlapping Student responsibilities. During integration:
1. Keep `StudentAIKeyStore` as the one canonical multi-provider key store.
2. Port the new Student settings card into the old branch's existing Settings screen, replacing its Gemini-only card; do not add a second Settings screen.
3. Replace Student-only Gemini key reads/call paths with `StudentAIProviderClient.generatePreferred()`.
4. Keep Admin primary/secondary keys and Admin requests on their server-managed path; never route Admin secrets through the Student client.
5. Remove `StudentGeminiKeyStore` and obsolete Student-only key-state methods only after all call sites are migrated and regression tests prove no references remain.
6. Keep one canonical PDF pipeline and one canonical question-generation planner. Route Student BYOK fallback through the existing pipeline; do not create another PDF pipeline.
7. Preserve review/approval gates. No generated questions are auto-published.
8. Compare the complete diff, detect duplicate classes/functions/routes/preferences and run all relevant tests before merging into the old AI branch.

## 5. Not yet complete

- The universal client currently handles text requests. PDF/image/multimodal request bodies and local-first PDF extraction integration are still pending.
- The provider client is not yet wired into every Student feature: question generation, PDF extraction fallback, answer/explanation generation, notes, summaries, syllabus analysis and study assistant.
- Community Doubts are intentionally excluded by the existing product rule.
- Streaming, controlled retry/backoff, provider-specific request-size limits and cancellation propagation need implementation.
- Provider model discovery confirms catalogue access, not guaranteed permission for every generation capability. First generation must still handle provider authorization/model errors.
- Local usage counts and last token metadata exist; a complete per-feature usage dashboard and provider-specific monetary billing are not implemented.
- Student key-store encryption and real provider network calls still require device/emulator verification.
- Old AI branch integration, duplicate removal, cross-branch regression testing and Main integration have not been performed.
- No production Firebase/Firestore/Storage change or deployment is authorized by this work.

## 6. Validation status

| Gate | Status | Evidence |
|---|---|---|
| Source changes committed and pushed | PASS | Branch contains Student-only files and Settings/ViewModel lifecycle hooks. |
| Main changed | NO | All current changes are isolated to `feature/student-universal-ai-connectors-v1`. |
| Android unit tests | OPEN | CI has been configured; no completed test result is recorded in this checkpoint. |
| Debug APK build | OPEN | CI has been configured; no successful artifact is recorded in this checkpoint. |
| Real provider API calls | NOT RUN | No live provider key was used. |
| Device AndroidKeyStore behavior | NOT RUN | Requires device/emulator execution. |
| Old AI branch integration | NOT STARTED | Must be selective and duplicate-aware. |

**Important:** A workflow starting is not a test pass. Record PASS only against the exact commit SHA and completed CI run.

## 7. Next implementation sequence

1. Complete the Student provider tests and fix every CI failure without weakening assertions.
2. Add robust cancellation, bounded retries and provider-specific request validation.
3. Connect the universal Student client to existing generation/PDF call sites while preserving Admin separation.
4. Add local-first PDF extraction and provider fallback, with no fake-question fallback and no auto-publish.
5. Reconcile this branch with the old AI branch; remove the Gemini-only duplicate Student implementation.
6. Run Android/backend/security/regression validation on the integrated old AI branch.
7. Only after integration review, prepare a selective PR to Main.
