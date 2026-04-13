# Fiserv Rapid Connect Technical Workshop — Softpay SoftPOS Integration

**Project:** RSO024 — Softpay ApS  
**Integration:** Rapid Connect v15.04, UMF XML over HTTPS via Datawire  
**Current Status:** Development  
**Prepared:** April 7, 2026  
**Purpose:** Structured agenda for the technical alignment call between Softpay and Fiserv to resolve open questions from the integration analysis.

---

## How to Use This Document

Each question includes:

- **Question** -- the specific item to resolve
- **Context** -- why we are asking (one sentence)
- **Assumption** -- what Softpay will proceed with if the question is not answered
- **Blocks** -- which project phase this blocks (Development / Certification / Go-Live)

Questions are grouped by topic and ordered by what blocks development start first. Within each group, questions are roughly ordered by criticality.

---

## 1. Connectivity & Environments

*Blocks: Development start. Without connectivity answers, Softpay cannot send the first transaction.*

### ~~Q1.1 — Datawire Sandbox/Certification/Production base URLs~~ RESOLVED

- **Answer:** Found in `Secure_Transport_Guide`. Staging: `https://stg.dw.us.fdcnet.biz/rc`, Production: `https://prod.dw.us.fdcnet.biz/rc`. SRS endpoints also documented. See FISERV_USA_ANALYSIS.md Section 4.4.
- **Remaining question:** Is the staging environment shared for both Development and Certification, or are they logically separate?

### ~~Q1.2 — Static IP whitelisting~~ PARTIALLY RESOLVED

- **Answer:** The Secure Transport Guide explicitly states: *"We do not recommend limiting access to the below hosts in your firewall to the provided IP addresses as they are subject to change."* Datawire authenticates per-transaction via MID/TID/DID — no static IP whitelisting is required from Fiserv's side.
- **Remaining question:** Does Softpay need to provision fixed outbound IPs for any reason (e.g., Fiserv-side firewall rules), or is DNS-based outbound sufficient?
- **Blocks:** Development

### ~~Q1.3 — Connection reuse policy~~ RESOLVED

- **Answer:** Found in `Secure_Transport_Guide`. HTTP 1.1 Keep-Alive is supported (exact timeout/max values are server-determined, not specified in documentation). SSL/TLS session reuse is recommended. Session-based transactions also supported via `InitiateSession` / `SessionTransaction` / `TerminateSession`. **Note:** Do NOT refresh DNS midway through a Datawire session (compliance requirement). If HTTP response `Connection: Close` is received, must open a new TCP/SSL connection.

### ~~Q1.4 — Health check / echo endpoint~~ PARTIALLY RESOLVED

- **Answer:** The Datawire Compliance Test Form (Section 4.10 / 7.3) explicitly states: *"Confirm application does NOT implement UMF EchoTest over Datawire connection (not necessary)."* No dedicated health-check endpoint is documented. Datawire does not provide or require a Ping mechanism.
- **Remaining question:** What is the recommended approach for Softpay to detect connectivity issues proactively? Options: (a) rely on transaction-level errors, (b) use a lightweight HTTP HEAD to the Datawire URL, or (c) another mechanism?
- **Blocks:** Development

### ~~Q1.5 — Authorization timeout values~~ RESOLVED

- **Answer:** Found in `Datawire Parameter Guidelines`. ClientTimeout: 15-40s (staging), 15-35s (production). Sample code uses 30 seconds. Softpay should use 30s as the default.
- **Remaining question:** Should TOR be sent immediately after ClientTimeout, or is there an additional grace period before TOR submission?

### ~~Q1.6 — EchoTest for keepalive~~ RESOLVED

- **Answer:** The Datawire Compliance Test Form (Section 4.10 / 7.3) explicitly states: *"Confirm application does NOT implement UMF EchoTest over Datawire connection (not necessary)."* EchoTest must NOT be used over Datawire. Additionally, Section 7.1/4.8 warns that *"any undocumented interaction with Datawire servers voids certification."* Softpay must NOT send EchoTest, Ping, or any other non-transaction request to Datawire.

---

## 2. Security & Key Management

*Blocks: PIN Debit development and overall encryption implementation. Cannot implement PINGrp or session encryption without these answers.*

### ~~Q2.1 — TLS version requirement~~ RESOLVED

- **Answer:** Found in `Required_TLS_Ciphers_Secure_Transport_RapidConnect.pdf` (Rev 1.3, Sep 2025). TLSv1.3 preferred + TLSv1.2 required fallback. Four specific cipher suites mandated: TLS_AES_256_GCM_SHA384, TLS_AES_128_GCM_SHA256 (1.3), TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 (1.2). DigiCert Root CA certificates required in trust store.
- **Blocks:** Development

### Q2.2 — Master Session Encryption: key ordering process

- **Question:** What is the process for ordering test encryption keys (for Sandbox/Certification) and production encryption keys for Master Session Encryption?
- **Context:** Master Session Encryption is selected in the project profile, requiring pre-shared master keys before session key rotation can begin.
- **Assumption:** Softpay will submit a key request to Fiserv via the Rapid Connect portal or the assigned Certification Analyst.
- **Blocks:** Development

### Q2.3 — Key exchange initiation

- **Question:** Who initiates the key exchange -- does Softpay send an `EncryptionKeyRequest` transaction, or does Fiserv push keys through a separate channel?
- **Context:** The UMF spec defines `EncryptionKeyRequest` as a transaction type, but it is unclear whether this is the primary mechanism or if keys are pre-loaded.
- **Assumption:** Softpay initiates key exchange via `EncryptionKeyRequest` after master key is pre-shared.
- **Blocks:** Development

### Q2.4 — MAC validation during sandbox

- **Question:** Can MAC (Message Authentication Code) validation be disabled during the Sandbox/Development phase, or must it be active from the start?
- **Context:** MAC Master Session is not selected in the RSO024 project profile, but Softpay needs to confirm that message authentication is not enforced during development to reduce initial complexity.
- **Assumption:** MAC is not required since MAC Master Session and MAC DUKPT are both deselected in the project profile. **Note:** This is separate from Master Session Encryption for PIN — MAC is for message authentication, not PIN encryption.
- **Blocks:** Development

### Q2.5 — HSM integration requirements

- **Question:** What HSM integration is required on the Softpay side for Master Session Encryption and TR-31 key block handling?
- **Context:** Softpay needs to determine whether a cloud HSM (e.g., AWS CloudHSM, Azure Dedicated HSM) is required or if software-based key management is acceptable.
- **Assumption:** Softpay will use a cloud HSM for production key storage and session key operations.
- **Blocks:** Development

### ~~Q2.6 — PIN encryption method preference~~ RESOLVED

- **Answer:** Softpay will use **Master Session Encryption** (selected in the RSO024 Project Profile). PIN encryption occurs on the Softpay backend using the session key obtained via `EncryptionKeyRequest`. PINGrp will contain `PINData` + `MSKeyID`.

### ~~Q2.7 — PIN block format~~ RESOLVED

- **Answer:** **ISO Format 0** (ISO 9564-1). Confirmed by the UMF XSD: `PINData` is defined as `Len16HexString` (exactly 16 hex chars = 8 bytes). ISO Format 4 produces 32 hex chars and does not fit this field.

### ~~Q2.8 — BDK provisioning for DUKPT~~ N/A

- **Answer:** Not applicable. Softpay will use Master Session Encryption, not DUKPT. No BDK provisioning required.

### ~~Q2.9 — KSN counter management~~ N/A

- **Answer:** Not applicable. Softpay will use Master Session Encryption, not DUKPT. No KSN counter management required.

---

## 3. EMV & Contactless Configuration

*Blocks: EMV implementation and contactless transaction processing.*

### Q3.3 — CAPK update frequency

- **Question:** What is the expected CAPK update frequency, and is there a notification mechanism when new CAPKs are published?
- **Context:** Softpay needs to schedule CAPK refresh cycles and handle mid-lifecycle key rotations without service interruption.
- **Assumption:** CAPKs are updated quarterly; Softpay will check for updates weekly.
- **Blocks:** Development

### Q3.5 — Specific kernel parameter settings for contactless SoftPOS

- **Question:** Are there specific kernel parameter settings (e.g., floor limits, transaction limits, CVM limits) that Fiserv mandates for contactless SoftPOS beyond the standard EMV Guide requirements?
- **Context:** SoftPOS may have different risk thresholds than traditional hardware POS; Fiserv may have specific requirements for COTS-based terminals.
- **Assumption:** Softpay will use the standard contactless CVM limits documented in the EMV Guide (Visa: variable, MC: $100, Amex: $50, Discover: $25).
- **Blocks:** Development

### Q3.6 — Form Factor Indicator (Tag 9F6E)

- **Question:** Are there specific requirements for Tag 9F6E (Form Factor / Device Type Indicator) when the terminal is a SoftPOS device?
- **Context:** Tag 9F6E is mandatory for contactless transactions; the correct byte values must identify the device as a COTS/SoftPOS terminal rather than a traditional POS.
- **Assumption:** Softpay will populate Tag 9F6E per the standard EMV Guide rules, setting byte values to indicate a COTS mobile device.
- **Blocks:** Development

### Q3.7 — Production contactless CVM limit recommendations

- **Question:** Table 24 in the EMV Guide references *test* CVM limits. Does Fiserv provide general recommendations for Contactless CVM limits in a **production** environment (per scheme: Visa, Mastercard, Amex, Discover)?
- **Context:** The EMV Guide's CVM limit table is scoped to test scenarios. SoftPOS deployments need authoritative production CVM thresholds (and any per-MCC or per-region adjustments) to configure the contactless kernel correctly and avoid declines or unnecessary CVM prompts.
- **Assumption:** Softpay will use scheme-published US production defaults (e.g., MC $100, Amex $50, Discover $25, Visa as configured per region) absent Fiserv-specific guidance.
- **Blocks:** Development

---

## 4. Transaction Processing

*Blocks: Dual-message implementation and core transaction flows.*

### ~~Q4.1 — Sale transaction type intentionally excluded~~ RESOLVED

- **Answer:** Confirmed in Fiserv meeting. Sale is intentionally excluded. Host Capture runs periodic batch cycles (every X minutes), automatically capturing all completed transactions. With Sale (single-message), transactions would be captured and batched almost immediately — leaving no window for voids. Softpay requires at least 15 minutes for void support. The dual-message flow (Authorization → Completion) gives Softpay control over capture timing: voids can be sent any time before the Completion is sent.

### Q4.2 — Partial Authorization requirement

- **Question:** Is Partial Authorization required for card-present SoftPOS environments? If so, it needs to be enabled in the project profile (currently not selected).
- **Context:** The Portal Definitions state that "Partial Authorization support is generally required for all Merchants in card-present environments" to handle prepaid and debit cards with limited funds; PIN Debit and PINless POS Debit are both selected.
- **Assumption:** Partial Authorization is required and should be added to the project profile; Softpay will implement split-tender handling.
- **Blocks:** Development

### ~~Q4.4 — Timeout values for auth/completion/reversal~~ PARTIALLY RESOLVED

- **Answer:** Per the Datawire Parameter Guidelines and Compliance Test Form: ClientTimeout is uniform across all transaction types (no per-type differentiation). Staging: 15-40s, Production: 15-35s. Recommended: 30s. **Critical:** The application's read timeout (after TCP/TLS handshake) must be a few seconds LONGER than ClientTimeout to reliably receive Datawire's response. Sample code uses 45s socket timeout with 30s ClientTimeout.
- **Remaining question:** Should TOR be sent immediately after ClientTimeout, or is there a grace period? Is TOR timeout also 30s? The Compliance Test Form states: *"Always prioritize TOR before retrying original transaction. If TOR fails, DO NOT retry original transaction."*
- **Blocks:** Development

### Q4.5 — Completion deadline after Authorization

- **Question:** Is there a maximum time window between an Authorization and its corresponding Completion? Are there card-brand-specific deadlines?
- **Context:** In the Restaurant tipping workflow, the Completion (with tip) may be sent minutes or hours after the Authorization; Softpay needs to enforce any deadline in the app.
- **Assumption:** Completions must be submitted before the daily settlement cut-off on the same day or within 7 days (per typical card brand rules).
- **Blocks:** Development

---

## 5. Tipping

*Blocks: Restaurant industry go-live. Tip Amount is selected in the project profile.*

### Q5.1 — Tipping flow confirmation

- **Question:** Softpay's preferred tipping flow is **tip-before-auth**: collect tip on device, authorize for the full amount (service + tip), then complete for the same amount. Confirm this is accepted by Fiserv across all three RSO024 industries (Restaurant, Retail/QSR, Supermarket). Is the `TipAmt` field in AddtlAmtGrp required or optional when tipping is enabled?
- **Context:** Tip-before-auth is simpler (auth = completion amount, no 20% tolerance needed) and works for any MCC. The traditional tip-after-auth flow (auth for subtotal, completion for subtotal+tip) is only needed for table-service restaurants with paper receipts.
- **Assumption:** Tip-before-auth works — `TxnAmt` is just an amount, the protocol doesn't distinguish service vs. tip portions. `TipAmt` is a reporting-only field per Portal Definitions.
- **Blocks:** Development (Restaurant)

### Q5.3 — Tipping across industries

- **Question:** Can tipping be supported in Retail/QSR and Supermarket industries, or is it limited to Restaurant only?
- **Context:** The Portal Definitions describe "Tip Amount" as being for "Quick Service Restaurant industry only," but merchants in other industries (e.g., service-based retail) may also want tipping.
- **Assumption:** Tipping is limited to Restaurant industry in the initial phase; Retail/QSR and Supermarket will not support tipping at launch.
- **Blocks:** Development

### Q5.4 — Tip field placement

- **Question:** Which fields carry the tip amount -- is it only the TxnAmt difference between Authorization and Completion, or should the tip also be explicitly specified in the AddtlAmtGrp (Additional Amount Group)?
- **Context:** The UMF spec supports `AddlAmtType` values including tip-related amounts; Softpay needs to know if both the total and the tip breakdown must be sent.
- **Assumption:** Tip is reflected only in the TxnAmt of the Completion (total including tip); AddtlAmtGrp is not required for tip unless Fiserv specifies otherwise.
- **Blocks:** Development (Restaurant)

---

## 6. DCC (Dynamic Currency Conversion)

*Blocks: DCC feature development. DCC is selected in the project profile.*

### Q6.1 — DCC rate feed provider

- **Question:** Who provides the DCC rate feed -- Fiserv directly, or a third-party DCC provider that Softpay must integrate with separately?
- **Context:** Softpay needs to display the converted amount to the cardholder before they choose their preferred currency; this requires a real-time or near-real-time rate feed.
- **Assumption:** Fiserv provides the DCC rate feed as part of the Rapid Connect service; Softpay will integrate with the Fiserv-provided feed.
- **Blocks:** Development (DCC)

### Q6.2 — DCC rate refresh frequency

- **Question:** How frequently are DCC rates refreshed, and what is the maximum acceptable staleness for a rate used in a transaction?
- **Context:** Stale rates create financial risk and may violate card brand rules; Softpay needs to implement an appropriate caching and refresh strategy.
- **Assumption:** Rates are refreshed daily; Softpay will pull fresh rates at the start of each business day and cache them locally.
- **Blocks:** Development (DCC)

### Q6.3 — Separate DCC certification

- **Question:** Is there a separate DCC certification requirement beyond the standard Rapid Connect Full-Cert, or is DCC tested as part of the regular certification test scripts?
- **Context:** DCC adds complexity to the transaction flow (currency selection, dual-currency receipts, EMV tag handling); it may require additional test cases or a separate approval.
- **Assumption:** DCC is covered by the standard certification test scripts included in the RSO024 test suite.
- **Blocks:** Certification

### Q6.4 — DCC receipt requirements

- **Question:** Are there specific receipt requirements for DCC transactions, such as showing both currencies, the conversion rate, and a DCC disclosure statement?
- **Context:** Card brand rules (Visa/MC) typically require the receipt to display the original currency, converted currency, exchange rate, and cardholder consent; Softpay needs the exact format requirements.
- **Assumption:** Softpay will display both currencies, the conversion rate, and a cardholder consent indicator on digital receipts per standard Visa/MC DCC receipt rules.
- **Blocks:** Development (DCC)

### Q6.5 — DCC brand restrictions in US

- **Question:** Confirm that DCC is not available for Amex, Discover, and Diners Club in the US Fiserv environment, and that it is limited to Visa and Mastercard only.
- **Context:** The Portal Definitions state DCC is available for Visa and Mastercard; Softpay needs explicit confirmation to disable the DCC offer for other card brands.
- **Assumption:** DCC is Visa and Mastercard only; Softpay will suppress the DCC offer for all other card brands.
- **Blocks:** Development (DCC)

---

## 7. Settlement & Reconciliation

*Blocks: Go-live. Softpay must understand the settlement model to implement end-of-day reconciliation.*

### Q7.4 — Settlement file delivery method

- **Question:** Are settlement files or detailed transaction reports delivered to Softpay via SFTP, API, or another mechanism?
- **Context:** Automated reconciliation requires machine-readable settlement data; Softpay's reporting infrastructure needs to ingest this data programmatically.
- **Assumption:** Softpay will reconcile from its own transaction logs and use the Rapid Connect portal for exception investigation.
- **Blocks:** Go-Live

---

## Proposed Workshop Agenda

Suggested duration: **2 hours**. Adjust based on attendee availability.

| Time | Duration | Topic | Section | Presenter |
|------|----------|-------|---------|-----------|
| 0:00 | 5 min | Introductions and agenda review | -- | Both |
| 0:05 | 5 min | Softpay SoftPOS platform overview | -- | Softpay |
| 0:10 | 5 min | RSO024 project profile walkthrough | -- | Fiserv |
| 0:15 | 15 min | **Connectivity & Environments** (Q1.1-Q1.6) | Section 1 | Both |
| 0:30 | 20 min | **Security & Key Management** (Q2.1-Q2.9) | Section 2 | Both |
| 0:50 | 15 min | **EMV & Contactless Configuration** (Q3.3, Q3.5-Q3.7) | Section 3 | Both |
| 1:05 | 10 min | **Transaction Processing** (Q4.1-Q4.2, Q4.4-Q4.5) | Section 4 | Both |
| 1:15 | 10 min | **Tipping** (Q5.1, Q5.3-Q5.4) | Section 5 | Both |
| 1:25 | 10 min | **DCC** (Q6.1-Q6.5) | Section 6 | Both |
| 1:35 | 5 min | **Settlement & Reconciliation** (Q7.4) | Section 7 | Both |
| 1:40 | 20 min | Action items recap and next steps | -- | Both |

**Note:** Sections 1-3 (Connectivity, Security, EMV) are prioritized as they block development start.

---

## Action Item Tracking Template

Use this table to capture assignments and deadlines during the workshop.

| # | Action Item | Owner | Due Date | Status | Blocks |
|---|-------------|-------|----------|--------|--------|
| A1 | ~~Provide Datawire Sandbox/Cert/Prod base URLs~~ | Fiserv | | Resolved | Development |
| A2 | ~~Confirm static IP whitelisting requirement~~ (No whitelisting required per docs; confirm outbound IP needs) | Fiserv | | Resolved | Development |
| A3 | ~~Provide recommended timeout values~~ (30s ClientTimeout, 45s socket; confirm TOR timing) | Fiserv | | Resolved | Development |
| A4 | Provide test encryption keys (Master Session) | Fiserv | | Open | Development |
| A5 | ~~Confirm TLS version requirement~~ (TLSv1.3 + 1.2, 4 cipher suites) | Fiserv | | Resolved | Development |
| A6 | ~~Confirm PIN encryption method and block format~~ (Master Session + ISO-0; Softpay decision) | Fiserv | | Resolved | Development |
| A7 | ~~Provide test BDKs for DUKPT~~ → Replaced: **Provide test master key for Master Session Encryption** | Fiserv | | Open | Development |
| A8 | Confirm Partial Authorization requirement | Fiserv | | Open | Development |
| A9 | Confirm tipping flow and field placement | Fiserv | | Open | Development |
| A10 | Clarify DCC rate feed provider and process | Fiserv | | Open | Development |
| A11 | Establish communication channel (Slack/Teams/email) | Both | | Open | Development |
| | | | | | |
| | | | | | |
| | | | | | |

**Status values:** Open / In Progress / Resolved / Deferred

---

## Summary of Questions by Blocker Category

| Blocks | Question IDs | Count | Resolved |
|--------|-------------|-------|----------|
| **Development** | Q1.1-Q1.6, Q2.1-Q2.9, Q3.3, Q3.5-Q3.7, Q4.1-Q4.2, Q4.4-Q4.5, Q5.1, Q5.3-Q5.4, Q6.1-Q6.2, Q6.4-Q6.5 | 25 | 10 resolved (Q1.1, Q1.3, Q1.5, Q1.6, Q2.1, Q2.6, Q2.7, Q4.1, Q4.4†), 2 N/A (Q2.8, Q2.9), 2 partial (Q1.2†, Q1.4†) |
| **Certification** | Q6.3 | 1 | 0 |
| **Go-Live** | Q7.4 | 1 | 0 |
| **Total** | | **27** | **12 fully resolved/N/A, 2 partially resolved, 13 remaining** |

† = resolved/partially resolved from SDK documentation (Secure Transport Guide, Datawire Compliance Test Form, sample code)

---

*This document was prepared based on the Fiserv USA Rapid Connect Integration Analysis for project RSO024 (Softpay ApS). All questions reference findings from the analysis of the UMF v15.04 specification, EMV Implementation Guide, Datawire documentation, and RSO024 Project Profile.*
