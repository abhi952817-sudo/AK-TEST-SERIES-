# AK TEST SERIES

AK TEST SERIES is an Android exam-preparation app project focused on NEET UG first, with a reusable foundation intended to support additional competitive examinations. The repository contains Android application code, Firebase-backed services, and a Node.js Cloud Functions backend.

> **Project status: Active development — not represented as production-ready.** The presence of a screen, class, service, or plan does not by itself mean that the complete user journey is working.

## What is present in the codebase

The current repository includes the following implemented components. Their presence is based on source and project documentation; it is not a claim that every feature has passed a current end-to-end device test.

- **Android application:** Kotlin, Jetpack Compose, ViewModel/Flow-based state, and an MVVM-style structure.
- **Local data layer:** Room database entities, DAOs, repositories, and migration code for local app data and test-related records.
- **Firebase integration:** Firebase Authentication, Cloud Firestore, Cloud Functions, Cloud Messaging-related code, and Firebase App Check integration.
- **Exam and question foundations:** Exam registry/configuration code, question models, question validation, duplicate-detection logic, and exam-specific scoring components.
- **Test and attempt foundations:** Student test screens and attempt-related code, including server-callable submission/scoring paths.
- **Results and ranking foundations:** Backend scoring and ranking functions, eligibility logic, and related Android state/UI code.
- **Admin and community areas:** Admin dashboard/management screens, community post and test-sharing code, and private-test/vault-related components.
- **AI integration foundations:** Gemini-related Android/backend integration code and AI-related configuration/components. Provider routing, secure key handling, quotas, and complete Student/Admin workflows still require integration and verification.

## Important limitations

The full NEET UG journey — sign-in, test discovery, start, question navigation, answer saving, timer recovery, submission, authoritative result, solutions, and eligible ranking — must be verified as one complete flow before the app is described as launch-ready.

Repository audits and development trackers record open work, including:

- Firebase project and named Firestore database identity must be confirmed before changing or migrating production data.
- Some Admin upload/publish paths are not consistently wired end-to-end.
- Student-generated tests and community sharing need private-by-default behavior and enforceable recipient access controls; answer keys and explanations must not be exposed publicly.
- AI entry points need a single secure, policy-controlled backend path; direct client-side provider calls and key handling require review.
- Room database upgrade paths and legacy data-loss risks need further validation.
- Exam patterns and scoring rules must be checked against current authoritative exam sources. A registry entry is not proof of current official accuracy.
- PDF/OCR extraction and question generation are not claimed as a reliable, launch-ready workflow.
- Ranking must use verified eligible attempts; a displayed ranking component is not proof of official national rank.

## Technology

- **Android:** Kotlin, Jetpack Compose, Android Gradle build system
- **Architecture:** ViewModels, Kotlin Flow/Coroutines, repositories
- **Local persistence:** Room
- **Cloud/backend:** Firebase Authentication, Cloud Firestore, Firebase Cloud Functions
- **Backend runtime:** Node.js
- **Quality checks:** Android tests, Node.js tests, Firestore rules tests, and GitHub Actions workflows are present in the repository. Test results must be checked against the exact commit being evaluated.

## Development and verification

The project is under active audit and integration. Work is organized around small, reviewable changes, reuse of existing implementations, security and data-safety checks, and explicit verification.

Do not treat historical test reports or a successful build as proof that every Student/Admin workflow works. For a release decision, run the relevant tests and verify critical flows on the target build and environment.

See the repository's existing development and audit records:

- [Master implementation tracker](app/MASTER_IMPLEMENTATION_TRACKER.md)
- [Branch reconciliation and integration plan](docs/BRANCH_CODE_RECONCILIATION_AND_INTEGRATION_PLAN.md)
- [Universal PDF intelligence architecture](docs/UNIVERSAL_PDF_INTELLIGENCE_ARCHITECTURE.md)
- [Community development plan](docs/community/COMMUNITY_DEVELOPMENT_PLAN.md)
- [CI run history snapshot](docs/CI_RUN_HISTORY_2026-11-13.md)

## Current release position

**Not yet declared launch-ready.** The project should be considered in development until the critical Student and Admin flows, security boundaries, data migrations, exam configuration, and regression checks are verified on the intended release commit.
