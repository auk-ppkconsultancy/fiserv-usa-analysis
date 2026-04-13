# Fiserv USA Rapid Connect Integration Analysis

**Project:** Softpay SoftPOS - Fiserv USA Rapid Connect Integration  
**Project ID (TPP ID):** RSO024  
**Rapid Connect Version:** 15.04 (UMF v15.04.5, effective February 25, 2026)  
**Date of Analysis:** April 7, 2026  
**Prepared by:** Softpay Integration Team  

---

## Table of Contents

- [Step 0 - Feature Deep Dives (Target Spec)](#step-0--feature-deep-dives-target-spec)
- [Step 1 - Protocol & Message Format](#step-1--protocol--message-format)
- [Step 2 - Supported Card Schemes & Scope](#step-2--supported-card-schemes--scope)
- [Step 3 - Supported Transaction Types](#step-3--supported-transaction-types)
- [Step 4 - Integration & Connectivity](#step-4--integration--connectivity)
- [Step 5 - Security & Key Management](#step-5--security--key-management)
- [Step 6 - EMV & Kernel Data](#step-6--emv--kernel-data)
- [Step 7 - Settlement & Reconciliation](#step-7--settlement--reconciliation)
- [Step 8 - Merchant Onboarding](#step-8--merchant-onboarding)
- [Step 9 - Certification & Test Requirements](#step-9--certification--test-requirements)
- [Step 10 - Special Features & Optional Capabilities](#step-10--special-features--optional-capabilities)
- [Step 11 - API-Specific Details](#step-11--api-specific-details)
- [Step 12 - Receipt Requirements & App Branding](#step-12--receipt-requirements--app-branding)
- [Step 13 - Project Management & Governance](#step-13--project-management--governance)
- [Step 14 - Open Questions & Action Items](#step-14--open-questions--action-items)

---

## Step 0 — Feature Deep Dives (Target Spec)

### 0.1 Deferred Authorizations (Store-and-Forward / Offline / Queued Auth)

| Aspect | Detail |
|---|---|
| **Supported?** | **Unknown — needs clarification from Fiserv.** The UMF XSD defines `CancelDeferredAuth` as a TxnType, but this is a **cancellation** mechanism only. No TxnType exists in the XSD for **submitting** a deferred/offline authorization (e.g., no `DeferredAuthorization`, `OfflineAuth`, or `SAFAuthorization`). |
| **What we found** | The XSD (UMF_XML_SCHEMA.xsd, `TxnTypeType` enumeration) lists 40 transaction types. `CancelDeferredAuth` is present, but it requires `OrigAuthGrp` — meaning it references an **already-submitted** deferred auth. The submission mechanism itself is not defined in the available SDK materials. |
| **SAF references** | The XSD contains SAF-related fields (`DSAFTblVer`, `HSAFTblVer`, `TLSAFBlk`, `SubFileType=SAF`) but these appear related to EMV terminal file management, not transaction submission. |
| **Missing pieces** | No TxnType for submitting deferred auth. No offline/deferred indicator flag on standard `Authorization`. No documentation of the submission flow in the SDK. The referenced "Appendix Z" was not found in the available materials. |
| **SoftPOS relevance** | A SoftPOS device loses connectivity more often than a fixed terminal. If deferred auth is not supported, Softpay must queue transactions locally and submit them as standard `Authorization` messages when connectivity returns — but this has different risk and compliance implications. |

**Open Questions (high priority — blocks offline flow design):**
- Does Fiserv Rapid Connect support submitting deferred/offline authorizations? If so, what is the mechanism — a special TxnType, a flag on standard `Authorization`, or something else?
- `CancelDeferredAuth` exists in the XSD — what does it cancel, and how was the original deferred auth submitted?
- Can Softpay implement store-and-forward by queuing standard `Authorization` messages and submitting them when online, or does Fiserv require a specific deferred auth flow?
- Are there time limits for deferred submission?
- Are there card brand restrictions on deferred auth for contactless/SoftPOS?
- Does Fiserv require special boarding flags to enable deferred auth?

### 0.2 Tipping

| Aspect | Detail |
|---|---|
| **Supported?** | **Yes** — "Tip Amount" is selected in the RSO024 Project Profile (reporting field for QSR). Tipping is supported through the standard dual-message flow. |
| **Project Profile Status** | **SELECTED** — "Tip Amount" is checked. Restaurant industry is also selected. |

#### Two supported tipping flows

**Flow A — Tip-before-auth (Softpay preferred):**
The SoftPOS app collects the tip from the cardholder **before** sending the authorization. The `Authorization` is sent for the full amount (service + tip), and the `Completion` captures the same amount. This is the standard Softpay flow and is fully compatible with Fiserv — `TxnAmt` is simply an amount; the protocol does not distinguish between "service" and "tip" portions at the authorization level.

| Step | Message | TxnAmt | Notes |
|---|---|---|---|
| 1. Cardholder taps | — | — | Card read, EMV data captured |
| 2. App shows tip screen | — | — | Customer selects tip |
| 3. Authorization | `TxnType=Authorization` | Service + tip (e.g., $58.00) | Full amount authorized |
| 4. Completion | `TxnType=Completion` | Same $58.00 | Auth and Completion amounts match — no tolerance needed |

- `FirstAuthAmt` = `TotalAuthAmt` = Completion `TxnAmt` (all the same amount)
- No 20% tolerance invoked since Completion = Authorization amount
- `TipAmt` in `AddtlAmtGrp` can optionally be sent for reporting purposes
- Works for **all industries** (Retail, QSR, Restaurant, Supermarket) — not limited to Restaurant MCC

**Flow B — Tip-after-auth (traditional restaurant flow):**
The authorization is sent for the subtotal only. After the cardholder adds a tip (e.g., on a paper receipt), the Completion is sent with the adjusted total. This is the traditional restaurant model.

| Step | Message | TxnAmt | Notes |
|---|---|---|---|
| 1. Cardholder taps | — | — | Card read |
| 2. Authorization | `TxnType=Authorization` | Subtotal only (e.g., $50.00) | Pre-tip amount |
| 3. Customer adds tip | — | — | E.g., $8.00 tip on receipt |
| 4. Completion | `TxnType=Completion` | Subtotal + tip ($58.00) | Exceeds auth — 20% tolerance applies |

- `FirstAuthAmt` = $50.00, `TotalAuthAmt` = $50.00, Completion `TxnAmt` = $58.00
- Card brand rules allow up to **20% tolerance** between auth and completion for Restaurant MCCs
- Tip adjustment must be submitted **before merchant cut-off** for the day
- Primarily for **Restaurant industry** (MCC 5812) where tip is added after card leaves customer

#### Reporting fields

| Field | Type | Notes |
|---|---|---|
| `TipAmt` | `AddlAmtType` in `AddtlAmtGrp` | Optional reporting field — flows to downstream reporting (CLX). QSR industry only per Portal Definitions. |
| `Tip` | `AddlAmtType` in `AddtlAmtGrp` | Limited platform availability; QSR only. |

#### Recommendation

**Use Flow A (tip-before-auth) as the default.** It is simpler, works across all industries, avoids the 20% tolerance dependency, and matches Softpay's existing flow on other acquirers. Flow B should only be used if the merchant workflow requires tip adjustment after the card has left the customer (e.g., table-service restaurants with paper receipts).

**Open Questions:**
- Confirm that Flow A (full amount including tip in Authorization, same amount in Completion) is accepted without issues across all three RSO024 industries (Restaurant, Retail/QSR, Supermarket).
- Is the `TipAmt` reporting field in `AddtlAmtGrp` optional or required when tipping is enabled in the project profile?
- Is there a percentage cap on tip relative to the service amount (beyond the 20% tolerance rule for Flow B)?
- Can tipping be supported for Retail (MCC 5399) and Supermarket (MCC 5411) industries, or only Restaurant (MCC 5812)?

### 0.3 Dynamic Currency Conversion (DCC)

| Aspect                      | Detail                                                                                                                                                                                                                                                                                                                                                                                                                   |
| --------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Supported?**              | **Yes** — DCC is explicitly selected in the RSO024 Project Profile under "International Currency Solutions."                                                                                                                                                                                                                                                                                                             |
| **Mechanism**               | DCC (also called Cardholder Preferred Currency / CPC) allows foreign cardholders to see the transaction amount converted to their home currency at the point of sale (Portal Definitions). Available for **Visa and Mastercard only**.                                                                                                                                                                                   |
| **Message Flow**            | 1. POS initiates a `Verification` or first authorization to determine card eligibility for DCC. 2. If eligible, the POS presents the cardholder with a choice of currencies. 3. If the cardholder selects their home currency, the POS submits the transaction with the DCC Group populated (conversion rate, original cardholder currency, conversion date). 4. The transaction processes in the cardholder's currency. |
| **Required Fields**         | `DCCGrp` in the UMF XML containing: DCC conversion rate, original cardholder currency code, conversion date. `TxnCrncy` set to the DCC currency. Per the EMV Guide (RQ 4200): Tag 5F2A (Transaction Currency Code) must be set to the DCC currency before 1st Generate AC for contact transactions.                                                                                                                      |
| **Response Handling**       | Standard approval/decline. The response reflects the DCC currency and amount.                                                                                                                                                                                                                                                                                                                                            |
| **Reconciliation**          | Settlement occurs in the DCC currency. Merchant funding remains in USD.                                                                                                                                                                                                                                                                                                                                                  |
| **Edge Cases**              | Per EMV Guide: DCC is **not supported below Contactless CVM Limit** (RQ 4201). For contactless transactions, the 1st Generate AC cryptogram is generated with the **original currency**, not the converted currency (BP 1200). DCC eligibility should NOT be determined using Application Currency Code Tag 9F42 (BP 1100). CAID (Common Debit AID) transactions are NOT DCC eligible.                                   |
| **Card Brand Restrictions** | Visa and Mastercard only. Amex, Discover, Diners Club are not eligible for DCC.                                                                                                                                                                                                                                                                                                                                          |
| **AID Selection for DCC**   | Per EMV Guide: If both CAID and Global Credit AID are present on the card, the Global Credit AID must be selected for DCC eligibility. If only CAID is present, the card is not DCC eligible.                                                                                                                                                                                                                            |

**Open Questions:**
- Who provides the DCC rate feed — Fiserv or a third-party DCC provider?
- Is there a specific DCC certification requirement beyond the standard Rapid Connect certification?
- Is the DCC offer presented by the POS app (Softpay) or does Fiserv provide a hosted DCC selection UI?
- What is the refresh frequency for DCC rates?
- Are there specific receipt requirements for DCC transactions (e.g., showing both currencies)?
- Confirm that DCC is not available for Discover, Amex, or Diners Club in the Fiserv US environment.

### 0.4 Surcharge (Merchant Surcharge / Tran Fee)

| Aspect | Detail |
|---|---|
| **Supported?** | **Not selected** in the RSO024 Project Profile. The feature "Tran Fee (Merchant Surcharge)" is available but unchecked. |
| **Definition** | Per Portal Definitions: "Surcharge is an additional fee merchants add to relevant transactions as permitted by Card Organization Rules and applicable laws. Processing fee associated with Credit Networks (Visa, Mastercard, Discover, Amex, Union Pay). Merchants must receive prior approval from Processor before assessing Surcharge. Merchants must sign Processor Surcharging contract or addendum." |
| **Required Fields** | `AdditlAmtGrp.AddlAmtType` = `Surchrg` or `TranFee`, plus the surcharge amount |
| **Card Brand Rules** | Surcharging is generally permitted on credit card transactions only (not debit). Each card brand has specific rules: Visa and Mastercard allow surcharging in the US up to 3% (or the merchant's cost of acceptance, whichever is lower). Rules vary by state — surcharging is prohibited in some US states (Connecticut, Massachusetts, etc.). |
| **Response Handling** | Standard response codes; the surcharge amount is included in the total transaction amount |
| **Reconciliation** | Surcharge amount settles as part of the total transaction amount |
| **Edge Cases** | Debit card transactions cannot be surcharged. Prepaid cards may or may not be surcharging-eligible depending on brand rules. State law restrictions apply. |

**Open Questions:**
- Should surcharging be enabled for the Softpay US integration? If so, the "Tran Fee (Merchant Surcharge)" feature must be added to the Project Profile.
- What are the state-by-state restrictions Softpay needs to enforce?
- Does Fiserv automatically validate surcharge amounts against card brand limits, or must the POS enforce these?
- Is prior Fiserv approval and a surcharging contract required before enabling this feature?
- How does the surcharge interact with DCC — can both be applied to the same transaction?

### Feature Summary Table

| Feature | Status | Notes |
|---|---|---|
| Deferred Authorizations | **Unknown** — only `CancelDeferredAuth` found in XSD (cancellation, not submission). No TxnType for submitting deferred auth. | **Must clarify with Fiserv** before designing offline flow |
| Tipping | **Selected and supported** | Auth+Completion flow available for Restaurant; "Tip Amount" feature explicitly selected in project profile |
| Dynamic Currency Conversion (DCC) | **Selected and supported** | Visa and Mastercard only; not below contactless CVM limit |
| Surcharge | Available but **not selected** | Requires Fiserv approval and contract; credit-only; state restrictions |

---

## Step 1 — Protocol & Message Format

### 1.1 Protocol Overview

| Attribute | Value |
|---|---|
| **Format** | XML (Unified Message Format — UMF) |
| **Schema** | `gmfV15.04` (namespace: `com/fiserv/Merchant/gmfV15.04`) |
| **XSD File** | `UMF_XML_SCHEMA.xsd` (6,247 lines) provided in SDK |
| **Transport** | XML over HTTPS via Datawire Secure Transport |
| **Max Message Size** | 14,336 bytes per message |
| **Encoding** | UTF-8 |
| **XML Reserved Characters** | Must use entity references: `&gt;`, `&lt;`, `&amp;`, `&#37;`, `&apos;`, `&quot;` |
| **Empty Tags** | **Not supported** — both `<Element></Element>` and `<Element />` are invalid and must not be transmitted |

### 1.2 Message Structure

The root element is `<GMF>` containing a single request or response child element. Each message is a **request/response pair** — the POS sends a request, and Rapid Connect returns a response (or a `RejectResponse` for errors).

**Request/Response Message Types:**

| Request | Response | Payment Type |
|---|---|---|
| `CreditRequest` | `CreditResponse` | Credit card transactions |
| `DebitRequest` | `DebitResponse` | PIN Debit |
| `PinlessDebitRequest` | `PinlessDebitResponse` | PINless Debit |
| `EBTRequest` | `EBTResponse` | EBT |
| `CheckRequest` | `CheckResponse` | Check |
| `PrepaidRequest` | `PrepaidResponse` | Fiserv Prepaid Closed Loop |
| `GenPrepaidRequest` | `GenPrepaidResponse` | Other Prepaid |
| `PrivateLabelRequest` | `PrivateLabelResponse` | Private Label |
| `FleetRequest` | `FleetResponse` | Fleet |
| `TransArmorRequest` | `TransArmorResponse` | TransArmor encrypted |
| `AdminRequest` | `AdminResponse` | Administrative |
| `ReversalRequest` | `ReversalResponse` | Voids/Reversals |
| `BatchRequest` | `BatchResponse` | Batch operations |
| `AltCNPRequest` | `AltCNPResponse` | Alternate CNP (PayPal) |
| `MTRequest` | `MTResponse` | Money Transfer |
| `(any)` | `RejectResponse` | Error/rejection |

### 1.3 Single-Message vs. Dual-Message Flow

Rapid Connect supports **both** single-message and dual-message flows:

- **Single-message (Sale):** `TxnType=Sale` — authorization + capture in one request. The transaction is authorized and immediately captured for settlement. **Not selected for RSO024 — see rationale below.**
- **Dual-message (Authorization + Completion):** `TxnType=Authorization` followed by `TxnType=Completion` — the authorization is sent first, and a separate completion/capture message is sent later. **This is the selected flow for RSO024.**

**Settlement Methodology:** The RSO024 project is configured for **Host Capture** (automated settlement). Host Capture runs on a periodic batch cycle (every X minutes), automatically settling all completed transactions that occurred before the batch run time.

**Why dual-message is required (confirmed in Fiserv meeting):** With Host Capture running periodic batches, a single-message Sale would be captured and batched almost immediately — leaving no window for voids. Softpay requires at least **15 minutes** for void support. The dual-message flow solves this: the Authorization holds funds without capturing, giving Softpay full control over when to send the Completion. A Void can be sent any time before the Completion (or within 25 minutes of the auth). This is why Sale is intentionally excluded from the project profile.

### 1.4 XML Group Structure

Each request/response is composed of **modular XML groups** in a defined sequence (per the XSD). Key groups include:

1. `CommonGrp` — Core transaction data (mandatory for all transactions)
2. `BillPayGrp` — Bill payment details
3. `AltMerchNameAndAddrGrp` — Alternate merchant info / soft descriptors
4. `CardGrp` — Payment card data (PAN, track data, card type)
5. `TermEnvGrp` — Terminal environment details
6. `PINGrp` — PIN encryption data
7. `AdditlAmtGrp` — Additional amounts (cashback, surcharge, tip, etc.) — up to 6 occurrences
8. `EMVGrp` — EMV chip data (tags, cryptograms)
9. `TAGrp` — TransArmor encryption/tokenization
10. `TknGrp` — Network tokenization data
11. `EcommGrp` — E-commerce/digital wallet data
12. `SecrTxnGrp` — Secure transaction authentication
13. `VisaGrp` — Visa-specific fields
14. `MCGrp` — Mastercard-specific fields
15. `DSGrp` — Discover-specific fields
16. `AmexGrp` — Amex-specific fields
17. `UnionPayGrp` — UnionPay-specific fields
18. `RespGrp` — Response data (authorization codes, response codes)
19. `OrigAuthGrp` — Original authorization reference (for subsequent transactions)
20. `DCCGrp` — Dynamic Currency Conversion data

**Important:** Elements within each group must appear in the order defined in the XSD. All elements are technically optional (`minOccurs="0"`) in the schema, but the UMF specification defines mandatory/conditional rules per transaction type.

### 1.5 Field Conventions

| Convention | Meaning |
|---|---|
| **M** | Mandatory |
| **O** | Optional |
| **C** | Conditional |
| **E** | Echoed (returned in response) |
| **-** | Not Present (must not be sent) |

**Field Attribute Types:**

| Type | Description |
|---|---|
| A | Alphabetic (a-z, A-Z) |
| an | Alphanumeric (a-z, A-Z, 0-9) |
| ans | Alphanumeric + special characters and space |
| h | Hexadecimal (0-9, A-F) |
| b64 | Base64 encoded data |
| N | Numeric (0-9) |

**Amount Format:** Amounts are in minor units with no decimal point. For USD (2 decimal places): `12034` = $120.34. For JPY (0 decimal places): `120` = 120 Yen.

### 1.6 Key Header/Envelope Fields (CommonGrp)

| Field | XML Tag | Type | Length | Description |
|---|---|---|---|---|
| Payment Type | `PymtType` | A | max 7 | Credit, Debit, PLDebit, EBT, Check, Preaid, PvtLabl, Fleet, AltCNP |
| Transaction Type | `TxnType` | A | max 20 | Authorization, Sale, Completion, Refund, etc. (35+ types) |
| Local Date/Time | `LocalDateTime` | N | 14 | YYYYMMDDhhmmss (local time) |
| Transmission Date/Time | `TrmnsnDateTime` | N | 14 | YYYYMMDDhhmmss (GMT/UTC) |
| STAN | `STAN` | N | 6 | 000001-999999, unique per day per MID+TID |
| Reference Number | `RefNum` | an | max 22 | Unique per day per MID+TID; must be identical in subsequent transactions |
| Order Number | `OrderNum` | ans | max 15 | Mandatory for MOTO/eCommerce; cannot be all zeroes |
| TPP ID | `TPPID` | an | 6 | Fixed: `RSO024` |
| Terminal ID | `TermID` | an | 8 | Assigned by Fiserv |
| Merchant ID | `MerchID` | an | max 16 | Assigned by Fiserv |
| Merchant Category Code | `MerchCatCode` | N | 4 | MCC code |
| POS Entry Mode | `POSEntryMode` | an | 3 | Entry method (e.g., `901` = contactless) |
| POS Condition Code | `POSCondCode` | N | 2 | Transaction condition |
| Terminal Category Code | `TermCatCode` | N | 2 | Terminal type identifier |
| Transaction Amount | `TxnAmt` | N | max 12 | Amount in minor units |
| Transaction Currency | `TxnCrncy` | N | 3 | ISO 4217 currency code (840 = USD) |
| Group ID | `GroupID` | an | max 13 | Platform routing identifier (20001 for this project) |

---

## Step 2 — Supported Card Schemes & Scope

### 2.1 Card Brands Selected in Project Profile (RSO024)

| Card Brand           | Card-Present | Notes                                                                                |
| -------------------- | ------------ | ------------------------------------------------------------------------------------ |
| **Visa**             | Yes          | Full support including contactless, EMV, DCC                                         |
| **Mastercard**       | Yes          | Full support including contactless, EMV, DCC                                         |
| **American Express** | Yes          | Test Contactless CVM limit: $10.00 (credit) / $200.01 (debit) per EMV Guide Table 24 |
| **Discover**         | Yes          | Test Contactless CVM limit: $50.00 per EMV Guide Table 24                            |
| **Diners Club**      | Yes          | Processed through Discover network                                                   |
Question: Table 24 in the EMV Guide references test CVM limits. Does Fiserv provide general recommendations for Contactless CVM limits in a production environment?
### 2.2 Debit Card Types

| Type | Notes |
|---|---|
| **PIN Debit** | Selected — significant for SoftPOS as online PIN on COTS device has MPoC implications; PIN encryption required |
| **PINless POS Debit** | Selected — debit routing without PIN |

**Important Note:** PIN Debit and PINless POS Debit are both selected. This means debit routing is enabled for cost optimization (Durbin compliance). PIN Debit requires implementation of `PINGrp` with PIN encryption via **Master Session Encryption** (selected in the project profile). Online PIN for contactless on a COTS/SoftPOS device has MPoC implications that must be clarified with Fiserv. PINless POS Debit provides debit routing without PIN entry.

### 2.3 Mobile/Digital Wallet Support

| Wallet | Notes |
|---|---|
| **Apple Pay** | NFC contactless; CDCVM supported |
| **Google Pay** | NFC contactless; CDCVM supported |
| **Samsung Pay** | NFC contactless; CDCVM supported |

### 2.4 Entry Modes Selected

| Entry Mode | Relevant POS Entry Mode Codes |
|---|---|
| **Swiped** | Magnetic stripe (not applicable for SoftPOS — no MSR reader) |
| **Contactless** | NFC tap (POS Entry Mode 07/91 for contactless EMV) |
| **EMV (Contact)** | Selected — but SoftPOS has no chip card reader (see open question) |

**Softpay Assessment:** For a SoftPOS solution, the primary entry mode will be **Contactless** (NFC tap). "Swiped" is selected but not physically applicable to SoftPOS devices (no MSR hardware). "EMV" (contact chip) is selected in the project profile, but SoftPOS devices do not have a chip card reader — **this should be clarified with Fiserv** (whether it is intentional or a configuration error).

---

## Step 3 — Supported Transaction Types

### 3.1 Core Transaction Types (Selected in Project Profile)

| Transaction Type | Description | Softpay Relevance |
|---|---|---|
| **Authorization** | Auth without capture (dual-message) | Core — pre-authorization |
| **Completion** | Capture a prior authorization | Core — settle after auth (e.g., with tip) |
| **Refund** | Credit/return funds to cardholder | Core — returns processing |
| **Online Refund** | Online refund authorization | Variant of Refund |
| **PIN Debit Refund** | Refund for PIN Debit transactions | Required for PIN Debit support |
| **Void/Full Reversal** | Cancel/fully reverse a transaction | Core — cancellation |
| **Void of Refund** | Void a refund transaction | Core — cancel a refund |

**Design Decision (confirmed with Fiserv):** `Sale` (single-message auth+capture) is intentionally excluded. Host Capture runs periodic batches (every X minutes), which would capture Sale transactions almost immediately — leaving no void window. Softpay requires at least 15 minutes for voids. The dual-message flow (Authorization → Completion) gives Softpay control over capture timing: voids can be sent before the Completion, and the Completion is sent only when the transaction is final.

### 3.2 Key Management Operations (Selected)

| Transaction Type | Description |
|---|---|
| **Master Session Encryption (HSM)** | Key management — session encryption |

### 3.3 Reversal Types & Support Matrix

| Reversal Type | XML Value | Description | Applicable To |
|---|---|---|---|
| **Timeout** | `Timeout` | No response received from host | All card types |
| **Void** | `Void` | Cancel/fully reverse previous transaction | All card types |
| **VoidFr** | `VoidFr` | Void for suspected fraud | Supported |
| **Partial** | `Partial` | Reverse amount less than original | Most card types |
| **TORVoid** | `TORVoid` | Timeout reversal of a Void | Prepaid Closed Loop only |

**Key Reversal Rules (from UMF Spec Appendix D & 2026 UMF Changes):**
- Voids (except Credit Authorization and Credit Completion) must be submitted **within 25 minutes** of the original transaction
- Credit Completion voids can be submitted before the **merchant cut-off for the day** (limited platform availability)
- Timeout Reversals: Network-specific reference fields (Visa `TransactionIdentifier`, Mastercard `BankNetData`, Amex `AmExTranID`, Discover `DiscoverNRID`) are now **exempt** for TORs (per 2026 UMF Changes)
- `Completion` has been added to the Void/Full Reversal support column for Visa, Amex, Discover, JCB, Diners, MasterCard, UnionPay (2026 change)

### 3.4 Gap: Partial Authorization

Per Portal Definitions: *"Partial Authorization support is generally required for all Merchants in card-present environments in order to accommodate cardholder accounts where limited funds may be available (for example Debit or Open Loop Prepaid cards)."* Partial Authorization is **not explicitly selected** in the current Project Profile — this should be verified with Fiserv, especially given that PIN Debit and PINless POS Debit are selected.

---

## Step 4 — Integration & Connectivity

### 4.1 Communication Protocol

| Attribute | Value | Source |
|---|---|---|
| **Protocol** | XML over HTTPS | Project Profile |
| **Communication Type** | Datawire Secure Transport | Project Profile |
| **Encryption** | TLS (SSL) + 512-bit Blowfish payload encryption | Datawire Overview PPTX |
| **Authentication** | Per-transaction: MID, TID, Service ID, Datawire ID (DID) | Datawire Overview PPTX |

### 4.2 Datawire Secure Transport Architecture

Datawire is Fiserv's patented, PCI-DSS compliant IP-based transport network. Key characteristics:

- **Scale:** 1M+ merchants, 30B+ transactions/year (per Datawire Solution overview)
- **Network Transit Time:** 30-60ms average within the Datawire network
- **Redundancy:** Geographic and carrier diversity across North America, EMEA, APAC, LAC
- **Connection Method:** Uses public internet — no VPN/MPLS/dedicated line required
- **PCI Compliance:** Fully PCI-DSS compliant

**Connection Flow:**
1. POS application builds XML message per UMF spec
2. Datawire API wraps the message with security, authentication, and routing parameters
3. Message transmitted over TLS to Datawire Client Edge Node
4. Client Node authenticates: MID, TID, Service ID, DID (every transaction)
5. Message encrypted with 512-bit Blowfish and routed via patented algorithm
6. Service Node (in Fiserv data center) receives, strips Datawire wrapper
7. Native payment message delivered to Fiserv host system
8. Response returns through the same path

### 4.3 Datawire Connectivity Options for Softpay

For Softpay's mobile/SoftPOS use case, the relevant connectivity options are:

| Option | Applicability | Notes |
|---|---|---|
| **Datawire API (Java)** | Possible | Java API available; could work for Android apps |
| **Datawire XML API** | **Recommended** | Language-agnostic; full control; no binary installation; works with any OS |
| **Datawire API (Linux)** | Possible | C/C++, scripting languages; relevant for backend |
| Datawire NAM | Not applicable | Windows-only, hardware-based |
| Datawire Micronode | Not applicable | Hardware device for petroleum |

**Recommendation:** The **XML API** is most suitable for Softpay's architecture (CloudSwitch backend or embedded SDK). It requires no Datawire binary installation, works with any programming language, and gives full control over resource management. The XML API handles Service Discovery, Ping, and URL failover.

### 4.4 Environments & URLs

| Environment | Transaction URL | SRS Service Discovery URL |
|---|---|---|
| **Staging (Dev + Cert)** | `https://stg.dw.us.fdcnet.biz/rc` | `https://stg.dw.us.fdcnet.biz/sd/srsxml.rc` |
| **Production** | `https://prod.dw.us.fdcnet.biz/rc` | `https://prod.dw.us.fdcnet.biz/sd/srsxml.rc` |

**Note:** The actual transaction URL for a given MID/TID is returned during SRS Registration and must be saved permanently. SRS may return multiple URLs (`tx_endpoint_url_1`, `tx_endpoint_url_2`) for failover.

| Protocol | Port |
|---|---|
| HTTPS (XML POST) | **443** (TCP outbound only) |

**IP Addresses (subject to change — DNS resolution preferred):**

| Hostname | IPs |
|---|---|
| `stg.dw.us.fdcnet.biz` | 208.72.254.252, 216.66.222.252 |
| `prod.dw.us.fdcnet.biz` | 208.72.254.254, 216.66.222.254 |
| `stagingsupport.datawire.net` | 66.241.131.101 |
| `support.datawire.net` | 66.241.131.100, 69.46.100.78 |

**Warning:** Fiserv strongly discourages IP-based firewall rules. Use DNS resolution.

**Test Credentials (from Project Profile):**

| Industry | Merchant ID | TID (Dev 3) | TID (Dev 2) | TID (Cert 1) | Group ID |
|---|---|---|---|---|---|
| Restaurant | RCTST1000119068 | 003 | 002 | 001 | 20001 |
| Retail/QSR | RCTST1000119069 | 003 | 002 | 001 | 20001 |
| Supermarket | RCTST1000119070 | 003 | 002 | 001 | 20001 |

### 4.5 SRS (Self-Registration System) — One-Time per MID/TID

Before sending transactions, each MID/TID combination must complete a one-time SRS registration to obtain a Datawire ID (DID):

1. **Service Discovery** — HTTP GET to SRS URL → returns SRS service endpoint
2. **Registration** — HTTP POST with App=`RAPIDCONNECTSRS`, Auth=`<GroupID+MID>|<TID>`, ServiceID=`160`, DID=empty → returns DID + transaction URLs
3. **Activation** — HTTP POST immediately after Registration using the returned DID → completes SRS
4. **Transaction** — Use saved DID and transaction URLs for all subsequent transactions

**SRS Parameters:**

| Parameter | Value |
|---|---|
| ServiceID | `160` (fixed) |
| App (SRS) | `RAPIDCONNECTSRS` |
| App (Transactions) | `RAPIDCONNECTVXN` |
| Auth | `<GroupID><MerchID>\|<TermID>` (pipe-delimited) |
| Request Version | `3` |
| Payload Encoding | `cdata` |

**AuthKey Format (Datawire Auth header):**

| Component | Format | Example |
|---|---|---|
| AuthKey1 | `<GroupID><MerchID>` (up to 32 alphanumeric chars) | `20001RCTST1000119068` |
| AuthKey2 | `<TermID>` (zero-padded to 8 chars) | `00000001` |
| Combined | `AuthKey1\|AuthKey2` | `20001RCTST1000119068\|00000001` |

**ClientRef Format:** 14 characters: `tttttttVxxxxxx` where `ttttttt` = 7-char transaction ID (unique per 24h) + `V` separator + `xxxxxx` = TPPID. Example: `0023183VRSO024`

**User-Agent Header:** Required in every HTTP request. Format: application name + version (e.g., `Softpay SoftPOS v1.0`)

**Payload Encoding:** Two options — `xml_escape` (default; `<` → `&lt;`, `>` → `&gt;`, `&` → `&amp;`) or `cdata` (`<![CDATA[...]]>`). Nested CDATA sections are **invalid XML** and must be avoided.

**SRS Error Handling:** If Registration returns "Retry", wait 30 seconds and retry (3-5 attempts max). If "AccessDenied" is received on re-registration, SRS is already complete (DID is active). DID and transaction URLs must be **persisted permanently**.

**SRS Error Codes (Datawire-specific):**

| StatusCode | Meaning | Action |
|---|---|---|
| OK | Success | Proceed |
| AuthenticationError | Auth/verification error | Check MID/TID/GID/ServiceID |
| UnknownServiceID | Service ID unknown | Check ServiceID (must be 160) |
| AccessDenied | Already activated or double-activate attempt | DID is already active — use it |
| InvalidMerchant | MID/TID format invalid | Fix credentials |
| NotFound | Merchant profile not in Datawire network | Contact `securetransport.integration@fiserv.com` |
| Failed | Registration failed | Retry (up to 5 times) |
| Retry | Registration not yet complete | Retry with 30s intervals (up to 5 times) |
| Duplicated | Duplicate entry | Check existing registrations |
| Timeout | Operation timed out | Retry |
| XMLError / SOAPError | Request validation failed | Fix request format |
| InternalError / OtherError | Datawire internal error (transient) | Retry |

**DID Lifecycle:**
- Each MID/TID/ServiceID/App combination gets a **unique** DID — cannot be shared
- DID must be activated within a limited time period after Registration or it is **auto-deactivated**
- DID appears permanent once activated (no documented expiry/renewal)
- On DID compromise/loss (reinstall, reformat, terminal replacement): re-perform SRS
- Do NOT re-trigger SRS for host declines or network connectivity issues
- Merchant profile must be pre-configured in Datawire before SRS — contact `securetransport.integration@fiserv.com`

### 4.6 Timeouts

| Context | Timeout |
|---|---|
| **ClientTimeout (Staging)** | 15-40 seconds |
| **ClientTimeout (Production)** | 15-35 seconds |
| **Recommended default** | 30 seconds |
| **HTTP Keep-Alive** | Supported; exact timeout/max values are server-determined (observed: timeout=30-60s, max=100) |

### 4.7 HTTP Protocol Details

| Attribute | Value |
|---|---|
| **Method** | POST only |
| **HTTP Version** | 1.1 (for Keep-Alive support) |
| **Content-Type** | `text/xml` |
| **Cache-Control** | `no-cache` (mandatory) |
| **Connection** | `Keep-Alive` |

### 4.8 Service Discovery & Failover

The Datawire XML API provides:
- **Service Discovery** — locates optimal Client Edge Node (via SRS URL)
- **Multiple Transaction URLs** — SRS returns 1-2 URLs for failover
- **HTTP Keep-Alive** — connection reuse supported (server-determined timeout; documentation states support without specifying exact values)
- **DNS Resolution** — resolve hostname for each non-session transaction (do not cache IPs)

**Open Questions:**
- Is the staging environment shared for both Development and Certification, or are they separate?

---

## Step 5 — Security & Key Management

### 5.1 Encryption Configuration (Selected in Project Profile)

| Feature | Selected | Description |
|---|---|---|
| **Master Session Encryption** | Yes | Pre-shared master key encrypts randomly generated session keys; session keys rotated every 24 hours using HSM |
| **Key Block TR-31** | Yes | ANSI standard secure key exchange format; used with Master Session Encryption for enhanced security during key transmission |

### 5.2 PIN Encryption

| Aspect | Detail |
|---|---|
| **PIN Debit Selected** | **Yes** — PIN Debit is selected in the project profile |
| **PINless POS Debit Selected** | **Yes** — PINless POS Debit is also selected |
| **Online PIN for Contactless** | Required for PIN Debit transactions above CVM limit — **clarification needed** on how this works with SoftPOS/COTS devices under MPoC |
| **PIN Block Format** | ISO Format 0 (confirmed by XSD: `PINData` = `Len16HexString` = 16 hex chars = 8 bytes) |
| **Encryption Method** | **Master Session Encryption** (selected in project profile; Softpay decision) |

**PINGrp Implementation Required:** Since PIN Debit is selected, Softpay must implement the `PINGrp` (PIN Group) in the UMF spec with the following fields:

| Field | Description |
|---|---|
| `PINData` | Encrypted PIN block (16 hex chars, ISO Format 0) |
| `MSKeyID` | **Master Session Key ID** (from `EncryptionKeyRequest` response) |
| `NumPINDigits` | Number of PIN digits entered |
| `EnhKeyFmt` | Enhanced key format (`T` = TR-31) |
| `EnhKeyMgtData` | Enhanced key management data (up to 256 chars) |
| `EnhKeyChkDig` | Enhanced key check digit (up to 6 hex chars) |
| `EnhKeySlot` | Enhanced key slot (`1` or `2`) |

**Note:** Since Softpay will use **Master Session Encryption**, the `KeySerialNumData` (DUKPT KSN) and `KeyOffset` fields are not used. PINGrp will contain `PINData` + `MSKeyID`.

**Critical Consideration:** Online PIN entry on a COTS/SoftPOS device has significant MPoC (Mobile Payments on COTS) implications. Softpay's certified PIN pad component handles PIN entry securely, but the interaction with Fiserv's Master Session key exchange process and HSM requirements must be clarified.

**Open Questions for PIN Debit:**
- What is the process for obtaining the test master key for Sandbox/Certification?
- Is online PIN supported for contactless transactions, or only for contact chip?
- Are separate `EncryptionKeyRequest` calls needed per MID/TID combination?

### 5.3 TLS Requirements

| Requirement | Detail |
|---|---|
| **TLS Version** | **TLSv1.3** (preferred) + **TLSv1.2** (required fallback) — client must support both |
| **Certificate** | DigiCert Root CA certificates must be in client trust store |

**Required Cipher Suites (in preference order):**

| TLS Version | Cipher Suite | Hex Code |
|---|---|---|
| TLSv1.3 | TLS_AES_256_GCM_SHA384 | 0x1302 |
| TLSv1.3 | TLS_AES_128_GCM_SHA256 | 0x1301 |
| TLSv1.2 | TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384 | 0xC030 |
| TLSv1.2 | TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 | 0xC02F |

**Required Root CA Certificates:**

| Certificate | SHA1 Fingerprint | Valid Until |
|---|---|---|
| DigiCert Global Root CA | A8985D3A65E5E5C4B2D7D66D40C6DD2FB19C5436 | 2031 |
| DigiCert Global Root G2 | df3c24f9bfd666761b268073fe06d1cc8d4f82a4 | 2038 |

Download from: `https://www.digicert.com/digicert-root-certificates.htm`

**Hostname Verification (MANDATORY):** Must verify hostname against TLS certificate per RFC 6125 to prevent MITM attacks. This is tested during Datawire compliance certification (fake hostname test — transaction must fail if hostname doesn't match certificate).

**Certificate Pinning:** **STRONGLY DISCOURAGED** by Fiserv. Certificates may change frequently due to renewal, rotation, or revocation. Use standard trust store mechanisms instead.

Source: `Required_TLS_Ciphers_Secure_Transport_RapidConnect.pdf` (Rev 1.3, Sep 2025), `Required_Root_CA_Certificates_SecureTransport_RapidConnect.pdf`, `Datawire Compliance Test Form` (v3.4.7)
| **Client Certificate** | Not required for Datawire API (authentication is per-transaction via MID/TID/DID) |

### 5.4 Key Exchange & HSM

| Aspect | Detail |
|---|---|
| **Key Type** | Master Session Encryption with HSM |
| **Session Key Rotation** | Every 24 hours (per Portal Definitions) |
| **Key Request** | `TxnType=EncryptionKeyRequest` — requests a new Master Session encryption key |
| **Generate Key** | `TxnType=GenerateKey` — generates merchant-specific key |
| **TR-31 Key Block** | Secure key exchange format wrapping session keys |

**Open Questions:**
- What is the process for ordering the test master key for Master Session Encryption (Sandbox/Certification)?
- Who initiates the key exchange — Softpay or Fiserv? (Assumption: Softpay sends `EncryptionKeyRequest`)
- What HSM integration is required on the Softpay side for decrypting the session key?
- Is online PIN supported for contactless transactions on SoftPOS?
- Are separate `EncryptionKeyRequest` calls needed per MID/TID combination?

---

## Step 6 — EMV & Kernel Data

### 6.1 Contactless EMV Support

Softpay is a **contactless-only** SoftPOS solution (no contact chip reader). Per the EMV Implementation Guide:

| Aspect | Detail |
|---|---|
| **Contactless EMV** | Supported — selected as entry mode |
| **Contact EMV** | Selected in project profile, but not physically applicable (no chip reader on SoftPOS) — **clarification needed** |
| **MSR Contactless** | Not physically applicable, but note: RQ 0100 states if MSR contactless is supported, EMV contactless **must** also be supported |
| **Mandate** | Latest contactless payment specifications must be supported (RQ 3900) |

### 6.2 Contactless CVM Limits by Card Brand

Test Contactless CVM Limit values from **Table 24 (EMV Test Parameter Considerations)**, Section 8.5, page 8-8 of the EMV Implementation Guide (v2025.RG20). Footnote 3: "Values set by Brand and subject to change."

| Card Brand       | AID              | Test Contactless CVM Limit | Source                                    |
| ---------------- | ---------------- | -------------------------- | ----------------------------------------- |
| Visa Credit/Debit| A0000000031010   | $25.00                     | EMV Impl. Guide, Table 24, p. 8-8        |
| Visa Interlink   | A0000000033010   | $0.00                      | EMV Impl. Guide, Table 24, p. 8-8        |
| US Debit         | A0000000980840   | $25.00                     | EMV Impl. Guide, Table 24, p. 8-8        |
| Mastercard       | A0000000041010   | $100.00                    | EMV Impl. Guide, Table 24, p. 8-8 (raised from $50, effective 10/14/17 per Sec. 5.4) |
| Maestro          | A0000000043060   | $100.00                    | EMV Impl. Guide, Table 24, p. 8-8        |
| Amex Credit      | A00000002501     | $10.00                     | EMV Impl. Guide, Table 24, p. 8-8        |
| Amex Debit       | A00000002504     | $200.01                    | EMV Impl. Guide, Table 24, p. 8-8        |
| Discover Common  | A0000001524010   | $50.00                     | EMV Impl. Guide, Table 24, p. 8-8        |
| Discover D-PAS   | A0000001523010   | $50.00                     | EMV Impl. Guide, Table 24, p. 8-8        |

Table 23 (Sec. 8.5, p. 8-7) lists `Contactless CVM Limit` as a common AID parameter that "Vary by Brand/AID". Section 5.4 (p. 5-4) notes: Contactless CVM Limit for AFD will be $0 for all Brands.

**Below CVM Limit:** No CVM required (tap-and-go)  
**Above CVM Limit:** CVM required — for SoftPOS, this is CDCVM (Consumer Device CVM) for digital wallets, or online PIN (PIN Debit is selected and requires PIN encryption implementation)

### 6.3 Consumer Device CVM (CDCVM)

| Aspect | Detail |
|---|---|
| **Supported?** | Yes — CDCVM is mandatory for all payment networks on contactless (RQ 4100) |
| **Mechanism** | Cardholder authenticates on their own device (biometrics, PIN) before tapping |
| **Terminal Requirement** | Terminal must **NOT** ask for offline PIN entry for contactless transactions |
| **Debit Networks** | Do NOT support offline PIN in CDCVM |
| **All CDCVM Transactions** | Sent as dual-message |
| **Amex CDCVM** | Set in Tag 9F33 Byte 2 Bit 8 |

### 6.4 EMV Tags Required in Authorization

**Minimum Tags for Cryptogram Generation (Table 6 of EMV Guide):**

| Tag | Name | Source |
|---|---|---|
| 9F02 | Amount, Authorised | Terminal |
| 9F03 | Amount, Other | Terminal |
| 9F1A | Terminal Country Code | Terminal |
| 95 | Terminal Verification Results (TVR) | Terminal |
| 5F2A | Transaction Currency Code | Terminal |
| 9A | Transaction Date | Terminal |
| 9C | Transaction Type | Terminal |
| 9F37 | Unpredictable Number | Terminal |
| 82 | Application Interchange Profile | Card |
| 9F36 | Application Transaction Counter | Card |

**Additional Key Tags:**
- **9F27** — Cryptogram Information Data (CID)
- **9F26** — Application Cryptogram (ARQC/TC/AAC)
- **9F10** — Issuer Application Data (IAD)
- **9F6E** — Form Factor / Device Type Indicator — **mandatory** for contactless transactions
- **5F24** — Application Expiration Date (must be populated from card tag)
- **9F33** — Terminal Capabilities
- **9F17** — PIN Try Counter

**Tag 9F6E (Form Factor) Special Rules:**
- Mandatory for MSR and EMV contactless transactions
- Mastercard Fleet: Uses Tag 9F6E for Fleet prompt data
- Non-Fleet: Send only if 9F6E byte 3 bit 8 = 0
- Discover: Expected for both Contact and Contactless

### 6.5 Track Data Format

EMV contactless transactions should provide Track 2 equivalent data in the `CardGrp.Track2Data` field. Format follows ANSI/ISO 7813/ICC standards.

### 6.6 Offline Card Authentication Methods

Per the EMV Guide, **all three methods must be supported** (RQ 1700):

1. **SDA (Static Data Authentication)** — Least secure; detects post-personalization tampering
2. **DDA (Dynamic Data Authentication)** — Dynamic signature per transaction
3. **CDA (Combined DDA/AC Generation)** — Most secure; **CDA Mode 1 is mandatory** (RQ 1800)

**Offline CAM Failures:** Must be sent for online authorization (RQ 1500).

### 6.7 Kernel Configuration & CA Public Keys

| Aspect | Detail |
|---|---|
| **Who manages kernels?** | Softpay manages contactless EMV kernels as part of the SoftPOS SDK |
| **AID Lists** | Softpay maintains the AID configuration; must support partial AID selection (RQ 1100) |
| **CAID (Common AID)** | Must be auto-selected if present on card (RQ 1300) |
| **CA Public Keys (CAPK)** | Mandatory to download updated CAPK files (RQ 5700); check regularly; max 6 active per scheme |
| **CAPK File Format** | "CA_KEYS" file with comma-separated fields; SHA-1 hash algorithm; RSA key algorithm |
| **CAPK Update Process** | New CAPK file replaces old (no merge); download from single data center |
| **Test vs Production Keys** | Separate sets — non-interchangeable |

**Supported AIDs (from EMV Implementation Guide):**
- Visa, Mastercard, Amex, Discover, JCB, Diners Club
- US Common Debit AIDs: DNA (A000000620620), Discover (A000001524010), Maestro (A000000004203), Visa (A000000098084), UnionPay (A000000333010108), Amex (A000000002504)

**Open Questions:**
- Does Fiserv provide CAPK files via a download API, or are they distributed through the Rapid Connect portal?
- What is the expected CAPK update frequency?
- Does Fiserv require notification of kernel configurations used (RQ 0600)?
- Are there specific kernel parameter settings Fiserv mandates for contactless SoftPOS?

---

## Step 7 — Settlement & Reconciliation

### 7.1 Settlement Model

| Attribute | Value |
|---|---|
| **Settlement Methodology** | **Host Capture** (automated settlement) |
| **Settlement Indicator** | Controls timing: 1 = day-based, 2 = mid-day, 3 = real-time |
| **Batch Cycle** | Periodic — runs every X minutes (exact interval TBD), captures all completed transactions before the batch run time |
| **Batch Operations** | OpenBatch → BatchSettleDetail → CloseBatch |

**Host Capture** means Fiserv automatically settles transactions after they are captured (via Completion). The merchant does not need to manually initiate settlement. The batch cycle runs periodically (every X minutes), sweeping all completed transactions into settlement.

**Implication for voids (confirmed in Fiserv meeting):** Because Host Capture batches run frequently, any transaction that has been completed (Completion sent) will be swept into settlement quickly. This is why dual-message is required — between Authorization and Completion, the transaction is authorized but not yet captured, so it can be voided. Once Completion is sent, the void window is limited to the time before the next batch run.

### 7.2 Settlement Flows by Transaction Type

| Scenario | Flow |
|---|---|
| **Dual-message (Auth + Completion)** | Authorization → [void window] → Completion → auto-settled in next batch run (this is the selected purchase flow) |
| **Refund** | Refund → auto-settled |
| **PIN Debit Refund** | PIN Debit Refund → auto-settled |
| **Void (within 25 min)** | Void reverses the original transaction before settlement |
| **Void of Refund** | Void reverses a refund transaction |
| **Void after settlement** | Becomes a credit/refund |

**Note:** Sale is intentionally excluded — see Section 1.3 for rationale (Host Capture batch timing vs. void window requirement).

### 7.3 Batch Operations

Although Host Capture is selected (meaning Fiserv manages settlement automatically via periodic batch runs), batch operations are still available for reporting and reconciliation:

| Transaction Type | Description |
|---|---|
| `OpenBatch` | Initiate batch transfer from POS to host |
| `BatchSettleDetail` | Send batch settlement details |
| `CloseBatch` | End of batch indicator |

### 7.4 Open Questions

- With Host Capture, does Softpay still need to send OpenBatch/CloseBatch, or are these only needed for Terminal Capture?
- How does Softpay receive settlement confirmations/reports from Fiserv?
- What is the daily settlement cut-off time?
- What happens to a Void submitted after the daily cut-off? Does the response indicate that clearing has already occurred?
- Are settlement files or reports provided (e.g., via SFTP or API)?

---

## Step 8 — Merchant Onboarding

### 8.1 Current Configuration

| Attribute | Value |
|---|---|
| **Payment Facilitator** | **No Payment Facilitator** selected |
| **Money Transfer** | Not enabled |
| **Funding** | Not enabled |
| **Client Type** | Vendor |

### 8.2 Merchant Identifiers

Fiserv assigns the following identifiers:

| Identifier            | Description                                 | Test Values           |
| --------------------- | ------------------------------------------- | --------------------- |
| **MID (Merchant ID)** | Up to 16 alphanumeric characters            | RCTST1000119068/69/70 |
| **TID (Terminal ID)** | Up to 8 alphanumeric characters             | 001, 002, 003         |
| **Group ID (GID)**    | 5-13 alphanumeric characters                | 20001                 |
| **TPP ID**            | 6 alphanumeric characters (Fiserv-assigned) | RSO024                |
| **DID (Datawire ID)** | Assigned during first transaction           | Auto-generated        |

**Production credentials** differ in length from test MID/TID/GID.

### 8.3 Onboarding Process

Per the SDK documentation, the Rapid Connect onboarding flow is:

1. **Project Creation** — Fiserv creates a project in the Rapid Connect portal with all selected features
2. **SDK Generation** — Customized SDK generated based on project profile selections
3. **Development** — Softpay develops integration using test credentials
4. **Certification** — Validation against test scripts
5. **Review** — Fiserv Certification Analyst review
6. **Accepted** — PCI-DSS/PA-DSS verification, business agreements
7. **Complete** — Merchants can be boarded

### 8.4 Terminal Provisioning (Datawire)

Per the Datawire Overview:

1. **Terminal Deployment** — Softpay app deployed to merchant device
2. **Datawire Provisioning** — Automatic via MAS XML API (SSL) or manual via web GUI
3. **Network Propagation** — Datawire propagates credentials to all Client Edge Nodes
4. **Registration** — First transaction: terminal sends MID/TID/ServiceID/AppID → Datawire validates → creates DID → DID used for all subsequent transactions

---

## Step 9 — Certification & Test Requirements

### 9.1 Certification Overview

| Attribute | Value |
|---|---|
| **Certification Type** | Full-Cert |
| **Current Status** | Development |
| **RC Version** | 15.04 |

### 9.2 Certification Lifecycle (5 Stages)

| Stage | Description |
|---|---|
| **1. Development** | Build and test in Sandbox; use TIDs 002/003; TOR testing required before moving to Certification |
| **2. Certification** | Run all mandatory test scripts on **TID 00000001**; real-time pass/fail validation in Sandbox; all mandatory tests must pass 100% |
| **3. Review** | Fiserv Certification Analyst reviews results; manual validation if needed; **EMV receipt validation required** |
| **4. Accepted** | PCI-DSS/PA-DSS verification; business agreements; compliance checks |
| **5. Complete** | Certification letter issued; merchants can be boarded |

### 9.3 Test Scripts

**Mandatory Test Cases:** 425+ test cases (from RSO024_Testscript_2026-04-07.xlsx)  
**Non-Mandatory (Unit) Test Cases:** 84+ test cases (recommended but not required for certification)

Test cases cover:
- All selected payment types (Credit: Visa, MC, Amex, Discover, Diners)
- All selected transaction types (Authorization, Completion, Refund, Online Refund, PIN Debit Refund, Void/Full Reversal, Void of Refund)
- All selected entry modes (Contactless, Swiped, EMV)
- Industries: Restaurant, Retail/QSR, Supermarket
- Special scenarios: DCC, reversals, timeouts, partial reversals
- SoftPOS-specific: Terminal Category Code validation

### 9.4 Test Card Numbers (Ad Hoc Testing)

> **Note:** The PANs below could not be verified against any document in the RSO024 SDK package. The verified test cards from `TestTransactions_RSO024.xlsx` are listed in the Java examples README (Section "Test Card Numbers"). Use those for certification testing. The PANs below may be from a prior Fiserv integration or generic industry test numbers — confirm with Fiserv before use.

| Card Brand | Test PAN | Verified? |
|---|---|---|
| Visa | 4761530001111118 | **No** — not found in SDK |
| MasterCard | 5137221111116668 | **No** — not found in SDK |
| Discover | 6011208701117775 | **No** — not found in SDK |
| Amex | 3710300891113338 | **No** — not found in SDK |
| Diners Club | 36185900011112 | **No** — not found in SDK |
| JCB | 3566002345432153 | **No** — not found in SDK |
| PIN Debit | 4017779991113335 | **No** — not found in SDK |
| EBT | 5076800002222223 | **No** — not found in SDK |

**Keyed transactions:** Use expiration date **12/49** (unverified — confirm with Fiserv)

### 9.5 Sample XML Payloads

The SDK provides 425+ sample XML request payloads in `TestTransactions_RSO024.xlsx`. Each test case includes:
- A test case ID (9-digit numerical identifier)
- A complete XML request in the GMF v15.04 format
- Example shows namespace: `com/fiserv/Merchant/gmfV15.04`

**Sample Transaction Structure:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<GMF xmlns="com/fiserv/Merchant/gmfV15.04">
   <CreditRequest>
      <CommonGrp>
         <PymtType>Credit</PymtType>
         <TxnType>Refund</TxnType>
         <LocalDateTime>20260407075748</LocalDateTime>
         <TrnmsnDateTime>20260407075748</TrnmsnDateTime>
         <STAN>474747</STAN>
         <RefNum>6961301835</RefNum>
         <OrderNum>17420490</OrderNum>
         <TPPID>RSO024</TPPID>
         <TermID>00000001</TermID>
         <MerchID>RCTST1000119069</MerchID>
         <MerchCatCode>5399</MerchCatCode>
         <POSEntryMode>901</POSEntryMode>
         <POSCondCode>00</POSCondCode>
         <TermCatCode>01</TermCatCode>
         <TermEntryCapablt>01</TermEntryCapablt>
         <TxnAmt>000000011900</TxnAmt>
         <TxnCrncy>840</TxnCrncy>
         <TermLocInd>0</TermLocInd>
         <CardCaptCap>1</CardCaptCap>
         <GroupID>20001</GroupID>
      </CommonGrp>
      <CardGrp>
         <Track2Data>4503300000001238=25121011000012345678</Track2Data>
         <CardType>Visa</CardType>
      </CardGrp>
      <VisaGrp>...</VisaGrp>
   </CreditRequest>
</GMF>
```

### 9.6 EMV Certification Requirements

Per the EMV Implementation Guide:
- **Card Brand Certification** required for each PIN Pad kernel configuration (RQ 0800)
- **EMV QRG** contains EMV-specific receipt requirements
- Receipt validation required during Review stage
- L1/L2 certification documentation required

**Certification Grace Periods:**
- Visa: 2 years after L1/L2 expiry date
- Other brands: 1 year after L1/L2 expiry date

### 9.7 Datawire Compliance Certification (Separate from Rapid Connect Full-Cert)

**CRITICAL:** In addition to Rapid Connect Full-Cert, Softpay must complete a **separate Datawire Secure Transport Compliance Certification**. This must be completed **before** Rapid Connect certification begins.

| Aspect | Detail |
|---|---|
| **Two-part process** | Part 1: Written compliance test form (self-assessment); Part 2: Scheduled audio test with Secure Transport analyst |
| **Validity** | 2 years from completion date; periodic re-engagement required |
| **Re-certification** | Required for any modifications to application software or payment engine |
| **Re-cert (expedited)** | Available for simple updates to existing certified solutions; requires pre-approval from `securetransport.integration@fiserv.com` |
| **SRS Reset** | Contact SecureTransport analyst within 7 days prior to submission for SRS Reset on MID+TID |

**Key Compliance Requirements Tested:**

- SRS automation (not manual/developer-crafted XML)
- DID secure storage and propagation
- Multiple MID/TID combination support (must demonstrate alternating transactions between 2+ combinations)
- Hostname verification per RFC 6125 (tested with fake hostname; must fail)
- TLS cipher suite support (Wireshark trace required)
- No undocumented Datawire interactions (**violation voids certification**)
- No UMF EchoTest over Datawire
- Error handling: Datawire vs host processor error distinction
- Retry limits: max 3 for transactions, max 5 for SRS
- Payload encoding validation (xml_escape or cdata; no nested CDATA)
- If using session transactions: batch settlement test (125 + 25 transactions)
- Proxy support documentation
- Scheduled automated process documentation (with randomized delays)
- User-Agent header in every request

Source: `Datawire Compliance Test Form for Rapid Connect.doc` (v3.4.7), `Datawire RapidConnect Re-Certification Compliance Script.doc` (v1.7)

### 9.8 Timeout Reversal (TOR) Testing

- TOR testing is **required before moving to Certification**
- Internal unattended testing to develop TOR message format
- Final attended TOR test with Fiserv Certification Analyst
- Detailed guidance in `Timeout_Reversal_Testing_QRG.pdf`

### 9.8 Quick Reference Guides in SDK

| Guide | Purpose |
|---|---|
| EMV_QRG.pdf | EMV chip card processing requirements |
| RESTAURANT_QRG.pdf | Restaurant industry specifics (tips, split checks) |
| RETAIL_QSR_QRG.pdf | Retail/QSR transaction flows |
| SUPERMARKET_QRG.pdf | Supermarket industry (WIC, EBT, age verification) |
| TOKENIZATION_QRG.pdf | Tokenization implementation |
| Timeout_Reversal_Testing_QRG.pdf | TOR testing procedures |

---

## Step 10 — Special Features & Optional Capabilities

### 10.1 Features Selected at Launch

| Feature | Status | Details |
|---|---|---|
| **DCC** | Selected | Visa & Mastercard; see Feature Deep Dive |
| **Tipping (Tip Amount)** | Selected | Tip Amount is checked in the project profile |
| **Apple Pay** | Selected | NFC contactless with CDCVM |
| **Google Pay** | Selected | NFC contactless with CDCVM |
| **Samsung Pay** | Selected | NFC contactless with CDCVM |
| **PIN Debit** | Selected | Requires PINGrp implementation and PIN encryption |
| **PINless POS Debit** | Selected | Debit routing without PIN |
| **Master Session Encryption** | Selected | Session key rotation every 24 hours |
| **Key Block TR-31** | Selected | ANSI standard secure key exchange |
| **SoftPOS Terminal Category** | Selected | Terminal Category Code for SoftPOS |
| **Mobile POS** | Selected | Terminal Category Code |

### 10.2 Multi-Currency Support

- **Transaction Currency:** USD (840) — single currency
- **DCC:** Supports conversion to cardholder's home currency for Visa/Mastercard
- **GMA (Global Merchant Acquiring):** Not enabled — for multi-national use

---

## Step 11 — API-Specific Details

### 11.1 Specification Format

| Attribute | Value |
|---|---|
| **Specification Type** | XML Schema (XSD) + PDF documentation |
| **XSD File** | `UMF_XML_SCHEMA.xsd` (6,247 lines, namespace `com/fiserv/Merchant/gmfV15.04`) |
| **PDF Documentation** | `UMF_RSO024_2026.04.07.pdf` (comprehensive field-level spec) |
| **Sample Code** | `RCToolkitSampleCode.zip` (Java, C#, PHP) |
| **YAML/OpenAPI** | **Not provided** — XSD only |

### 11.2 Data Types & Encoding

| Aspect | Detail |
|---|---|
| **Encoding** | UTF-8 |
| **Amount Fields** | Numeric, max 12 digits, no decimal point (minor units) |
| **Date/Time** | YYYYMMDDhhmmss (14 digits) |
| **Max Message Size** | 14,336 bytes |
| **Base64 Fields** | Max 4,095 characters |
| **Account Number** | Limited to 19 digits (except Sunoco/Valero) |

### 11.3 Key Enumerations

**Payment Types (`PymtType`):** Credit, Debit, PLDebit, EBT, Check, Preaid, PvtLabl, Fleet, AltCNP

**Card Types (`CardType`):** Visa, MasterCard, Discover, JCB, Diners, AMEX, MaestroInt, UnionPay, + many more (30+ values)

**Transaction Types (`TxnType`):** 35+ values (see Step 3)

**Additional Amount Types (`AddlAmtType`):** Cashback, Surchrg, Tax, Fee, Fuel, Service, Tip, Discount, + 30 more

### 11.4 Response Codes

The UMF Spec Appendix A contains a comprehensive list of response codes. Key codes:

| Code | Meaning |
|---|---|
| 000 | Approved |
| 002 | Partial Approval |
| 00x | Various approval types |
| 05x | Declined |
| 51x | Insufficient funds |
| 100-199 | Various decline/error codes |
| 125 | Card being used in off hours (new in 2026) |

**2026 Addition:** Response code **125 — "Card being used in off hours"** added (per 2026 UMF Changes).

**Error Handling:**
- Errors are returned immediately without being sent to the processing host
- Error codes returned in `RespGrp.ErrorData` field
- Malformed XML: Application must continue processing (not cause failures)
- `RejectResponse` returned for any invalid request

### 11.5 Timeout & Retry Requirements

| Aspect | Detail |
|---|---|
| **ClientTimeout (Staging)** | 15-40 seconds (per request, sent in Datawire envelope) |
| **ClientTimeout (Production)** | 15-35 seconds |
| **Recommended ClientTimeout** | 30 seconds (all transaction types — no per-type differentiation) |
| **Application Read Timeout** | Must be **a few seconds longer** than ClientTimeout (sample code uses 45s) |
| **TOR Requirement** | If no response received, send Timeout Reversal **before** any retry |
| **Max Retries (Transactions)** | 3 automated retries (applies to Simple and InitiateSession only) |
| **Max Retries (SRS)** | 3-5 retries with 30-second intervals |

**Critical TOR/Retry Rules (from Datawire Compliance Test Form):**
1. **Always prioritize TOR before retrying the original transaction**
2. **If TOR fails, DO NOT retry the original transaction**
3. For session transactions: terminate the session on error, then start a new session
4. Automatic retry is NOT a Datawire compliance requirement — it is recommended but optional
5. Distinguish Datawire errors from host processor errors — Datawire retry guidance applies only to Datawire-generated errors

### 11.6 Connection Reuse

Per the Secure Transport Guide:
- HTTP 1.1 Keep-Alive is supported (exact timeout/max values are server-determined, not specified in documentation)
- SSL/TLS session reuse is recommended for high-throughput terminals
- DNS should be resolved for each non-session transaction (do not cache IPs)
- Session-based transactions are supported: `InitiateSession` → `SessionTransaction` (reuses SessionContext) → `TerminateSession`

**Open Questions:**
- Request official API specification in YAML/OpenAPI format for automated testing and code generation.
- Is there a rate limit per MID/TID?
- Does Fiserv pass through ISO 8583 Field 39 response codes directly, or are they mapped to UMF-specific codes?

---

## Step 12 — Receipt Requirements & App Branding

### 12.1 EMV Receipt Requirements (from EMV Implementation Guide)

**Mandatory Fields on All Receipts:**

| Field | Requirement |
|---|---|
| Merchant/DBA name and address | Mandatory |
| Transaction time and date | Mandatory |
| Transaction number | Mandatory |
| Receipt/Invoice number | Mandatory |
| Card type (Visa, MC, Amex, etc.) | Mandatory |
| Account number (last 4 digits only, truncated) | Mandatory (RQ 6000) |
| Card Entry Method | Mandatory (RQ 6200) |
| Application Preferred Name or Label | Mandatory (RQ 5900) |
| AID (truncated) | Mandatory (RQ 6100) |
| Approval Code | Mandatory |
| Authorization Mode (Issuer or Card) | Mandatory (RQ 6400) |
| Transaction Type (Sale/Refund/Void/TOR) | Mandatory |
| Transaction Amount / Grand Total | Mandatory (RQ 5800) |
| Currency (if not US) | Mandatory |
| PIN Verify Statement | Mandatory (RQ 6300) |
| Signature Line | Conditional |
| Return and Refund Policies | Mandatory (RQ 6501) |
| Payment Network (for CAID dual/credit messages) | Conditional |

**Card Entry Method Labels:**

| Label | Meaning |
|---|---|
| Chip | Contact Chip Read |
| Contactless / Waved | Contactless Chip Read |
| FSwipe / Fallback | Swiped after failed chip read |
| Magstripe | Swiped non-EMV transaction |

**Declined Transaction Receipts Must Include:**
- Decline Code
- Decline Message
- Mandatory EMV Tags: AID, TVR, IAD, ARQC (RQ 6500, RQ 6700)

### 12.2 Softpay Considerations

Since Softpay provides digital receipts (email/in-app), the same data elements must be included in the digital receipt format. Softpay should:
- Include all mandatory EMV receipt fields
- Support receipt formatting for all three industries (Restaurant, Retail/QSR, Supermarket)
- Handle DCC-specific receipt requirements (showing both currencies)
- Include the "Contactless" or "Waved" entry method label for NFC transactions

**Open Questions:**
- Does Fiserv have a specific "Receipt Requirements Specification" document beyond the EMV QRG?
- Are there specific formatting, language, or branding requirements mandated by Fiserv?
- Do receipts need to include the Datawire Transaction ID or other Fiserv-specific identifiers?
- Are dynamic descriptors (Soft Descriptors) needed for cardholder statement clarity?

---

## Step 13 — Project Management & Governance

### 13.1 Estimated Timeline

Based on the 5-stage certification lifecycle and typical SoftPOS integration complexity:

| Phase | Estimated Duration | Key Activities |
|---|---|---|
| **Analysis & Design** | 2-4 weeks | Protocol analysis (this document), architecture design, field mapping |
| **Development** | 8-12 weeks | XML message builder, Datawire integration, transaction flow implementation, EMV tag handling |
| **Internal Testing** | 4-6 weeks | Unit testing, integration testing, TOR testing |
| **Certification (Stage 2)** | 2-4 weeks | Run 425+ mandatory test cases, fix failures |
| **Review (Stage 3)** | 2-4 weeks | Fiserv analyst review, EMV receipt validation |
| **Accepted (Stage 4)** | 2-4 weeks | PCI compliance verification, business agreements |
| **Pilot** | 2-4 weeks | Live testing at 1-2 merchant locations |
| **Go-Live** | 1-2 weeks | Production deployment, monitoring |
| **Total Estimated** | **~6-9 months** | |

### 13.2 Key Milestones

1. Analysis complete & design approved
2. Datawire connectivity established (Sandbox)
3. First successful transaction in Sandbox
4. **Datawire Secure Transport Compliance Certification** (must complete BEFORE Rapid Connect Full-Cert)
5. All 425+ mandatory test cases passing
6. TOR testing complete
7. Certification submitted (Stage 2)
8. Fiserv Review passed (Stage 3)
9. Business agreements signed (Stage 4)
10. Production keys received
11. Pilot merchant live
12. General availability

### 13.3 Resource Requirements

| Role | Responsibility |
|---|---|
| **Integration Lead** | Architecture, design, field mapping |
| **Backend Developer(s)** | Datawire API integration, XML message handling, key management |
| **Mobile Developer(s)** | SoftPOS SDK integration, receipt formatting, UI flows |
| **QA Engineer** | Test script execution, TOR testing, regression |
| **DevOps** | Environment setup, Datawire provisioning, certificate management |
| **Security Engineer** | Key management, HSM integration, PCI compliance |
| **Project Manager** | Timeline, stakeholder communication, Fiserv coordination |

### 13.4 Fiserv Contacts

| Purpose | Contact |
|---|---|
| General | 800-872-7882 / getsolutions@fiserv.com |
| Datawire Integration | securetransport.integration@fiserv.com |
| Vendor/Gateway Integration | IntegratedPartners@fiserv.com |
| Merchant Integration | Sales Representative / Account Manager |

### 13.5 Communication & Governance

**Open Questions:**
- Who is the assigned Fiserv Certification Analyst for RSO024?
- What is the preferred communication channel (email, Slack, Teams)?
- Can we schedule a kickoff/technical workshop to address open questions?
- What is the SLA for Fiserv support during Development and Certification?
- Is there a dedicated Fiserv technical support contact for real-time issues during testing?

---

## Step 14 — Open Questions & Action Items

### 14.1 Critical Open Questions (Must Resolve Before Development)

| #   | Category       | Question                                                                                                                                                                                     | Priority |
| --- | -------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- |
| 1   | Security       | What is the process for ordering test and production encryption keys?                                                                                                                        | Critical |
| 2   | PIN Encryption | What is the process for obtaining the test master key for Master Session Encryption?                                                                                                         | Critical |
| 3   | Entry Mode     | TPP RSO024 currently rejects keyed POSEntryMode (`011`/`012`) with `TP0003`. Cert may require PAN-entry support — please enable it on the TPP (or provision a cert-only TPP that allows it). | Critical |
| 4   | Onboarding     | How are production MIDs/TIDs assigned and delivered to Softpay?                                                                                                                              | Critical |
| 5   | Transaction    | Is Partial Authorization required for card-present SoftPOS? If so, it needs to be enabled.                                                                                                   | Critical |
| 6   | Provisioning   | Test MIDs return `109 INVALID TERM` from BUYPASS on every authorization — please confirm at least one cert MID/TID is provisioned on the BUYPASS host.                                       | Critical |
| 7   | Provisioning   | Datawire SRS registration tickets for all four cert MIDs have been consumed. Please reset them so we can re-register and test additional combos.                                             | Critical |

### 14.2 Important Open Questions (Should Resolve Before Certification)

| #   | Category      | Question                                                                                                                                                               | Priority     |
| --- | ------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------ |
| 8   | Tipping       | "Tip Amount" is selected — can tipping be used outside of Restaurant industry (Retail/QSR, Supermarket)?                                                               | High         |
| 9   | DCC           | Who provides the DCC rate feed — Fiserv or third party?                                                                                                                | High         |
| 10  | DCC           | What is the DCC rate refresh frequency?                                                                                                                                | High         |
| 11  | DCC           | Are there specific DCC receipt requirements?                                                                                                                           | High         |
| 12  | Receipt       | Does Fiserv have a Receipt Requirements Specification document?                                                                                                        | High         |
| 13  | Deferred Auth | Does Rapid Connect support submitting deferred/offline authorizations? Only `CancelDeferredAuth` exists in the XSD — no TxnType for submission. What is the mechanism? | **Critical** |
| 14  | Deferred Auth | Can Softpay queue standard `Authorization` messages offline and submit when connectivity returns, or is a specific deferred auth flow required?                        | High         |

### 14.3 Assumptions Made

| #   | Assumption                                                                                                                                 | Risk if Wrong                                                   |
| --- | ------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------- |
| A1  | Host Capture means Fiserv handles settlement automatically via periodic batch runs (every X minutes) without batch operations from Softpay | May need to implement batch operations                          |
| A2  | Softpay's existing MPoC/EMV L1/L2 certification satisfies Fiserv's EMV requirements                                                        | May need additional card brand certification                    |
| A3  | Datawire XML API is the correct connectivity option for Softpay                                                                            | May need to use a different API variant                         |
| A4  | Tipping is enabled via "Tip Amount" selection and works with the dual-message auth+completion flow                                         | May need additional configuration                               |
| A5  | Production MID/TID assignment is a manual process through Fiserv                                                                           | May be automated/API-based                                      |
| A6  | EMV (contact chip) selection in the profile is a configuration oversight since SoftPOS has no chip reader                                  | May need to support contact chip in some manner                 |
| A7  | Softpay's certified PIN pad satisfies Fiserv's requirements for online PIN entry on COTS devices                                           | May need additional PIN pad certification or different approach |

### 14.4 Key Risks

| #   | Risk                                                                                                                                                | Impact                                                                           | Mitigation                                                                                                               |
| --- | --------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| R1  | 425+ mandatory test cases may require extensive development time                                                                                    | Timeline delay                                                                   | Start with most common transaction flows; parallelize test execution                                                     |
| R2  | EMV card brand certification may require additional testing beyond Rapid Connect                                                                    | Timeline delay; additional cost                                                  | Clarify early with Fiserv; leverage existing Softpay certifications                                                      |
| R3  | DCC implementation requires rate feed integration and specific EMV handling                                                                         | Feature delay if not planned properly                                            | Engage DCC provider early; plan contactless edge cases                                                                   |
| R4  | 2026 UMF Changes (v15.04.5) introduce new fields (Transaction Acceptance Method, Cardholder Form Factor, Payment Method Type)                       | May require implementation of new fields                                         | Review all 2026 changes for applicability                                                                                |
| R5  | EMV (contact chip) is selected but SoftPOS has no chip reader — may cause certification issues if tests require contact chip flows                  | Certification delays                                                             | Clarify with Fiserv immediately whether this should be deselected                                                        |

---

## Appendix A — 2026 UMF Changes Relevant to Softpay

The 2026 UMF Changes document (effective February 25, 2026, version 15.04.5) includes several changes relevant to the Softpay integration:

### New Fields Added (Common Chapter)
- **Transaction Acceptance Method** — Indicates how the transaction was accepted
- **Cardholder Form Factor** — Specifies the physical form of the payment instrument
- **Payment Method Type** — New field added to Card Group

### Timeout Reversal Exemptions
Network-specific reference fields are now **exempt for Timeout Reversals**:
- Mastercard `BankNetData` — TOR exception added
- Amex `AmExTranID` — TOR exception added
- Discover `DiscoverNRID` — TOR exception added
- Visa `TransactionIdentifier` — TOR exception added

### Completion Added to Void/Reversal Support
- `Completion` added to Void/Full Reversal column for Visa, Amex, Discover, JCB, Diners, MasterCard, UnionPay

### New Response Code
- **125 — "Card being used in off hours"** added to response code list

### Digital Wallet
- **BankPay** added as new Digital Wallet Program Type value

### Bill Payment Rules
- Updated rules for `BillPymtTxnInd` and `POSCondCode` interactions
- Refund transactions must NOT include Bill Payment Transaction Indicator with POS Condition Code '04'

### Refund Type Field
- Now mandatory when Transaction Type is 'Refund' and Card Type is 'MasterCard' or 'Maestro'

---

## Appendix B — Project Profile Summary

| Category | Configuration |
|---|---|
| **Project ID** | RSO024 |
| **Company** | Softpay ApS (SOF006) |
| **App Type** | Mobile |
| **Region** | North America (US) |
| **Currency** | USD |
| **Industries** | Restaurant, Retail/QSR, Supermarket |
| **Settlement** | Host Capture |
| **Protocol** | XML over HTTPS |
| **Transport** | Datawire |
| **Credit Cards** | Visa, Mastercard, Amex, Discover, Diners Club |
| **Debit** | PIN Debit, PINless POS Debit |
| **Entry Modes** | Swiped, Contactless, EMV (Contact) |
| **Wallets** | Apple Pay, Google Pay, Samsung Pay |
| **Terminal Category** | Mobile POS, SoftPOS |
| **Transaction Types** | Authorization, Completion, Refund, Online Refund, PIN Debit Refund, Void/Full Reversal, Void of Refund, Master Session Encryption (HSM) |
| **Encryption** | Master Session Encryption, Key Block TR-31 |
| **DCC** | Selected |
| **Tipping** | Selected (Tip Amount) |
| **PayFac** | Not selected |
| **Volume** | 1M-5M transactions/year |
| **Test MIDs** | RCTST1000119068, RCTST1000119069, RCTST1000119070 |

---

## Appendix C — Document Inventory

| Document | Version/Date | Purpose |
|---|---|---|
| UMF_RSO024_2026.04.07.pdf | v15.04.5 / Feb 25, 2026 | Main UMF protocol specification |
| UMF_XML_SCHEMA.xsd | gmfV15.04 / Jan 7, 2026 | XML schema definition |
| Fiserv_Generic_EMV_Implementation_Guide_v2025_RG20_081225.pdf | v2025.RG20 / Aug 12, 2025 | EMV implementation requirements |
| 2026 UMF Changes-1.pdf | Feb 25, 2026 | UMF change log |
| Fiserv USA RapidConnect Project Profile.pdf | Apr 2026 | Project configuration |
| Rapid Connect Portal Definitions.pdf | v1.85 / Apr 29, 2025 | Portal field definitions |
| The_Datawire_Solution.pdf | v1.1 / Feb 22, 2022 | Datawire connectivity options |
| Datawire_Secure_Transport_Overview.pptx | Jan 2023 | Datawire architecture |
| START_HERE.pdf | v12.04.2 / Jul 26, 2022 | SDK onboarding guide |
| Functionality Grid.xlsx | — | Feature/platform matrix |
| TestTransactions_RSO024.xlsx | Apr 7, 2026 | Sample XML test payloads |
| RSO024_Testscript_2026-04-07.xlsx | Apr 7, 2026 | Certification test scripts |
| RCToolkitSampleCode.zip | — | Java/C#/PHP sample code (includes HTTP POST, SOAP, TCP/IP handlers) |
| Secure_Transport_Guide.zip | — | Datawire integration guide (extracted — see below) |
| SecureTransport-RapidConnect-Guide.pdf | — | Datawire XML/SOAP/TCP protocol, SRS registration, transaction flow |
| SecureTransport-SRS-RapidConnect.pdf | — | SRS (Self-Registration System) detailed process |
| Datawire Parameter Guidelines for Rapid Connect.pdf | — | Auth, DID, ServiceID, ClientRef, timeout parameters |
| Required_TLS_Ciphers_Secure_Transport_RapidConnect.pdf | Rev 1.3 / Sep 2025 | TLSv1.2/1.3 cipher suite requirements |
| Required_Root_CA_Certificates_SecureTransport_RapidConnect.pdf | — | DigiCert Root CA certificates for trust store |
| Step By Step SRS Process for Secure Transport Rapid Connect.pdf | — | SRS walkthrough with screenshots |
| rcxml.xsd | — | Datawire transport XML schema (request/response envelope) |
| DW_RC_SRS_namespace-less.XSD | — | SRS XML schema (registration/activation operations, 14 status codes) |
| Datawire Compliance Test Form for Rapid Connect.doc | v3.4.7 | Datawire Secure Transport compliance test (Part 1: written, Part 2: audio) |
| Datawire RapidConnect Re-Certification Compliance Script.doc | v1.7 | Datawire re-certification for minor updates |
| EMV_QRG.pdf | — | EMV Quick Reference Guide |
| RESTAURANT_QRG.pdf | — | Restaurant QRG |
| RETAIL_QSR_QRG.pdf | — | Retail/QSR QRG |
| SUPERMARKET_QRG.pdf | — | Supermarket QRG |
| TOKENIZATION_QRG.pdf | — | Tokenization QRG |
| Timeout_Reversal_Testing_QRG.pdf | — | TOR Testing QRG |

---

*This analysis was prepared based on the SDK documentation package for project RSO024 (Softpay by Softpay ApS). All findings reference specific sections of the provided documentation. Open questions should be addressed in the technical workshop with Fiserv.*
