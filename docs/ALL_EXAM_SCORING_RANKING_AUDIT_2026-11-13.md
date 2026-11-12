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
| `cds` | UPSC CDS 2026 notice located | Current official 2026 notice exists; detailed paper-level scheme not fully extracted in this pass | **OPEN: source document/paper-level marking review** |
| `ssc_chsl` | Not yet source-verified | No policy values accepted from registry alone | **UNVERIFIED** |
| `ssc_mts` | Not yet source-verified | No policy values accepted from registry alone | **UNVERIFIED** |
| `ssc_gd` | Not yet source-verified | No policy values accepted from registry alone | **UNVERIFIED** |
| `ssc_cpo` | Not yet source-verified | No policy values accepted from registry alone | **UNVERIFIED** |
| `rrb_ntpc` | Not yet source-verified | Must split CBT stages and recruitment CEN/year | **UNVERIFIED** |
| `rrb_group_d` | Not yet source-verified | Must use current CEN/year and CBT pattern | **UNVERIFIED** |
| `rrb_technician` | Not yet source-verified | Must split grade/stage and current CEN/year | **UNVERIFIED** |
| `rrb_je` | Not yet source-verified | Must split CBT-1/CBT-2 and current CEN/year | **UNVERIFIED** |
| `ugc_net` | Not yet source-verified | Must verify Paper-I/Paper-II and current cycle | **UNVERIFIED** |
| `cat` | Not yet source-verified | Must verify current CAT year and MCQ/TITA sections | **UNVERIFIED** |
| `ibps_po` | Not yet source-verified | Must split Prelims/Mains and current CRP year | **UNVERIFIED** |
| `ibps_clerk` | Not yet source-verified | Must split Prelims/Mains and current CRP year | **UNVERIFIED** |
| `sbi_clerk` | Not yet source-verified | Must split Prelims/Mains and current advertisement year | **UNVERIFIED** |
| `sbi_po` | Not yet source-verified | Must split Prelims/Mains/Descriptive/Interview and current advertisement year | **UNVERIFIED** |
| `gate` | Not yet source-verified | Must verify paper code, 1/2-mark, MCQ/MSQ/NAT and negative/partial marking per year | **UNVERIFIED** |

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

Initial source-backed vectors cover NEET 2026, JEE Main 2027, CUET UG 2026, CTET 2026, SSC CGL 2026 Tier-I and RRB ALP 2026 CBT-1. They are **not yet consumed by Kotlin or Node tests**. The next code phase must add one shared fixture loader in both test suites and compare all result fields, including raw/final/max score and ungraded count.

Required additional vectors: JEE Advanced dynamic schemes and dropped questions; UPSC qualifying Paper-II; all SSC/RRB stages; UGC NET; CAT MCQ/TITA; IBPS/SBI; GATE MCQ/MSQ/NAT; NDA/CDS; ties, duplicate IDs, missing config, invalid answer keys, NaN/Infinity, malformed decimals, retries and optional sections.

## 7. Acceptance / test evidence

No Kotlin, Node, Firestore emulator, concurrency or APK tests were run in this documentation/contract-draft pass. The exact current CI status must be fetched for the resulting commit.

| Gate | Status | Required evidence |
|---|---|---|
| Registry inventory | PASS (source extraction) | 25 exam IDs; 27 paper IDs; 99 section IDs |
| Official policies | PARTIAL / RELEASE BLOCKED | 8 official-cycle sources located; remaining 17 unverified; mapping incomplete even for sourced exams |
| Android/backend call-site audit | FAIL | Fallbacks, independent policy code, optional max and decimal parity defects |
| Versioned contract | DRAFT | JSON created; not runtime wired |
| Shared golden fixtures | DRAFT | JSON created; not loaded by Kotlin/Node |
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
- UPSC Civil Services Examination Rules 2026, Gazette: https://egazette.gov.in/WriteReadData/2026/269828.pdf
- SSC CGL 2026 notice: https://ssc.gov.in/api/attachment/uploads/masterData/NoticeBoards/Notice_of_adv_cgl_2026.pdf
- CTET September 2026 Information Bulletin: https://cdnbbsr.s3waas.gov.in/s3443dec3062d0286986e21dc0631734c9/uploads/2026/05/202605111250310617.pdf
- RRB ALP CEN 01/2026: https://www.rrbcdg.gov.in/uploads/2026/01-ALP/012026ALP-CEN.pdf
- UPSC NDA/NA II 2026 notice: https://www.upsc.gov.in/examinations/National%20Defence%20Academy%20and%20Naval%20Academy%20Examination%20%28II%29%2C%202026
- UPSC CDS II 2026 archive: https://www.upsc.gov.in/exams-related-info/exam-notification/archives

**Final audit status:** NOT READY. Source inventory and draft artifacts are committed to the isolated audit branch only. Runtime scoring, ranking, Kotlin/Node parity and full tests remain blocked/open.
