# Official Authority Discovery Matrix — 25 Registered Exams
Research date: 2026-11-20
Branch: `research/official-exam-syllabus-audit-2026-11-20`
Status: discovery pass; this is not yet the final topic-level verification for every exam.

## Priority findings
1. **GATE 2027 is materially revised.** IIT Madras is the organizing institute; the official portal says 30 papers and explicitly says syllabi have been revised. It introduces Robotics & Automation (RA) and changes XE/XH/XL section-code mappings. The old single static GATE config is not sufficient for 2027.
2. **UGC-NET December 2026 has an updated bulletin and corrigendum dated 16 October 2026.** The updated bulletin supersedes inconsistent earlier provisions. Pattern: Paper I 50 questions/100 marks, Paper II 100 questions/200 marks, 180 minutes total, all compulsory, 2 marks each, no negative marking. Syllabus PDFs are hosted by UGC, not embedded in NTA's exam bulletin.
3. **CUET UG 2026 exposes a subject-code catalogue** on the official NTA portal: language, domain and General Aptitude subjects. Each subject syllabus is linked separately; do not flatten CUET into one generic subject list.
4. **SSC has a 2026–27 examination calendar and an official scheme-of-examination page** with downloadable exam-specific documents. A single common SSC marking pattern would be incorrect.
5. **UPSC Annual Calendar 2027 is published.** The UPSC portal lists NDA I 2027 and CDS I 2027 notification dates as 2 December 2026, which is still in the future relative to this research date. Do not create final 2027 paper policies from a calendar entry alone.
6. **RRB notices are CEN-specific.** The current official RRB Chandigarh index lists CEN-01/2026 ALP and 2025 CEN notices for NTPC graduate/undergraduate, Group D, JE and Technician. Each CEN is its own source record.
7. **CAT 2026 official portal access returned HTTP 403 in this research pass.** Secondary sources are discovery leads only. Obtain the official CAT bulletin directly through Firecrawl/Acrobat or an official IIM-hosted PDF before approving exact section/marking data.

## Registered exam-by-exam status
| Exam ID | Exam | Authority / official entry point | Discovery status as of 2026-11-20 |
|---|---|---|---|
| `neet_ug` | NEET UG | NMC/UGMEB; NTA; https://nmc.org.in/whats-new/download/920 | 2026 syllabus verified; 2027 syllabus not located in official search. |
| `jee_main` | JEE Main | NTA; https://jeemain.nta.nic.in/ | 2027 syllabus and bulletin listed; official NTA exam overview gives 75 compulsory / 300 marks / 180 min / +4-1-0. Topic PDF extraction pending. |
| `jee_advanced` | JEE Advanced | Joint Admission Board / https://jeeadv.ac.in/ | Official source audit not yet completed. Must be separate from JEE Main. |
| `cuet_ug` | CUET UG | NTA; https://cuet.nta.nic.in/cuetug-2026-syllabus/ | 2026 subject-code list and 2026 bulletin listed. Subject syllabi must be imported separately. |
| `upsc_cse` | Civil Services Examination | UPSC; https://upsc.gov.in/examination-calendar/ | 2027 annual calendar published; detailed CSE notice/syllabus extraction pending. |
| `ssc_cgl` | SSC CGL | SSC; https://ssc.gov.in/for-candidates/examination-calendar and https://ssc.gov.in/for-candidates/scheme-of-examination | 2026 calendar and CGL scheme PDF listing found; exact notice/corrigendum audit pending. |
| `ssc_chsl` | SSC CHSL | SSC; same SSC calendar and scheme pages | 2026 calendar and CHSL scheme PDF listing found; exact notice/corrigendum audit pending. |
| `ssc_mts` | SSC MTS/Havaldar | SSC; same SSC calendar and scheme pages | 2026 calendar lists MTS; current-year detailed pattern audit pending. |
| `ssc_gd` | SSC GD | SSC; same SSC calendar and scheme pages | GD Constable 2027 appears in 2026–27 calendar; detailed 2027 notice audit pending. |
| `ssc_cpo` | SSC CPO | SSC; same SSC calendar and scheme pages | 2026 calendar lists CPO; detailed notice/pattern audit pending. |
| `rrb_ntpc` | RRB NTPC | RRB; https://www.rrbcdg.gov.in/employment-notices.php | CEN-06/2025 Graduate and CEN-07/2025 Undergraduate listed; keep separate CEN/source versions. |
| `rrb_group_d` | RRB Group D / Level 1 | RRB; same employment notices page | CEN-09/2025 Level 1 listed; official detailed CEN PDF located. |
| `rrb_alp` | RRB ALP | RRB; https://www.rrbcdg.gov.in/ | CEN-01/2026 listed; official detailed notice and FAQ found. |
| `rrb_technician` | RRB Technician | RRB; same employment notices page | CEN-02/2025 listed; detailed CEN extraction pending. |
| `rrb_je` | RRB JE | RRB; same employment notices page | CEN-05/2025 JE/DMS/CMA/CS/MS listed; detailed CEN extraction pending. |
| `ctet` | CTET | CBSE/CTET; https://ctet.nic.in/information-bulletin/ | Feb-2026 and Sept-2026 information bulletins listed; use current bulletin and NCTE syllabus references. |
| `ugc_net` | UGC-NET | NTA/UGC; https://ugcnet.nta.nic.in/information-bulletin/ | Updated Dec-2026 bulletin and 16-Oct-2026 corrigendum found; official pattern verified; individual subject syllabus must be fetched from UGC. |
| `cat` | CAT | IIMs / CAT; https://iimcat.ac.in/ | Official site identified but returned 403 to browser extraction. Do not use secondary-only facts to approve registry. |
| `ibps_po` | IBPS PO/MT | IBPS; https://www.ibps.in/index.php/management-trainees-xvi/ | CRP-PO/MT-XVI notification and 2026 updates found; exact official bulletin extraction pending. |
| `ibps_clerk` | IBPS CSA (legacy app label: Clerk) | IBPS; https://www.ibps.in/index.php/clerical-cadre-xvi/ | CRP-CSA-XVI notification 01-Aug-2026 and corrigendum 29-Sep-2026 found; use CSA name and updated documents. |
| `sbi_clerk` | SBI Junior Associate | SBI Careers; https://sbi.co.in/web/careers/junior-associate | General careers pattern page exists; year-specific current notification must be retrieved before verification. |
| `sbi_po` | SBI Probationary Officer | SBI Careers; https://sbi.co.in/web/careers/probationary-officers | General careers pattern page exists; year-specific current notification must be retrieved before verification. |
| `nda` | NDA & NA | UPSC; https://upsc.gov.in/examination-calendar/ | UPSC Annual Calendar 2027 lists NDA I 2027 notification on 02-Dec-2026; future at research date. |
| `cds` | CDS | UPSC; same calendar | UPSC Annual Calendar 2027 lists CDS I 2027 notification on 02-Dec-2026; future at research date. |
| `gate` | GATE | IIT Madras; https://gate2027.iitm.ac.in/exam_papers_and_syllabus | 2027 official portal confirms revised syllabi, 30 papers, RA new paper, and revised XE/XH/XL mappings. Full paper-wise extraction required. |

## Source evidence and concrete findings

### GATE 2027
Official page: https://gate2027.iitm.ac.in/exam_papers_and_syllabus
- Organizing Institute: IIT Madras.
- Official page says GATE 2027 syllabi have been revised.
- 30 test papers are offered.
- A candidate may appear in one or up to two papers subject to allowed combinations.
- Each paper totals 100 marks; General Aptitude is 15 marks and the paper-specific syllabus is 85 marks.
- Robotics & Automation (RA) is a new paper.
- XE, XH and XL section codes/mappings are revised. For example, XE has a common Engineering Mathematics section plus any two optional sections; XH has common Reasoning and Comprehension plus one optional section; XL has common Chemistry plus two optional sections.
- Implication: GATE must be modeled as 30 distinct paper codes with their own syllabus, section choices and marking rules. Do not keep a single `gate` subject/paper schema.

### UGC-NET December 2026
Official updated bulletin/corrigendum:
https://nta.gov.in/cms/uploads/202610161905649093_10704d6197.pdf
- NTA issued a corrigendum and updated information bulletin on 16-Oct-2026. Updated bulletin prevails over inconsistent earlier text.
- Pattern: Paper I = 50 questions / 100 marks; Paper II = 100 questions / 200 marks; total duration 180 minutes, without a break; all questions compulsory.
- Each correct response receives 2 marks; there is no negative marking.
- The subject-specific syllabus is published on UGC's syllabus portal: https://www.ugcnetonline.in/syllabus-new.php
- NTA's English overview reports 85 subjects while the Hindi overview surfaced 87. Count the actual current Annexure-II subject list and reconcile the discrepancy before importing the subject registry.
- The 16-Oct corrigendum changes dates and some candidate instructions; do not ignore it when consuming the base bulletin.

### CUET UG 2026
Official page: https://cuet.nta.nic.in/cuetug-2026-syllabus/
- The portal lists subject codes and categories, including language tests, domain subjects and General Aptitude Test.
- Example: English code 101; Hindi code 102; Biology/Biological Science/Biotechnology/Biochemistry code 304; Chemistry 306; Physics 322; Mathematics/Applied Mathematics 319; General Aptitude Test 501.
- Each subject has a separate linked syllabus document. Model CUET as selectable test subjects with per-subject syllabus and test structure, not as a single monolithic paper.

### SSC 2026–27
Official calendar: https://ssc.gov.in/for-candidates/examination-calendar
Official scheme page: https://ssc.gov.in/for-candidates/scheme-of-examination
- Calendar is explicitly titled Tentative Calendar of Examinations for 2026–2027.
- It lists CGL 2026, CHSL 2026, MTS/Havaldar 2026, CPO 2026 and GD Constable 2027.
- The scheme page exposes separate downloadable pattern documents, including CGL and CHSL. The app must keep each SSC exam's own tier/session structure.

### UPSC 2027
Official calendar: https://upsc.gov.in/examination-calendar/
- Annual Calendar 2027 upload is listed as 20-May-2026.
- CDS I 2027 and NDA/NA I 2027 notification date is listed as 02-Dec-2026, which is after this research date.
- Keep calendar events separate from a final exam notification or syllabus. The latter must be captured when published.

### RRB
Official employment notices: https://www.rrbcdg.gov.in/employment-notices.php
- Current index lists CEN-01/2026 ALP.
- 2025 CENs include NTPC UG (07/2025), NTPC Graduate (06/2025), Level 1/Group D (09/2025), JE/DMS/CMA/CS/MS (05/2025), Technician (02/2025).
- ALP CEN-01/2026 FAQ says the CBT syllabus is in the detailed CEN paragraphs 12.1 and 12.2 and the stages include CBT-1, CBT-2, CBAT, DV and Medical.
- Treat each CEN and post category as its own versioned source package.

### CTET 2026
Official bulletin page: https://ctet.nic.in/information-bulletin/
- The official page lists both Feb-2026 and Sept-2026 information bulletins.
- Use the target session's bulletin and its cited NCTE curriculum/syllabus. Do not assume every CTET session shares all dates and administrative rules.

### IBPS 2026
- PO/MT-XVI official page: https://www.ibps.in/index.php/management-trainees-xvi/
- CSA-XVI official page: https://www.ibps.in/index.php/clerical-cadre-xvi/
- The CSA post is officially named Customer Service Associate; retain `ibps_clerk` only as a backward-compatible app ID/display alias.
- Current source pages contain 2026 notices and corrigenda. Use the latest corrected official documents, not a generic old banking pattern.

## Important source/version rules
- A syllabus document and an information bulletin are different sources. Capture both independently.
- A calendar does not establish a full paper pattern or syllabus.
- An official exam portal listing a PDF proves the document exists, not that its full contents have been extracted.
- Where a current bulletin has a corrigendum, the corrected version supersedes the base text.
- Dynamic subject catalogues (CUET, UGC-NET, GATE) must be stored as first-class collections, not hard-coded as one subject string.
- Legacy exam IDs may be retained for compatibility, but labels, year, paper codes and scoring versions must reflect the official authority terminology.

## Immediate research queue
1. GATE 2027: extract all 30 paper syllabi and revised section mappings; mark old GATE config stale.
2. UGC-NET Dec 2026: count official subjects from corrected Annexure-II and import official UGC subject syllabi.
3. CUET UG 2026: download all subject-specific syllabus PDFs and capture the selectable paper structure.
4. RRB: extract each CEN separately, beginning with ALP 01/2026 and NTPC UG/Graduate.
5. SSC: extract current 2026/2027 notices and separate scheme documents.
6. UPSC: capture 2027 annual calendar, then await the official CSE/NDA/CDS notices where not yet released.
7. CAT and SBI: obtain the exact current-year official bulletin/advertisement directly from their portals before marking any pattern verified.
8. JEE Main 2027: obtain the official syllabus PDF bytes and compare topic-by-topic with 2026.
9. NEET UG 2027: monitor NMC/UGMEB and NTA notices; do not pre-populate a 2027 syllabus.
