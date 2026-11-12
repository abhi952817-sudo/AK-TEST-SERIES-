# AK TEST SERIES — Remaining Universal Syllabus & Gemini Work Plan

**Status date:** 2026-11-09  
**Working branch:** `feature/universal-pdf-prompt-contract-v1`  
**Target:** Complete the exam-wise source-backed syllabus system and connect it to the shared Student/Admin PDF intelligence pipeline without changing `main` until release gates pass.

## 1. Non-negotiable safety rules

1. Never invent syllabus units, subtopics, official weightage, exam dates, marking rules or question counts.
2. Every syllabus snapshot is keyed by exact `examId + syllabusVersion + stageId + paperId + subjectId`. No fuzzy matching, alias fallback or cross-exam borrowing.
3. Preserve the issuing authority's exact English source text and document/page evidence. Hindi is a separately reviewed translation layer.
4. Gemini can classify, extract and suggest mappings. It cannot approve a syllabus, mark a topic official, publish a test or override an explicit exam/paper selection.
5. Unknown, conflicting or unverified topics remain review candidates / extra practice; they do not count toward official syllabus coverage.
6. Every PDF upload starts private. Only authorized Admin approval and explicit publish can make it public.
7. Counts, coverage, Recent Tests and rankings must derive from real, validated, non-duplicate, published data only.
8. Keep all work on the feature branch. Do not merge ranking/scoring branches or deploy Firebase rules/data as part of this syllabus task.

## 2. Ordered execution phases

### Phase 0 — Baseline and branch safety
- Re-check branch head, PR diff, main comparison and existing file conventions before each batch.
- Keep PR #4 in draft; record every changed path and reason.
- Check for accidental overlap with ranking/scoring work and preserve unrelated features.
- **Exit gate:** no main-branch writes, no unrelated branch merge, no unexplained file replacement.

### Phase 1 — Finish JEE Main 2027 Paper 1 source transcription
- Source: official NTA JEE Main 2027 syllabus PDF linked from the NTA documents page.
- Complete all 54 Paper 1 units: Physics 20, Chemistry 20, Mathematics 14.
- Transcribe every listed subtopic, practical/experimental skill and special note from the official PDF, with 1-based page evidence.
- Preserve exact English source wording; add a separate Hindi translation for every unit and subtopic.
- Keep Paper 1 B.E./B.Tech separate from Paper 2A B.Arch and Paper 2B B.Planning.
- Do not add official weightage unless NTA explicitly publishes it.
- **Tests:** exact subject/unit counts; non-empty topic trees; unique stable IDs; every node has source-page evidence; Hindi coverage; no Paper 2 contamination; no duplicate IDs; no fabricated weightage.
- **Exit gate:** source transcription audited against every relevant PDF page; remains pending human approval until Admin review.

### Phase 2 — JEE Advanced, independently
- Locate the latest official IIT/JAB syllabus and information brochure.
- Keep the 2026 syllabus as a versioned historical reference only if no official 2027 syllabus is published.
- Never copy JEE Main topics, paper IDs, subject mapping or marking rules into JEE Advanced.
- **Tests:** exact exam identity; version/effective year; paper/subject boundaries; source page evidence.
- **Exit gate:** official-source status and year are explicit; no unsupported 2027 claims.

### Phase 3 — Audit existing legacy syllabus data
- Review NEET UG, UPSC CDS and SSC CGL modules already present in the repository.
- For each: identify issuing authority, official notice/PDF, exam year/version, exact topic hierarchy, source pages and last verified date.
- Compare every existing node against the official source. Correct supported entries; move unsupported/obsolete entries to legacy/unverified storage rather than deleting useful history.
- Remove fabricated weightage and estimated test counts from official metadata.
- **Exit gate:** each module is either source-verified and reviewable or clearly marked pending; no legacy data silently appears as current official syllabus.

### Phase 4 — Build remaining exam modules in controlled batches
Do not generate all modules in one unreviewed batch. Work in this order, completing source research, transcription, tests and review for each exam before moving on:

**Batch A — National entrance / university**
- CUET UG
- CAT
- GATE
- NDA
- CDS

**Batch B — SSC**
- SSC CGL
- SSC CHSL
- SSC MTS
- SSC GD
- SSC CPO

**Batch C — Railways**
- RRB NTPC
- RRB Group D
- RRB ALP
- RRB Technician
- RRB JE

**Batch D — Teaching / research**
- CTET
- UGC NET

**Batch E — Banking**
- IBPS PO
- IBPS Clerk
- SBI PO
- SBI Clerk

**Already in separate audit track**
- NEET UG
- JEE Main
- JEE Advanced
- UPSC CSE

For every exam, preserve its native stage/paper/subject structure. If an authority publishes only a notification or broad syllabus, record only what it actually states. Never invent chapter-level breakdowns to make modules look complete.

**Per-exam completion checklist:**
- Official authority and primary-source URL.
- Source document title, publication date and effective exam cycle.
- Stable version ID and exact exam/stage/paper/subject IDs.
- Complete source-backed hierarchy and page/section evidence.
- Separate Hindi translation, reviewed against English source.
- No inferred weightage, question distribution or exam pattern.
- Approval status and reviewer metadata.
- Unit tests for counts, uniqueness, isolation and source evidence.

### Phase 5 — Connect the versioned resolver to live Gemini runtime
- Trace the existing `UniversalPdfPipeline` call chain from Student and Admin entry points to Gemini request/response parsing.
- Resolve the exact approved snapshot before prompt construction.
- Inject only the matching approved snapshot into the universal prompt contract.
- If no snapshot exists, allow document classification and candidate topic extraction, but set syllabus match to unverified and require review.
- Preserve explicit user/Admin exam and paper selections; Gemini may flag mismatch but cannot silently switch identity.
- Validate typed JSON response, canonical IDs, question counts, source references, subject/paper consistency and unknown-topic routes.
- **Exit gate:** tests prove no-syllabus, wrong-exam, wrong-version, wrong-paper, blank-subject and malformed-response cases fail closed.

### Phase 6 — Student/Admin permissions and review workflow
- Both upload paths use the same classification and question-generation contract.
- Student: private personal draft only; no canonical syllabus write, no public publish, no Admin review bypass.
- Admin: private review queue, source/mapping correction, approve/reject, explicit publish.
- Add Firestore collections and rules only after inspecting current schema and existing access patterns.
- Keep answer keys private; never expose them in student-readable documents.
- Record reviewer, timestamp, source version, audit event and publish actor.
- **Exit gate:** Firestore emulator/rules tests cover owner, admin, student, anonymous and cross-user access.

### Phase 7 — Deduplication, coverage and Recent Tests integrity
- Create deterministic normalized question fingerprints with safe collision handling.
- Detect duplicate PDFs/tests/questions across uploads without deleting source records.
- Derive chapter/topic coverage only from validated question-to-node mappings.
- Derive Recent Tests only from published, non-duplicate, eligible tests.
- Keep private, pending, rejected, unverified and duplicate records out of public counts and ranking inputs.
- **Exit gate:** integration tests prove duplicate and unpublished records never inflate counts, coverage or ranks.

### Phase 8 — Full regression, CI and release review
- Run Kotlin unit tests, Android debug build, backend tests and Firestore emulator rules tests where applicable.
- Review Room/Firestore schema compatibility and migrations; no destructive fallback or production migration without separate approval.
- Inspect complete PR diff against `main`; verify no accidental ranking, UI, identity or data changes.
- Fix every failing test before marking a phase complete.
- Keep PR #4 draft until all release gates pass. No merge or Firebase deployment is implied by this plan.

## 3. Current known status (2026-11-09)

| Area | Status | Remaining work |
|---|---|---|
| Exact versioned resolver | Implemented on feature branch | Runtime call-site and persistence integration |
| Universal prompt contract | Implemented on feature branch | Live Gemini call-site, strict response validation |
| JEE Main 2027 Paper 1 | Official source identified; 54-unit heading index added | Full subtopic transcription, Hindi review, tests and Admin approval |
| JEE Advanced | Separate identity enforced | Official year-specific module/source verification |
| NEET UG | Existing versioned module found | Re-audit source, version and approval metadata |
| UPSC CDS / SSC CGL | Legacy catalog entries found | Official-source comparison and correction |
| Other registry exams | Not fully source-backed | Research and build one by one |
| Admin/Student review | Not complete | Shared runtime, private drafts, review queue and permissions |
| Firestore security | Existing rules require audit | Add least-privilege review/discovery rules and emulator tests |
| Deduplication/coverage | Not end-to-end verified | Deterministic fingerprints and published-only aggregation |
| CI/build | Not verified for latest branch head | Fresh Android unit tests and debug build |

## 4. Registry coverage inventory

The current registry has 25 configured IDs:
`neet_ug`, `jee_main`, `jee_advanced`, `cuet_ug`, `upsc_cse`, `ssc_cgl`, `ssc_chsl`, `ssc_mts`, `ssc_gd`, `ssc_cpo`, `rrb_ntpc`, `rrb_group_d`, `rrb_alp`, `rrb_technician`, `rrb_je`, `ctet`, `ugc_net`, `cat`, `ibps_po`, `ibps_clerk`, `sbi_clerk`, `sbi_po`, `nda`, `cds`, `gate`.

Registry presence is not syllabus completion. UPSC CSE is included in the audit track because it is registered even though no dedicated detailed module was confirmed in the current audit.

## 5. Definition of done

An exam is marked **SYLLABUS_READY** only when:
- its exact official source and effective version are recorded;
- all source-listed topics are transcribed with evidence;
- translations are reviewed separately;
- no duplicate IDs or cross-exam/paper contamination exists;
- automated structural tests pass;
- an authorized human reviewer approves the snapshot;
- the live pipeline resolves and injects that exact snapshot;
- security and end-to-end tests pass.

Until then, its status must remain `SOURCE_IDENTIFIED`, `TRANSCRIBED`, `PENDING_REVIEW` or `SYLLABUS_NOT_LOADED`, as appropriate. Never display an incomplete module as official or complete.

## 6. Next phase reserved: generated-question rating + balanced paper engine

This phase starts only after the JEE syllabus source tree is transcribed, structurally tested and exact-version resolver integration is safe. It must not be mixed into syllabus approval.

### 6.1 Question rating (0–100), not a single AI opinion

Each generated question receives a transparent multi-dimensional score. The score is a review aid, not proof that the answer is correct.

| Dimension | Weight | What is measured |
|---|---:|---|
| Answer correctness | 25 | Independent solution/check, answer-option consistency, numerical tolerance and unit correctness |
| Syllabus alignment | 20 | Exact approved topic IDs, scope and version match |
| Conceptual quality | 15 | Tests the intended concept, not irrelevant wording or trivia |
| Clarity and completeness | 10 | Unambiguous stem, sufficient data, no missing conditions |
| Explanation quality | 10 | Valid derivation, steps, units and consistency with answer |
| Option/distractor quality | 8 | Plausible distinct distractors; no duplicate or giveaway options |
| Exam-pattern fit | 7 | Question type, marks, format and level fit the selected exam/paper |
| Originality / duplication | 5 | Exact/near-duplicate risk against existing question bank |

Rules:
- Hard failures override the numeric score: missing/contradictory answer, invalid numeric answer, wrong canonical exam/paper, unapproved topic claimed as official, missing required diagram, malformed options or confirmed duplicate cannot be auto-approved.
- A high score never overrides a hard failure or human approval gate.
- Store per-dimension scores, evidence, validator version, model name/version, prompt version, timestamp and review outcome. Do not store only a final opaque number.
- Suggested routing: 90–100 eligible for priority human review; 75–89 standard review; 60–74 correction required; below 60 reject/regenerate. These are proposed thresholds and require calibration on a labelled sample before production use.
- Difficulty (Easy/Medium/Hard) is separate from quality. A poor question is not made good by being difficult.
- Do not treat Gemini self-reported confidence as independent answer verification.

### 6.2 Balanced generation algorithm

Balance must be calculated for the complete requested paper, not independently per small API batch.

1. Resolve the exact exam, version, stage, paper, subject, section, question type and marking scheme first.
2. Build target counts using a deterministic largest-remainder allocation so Easy + Medium + Hard exactly equals requested count. Respect exam-specific configured ratios; never invent a ratio when the exam configuration is missing.
3. Build a second allocation for topic coverage from the approved syllabus snapshot. Use explicit Admin targets or a documented sampling policy; do not invent official chapter weightage.
4. Apply question-type and section quotas from the exam's verified pattern. A question cannot be counted in a section that does not support its type.
5. Generate in batches, but maintain one paper-level ledger of accepted, rejected, duplicate, deficient and remaining slots.
6. Validate every candidate, then select candidates against the remaining quotas. Do not replace arbitrary questions or silently substitute an excess difficulty category.
7. Regenerate only the exact missing slots, with prompt constraints for the missing level/topic/type. Deduplicate regenerated candidates against both the current paper and the existing bank.
8. If a quota cannot be met, return a partial draft with explicit unmet counts and NEEDS_REVIEW; never fabricate questions or mislabel difficulty to force a pass.
9. Re-run final whole-paper checks: exact question count, exact marks, duration, section constraints, difficulty distribution, topic coverage, answer validation, duplicate checks and no cross-exam contamination.
10. Preserve all generation attempts and validation outcomes for audit. Only Admin approval can publish.

### 6.3 Known current defects to fix in that phase

- Existing validateBatchDifficulty uses a tolerance that can allow a paper to pass despite materially incorrect small-batch ratios.
- The current PDF pipeline rebalances only the first deficient difficulty level and may replace the first available excess-level question, or an arbitrary question when no excess level is found. This does not guarantee exact quotas.
- Current heuristic difficulty estimation uses question length, numbers and keyword counts; it is not a reliable difficulty classifier by itself.
- Current quality score averages six coarse checks and assigns source confidence as 100 by default. This can overstate confidence and must be replaced by evidence-backed dimensions.
- Current parser can infer paper ID from the first configured paper rather than the exact selected paper. The exact route must be passed through and persisted.
- Current batch indexing assumes a batch size of 10 in response parsing; this must use the actual configured batch size to avoid section/index drift.

### 6.4 Required tests and release gates

- Largest-remainder allocations sum exactly to requested count for all supported ratios and counts, including 1, 2, 3, 7, 20, 75 and 180.
- Difficulty/type/section/topic quotas never overlap or exceed paper capacity.
- Regeneration cannot increase the count beyond requested count and cannot replace a valid candidate without a recorded reason.
- Impossible quotas return explicit unmet slots; no silent fallback or fabricated questions.
- Duplicate candidates are excluded across all batches and prior bank records.
- Hard failures cannot be auto-approved regardless of numeric rating.
- Wrong exam/version/paper/subject and unapproved syllabus nodes fail closed.
- Final paper totals, marks, negative marking, timer and section rules match the verified exam configuration.
- Golden tests cover known valid and invalid questions with human-reviewed expected ratings.

**Order of work:** (A) complete JEE Main detailed syllabus and independent JEE Advanced version module; (B) wire exact approved resolver into Gemini runtime; (C) finish the question-rating engine; (D) replace current rebalancer with paper-level quota allocation; (E) run regression and build/CI. Do not skip from A to production or merge unfinished phases.

## Universal question-to-syllabus mapping prompt (NEET first)

Implementation update:
- Added `UniversalPdfPromptContract.buildUniversalQuestionMappingPrompt(...)`.
- The prompt is exam-neutral. NEET is the first planned runtime route; JEE and other exams reuse the same mapping contract only with their own exact approved syllabus snapshots.
- Mapping is restricted to exact IDs in the approved versioned hierarchy. No fuzzy IDs, invented chapters/topics, or inferred official status.
- Mapping depth is evidence-based: chapter-only matches remain chapter-only; topic/subtopic IDs stay null when not supported.
- Each input question must appear exactly once in the mapping result. Mapping preserves question content and does not attempt answer verification.
- Missing/unapproved syllabus blocks canonical mapping. Raw labels may be returned only as non-canonical discovery candidates.
- Every result remains private and reviewable; `officialPromotionAllowed=false` and `publishAllowed=false`.
- Added unit tests for NEET-first use, exact-ID-only mapping, one-to-one question preservation, and no-snapshot blocking.

Not yet complete:
- Runtime invocation from the PDF pipeline after extraction.
- Strict JSON parsing and validation of mapping response against the actual input question IDs and approved syllabus IDs.
- Persistence of mapping results and mapping provenance.
- Admin review/approval workflow and coverage aggregation.
- Build and test execution on this branch.

Do not mark mapping as operational until these runtime and verification steps pass.


## 7. Gemini credential routing: owner key + user BYOK

### 7.1 Required product behavior

AK TEST SERIES must support two credential sources without creating two prompt systems:

- Owner-managed Gemini: the owner's key is stored as a GitHub Actions Secret for deployment/CI handoff, then provisioned into Firebase Secret Manager for the live backend. The Android APK must never contain this key or read GitHub Secrets.
- User BYOK: a user can paste a personal Google AI Studio Gemini API key in Settings. The key is held only for that user's request/session or encrypted locally; it is never uploaded to Firestore, analytics, Crashlytics, logs, or source control. If sent through the backend proxy, it is request-scoped, never persisted, and redacted from logs.
- One prompt contract: both credential sources call the same Gemini request executor with the exact same already-built prompt, system instruction, model policy, response schema, timeout/retry policy and downstream validators. Credential selection changes transport/authentication only, not prompt content or parsing rules.
- One processing pipeline: PDF extraction, classification, question extraction, syllabus mapping, answer verification, quality checks, duplicate checks and review routing remain common.

### 7.2 Target request flow

```text
Android UI / Admin PDF flow
  -> CredentialResolver (OWNER_MANAGED or USER_BYOK)
  -> Shared GeminiRequest {model, prompt, systemInstruction, responseSchema, taskId}
  -> Authenticated Firebase Callable Function (Gemini proxy)
       -> OWNER_MANAGED: Firebase Secret Manager GEMINI_API_KEY
       -> USER_BYOK: request-scoped key; never persisted
  -> Google Gemini generateContent
  -> normalized Gemini response
  -> same parser + strict task-specific validator
  -> same private draft / Admin review path
```

The owner key in GitHub Secrets is not a runtime credential source for an installed Android app. GitHub Actions can use it only during a trusted deployment step to provision/update the Firebase Secret Manager secret. The deployed Cloud Function reads the secret at runtime. Never inject it into Gradle BuildConfig, resources, APK assets, Remote Config or Firestore.

### 7.3 Current audit findings (verified from branch source)

- UniversalPdfPipeline.processPdfForExam currently resolves apiKey ?: BuildConfig.GEMINI_API_KEY.
- callGeminiApi currently calls Gemini directly from Android using HttpURLConnection.
- The current request places the key in the URL query string. This commit changes it to the x-goog-api-key request header to reduce URL/log exposure.
- The Admin PDF screen calls the pipeline without an explicit apiKey; therefore it currently relies on the BuildConfig fallback.
- .env.example contains only a placeholder. The Android CI workflow copies it to .env; it does not provision a live backend secret.
- The repository already has a Firebase Functions codebase at backend_functions, configured in firebase.json, so a server-side Gemini proxy can be added there without putting the owner key in the APK.
- User BYOK settings UI and encrypted key storage are not yet implemented.
- The live PDF pipeline still does not invoke the new universal syllabus mapping prompt or strict mapping validator.

### 7.4 Implementation phases and acceptance gates

**G1 — Credential contract and storage**
- Add explicit provider modes: OWNER_MANAGED, USER_BYOK, DISABLED.
- Add Settings UI: provider selection, masked key entry, Test Key, Save, Replace, Remove, usage warning and current provider status.
- Store user BYOK locally using Android Keystore-backed encryption; never store it in Firestore, Room question records, logs or crash reports.
- Never silently fall back from a failed user key to the owner's paid key. Require explicit user choice.
- Acceptance: key never appears in APK resources/BuildConfig, logs, Firestore or exported backups; clear/remove works; invalid key gives a safe actionable error.

**G2 — Owner-managed server proxy**
- Add an authenticated Firebase Callable Function in backend_functions using a Firebase Secret Manager secret named GEMINI_API_KEY.
- Owner-managed mode is Admin-only; verify Firebase Auth and server-side admin claim/owner policy.
- User BYOK mode may pass the user's key only in the callable request over TLS; do not persist or log it. Prefer request-scoped use and immediate discard.
- Add request size, model allowlist, timeout, rate limits, per-user quotas, task allowlist and audit metadata that excludes credentials and prompt-sensitive PDF content.
- Enable Firebase App Check after the Android App Check provider is configured and emulator/test coverage exists.
- GitHub Actions may provision the Firebase secret during a protected deployment using GitHub Secrets, but the runtime function must read Firebase Secret Manager, not GitHub.
- Acceptance: owner key is absent from APK and client network calls; non-admin cannot invoke owner mode; BYOK is never stored; unauthenticated calls fail.

**G3 — Shared Gemini executor**
- Replace direct Android Gemini HTTP calls with one GeminiRequestExecutor interface.
- Build prompt/system instruction/JSON schema once before credential routing.
- Implement one backend transport for owner and BYOK credential modes. The provider must not modify prompt text or task parameters.
- Normalize HTTP errors, quota errors, model-not-found, invalid-key, safety-block and malformed-response results.
- Use the same model allowlist and retry policy for both modes; no unsupported model-name guessing.
- Acceptance: tests compare the exact serialized prompt and schema for owner vs BYOK routes; only credential source differs.

**G4 — Settings and Admin integration**
- Add a visible AI provider status in Settings and Admin PDF processing.
- User BYOK is opt-in and belongs to the signed-in user/device. Owner-managed mode is shown only to authorized Admins.
- Add a safe Test Connection action that does not expose key fragments.
- Admin PDF flow must pass the selected credential mode explicitly; remove the current implicit BuildConfig key fallback only after the backend proxy is live.
- Acceptance: no silent credential switching, no accidental owner-key use for student requests, and no regression to non-AI app features.

**G5 — Universal PDF pipeline integration**
- Preserve one shared task/prompt contract for extraction, mapping, answer verification and quality review.
- Run document classification first; resolve exact exam/stage/paper/subject; resolve only the approved versioned syllabus snapshot.
- Execute question extraction, then universal mapping as a separate Gemini task, then independent answer verification as a separate task. Do not merge mapping and answer correctness into one field.
- Strictly validate response JSON against input question IDs and the exact approved syllabus IDs. Fail closed on missing, duplicate, unknown or cross-exam IDs.
- Persist private drafts with prompt version, model, provider mode (not API key), source hash, syllabus version, validator version and review status.
- Acceptance: NEET end-to-end tests pass; no approved syllabus means no canonical IDs; no automatic publishing.

**G6 — Test, security review and release**
- Kotlin unit tests for credential resolution, prompt identity, JSON parsing, mapping and answer conflicts.
- Firebase Functions unit/emulator tests for auth, owner-only mode, BYOK request handling, quota, rate limit and error normalization.
- Verify APK/source scans contain no real key; test log redaction and backup exclusion.
- Run Android unit tests, debug build, backend tests and Firestore/Functions emulator tests.
- Keep feature branch and draft PR until all gates pass; no production deploy or main merge without separate approval.

### 7.5 Immediate implementation order

1. Build Firebase Gemini proxy and tests.
2. Build shared Kotlin Gemini executor and remove direct BuildConfig.GEMINI_API_KEY use.
3. Add encrypted user BYOK settings and Admin-only owner mode.
4. Connect existing universal document classification and question extraction prompts through the shared executor.
5. Wire approved syllabus resolver + universal mapping prompt + strict validator.
6. Add separate answer-key extraction and independent answer-verification stages.
7. Finish NEET end-to-end verification before enabling JEE on the same engine.

**Do not mark dual-key support complete until G1–G6 pass.** The current header change is a transport hardening step only; it does not make the current BuildConfig owner key safe for production.


### 7.6 Implementation status after initial integration pass (2026-11-09)

**Code added on the feature branch:**
- Added backend_functions/geminiProxy.js as an authenticated Firebase Callable Function using Firebase Functions v2 and a Firebase Secret Manager parameter named GEMINI_API_KEY.
- Owner-managed credential mode is restricted to the verified owner email or an Admin custom claim.
- User BYOK credential mode accepts a request-scoped key, does not write it to Firestore, and uses the same Gemini generateContent request path.
- Added a per-user minute rate limit, prompt/model/task allowlists, request-size limits and normalized key/quota/service errors.
- Added app/src/main/java/Com/ak/testseries/exam/GeminiRequestExecutor.kt as the shared Android transport.
- Updated UniversalPdfPipeline to call the shared Firebase proxy for both ordinary extraction/generation and targeted difficulty regeneration. The pipeline no longer reads BuildConfig.GEMINI_API_KEY.
- Moved the direct Gemini API key from URL query string to the x-goog-api-key header in the transitional direct-call code before replacing the pipeline call path.
- Registered generateGeminiContent in backend_functions/index.js.

**Still not complete / not yet verified:**
- User BYOK Settings UI, Android Keystore-backed local storage, Test Key, Replace and Remove controls.
- Firebase Secret Manager provisioning for GEMINI_API_KEY. A GitHub Actions Secret alone is not available to a running Cloud Function; a protected deployment workflow must provision the Firebase secret, or it must be set manually in Firebase.
- Firebase Functions deployment and emulator tests. The new callable is not live until deployed.
- Firebase App Check enforcement and production rate/quota policy review.
- Strict structured response schema wiring from every task contract.
- Runtime calls to the document classification, approved syllabus resolver, universal mapping prompt and independent answer-verification prompt.
- Strict mapping/answer JSON parsing, persistence and private review workflow.
- Android build, Kotlin unit tests and backend syntax/emulator tests have not yet been run for this integration batch.

The current default route without a supplied user key selects OWNER_MANAGED and therefore succeeds only for an authenticated authorized Admin after the backend secret is configured and the function is deployed. A normal user must use USER_BYOK after the settings UI is implemented. Do not describe BYOK or owner-managed Gemini as production-ready until the missing gates above pass.


### 7.7 Protected GitHub Secrets handoff workflow

A manual-only workflow was added at .github/workflows/deploy-gemini-proxy.yml. It requires the explicit confirm_deploy input and the protected production GitHub Environment. It reads three repository/environment secrets:
- GEMINI_API_KEY: owner's Google AI Studio key; piped to Firebase Functions Secret Manager and never written to the repository.
- FIREBASE_PROJECT_ID: target Firebase project ID.
- FIREBASE_SERVICE_ACCOUNT_JSON: deployment service-account JSON with the required Firebase/Cloud Functions/Secret Manager permissions.

The workflow provisions the Firebase secret and deploys only generateGeminiContent. It is not run automatically and has not been executed. Before enabling it, configure the production Environment protection rules and verify the service account's least-privilege IAM permissions. The running function reads the key from Firebase Secret Manager; it does not read GitHub Secrets directly.


---

# Branch audit refresh — PDF, prompt contract and syllabus (2026-11-12)

**Branch:** `feature/universal-pdf-prompt-contract-v1`  
**Audited HEAD:** `87dfb8734a0bbb05afb9b56ade5d1e79be6d3e46`  
**Main HEAD:** `b38bd497434dd24438b37b3de87bd26b4fbe98f8`  
**Git comparison:** 139 commits ahead, 86 behind; merge base `80c5ef5c032b35d0937133efc09bf517e58b90d4`. This branch is diverged from main.

## Rules
- Keep work on this feature branch. Do not merge to main or deploy the Gemini proxy/production Firebase resources without explicit approval.
- Preserve the existing PDF pipeline, session recovery, user data, private vault and review flow.
- No invented syllabus topics, marks, question answers, source references or translations.
- Do not treat source-identified-but-unapproved data as official. Resolver must fail closed unless exact exam/version/stage/paper/subject snapshot is APPROVED with reviewer and timestamp.
- Student BYOK and Admin-managed Secret Manager credentials remain isolated; no Admin key in Android.
- Use the existing Gemini proxy/executor and existing UI. Avoid creating duplicate transports or duplicate API Management screens.

## Current implementation evidence and open work

The existing plan records a Firebase Callable proxy `generateGeminiContent`, server Secret Manager parameter `GEMINI_API_KEY`, owner-managed and request-scoped BYOK modes, a shared Kotlin `GeminiRequestExecutor`, and routing from `UniversalPdfPipeline`. It also records that Secret Manager provisioning/deployment workflow was added but not run. These are implementation notes, not proof that the deployed production function is live.

### P1. Live provider/proxy security
- [ ] Verify current branch code and exact callable export; test authenticated Student BYOK and owner/Admin mode separately.
- [ ] Configure Secret Manager and protected deployment environment only after explicit approval; run the manual workflow only with explicit confirmation.
- [ ] Verify Firebase App Check, auth claims, owner email policy, least-privilege IAM, rate limits, request size, model/task allowlists and secret redaction.
- [ ] Test missing/invalid key, 401/403/429/5xx, timeout, malformed/empty response and provider outage. No silent fallback.

### P2. Prompt contract runtime coverage
- [ ] Inventory every Gemini call site: classification, extraction, generation, syllabus mapping, answer-key extraction, answer verification, difficulty regeneration and legacy pathways.
- [ ] Ensure every supported task uses versioned `UniversalPdfPromptContract` templates and structured response schemas.
- [ ] Validate schema parsing, Hindi/English/bilingual parity, exact question count, canonical exam/stage/paper/section IDs, supported question type, option count, marks and negative marks.
- [ ] Keep independent answer verification separate from question generation; conflicts become CONFLICT/UNVERIFIED and require review.
- [ ] No model-generated chain-of-thought or hidden reasoning stored/displayed.

### P3. Syllabus data correctness
- [ ] Finish JEE Main 2027 Paper 1 detailed topic/subtopic/practical/experimental transcription from official NTA source with exact page citations.
- [ ] Add reviewed Hindi translations, canonical IDs, uniqueness checks and subject boundaries.
- [ ] Add Paper 2A and 2B as separate exact modules only when required and source-backed.
- [ ] Research JEE Advanced from its own official year-specific brochure; never reuse JEE Main structure.
- [ ] Build a readiness matrix for all supported exams: official source, year, language, topics/subtopics, approval, last review.
- [ ] Keep unverified exam/syllabus entries disabled for official generation.

### P4. Resolver and app integration
- [ ] Wire `VersionedSyllabusResolver` into Admin and Student PDF classification, topic mapping, generation, persistence and publication.
- [ ] Exact key must include exam + version + stage + paper + subject; no fuzzy aliases or default syllabus.
- [ ] Persist snapshot version and source page evidence with every question.
- [ ] Add Room/Firestore draft/review persistence, migration tests, access rules and Admin approve/reject/correct workflow.
- [ ] Confirm the runtime pipeline never treats the current 54-unit JEE heading index as a complete approved topic tree.

### P5. PDF extraction and real document handling
- [ ] Test original PDF bytes through supported Gemini document input/File API path where appropriate.
- [ ] Handle text, scanned, mixed, diagram, equation and table pages with page-level boundaries.
- [ ] OCR must be a real configured engine; if unavailable or confidence is low, mark review-required rather than fabricating text.
- [ ] Add file signature/MIME, size/page limits, storage lifecycle, retries, cleanup and session recovery tests.
- [ ] Preserve original source and page references through parsing, deduplication, validation, Room, Firestore and Admin review.

### P6. Student/Admin API Management experience
- [ ] Reuse the existing API Management page and integrate it into the existing Sidebar; do not create a second page or a new Settings route.
- [ ] Keep Student and Admin keys separate; Student key encrypted locally with Android Keystore, Admin key server-only.
- [ ] Implement real Test Key / replace / remove behavior and truthful provider health. Do not persist raw keys in ordinary preferences or Firestore.
- [ ] Verify key lifecycle, account switching, backup exclusion and log/crash redaction.

### P7. Verification and rollout
- [ ] Run Kotlin unit tests, backend Node tests, Firestore emulator tests and exact-head Android build.
- [ ] Use authorized representative PDFs and golden expected extraction/mapping fixtures.
- [ ] Verify duplicate detection, answer-key separation, publication authorization and no cross-exam leakage.
- [ ] Record current test inventory and exact CI run. Historical test counts from other branches are not interchangeable.
- [ ] No production deployment, rule/index deployment or main merge without separate owner approval.

## Cross-branch reconciliation
Main has newer subject-routing fail-closed fixes and a deterministic `UniversalQuestionGenerationPlanner`; review those before integrating. The Gemini connector branch has the broader Student/Admin connector and Groq/Cerebras roadmap. Reuse shared contracts rather than creating duplicate transports or syllabus systems.

## Audit status
All P1–P7 items remain open until current-head evidence is attached. The branch's prior implementation plan records code and workflow creation, but its own note says deployment and full Android/backend/emulator verification were pending. This refresh did not run tests or deploy services.
