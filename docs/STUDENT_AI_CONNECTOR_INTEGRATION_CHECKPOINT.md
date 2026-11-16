# Student AI Connector — Integration Checkpoint

**Repository:** `abhi952817-sudo/AK-TEST-SERIES-`  
**Integration branch:** `feature/universal-gemini-ai-10-phase-2026-11-08`  
**Student source branch:** `feature/student-universal-ai-connectors-v1` (latest reviewed Student core checkpoint: `9c710af904dea246cbe79b28b30443fc05996212`)  
**Protected branch:** `main`  
**Checkpoint date:** 2026-11-16

## 2026-11-16 integration audit

### Universal module status
- Confirmed integrated: Student generation uses `NEETViewModel.generateAiForQuestions()` -> `StudentAIProviderClient.generatePreferred()`; Admin generation uses the server-side callable. Prompt construction is shared through `UniversalAIContext`.
- Confirmed universal question-generation path: `NEETViewModel` builds exam-specific prompts with `UniversalAIContext.buildDynamicGenerationPrompt()` and validates responses with `GeminiResponseValidator`. PDF exam recognition also uses the registered `UniversalExamRegistry`.
- Student providers implemented in the connector: Gemini, OpenAI, Groq, Anthropic Claude, Cerebras. Student credentials stay on-device; Admin connector remains separate and server-side.
- The registered exam catalogue contains 20+ exam configurations, but this does **not** mean every exam's syllabus is independently verified. `UniversalAIContext` explicitly requires an Admin-supplied, verified official syllabus for non-NEET exams and refuses to claim official scope from legacy catalogue entries.
- Admin PDF route is server-Gemini only; it is not yet a provider-neutral Admin multi-provider connector. It currently extracts PDF text and fails to review when no readable text is available; image-only/scanned PDF OCR and image/diagram-aware extraction are not yet integrated in this Admin route.
- Reconfirmed **UAI-001 / High / confirmed source-level**: `ProcessPdfTab` exposes subjects from only the first stage and first paper; `UniversalPdfPipeline.parseGeminiResponse()` stamps `examConfig.stages.firstOrNull()?.papers.firstOrNull()?.paperId` and the Admin route passes an empty stage ID. Multi-stage/multi-paper exams therefore need explicit stage + paper selection and selected-paper-aware validation/mapping before the Admin route is fully universal.
- Student and Admin use the shared Universal prompt/context policy, but Student PDF workflows do not use the Admin-only `processAdminUploadedPdf()` route; they use the existing local extraction/OCR + Student text provider + universal generation/validation path. The Student provider transport is text-only: diagram/image crops are extracted locally, but image bytes are not sent to Gemini/OpenAI/Claude/Groq/Cerebras for visual reasoning yet.
- Student PDF extraction has an offline text-first path, on-device ML Kit OCR fallback for scanned PDFs, and local diagram/image candidate extraction. The Admin PDF route currently lacks that OCR fallback.
- The registry contains 25 exam configurations. The universal prompt builder is connected, but only NEET has the versioned official syllabus module wired in. Other exams must receive a separately Admin-verified official syllabus source; the Student PDF flow does not yet select/attach such a versioned syllabus source automatically.

### CI diagnosis and fix
- Integration CI run #222 failed because the workflow used JDK 17 while the project compiles against Android SDK 36.1 and Robolectric reports SDK 36 requires Java 21. This was an environment/toolchain mismatch, not a Kotlin compilation error.
- Updated `.github/workflows/universal-gemini-validation.yml` to JDK 21.
- Latest integrated AI branch run #227 passed Android unit tests, backend Functions tests, and debug APK assembly for SHA `bad4cbb727f0ba116505f2c3d4f957320d45d826`: `https://github.com/abhi952817-sudo/AK-TEST-SERIES-/actions/runs/43798213626`.
- Student source branch run #39 passed Android unit tests and debug APK assembly for SHA `e803078e1d31d38a212deaeab324e03db217167a`: `https://github.com/abhi952817-sudo/AK-TEST-SERIES-/actions/runs/43794251283`.
- Aligned the Student source branch's `StudentAIProviderClient.kt` with the integrated version by adding the authenticated-UID recheck immediately before request construction. New Student CI run #40 is pending for SHA `6f0e010e347e9e394b2d9d1a757357e662c5d648`: `https://github.com/abhi952817-sudo/AK-TEST-SERIES-/actions/runs/43803245008`.

### Branch / duplicate check
- Main remains unchanged.
- Integration branch has the Student connector classes at the same canonical paths as the Student source branch; the overlap is intentional selective porting, not a second set of duplicate classes. The retired Gemini-only store is absent; the migration helper is retained only to clear legacy credentials.
- Branches remain substantially divergent. Continue selective integration only; do not merge either feature branch wholesale.

## Branch comparison checkpoint

At the time of this checkpoint:
- `main` SHA: `dabeeaec90ddfde4fa69f4b89e71a5f2ffe1fcb4` (unchanged by this work).
- Integration branch compared with Main: 391 commits ahead, 108 behind.
- Student source branch compared with Main: 57 commits ahead, 0 behind.
- Student source branch compared with the integration branch: 165 commits ahead, 391 behind.

Because the branches have substantial divergence, do not merge either feature branch wholesale. Student files were ported selectively and adapted to existing AI/Admin call sites. Main has not been merged or modified.

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
| Admin PDF route server-only | Source-level change complete; CI #227 passed | Both Admin PDF screens pass `viewModel.generateAiForQuestions`; pipeline refuses to process without the server callback. Legacy direct Gemini transport and fallback generator removed. Question drafts are parsed and returned to the existing review UI; publication still requires Admin approval. |
| Direct Student/legacy Gemini transport audit | Source-level scan | No direct provider HTTP transport remains in `NEETViewModel`; the retired `GeminiPdfExtractor` is fail-closed. Student generation goes through `StudentAIProviderClient`; Admin generation goes through the server connector. |
| Admin server route preserved | Source-level check only |
| Android unit tests on integration branch | Passed on SHA `bad4cbb...` | Run #227 passed. |
| Debug APK build on integration branch | Passed on SHA `bad4cbb...` | Run #227 passed `assembleDebug`. |
| Real provider calls / AndroidKeyStore device test | Not run |
| Main branch changes | None |
| Production deployment / Firebase rules changes | None |

**Latest verified integration code checkpoint:** `bad4cbb727f0ba116505f2c3d4f957320d45d826`; CI run #227 passed Android unit tests, backend tests and debug APK assembly. The documentation update below triggers a new CI run; do not treat it as passed until it completes.

## Remaining work

1. Fix **UAI-001**: add explicit stage/paper selection to the Admin PDF UI and make validation, section mapping and `paperId` use the selected paper.
2. Add an Admin-verified syllabus source selector/version store for non-NEET exams; do not treat all 25 registry entries as syllabus-verified.
3. Extend the Student provider contract with safe multimodal/image support or explicitly route diagrams to manual review; do not pretend text-only requests understand extracted images.
4. Add OCR fallback to the Admin PDF route and retain Admin approval before publication.
5. Verify the legacy key migration and AndroidKeyStore behavior on a real Android device/emulator.
6. Verify one successful real request per provider using owner-supplied test credentials; do not commit or log keys.
7. Review provider request limits/JSON mode, bounded retry, usage display and all generation call sites.
8. Run the full security/regression checklist. Only after review and explicit approval, selectively integrate reviewed changes into Main.
