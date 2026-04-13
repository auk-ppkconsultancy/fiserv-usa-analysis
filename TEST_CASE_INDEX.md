# Test Case Index & Certification Roadmap

**Project:** RSO024 — Softpay by Softpay ApS  
**Source:** `RSO024_Testscript_2026-04-13.xlsx` + `TestTransactions_RSO024.csv` (2026-04-13 re-issue)  
**Date:** April 2026

---

## Table of Contents

1. [Summary](#1-summary)
2. [Coverage Matrix](#2-coverage-matrix)
3. [Test Cases by Card Brand](#3-test-cases-by-card-brand)
4. [Test Cases by Transaction Type](#4-test-cases-by-transaction-type)
5. [Test Cases by Entry Mode](#5-test-cases-by-entry-mode)
6. [Test Cases by Industry](#6-test-cases-by-industry)
7. [Test Cases by Feature/Scenario](#7-test-cases-by-featurescenario)
8. [Smoke Test Subset (First 30-50 Tests)](#8-smoke-test-subset)
9. [Certification Stage Checklist](#9-certification-stage-checklist)
10. [Non-Mandatory Tests](#10-non-mandatory-tests)
11. [TOR Testing Requirements](#11-tor-testing-requirements)
12. [MID Assignments by Industry](#12-mid-assignments-by-industry)

---

## 1. Summary

| Metric | Count |
|---|---|
| **Total Mandatory Test Cases** | 424 |
| **Total Non-Mandatory (Unit) Test Cases** | 83 |
| **Grand Total** | 507 |
| **Mandatory Pass Requirement** | 100% (all 424 must pass) |
| **Non-Mandatory** | Recommended but not required |
| **Test Certification TID** | `00000001` |
| **Development TIDs** | `002`, `003` |

### Test Case ID Format

Test case IDs are 12-digit numbers (e.g., `200020170040`). IDs ending in `0010` are typically the primary transaction; IDs ending in `0011` are the corresponding void/reversal.

---

## 2. Coverage Matrix

### 2.1 Mandatory Tests: Card Brand x Transaction Type

| Card Brand | Authorization | Completion | Refund | Total |
|---|---|---|---|---|
| **Visa** | 34 | 12 | 35 | 81 |
| **MasterCard** | 42 | 16 | 43 | 101 |
| **Amex** | 44 | 15 | 37 | 96 |
| **Discover** | 42 | 14 | 37 | 93 |
| **Diners** | 21 | 9 | 12 | 42 |
| **Debit (no card type)** | — | — | 10 | 10 |
| **Admin** | 1 (EncryptionKeyRequest) | — | — | 1 |
| **Total** | **183** | **66** | **174** | **424** |

### 2.2 Mandatory Tests: Card Brand x Entry Mode

| Card Brand | Swiped | Contactless | Total |
|---|---|---|---|
| **Visa** | 37 | 44 | 81 |
| **MasterCard** | 41 | 60 | 101 |
| **Amex** | 31 | 65 | 96 |
| **Discover** | 29 | 64 | 93 |
| **Diners** | 13 | 29 | 42 |
| **Debit** | 2 | 8 | 10 |
| **Other** | — | 1 | 1 |
| **Total** | **173** | **250** | **424** |

> **Note:** ~59% of mandatory tests are contactless — reflecting Softpay's primary entry mode. But 41% are swiped, which is relevant for fallback scenarios and certain certification requirements.

### 2.3 Mandatory Tests: Industry Distribution

Verified directly from `TestTransactions_RSO024.csv` — the test script references a different set of MIDs from the Project Profile.

| Industry | MCC | Count | MID used in test XMLs |
|---|---|---|---|
| **Retail** | 5399 | 213 | RCTST1000120415 |
| **Supermarket** | 5411 | 187 | RCTST1000120416 |
| **Restaurant** | 5812 | 23 | RCTST1000120414 |
| **EncryptionKeyRequest only** | — | 1 | RCTST0000000065 |
| **Total** | — | **424** | — |

> **Workshop ask:** The Project Profile lists MIDs `RCTST1000119068/69/70`, but the test script uses `RCTST1000120414/15/16`. Confirm which set is authoritative before cert runs.

---

## 3. Test Cases by Card Brand

### 3.1 Visa (81 mandatory tests)

| Sub-Category | Count | Entry Modes |
|---|---|---|
| Swiped Auth + Completion | 20 | Swiped |
| Swiped Refund (incl. DCC, Void) | 17 | Swiped |
| Contactless Auth + Completion (incl. ApplePay, SoftPOS) | 26 | Contactless |
| Contactless Refund (incl. ApplePay, SoftPOS) | 12 | Contactless |
| Decline scenarios | 2 | Contactless, Swiped |

### 3.2 MasterCard (101 mandatory tests)

| Sub-Category | Count | Notes |
|---|---|---|
| Swiped Auth + Completion | 18 | — |
| Swiped Refund (incl. DCC, Void, RefundType) | 23 | **RefundType mandatory** per 2026 UMF |
| Contactless Auth + Completion (incl. ApplePay, SoftPOS) | 40 | — |
| Contactless Refund (incl. ApplePay, SoftPOS) | 20 | **RefundType mandatory** per 2026 UMF |

> **Important:** MasterCard has the highest test count. The mandatory `RefundType` field (values: `R`=Full Refund, `Online`=Online Refund) adds complexity.

### 3.3 Amex (96 mandatory tests)

| Sub-Category | Count | Notes |
|---|---|---|
| Swiped Auth + Completion | 19 | — |
| Swiped Refund (incl. Void, RefundType) | 12 | — |
| Contactless Auth + Completion (incl. ApplePay, SoftPOS) | 40 | — |
| Contactless Refund (incl. ApplePay, SoftPOS) | 20 | — |
| Decline scenarios | 1 | Authorization decline |

### 3.4 Discover (93 mandatory tests)

| Sub-Category | Count | Notes |
|---|---|---|
| Swiped Auth + Completion | 16 | — |
| Swiped Refund (incl. Void, RefundType) | 13 | — |
| Contactless Auth + Completion (incl. ApplePay, SoftPOS) | 40 | — |
| Contactless Refund (incl. ApplePay, SoftPOS) | 20 | — |
| Decline scenarios | 1 | Refund decline |

### 3.5 Diners Club (42 mandatory tests)

| Sub-Category | Count | Notes |
|---|---|---|
| Swiped Auth + Completion | 14 | — |
| Contactless Auth + Completion (SoftPOS) | 16 | — |
| Contactless Refund (SoftPOS) | 12 | — |

### 3.6 Debit (10 mandatory tests)

| Sub-Category | Count | Notes |
|---|---|---|
| Contactless Refund (SoftPOS) | 8 | Terminal Category Code 14 |
| Swiped Refund (PIN + PINless) | 2 | PIN 1234 and PINless POS Debit |

### 3.7 Admin (1 mandatory test)

| Test Case | Type | Notes |
|---|---|---|
| `200136620010` | EncryptionKeyRequest | Master Session key exchange; do NOT send KeyOffset field |

---

## 4. Test Cases by Transaction Type

### 4.1 Authorization (183 tests)

Authorization is the first message in the dual-message flow. Includes:
- Standard purchase authorizations
- Void of authorization (paired tests ending in `0011`)
- ApplePay NFC DPAN authorizations
- SoftPOS (TermCatCode 14) authorizations
- Decline authorizations (5 tests with `response_code=500`)

### 4.2 Completion (66 tests)

Completion is the second message that captures an authorized transaction. Key patterns:
- Always paired with a preceding Authorization
- Must echo `OrigAuthGrp` fields from the Authorization response
- Restaurant industry: may include tip in `AddtlAmtGrp`

### 4.3 Refund (174 tests)

Refund is the largest category. Includes:
- Standard credit refunds
- Online Refund (26 tests — `RefundType=Online`)
- PIN Debit Refund (6 tests — requires PIN 1234)
- PINless POS Debit Refund (2 tests)
- DCC Refunds (10 tests — Dynamic Currency Conversion)
- Void of Refund (paired tests ending in `0011`)

### 4.4 EncryptionKeyRequest (1 test)

| Test Case ID | Description |
|---|---|
| `200136620010` | Request encryption key; do NOT send KeyOffset field |

---

## 5. Test Cases by Entry Mode

### 5.1 Contactless (250 tests — 59%)

All contactless tests use the NFC tap entry mode. Sub-categories:

| Category | Count | Terminal Category Code |
|---|---|---|
| Standard contactless | ~116 | `09` (mPOS) |
| SoftPOS contactless | ~176 | `14` (SoftPOS) |
| ApplePay NFC DPAN | ~134 | `09` or `14` |
| Debit contactless | 8 | `14` (SoftPOS) |

> Many tests overlap across categories (e.g., a test can be both SoftPOS and ApplePay).

### 5.2 Swiped (173 tests — 41%)

Swiped tests cover MSR (Magnetic Stripe Read) scenarios. Even though Softpay is primarily contactless, swiped tests are required for:
- Certification completeness
- DCC scenarios (mostly swiped in the test script)
- PIN Debit (swiped with PIN)
- RefundType validation

### 5.3 EMV Chip Read — No longer in scope

EMV contact entry was removed from the RSO024 Project Profile on 2026-04-13. The current test script contains no mandatory contact-chip test cases. SoftPOS has no physical chip reader, so this removal is correct and aligned.

---

## 6. Test Cases by Industry

### 6.1 Restaurant (MID: RCTST1000120414, MCC 5812) — 23 tests

| Card Brand | Auth | Completion | Refund | Total |
|---|---|---|---|---|
| Visa | 4 | 2 | 4 | 10 |
| MasterCard | 1 | 1 | 3 | 5 |
| Diners | 1 | 1 | — | 2 |
| Discover | 1 | — | 1 | 2 |
| Amex | 2 | 1 | 1 | 4 |

**Restaurant-specific features:** Tipping (tip in Completion via `AddtlAmtGrp`), split checks.

### 6.2 Retail (MID: RCTST1000120415, MCC 5399) — 213 tests

The second-largest industry segment. Covers all card brands with extensive contactless and SoftPOS testing.

### 6.3 Supermarket (MID: RCTST1000120416, MCC 5411) — 187 tests

The largest industry segment, covering all card brands with extensive contactless and SoftPOS testing.

### 6.4 EncryptionKeyRequest (MID: RCTST0000000065) — 1 test

A single `AdminRequest` test case exercises the `EncryptionKeyRequest` path used for Master Session Encryption.

---

## 7. Test Cases by Feature/Scenario

### 7.1 SoftPOS Tests (176 tests)

Tests that specifically require `TermCatCode=14` (SoftPOS Terminal Category). These are the highest priority for Softpay.

| Card Brand | Auth | Refund | Void (paired) | Total |
|---|---|---|---|---|
| MasterCard | 24 | 24 | ~24 | 48 |
| Amex | 24 | 24 | ~24 | 48 |
| Discover | 24 | 24 | ~24 | 48 |
| Diners | 12 | 12 | ~12 | 24 |
| Debit | — | 8 | ~4 | 8 |

> **Notably absent:** Visa SoftPOS tests — Visa tests use `TermCatCode=09` (mPOS) instead of `14`.

### 7.2 ApplePay NFC DPAN Tests (134 tests)

Tests specifically for Apple Pay contactless transactions using Device Primary Account Number (DPAN).

### 7.3 DCC Tests (10 tests)

| Test Case ID | Card Brand | Description |
|---|---|---|
| `200020170040` | Visa | Day One DCC Refund |
| `200021060030` | MasterCard | Day One DCC Refund |
| `201159760010` | Visa | DCC Refund (variant 1) |
| `201159770010` | Visa | DCC Refund (variant 2) |
| `201065220010` | MasterCard | Day Two DCC Refund |
| `200010910010` | Visa | Day One DCC Refund |
| `200011500010` | MasterCard | Day One DCC Refund |
| `201045730010` | Visa | Day Two DCC Refund |
| `201066040010` | MasterCard | Day Two DCC Refund |
| `201066060010` | MasterCard | Day Two DCC Refund |

> DCC tests are all refunds and swiped entry mode. "Day One" vs. "Day Two" may refer to settlement timing.

### 7.4 Void Tests (132 tests)

Void tests always come in pairs with the primary transaction:
- Primary test: `XXXXXXXXXXXX0010` (the original transaction)
- Void test: `XXXXXXXXXXXX0011` (the void of that transaction)

All voids use `ReversalInd=Void` in a `ReversalRequest`.

### 7.5 Decline Tests (5 tests)

| Test Case ID | Card Brand | Txn Type | Expected Response |
|---|---|---|---|
| `200113620010` | Visa | Authorization | 500 (Decline) |
| `200113070010` | Visa | Authorization | 500 (Decline) |
| `200229040010` | Visa | Refund | 500 (Decline) |
| `200229160010` | Discover | Refund | 500 (Decline) |
| `201009500010` | Amex | Authorization | 500 (Decline) |

### 7.6 RefundType Tests (26 tests)

Tests requiring the `RefundType` XML tag, mandated for MasterCard/Maestro per 2026 UMF changes. Values: `R` (Full Refund), `Online` (Online Refund).

### 7.7 PIN Debit Tests (6 tests)

| Test Case ID | Description | Entry Mode |
|---|---|---|
| `200081710010` | Refund with PIN 1234 | Swiped |
| `200081710011` | Void of PIN Debit Refund | Swiped |

Plus 4 debit contactless refund tests in the SoftPOS category.

### 7.8 PINless POS Debit Tests (2 tests)

| Test Case ID | Description |
|---|---|
| `200082290010` | PINless POS Debit Refund (MasterCard, Swiped) |
| `200082290011` | Void of PINless POS Debit Refund |

### 7.9 Encryption Key Request (1 test)

| Test Case ID | Description |
|---|---|
| `200136620010` | `EncryptionKeyRequest` — do NOT send `KeyOffset` field |

---

## 8. Smoke Test Subset

For initial connectivity validation, prioritize these ~40 tests in order:

### Phase 1: Basic Connectivity (5 tests)

| Priority | Test | What It Validates |
|---|---|---|
| 1 | Encryption Key Request (`200136620010`) | Master Session key exchange works |
| 2 | Visa Swiped Auth (any `200070230010`-like) | Basic authorization flow |
| 3 | Visa Swiped Completion (paired) | Dual-message capture works |
| 4 | Visa Swiped Refund (any) | Refund flow works |
| 5 | Visa Swiped Auth + Void (paired `0010`/`0011`) | Void/Reversal flow works |

### Phase 2: All Card Brands Swiped (10 tests)

| Priority | Test | What It Validates |
|---|---|---|
| 6-7 | MasterCard Auth + Completion | MC flow works |
| 8-9 | Amex Auth + Refund | Amex flow works |
| 10-11 | Discover Auth + Refund | Discover flow works |
| 12-13 | Diners Auth + Completion | Diners flow works |
| 14-15 | MC Refund with RefundType | MasterCard RefundType mandatory field |

### Phase 3: Contactless + ApplePay (10 tests)

| Priority | Test | What It Validates |
|---|---|---|
| 16-17 | Visa Contactless Auth + Void | Contactless entry mode |
| 18-19 | MC ApplePay Auth + Completion | Digital wallet NFC DPAN |
| 20-21 | Amex ApplePay Refund + Void | Wallet refund flow |
| 22-23 | Discover Contactless Auth | All brands contactless |
| 24-25 | Diners Contactless Auth | Diners contactless |

### Phase 4: SoftPOS Terminal Category (10 tests)

| Priority | Test | What It Validates |
|---|---|---|
| 26-27 | MC SoftPOS Auth + Void | TermCatCode 14 accepted |
| 28-29 | Amex SoftPOS Auth + Void | SoftPOS for all brands |
| 30-31 | Discover SoftPOS Auth + Void | SoftPOS routing |
| 32-33 | Diners SoftPOS Auth | Diners SoftPOS |
| 34-35 | MC ApplePay SoftPOS Auth + Void | Combined wallet + SoftPOS |

### Phase 5: Special Features (5 tests)

| Priority | Test | What It Validates |
|---|---|---|
| 36-37 | DCC Refund (Visa + MC) | DCC flow works |
| 38-39 | Debit Refund (SoftPOS) | Debit routing |
| 40 | Decline test (Visa Auth resp=500) | Decline handling |

---

## 9. Certification Stage Checklist

### Stage 1: Development (Sandbox)

- [ ] Establish Datawire connectivity
- [ ] Successfully send/receive first transaction
- [ ] Validate all 5 transaction types: Authorization, Completion, Refund, Void, EncryptionKeyRequest
- [ ] Validate all 5 card brands: Visa, MasterCard, Amex, Discover, Diners
- [ ] Validate both entry modes: Swiped and Contactless
- [ ] Validate all 3 industries (Restaurant, Retail/QSR, Supermarket)
- [ ] Validate SoftPOS `TermCatCode=14` acceptance
- [ ] Validate ApplePay NFC DPAN flow
- [ ] Validate DCC flow (Visa + MasterCard)
- [ ] Validate PIN Debit (swiped with PIN)
- [ ] Validate PINless POS Debit
- [ ] Complete TOR (Timeout Reversal) testing — **mandatory before Stage 2**
- [ ] Run all smoke tests (Phase 1-5 above)
- [ ] Fix any errors and re-run

### Stage 2: Certification (TID 00000001)

- [ ] Switch to TID `00000001` for all test execution
- [ ] Run all 424 mandatory test cases
- [ ] Verify 100% pass rate (real-time validation in Sandbox)
- [ ] Track failures and fix — all tests must pass
- [ ] Document any test cases requiring clarification with Fiserv

**Test Execution Strategy:**

| Order | Category | Count | Rationale |
|---|---|---|---|
| 1 | Admin (Encryption Key) | 1 | Pre-requisite for PIN Debit |
| 2 | Visa Swiped | 37 | Baseline validation |
| 3 | Visa Contactless | 44 | Primary entry mode |
| 4 | MasterCard (all) | 101 | Largest set; RefundType validation |
| 5 | Amex (all) | 96 | Second largest |
| 6 | Discover (all) | 93 | Third largest |
| 7 | Diners (all) | 42 | Processed via Discover network |
| 8 | Debit (all) | 10 | PIN/PINless flows |
| 9 | Decline tests | 5 | Error handling |

### Stage 3: Review

- [ ] Submit certification results to Fiserv
- [ ] Fiserv Certification Analyst reviews pass/fail data
- [ ] Provide EMV receipts for validation (mandatory)
- [ ] Address any analyst feedback

### Stage 4: Accepted

- [ ] PCI-DSS/PA-DSS verification
- [ ] Business agreements signed
- [ ] Compliance checks passed

### Stage 5: Complete

- [ ] Certification letter issued
- [ ] Production keys provisioned
- [ ] Production MIDs assigned
- [ ] Ready for merchant boarding

---

## 10. Non-Mandatory Tests

The non-mandatory sheet contains 83 "Unit" tests. While not required for certification, they provide additional coverage:

### Summary

| Payment Type | Count |
|---|---|
| Credit | 77 |
| Debit | 6 |

| Entry Mode | Count |
|---|---|
| Swiped | 29 |
| Contactless | 42 |
| EMV Chip Read | 12 |

| Card Brand | Count |
|---|---|
| Visa | 28 |
| MasterCard | 18 |
| Amex | 17 |
| Discover | 10 |
| Diners | 4 |
| Debit (no type) | 6 |

### Notable Non-Mandatory Tests

- **EMV Chip Read:** Contact-chip entry mode was removed from the RSO024 Project Profile on 2026-04-13. Non-mandatory contact-chip tests (if any) can be skipped.
- **Decline test (resp=107):** One test expects `107` (Call for Authorization) — referral handling.
- **Decline tests (resp=500):** Six decline scenarios across different card brands.

---

## 11. TOR Testing Requirements

Timeout Reversal (TOR) testing is **mandatory before moving to Stage 2 (Certification)**.

### Requirements

| Step | Description |
|---|---|
| 1 | **Internal Unattended Testing:** Develop and validate TOR message format independently |
| 2 | **Attended Testing:** Final TOR test with Fiserv Certification Analyst |
| 3 | **TOR Message:** `ReversalRequest` with `ReversalInd=Timeout` |
| 4 | **Scheme Fields Exempt:** Per 2026 UMF changes, Visa TransID, MC BanknetData, Amex AmExTranID, Discover NRID are EXEMPT for TOR |
| 5 | **OrigAuthGrp:** Use stored values from the original authorization (if available) |

### TOR Scenarios to Test

| Scenario | Description |
|---|---|
| Auth timeout | Send Authorization, receive no response, send TOR |
| Completion timeout | Send Completion, receive no response, send TOR |
| TOR timeout | Send TOR, receive no response — re-send TOR |
| Late response | Receive original response after TOR already sent |

Reference: `Timeout_Reversal_Testing_QRG.pdf` in the SDK

---

## 12. MID Assignments by Industry

**Project Profile MIDs** (as provided by Fiserv for dev / cert use):

| Industry | MID | Test TIDs (Dev) | Cert TID |
|---|---|---|---|
| **Restaurant** | RCTST1000119068 | 002, 003 | 001 |
| **Retail/QSR** | RCTST1000119069 | 002, 003 | 001 |
| **Supermarket** | RCTST1000119070 | 002, 003 | 001 |

**Test-script MIDs** (actually embedded inside every `<MerchID>` element in `TestTransactions_RSO024.csv`):

| Industry | MCC | MID | TID |
|---|---|---|---|
| Restaurant | 5812 | RCTST1000120414 | 00000001 |
| Retail | 5399 | RCTST1000120415 | 00000001 |
| Supermarket | 5411 | RCTST1000120416 | 00000001 |
| EncryptionKeyRequest | — | RCTST0000000065 | 00000001 |

**Important:** There are two different MID sets. Before cert runs, **confirm with Fiserv which MIDs the sandbox's TestCase matcher expects**. Early probing suggests the test-script MIDs (`1000120414/15/16`) are what the sandbox validates against — sending transactions for the Project Profile MIDs with any other parameters returns `109 INVALID TERM`.

---

*This index was generated from `RSO024_Testscript_2026-04-13.xlsx` + `TestTransactions_RSO024.csv` (April 13, 2026 re-issue). All 424 mandatory test cases were parsed and categorized. The smoke test subset and execution strategy are recommendations based on Softpay's SoftPOS use case.*
