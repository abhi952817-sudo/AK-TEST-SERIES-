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
