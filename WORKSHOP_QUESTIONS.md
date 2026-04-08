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
- **Assumption:** MAC is not required since MAC Master Session and MAC DUKPT are both deselected in the project profile.
- **Blocks:** Development

### Q2.5 — HSM integration requirements

- **Question:** What HSM integration is required on the Softpay side for Master Session Encryption and TR-31 key block handling?
- **Context:** Softpay needs to determine whether a cloud HSM (e.g., AWS CloudHSM, Azure Dedicated HSM) is required or if software-based key management is acceptable.
- **Assumption:** Softpay will use a cloud HSM for production key storage and session key operations.
- **Blocks:** Development

### Q2.6 — PIN encryption method preference

- **Question:** What PIN encryption method does Fiserv require or recommend for SoftPOS -- DUKPT, AES-DUKPT, or Master Session Encryption?
- **Context:** PIN Debit is selected in the project profile, requiring Softpay to implement the PINGrp; the encryption method determines the key management architecture.
- **Assumption:** DUKPT (3DES) as the most common method for card-present environments; Softpay's MPoC-certified PIN pad supports this.
- **Blocks:** Development (PIN Debit)

### Q2.7 — PIN block format

- **Question:** What PIN block format is required -- ISO Format 0 (ISO 9564-1) or ISO Format 4 (AES-based)?
- **Context:** The PIN block format determines the encryption algorithm and impacts the certified PIN pad configuration in Softpay's SoftPOS SDK.
- **Assumption:** ISO Format 0 (most widely used in US card-present environments).
- **Blocks:** Development (PIN Debit)

### Q2.8 — BDK provisioning for DUKPT

- **Question:** How are Base Derivation Keys (BDKs) provisioned for SoftPOS devices? Are test BDKs available for Sandbox/Certification? What is the provisioning process at scale?
- **Context:** Each SoftPOS device needs a unique initial PIN encryption key derived from the BDK; the provisioning process must work at scale for thousands of devices.
- **Assumption:** Fiserv provides a test BDK for development; production BDKs are provisioned through a secure key injection ceremony.
- **Blocks:** Development (PIN Debit)

### Q2.9 — KSN counter management

- **Question:** How should the DUKPT Key Serial Number (KSN) counter be managed across device reinstalls, app updates, and device resets on SoftPOS?
- **Context:** Unlike hardware terminals, SoftPOS apps can be reinstalled or reset, which may cause KSN counter reuse -- a critical security issue for DUKPT.
- **Assumption:** Softpay will persist the KSN counter server-side and synchronize on app initialization to prevent reuse.
- **Blocks:** Development (PIN Debit)

### Q2.10 — Online PIN for contactless transactions

- **Question:** Is online PIN supported for contactless transactions on SoftPOS/COTS devices, or is it restricted to contact chip only?
- **Context:** PIN Debit is selected, and for contactless transactions above the CVM limit, CDCVM is the primary mechanism; Softpay needs to know if online PIN is also expected for contactless debit.
- **Assumption:** Contactless transactions above CVM limit use CDCVM (for digital wallets) or online PIN (for physical debit cards); Softpay's MPoC-certified PIN pad handles the online PIN scenario.
- **Blocks:** Development (PIN Debit)

---

## 3. EMV & Contactless Configuration

*Blocks: EMV implementation and contactless transaction processing.*

### Q3.1 — EMV contact chip in project profile

- **Question:** EMV (contact chip / dip) is selected as an entry mode in the RSO024 project profile, but SoftPOS devices have no chip card reader. Should this be deselected, or is it intentional (e.g., for fallback scenarios)?
- **Context:** If contact chip remains selected, Fiserv may include contact chip test cases in certification that Softpay cannot execute; deselecting it avoids unnecessary test scope.
- **Assumption:** This is a configuration oversight and should be deselected; Softpay will request removal from the project profile.
- **Blocks:** Development / Certification

### Q3.2 — CAPK distribution method

- **Question:** How are CA Public Key (CAPK) files distributed -- via a download API, through the Rapid Connect portal, or another mechanism?
- **Context:** Softpay manages its own EMV contactless kernels and needs to keep CAPKs current; the EMV Guide mandates regular CAPK updates (RQ 5700).
- **Assumption:** CAPKs are downloaded from a Fiserv-hosted endpoint; Softpay will integrate an automated download process.
- **Blocks:** Development

### Q3.3 — CAPK update frequency

- **Question:** What is the expected CAPK update frequency, and is there a notification mechanism when new CAPKs are published?
- **Context:** Softpay needs to schedule CAPK refresh cycles and handle mid-lifecycle key rotations without service interruption.
- **Assumption:** CAPKs are updated quarterly; Softpay will check for updates weekly.
- **Blocks:** Development

### Q3.4 — Kernel configuration notification (RQ 0600)

- **Question:** Does Fiserv require notification or approval of the contactless kernel configurations used by Softpay (per EMV Implementation Guide RQ 0600)?
- **Context:** RQ 0600 states that kernel configurations may need to be reported; Softpay needs to know if this is a formal approval step or informational only.
- **Assumption:** Softpay will document kernel configurations and share them with Fiserv during the certification review stage.
- **Blocks:** Certification

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

---

## 4. Transaction Processing

*Blocks: Dual-message implementation and core transaction flows.*

### Q4.1 — Sale transaction type intentionally excluded

- **Question:** The Sale transaction type (single-message auth+capture) is NOT selected in the project profile. Can Fiserv confirm this is intentional and that all purchase transactions should use the dual-message Authorization + Completion flow?
- **Context:** Dual-message is consistent with the tipping workflow (auth pre-tip, complete with tip), but for non-tipping scenarios (Retail/QSR, Supermarket), single-message Sale would be simpler and faster.
- **Assumption:** All purchases use Authorization + Completion; Softpay will implement accordingly but may request Sale be added in a future phase.
- **Blocks:** Development

### Q4.2 — Partial Authorization requirement

- **Question:** Is Partial Authorization required for card-present SoftPOS environments? If so, it needs to be enabled in the project profile (currently not selected).
- **Context:** The Portal Definitions state that "Partial Authorization support is generally required for all Merchants in card-present environments" to handle prepaid and debit cards with limited funds; PIN Debit and PINless POS Debit are both selected.
- **Assumption:** Partial Authorization is required and should be added to the project profile; Softpay will implement split-tender handling.
- **Blocks:** Development

### Q4.3 — Void timing and post-cut-off behavior

- **Question:** Voids must be submitted within 25 minutes of the original transaction (per UMF Spec Appendix D). What happens if a Void is submitted after the daily cut-off? Does the response indicate that clearing has already occurred?
- **Context:** Softpay needs to handle the edge case where a merchant attempts to void a transaction that has already been settled; the app must guide the merchant to process a refund instead.
- **Assumption:** A Void submitted after cut-off will be declined with a response code indicating settlement has occurred; Softpay will then prompt the merchant to process a refund.
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

- **Question:** Confirm the tipping flow: Authorization (pre-tip amount) followed by Completion (with tip added to TxnAmt). Is this the recommended approach, or does Fiserv expect a different mechanism?
- **Context:** "Tip Amount" is selected in the project profile and described as a "Reporting field from Rapid Connect to CLX for Quick Service Restaurant industry only," which suggests it may be reporting-only rather than a transactional field.
- **Assumption:** Tipping works via the dual-message flow: auth for pre-tip amount, Completion for total amount including tip. The tip delta is reflected in the TxnAmt difference.
- **Blocks:** Development (Restaurant)

### Q5.2 — Tip percentage cap

- **Question:** Is there a percentage cap on the tip amount relative to the original authorization (e.g., 20%, 50%, no limit)?
- **Context:** Card brands typically allow tip adjustments up to a certain percentage above the authorized amount; Softpay needs to enforce this limit in the app to avoid declines.
- **Assumption:** Tip can be up to 25% above the original authorization amount (common Visa/MC rule); Softpay will enforce this in the UI.
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

### Q7.1 — OpenBatch/CloseBatch requirement under Host Capture

- **Question:** With Host Capture selected, does Softpay need to send OpenBatch/CloseBatch transactions, or does Fiserv handle batch management entirely?
- **Context:** Batch Open and Batch Close are not selected in the project profile, but Softpay needs to confirm that no batch operations are required from the terminal side.
- **Assumption:** No batch operations required from Softpay; Fiserv manages settlement automatically via Host Capture.
- **Blocks:** Development

### Q7.2 — Settlement reports and confirmations

- **Question:** How does Softpay receive settlement reports or confirmations from Fiserv? Is there an API, SFTP delivery, or portal access?
- **Context:** Softpay needs to reconcile transactions on the merchant's behalf and provide end-of-day settlement summaries.
- **Assumption:** Settlement data is available through the Rapid Connect portal; Softpay will reconcile based on its own transaction records.
- **Blocks:** Go-Live

### Q7.3 — Daily settlement cut-off time

- **Question:** What is the daily settlement cut-off time (in ET or UTC)?
- **Context:** Softpay needs to communicate settlement timing to merchants and enforce Completion deadlines (e.g., tip adjustments must be submitted before cut-off).
- **Assumption:** Cut-off is at midnight ET (Eastern Time) per standard Fiserv processing.
- **Blocks:** Go-Live

### Q7.4 — Settlement file delivery method

- **Question:** Are settlement files or detailed transaction reports delivered to Softpay via SFTP, API, or another mechanism?
- **Context:** Automated reconciliation requires machine-readable settlement data; Softpay's reporting infrastructure needs to ingest this data programmatically.
- **Assumption:** Softpay will reconcile from its own transaction logs and use the Rapid Connect portal for exception investigation.
- **Blocks:** Go-Live

---

## 8. Merchant Onboarding

*Blocks: Scaling beyond pilot. Critical for operationalizing the integration at volume.*

### Q8.1 — Production MID/TID assignment

- **Question:** How are production MIDs and TIDs assigned for each Softpay merchant? Is this a manual process or is there an automated provisioning flow?
- **Context:** Softpay onboards merchants programmatically via its Merchant Integration API (MIA); the MID/TID assignment process must align with Softpay's automated onboarding.
- **Assumption:** MID/TID assignment is a manual process through the Fiserv relationship manager; Softpay will need to coordinate onboarding for each new merchant.
- **Blocks:** Go-Live

### Q8.2 — API for programmatic merchant boarding

- **Question:** Is there an API or automated process for programmatic merchant boarding, similar to Softpay's MIA (Merchant Integration API)?
- **Context:** Softpay needs to scale to thousands of merchants without manual intervention for each onboarding; an API-based flow is essential for the self-service model.
- **Assumption:** No API exists; Softpay will work with Fiserv to establish a batch onboarding process for scale.
- **Blocks:** Go-Live

### ~~Q8.3 — Datawire DID management at scale~~ PARTIALLY RESOLVED

- **Answer:** Per the Secure Transport Guide: Each MID/TID/ServiceID/App combination gets a unique DID via SRS. No bulk provisioning API exists — SRS must be performed individually per combination. DID must be stored securely and permanently; it cannot be reused across different MID/TID combinations. Fiserv *"strongly recommends an automated process for SRS that can be easily re-triggered as needed"* — manual SRS (developer-crafted XML) is explicitly discouraged. Activation must complete within a limited time period or the DID is auto-deactivated and SRS must restart.
- **Remaining questions:** (1) Are there limits on the number of active DIDs per merchant/group? (2) Is there a DID expiry or renewal requirement? (3) For Softpay's model where one MID may have thousands of TIDs (one per device), is the per-TID SRS the correct model, or should Softpay use a single TID per MID with session/device differentiation?
- **Blocks:** Go-Live

### Q8.4 — KYC/KYB requirements and responsibility

- **Question:** What KYC/KYB documentation is required for merchant onboarding, and who is responsible -- Softpay, Fiserv, or the merchant's acquiring bank?
- **Context:** Softpay needs to understand the compliance requirements for US merchant onboarding to build the correct document collection flow.
- **Assumption:** KYC/KYB is handled by the acquiring bank or Fiserv; Softpay collects and forwards documentation as needed.
- **Blocks:** Go-Live

---

## 9. Certification

*Blocks: Certification stage. These questions should be answered before development concludes.*

### Q9.1 — Estimated Full-Cert timeline

- **Question:** What is the estimated timeline for Full-Cert completion based on similar SoftPOS or mobile POS integrations Fiserv has certified?
- **Context:** Softpay estimates 6-9 months end-to-end (analysis through go-live); certification timeline directly impacts the go-live date commitment.
- **Assumption:** Certification (Stage 2 through Stage 4) takes 6-12 weeks after development is complete, based on typical payment integration timelines.
- **Blocks:** Certification

### Q9.2 — MPoC/EMV certification acceptance

- **Question:** Does Softpay's existing MPoC certification and EMV L1/L2 certification satisfy Fiserv's card brand certification requirements (per EMV Guide RQ 0800), or is additional card brand-specific certification needed?
- **Context:** Softpay is already MPoC-certified with pre-certified contactless EMV kernels; re-certification through Fiserv would add significant time and cost.
- **Assumption:** Softpay's existing MPoC/L1/L2 certifications are accepted; Softpay will provide certification documentation during the Review stage.
- **Blocks:** Certification

### Q9.3 — Visa/MC contactless simulators

- **Question:** Are Fiserv-hosted Visa and Mastercard contactless simulators available for development testing, or must Softpay use its own test card infrastructure?
- **Context:** Contactless EMV testing requires specific test card profiles that exercise all code paths (ARQC generation, CDCVM, below/above CVM limit); simulators accelerate development.
- **Assumption:** Softpay will use its own test card infrastructure and the test PANs provided in the SDK.
- **Blocks:** Development

### Q9.4 — Sandbox availability

- **Question:** Is the Sandbox environment available 24/7, or are there scheduled maintenance windows?
- **Context:** Softpay's development team spans multiple time zones and may test outside US business hours.
- **Assumption:** Sandbox is available 24/7 with occasional maintenance windows communicated in advance.
- **Blocks:** Development

### Q9.5 — L3 scheme certification

- **Question:** Is a separate Level 3 (L3) scheme certification required for each card brand, or does Fiserv's Rapid Connect certification cover scheme-level approval?
- **Context:** L3 certification can add months to the timeline; Softpay needs to plan accordingly if separate scheme certifications are required.
- **Assumption:** Fiserv's Full-Cert covers scheme-level requirements; no separate L3 certification is needed.
- **Blocks:** Certification

### ~~Q9.6 — Environment credential separation~~ PARTIALLY RESOLVED

- **Answer:** Per the Secure Transport Guide: Staging and Production use completely separate infrastructure (URLs, SRS hosts, DIDs). Staging: `stg.dw.us.fdcnet.biz` + `stagingsupport.datawire.net`. Production: `prod.dw.us.fdcnet.biz` + `support.datawire.net`. Hard-coded parameters (App=RAPIDCONNECTSRS, ServiceID=160) are environment-agnostic. AuthKey1/AuthKey2 (GroupID+MID / TID) are account-specific, not environment-specific — the same MID/TID values would need separate SRS in each environment. Datawire compliance certification is valid for 2 years and requires re-engagement for review.
- **Remaining question:** Do the test MIDs (RCTST1000119068-070) work in both staging (dev) and staging (cert), or are cert-phase MIDs different?
- **Blocks:** Certification

---

## Proposed Workshop Agenda

Suggested duration: **2 hours**. Adjust based on attendee availability.

| Time | Duration | Topic | Section | Presenter |
|------|----------|-------|---------|-----------|
| 0:00 | 5 min | Introductions and agenda review | -- | Both |
| 0:05 | 5 min | Softpay SoftPOS platform overview | -- | Softpay |
| 0:10 | 5 min | RSO024 project profile walkthrough | -- | Fiserv |
| 0:15 | 15 min | **Connectivity & Environments** (Q1.1-Q1.6) | Section 1 | Both |
| 0:30 | 20 min | **Security & Key Management** (Q2.1-Q2.10) | Section 2 | Both |
| 0:50 | 15 min | **EMV & Contactless Configuration** (Q3.1-Q3.6) | Section 3 | Both |
| 1:05 | 10 min | **Transaction Processing** (Q4.1-Q4.5) | Section 4 | Both |
| 1:15 | 10 min | **Tipping** (Q5.1-Q5.4) | Section 5 | Both |
| 1:25 | 10 min | **DCC** (Q6.1-Q6.5) | Section 6 | Both |
| 1:35 | 5 min | **Settlement & Reconciliation** (Q7.1-Q7.4) | Section 7 | Both |
| 1:40 | 5 min | **Merchant Onboarding** (Q8.1-Q8.4) | Section 8 | Both |
| 1:45 | 5 min | **Certification** (Q9.1-Q9.6) | Section 9 | Both |
| 1:50 | 10 min | Action items recap and next steps | -- | Both |

**Note:** Sections 1-3 (Connectivity, Security, EMV) are prioritized as they block development start. If time runs short, Sections 7-9 (Settlement, Onboarding, Certification) can be deferred to a follow-up call as they block later project phases.

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
| A6 | Confirm PIN encryption method and block format | Fiserv | | Open | Development |
| A7 | Provide test BDKs for DUKPT | Fiserv | | Open | Development |
| A8 | Clarify online PIN for contactless on SoftPOS | Fiserv | | Open | Development |
| A9 | Deselect EMV contact chip from project profile (if confirmed) | Fiserv | | Open | Development |
| A10 | Confirm Partial Authorization requirement | Fiserv | | Open | Development |
| A11 | Confirm tipping flow and field placement | Fiserv | | Open | Development |
| A12 | Clarify DCC rate feed provider and process | Fiserv | | Open | Development |
| A13 | Share Softpay MPoC/L1/L2 certification docs | Softpay | | Open | Certification |
| A14 | Share Softpay kernel configuration documentation | Softpay | | Open | Certification |
| A15 | Establish communication channel (Slack/Teams/email) | Both | | Open | Development |
| A16 | Schedule follow-up call for Settlement/Onboarding/Certification | Both | | Open | Go-Live |
| | | | | | |
| | | | | | |
| | | | | | |

**Status values:** Open / In Progress / Resolved / Deferred

---

## Summary of Questions by Blocker Category

| Blocks | Question IDs | Count | Resolved |
|--------|-------------|-------|----------|
| **Development** | Q1.1-Q1.6, Q2.1-Q2.10, Q3.1-Q3.3, Q3.5-Q3.6, Q4.1-Q4.5, Q5.1-Q5.4, Q6.1-Q6.2, Q6.4-Q6.5, Q7.1 | 33 | 6 resolved (Q1.1, Q1.3, Q1.5, Q1.6, Q2.1, Q4.4†), 2 partial (Q1.2†, Q1.4†) |
| **Certification** | Q3.4, Q6.3, Q9.1-Q9.2, Q9.5-Q9.6 | 6 | 1 partial (Q9.6†) |
| **Go-Live** | Q7.2-Q7.4, Q8.1-Q8.4 | 7 | 1 partial (Q8.3†) |
| **Total** | | **46** | **8 fully resolved, 4 partially resolved** |

† = resolved/partially resolved from SDK documentation (Secure Transport Guide, Datawire Compliance Test Form, sample code)

---

*This document was prepared based on the Fiserv USA Rapid Connect Integration Analysis for project RSO024 (Softpay ApS). All questions reference findings from the analysis of the UMF v15.04 specification, EMV Implementation Guide, Datawire documentation, and RSO024 Project Profile.*
