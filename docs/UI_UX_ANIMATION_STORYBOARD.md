# AK Test Series — UI/UX Animation Video Storyboard

**Status:** Planned; no rendered video is attached yet.  
**Target duration:** 90–120 seconds  
**Format:** 16:9 landscape, 1080p, with captions; create a separate vertical cut only if needed.  
**Audience:** Students, Admins, developers, and reviewers.

## Purpose

Demonstrate how a student and an Admin move through the product, how the interface responds to actions, and how motion communicates state without interrupting important tasks.

## Scene plan

| Time | Scene | Visual action | Narration / on-screen message |
|---|---|---|---|
| 0–8 sec | Product introduction | App mark, exam dashboard, exam cards | “One exam-preparation experience, configured for multiple exams.” |
| 8–18 sec | Student dashboard | Highlight exam selection, continue test, progress summary | “The dashboard makes the next useful action easy to find.” |
| 18–30 sec | Test journey | Exam → test list → instructions → player | “Students can start a test through a clear, predictable flow.” |
| 30–43 sec | Test player | Select answer, move question, flag for review, timer stays visible | “Answer state, question position, and time remain clear.” |
| 43–53 sec | Submit and result | Confirmation → progress → result breakdown | “Submission is protected against accidental repeats; official results come from the server.” |
| 53–68 sec | PDF-to-test | Choose PDF → extraction progress → preview → validation status | “The app shows what was extracted and flags uncertain content instead of inventing questions.” |
| 68–82 sec | Admin exception review | Role-based dashboard → exception queue → review → audit confirmation | “Admins focus on exceptions and only perform actions their roles permit.” |
| 82–98 sec | Motion/accessibility | Show subtle transitions, loading/error/empty states, reduced-motion alternative | “Animation supports understanding, but never blocks tasks or carries meaning alone.” |
| 98–110 sec | Closing | Student flow and Admin flow side by side | “Clear journeys, secure actions, and consistent behavior across supported exams.” |

## Motion direction

- Use restrained fades, short slides, and small scale/opacity feedback.
- Keep transitions quick and consistent; avoid long cinematic effects inside the test player.
- Show readable captions for every narration point.
- Include a reduced-motion version using immediate state changes or simple fades.
- Use representative UI captures from the real app; do not present mock screens as shipped features.
- Blur or replace all personal information, tokens, email addresses, and real student records.
- Never show private answer keys, credentials, or production secrets.

## Capture checklist

1. Record the actual app build and note its commit/build identifier.
2. Verify each scene on a real supported device or emulator.
3. Capture loading, empty, success, error, and offline states where relevant.
4. Verify the student test timer and answer selection remain responsive.
5. Verify the Admin role shown has permission for the depicted action.
6. Add captions and narration; check readability on mobile.
7. Export MP4, review the complete file, then place it at `docs/media/ak-test-series-ui-ux-overview.mp4`.
8. Update the README video status and add the verified video link only after the file or hosted URL exists.

## Truthfulness rule

This storyboard is documentation, not proof that every described screen or interaction is implemented. Mark any unavailable screen as a design prototype during recording. The walkthrough must reflect the actual build being demonstrated.
