# Phase 1 - Extended Branch and Unique-File Audit
Date: 2026-11-20
Repository: abhi952817-sudo/AK-TEST-SERIES-
Audit base: integration/ak-test-series-complete-2026-11-20
Integration snapshot: c58438cf2235436c25c37b69fb32f8c2b20c8189
Main baseline remains: 29360378146d7c354377b4ea16ff01a7070057ef

## Scope and safety
This is a read-only comparison report plus documentation on the integration branch. No application source files were changed by this report. No PR was merged to main, no branch was deleted, no Firebase deployment or database switch was performed, and no production data was accessed. Branch comparison counts are relative to the integration snapshot, not a statement that every changed line has received a full semantic review.

## Current integration CI
Workflow run: 44714010245 (Android CI, run 698).
Final result for integration code commit c58438cf2235436c25c37b69fb32f8c2b20c8189: completed / success. The build job completed; JavaScript syntax, Backend Scoring Tests, Android Unit Tests, Gradle build, and APK upload all succeeded. The subsequent commits b999f7f and 081c7c9 update only this Markdown report; no application source changed in those two commits.

## Unique branch comparison inventory
The following counts and changed-file paths are GitHub compare results against the integration branch. "Ahead" means commits unique to the compared branch relative to integration; "behind" means integration has commits absent from that branch. Large diverged branches are not safe for wholesale merging.

| Branch | Ahead | Behind | Changed files | Initial disposition |
|---|---:|---:|---:|---|
| audit/all-exam-scoring-ranking-2026-11-13 | 42 | 60 | 10 | Extract shared scoring policy/golden vectors selectively; exclude destructive Actions cleanup workflow |
| audit/default-firestore-consolidation-2026-11-15 | 13 | 48 | 2 | Documentation only; stale baseline must be refreshed |
| audit/phase1-repository-consolidation-2026-11-20 | 2 | 2 | 1 | Audit documentation; preserve as historical report |
| copilot/firestore-default-clean | 0 | 47 | 0 | No unique diff against integration; cleanup candidate after references checked |
| copilot/standardize-firestore-default | 3 | 47 | 3 | Firestore target conflict; do not merge before live database inventory |
| feature/community-completion-2026-11-13 | 184 | 49 | 22 | Community feature candidate; extensive overlap, cherry-pick by file/commit only after tests |
| feature/community-development-plan-2026-11-13 | 33 | 52 | 9 | Preserve plan and selected rules/tests; compare against later Community work |
| feature/community-mvp-completion-2026-11-13 | 64 | 49 | 14 | Smaller Community candidate; overlaps Community completion branch |
| feature/ranking-engine-v2-2026-11-06 | 110 | 155 | 14 | Deeply diverged ranking composite; no wholesale merge |
| feature/ranking-universal-integration-v1 | 477 | 155 | 44 | Very large composite; no wholesale merge |
| feature/secure-user-owned-ai-connectors | 0 | 47 | 0 | No unique diff against integration; cleanup candidate after references checked |
| feature/student-universal-ai-connectors-v1 | 60 | 47 | 13 | Student BYOK candidate; inspect encrypted key storage, backup exclusions, quota enforcement and provider contract |
| feature/universal-exam-scoring-v1-2026-11-06 | 148 | 155 | 22 | Scoring/ranking/Room composite; no wholesale merge, review migration and scoring independently |
| feature/universal-gemini-ai-10-phase-2026-11-08 | 392 | 155 | 69 | AI/PDF composite; extract only reviewed components, never merge wholesale |
| feature/universal-pdf-prompt-contract-v1 | 143 | 148 | 46 | PDF route/validation/prompt components useful; broad overlap and deployment workflow blocked |
| fix/community-answer-key-fail-closed-2026-11-19 | 1 | 26 | 1 | Small candidate; inspect exact ViewModel call site and tests |
| fix/community-ci-recovery-2026-11-13 | 75 | 49 | 17 | Community validation/rules candidate; compare with current Community branch |
| fix/fail-closed-configured-scoring-2026-11-19 | 4 | 2 | 2 | Scoring candidate; already represented partly by PR #19; check for remaining unique tests |
| fix/firestore-default-database-2026-11-17 | 4 | 47 | 4 | Firestore target conflict; block pending owner-confirmed database inventory |
| fix/phase1-real-questions-only | 196 | 155 | 53 | Broad exam/AI/security composite; no wholesale merge |
| fix/ranking-unavailable-state-2026-11-19 | 5 | 18 | 3 | Backend ranking unavailable-state candidate; inspect parity with PR #18 |
| fix/remaining-firebase-analytics-2026-11-15 | 201 | 49 | 34 | Broad Community/backup/analytics composite; selectively review backup ownership and pagination |
| fix/remove-client-ai-secret-metadata-2026-11-19 | 2 | 45 | 2 | Small AI metadata security fix; verify already merged equivalent in Main before any further action |
| fix/retain-single-firestore-db-9fa | 6 | 48 | 4 | Firestore target conflict; block pending database inventory |
| fix/strict-numerical-equivalence-2026-11-19 | 3 | 45 | 3 | Backend scoring parity candidate; review exact diff and tests |
| fix/unify-firestore-database | 19 | 47 | 13 | Firestore plus app/backend composite; no merge before database identity decision |
| fix/use-default-firestore-everywhere | 32 | 48 | 17 | Default DB plus backup/storage changes; block database switch, selectively review backup/security code |
| integration/ak-test-series-complete-2026-11-20 | 2 | 0 | 5 | Current controlled integration branch |
| integration/community-firestore-default-2026-11-15 | 207 | 49 | 36 | Large composite; no wholesale merge |
| integration/root-to-fruit-master-2026-11-13 | 18 | 49 | 5 | Documentation plus scoring backend changes; inspect scoring diff independently |
| integration/verified-branch-consolidation-2026-11-19 | 11 | 46 | 3 | Historical audit docs; new report supersedes stale baseline |
| restore-files/removed-env-gitignore-2026-10-19 | 2 | 190 | 1 | Documentation-only remaining-work plan; no env file restoration proposed |
| usi | 4 | 190 | 3 | .env.example and documentation; inspect for secret leakage before retention |

## File-level overlap findings

### Scoring and ranking
- PR #19 changes Android ExamScoringEngine and its unit tests. The fail-closed behavior is now present in integration; backend parity is still a release gate.
- PR #18 changes ranking unavailable-state handling. Verify all Android/UI consumers tolerate null and backend ranking population is consistent.
- PR #5 adds shared/scoring/scoring-policy.v1.json, schema and golden vectors plus Kotlin/Node parity tests. It also adds .github/workflows/cleanup-completed-actions-history.yml, which deletes completed Actions history. That destructive workflow is explicitly excluded.
- PR #2 and the Universal Exam Scoring branch touch Room schema 21, AppDatabase, entities, NEETViewModel, scoring engines and backend functions. The migration must not be taken without the complete 15-to-current migration chain, backup/restore rehearsal, and real-data tests.
- Ranking V2 branches overlap MainActivity, NEETViewModel, backend_functions/index.js, rankingEngine.js, Firestore rules and indexes. Their 110/477 unique commits make wholesale integration unsafe.

### Community
- PR #8 adds Community UI, highlighted text, one-question MCQ UI, moderation UI, exam-subject registry, backend validation, Firestore/Storage rules and tests.
- PR #12 combines Community with backup, Firestore-default, analytics, uploader and runtime changes; it overlaps PR #8 in CommunityUI, moderation, NEETViewModel, backend index, rules and tests.
- PR #7 was merged into a feature branch, not Main. Preserve its useful content through the Community consolidation review, not by merging its parent branch blindly.
- Community subject mapping must be validated against the selected exam. Doubt creation remains non-AI; image upload and one-question quiz rules remain product constraints.

### Firestore and Storage
- Competing branches/PRs target (default) versus two historical named Firestore database IDs. Code alone cannot establish which database contains production data.
- PR #9/#13 propose one named database identity; PR #10/#14 and several fix branches propose (default). These are mutually exclusive until the owner confirms the live project/database and a backup/restore rehearsal is complete.
- PR #10 includes backup/restore runbooks and Cloud Auto Restore security tests; these are useful for review but do not authorize a production DB switch.
- Storage rules and uploader changes overlap Community PRs. Review ownership, MIME/size restrictions, path scoping, and unauthorized read/write tests before selecting patches.

### AI and PDF
- PR #4 adds exact question route resolution, versioned syllabus structures, prompt safety rules and PDF pipeline changes. These are candidates for selective adoption.
- The PR #4 diff removes the old GeminiPdfExtractor that contained placeholder question generation and offline heuristic fallback. Do not restore that code.
- Its new JEE Main 2027 unit index is explicitly marked not approved and headings-only; it cannot be represented as a complete official syllabus.
- The Gemini deployment workflow provisions secrets and deploys a Firebase callable. It remains unexecuted and must stay blocked until explicit deployment approval.
- Large AI branches overlap UniversalAIContext, UniversalPdfPipeline, UniversalExamRegistry, NEETViewModel, MainActivity and database files. Extract isolated, tested pieces only.
- Student AI connector branch needs review of encrypted local key storage, Android backup exclusions, usage/quota enforcement, and provider request routing. Admin credentials must remain server-side.

## Branch cleanup candidates
No branch has been deleted. First-pass cleanup candidates with zero unique file diff against integration:
- copilot/firestore-default-clean
- feature/secure-user-owned-ai-connectors

Before deletion, verify no open PR head/base, workflow reference, deployment environment, or external collaborator dependency. Branches with unique docs or code are retained until their unique changes are either accepted or explicitly rejected. Branch deletion remains a separate destructive operation and is not performed in this audit.

## Current decisions
1. Keep Main unchanged.
2. Keep integration branch as the only controlled consolidation line.
3. Do not merge the large Community, Ranking, Firestore, or AI composites wholesale.
4. Select scoring golden vectors and parity tests from PR #5, excluding the destructive workflow.
5. Review Community PR #8 and #12 at exact-file level; #12 is composite and must not be merged as one unit.
6. Treat Firestore database selection as blocked pending live project/database inventory and owner approval.
7. Extract route resolution, versioned syllabus resolver, strict PDF extraction and fail-closed validation from PR #4 only after compile/test verification.
8. No production deploy, secret provisioning, data migration, or branch deletion in this phase.

## Remaining Phase 1 exit gates
- Complete exact-file semantic review for scoring PRs/branches, Community, Firestore, and AI/PDF.
- Finish Android CI run 44714010245 including Gradle build and APK upload.
- Run targeted backend, Firestore rules, Storage rules and Android tests on the final integration head.
- Reconcile open PR heads/bases and verify no unreviewed unique commits are lost.
- Produce a final branch cleanup list with dependency checks.
- Keep Phase 1 open until all gates are satisfied.


## Targeted semantic review notes (exact changed-file patches)

### PR #5 shared scoring contract
- The JSON explicitly declares status DRAFT_NOT_RUNTIME_WIRED and disables scoring/ranking for listed exams. Keep this as a design/test candidate, not live runtime policy.
- The policy contains conflicting or stale pattern descriptions in the same exam record. Example: NEET registryPatternRules still describes the old optional Section A/B scheme while officialRules describes 180 compulsory questions. These fields must be normalized before any policy is enabled.
- Several exam records are UNVERIFIED or have only partial stage/paper coverage. A registry verified flag must not be treated as official policy evidence.
- Golden vectors are useful only after the schema, exact exam-year-stage-paper-section-question-type resolution, and Kotlin/Node implementations agree. No broad import into runtime yet.
- Exclude cleanup-completed-actions-history workflow entirely; it is destructive and unrelated to scoring correctness.

### PR #8 Community rules and validation
- Positive: server-side exam/subject mapping, bounded text/highlight payloads, no PDF fields, report evidence path scoping, admin-only answer-key reads, and server-mediated answer/comment deletion are security-oriented improvements.
- Required follow-up: inspect callable implementations to prove student create/reply/report flows use trusted server writes; rules alone do not prove the callable validates ownership, rate limits, image ownership, parent counters, or moderation state atomically.
- Rule changes overlap PR #12 and the Community completion branches. Do not merge the same rules/indexes twice or mix their backend index.js revisions.
- User-facing constraints still need UI tests: choose subject first, one image only for doubt, no PDF, quiz is exactly one question with 4 or 5 options and no timer.

### PR #10 and #14 Firestore helper alternatives
- PR #10 fails closed when the resource is not (default), while PR #14 unconditionally uses the default database and removes resource checks. Both choose (default), but neither establishes that production data currently lives there.
- Do not combine both implementations. Select one only after verifying Firebase project ID, all database IDs, actual app/backend configuration, indexes, rules, backup and restore rehearsal.
- PR #10's backup tests and runbooks are useful independent candidates; its Firestore target change remains blocked.

### PR #4 AI/PDF route and extraction
- Positive: exact exam/subject/stage/paper routing; reject ambiguous subject matches; stop when extracted PDF text is empty; no placeholder offline questions; preserve private-draft status.
- Critical design concern: UniversalPdfPipeline sets isExtractionMode = true unconditionally in the inspected patch. That forces the pipeline into extraction behavior even where a generation route may be intended. Split extraction and generation modes explicitly at the call boundary and test both before adoption.
- The pipeline now fails closed when PDF text extraction is empty, which is correct for extraction, but the caller must not label generated content as PDF extraction or invent source provenance.
- VersionedSyllabusResolver stores snapshots in process memory only. It is not a persistent, server-authoritative approval workflow; approved status must be stored and authorized on the backend before production use.
- JEE Main 2027 unit index is explicitly NOT APPROVED and headings-only. It is not a completed syllabus.
- Deployment workflow provisions Firebase secrets and deploys a callable. Keep it unexecuted pending explicit production deployment approval.

### CI and integration status
The extended report was added in b999f7f96c174fdec3416e8614cb7a150ab8fd51 and then updated in 081c7c9c68faac8b0e5cdce434984ea2146bfa6e. Both commits change documentation only. Android CI run 44714010245 for the application-code integration commit c58438cf2235436c25c37b69fb32f8c2b20c8189 completed successfully, including Gradle build and APK upload. The report-only commits were not separately built because they do not change application code.
