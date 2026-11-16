# Student AI Connector — Integration Checkpoint

**Repository:** `abhi952817-sudo/AK-TEST-SERIES-`  
**Integration branch:** `feature/universal-gemini-ai-10-phase-2026-11-08`  
**Student source branch:** `feature/student-universal-ai-connectors-v1` (latest reviewed Student core checkpoint: `9c710af904dea246cbe79b28b30443fc05996212`)  
**Protected branch:** `main`  
**Checkpoint date:** 2026-11-16

## Integration approach

The Student connector was developed separately, then its reviewed files and feature changes were selectively ported into the existing AI Connector branch. No wholesale merge of the Student branch or Main branch was performed.

## Consolidated Student implementation

- `StudentAIKeyStore.kt` is the single active Student key store for Gemini, OpenAI, Groq, Anthropic and Cerebras.
- Keys are encrypted with AES-GCM using UID/provider-scoped non-exportable AndroidKeyStore keys. Ciphertext and usage preferences are excluded from backup/device transfer.
- `StudentAIProviderClient.kt` is the one direct Student BYOK text transport. Keys are sent only in provider-specific headers; requests use no-store caching and do not put keys in URL parameters.
- `StudentAIProviderSettingsCard.kt` replaces the old Gemini-only key dialog/card in the existing Settings screen. It verifies provider catalogue access, discovers models, saves a preferred provider/model, supports disconnect and displays provider-reported usage.
- `StudentAIUsageStore.kt` stores successful request counts and actual last token metadata locally. It does not estimate monetary cost.
- `LegacyStudentGeminiKeyMigration.kt` is a one-time migration/cleanup helper only. It migrates the old Gemini-only encrypted key into the universal store and removes the old ciphertext/alias. It is not a second active key store.
- The old active `StudentGeminiKeyStore.kt` implementation was deleted after call sites were migrated.
- Student key and usage data are cleared on logout and account switch.

## Existing app feature integration

Student requests now route through the universal provider client in these existing ViewModel workflows:
- AI question generation from the existing PDF/text extraction pipeline.
- PDF metadata/exam recognition requests.
- Smart Doubt Resolver.
- Reported-question error analysis.

The app's existing parsing, validation and approval/publish workflow remains in place. This change does not add a second PDF pipeline or a second Settings screen.

## Admin separation preserved

- Admin requests continue through the existing server-side `callAdminGeminiContent()` callable path.
- Admin connector verification/model selection remain Admin-only.
- Admin secrets are not read by the Student key store and are not sent through Student BYOK requests.
- Student provider UI is hidden for Admin accounts.
- Community Doubts remain manual-only; no Student AI provider was connected to Community Doubts.

## Removed duplicate implementation

| Old implementation | Consolidation |
|---|---|
| `StudentGeminiKeyStore.kt` | Replaced by `StudentAIKeyStore.kt`; legacy encrypted data is migrated once. |
| Gemini-only Settings dialog and menu item | Replaced by one `StudentAIProviderSettingsCard`. |
| Student Gemini key state/connect/disconnect methods | Removed from `NEETViewModel`; provider state and key custody now use the universal store/card. |
| Student Gemini key list and direct Gemini HTTP fallback | Removed; Student generation now calls `StudentAIProviderClient.generatePreferred()`. |
| Student direct Gemini HTTP functions in ViewModel | Removed; one Student provider transport handles the supported providers. |

## Validation and status

| Gate | Status |
|---|---|
| Selective Student files ported | Complete |
| Old Gemini-only Student key store removed | Complete |
| Legacy key migration helper added | Complete; device-level migration test still required |
| Existing Settings screen consolidated | Complete |
| Student generation call sites routed to universal client | Complete for listed workflows |
| Admin PDF route server-only | Source-level change complete; CI pending | Both Admin PDF screens pass `viewModel.generateAiForQuestions`; pipeline refuses to process without the server callback. Legacy direct Gemini transport and fallback generator removed. Question drafts are parsed and returned to the existing review UI; publication still requires Admin approval. |
| Direct Student/legacy Gemini transport audit | Source-level scan | No direct provider HTTP transport remains in `NEETViewModel`; the retired `GeminiPdfExtractor` is fail-closed. Student generation goes through `StudentAIProviderClient`; Admin generation goes through the server connector. |
| Admin server route preserved | Source-level check only |
| Android unit tests on integration branch | Pending CI; one compile failure diagnosed and patched | Run #212 failed only at the stale `activeGeminiKeyIndex` reference in `CommunityAdminUI.kt`; removed in the latest code checkpoint. Latest run #216 is in progress. |
| Debug APK build on integration branch | Pending CI | Run #212 skipped APK assembly because compilation failed; latest run #216 will verify the corrected tree. |
| Real provider calls / AndroidKeyStore device test | Not run |
| Main branch changes | None |
| Production deployment / Firebase rules changes | None |

**Current integration-branch code checkpoint before this documentation update:** `f874762f1ee88404cb2a057fdd69d8d697e10522`. CI run #216: `https://github.com/abhi952817-sudo/AK-TEST-SERIES-/actions/runs/43793953237` (in progress). Run #212 failed Android compilation because `CommunityAdminUI.kt` still collected the removed, unused `activeGeminiKeyIndex`; that stale UI line was removed in this checkpoint. Backend tests passed in run #212. Runs #213–#215 remain in progress. The cancel-in-progress setting has been removed so future commits will not cancel previous runs. CI is triggered on pushes. A workflow starting is not a pass; record the exact completed run and SHA before marking tests passed.

## Remaining work

1. Review the new integration diff and fix any compile/test failures reported by CI.
2. Verify the legacy Gemini key migration on an Android device/emulator.
3. Verify model catalogue access and one successful generation for each provider using test credentials supplied by the owner; do not commit or log keys.
4. Add provider-specific request limits/JSON mode where supported and bounded cancellation-safe retry only where safe.
5. Review all Student generation call sites to ensure no remaining Gemini-only transport or duplicate route exists.
6. Complete PDF fallback/multimodal integration and feature-level usage display without bypassing Admin approval.
7. Run the full security/regression checklist on this integration branch.
8. Only after review and explicit approval, selectively integrate the reviewed result into Main.
