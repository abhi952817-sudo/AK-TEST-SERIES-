# AK TEST SERIES — Pending Exam Verification & Development Blueprint
**Document type:** Source-verification and implementation plan  
**Target branch:** `feature/ranking-universal-integration-v1`  
**Prepared:** 2026-11-07  
**Scope:** Exam registry, exam-specific scoring, pattern, timer, question model and ranking eligibility  
**Safety boundary:** Planning document only. This commit does not change production code, Firestore data, rules, indexes, Cloud Functions, or `main`.

## 1. Executive finding

The current integration-branch audit records **25 registered exam identities**. Only these two configurations are currently treated as source-verified and active for authoritative scoring/ranking:

1. `neet_ug` — NEET UG 2026
2. `jee_main` — JEE Main Paper 1 (B.E./B.Tech), 2027

The remaining **23 exam identities are registered/scaffolded but not verified for authoritative scoring**. A name, syllabus heading, mock-test template, or hard-coded marking scheme does not make an exam implementation complete. Keep these exams inactive for ranked scoring until the official source packet and automated checks are complete.

This is deliberately stricter than making all 25 appear usable with guessed values. Incorrect negative marking or question counts would corrupt scores and AIR/ranks.

## 2. Pending exam list

| # | Canonical exam identity | Current status | Main source authority to verify |
|---:|---|---|---|
| 1 | JEE Advanced — `jee_advanced` | Pending | JEE Advanced official portal and current Information Brochure |
| 2 | CUET UG — `cuet_ug` | Pending | NTA CUET UG current Information Bulletin and subject-wise syllabus |
| 3 | UPSC CSE Prelims — `upsc_cse` | Pending | UPSC current Civil Services Examination notification and exam rules |
| 4 | SSC CGL — `ssc_cgl` | Pending | SSC current CGL notice, scheme and syllabus |
| 5 | SSC CHSL — `ssc_chsl` | Pending | SSC current CHSL notice, scheme and syllabus |
| 6 | SSC MTS — `ssc_mts` | Pending | SSC current MTS notice, scheme and syllabus |
| 7 | SSC GD — `ssc_gd` | Pending | SSC current Constable GD notice, scheme and syllabus |
| 8 | SSC CPO — `ssc_cpo` | Pending | SSC current SI CPO notice, Paper-I/Paper-II and PET/PST stages |
| 9 | RRB NTPC — `rrb_ntpc` | Pending | Current RRB CEN for Graduate and Undergraduate posts; confirm whether separate paper/stage profiles are required |
| 10 | RRB Group D / Level-1 — `rrb_group_d` | Pending | Current RRB Level-1 CEN and CBT/other applicable stages |
| 11 | RRB ALP — `rrb_alp` | Pending | Current RRB ALP CEN, CBT-1, CBT-2 parts, CBAT and applicable qualifying rules |
| 12 | RRB Technician — `rrb_technician` | Pending | Current RRB Technician CEN and post/grade-specific CBT pattern |
| 13 | RRB JE — `rrb_je` | Pending | Current RRB JE CEN, CBT-1, CBT-2 and post/discipline-specific syllabus |
| 14 | IBPS PO — `ibps_po` | Pending | Current IBPS CRP PO/MT notification, prelims, mains and interview rules |
| 15 | IBPS Clerk/CSA — `ibps_clerk` | Pending | Current IBPS CRP CSA notification, prelims/mains and language-proficiency rules |
| 16 | SBI PO — `sbi_po` | Pending | Current SBI PO advertisement, prelims, mains, descriptive test and later stages |
| 17 | SBI Clerk/Junior Associate — `sbi_clerk` | Pending | Current SBI Junior Associate advertisement, prelims/mains and applicable language test |
| 18 | CAT — `cat` | Pending | Current CAT official notification, test structure, slot duration, section timing and TITA rules |
| 19 | UGC NET — `ugc_net` | Pending | Current NTA UGC-NET Information Bulletin, Paper 1/Paper 2 and subject rules |
| 20 | CTET — `ctet` | Pending | Current CTET Information Bulletin, Paper I/Paper II and qualifying rules |
| 21 | NDA & NA — `nda` | Pending | Current UPSC NDA/NA notification and Mathematics/GAT paper rules |
| 22 | CDS — `cds` | Pending | Current UPSC CDS notification; IMA/INA/AFA vs OTA paper differences |
| 23 | GATE — `gate` | Pending | Current GATE organizing institute's official Information Brochure; paper-code and discipline rules |

**Identity warning:** Do not collapse distinct papers/stages into one generic exam profile. For example, RRB NTPC Graduate vs Undergraduate, IBPS PO vs Clerk, CTET Paper I vs Paper II, CDS OTA vs IMA/INA/AFA, and GATE paper codes need explicit stage/paper/discipline identities wherever their official rules differ. Keep the existing canonical exam IDs stable; introduce a versioned stage/paper key rather than inventing aliases.

## 3. Official-source-first research process

For each pending exam, research the latest applicable cycle from the exam authority. Use the latest official notification/bulletin as the primary authority. A coaching website, blog, YouTube video, old PDF, search snippet, or mock-test vendor is not authoritative evidence.

Official portals to start from:

- JEE Advanced: https://jeeadv.ac.in/
- CUET UG (NTA): https://cuet.nta.nic.in/
- UPSC: https://upsc.gov.in/
- SSC: https://ssc.gov.in/
- Railway Recruitment Boards: https://rrbapply.gov.in/ and the relevant official regional RRB/CEN notice
- IBPS: https://www.ibps.in/
- SBI Careers: https://sbi.co.in/web/careers
- CAT: https://iimcat.ac.in/
- UGC NET (NTA): https://ugcnet.nta.nic.in/
- CTET: https://ctet.nic.in/
- GATE: use the official organizing IIT/IISc GATE portal for the target cycle; record the actual cycle-specific URL in the source packet.

### Required source packet per exam

Create one evidence record for every exam and every distinct paper/stage where rules differ:

1. Exam authority and official portal.
2. Exact notification/bulletin title, cycle/year, publication/update date.
3. Direct official PDF/HTML URL and retrieval date.
4. PDF page numbers or HTML section headings supporting each value.
5. Exam identity, stage ID, paper ID, post/course/discipline scope.
6. Mode, language, total duration, sectional timing and any sectional lock.
7. Total questions, compulsory questions, optional questions, and section-wise counts.
8. Question types: single-correct MCQ, multiple-correct, numerical/NAT/TITA, passage, matching, descriptive, etc.
9. Options count and answer input constraints per question type.
10. Correct, incorrect, unanswered and partially correct marks; negative marking formula; fractional penalties; rounding/tolerance rules.
11. Bonus, dropped-question, answer-key revision and normalization rules.
12. Syllabus and subject/section mapping.
13. Whether the test is suitable for exact-score simulation or only practice simulation.
14. A source-confidence status: VERIFIED, PARTIALLY_VERIFIED, or UNVERIFIED, with reviewer notes.

Do not derive a rule from a secondary site when the official notice is silent. Mark the field UNKNOWN and keep authoritative ranking disabled until it is resolved.

## 4. Development architecture

Implement each exam as configuration data consumed by the existing universal engines. Do not create a separate scoring engine or duplicate rank calculation for every exam.

### Layer A — Canonical exam registry
- Preserve stable canonical IDs and existing data compatibility.
- Add explicit `configVersion`, `sourceYear`, `sourceTitle`, `sourceUrl`, `verifiedAt`, and source-page/section references.
- Add explicit `verified` and `rankingEnabled` gates. Defaults must be false.
- Resolve only exact `examId + stageId + paperId`; reject ambiguous display-name aliases.
- Support cycle-specific pattern versions. A new year's notification must not silently rewrite historical attempts.

### Layer B — Stage and paper model
- Represent prelims/mains, Paper I/II, sections, post groups and subject/discipline variants separately.
- Store duration at the correct level: whole paper, section, or sectional timer.
- Model locked sections and navigation rules explicitly.
- Do not infer a paper from a broad exam label.

### Layer C — Question and answer model
- Explicitly support MCQ single, MCQ multiple, numerical/NAT/TITA, matching, statement-based, passage-based and descriptive response only where the official pattern requires them.
- Define option count per section/type; numerical questions must not inherit MCQ options.
- Define partial-credit policy for multiple-correct questions.
- Define numeric exactness, accepted range/tolerance, integer-only constraints and unit handling.
- Unknown question types or untrusted answer keys must become UNGRADED, not silently wrong or skipped.

### Layer D — Scoring engine
- One shared deterministic scoring engine on Android and backend.
- Config-driven positive marks, negative marks, partial marks, unanswered marks and rounding.
- Keep raw score, maximum score, normalized score (if officially applicable), and display score distinct.
- Apply official normalization only when the formula and inputs are documented and reproducible. Do not approximate normalization.
- Reject duplicate/missing question IDs, invalid options, malformed answer keys and contradictory test metadata.
- No fallback to generic `+1/-0.25` or any other default when configuration is absent.

### Layer E — Ranking eligibility
- Ranking is enabled only for VERIFIED configurations and validated public/official test records.
- A test must match the exact paper structure to receive Full Test weight. Partial tests retain the existing 0.2x rule only if the universal ranking policy explicitly classifies them as part tests.
- Never award 1.0x based only on a title such as "Full Mock".
- Preserve first qualifying attempt, duplicate protection, banned-user filtering, privacy-safe callable access and canonical score snapshots.
- Keep exam-wise AIR, class/cohort rank, per-test rank, Top 100, My Rank, percentile and XP/league calculations on the existing universal ranking engine. Exam-specific code must not duplicate those calculations.

### Layer F — Question ingestion and test builder
- Admin upload must select exact exam, cycle, stage, paper, subject, section and question type.
- Validate uploaded PDF/OCR questions against the selected verified configuration.
- Do not create sample/dummy questions as production content.
- Tests with incomplete/unknown source metadata may be saved as PRIVATE PRACTICE ONLY, but must not enter public leaderboard or AIR.
- Preserve original source, answer key provenance and review status for each imported question.

## 5. Per-exam implementation sequence

Use this sequence for each pending exam. Do not implement all 23 by copying one generic template.

**Step 1 — Research:** collect and cross-check official notification, information bulletin, syllabus and any corrigendum.  
**Step 2 — Evidence file:** add the source packet with exact page/section references.  
**Step 3 — Pattern model:** define canonical stage/paper/section/question-type configuration.  
**Step 4 — Scoring specification:** write a human-readable scoring truth table including correct, wrong, skipped, partially correct, numeric tolerance, bonus and dropped questions.  
**Step 5 — Android/backend parity:** implement the config once in a shared contract or mirrored typed models with parity tests.  
**Step 6 — Validation:** reject invalid question counts, marks totals, option counts, duration conflicts, stage mismatch and unverified metadata.  
**Step 7 — Ranking gate:** enable ranking only after official source review and passing contract tests.  
**Step 8 — Regression tests:** test exact sample cases from the official pattern, boundary values, duplicate submissions, offline/online parity and historical version compatibility.  
**Step 9 — Review:** independent audit of source accuracy, code overlap, data migration risk and privacy.  
**Step 10 — Release readiness:** CI green on the current branch head, emulator/rules/index review and a separately approved deployment plan.

## 6. Required automated tests for every exam

- Canonical identity and alias collision tests.
- Exact stage/paper resolution tests.
- Section question-count and total-mark arithmetic tests.
- Timer and sectional-lock tests.
- Correct/wrong/skipped scoring tests.
- Negative-marking fractional precision and rounding tests.
- Multiple-correct partial-credit tests where applicable.
- Numeric exact/tolerance/integer tests where applicable.
- Bonus/dropped question and revised answer-key tests.
- Android vs backend score parity tests using the same fixtures.
- Full-test vs part-test classification and 1.0x/0.2x ranking-weight tests.
- Unverified config must fail closed.
- Old config versions must continue to reproduce historical scores.
- Public/private profile and banned-user leaderboard tests.
- Concurrent submission, duplicate attempt and migration idempotency tests.

No exam is complete merely because it compiles. Its source packet, implementation, parity tests, ranking gate and CI must all pass.

## 7. Priority order

Recommended delivery order, subject to official source availability:

1. **JEE Advanced** — closest to existing JEE engine; requires careful multi-correct/partial marking and two-paper model.
2. **CUET UG** — important subject-wise papers and variable subject selection; avoid one fixed paper template.
3. **UPSC CSE Prelims** — GS Paper-I and CSAT Paper-II need separate scoring/qualifying treatment.
4. **SSC CGL and CHSL** — tier/stage and section-specific rules; implement separately after the latest notices are verified.
5. **Banking** — IBPS PO, IBPS CSA, SBI PO and SBI Clerk separately; sectional timing and descriptive/qualifying stages matter.
6. **Railways** — NTPC, Group D, ALP, Technician and JE separately; post/grade and CBT stage differences matter.
7. **UGC NET and CTET** — paper/subject and qualification rules.
8. **NDA and CDS** — subject-paper and academy/course-specific rules.
9. **CAT** — section-specific timers, MCQ vs TITA scoring and slot-cycle pattern.
10. **GATE** — paper-code-specific syllabus, question types, multi-select/numerical marking and partial credit.

Priority is a delivery sequence, not permission to assume a pattern. If a source is not yet published for a new cycle, use the last officially published cycle only with an explicit cycle label and do not claim it represents the next cycle.

## 8. Branch and overlap controls

- Keep all work on a dedicated feature branch derived from the current approved integration branch head.
- Do not edit `main`, merge, deploy or write production data during this phase.
- Before editing any file, search all branches and the integration branch for existing registry, scoring, exam config, answer-key validation and ranking implementations.
- Reuse the current universal engine. Do not create duplicate exam registries or competing scoring helpers.
- Keep one authoritative config source per exam/version. Android/backend copies must be generated from or tested against the same contract.
- Add a file ownership/change map before code edits. If a file is already owned by an active ranking/scoring change, coordinate rather than overwrite.
- No dummy exam pattern, dummy question, guessed marks, guessed timer or guessed negative marking.
- Record every code change, source update, test result and unresolved item in this document or a linked per-exam evidence file.

## 9. Definition of done

An exam can be marked **IMPLEMENTED + RANKING ENABLED** only when all are true:

- Official current-cycle source packet is complete and independently checked.
- Canonical exam/stage/paper identity is unambiguous.
- All question counts, marks, timing, question types and negative-marking rules match official sources.
- Android and backend return identical scores for the same answer fixtures.
- All exam-specific and shared regression tests pass.
- Invalid/unverified inputs fail closed.
- Historical attempts retain their original config version and reproducible score.
- Ranking eligibility and 1.0x/0.2x classification are proven by tests.
- Privacy, security rules, indexes and callable API behavior are reviewed.
- Current branch-head CI is green.
- No unresolved overlap or dummy-data path remains.

Until then, status must remain **PENDING / NOT RANKING-ELIGIBLE**.

## 10. Current project verification boundary

This document is based on the integration-branch audit note that NEET UG 2026 and JEE Main Paper 1 2027 are the only two source-verified active exam configurations. The other 23 identities are not certified for authoritative scoring by this document. Re-read the actual branch head and current official notifications before coding; a historical audit note is not proof that the live branch or exam authority has not changed.

Latest known ranking integration audit also reported that current-head CI was queued/pending at the time of that audit. Therefore do not describe the overall ranking/scoring system as fully tested or release-ready until a new completed workflow confirms it.
