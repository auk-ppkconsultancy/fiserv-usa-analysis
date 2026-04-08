# Payment Protocol Analysis — Prompt Template for Claude

## Context: Softpay Solution Overview

Softpay is an independent SoftPOS platform that transforms smartphones, tablets, and enterprise devices into certified payment terminals. It runs on Android 8+ and iOS devices with NFC and requires no dedicated hardware. Merchants can use the Softpay app, a white-labelled version, or embed Softpay functionality directly into their own POS systems.

### Key Characteristics

- **Multi-device & multi-acquirer support** — Any Android or iOS device with NFC (Zebra, Sunmi, Elo, iMin, Unitech, etc.) can become a payment terminal. Softpay connects to a network of processors and acquirers and supports deploying additional terminals during peak hours or emergencies.
- **Wide card scheme & wallet acceptance** — Visa, Mastercard, BankAxept, Amex, Discover, Diners Club, Dankort, and other domestic schemes. Digital wallets: Apple Pay, Google Pay, Samsung Pay. Co-badged card scheme selection is supported.
- **Fast transaction flow** — Ready to tap in under one second; contactless transactions complete in under two seconds. Can be preinstalled and used as an emergency POS.
- **Security & compliance** — Certified PIN pad with stable digit positions; card and PIN data handled separately and never stored on device. Backend follows PCI DSS and MPoC standards with attestation-based device integrity checks.
- **Feature set** — Tipping, loyalty programs, DCC, surcharging, multi-store on one device, cancellations, refunds, email receipts, multi-language/currency, end-of-day reports, and full API access.
- **Integration options** — Standalone app, white-label, or embedded. Integration methods: AppSwitch (Android client library), CloudSwitch (backend REST API), Deep Linking, and JavaScript-based app switching (Softpay 2.0).
- **SoftPOS SDK** — Contactless EMV kernels, secure PIN component, attestation client, cloud-hosted backend, REST APIs for merchant/terminal onboarding, and webhooks. Fully pre-certified and acquirer-network-ready.
- **Retailer benefits** — Leverages existing devices to cut hardware costs, enables floor-roaming payments, shortens checkout queues, and integrates with inventory, accounting, and CRM systems.
- **Platform** — Fully managed SaaS with its own payment integrator. Connects to acquirers via ISO 20022/NEXO, ISO 8583, and REST. Supports purchase, refund, cancellation, and payment-with-loyalty flows.
- **Integration models** — Standalone terminal, AppSwitch, or CloudSwitch. AppSwitch is preferred for Android POS apps; CloudSwitch suits merchants without an Android POS app (REST-initiated, JavaScript/browser UI, push-notification wake).
- **Device requirements** — Android 10+ (consumer) or Android 8+ (enterprise), Android 11+ recommended. Requires: post-2021 security patch, built-in NFC, 64-bit ARMv8 CPU, stable internet, GMS certification. Foldable/multi-screen devices not supported.
- **App vs SDK** — App is pre-certified with ready-made flows and no Android dev required. SDK allows full branding/UI customisation but requires integrators to implement flows, error handling, and lifecycle management; may require lab integration review.
- **SDK architecture** — Kotlin library with event-driven flow. Integrators implement all screens except the PIN pad, supply credentials via the Merchant Integration API, and handle transaction states. Webhooks fire on transaction complete, abort, or decline.
- **Merchant onboarding (MIA)** — Partners onboard merchants programmatically: merchant details, store info (MID), terminal data (TID), CVM limits, EMV config, and enabled transaction types. Credentials distributed via email (user ID) and SMS (password). Supports create, update, and release of terminals and stores.

---

## Your Task

You are reviewing a **new payment protocol from an acquirer or processor** for integration with a SoftPOS solution.

> Before beginning, ensure you are using the most recent information available. Use web searches if the documentation appears outdated.

Follow the steps below to prepare a comprehensive assessment.

---

## Step 0 — Mandatory Feature Support Check

Before and throughout the review, **explicitly check whether the target specification supports each feature below**. If a feature is absent, flag it as **"Not supported / Not specified"** and add an **actionable open question** for the workshop. For each supported feature, summarize how it works per the target spec.

**Features to verify and summarize (minimum):**

| Feature | Check |
|---|---|
| Deferred authorizations | Store-and-forward / offline / queued auth flows |
| Tipping | |
| Dynamic Currency Conversion (DCC) | |
| Surcharge | Including scheme rules/constraints |

**Deliverable:** Include a dedicated section titled **"Feature Deep Dives (Target Spec)"** with one mini-spec per feature covering: supported/unsupported status, message flow, required fields, response handling, reconciliation/settlement behavior, edge cases, and test cases to request.

---

## Review Steps

### 1. Protocol & Message Format

- Identify whether the spec uses ISO 8583, SPDH, XML, JSON, or another format.
- Describe the message structure (e.g., header + MTI/bitmap, FID/SFID records).
- Note whether the flow is **single-message** (sale + capture combined) or **dual-message** (authorization + capture).
- Highlight required header/envelope fields (TPDU, protocol version).
- Note any versioning or schemas (e.g., DTD for XML).
- List specific questions about mandatory fields, TLV tags, and maximum field lengths if mappings are ambiguous.

### 2. Supported Card Schemes & Scope

- List all supported card brands (Visa, Mastercard, Amex, Discover, Bancontact, etc.) for card-present and card-not-present flows.
- Note any brands supported only in later phases.
- If a definitive list is absent, explicitly request confirmation of supported schemes.

### 3. Supported Transaction Types

- Identify the core transaction set: purchase/sale, refund, void/cancellation, technical reversal.
- Confirm whether transactions are **referenced** (linked to original sale) or **unreferenced**.
- Check support for: partial authorization, partial capture, pre-authorization/capture, ping/health-check.
- Note whether SoftPOS should disable or enable any of these.
- Determine if the processor supports **multi-host routing** (different card types to different acquirers).

### 4. Integration & Connectivity

- Map all environments: development, sandbox, certification, production.
- Obtain base URLs, IP addresses, and ports for each environment.
- Clarify connection type: public internet, site-to-site VPN, TLS, or mutual TLS.
- Confirm if static IP whitelisting is required.
- Document authentication methods (HTTP basic auth, client certificates) and whether credentials are merchant-specific or platform-wide.
- Identify heartbeat/health-check endpoints and recommended call frequencies.

### 5. Security & Key Management

- Determine PIN encryption method (DUKPT, AES-256, 3DES) and PIN block format (ISO-0 or ISO-4).
- Verify if **online PIN is supported for contactless transactions**.
- List data encryption requirements (e.g., track-2 encryption) and MAC/HMAC algorithm used.
- Clarify whether message authentication can be disabled during testing.
- Identify HSM and key exchange procedures (BDK ordering, KSN counters, test vs production keys) and responsible parties.
- Confirm TLS version requirements and certificate management process.

### 6. EMV & Kernel Data

- Confirm contactless EMV support and any transaction amount limits by country or currency.
- List EMV tags required in requests; confirm track-2 format (ANSI/ISO 7813/ICC); note behavior when a tag is missing.
- Clarify who manages kernel configurations: SoftPOS or acquirer (AID lists, contactless kernel parameters, CA public keys).

### 7. Settlement & Reconciliation

- Identify the settlement model: batch reconciliation, auto-close, single-message capture, or dual-message settlement.
- Clarify what happens to reversals or cancellations after cut-off (and whether the response signals that clearing has occurred).
- Confirm whether network management messages are required (logon/logoff/echo).
- Ask whether settlement files or reports will be provided.

### 8. Merchant Onboarding & PayFac Requirements

- Describe the onboarding process: API (e.g., MIA), file upload, or manual entry.
- Confirm when MID/TID values are assigned and how they are delivered to SoftPOS.
- If payment facilitator (PF) models are supported, list additional fields/identifiers for sub-merchants and clarify settlement and payout handling.
- Determine who is responsible for KYC/KYB checks and what documentation is required.

### 9. Certification & Test Requirements

- Confirm if Level-3 (L3) certification or scheme approval is required and which team owns it.
- Estimate typical timelines based on similar integrations.
- Collect test environment details: availability, Visa/Mastercard simulators, sample test cards, and pre-test scripts.
- Confirm whether certification, QA, and production share a test URL or use separate environments.

### 10. Special Features & Optional Capabilities

- Check support for: tipping, surcharge, cashback, DCC, PAR tokens, tokenization, Apple Pay, Google Pay.
- Note which features are available at launch vs. planned for later.
- Ask whether multi-currency, multiple captures, partial captures, or partial reversals are supported.

### 11. API-Specific Details

- Request an official API specification in YAML or JSON for accurate implementation and automated testing.
- Verify data types, encoding (UTF-8), maximum field lengths, enum values, and error codes.
- Obtain a full list of response codes with descriptions for internal error-handling mapping (note if the host passes through ISO 8583 field 39 codes directly).
- Clarify timeout requirements and connection-reuse policies (e.g., whether multiple terminal requests can share a single HTTPS connection).

### 12. Receipt Requirements & App Branding

- Request any "Receipt Requirements Specification" or formatting guidelines.
- Note whether formatting, language, dynamic descriptors, or branding must be handled by SoftPOS.

### 13. Project Management & Governance

- Estimate timeline and resource requirements based on similar integrations.
- Identify key milestones: analysis → design → development → environment setup → certification → pilot → go-live.
- Clarify roles and responsibilities across teams (integration, DevOps, QA, security, support).
- Align on communication channels and meeting cadence.
- Document technical support contacts and escalation procedures.

### 14. Open Questions & Action Items

- List all unanswered questions to address in the technical workshop.
- Highlight assumptions made, potential risks, and areas requiring further clarification.

---

## Report Format Requirements

- Use **clear headings** for each section above.
- **Bullet lists** are preferred for enumerating features and requirements.
- **Tables** should contain only keywords, short phrases, or values — no long sentences.
- **Cite specific sections** of the protocol documentation for every key fact or requirement.

---

## Softpay Reference Links

| # | Source |
|---|--------|
| [1][2][5][6][8][10][12][13][19] | [Softpay App — softpay.io](https://softpay.io/solutions/softpay-app/) |
| [3][7][18] | [Emergency POS — softpay.io](https://softpay.io/use-cases/emergency-pos/) |
| [4][17] | [A Guide to SoftPOS for Retail — softpay.io](https://softpay.io/a-guide-to-softpos-for-retail/) |
| [9] | [Home — softpay.io](https://softpay.io/) |
| [11][15][16] | [SoftPOS SDK — softpay.io](https://softpay.io/solutions/softpos-sdk/) |
| [14] | [Softpay 2.0 — softpay.io](https://softpay.io/softpay-2-0-0-seamlessly-integrate-payments-your-way/) |
