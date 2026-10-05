# Production access: draft answers

Draft answers for Google Play's production-access questionnaire, based on
the tester's template ([`com.bradflaugher.bf12c_production.pdf`](com.bradflaugher.bf12c_production.pdf))
but rewritten to match what actually changed. Brad: check every
**[BRAD: ...]** spot before pasting. Note that the template says a "Rate Your
App" option was added. A **Rate on Google Play** button was added to the
menu, but there's no rating prompt or reminder, by design, and the answers
say exactly that.

---

### 1. How did you recruit users for your closed test?

I used a paid testing provider so the app got a proper run across a range
of real devices and Android versions, with a written report at the end.
**[BRAD: if you also had friends, classmates or finance people try it, add
one sentence, e.g. "I also gave it to a few people who use a 12c at work."
Otherwise delete this note.]**

### 2. How easy was it to recruit testers for your app?

**[BRAD: pick one: Very easy / Easy / Neither easy nor difficult / Difficult / Very difficult.]**
(The template suggests "Easy". With a paid provider that's honest.)

### 3. Describe the engagement you received from testers during your closed test

Testers used the app across phones and tablets on several Android versions
and went through every part of it: the financial keys, the stack, program
mode, the display themes and the settings menu. They reported no crashes,
bugs or broken functions. Their feedback was mostly about the store listing
and about features around the calculator, such as sharing, sending
feedback, onboarding and help, which is the useful kind of feedback for a
calculator that already worked.

### 4. Provide a summary of the feedback that you received from testers. Include how you collected the feedback.

The feedback came as a written report from the testing provider at the end
of the test. It found no crashes or bugs. The suggestions were: improve the
store listing's keywords and layout, make the screenshots show individual
features with captions, add a way to share the app and a way to send
feedback, add first-run onboarding and an in-app help or FAQ section, and
check accessibility. They also suggested a rate button with a prompt at
"neutral" times; I added the button but not the prompt (see question 8).

### 5. Who is the intended audience for your app?

People who use or are learning an RPN financial calculator: finance and
business students, analysts, real estate and lending professionals,
accountants, and anyone who grew up on a 12c and wants one on their phone.
It also suits anyone who just wants a precise calculator with no ads.

### 6. Describe how your app provides value to the users.

bf-12c is a full RPN financial calculator: time value of money for loans
and mortgages, NPV and IRR, amortization, bonds, depreciation, date math,
statistics and keystroke programming, with the classic 12c keyboard layout
people already know. It works to 34 significant digits, keeps everything
between sessions, and works on phones, tablets and Chromebooks, with touch,
mouse, a hardware keyboard or TalkBack. It's free, with no ads, no
tracking, no network access and no permissions at all.

### 7. How many installs do you expect your app to have in your first year?

**[BRAD: pick a range. The template suggests 10K - 100K. For a niche RPN
calculator with no marketing, something like 1K - 10K may be more
realistic. Your call.]**

### 8. What changes did you make to your app based on what you learned during your closed test?

- Rewrote the store description with the terms people actually search for
  (RPN, financial calculator, TVM, NPV, IRR, amortization) and split it into
  short sections.
- Replaced the screenshots with captioned ones, each showing one feature,
  in the app's own style, plus a new feature graphic.
- Added "Share app" to the settings menu, which opens the Android share
  sheet with a link to the app.
- Added "Rate on Google Play" next to it, which opens the app's Play
  listing so anyone who wants to leave a rating can find it in one tap.
- Added "Send feedback", which opens a new GitHub issue with the app and
  device details filled in, so bugs and feature requests have a home.
- Added first-run tips that show the gestures you'd never guess (swipe to
  backspace, long-press to copy, double-tap to paste) and the settings menu.
  They show once, go away with one tap, and can be brought back from the
  menu.
- Expanded the built-in manual with an FAQ.
- Accessibility fixes: larger touch targets in the menu, better screen
  reader labels, and full keyboard access to the menu.

One part I deliberately didn't take: prompting people to rate. I find those
pop-ups annoying, and a calculator should just calculate, so the rate
button only does anything when someone presses it. There's no rating
prompt, no reminder, and no nagging to share.

### 9. How did you decide that your app is ready for production?

The closed test found no crashes or bugs on any device it ran on, and the
improvements it suggested are now in. Beyond that, the calculator engine
has a large automated test suite (worked examples from the 12c owner's
handbook, a regression test for every bug fixed, and randomized property
tests and a keystroke fuzzer), and every change has to pass it and Android
lint before it can be merged.

### 10. What did you do differently this time?

**[BRAD: this question is about your previous production-access attempt or
previous apps. If this is your first application for bf-12c, say so in a
sentence. If you were turned down before, describe the difference, e.g.:]**
This time I ran a full closed test with outside testers on a wide range of
devices, got a written report, and acted on it before applying: a clearer
store listing, feature screenshots, share and rate buttons, a feedback
link, onboarding tips, an FAQ and accessibility fixes.
