# Fiserv Rapid Connect — Java Code Examples

Reference implementation for the RSO024 (Softpay) integration with Fiserv Rapid Connect via Datawire Secure Transport.

**These are sample/reference files, not a production library.** They demonstrate the correct XML structures, field ordering, and protocol flows documented in the analysis.

## Quick Start

```bash
cd java-examples
javac -d out *.java
java -cp out com.softpay.fiserv.FiservIntegrationRunner          # dry-run
java -cp out com.softpay.fiserv.FiservIntegrationRunner --live    # staging
java -cp out com.softpay.fiserv.FiservIntegrationRunner --live --did=YOUR_DID
```

**Dry-run mode** (default) prints all XML payloads and Datawire envelopes without making network calls. Use this to inspect message structure.

**Live mode** (`--live`) connects to Fiserv staging (`stg.dw.us.fdcnet.biz`). Requires the MID/TID to be provisioned by Fiserv.

**Pre-registered DID** (`--did=...`) skips SRS registration/activation and reuses an existing Device ID.

## Files

| File | Purpose |
|---|---|
| `FiservIntegrationRunner.java` | Runnable entry point. Executes the full lifecycle: SRS discovery/registration/activation, then five transaction flows (Auth+Completion, Auth+Void, Timeout Reversal, Referenced Refund, Unreferenced Refund). Contains test card numbers and simulated responses for dry-run mode. |
| `DatawireClient.java` | Datawire Secure Transport client. Handles service discovery, SRS registration/activation, transaction envelope wrapping (CDATA), AuthKey/ClientRef construction, HTTP POST with timeouts, and response parsing with retry logic. |
| `FiservUmfMessageBuilder.java` | UMF XML message builder. Produces spec-compliant GMF XML for Authorization, Completion, Void/Reversal, Refund, DCC, PIN Debit, and Encryption Key Request. Handles CommonGrp, CardGrp, EMVGrp, PINGrp, DCCGrp, AddtlAmtGrp, OrigAuthGrp, and scheme-specific groups (VisaGrp/MCGrp/DSGrp/AmexGrp). |
| `FiservResponseParser.java` | UMF XML response parser. Extracts RespCode, AuthID, scheme-specific references (Visa TransID, MC BanknetData, etc.), EMV response data, and all fields needed for OrigAuthGrp in subsequent messages. |
| `EmvTlvParser.java` | EMV TLV hex string parser/builder for the EMVGrp.EMVData field. Handles 1/2-byte tags, BER-TLV length encoding, and Fiserv's 4-char length prefix convention. |

## Transaction Flows Demonstrated

### Flow 1: Authorization + Completion (tip-before-auth, Softpay preferred)

```
Visa contactless tap  -->  App shows tip screen
                              |
                              v  Customer adds $8 tip
                              |
                           Authorization ($58.00 = $50 + $8 tip)
                              |
                              v  (store AuthID, STAN, ResponseDate, VisaTransID)
                              |
                           Completion ($58.00 — same amount)
                              |  - New STAN, same RefNum
                              |  - Auth and Completion amounts match
                              |  - No 20% tolerance needed, works for any MCC
                              v
                           Captured
```

### Flow 2: Authorization + Void

```
Visa contactless tap  -->  Authorization ($75.00)
                              |
                              v  (store AuthID, STAN, ResponseDate, VisaTransID)
                           Merchant cancels
                              |
                              v
                           Void (ReversalRequest)
                              |  - New STAN, same RefNum
                              |  - ReversalInd="Void"
                              |  - OrigAuthGrp echoes Auth response fields
                              |  - VisaGrp.TransID echoed (NOT exempt for Void)
                              v
                           Voided
```

### Flow 3: Timeout Reversal (TOR)

```
Visa contactless tap  -->  Authorization ($25.00)
                              |
                              v  TIMEOUT (no response received)
                              |
                              v
                           TOR (ReversalRequest)
                              |  - New STAN, same RefNum
                              |  - ReversalInd="Timeout"
                              |  - OrigAuthGrp: ONLY OrigSTAN, OrigLocalDateTime,
                              |    OrigTranDateTime (no AuthID/RespCode — no response)
                              |  - Scheme-specific fields EXEMPT (2026 change)
                              v
                           Reversed (or no-op if original was not processed)
```

### Flow 4: Referenced Refund

```
(After auth + completion)  -->  Refund ($40.00)
                                   |  - CreditRequest with TxnType=Refund
                                   |  - NEW RefNum (not original auth's)
                                   |  - OrigAuthGrp links to original auth
                                   |  - Scheme echo fields from original response
                                   v
                                Refunded
```

### Flow 5: Unreferenced Refund

```
(No original reference)   -->  Refund ($15.00)
                                  |  - CreditRequest with TxnType=Refund
                                  |  - No OrigAuthGrp
                                  |  - For MasterCard: RefundType mandatory (2026)
                                  v
                               Refunded
```

## Datawire Lifecycle (SRS)

Before any transaction, the terminal must be registered with Datawire:

```
1. Service Discovery   GET  /sd/srsxml.rc    -->  SRS endpoint URL
2. Registration        POST (empty DID)       -->  DID + transaction URLs
3. Activation          POST (with DID)        -->  DID activated
4. Transactions        POST (with DID)        -->  UMF payload in CDATA envelope
```

The DID is permanent once activated. Store it and reuse it — never re-register.

## Key Protocol Rules

| Rule | Detail |
|---|---|
| **STAN** | New 6-digit number for every message (Auth, Completion, Void, TOR). Unique per day per MID+TID. |
| **RefNum** | Same across a transaction chain (Auth -> Completion -> Void -> TOR). **New** for Referenced Refund and Unreferenced Refund. |
| **OrigAuthGrp** | Mandatory in Completion, Void, TOR, and Referenced Refund. Echoes AuthID, ResponseDate, STAN, LocalDateTime, TrnmsnDateTime, RespCode from the Authorization response. For TOR: only OrigSTAN/OrigLocalDateTime/OrigTranDateTime (no response fields). |
| **Scheme echo fields** | Visa TransID, MC BanknetData, Amex AmExTranID, Discover DiscNRID must be echoed in Completion and Void. **Exempt** for Timeout Reversals (TOR) per 2026 UMF changes. |
| **AuthKey** | `GroupID + MerchID \| TermID` (e.g., `20001RCTST1000119069\|00000001`) |
| **ClientRef** | 14 chars: `tttttttVxxxxxx` (7-digit counter + "V" + TPPID) |
| **ClientTimeout** | 30s (staging: 15-40s, production: 15-35s). App read timeout must exceed this (recommended: 45s). |
| **Payload encoding** | CDATA (default). Nested CDATA sections are invalid. |
| **Max message size** | 14,336 bytes |
| **No empty XML tags** | Both `<Elem></Elem>` and `<Elem/>` are invalid per Fiserv. |

## Test Card Numbers

From `RSO024_Testscript_2026-04-07.xlsx`:

| Brand | PAN | Track2 | Approval Range |
|---|---|---|---|
| Visa | 4111111111111111 | 4111111111111111=25121011000012345678 | $1.02 - $131.85 |
| Visa | 4005520000000947 | 4005520000000947=25121011000012300000 | $413.34 - $572.09 |
| MasterCard | 5204242750270010 | 5204242750270010=2512101100000123456 | $112.18 - $144.26 |
| MasterCard | 5424180273333333 | 5424180273333333=2512101100000123456 | $835.41 - $2413.30 |
| Discover | 6504840209544524 | 6504840209544524=25121011000012345678 | $131.87 - $144.74 |
| Amex | 370295571160496 | 370295571160496=251210107108069000000 | $111.22 - $145.22 |

## Known Limitations

1. **Scheme-specific echo fields not passed in Completion/Void.**
   `FiservUmfMessageBuilder.buildCompletion()` and `buildVoidFullReversal()` call `writeSchemeGroup()` but do not accept or write the echo fields (Visa TransID, MC BanknetData, Amex AmExTranID, Discover DiscNRID). The scheme group is written empty. In production, Fiserv will reject Completions and Voids without these fields. See the TODO comments in the source for where to add them.

2. **No TLS certificate validation customization.**
   `DatawireClient` uses the JVM's default trust store. For production, ensure the Fiserv/Datawire root CA certificates are in the trust store. Certificate pinning is discouraged by Fiserv.

3. **Simulated EMV data.**
   `FiservIntegrationRunner.buildSampleEmvData()` generates plausible but non-cryptographic EMV TLV data. Real EMV data comes from the contactless kernel after a tap. The test host may accept or ignore the cryptogram.

4. **Single-threaded STAN counter.**
   The `stanCounter` in `FiservIntegrationRunner` is not thread-safe. In production, STAN must be unique per day per MID+TID and persisted across restarts.

5. **`java.net.URL` constructor deprecation warning.**
   `DatawireClient` uses `new URL(...)` which is deprecated in Java 20+. Replace with `URI.create(...).toURL()` for production.

## Configuration (RSO024)

| Parameter | Value |
|---|---|
| TPP ID | RSO024 |
| Group ID | 20001 |
| MID (Restaurant) | RCTST1000119068 |
| MID (Retail/QSR) | RCTST1000119069 |
| MID (Supermarket) | RCTST1000119070 |
| Terminal ID (Cert) | 00000001 |
| Service ID | 160 |
| Staging URL | `https://stg.dw.us.fdcnet.biz/rc` |
| Staging SRS | `https://stg.dw.us.fdcnet.biz/sd/srsxml.rc` |
| Production URL | `https://prod.dw.us.fdcnet.biz/rc` |

## Related Documentation

| Document | Description |
|---|---|
| [FISERV_USA_ANALYSIS.md](../FISERV_USA_ANALYSIS.md) | Full protocol analysis (message format, fields, groups, rules) |
| [TRANSACTION_FLOWS.md](../TRANSACTION_FLOWS.md) | Sequence diagrams and field echo tables for all flows |
| [RESPONSE_CODES.md](../RESPONSE_CODES.md) | Response code reference with error handling decision tree |
| [PIN_DEBIT_GUIDE.md](../PIN_DEBIT_GUIDE.md) | Master Session Encryption, PIN block, PINGrp mapping |
| [POS_ENTRY_MODES.md](../POS_ENTRY_MODES.md) | POS Entry Mode decision tree for SoftPOS |
| [TEST_CASE_INDEX.md](../TEST_CASE_INDEX.md) | Certification test case index (424 mandatory + 83 optional) |
