# First-Launch Interactive Tutorial — Implementation Plan

**Status:** PLAN READY — implementation is the next phase.  
**Target:** Android app (Kotlin + Jetpack Compose), reusable across 20+ exam configurations.  
**Scope:** Repair/replace the current first-run tutorial after auditing it; add contextual floating guidance to important options; provide replay/help access.

## 1. Experience goal

When a person opens the app for the first time, explain the interface in context. Do not show a long wall of text. Highlight the actual control being explained, display a short floating coach-mark card near it, and guide the user to the next relevant action.

The tutorial must teach the user how to use the app, not merely show decorative animations.

## 2. Tutorial modes

1. **First-launch walkthrough:** A short welcome and the essential app navigation. Show only once after the user has successfully entered the app.
2. **Contextual floating tips:** Small coach marks on important options such as exam selection, test series, start test, question palette, mark for review, timer, submit, PDF upload, private/public sharing, and help. Show a tip when the user first reaches the relevant screen or taps its help icon.
3. **Replay tutorial:** A permanent Help / “App kaise use karein?” entry in Profile or Settings. Allow the user to replay all or a specific section.
4. **Skip and resume:** Let users skip, close, or continue later. Never trap the user in a tutorial.

## 3. Visual behavior

- Dim the background lightly and draw a clear focus highlight around the real target control.
- Place a floating card/tooltip above, below, or beside the target depending on available screen space.
- Include a small title, one or two simple Hindi-first explanation lines, progress such as “2/6”, and Back / Next / Skip / Close actions as appropriate.
- Use a subtle pulse or glow on the target and a short fade/slide transition for the tooltip.
- Recalculate placement on screen-size/orientation changes; keep the card within visible bounds.
- If a target is off-screen, scroll it into view before highlighting it.
- If a target is missing, disabled, or inaccessible due to role/feature availability, skip that step safely and explain only when useful.
- Respect system reduced-motion settings; animation must never be the only way to convey meaning.

## 4. Suggested Student walkthrough

| Step | Target | Explanation |
|---|---|---|
| 1 | Home / Dashboard | “Yahan se aap apna exam aur zaroori activity khol sakte hain.” |
| 2 | Exam selector | “Apna exam chunein; isi ke hisaab se syllabus aur test dikhenge.” |
| 3 | Test Series | “Yahan mock, practice aur topic-wise tests milenge.” |
| 4 | Start Test | “Nirdesh padhkar test shuru karein.” |
| 5 | Question controls | “Answer chunein, question badlein aur zarurat par review ke liye mark karein.” |
| 6 | Timer and Submit | “Bacha hua samay dekhein; submit karne se pehle answers check karein.” |
| 7 | Result / Review | “Server se confirm hua result aur anumati-prapt solution yahan dekhein.” |
| 8 | Help / Profile | “Tutorial dobara dekhne aur settings ke liye yahan aayein.” |

Keep the first-run core tour short. Explain advanced options contextually when the user first opens those screens instead of forcing every option into one long tour.

## 5. Contextual tips for other important panels

- PDF upload: file selection, page count, quota, extraction progress, validation, failure/retry.
- Private Test Vault: private ownership, rename/delete, and explicit sharing choice.
- Community: browse, download, share, and report controls.
- Study material and analytics: chapter/resource navigation and progress interpretation.
- Doubt/help: how to ask a question and attach relevant context.
- Admin: show role-specific tutorials only to Super Admin, Content Admin, or Support Admin; explain permissions and audit reasons. Never expose admin tutorial entries or privileged actions to students.

## 6. Technical design proposal

- Build a reusable Compose coach-mark/spotlight component (overlay + target bounds + tooltip placement + step controller), rather than implementing separate one-off animations on every screen.
- Use stable semantic/test tags or explicit target registration so each tutorial step is anchored to the correct composable. Avoid fragile screen-coordinate constants.
- Keep step definitions in a central, versioned registry. Store copy in Android string resources for Hindi and English (and other supported app languages).
- Persist completion/skip/version state per signed-in user using the app's existing preferences/DataStore architecture; use a safe local anonymous state before sign-in if necessary. Do not store sensitive profile data for tutorial state.
- Use a tutorial version key so a redesigned major flow can offer a refreshed tour without repeatedly interrupting users.
- Ensure the tutorial overlay does not intercept touches outside its intended controls unless the step explicitly requires an action.
- Make Next/Back/Skip/Close accessible to TalkBack and keyboard/focus navigation.
- Add analytics only for aggregate tutorial progress if already permitted by the app privacy policy; do not log question content, answers, PDFs, or sensitive user data.

## 7. Existing tutorial audit — required before replacement

The implementation phase must locate every existing onboarding/tutorial/tooltip/coach-mark implementation and answer:
- Which screens launch it, and under what condition?
- Is it shown once or repeatedly? Where is completion saved?
- Are target positions tied to hard-coded coordinates?
- Do Next/Skip/Close work on every screen size?
- Does it explain current controls, or refer to removed/renamed options?
- Does it cover Student and Admin separately?
- Does it block taps, navigation, test timer, or accessibility?
- Are Hindi/English strings correct and synchronized?

Classify each existing tutorial as KEEP, FIX, REPLACE, or REMOVE, with file path and evidence. Do not delete a working flow until its replacement and regression coverage exist.

## 8. Acceptance tests

- Fresh install: first-launch tutorial appears at the right point, once.
- Returning user: completed tutorial does not unexpectedly restart.
- Skip/close: app remains fully usable and tutorial can be replayed from Help.
- Tutorial version update: refreshed tour appears only according to the documented version policy.
- Student account: sees only Student guidance.
- Each Admin role: sees only permitted role-specific guidance.
- Different screen sizes, font scaling, orientation, and long Hindi text: tooltip remains visible and readable.
- Reduced motion enabled: no pulse/slide motion; all explanations remain available.
- TalkBack: step title, explanation, progress, and controls are announced in order.
- Missing target / feature disabled / navigation interruption: tutorial recovers without crash or dead end.
- Test player: tutorial never pauses, changes, or obscures the official timer or answer controls.
- Regression: normal navigation and all underlying actions work with tutorial enabled and disabled.

## 9. Implementation order

1. Inventory current tutorial code and navigation entry points.
2. Record the existing behavior and defects in the implementation tracker.
3. Define target IDs and a versioned Student/Admin step registry.
4. Implement reusable Compose spotlight + floating tooltip component.
5. Add persistence, first-launch gating, skip/resume, and replay entry.
6. Wire the Student core tour and contextual panel tips.
7. Wire role-specific Admin guidance and permission-aware target filtering.
8. Add Hindi/English strings, accessibility, reduced-motion, and layout handling.
9. Add Compose/UI regression tests and run Android build/test suites.
10. Update README and tracker with exact files, commit, and actual test status.

## 10. Definition of done

The feature is complete only when the old tutorial has been audited, important controls have correct contextual explanations, the floating highlight/tooltip works on supported screen sizes, first-run/replay/skip behavior is reliable, role separation and accessibility pass, and tests have actually been executed.

**Important:** This document is a plan, not a claim that the tutorial implementation already exists. The code changes and runtime verification are pending.
