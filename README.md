# Exit & EMI

An offline Android app that answers one question: **if you left your job, could you
afford the gap?**

It puts your exit settlement on one side and the loan EMIs it has to cover on the other,
and tells you how many months that buys. A full and final settlement calculator and a
multi-loan EMI calculator feed that answer.

See [PLAN.md](PLAN.md) for the research, the product reasoning and the phasing.

## Why it exists

India's four Labour Codes came into force on **21 November 2025** and redefined "wages":
basic + DA + retaining allowance must be at least half of total pay, with any excess
added back. Per the Ministry of Labour's FAQ dated 16 March 2026, **gratuity is computed
on that revised base with effect from 21.11.2025** — which means a lot of settlement
calculators and HR spreadsheets still in circulation are using the wrong base.

The other thing this app does differently: the figures that vary are *inputs*. The
statute fixes gratuity at 15/26, but employers differ on the per-day divisor (26 vs 30),
whether leave encashment runs on basic or gross, and what notice recovery is calculated
on. Those are pickers with defaults, not hardcoded constants, and every computed figure
can show its formula and its source.

## Modules

| Module | What it is | Tested |
|---|---|---|
| `:core-calc` | Pure Kotlin/JVM. EMI, amortisation, prepayment, settlement, gratuity, leave encashment, tax, scratch calculator, history and resume models. `BigDecimal` throughout. | **73 tests** |
| `:core-ui` | Pure Kotlin presentation logic: Indian number formatting, input parsing, calculator keypad reducer. No Android dependency. | **31 tests** |
| `:app` | Jetpack Compose UI, Room persistence, navigation. | Not yet |

Keeping the first two free of Android means the arithmetic — where all the correctness
risk is — runs in CI without an emulator, and stays portable if iOS is ever added.

## Building

```bash
./gradlew :core-calc:test :core-ui:test   # no Android SDK needed
./gradlew :app:assembleDebug              # needs the Android SDK
```

`:app` is **included only when an Android SDK is present** (`ANDROID_HOME`,
`ANDROID_SDK_ROOT`, or `sdk.dir` in `local.properties`). Without one, Gradle prints a
notice and builds the two pure modules, so CI and restricted environments still get the
tests. Opening the project in Android Studio works as normal.

### Money is never a `Double`

Every amount is a `BigDecimal`. Binary floating point cannot represent most decimal
fractions, so interest accrual drifts and schedules stop closing at zero. Room stores
amounts as `TEXT` for the same reason — SQLite's `REAL` is a double.

### The schedule closes at exactly N instalments

Lenders quote the EMI in whole rupees, which leaves a small residue. A naive schedule
spills that into an extra instalment — an 80 lakh, 20-year loan produces a 241st
instalment of ₹121.76. Real lenders put the residue in the **final** instalment instead,
and so does this. It is the most common reason a calculator disagrees with a bank
statement.

## Privacy

The app declares **no `INTERNET` permission**. Every figure is computed on the device
from values you type, so salary and loan data cannot leave the phone — enforced by the
system rather than promised in a policy.

## Status

The calculation and presentation cores are built and tested. The Compose UI is written
but has **not been compiled or run** — it was authored in an environment without access
to Google's Maven repository. Expect to run `:app:assembleDebug` once and fix whatever
the first build reports.

---

Figures are estimates for planning, not legal or tax advice. Central and state rules
under the Labour Codes were still being notified at the time of writing; re-verify
before relying on any number.
