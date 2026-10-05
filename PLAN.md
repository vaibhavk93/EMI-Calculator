# Loan EMI + Exit Settlement (F&F) Android App — Research & Plan

**Status:** Plan agreed. Calculation core built and tested (73 tests green). Android UI not started.
**Date:** 2026-10-05

### Decisions taken

| Decision | Choice |
|---|---|
| Primary user | **Both, employee-first** — consumer MVP, core kept reusable for an HR layer later |
| Product thesis | **Runway is the hero screen** |
| Platform | **Android only for now** (Compose native); core module keeps iOS open |
| Build environment | **Unblock `dl.google.com`** so the APK builds in the cloud session |

---

## 1. Executive summary

**Recommendation: build it as one Android app, offline-first, with no backend in v1 — but reframe the product around a single job, not two calculators.**

The brief ("calculate multiple loan EMIs" + "F&F of a leaving employee") describes two unrelated jobs done by two different people:

| | EMI calculator | F&F calculator |
|---|---|---|
| Who | A borrower | An exiting employee, or an HR/payroll executive |
| How often | Ongoing, monthly | Once every 2–4 years |
| Market | **Red ocean.** Every bank, Groww, ET Money, BankBazaar has a free one; Google answers it in the search result itself | **Thin and, right now, mostly stale** |
| Willingness to pay | ~Zero | Low for employees, real for HR |

Bundling two unrelated calculators produces a worse product than either alone. **It only becomes a product if one job connects them**, and there is one that genuinely does:

> **"I'm leaving my job. Can I afford the gap?"**
> My F&F payout is ₹X. My EMIs are ₹Y/month. So I have **Z months of runway** — and here's which loan to pay down with the payout.

That is a real, high-anxiety, poorly-served moment, and it is the only version of this app where 1 + 1 > 2. **The "Runway" screen is the product. The two calculators are its inputs.**

If you don't want that thesis, my honest advice is to build the F&F side only and drop the EMI side — a standalone EMI calculator in 2026 has no room to differentiate.

### The timing opening (the most important research finding)

**India's four Labour Codes came into force on 21 November 2025**, and they changed the *definition of "wages"* — which is the base for gratuity and leave encashment. The Ministry of Labour's own FAQ (16 Mar 2026) confirms gratuity must now be computed on the revised wage definition **with effect from 21.11.2025**.

Consequence: **a large share of the F&F calculators and HR spreadsheets in circulation are now computing gratuity on the wrong base.** That is a genuine, time-bound accuracy wedge — and the reason the F&F side is worth building while the EMI side isn't.

---

## 2. What the research established

### 2.1 Statutory basis — F&F (verified against primary/official sources)

| Item | Rule | Source |
|---|---|---|
| Labour Codes in force | **21 Nov 2025** (Code on Wages 2019, IR, Social Security, OSH). Central/state rules still rolling out | MoLE press release; DLA Piper; KPMG |
| **"Wages" redefined** | Basic + DA + retaining allowance must be **≥50% of total remuneration**. If excluded allowances exceed 50%, **the excess is added back into wages** | Code on Wages s.2(y) |
| Wages definition effective | **21.11.2025** | MoLE FAQ (16.03.2026), Q7 |
| **Gratuity on new wage base** | **Yes, w.e.f. 21.11.2025**, based on **last-drawn** wages | MoLE FAQ Q6, Q11, Q17 |
| Gratuity formula | **15 days' wages × completed years**, divisor 26 | SS Code 2020 s.53 |
| Gratuity eligibility | 5 years' continuous service (>6 months in final year counts as a full year). *"4y 240d" is a contested reading — treat as a user-toggleable assumption, not a default* | PGA 1972 s.4 |
| **Fixed-term employees** | Eligible for gratuity after **1 year** — a real change | MoLE FAQ Q19 |
| Gratuity payment deadline | **30 days**; interest payable on delay | PGA s.7(3), s.7(3A) |
| **Final wages deadline** | **2 working days** from resignation/removal — much tighter than the old 7–10 day norm | Code on Wages s.17(2) |
| Leave encashment | Carry forward ≤30 days/yr; **no statutory cap on days encashable at separation**. Statutory entitlement covers "workers" + supervisors on ≤₹18,000/mo — above that it is **contractual, per company policy** | OSH Code 2020; MoLE FAQ Q20–22, Q26 |

### 2.2 Tax layer (verified)

| Item | Value |
|---|---|
| Gratuity exemption — s.10(10) | Least of (actual, statutory formula, **₹20,00,000**) |
| Leave encashment exemption — s.10(10AA)(ii) | **Least of four**: actual / **₹25,00,000** / 10 months' average salary / cash equivalent of **30 days per completed year**. ₹25L is a **lifetime, cross-employer** cap (w.e.f. 01.04.2023) |
| New regime slabs FY 2026-27 | 0–4L nil · 4–8L 5% · 8–12L 10% · 12–16L 15% · 16–20L 20% · 20–24L 25% · >24L 30% |
| Standard deduction | ₹75,000 |
| s.87A rebate | Up to ₹60,000 → **zero tax at ₹12L taxable** (₹12.75L gross for salaried) |
| Also needed | 4% cess; **marginal relief** just above ₹12L; surcharge at high income |
| Notice pay | Recovery is a **deduction from gross**, not a tax-deductible expense — a very common user error |

### 2.3 The single most important design finding

> **The variance is in company policy, not in the statute.**

The statute fixes gratuity at 15/26. But companies differ on almost everything else:
- Per-day divisor: **26 vs 30 vs actual days in month** (changes every figure)
- Leave encashment base: **basic / basic+DA / gross**
- Notice recovery base: **basic vs gross**, and whether it's on 30-day or calendar-day basis
- Clawbacks: joining bonus, relocation, retention bonus, training bond

**So the app must expose these as visible, editable assumptions with sensible defaults — never hardcode them.** Any calculator that hardcodes one company's policy is wrong for everyone else. This is the core differentiator and the main reason existing tools generate disputes.

### 2.4 EMI engine — validated, not assumed

I implemented and numerically tested the engine before proposing it:

| Check | Result |
|---|---|
| ₹80L @ 8% / 240m | **₹66,915 — exact match** to published reference figures |
| ₹50L @ 8.5% / 240m | ₹43,391 — matches standard benchmark |
| 0% interest loan | Handled (no divide-by-zero) |
| Reduce-tenure vs reduce-EMI, same prepayments | Tenure: 189 months, ₹19.8L interest saved · EMI: 240 months, ₹8.9L saved → **invariant holds: reduce-tenure always saves more** |
| Schedule closes at exactly zero | Yes |

**Bug found during validation (and worth planning for):** naive rounding produced a **241st instalment of ₹121.76** on a 240-month loan. Real lenders cap at N instalments and let the **final instalment absorb the residue**. After the fix: exactly 240 instalments, closing balance zero, final instalment ₹67,035.95.

This is the #1 reason users say *"your calculator doesn't match my bank statement."* It must be an explicit, tested convention — along with:
- **`BigDecimal`, never `Double`.** Floating-point money is a correctness bug, not a style preference.
- Floating-rate resets (repo-linked loans reprice; EMI or tenure changes)
- Pre-EMI interest during home-loan disbursement
- Moratorium with interest capitalisation
- Prepayment lock-ins and charges

---

## 3. Recommended architecture

### 3.1 Backend: **none in v1**

Every calculation here is deterministic arithmetic on user-entered data. A backend in v1 would add cost, DPDP Act 2023 obligations, and breach liability around *salary + loan data* — some of the most sensitive PII a person has — while adding **zero** user value.

Offline-only is simultaneously the **cheaper**, **faster**, and **better** option, and it's a marketing asset: *"your salary never leaves your phone."*

**One exception — and it matters:** ship tax slabs, exemption caps and statutory constants as a **versioned JSON file on a CDN**, not compiled into the app. Otherwise every Union Budget (1 Feb) silently makes the app wrong and forces an app-store release. This is a static file, not a server — very cheap, very high leverage.

Defer a real backend to v2+, and only for: cross-device sync, shared/exportable F&F statements, or an HR-facing multi-employee product.

### 3.2 Frontend: Kotlin + Jetpack Compose (native)

Native is the default-correct choice here: Android-only brief, offline-first, no JS bridge, smallest APK. **Revisit only if iOS is on the roadmap** — in which case Compose Multiplatform or Flutter becomes the conversation, and the module split below makes that switch cheap.

### 3.3 Module layout

```
:core-calc     ← PURE Kotlin/JVM. No Android deps. BigDecimal. The crown jewel.
   core/       Money helpers (BigDecimal, scales, rounding)
   core/loan/  EMI, amortisation, prepayment, rate resets, portfolio
   core/fnf/   wage base, gratuity, leave encashment, notice, tax
   core/calc/  scratch calculator (expression parser)
   core/runway/payout vs obligations
   core/session/history, notes, resume — models + store interfaces
:core-data     ← Room (SQLite) implementations of the store interfaces
:feature-loans ← loan list, add/edit, amortisation, prepayment compare
:feature-fnf   ← F&F wizard, assumptions, statement, PDF export
:feature-calc  ← scratch calculator with its history tape
:feature-runway← THE unifying screen: payout vs obligations vs months of runway
:app           ← single activity, Compose navigation, Material 3
```

`:core-calc` being pure Kotlin is deliberate: it's testable in CI without an emulator, portable to iOS/web later, and **buildable in this cloud container today** (see §5).

### 3.4 Scratch calculator, memory and resume

Three capabilities added after the initial review, and worth saying why they are not
feature creep:

**Scratch calculator.** A generic arithmetic calculator is a commodity — the phone
already has one. Its value *here* is that it sits next to the forms: you work out
"12.5% of basic" or "what do these four allowances add up to", and the answer flows
straight into the loan or settlement field instead of being retyped from another app.
It uses the same `BigDecimal` arithmetic as everything else, so `0.1 + 0.2` is exactly
`0.3`. It accepts what people actually type — `80,00,000` with Indian grouping, and the
`×`/`÷` glyphs from phone keypads.

**Memory, with delete.** Every interaction — calculation, loan, settlement, runway — is
recorded with its *inputs*, not just its answer, so an entry can be reopened and edited
rather than only read. Deletion is **reversible** (soft delete, then a retention sweep):
a settlement takes twenty minutes to fill in, and losing one to a stray tap is not
forgiven. Pinning both floats an entry to the top and protects it from delete and
clear-all. Hard purge exists for when the user really means it.

**Notes and resume.** Any entry takes a free-text note, which is what turns a list of
bare numbers into something meaningful a month later ("this is the figure HR quoted on
the call"). Separately, each flow keeps its own in-progress draft, so the app reopens
exactly where it was left — including which field had focus. This matters most for the
settlement wizard: it is long, people fill it in over several sittings, and an app that
forgets halfway is an app they stop using.

Both are declared as store *interfaces* in the pure-Kotlin core, so the rules are
testable without a device; Room and DataStore back them in the app module.

### 3.5 Why this ordering

Per the decision framework — business growth → UX → platform scalability → ops → engineering simplicity:

- **Growth:** the Runway screen is the only defensible wedge; the labour-code change is a time-bound accuracy claim
- **UX:** offline, instant, no login, no ads — the bar competitors fail
- **Platform scalability:** pure-Kotlin core keeps iOS/web open at near-zero cost
- **Ops:** no servers, no on-call, no data-breach surface; CDN config absorbs annual tax changes
- **Eng simplicity:** one language, one platform, no network layer in v1

---

## 4. Phasing

### MVP (~3–4 weeks solo) — prove the wedge
- Multi-loan entry + amortisation (BigDecimal, capped-schedule convention)
- Portfolio view: total EMI, total interest, debt-free date
- F&F: unpaid salary, leave encashment, gratuity, notice recovery, **new ≥50% wage base**, tax exemptions, TDS estimate
- **Runway screen** (payout ÷ monthly obligations)
- **Scratch calculator** whose results feed the forms
- **Memory**: every interaction saved with its inputs, reversible delete, pin, clear-all
- **Notes** on any entry, and **resume where you left off** per flow
- All assumptions visible and editable; formula shown next to every number
- Local persistence; disclaimers

**Deliberately excluded from MVP:** login, cloud sync, ads, PDF export, multi-currency, investment advice, credit-score features, iOS.

### Production-ready (+2–3 weeks)
PDF/share export · CDN-driven tax config + version banner · prepayment strategy compare (avalanche vs snowball) · floating-rate resets · accessibility, large-font, dark mode · crash reporting · Play Store data-safety declaration · golden-value regression test suite

### Long-term (only if MVP validates)
Cross-device sync · HR/B2B multi-employee F&F with audit trail · iOS via shared core · bank-statement import · regional languages

---

## 5. Build environment — a real blocker to decide on

This cloud container **cannot build an Android app.** Verified, not assumed:

| Host | Status |
|---|---|
| `dl.google.com` | **BLOCKED** (403, org egress policy) — this is the Android SDK *and* the AndroidX/Compose/AGP Maven repo |
| `maven.google.com` | Resolves, but only **301-redirects to `dl.google.com`** → also unusable |
| `repo1.maven.org` (Maven Central) | ✅ Reachable |
| `services.gradle.org`, `plugins.gradle.org` | ✅ Reachable |

Java 21 and Gradle 8.14.3 are installed.

**What is built and green today** — `:core-calc`, 73 passing tests:

| Area | Covered |
|---|---|
| EMI | Golden values, zero-interest, single instalment, input rejection |
| Amortisation | Closes at exactly N instalments with zero balance across a 400-case sweep of principals, rates and tenures; principal reconciles to the amount borrowed |
| Prepayment | Reduce-tenure beats reduce-EMI; earlier beats later; oversized prepayment cannot overpay |
| Rate resets | Rate rise raises interest; holding EMI preserves tenure |
| Wage base | 50% floor bites on allowance-heavy pay, dormant otherwise |
| Gratuity | Golden value, ₹20L cap, non-vesting, the six-month rounding rule, fixed-term at 1 year |
| Leave encashment | Each of the four s.10(10AA) limits binding in turn, lifetime cap net of prior claims |
| Notice recovery | Shortfall, full service, over-service, and divisor sensitivity |
| Settlement | Gross/deductions/tax/net reconcile; negative net when the employee owes money |
| Income tax | Rebate ceiling, marginal relief, no-cliff sweep, monotonicity to ₹3cr, surcharge |
| Calculator | Precedence, percent, unary minus, nesting, grouping separators, exact decimals, errors as values |
| History | Ordering, filtering, notes add/replace/clear, reversible delete, pin protection, retention sweep |
| Resume | Per-flow drafts, overwrite, scoped clear, editing an existing entry |

Two findings worth recording from building it:

- **`80,00,000` originally failed to parse.** Commas were skipped as whitespace, which
  split it into three separate numbers. Grouping separators now have to sit *inside* a
  number run. Caught by a test written from how people actually type.
- **Marginal relief is granted on tax before cess.** So the all-in liability just above
  ₹12L does rise slightly faster than the extra income — by the 4% cess on the relieved
  amount. The no-cliff guarantee holds on the pre-cess figure, and the test asserts it
  there. Worth showing in the UI, because it looks like a bug otherwise.

**What this means practically:**
- ✅ I can build, run and fully unit-test **`:core-calc`** here right now — which is where all the correctness risk lives
- ❌ The Compose UI and the APK must be built either on your machine in Android Studio, or here after `dl.google.com` is allowed

To unblock: open the cloud environment menu in the session title bar → **Edit** → **Network access** → either a broader level, or **Custom** with `dl.google.com` added to Allowed domains (keep the default package-manager list). Steps: https://code.claude.com/docs/en/cloud-environments#network-access

**My suggested split regardless:** I build and test `:core-calc` here (highest value, fully verifiable), you run the UI in Android Studio locally. The engine is the hard part; the Compose screens are straightforward once the numbers are trustworthy.

---

## 6. Risks

| Risk | Severity | Mitigation |
|---|---|---|
| **Wrong F&F number causes real financial harm / liability** | **High** | Label everything an estimate, not legal or tax advice. Show the formula and the statute next to every figure. Make every assumption user-editable. Golden-value test suite. |
| Company policy variance breaks "correct" output | High | Treat policy as **input**, never constant (§2.3) |
| Tax/statute drift (Budget every Feb; labour code rules still rolling out) | High | CDN config + visible "rules as of" version badge |
| **Two-calculator incoherence / feature creep** | High | Runway screen is the thesis; cut anything that doesn't serve it |
| EMI side has no moat | Medium | Accept it. It's an input to Runway, not the pitch |
| Float rounding mismatches bank statements | Medium | BigDecimal + capped-schedule convention — **already validated** |
| Over-reliance on scraped secondary sources | Medium | Primary sources used for every statutory claim; MoLE FAQ cited directly |
| Container can't build Android | **Confirmed, not a risk** | §5 |

---

## 7. Definition of Done (MVP)

1. `:core-calc` has unit tests covering: EMI incl. 0% and 1-month edge cases; schedule closes at exactly N instalments with zero balance; prepayment invariant (reduce-tenure ≥ reduce-EMI savings); gratuity incl. ₹20L cap and >6-month year rounding; leave encashment least-of-four with each of the four branches binding; ≥50% wage-base add-back; slab tax incl. ₹12L rebate cliff and marginal relief
2. Every money value is `BigDecimal`; no `Double`/`Float` anywhere in `:core-calc` (enforced by a lint/test check)
3. Golden-value tests pinned to the worked examples in §2.4 and §2.2
4. App installs and runs offline on a clean device; no network permission needed for core flows
5. Every output screen shows its formula, its assumptions, and a "rules as of <date>" badge
6. No crash on empty/zero/absurd input (₹0 loan, 0-month tenure, 40-year service)

---

## 8. Next steps

All four opening questions are now settled (see the table at the top). Remaining work,
in order:

1. **Unblock `dl.google.com`** in the environment's network settings so the Android
   modules can resolve AGP and Compose. Until then the UI cannot be compiled here.
2. **`:core-data`** — Room implementations of `HistoryStore` and `ResumeStore`, plus
   loan and settlement entities.
3. **`:app` + feature modules** — Compose screens over the tested core.
4. **Remote statutory config** — move `StatutoryConfig.DEFAULT` to a versioned JSON
   file with a "rules as of" badge in the UI.
5. **PDF/share export** for the settlement statement.

### Still open, lower stakes

- **Does the HR layer ever get built?** The core is kept reusable for it, but nothing
  else is being designed for it yet. Revisit once the consumer MVP has been used in anger.
- **Scratch calculator scope.** It is currently `+ − × ÷`, parentheses and postfix `%`.
  Memory registers (M+/MR) and a running tape with per-line notes are deliberately not
  in yet — say the word if you want them.

---

*Statutory and tax positions above are compiled from official sources (MoLE FAQs, PIB, Income Tax Department) as of October 2026 and are intended for product scoping — not as legal or tax advice. Central and state rules under the Labour Codes were still being notified at the time of writing, so figures should be re-verified before any production release.*
