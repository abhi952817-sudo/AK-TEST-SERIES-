# Full CBT Simulation Interface & Bilingual Hindi/English Exam Experience

We will upgrade the test-taking experience into an authentic, production-grade Computer Based Test (CBT) simulation modeled after NTA (NEET/JEE) and TCS iON (SSC/Banking/Railways) standards, featuring a comprehensive Question Palette, instant Hindi/English bilingual toggle, authentic question status lifecycle, section breakdown, and test-taking tools.

## User Review & Critical Decisions

> [!IMPORTANT]
> Based on your answers, this implementation prioritizes:
> 1. **CBT Exam Interface and Hindi/English Toggle**: Top-bar global language switcher + question-level instant bilingual view.
> 2. **Full CBT Simulation with Question Palette**: Authentic NTA/TCS status badges (Answered, Not Answered, Not Visited, Marked for Review, Answered & Marked for Review), section tabs, status counters, and 1-tap jump navigation.

- **Confirmed Decision 1**: Add a prominent Language Toggle (`EN` | `HI` / `English` | `हिंदी`) in the top app bar for instant real-time translation of question stems, options, and explanations, while preserving the user's global language preference in `NEETViewModel`.
- **Confirmed Decision 2**: Transform the question palette into an authentic CBT status matrix with Section tabs (Section A compulsory, Section B optional attempt limits), real-time counters, status filter chips, and standardized color codes.
- **Enhanced CBT Tooling**: Include font-size zoom controls (A- / A / A+) and a scientific/basic virtual calculator accessible for applicable exams (GATE, JEE).

## 1. Overview & Core Concept

- **What It Does**: Provides competitive exam aspirants with a test interface identical to actual national CBT exams, eliminating exam-day anxiety and enhancing speed and navigation accuracy.
- **Target Audience / Persona**: Students preparing for NEET UG, JEE Main, SSC CGL/CHSL, Banking (IBPS/SBI), Railways (RRB NTPC), UPSC, GATE, and 15+ other competitive exams.
- **Key Value**: Accurate simulation of timing, marking schemes (+4/-1, +2/-0.5, +1/-0.25, etc.), question navigation, review workflows, and seamless bilingual comprehension.

## 2. User Experience & Visual Design

### Key User Flows

1. **Entering the Exam**:
   - The user launches any mock test, PYQ, or private test.
   - Screen enters immersive fullscreen CBT mode.
   - Top Bar displays test title, target exam badge, real-time animated countdown timer, global Language Switcher (`English` ⇄ `हिंदी`), Font Zoom (`A-`/`A+`), and Question Palette trigger button.

2. **Engaging with Questions**:
   - Section tabs at the top allow jumping between subjects/sections (e.g. `Physics - Sec A`, `Physics - Sec B`, `Chemistry`, `Biology / Maths`).
   - Question card displays question number, section rules, marking scheme chip (+4 / -1), and dual-language toggle button.
   - Options are selectable via tactile touch cards with clear radio/checkbox indicators.
   - Image diagrams and LaTeX formulas are rendered cleanly with pinch-to-zoom support.

3. **Authentic Action Bar**:
   - **Save & Next** (Green primary): Records choice and advances to next question.
   - **Save & Mark for Review** (Purple with green check): Records answer, marks for review, and advances.
   - **Mark for Review & Next** (Purple): Leaves question unanswered, marks for later review, and advances.
   - **Clear Response** (Outlined): Unselects the selected option for the current question.
   - **Previous & Next**: Direct pagination arrows.

4. **Interactive Question Palette Drawer / BottomSheet**:
   - Displays candidate details, exam title, and time remaining.
   - 5-Color NTA Status Grid with live count badges:
     - 🟢 **Answered**: Green
     - 🔴 **Not Answered**: Red/Orange
     - ⚪ **Not Visited**: Neutral Gray
     - 🟣 **Marked for Review**: Purple
     - 🟣✅ **Answered & Marked for Review**: Purple with green badge (evaluated in scoring)
   - Quick filters (Show All, Marked Only, Unanswered Only).
   - Instant tap to jump to any question.

5. **Submission & Analytics Handoff**:
   - Comprehensive summary dialog showing total questions, answered, unanswered, and marked counts before final submission.
   - Smooth transition into `TestReviewScreen` with percentile, rank prediction, and AI solution breakdowns.

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Real-Time Dual Language Strategy**
  - *Chosen Approach*: Support both global language toggle (changes default language across all test views) and individual question toggle (allows student to inspect English technical terms when studying in Hindi, or vice-versa).
  - *Why*: In exams like NEET and JEE, students frequently switch to English to verify specific scientific formulas or terms when Hindi phrasing is ambiguous.
- **Decision 2: Sectional Rules & Limit Enforcement**
  - *Chosen Approach*: Enforce NEET/JEE Section B rules (e.g. maximum 10 attempts out of 15 questions allowed in Section B) with instant warning feedback, matching actual exam criteria.
  - *Why*: Prevents students from over-attempting optional questions and simulates authentic exam discipline.
- **Decision 3: Non-Blocking Floating Virtual Calculator**
  - *Chosen Approach*: Render a clean, lightweight modal calculator dialog available on demand without leaving the exam screen.
  - *Why*: Crucial for GATE and engineering practice without relying on external apps or losing test focus.

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ExamSimulationScreen.kt                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ CBT Top Bar: Title | Target Exam | Timer | Lang Toggle (EN/HI)   │  │
│  │              Font Zoom (A-/A+) | Virtual Calc | Palette Icon     │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ Section Navigation Strip: [Physics Sec A] [Physics Sec B] ...   │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ Question Page View:                                              │  │
│  │ - Question Stem (EN / HI fallback) + Zoomable Diagram            │  │
│  │ - 4 or 5 Interactive Options / NAT Numerical Input               │  │
│  │ - Per-Question Language Toggle & Report Error Button             │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ CBT Navigation Bar: [Clear Response] [Mark for Review]           │  │
│  │                     [Save & Next] [Save & Mark for Review]       │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                  │                                     │
│                                  v                                     │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ CBT Question Palette (Drawer / BottomSheet):                     │  │
│  │ - 5 Status Badges: Answered / Not Answered / Not Visited /       │  │
│  │                    Marked / Answered & Marked                    │  │
│  │ - Section Filter & Fast-jump Grid (1..N)                         │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
                                   │
                                   v
┌────────────────────────────────────────────────────────────────────────┐
│                NEETViewModel & ExamScoringEngine                       │
│  - userAnswers: Map<String, String>                                    │
│  - markedForReview: Set<String>                                        │
│  - visitedQuestions: Set<String>                                       │
│  - selectedLanguage: StateFlow<AppLanguage> (EN / HI)                  │
│  - sectionAttemptLimits & validation                                   │
└────────────────────────────────────────────────────────────────────────┘
```
