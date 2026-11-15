# AK TEST SERIES — App-First Integration Matrix

**Branch:** `integration/root-to-fruit-master-2026-11-13`  
**Baseline HEAD audited:** `282e3c6d83c78ae112bd2cda620dcf9611d6698b`  
**Audit date:** 2026-11-15  
**Audit mode:** GitHub static source/tree inspection only  
**Phase:** 0 — Baseline and Safety  
**Phase 0 result:** STATIC INVENTORY RECORDED; runtime verification and implementation not yet performed.

## 1. Scope and evidence limits

This matrix maps the 22 app-first workstreams to the current integration-branch code, canonical candidates, observed gaps, and next action. It is an inventory, not a claim that features are complete.

The repository tree contains 231 files and 102 Kotlin source files under the Android app source tree (including PDF/OCR-related files that are excluded from this plan). Relevant existing code includes:
- MainActivity and a single large NEETViewModel.
- Room database, entities, DAOs, and DatabaseRepository.
- Firebase remote data source and repository implementations.
- Exam registry, hierarchy/configuration, scoring, validation, and AI context/planner.
- Student/Admin/community/private-vault screens.
- Existing unit tests for Room migration, authorization boundaries, account switching, exam scoring, registry/hierarchy, and ranking.

No local checkout, Android SDK/Gradle build, emulator/device run, Firebase Emulator, live Firebase Console/API inventory, or live backend call was available during this audit. Therefore no runtime/build/test result is asserted.

## 2. 22-workstream status matrix

Status legend: **PARTIAL** = relevant code exists but integration/coverage is incomplete; **BLOCKED** = a concrete dependency/safety issue prevents a safe end-to-end claim; **UNVERIFIED** = source exists but runtime behavior has not been tested; **NOT STARTED** = no implementation in this phase.

| ID | Workstream | Existing code / canonical candidate | Static finding | Initial status | Next action |
|---|---|---|---|---|---|
| APP-01 | Codebase and screen inventory | `MainActivity.kt`, `ui/*`, `viewmodel/NEETViewModel.kt` | MainActivity ~551 KB and NEETViewModel ~409 KB; many screens are declared in MainActivity. Inventory recorded; high coupling/large-file risk. | PARTIAL | Map individual screen entry points and state owners before editing. |
| APP-02 | App architecture and data flow | `data/AppDatabase.kt`, `DatabaseRepository.kt`, `data/repository/TestRepositoryImpl.kt`, `domain/*` | Repository abstraction exists; ViewModel directly owns Room/repository and many Firebase operations. Direct Firebase calls also appear in UI files. | PARTIAL | Select one canonical data path per feature; remove duplication only after call-site proof. |
| APP-03 | Authentication and sessions | `domain/auth/AuthManager.kt`, `data/auth/FirebaseAuthManagerImpl.kt`, `FirebaseUserRepositoryImpl.kt`, MainActivity | Email/password, Google, magic-link and session restoration paths exist. Admin claim check exists in ViewModel. | UNVERIFIED | Trace all login/logout/account-switch flows and run auth/role tests. |
| APP-04 | Exam registry | `exam/UniversalExamRegistry.kt`, `UniversalExamModels.kt` | Registry and many exam definitions exist; prior audit found source-policy and registry mapping mismatches for several exams. | PARTIAL | Reconcile active exam entries against official rules and backend parity before UI freeze. |
| APP-05 | Stage, paper, section | `UniversalExamModels.kt`, `UniversalHierarchyCatalog.kt`, `UniversalExamRegistry.kt` | Hierarchical model/catalog exists; not yet proven every app flow uses it consistently. | PARTIAL | Trace selection → test creation → attempt → scoring identity fields. |
| APP-06 | Subject and syllabus | `SyllabusCatalog.kt`, `NeetUg2026Syllabus.kt`, `Entities.kt:SyllabusData`, syllabus UI | Multiple syllabus/config sources coexist (registry/catalog and legacy SyllabusData). | PARTIAL | Identify canonical source and map all consumer call sites; avoid parallel syllabus truth. |
| APP-07 | AI entry points | `UniversalAIContext.kt`, `UniversalQuestionGenerationPlanner.kt`, `NEETViewModel.kt`, `UploadAiTabScreen.kt` | Shared prompt/context pieces exist, but ViewModel contains direct Gemini key/API paths; local generation quota counter is present. | BLOCKED | Inventory every direct provider call; route only through existing approved backend contract; keep Student controls restricted. |
| APP-08 | Question management | `QuestionValidationEngine.kt`, `UniversalExamAdminScreen.kt`, Admin generation screens, `QuestionEntity` | Validation and Admin UI exist. Question answer data is also represented in local QuestionEntity. | PARTIAL | Map create/edit/delete/validate flows and ensure private answer-key handling. |
| APP-09 | Admin review and publishing | `UniversalExamAdminScreen.kt`, `NEETViewModel.kt`, Admin menu | Review/publish UI and paths exist, but earlier audit found local-only approval state and publisher contract inconsistencies. | BLOCKED | Reconcile all publishing routes; no UI success until durable server publication succeeds. |
| APP-10 | Student test discovery | `FirebaseRemoteDataSourceImpl.kt`, `TestRepositoryImpl.kt`, `TestVisibilityManager.kt` | Remote listener and Room persistence exist; discovery validates question payload and filters tests. | UNVERIFIED | Test visibility across public/private/under-review/admin/owner identities. |
| APP-11 | Test-taking engine UI | `NEETViewModel.kt:startTest/selectAnswer`, MainActivity exam/result screens, `ExamScoringEngine.kt` | Start/answer flows and section constraints exist. | UNVERIFIED | Verify timer, navigation, section caps, app background/restore, and multiple exam rules. |
| APP-12 | Progress, resume, submit | `NEETViewModel.kt`, `AttemptEntity`, Room DAOs, callable in `backend_functions/index.js` | Attempt session/idempotency fields and server submission path exist; earlier audit found progress persistence gaps and client preview fallback. | BLOCKED | Trace answer-change autosave, resume, timeout, duplicate submit, offline pending, and server-failure states. |
| APP-13 | Result and solutions | MainActivity ResultScreen, `NEETViewModel.kt:fetchTestSolutions`, backend callable | Result and solution retrieval functions exist; answer authorization and payload leakage require strict end-to-end verification. | BLOCKED | Ensure no client-authoritative result and no solution-key exposure before authorized review. |
| APP-14 | Ranking and eligibility | `ExamScoringEngine.kt`, `backend_functions/examScoring.js`, ranking UI/tests | Ranking/scoring code and tests exist; previous audit marks full Android/backend parity and ranking eligibility open. | BLOCKED | Reconcile ranking eligibility, duplicate attempts, exam keys, tie-breaks, and server authority. |
| APP-15 | Private Test Vault | `PrivateTestsVaultScreen.kt`, `TestVisibilityManager.kt`, Room entities, `NEETViewModel.kt` | Vault UI and local save path exist; TestEntity defaults `isPublic=true`, and generation flags default public/auto-share in ViewModel. | BLOCKED | Make every student generation route private by default; verify owner isolation and no public full package. |
| APP-16 | Community and sharing | `CommunityUI.kt`, `CommunityAdminUI.kt`, ViewModel share/deep-link paths, Firestore rules | Community feed/cards exist; earlier audit found full question payload with answers written to shared collection and no recipient ACL. | BLOCKED | Replace public full payload with metadata-only cards and implement/verify explicit recipient authorization before sharing. |
| APP-17 | Admin panel | MainActivity AdminMenu, `AdminPanelScreen.kt`, `CommunityAdminUI.kt`, `SuperAdminViewModel.kt` | Admin destinations and controls exist; some direct Firestore UI calls and dashboard placeholder-looking defaults require review. | PARTIAL | Trace role gate for every Admin destination/action and bind displayed metrics to real data. |
| APP-18 | Student panel | MainActivity HomeTab, `HomeTabScreen.kt`, `UploadAiTabScreen.kt`, profile/test screens | Student destinations and features exist. | UNVERIFIED | Trace each visible action to real state/repository/backend operation; remove false-success paths. |
| APP-19 | Notifications | MainActivity notification screens, `AdminPanelScreen.kt`, FCM/notification code in ViewModel | Notification UI and send/listener code exist; permission and event correctness not runtime checked. | UNVERIFIED | Trace schedule/publish/result events and notification permission-denied behavior. |
| APP-20 | Offline and synchronization | Room v20, `DatabaseRepository.kt`, `TestRepositoryImpl.kt`, backup/restore modules | Local Room source of truth and remote test sync exist; Room has migrations through 19→20 but also `fallbackToDestructiveMigration()`. | BLOCKED | Test all migration paths, account partitioning, sync conflicts, and destructive fallback risk. |
| APP-21 | UI states and error handling | Compose screens, ViewModel StateFlows, crash handlers | Some state/error infrastructure exists; broad screen-by-screen loading/empty/error/retry audit not complete. | UNVERIFIED | Audit each user-visible action and standardize truthful loading/error/retry states. |
| APP-22 | Regression and final app verification | `app/src/test/java/**`, `app/src/androidTest/**` if present | Many unit tests exist for migration, auth boundaries, scoring, ranking, backup and hierarchy. No tests/build were run in this static audit. | UNVERIFIED | Run Gradle unit tests, Android build, emulator tests and critical end-to-end flows when runtime is available. |

## 3. Baseline architecture map

### App entry and navigation
- `app/src/main/AndroidManifest.xml` registers `.NEETApplication`.
- `MainActivity.kt` owns Compose setup, screen routing, Student tabs, Admin menu, result/test screens and several feature composables.
- No separate dedicated Navigation graph file was identified in the source tree; navigation is primarily coordinated from MainActivity and ViewModel screen state.

### State and business logic
- `NEETViewModel.kt` is the central AndroidViewModel; it creates `AppDatabase`, `DatabaseRepository`, and `TestRepositoryImpl`, and contains auth/admin state, test flows, generation flows, sharing, submissions and other feature logic.
- `SuperAdminViewModel.kt` exists separately for Super Admin dashboard metrics.
- `UniversalExamRegistry.kt`, `UniversalHierarchyCatalog.kt`, `ExamScoringEngine.kt`, `QuestionValidationEngine.kt`, and `UniversalAIContext.kt` are existing domain candidates to reuse.

### Data layer
- Local: `AppDatabase.kt` (Room schema version 20), `Entities.kt`, `DatabaseRepository.kt`.
- Remote: `FirebaseRemoteDataSourceImpl.kt`, `FirebaseUserRepositoryImpl.kt`, `FirestoreHelper.kt`.
- Interfaces: `TestRepository.kt`, `UserRepository.kt`, `RemoteDataSource.kt`.
- Existing test repository treats Room as UI source of truth and persists remote snapshots/questions locally.

### Authentication and roles
- `AuthManager` interface and `FirebaseAuthManagerImpl` implementation exist.
- Admin custom-claim verification exists in `NEETViewModel.checkAdminCustomClaim()`; every Admin destination/action still needs call-site verification.
- MainActivity creates auth and user repository instances in Compose; dependency lifecycle/singleton policy needs review.

### Test evidence inventory
Existing unit test files include:
- `MainActivityTest.kt`
- `AccountSwitchingPrivacyTest.kt`
- `AppDatabaseMigrationTest.kt`
- `AuthorizationBoundaryTest.kt`
- `ExamScoringEngineTest.kt`
- `ExamPatternVerificationTest.kt`
- `UniversalRankingAndIdentityTest.kt`
- `UniversalRankingAndIsolationTest.kt`
- backup/restore and crash-resilience suites.

Presence of tests is not evidence that they pass. No Gradle task was executed in this audit.

## 4. Confirmed high-priority blockers from source inspection

| Finding ID | Severity | File / symbol | Finding | Required action |
|---|---|---|---|---|
| APP-GAP-001 | CRITICAL | `NEETApplication.kt:27-35` | Programmatic Firebase fallback uses a Firestore database-like identifier as Project ID and a placeholder-looking API key/App ID. This cannot establish valid Firebase identity. | Resolve Firebase identity separately; remove invalid fallback only after correct app config is proven. |
| APP-GAP-002 | HIGH | `FirestoreHelper.kt:22-34` | If named Firestore initialization fails, code falls back to default Firestore instance, risking silent data split. | Make database selection explicit and fail visibly; coordinate with Firebase identity audit before changing. |
| APP-GAP-003 | HIGH | `NEETViewModel.kt:2006, 4672+` | Student generation state defaults to public and auto-share enabled; generation routes also write visibility fields. | Private-by-default is a prerequisite before student AI integration. |
| APP-GAP-004 | CRITICAL | Community share/deep-link flow (prior Section 23 audit) | Full shared question payload includes answer/explanation; shared collection is readable by authenticated users; recipient ACL absent. | Do not wire/enable full-test sharing until secure grant design is implemented. |
| APP-GAP-005 | HIGH | `Entities.kt:23` | `TestEntity.isPublic` defaults true, so creation paths that omit explicit visibility can expose tests. | Audit all constructors and set safe explicit visibility policy. |
| APP-GAP-006 | HIGH | `AppDatabase.kt:254` | `fallbackToDestructiveMigration()` remains configured alongside explicit migrations through v20. | Inventory old versions and backup/recovery behavior; remove/replace only after migration coverage proof. |
| APP-GAP-007 | HIGH | `TestRepositoryImpl.kt` + `FirebaseRemoteDataSourceImpl.kt` | Remote question JSON is parsed into local QuestionEntity including `correctAnswer` and `explanation`; test-taking/review access boundary must be verified. | Separate test-time question projection from authorized solution payload. |
| APP-GAP-008 | HIGH | MainActivity / AdminPanelScreen / CommunityUI | Several UI files directly call `FirebaseFirestore.getInstance()` rather than the shared configured helper. | Inventory every direct Firestore call and unify only after named DB identity is settled. |
| APP-GAP-009 | HIGH | `NEETViewModel.kt` direct Gemini paths | Provider keys/API calls remain in Android code, while the target design requires server-side shared router and Admin policy. | Keep AI app work blocked from provider-control integration until secure backend contract is available. |
| APP-GAP-010 | MEDIUM | `MainActivity.kt`, `NEETViewModel.kt` | Very large files combine navigation/UI/state/business logic; high regression and integration coupling risk. | Make narrow changes; extract only when a safe boundary is proven, avoiding wholesale rewrite. |
| APP-GAP-011 | HIGH | `FirebaseRemoteDataSourceImpl.kt:hasVerifiedQuestionPayload` | Public test discovery requires question payload entries to claim Gemini generation and sourceVerified; compatibility with non-Gemini/manual/admin test sources may be overly restrictive. | Verify intended source policy and tests before changing filter; do not weaken validation blindly. |
| APP-GAP-012 | HIGH | `SuperAdminViewModel.kt:AdminDashboardState` | Dashboard state initializes server status and some metrics with display defaults; must verify all visible values are replaced by real query results or shown as unavailable. | Audit every dashboard metric and eliminate misleading placeholder-like defaults. |

## 5. Dependency and sequencing decisions

1. **Firebase identity is a cross-cutting blocker.** Do not change Firestore helper/fallback or direct Firestore calls until the authoritative Firebase Project ID and named Database ID are proven by the separate Firebase audit.
2. **Privacy precedes feature wiring.** Private-by-default AI tests, answer-key projection, and secure community access must be addressed before enabling those Student flows.
3. **Use existing canonical candidates.** Room + DatabaseRepository + TestRepositoryImpl for local test state; FirebaseRemoteDataSourceImpl for current remote test discovery; UniversalExamRegistry/hierarchy for exam identity; existing scoring/validation engines for shared domain rules. Validate call sites before deciding final ownership.
4. **Do not use client-side data as authoritative.** Result, answer-key authorization, publishing, quotas and ranking must continue to rely on secure server contracts.
5. **PDF/OCR remains excluded.** Existing PDF/OCR files may be present in the repository but are not part of this app-first plan.

## 6. Phase 0 exit checklist

| Item | Result |
|---|---|
| Reconfirm exact integration branch HEAD | PASS — `282e3c6d83c78ae112bd2cda620dcf9611d6698b` |
| Inventory Android Kotlin source tree | PASS — 102 Kotlin files in app source tree, including excluded PDF/OCR paths |
| Map main app entry/navigation | PASS — MainActivity + ViewModel-driven Compose routing identified |
| Map data and repository layers | PASS — Room, repository interfaces/implementations, Firebase remote source identified |
| Map auth and Admin role candidates | PASS — AuthManager/FirebaseAuthManagerImpl and custom-claim check identified |
| Map exam/question/scoring candidates | PASS — Registry, hierarchy, validation, scoring and AI context identified |
| Map 22 workstreams to source and status | PASS — recorded above |
| Record blockers and dependencies | PASS — APP-GAP-001 through APP-GAP-012 |
| Runtime build/unit/emulator/E2E verification | NOT RUN — no local Android/Gradle runtime available through this GitHub-only audit |
| Firebase live identity/data inventory | BLOCKED — separate Firebase audit; requires authorized Console/API evidence |

**Phase 0 assessment:** Static baseline and integration map are recorded. Phase 0 is complete for repository source inventory only; runtime baseline and live Firebase checks remain unverified/blocked. No application code was changed in this phase.

## 7. Next phase

Proceed to Phase 1 (App foundation) only after reviewing the canonical data/navigation decisions and resolving or explicitly accepting blockers that affect authentication and Firebase identity. Begin with a narrow, low-risk integration slice; do not refactor the entire MainActivity or NEETViewModel in one batch.
