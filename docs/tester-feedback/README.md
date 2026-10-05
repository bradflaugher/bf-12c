# Closed-test feedback

Before production access, bf-12c went through a closed test with a paid
testing provider. Their two reports are here as they arrived:

- [`com.bradflaugher.bf12c_feedback.pdf`](com.bradflaugher.bf12c_feedback.pdf):
  the test report. No crashes, bugs or broken features on any device or SDK
  they tried, plus a list of suggested improvements.
- [`com.bradflaugher.bf12c_production.pdf`](com.bradflaugher.bf12c_production.pdf):
  template answers for Google Play's production-access questionnaire. Our own
  answers, accurate to what we actually changed, are in
  [`production-access-answers.md`](production-access-answers.md).

## What we did with each suggestion

| Suggestion | What we did | Where |
|---|---|---|
| **App Store Optimization**: more keywords (financial calculator, RPN, cash flow, amortization), skimmable bullets, a call to action | Rewrote the full description around what people search for (RPN, financial calculator, loan and mortgage payments, TVM, NPV, IRR, amortization, bonds, depreciation) in bullet sections, with a one-line invitation at the end. Short description now names TVM, NPV/IRR, 34 digits and programs. Same voice, no keyword stuffing. | `fastlane/metadata/android/en-US/full_description.txt`, `short_description.txt` |
| **Play Store screenshots**: one feature per shot, text overlays, a consistent look | Every store screenshot now shows one feature (TVM, 34 digits, NPV/IRR with the f shift, the stack, programs, phosphors, the MENU key, the full keyboard) under a headline and subline set in the app's own fonts and colors. They are generated, not hand-made: `shoot.py` captures the real app on an emulator and `caption.py` adds the captions. The feature graphic was redone the same way. The README shots stay uncaptioned. | `tools/screenshots/shoot.py`, `tools/screenshots/caption.py`, `fastlane/metadata/android/en-US/images/` |
| **In-app ratings prompt** / "Rate Your App" button | **Button: done. Prompt: not done, on purpose.** Added **RATE ON GOOGLE PLAY** to the system menu (the MENU key), next to Share app: it opens the Play Store app's listing (`market://`), or the web listing if there's no store app, and says NO STORE OR BROWSER if neither can open. There is no prompt, no "neutral timing", no reminder and no in-app review API: it only does anything when someone chooses it. A unit test keeps the review API and prompts out. | `Links.kt` (`rateApp`), `ui/SystemMenu.kt`, `InvariantsTest.noReviewPromptsOrEmail` |
| **Share App** in settings | Added **SHARE APP** to the MENU-key system menu: it opens the Android share sheet with one line about the app and the Play link. No permission needed (the share sheet does the sending). | `Links.kt` (`shareApp`), `ui/SystemMenu.kt` |
| "Encouragement of sharing": reminders when using key features | **Not done, on purpose.** No "please share" prompts anywhere. Sharing is one menu item, there when wanted. | none |
| **User onboarding** | A first-run tips screen teaches what the keys can't show: swipe the display to backspace, long-press to copy, double-tap to paste, f / g and cancelling them, the MENU key, keyboard shortcuts, and what the other orientation gives you (worded for the current window shape). Shown once, dismissed with one tap anywhere (or Back, Esc, any key), and back any time from MENU › TIPS or the manual. Screenshot runs skip it with a launch extra. The key that opens the menu, the 12c's ON key, now reads **MENU** (same place, same keycode 41), so the menu is findable without the tips. | `ui/SystemMenu.kt` (`TipsOverlay`), `CalcViewModel.kt` (`tipsOpen`), `MainActivity.kt` (`EXTRA_SKIP_TIPS`), `engine/Key.kt` (`MENU`) |
| **Feedback mechanism** | Added **SEND FEEDBACK** to the system menu (the MENU key): it opens a new GitHub issue in the browser with the app version, Android version and device already filled in. No email address in the app. If there's no browser (some TVs, kiosks), the menu says NO BROWSER FOUND instead of crashing. | `Links.kt` (`sendFeedback`, `feedbackUrl`), `LinksTest.kt` |
| **Performance monitoring** | Nothing needed: the report found it fast on every device. The engine already runs on its own thread, and nothing added here touches it. No analytics or monitoring SDK was added (that would break the no-network promise). | none |
| **Accessibility** | Audited and fixed: every menu item and phosphor choice is now at least 48dp tall; the menu, manual and tips are TalkBack panes with headings; menu notices are announced; the brand plate reads as one name instead of three fragments; keyboard users get **F1** (or the Menu key) to open the system menu, which was mouse- or touch-only before, plus a visible focus bar and Tab / arrow / Enter navigation inside it; section labels got a little more contrast. Keys already had spoken names, and text already scales. | `ui/SystemMenu.kt`, `ui/CalculatorScreen.kt` |
| **Support documentation**: help section or FAQ | The system menu's manual became **MANUAL + FAQ**: new CLEARING and QUESTIONS sections (no = key, wrong TVM answers, the cash-flow sign rule, rotating, privacy, reporting a bug), plus SHOW TIPS AGAIN. | `ui/SystemMenu.kt` (`MANUAL`) |

## Unchanged promises

bf-12c still asks for no permissions at all: no `INTERNET`, no backups.
Share and feedback hand an intent to the share sheet or the browser, and
those apps do the networking. `InvariantsTest` checks the manifest.
