# Fiserv USA Rapid Connect — Integration Analysis & Documentation

**Project:** RSO024 — Softpay by Softpay ApS  
**Protocol:** Fiserv Rapid Connect UMF v15.04.5  
**Region:** North America (US) — USD  
**Date:** April 2026

---

## Deliverables

| # | Document | Description |
|---|---|---|
| 1 | [FISERV_USA_ANALYSIS.md](FISERV_USA_ANALYSIS.md) | Comprehensive protocol analysis covering all 15 review sections: message format, card schemes, transaction types, connectivity, security, EMV, settlement, onboarding, certification, special features, API details, receipts, project management, and 40+ open questions |
| 2 | [RESPONSE_CODES.md](RESPONSE_CODES.md) | Complete response code reference with error handling decision tree, retry logic, and Softpay-specific terminal-level handling guidance |
| 3 | [response_codes.json](response_codes.json) | Machine-readable JSON with 326 response codes — each with category, retryable flag, recommended action, and merchant/cardholder messages |
| 4 | [TRANSACTION_FLOWS.md](TRANSACTION_FLOWS.md) | Sequence diagrams for all transaction flows (Auth+Completion, Void, TOR, Refund, DCC, Digital Wallet, PIN Debit, Key Exchange) with field echo tables and STAN/RefNum management rules |
| 5 | [POS_ENTRY_MODES.md](POS_ENTRY_MODES.md) | POS Entry Mode decision tree for SoftPOS, Terminal Category Codes, digital wallet detection (Apple Pay / Google Pay / Samsung Pay), Card Type BIN determination, and complete code reference tables |
| 6 | [PIN_DEBIT_GUIDE.md](PIN_DEBIT_GUIDE.md) | Master Session Encryption lifecycle, PIN block construction (ISO Format 0), PINGrp field mapping, online PIN vs. CDCVM decision tree, PINless POS Debit, and MPoC considerations |
| 7 | [TEST_CASE_INDEX.md](TEST_CASE_INDEX.md) | All 424 mandatory + 83 non-mandatory test cases parsed and categorized by brand, type, entry mode, industry, and feature — with a prioritized smoke test subset and certification stage checklist |
| 8 | [WORKSHOP_QUESTIONS.md](WORKSHOP_QUESTIONS.md) | 46 open questions grouped by topic with context, assumptions, blocker classification, a time-boxed 2-hour workshop agenda, and action item tracking template |

## Java Code Examples

| File | Description |
|---|---|
| [java-examples/FiservIntegrationRunner.java](java-examples/FiservIntegrationRunner.java) | **Runnable** end-to-end demo: Datawire SRS lifecycle (discover → register → activate), Credit Authorization ($50 Visa), Completion with $8 tip, and Void — dry-run mode prints all XML; `--live` connects to staging |
| [java-examples/DatawireClient.java](java-examples/DatawireClient.java) | Datawire Secure Transport client: service discovery, SRS registration/activation, transaction envelope wrapping (CDATA), AuthKey/ClientRef construction, HTTP POST, response parsing, retry logic |
| [java-examples/FiservUmfMessageBuilder.java](java-examples/FiservUmfMessageBuilder.java) | UMF XML message builder for Authorization, Completion, Void/Reversal, Refund, DCC, PIN Debit, and Encryption Key Request — demonstrates Track2, EMV TLV, PINGrp, DCCGrp, and scheme-specific group population |
| [java-examples/FiservResponseParser.java](java-examples/FiservResponseParser.java) | StAX parser extracting approval/decline, OrigAuthGrp fields, scheme-specific references, and EMV response data — with full dual-message flow example |
| [java-examples/EmvTlvParser.java](java-examples/EmvTlvParser.java) | EMV TLV hex string parser and builder for the EMVGrp.EMVData field — handles 1/2-byte tags, BER-TLV length encoding, and Fiserv's 4-char length prefix |

## Project Configuration

| Attribute | Value |
|---|---|
| **Project ID / TPP ID** | RSO024 |
| **Company** | Softpay ApS (SOF006) |
| **Industries** | Restaurant, Retail/QSR, Supermarket |
| **Card Brands** | Visa, Mastercard, Amex, Discover, Diners Club |
| **Entry Modes** | Contactless (NFC), Swiped, EMV |
| **Digital Wallets** | Apple Pay, Google Pay, Samsung Pay |
| **Debit** | PIN Debit, PINless POS Debit |
| **Special Features** | DCC, Tipping, Master Session Encryption, TR-31 Key Block, SoftPOS Terminal Category |
| **Settlement** | Host Capture (dual-message: Authorization + Completion) |
| **Transport** | Datawire Secure Transport (XML over HTTPS) |
| **Test MIDs** | RCTST1000119068 (Restaurant), RCTST1000119069 (Retail/QSR), RCTST1000119070 (Supermarket) |

## Source Documents

| Document | Purpose |
|---|---|
| UMF_RSO024_2026.04.07.pdf | Main UMF protocol specification (v15.04.5) |
| UMF_XML_SCHEMA.xsd | XML schema definition (6,247 lines) |
| Fiserv_Generic_EMV_Implementation_Guide_v2025_RG20_081225.pdf | EMV implementation requirements |
| 2026 UMF Changes-1.pdf | UMF v15.04.5 change log |
| Fiserv USA RapidConnect Project Profile.pdf | Project configuration |
| Rapid Connect Portal Definitions.pdf | Portal field definitions (v1.85) |
| The_Datawire_Solution.pdf | Datawire connectivity options |
| RSO024_Testscript_2026-04-07.xlsx | 424 mandatory + 83 non-mandatory certification test cases |
| TestTransactions_RSO024.xlsx | Sample XML test payloads |
| Secure_Transport_Guide/ | Datawire integration guide, SRS docs, TLS ciphers, Root CA certs, parameter guidelines, schemas |
| RCToolkitSampleCode/ | Java/C# sample code: HTTP POST, SOAP, TCP/IP handlers, Credit/Debit request builders |
| Datawire Compliance Test Form (v3.4.7) | Datawire Secure Transport compliance certification (separate from Rapid Connect Full-Cert) |
| Datawire Re-Certification Script (v1.7) | Expedited re-certification for minor updates |
| Quick Reference Guides | EMV, Restaurant, Retail/QSR, Supermarket, Tokenization, TOR Testing |
