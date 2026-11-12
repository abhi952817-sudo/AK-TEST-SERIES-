# All-Exam Scoring, Android/Backend Parity & Ranking Audit

**Audit date:** 2026-11-13  
**Repository:** `abhi952817-sudo/AK-TEST-SERIES-`  
**Base:** `main @ 8f7eee9c4beee7db3e447daeff0325cdb685e512`  
**Working branch:** `audit/all-exam-scoring-ranking-2026-11-13`  
**Boundary:** Audit and draft contract only. No main application source, Firebase, Firestore, production data, ranking records or deployment changed.

## Executive verdict

**RELEASE BLOCKED.** The registry contains 25 exam IDs, 25 stage IDs, 28 paper IDs and 99 section IDs. This is a source inventory, not proof that all official stages, papers or sections are represented. All 25 entries currently have `verified = true` in Kotlin, but that flag has no year/source provenance and is not a defensible official marking verification flag.

The draft canonical contract is in `shared/scoring/scoring-policy.v1.json`; the shared fixture set is in `shared/scoring/scoring-golden-v1.json`. Both are **DRAFT / NOT RUNTIME WIRED**. All exam policies remain disabled for official scoring/ranking until contract adapters and parity tests are implemented. This is deliberate fail-closed behavior in the draft, not a runtime behavior change.

## 1. Exact registry inventory

The following IDs and their current stage/paper/section identifiers were extracted from `UniversalExamRegistry.kt`. Repeated generic section IDs across different exams are retained because they are scoped by exam.

| # | Exam ID | Current Stage IDs | Current Paper IDs | Current section IDs |
|---:|---|---|---|---|
| 1 | `neet_ug` | `neet_main` | `neet_paper_1` | `phy_sec_a`, `phy_sec_b`, `chem_sec_a`, `chem_sec_b`, `bot_sec_a`, `bot_sec_b`, `zoo_sec_a`, `zoo_sec_b` |
| 2 | `jee_main` | `jee_paper_1` | `jee_main_p1` | `phy_sec_a`, `phy_sec_b`, `chem_sec_a`, `chem_sec_b`, `math_sec_a`, `math_sec_b` |
| 3 | `jee_advanced` | `jee_adv_paper_1` | `jee_adv_p1` | `phy_sec_single`, `phy_sec_multi`, `phy_sec_num`, `chem_sec_single`, `chem_sec_multi`, `chem_sec_num`, `math_sec_single`, `math_sec_multi`, `math_sec_num` |
| 4 | `cuet_ug` | `cuet_domain` | `cuet_domain_paper` | `domain_sec` |
| 5 | `upsc_cse` | `prelims` | `gs_paper_1` | `polity_sec`, `econ_sec`, `hist_sec`, `env_sec`, `sci_sec` |
| 6 | `ssc_cgl` | `tier_1` | `cgl_tier1_paper` | `gi_sec`, `ga_sec`, `qa_sec`, `eng_sec` |
| 7 | `ssc_chsl` | `tier_1` | `chsl_tier1_paper` | `gi_sec`, `ga_sec`, `qa_sec`, `eng_sec` |
| 8 | `ssc_mts` | `session_1_2` | `mts_paper` | `math_sec`, `reaz_sec`, `ga_sec`, `eng_sec` |
| 9 | `ssc_gd` | `cbe` | `gd_cbt` | `gi_sec`, `gk_sec`, `math_sec`, `lang_sec` |
| 10 | `ssc_cpo` | `paper_1` | `cpo_p1` | `gi_sec`, `gk_sec`, `qa_sec`, `eng_sec` |
| 11 | `rrb_ntpc` | `cbt_1` | `ntpc_cbt1` | `ga_sec`, `math_sec`, `gi_sec` |
| 12 | `rrb_group_d` | `cbt` | `group_d_paper` | `sci_sec`, `math_sec`, `gi_sec`, `ga_sec` |
| 13 | `rrb_alp` | `cbt_1` | `alp_cbt1` | `math_sec`, `reason_sec`, `sci_sec`, `ga_sec` |
| 14 | `rrb_technician` | `cbt` | `tech_paper` | `math_sec`, `gi_sec`, `sci_sec`, `ga_sec` |
| 15 | `rrb_je` | `cbt_1` | `je_cbt1` | `math_sec`, `gi_sec`, `ga_sec`, `sci_sec` |
| 16 | `ctet` | `paper_1` | `ctet_p1` | `cdp_sec`, `l1_sec`, `l2_sec`, `math_sec`, `evs_sec` |
| 17 | `ugc_net` | `paper_1_2` | `net_p1` | `ta_sec` |
| 18 | `cat` | `cat_exam` | `cat_paper` | `varc_sec`, `dilr_sec`, `qa_sec` |
| 19 | `ibps_po` | `prelims` | `po_prelims` | `eng_sec`, `qa_sec`, `reaz_sec` |
| 20 | `ibps_clerk` | `prelims` | `clerk_prelims` | `eng_sec`, `num_sec`, `reaz_sec` |
| 21 | `sbi_clerk` | `prelims` | `sbi_clerk_prelims` | `eng_sec`, `num_sec`, `reaz_sec` |
| 22 | `sbi_po` | `prelims` | `sbi_po_prelims` | `eng_sec`, `qa_sec`, `reaz_sec` |
| 23 | `nda` | `written` | `nda_math`, `nda_gat` | `math_sec`, `eng_sec`, `gk_sec` |
| 24 | `cds` | `written` | `cds_english`, `cds_gk`, `cds_math` | `cds_eng_sec`, `cds_gk_sec`, `cds_math_sec` |
| 25 | `gate` | `gate_cbt` | `gate_paper` | `ga_1m`, `ga_2m`, `tech_1m`, `tech_2m` |

**Important:** This is exactly what the app registry currently declares. It does not mean the exam's real-world stages/papers are complete. Example: CTET has two official papers but only one current paper ID; UPSC CSE Prelims requires two papers but the registry models only GS Paper 1; JEE Advanced requires two compulsory papers but the registry models only one; SSC CGL has two tiers but only Tier-I; RRB ALP includes CBT-1, CBT-2 and CBAT but the registry only models CBT-1.

## 2. Official 2026/next-cycle policy verification

| Exam ID | Official cycle/source reviewed | Confirmed facts | Registry discrepancy / status |
|---|---|---|---|
| `neet_ug` | NEET UG 2026 NTA bulletin | 180 compulsory questions, 180 minutes, +4/-1/0, dropped/invalid question +4 to all appeared candidates | Registry says 200 minutes and optional Section B (15 attempt 10 per subject). **FAIL: stale pattern** |
| `jee_main` | JEE Main 2027 NTA bulletin (published Oct 2026) | Paper 1: 20 MCQ + 5 numerical per subject, 75 total, +4/-1/0; Paper 2A and 2B have separate patterns | Registry says 20 MCQ + 10 numerical attempt 5 per subject, 90 question pool and only one paper. **FAIL: stale pattern and missing papers** |
| `jee_advanced` | JEE Advanced 2026 official brochure | Two compulsory papers, 180 minutes each; negative marks may vary by question and exact scheme is displayed in CBT instructions; dropped question gives full marks to all | Registry contains one paper and hardcodes static section marks. **FAIL: incomplete and cannot use generic static policy** |
| `cuet_ug` | CUET UG 2026 NTA bulletin | 50 questions per test paper, all compulsory, 60 minutes, +5/-1/0; special key challenge/dropped-question rules | Registry says 50 questions with any 40 attempts and 45 minutes. **FAIL: stale pattern** |
| `upsc_cse` | Civil Services Examination Rules 2026, Gazette | Prelims two papers of 200 marks each; wrong answer penalty one-third of assigned marks; Paper-II qualifying, 33% | Registry models only GS Paper-I and uses a rounded 0.66 penalty. **FAIL: missing qualifying Paper-II and exact fraction** |
| `ssc_cgl` | SSC CGL 2026 notice | Two tiers; Tier-I 100 questions/200 marks/60 min and -0.50; Tier-II has separate papers/sections, +3 in core Paper-I objective sections and -1 there; Paper-II/III use -0.50 | Registry models only Tier-I. **FAIL: missing Tier-II and section-specific policy** |
| `ctet` | CTET September 2026 bulletin | Two papers; each MCQ +1 and no negative marking | Registry models only one paper with no Paper-I/Paper-II separation. **FAIL: missing paper mapping** |
| `rrb_alp` | RRB CEN 01/2026 | CBT-1 75 questions/75 marks/60 min, +1/-1/3; CBT-2 Part-A and Part-B; CBAT no negative marking; final merit uses CBT-2 Part-A and CBAT 50:50 after qualifying Part-B | Registry models CBT-1 only. **FAIL: missing CBT-2/CBAT/final-merit flow** |
| `nda` | UPSC NDA/NA 2026 notice located | Current official 2026 notice exists; detailed paper-level scheme not fully extracted in this pass | **OPEN: source document/paper-level marking review** |
| `cds` | UPSC CDS-II 2026 official notice | IMA/INA/AFA: English, GK, Maths; OTA: English/GK; wrong answer -1/3 of assigned marks; blank 0; multiple answers wrong; tie marks rounded to 2 decimals | Registry must split OTA vs IMA/INA/AFA papers and SSB stage. **SOURCE VERIFIED, MAPPING FAIL** |
| `ssc_chsl` | SSC CHSL 2025 notice (Tier-II scheduled Feb-Mar 2026) | Tier-I 100/200, 60 min, +2/-0.50; Tier-II Sections I/II +3/-1; Computer Knowledge and Skill/Typing sections qualifying | Registry models only Tier-I. **SOURCE VERIFIED, MAPPING FAIL** |
| `ssc_mts` | Not yet source-verified | No policy values accepted from registry alone | **UNVERIFIED** |
| `ssc_gd` | SSC Constable GD 2026 notice (published Dec 2025) | 80 questions/160 marks/60 min, +2/-0.25; NCC bonus 2/3/5% of maximum marks; PET/PST/Medical/DV after CBE | Registry needs explicit NCC bonus and qualifying stages. **SOURCE VERIFIED, MAPPING REQUIRED** |
| `ssc_cpo` | Not yet source-verified | No policy values accepted from registry alone | **UNVERIFIED** |
| `rrb_ntpc` | RRB CEN 06/2025 Graduate + CEN 07/2025 Undergraduate | CBT-1 100/100, 90 min; CBT-2 120/120, 90 min; -1/3 for wrong; CBAT/Typing for applicable posts | Registry must split Graduate/Undergraduate and post-specific stages. **SOURCE VERIFIED, MAPPING FAIL** |
| `rrb_group_d` | RRB CEN 09/2025 Level-1 | CBT 100/100, 90 min, +1/-1/3; PET/DV/Medical are qualifying stages | Stage and year mapping must be verified against current registry. **SOURCE VERIFIED, MAPPING REQUIRED** |
| `rrb_technician` | RRB CEN 02/2025 | Technician Grade-I Signal and Grade-III have different subject distributions; 100/100, 90 min, +1/-1/3 | Registry must split grade/paper/sections. **SOURCE VERIFIED, MAPPING FAIL** |
| `rrb_je` | RRB CEN 05/2025 JE/DMS/CMA | CBT-1 100/100 in 90 min; CBT-2 150/150 in 120 min; -1/3 per wrong answer | Registry must split CBT-1/CBT-2 and post-specific section marks. **SOURCE VERIFIED, MAPPING FAIL** |
| `ugc_net` | UGC-NET December 2026 NTA bulletin (updated 16 Oct 2026) | Paper I: 50 MCQ/100 marks; Paper II: 100 MCQ/200 marks; all compulsory, 180 minutes; +2 per correct, no negative; special dropped/multiple-correct rules | Registry has one paper ID and one section only. **SOURCE VERIFIED, MAPPING FAIL** |
| `cat` | Not yet source-verified | Must verify current CAT year and MCQ/TITA sections | **UNVERIFIED** |
| `ibps_po` | IBPS CRP PO/MT-XVI 2026 notification | Prelims 100/100 in 60 min; Mains 170 objective/200 in 160 min plus descriptive 2/25 in 30 min; -0.25 of assigned marks; equi-percentile normalization; final Main:Interview 80:20 | Registry has Prelims only. **SOURCE VERIFIED, MAPPING FAIL** |
| `ibps_clerk` | IBPS CRP CSA-XVI 2026 notification | Prelims 100/100 in 60 min; Mains 160/200 in 125 min; -0.25 of assigned marks; equi-percentile normalization to two decimals; Main only for final merit | Registry has Prelims only. **SOURCE VERIFIED, MAPPING FAIL** |
| `sbi_clerk` | Not yet source-verified | Must split Prelims/Mains and current advertisement year | **UNVERIFIED** |
| `sbi_po` | Not yet source-verified | Must split Prelims/Mains/Descriptive/Interview and current advertisement year | **UNVERIFIED** |
| `gate` | GATE 2026 IIT Guwahati Information Brochure | 65 questions/100 marks/180 min; 1- and 2-mark MCQ/MSQ/NAT; MCQ -1/3 or -2/3; MSQ/NAT no negative and no partial credit; optional sections depend on paper code | Registry has one generic paper and no 30 paper-code mapping. **SOURCE VERIFIED, MAPPING FAIL** |

Official source URLs are recorded per exam in the JSON contract. Do not infer an exam's policy from a different exam or a previous year. JEE Advanced is intentionally represented as dynamic marking until the candidate-paper instructions are available.

## 3. Android / Backend scoring call-site audit

### Android
- Canonical scoring entry found: `ExamScoringEngine.evaluateTestAttempt(...)`.
- Secondary configured path: `ExamScoringEngine.calculateScore(...)`.
- UI display paths call `UniversalExamRegistry.getMarkingSchemeForQuestion(...)` in `ExamSimulationScreen.kt`, and `getMarkingSchemeForExam(...)` in `UploadAiTabScreen.kt` / `SyllabusPdfModal.kt`.
- `calculateScore` defaults missing section config to the first section's marks, then to +4/-1 if absent. **FAIL: silent policy fallback.**
- `evaluateTestAttempt` calculates `maxPossible` before enforcing optional attempt caps for multi-scheme sections. **FAIL: inflated max score risk.**
- `getMarkingSchemeForExam` returns the first section's marks; for multi-scheme exams this is not a complete paper-level scheme and can mislead UI.
- Registry alias resolution uses substring matching and generic aliases; `JEE` resolves to JEE Main, and bare `RRB` resolves to NTPC. Ambiguous display aliases are not safe for scoring identity.
- Android and Backend each contain separate per-exam/per-question special-case code. **FAIL: no single source of truth.**

### Backend
- Production callable `submitTestAttemptServerSide` checks `testData.examId || testData.targetExam` through `normalizeExamKey` and rejects `UNKNOWN`; this guard is good.
- The lower-level exported `evaluateTestAttempt` still does `String(targetExam || "NEET")`. **FAIL: direct/internal caller can silently score missing identity as NEET.**
- `callableValidation.test.js` contains a test-path fallback `testDocData.targetExam || "NEET"`; this is not proof of production fallback but should be removed from validation fixtures to prevent false confidence.
- Backend has separate hardcoded marking branches for CAT, JEE Advanced, NDA, CDS, GATE, SSC MTS and broad exam-family defaults. These are not year/stage/paper/section-specific.
- Backend uses fixed numeric tolerance and permissive `parseFloat`-style handling; Android uses its own numeric comparator. **OPEN/FAIL pending strict shared normalization.**
- Backend rounds aggregate marks to 2 decimal places; Android may return raw Double. **FAIL: rounding boundary mismatch.**

## 4. Ranking audit

Source inspected: `backend_functions/index.js`, `backend_functions/examScoring.js`, Android ranking tests/search results.

| ID | Finding | Status |
|---|---|---|
| RANK-001 | Ranking adds raw `finalScore * weight`; it does not normalize by verified paper max. Cross-exam scores therefore have incomparable scales. | **FAIL** |
| RANK-002 | `rankingWeight` accepts any numeric value from test data; it is not restricted to verified Full=1.0 / Part=0.2. | **FAIL** |
| RANK-003 | Test rank/percentile is queried before the transaction, so concurrent submissions can calculate stale rank/population. | **FAIL** |
| RANK-004 | Query failure sets `testRank=0` and percentile 0, then stores these as if calculated. Rank must be unavailable/null on failure. | **FAIL** |
| RANK-005 | Retry response uses `existing.testRank || 1` and `existing.percentile || 0`; stored zero/unavailable values can be converted into fabricated rank 1. | **FAIL** |
| RANK-006 | Idempotency compares JSON string serialization, which is property-order sensitive; canonical answer hashing is required. | **OPEN** |
| RANK-007 | First attempt check is a transaction query, but no current exact-head concurrency/race evidence was found. | **OPEN** |
| RANK-008 | Exam-specific cumulative keys exist, but all exam IDs must align with one canonical identity resolver and board query contract. | **OPEN** |
| RANK-009 | `getUserRankingDetails` uses `higherCount + 1`; tie/percentile semantics need one documented, golden-tested policy. | **OPEN** |
| RANK-010 | Platform rank is separate from official AIR in response metadata; preserve that boundary. | **PASS (source-level only)** |
| RANK-011 | XP separation, privacy filters, migration/backfill and rollback were not fully proven on exact current HEAD. | **OPEN** |

## 5. Canonical versioned scoring contract

Draft contract: `shared/scoring/scoring-policy.v1.json`.

Canonical key: `examId + examYear + stageId + paperId + sectionId + questionType`.

Required contract fields:
- positive marks, negative marks/fraction, unanswered marks;
- partial credit and exact-set MSQ policy;
- numeric answer format, tolerance and precision;
- optional/compulsory question selection metadata;
- dropped/bonus/invalid-question handling;
- paper maximum and qualifying-only section behavior;
- source URL, official cycle/year, source version/date, reviewer and verification status;
- ranking eligibility and normalization policy.

Rules:
- Missing/ambiguous exam identity, missing policy, missing section or unsupported question type => explicit error, never NEET/+4/-1 fallback.
- Unknown/unverified answer key remains UNGRADED, not correct/wrong/unanswered.
- MSQ exact-set by default; partial credit only when official policy explicitly says so.
- Strict finite decimal parser; no `parseFloat("12abc")` acceptance.
- Optional sections require explicit submitted selection metadata; do not choose first N by array order.
- Use decimal-safe arithmetic and one final rounding boundary.
- Server-authoritative score result carries policy version and feeds ranking unchanged.

All registry entries in the draft JSON are disabled for official scoring/ranking until the contract is wired into both platforms and official year/paper mapping is approved. The draft file itself does not alter runtime behavior.

## 6. Shared golden fixtures

Draft fixtures: `shared/scoring/scoring-golden-v1.json`.

Initial source-backed vectors cover NEET 2026, JEE Main 2027, CUET UG 2026, CTET 2026, SSC CGL 2026 Tier-I, RRB ALP 2026 CBT-1 and UGC-NET December 2026. IBPS PO/CSA policies are source-reviewed but have section-specific marks/stages not fully mapped, so no generic IBPS vector is included. Added consumers: `app/src/test/java/Com/ak/testseries/exam/SharedScoringGoldenVectorParityTest.kt` and `backend_functions/sharedScoringGoldenVectors.test.js`. Both load the same JSON and compare correct/wrong/unanswered counts, positive/negative marks, raw/final score and maximum score. These tests are **added but execution is NOT VERIFIED**. The tolerance is 0.005 to accommodate the existing 2-decimal backend rounding versus Android Double values; this is not a substitute for the final shared decimal contract. These vectors test current engine behavior for seven policy samples, not every stage/paper/section.

Required additional vectors: JEE Advanced dynamic schemes and dropped questions; UPSC qualifying Paper-II; all SSC/RRB stages; CAT MCQ/TITA; IBPS PO/CSA Mains and SBI PO/Clerk; GATE MCQ/MSQ/NAT; NDA/CDS; ties, duplicate IDs, missing config, invalid answer keys, NaN/Infinity, malformed decimals, retries and optional sections.

## 7. Acceptance / test evidence

The initial documentation/contract draft did not run tests. A previous six-vector Backend CI run passed 62/62 tests; the expanded nine-vector head is queued for a fresh exact-head run. Android unit tests and Debug APK on the expanded head are not yet verified.

| Gate | Status | Required evidence |
|---|---|---|
| Registry inventory | PASS (source extraction) | 25 exam IDs; 28 paper IDs; 99 section IDs |
| Official policies | PARTIAL / RELEASE BLOCKED | 19 official-cycle sources located; remaining 6 unverified; mapping incomplete even for sourced exams |
| Android/backend call-site audit | FAIL | Fallbacks, independent policy code, optional max and decimal parity defects |
| Versioned contract | DRAFT | JSON created; not runtime wired |
| Shared golden fixtures | DRAFT | Seven shared vectors and Kotlin/Node test consumers added; execution NOT VERIFIED |
| Score-to-ranking handoff | FAIL | Raw-score aggregation, unverified weighting and stale pre-transaction rank |
| Ranking tie/percentile/retry/concurrency | OPEN/FAIL | Exact-head transaction and emulator tests absent |
| Full Android tests | NOT RUN | Need exact discovered/executed/skipped/failed counts |
| Backend tests | NOT RUN | Need exact Node test totals and results |
| Debug APK | NOT RUN | Need exact SHA artifact and build result |

## 8. Next implementation sequence

1. Freeze registry snapshot and source-backed matrix; get Admin/academic review for every official source.
2. Correct year-specific registry hierarchy: NEET 2026, JEE Main 2027, CUET 2026, UPSC Prelims, SSC tiers, CTET Paper I/II, RRB stages.
3. Add strict versioned contract loader to Kotlin and Node; remove duplicate hardcoded branches only after parity coverage.
4. Reject missing exam identity and missing policy/section at all callable and local call sites.
5. Fix optional-section selection and max score using explicit selection metadata.
6. Add shared golden fixtures to both test suites; run Kotlin and Node parity.
7. Return typed score result with policy version, raw/final/max/counts/ungraded/eligibility.
8. Feed only verified normalized score/max and verified Full/Part weight into ranking.
9. Fix rank failure/null semantics, ties/percentile, idempotent canonical hash, transaction/concurrency and exam isolation.
10. Run full Android unit/UI tests, Backend Node tests, Firestore emulator rules, security checks and Debug APK on the exact final SHA.
11. Update branch plan and change log. No merge to main or production deploy without explicit approval.

## 9. Official source register

- NEET UG 2026 NTA Information Bulletin: https://neet.nta.nic.in/documents/ (2026 bulletin linked from official documents page).
- JEE Main 2027 NTA Information Bulletin: https://jeemain.nta.nic.in/information-bulletin/
- JEE Advanced 2026 Information Brochure: https://jeeadv.ac.in/documents/IBEnglish_2026.pdf
- CUET UG 2026 NTA Information Bulletin: https://cdnbbsr.s3waas.gov.in/s3d1a21da7bca4abff8b0b61b87597de73/uploads/2026/01/202601031633478370.pdf
- UPSC Civil Services Examination 2026 official notice: https://d35xcwcl37xo08.cloudfront.net/upsc-exam-wp-uploads/2026/02/upsc-notification-2026-PDF.pdf
- UPSC Civil Services Examination Rules 2026, Gazette: https://egazette.gov.in/WriteReadData/2026/269828.pdf
- SSC CGL 2026 notice: https://ssc.gov.in/api/attachment/uploads/masterData/NoticeBoards/Notice_of_adv_cgl_2026.pdf
- CTET September 2026 Information Bulletin: https://cdnbbsr.s3waas.gov.in/s3443dec3062d0286986e21dc0631734c9/uploads/2026/05/202605111250310617.pdf
- RRB ALP CEN 01/2026: https://www.rrbcdg.gov.in/uploads/2026/01-ALP/012026ALP-CEN.pdf
- UGC-NET December 2026 Updated Information Bulletin (16 Oct 2026): https://cdnbbsr.s3waas.gov.in/s301eee509ee2f68dc6014898c309e86bf/uploads/2026/10/20261016860019603.pdf
- IBPS CRP PO/MT-XVI 2026 notification: https://www.ibps.in/wp-content/uploads/Detailed-Notification_CRP-PO-XVI_Final_V1_30.06.2026.pdf
- IBPS CRP CSA-XVI 2026 notification: https://www.ibps.in/wp-content/uploads/Notification_CRP_CSA_XVI-Final.pdf
- RRB NTPC Undergraduate CEN 07/2025: https://www.rrbcdg.gov.in/uploads/2025/07-NTPCUG/072025NTPCUG-CEN.pdf
- RRB NTPC Graduate CEN 06/2025 official notice page: https://www.rrbcdg.gov.in/2025-06-ntpcg.php
- RRB Group-D CEN 09/2025 official notice page: https://www.rrbcdg.gov.in/2025-09-level1.php
- RRB Technician CEN 02/2025: https://www.rrbcdg.gov.in/uploads/2025/02-TECH/022025-CEN.pdf
- RRB JE CEN 05/2025: https://www.rrbcdg.gov.in/uploads/2025/05-JE/052025JE-CEN.pdf
- SSC Constable GD 2026 notice (published 01 Dec 2025): https://ssc.gov.in/api/attachment/uploads/masterData/NoticeBoards/notice_01122025.pdf
- SSC CHSL 2025 notice (Tier-II scheduled Feb-Mar 2026): https://ssc.gov.in/api/attachment/uploads/masterData/NoticeBoards/Notice_of_adv_chsl_2025.pdf
- GATE 2026 IIT Guwahati Information Brochure: https://gate2026.iitg.ac.in/doc/IB/GATE2026-IB-10102025.pdf
- UPSC CDS-II 2026 official notice: https://www.upsc.gov.in/sites/default/files/Notif-CDS-II-2026-Engl-200526.pdf
- UPSC NDA/NA II 2026 notice: https://www.upsc.gov.in/examinations/National%20Defence%20Academy%20and%20Naval%20Academy%20Examination%20%28II%29%2C%202026
- UPSC CDS II 2026 archive: https://www.upsc.gov.in/exams-related-info/exam-notification/archives

**Final audit status:** NOT READY. Source inventory and draft artifacts are committed to the isolated audit branch only. Runtime scoring, ranking, Kotlin/Node parity and full tests remain blocked/open.


## 10. Isolated audit CI workflow

Added `.github/workflows/all-exam-scoring-ranking-audit.yml` on this audit branch. It is configured for pushes to this exact audit branch, relevant pull requests targeting main, and manual dispatch. It runs:
- JSON inventory validation for 25 exam policies and 7 initial fixtures, and asserts all policies remain disabled until review;
- Node 18 syntax checks plus `examScoring.test.js`, `callableValidation.test.js` and `sharedScoringGoldenVectors.test.js`;
- JDK 21 / Gradle 9.3.1 `testDebugUnitTest`;
- Gradle `assembleDebug`;
- upload of Android test XML/reports and Debug APK artifact.

It does **not** yet run Firestore emulator rules tests, ranking transaction/concurrency tests, or automated policy-source validation. The Kotlin and Node golden-vector tests are now included in their respective test tasks. Their current nine-vector exact-head run remains queued/in progress until results are reported. The latest workflow update uses JDK 21 and is committed as `f1e967e341e782e1f8865e44f4f932caf875c9fd`.

**CI evidence:** the available GitHub commit-status and workflow-run lookups returned no status records for the audit commits. Therefore the workflow is configured but its run is **NOT VERIFIED / OPEN**. No test count, test PASS or APK artifact is claimed.

## 11. Shared fixture test wiring update

Added Kotlin test `SharedScoringGoldenVectorParityTest.kt`, Node test `sharedScoringGoldenVectors.test.js`, and Gradle test property `sharedScoringFixtures` so both platforms read the same `shared/scoring/scoring-golden-v1.json`. The audit CI workflow includes the new Node test and the Android unit-test task automatically discovers the Kotlin test. This is test wiring only: no scoring engine or ranking production call site was changed. No current-head test result is available, so parity remains NOT VERIFIED.

## 12. Expanded official source verification

Official source review has expanded from seventeen to nineteen exam cycles by adding GATE 2026 and CDS-II 2026. The current registry for these exams still omits one or more official papers/stages/grades/bonus components, so the policy is source-verified but mapping remains blocked. The shared fixture set now has seven vectors, and the Kotlin test plus workflow inventory assertion were updated from six to seven. Backend CI previously passed the six-vector version (62/62 tests); the added UGC NET vector has not yet been run in CI. Do not extend that earlier PASS to the new head.


## 13. Cross-branch candidate review: `feature/ranking-universal-integration-v1`

The integration branch was inspected as a candidate because it contains newer fail-closed scoring and ranking work than main. Current Git comparison reports **469 commits ahead and 98 behind main** (merge base `d2feae47f908b705209c27b8e61e624f94c400d7`). This divergence is too large for a whole-branch merge.

### Improvements present on the integration branch
- Registry stores `sourceYear`, `sourceTitle` and `sourceUrl`; only NEET UG 2026 and JEE Main Paper 1 (2027) are source-verified/active. The other 23 registered identities are inactive instead of falsely scoring from generic defaults.
- Backend `evaluateTestAttempt` rejects missing/blank exam identity, validates unique question IDs, rejects malformed numeric prefixes, supports exact-set MSQ and preserves unknown/untrusted keys as ungraded.
- Android `calculateScore` requires exact stage and paper, rejects ambiguous IDs and section-specific mixed marking in its single-scheme path; it does not use a generic +4/-1 fallback.
- Ranking v2 normalizes scores to a 10,000-point scale before weight; accepts only verified 1.0/0.2 weights; canonicalizes answer payloads for idempotency; uses a first-qualifying-attempt lock/transaction; and the per-test snapshot returns null rank/percentile on query failure.
- The branch includes pure ranking helper tests for normalization, weight validation, ties, percentile boundaries, ungraded exclusion, null snapshots and migration checkpoint safety.

### Remaining defects / limitations on that branch
- Android and Backend `evaluateTestAttempt` still add each valid question's positive marks to max score without a generic optional-section selection/cap model. This is safe only for currently active compulsory-paper patterns; do not activate optional-section exams until fixed.
- `getUserRankingDetails` still uses rank 0 / percentile 0 as the "Rank Pending" sentinel. This conflicts with the required null/unavailable contract.
- Per-test rank and exam rank/percentile are derived from separate count queries after commit / outside a single consistent read snapshot. Concurrent writes can make rank and denominator inconsistent; emulator concurrency tests are required.
- `examScoring.js` and `rankingEngine.js` both contain percentile helpers. Consolidate one canonical definition and test the exact tie/denominator convention.
- Registry source verification on this branch is only 2/25, despite 17 official source sets now identified in this audit. Add source packets and stage/paper/section mapping before enabling more exams.
- Current branch code has no shared versioned policy JSON consumed by both runtimes; it still mirrors policy logic in Kotlin and Node.
- Historical CI documented on this branch: run `42355909308` had 306 Android tests with 19 failures, backend 70/71 failed, while Firestore rules and Debug APK assembly passed. Later code/test corrections were made, but README states latest runs were queued and no exact-current-head green run is established. Treat all those results as historical, not current PASS.

### Port decision
**KEEP selected improvements, DO NOT merge the branch wholesale.** Port only after exact-head CI is green and each file is reviewed against current main:
1. Source-year metadata and verified/inactive registry gates.
2. Strict backend identity/numeric/MSQ/answer-key validation.
3. Android exact stage/paper validation.
4. RankingEngine normalized scoring, fixed weights, canonical answer hash and transactional first qualifying attempt.
5. Privacy-safe callable-only leaderboard reads and Firestore rules, but only with coordinated Android/backend/rules compatibility tests.
6. Do not port stale `NEETViewModel`, `MainActivity`, database, PDF, AI, or backend index wholesale; main contains newer planner/Room changes.

**Status:** This branch contains the strongest candidate implementation for several ranking/scoring safety requirements, but it is NOT release-ready and is not approved for merge. No feature-branch code was changed by this audit section.

## 14. Exact CI results and Java runtime correction

- Backend run `43325351355` on the earlier six-vector commit: **62 tests passed, 0 failed, 0 skipped**.
- Backend run `43325704588` on the intermediate nine-vector commit: **65 tests passed, 0 failed, 0 skipped**. This is historical only: two IBPS vectors were subsequently removed because the current stage/section mark mapping is not sufficiently explicit in the source-backed contract.
- Android run `43325351355`: **294 tests completed, 1 failed**. The failure is `CrashResilienceTest > classMethod`; Robolectric reports Android SDK 36 requires Java 21, but the workflow used Java 17. The Debug APK step was skipped because the unit-test task failed. Test XML/report artifact ID: `13992911800`.
- The audit workflow was corrected to use JDK 21 (source/target compilation remains project-configured); the correction is commit `f1e967e341e782e1f8865e44f4f932caf875c9fd`.
- Current policy-correct fixture set contains **7 vectors** (the six original plus UGC-NET December 2026; no generic IBPS vector). The seven-vector exact-head run, full Android test suite and Debug APK have not yet returned results. Do not transfer the 62/62 or 65/65 historical Backend result to this latest head.
- Firestore emulator tests, ranking transaction/concurrency tests and ranking end-to-end tests remain outside the audit workflow and are still OPEN.

**Current verdict:** Backend sample tests have historical PASS evidence; Android test gate FAIL due wrong Java runtime; JDK 21 correction is committed but not yet verified by a fresh completed run. Full release remains blocked.

### Current official-source coverage update

The policy JSON now has 17 source-backed exam cycles and 8 unverified registry identities. Source-backed does not mean enabled: all 25 remain `scoringEnabled=false` and `rankingEnabled=false` until stage/paper/section mapping and runtime contract integration pass. SSC GD's NCC bonus and CHSL's Tier-II qualifying sections, plus the RRB stage/grade splits, are explicitly retained as separate policy fields rather than flattened into one +/− value.

### GATE and CDS source mapping update

GATE 2026 and CDS-II 2026 are now source-backed in the contract. GATE must be split by paper code and optional section structure; CDS must split OTA (two written papers) from IMA/INA/AFA (three written papers), and keep SSB interview separate. These source additions bring the verified-source count to 19/25; six registry IDs remain unverified. No GATE/CDS policy is enabled in runtime.


## 15. Draft typed score-to-ranking contract

The JSON contract now contains a `rankingContract` block (version 1.0.0), and its schema/CI inventory check validates the core invariants. This remains **DRAFT NOT RUNTIME WIRED**.

- Ranking input is only a server-authoritative verified score result carrying exam/year/stage/paper, policy version, raw/final/max score, correct/wrong/unanswered/ungraded counts, attempt ID and verified test type.
- Normalized score is `round(finalScore / maxScore * 10000)`, clamped to 0..10000.
- Full Test weight is exactly 1.0; Part/Chapter/Topic exactly 0.2; unknown or client-supplied arbitrary weights are rejected.
- Competitive rank is `higherScoreCount + 1`; equal scores share rank and subsequent rank positions skip ties.
- Percentile is `((lowerScoreCount + equalScoreCount) / totalEligibleCount) * 100`, rounded to two decimals. A single authoritative implementation and consistent read snapshot are still required.
- Rank/percentile unavailable or query failure is `null`, never zero or one.
- First valid qualifying attempt per test; canonical sorted-answer SHA-256 idempotency; retries do not double-count; conflicting attempt ID reuse rejects.
- Firestore transaction required for the attempt lock and cumulative score update; rank snapshot must be consistent or explicitly provisional.
- Private profiles and banned/suspended users are excluded server-side; client direct writes are denied. Label is AK Test Series Rank, never government/NTA AIR.

No ranking runtime code was changed and no ranking transaction, tie, percentile, retry or concurrency test was run on this audit branch. The contract describes the target behavior only.


## 16. Current review/CI state

- Draft PR: https://github.com/abhi952817-sudo/AK-TEST-SERIES-/pull/5
- Exact PR/audit head: `a0c4d3a6c1df84aeffe901c270c34928139c5b9f`
- Compare to current main: audit branch is 32 commits ahead and 7 behind; GitHub reports `mergeable=false`.
- PR file diff is limited to eight files: audit workflow, Gradle test property, one Kotlin test, one Node test, audit Markdown, shared policy JSON, shared golden fixture JSON and JSON schema. No production scoring/ranking source file is changed.
- Latest exact-head runs queued: All-Exam Audit CI `43326951571`; Android CI `43326951437`. No PASS/FAIL conclusion is available yet.
- PR is draft/open for review and CI only. It has not been merged. Do not merge until branch reconciliation, runtime contract integration and all tests are green.

**Current status:** 19/25 official source cycles found; 6 still unverified; all 25 policies disabled in the draft contract; 7 shared fixtures and Kotlin/Node consumers present; latest CI queued; Android prior attempt failed under Java 17 and the isolated workflow now uses Java 21; ranking runtime and integration tests remain open.


## 17. Failed CI root-cause register and cleanup status (2026-11-13)

This section records the actual failed jobs separately from the fixes applied. A workflow file being edited is not proof that every workflow using that file has been fixed.

| Run ID | Job | Observed result | Root cause / evidence | Remediation state |
|---|---|---|---|---|
| `43325351355` | Android unit tests and Debug APK | **FAIL**; 294 tests completed, 1 failed; APK step skipped | `CrashResilienceTest > classMethod`; Robolectric Android SDK 36 requires Java 21, while this job used JDK 17. Test report artifact: `13992911800`. | JDK 21 was applied to the isolated all-exam audit workflow only. Fresh exact-head verification is pending. |
| `43325704588` | Android unit tests and Debug APK | **FAIL**; same JDK 17 setup and same Android test-task failure class | Job steps show JDK 17; test task failed before APK build. The prior report attributes the failure to SDK 36/Robolectric requiring Java 21. | Same as above. Do not count as fixed until JDK 21 run completes. |
| `43325351355` | Backend scoring and callable unit tests | **PASS**; historical 62/62 | Syntax, draft contract validation and backend tests passed on the six-vector revision. | Historical PASS only; not a current-head verification. |
| `43325704588` | Backend scoring and callable unit tests | **PASS**; historical 65/65 | Passed on an intermediate nine-vector revision. Two IBPS vectors were later removed because their section-level marking was not fully mapped. | Historical PASS only; do not transfer to the current seven-vector head. |
| `43326951571` | Android unit tests and Debug APK | **IN PROGRESS** at last inspection; JDK 21 setup succeeded | The corrected isolated audit job reached the Android test step. Test and APK results were not yet available. | **FIX APPLIED / VERIFICATION PENDING**. |
| `43326951571` | Backend scoring and callable unit tests | **IN PROGRESS** at last inspection | Node setup, dependency install and JS syntax check succeeded; contract validation/test results not yet available. | **PENDING**. |
| `43326951437` | Existing Android CI build | **IN PROGRESS** at last inspection; still uses JDK 17 | Its workflow has a separate JDK 17 setup. The isolated audit workflow's JDK 21 change does not modify this separate workflow. | **NOT FIXED** in this workflow. It must be assessed separately; do not claim all Android CI paths were corrected. |

### Explicit remediation decisions

1. JDK 21 correction is present in `.github/workflows/all-exam-scoring-ranking-audit.yml`. It only addresses the isolated audit workflow.
2. The separate Android CI workflow still declares JDK 17 at run `43326951437`. This is a confirmed remaining configuration gap. No change to that workflow is claimed in this audit entry.
3. Backend historical PASS results are retained for traceability, not treated as proof that the latest head passes.
4. The audit workflow's current test run must finish before its test count, result or Debug APK can be recorded.
5. No workflow definitions have been deleted. Deleting CI definitions would disable future validation and is not equivalent to deleting historical run logs/artifacts.

### GitHub Actions storage cleanup

Requested cleanup scope: remove failed workflow-run logs and obsolete artifacts only after preserving the failure evidence above. Keep source branches, commits, PR review history, workflow definitions and the Markdown audit record.

**Cleanup execution status: BLOCKED / NOT EXECUTED.** The connected GitHub actions available in this session expose workflow-run job/artifact listing and artifact download, but do not expose the destructive GitHub API operations to delete a workflow run/logs or delete an artifact. The available read-only GitHub fetch operation cannot perform those deletions. No deletion is claimed. Use GitHub repository Actions UI (or an authorized GitHub Actions API token) to delete selected failed runs and their associated artifacts. Do not delete successful runs or the currently running verification run before its results and evidence are captured.

**Important:** GitHub Actions logs and artifacts consume Actions storage; deleting local workflow YAML or branch Markdown does not clear historical run logs. Retention settings can prevent future accumulation but do not retroactively delete existing runs.
