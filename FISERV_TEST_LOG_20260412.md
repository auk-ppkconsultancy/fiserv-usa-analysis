# Fiserv Rapid Connect — Staging Test Log

**Dates:** 2026-04-12 (initial), 2026-04-13 (extended probe + test-script MID re-run)
**Project:** RSO024 (Softpay ApS)
**Environment:** Staging (`stg.dw.us.fdcnet.biz`)
**Issue:** RespCode 109 "INVALID TERM" on all authorization attempts.

## 2026-04-13 update — New test-script MIDs also return 109 INVALID TERM from BUYPASS

We re-ran `FullProbe` (SRS register + activate + auth) and `TxnOnlyProbe` (auth only, with DID reuse) against the **test-script MIDs** from `TestTransactions_RSO024.csv`, replaying test case **TC 200376490010** (Visa Contactless Authorization) byte-for-byte. Results below.

### SRS layer — all three MIDs registered and activated cleanly

| MID | MCC | DID | Status |
|---|---|---|---|
| `RCTST1000120415` | 5399 (Retail) | `00068870522555872903` | Registered + Activated OK |
| `RCTST1000120416` | 5411 (Supermarket) | `00068870579979596967` | Registered + Activated OK |
| `RCTST1000120414` | 5812 (Restaurant) | `00068870490417436045` | Registered + Activated OK |

### Transaction layer — all three return 109 INVALID TERM from BUYPASS

Actual payload returned (all three MIDs give identical shape):

```xml
<CreditResponse>
  <CommonGrp>
    <PymtType>Credit</PymtType>
    <TxnType>Authorization</TxnType>
    <MerchID>RCTST1000120415</MerchID>
    <TxnAmt>000000000314</TxnAmt>
    <TxnCrncy>840</TxnCrncy>
    ...
  </CommonGrp>
  <VisaGrp>
    <TransID>2314885530818453</TransID>
  </VisaGrp>
  <RespGrp>
    <RespCode>109</RespCode>
    <AddtlRespData>INVALID TERM</AddtlRespData>
    <AthNtwkID>14</AthNtwkID>
    <AthNtwkNm>BUYPASS</AthNtwkNm>
  </RespGrp>
</CreditResponse>
```

Request payload was the exact TC 200376490010 shape: POSEntryMode=`911`, TermCatCode=`09`, TermEntryCapablt=`01`, MCC set per MID (5399/5411/5812), Track2=`4005520000000921=25121011000012300000`, Amount=`$3.14`, CardType=`Visa`, with `AddtlAmtGrp.PartAuthrztnApprvlCapablt=1` and `VisaGrp` (ACI=Y, VisaBID=56412, VisaAUAR, TaxAmtCapablt=1). Only STAN/RefNum/timestamps/ClientRef vary from the CSV row.

### Key implications

1. **"Sandbox" = the staging Rapid Connect endpoint `https://stg.dw.us.fdcnet.biz/rc`.** There is no separate sandbox host; the staging URL *is* the sandbox, and it enforces test-script conformance for every inbound transaction.
2. **The sandbox expects transactions to follow the official test script** (`TestTransactions_RSO024.csv`, 424 rows). A transaction is expected to byte-match one of those rows on (Currency, MCC, PymtType, TxnType, Amount, POSEntryMode, Encryption, Token, PAN). Non-conforming transactions are rejected and flagged in the Fiserv sandbox portal's diagnostic view.
3. **The `Recommendation: "TestCase not found. Please check the parameters: …"` text is NOT in the UMF HTTP response.** It is only visible in the **Fiserv sandbox portal UI** (the user viewed it manually there and shared it with this analysis). The UMF response body contains only the `RespGrp` shown above — `RespCode=109`, `AddtlRespData=INVALID TERM`, `AthNtwkNm=BUYPASS`. Any probe that inspects only the HTTP response will never see the Recommendation text, which is why prior probe runs looked like plain `109 INVALID TERM` without the context that Fiserv support sees.
4. **Our byte-for-byte replay of TC 200376490010 still returned 109 INVALID TERM** across all three test-script MIDs. So either (a) our replay still has a subtle mismatch versus the canonical test-script row that the sandbox is checking for, or (b) the matching check passed and the 109 is then coming from the BUYPASS/terminal-provisioning layer behind the sandbox. The response carries `AthNtwkNm=BUYPASS / AthNtwkID=14`, which is consistent with either — it can either be a genuine host hop or a sandbox-fabricated envelope. Only Fiserv can confirm which, by looking at the portal diagnostic for our STAN/RefNum.
5. **Net actionable:** (a) ask Fiserv to pull the sandbox portal log for our specific STAN/RefNum runs (so we can see whether the "TestCase not found" annotation fires or not), (b) if it fires, identify which field differs from the canonical TC 200376490010 row, (c) if it does not fire, then this is a BUYPASS-side terminal-provisioning issue against the test-script MIDs. Workshop Q4.6 and Q4.7 cover both branches.

### 2026-04-13 evening — Byte-exact replay across 8 diverse test-script rows

Built `TestCaseProbe.java`, which reads `TestTransactions_RSO024.csv` directly and replays selected rows verbatim (only `STAN`, `RefNum`, `OrderNum`, `LocalDateTime`, `TrnmsnDateTime` are refreshed per run — every other field including `MerchID`, `TermID`, `POSEntryMode`, `TermCatCode`, `TxnAmt`, `Track2Data`, `CardType`, `VisaGrp`/`MCGrp`, `AddtlAmtGrp`, `DigWltProgType` is kept byte-identical to the CSV). This eliminates any possibility of hand-transcription drift from the canonical test case.

The 8 cases were chosen to span every axis the test script varies along:

| TestCase ID | MerchID | Industry | EntryMode | TermCatCode | Brand | TxnType | Feature flag | Result |
|---|---|---|---|---|---|---|---|---|
| 200376490010 | 120415 | Retail 5399 | 911 contactless | 09 | Visa | Authorization | plain | **109 INVALID TERM** |
| 200113500010 | 120415 | Retail 5399 | 911 contactless | 01 | Visa | Authorization | `DigWltProgType=ApplePay` | **109 INVALID TERM** |
| 200072070010 | 120415 | Retail 5399 | 901 swiped | 01 | Visa | Authorization | plain | **109 INVALID TERM** |
| 200466910010 | 120415 | Retail 5399 | 911 contactless | 09 | MasterCard | Authorization | `MCGrp.DevTypeInd=01` | **109 INVALID TERM** |
| 200376410010 | 120416 | Supermarket 5411 | 911 contactless | 09 | Visa | Authorization | plain | **109 INVALID TERM** |
| 200070230010 | 120414 | Restaurant 5812 | 901 swiped | 01 | Visa | Authorization | plain (no contactless TC exists for Restaurant) | **109 INVALID TERM** |
| 200151030010 | 120415 | Retail 5399 | 901 swiped | 01 | Visa | Refund | `RefundType=Online` | **109 INVALID TERM** |
| 200109600010 | 120415 | Retail 5399 | 911 contactless | 01 | Visa | Refund | plain | **109 INVALID TERM** |

Every response has the exact same shape as §8-2026-04-13: `RespCode=109`, `AddtlRespData=INVALID TERM`, `AthNtwkID=14`, `AthNtwkNm=BUYPASS`. No `RejectResponse`, no `ErrorData`, no schema complaints.

### What this rules out

Replaying *actual* test-script rows byte-for-byte — and still getting `109 INVALID TERM` on every single one, across every dimension the script varies along — rules out the main non-provisioning theories:

- **Not a POSEntryMode mismatch.** Both `911` (contactless) and `901` (swiped) fail identically.
- **Not a TermCatCode mismatch.** Both `01` and `09` fail.
- **Not an industry/MCC mismatch.** Retail `5399`, Supermarket `5411`, and Restaurant `5812` all fail.
- **Not a card-brand gap.** Visa and MasterCard both fail; Amex/Discover/Diners would almost certainly behave the same.
- **Not a TxnType scoping issue.** Authorization and Refund both fail.
- **Not a feature-group issue.** Plain, `DigWltProgType=ApplePay`, and `MCGrp.DevTypeInd=01` variants all fail.
- **Not a payload-shape issue at the Rapid Connect layer.** If the XML didn't match the RSO024 TPP feature config, we would see a `RejectResponse` or `TP0003 - UMF feature not found` (like the keyed-entry test in §8). Instead we get a fully-formed `CreditResponse` with a `VisaGrp.TransID`, routed through `AthNtwkNm=BUYPASS` and rejected there.

### What this leaves

The rejection can only be **terminal-side on the authorization host**: the test-script MIDs `RCTST1000120414/15/16` (and their one TermID `00000001`) are not provisioned/enabled on BUYPASS for RSO024 staging, regardless of payload. This is consistent with, and now strongly supports, the 2026-04-12 Project-Profile-MID finding.

### Current residual asks for Fiserv

1. **Terminal/MID provisioning on BUYPASS (blocking everything).** Please provision at least one MID/TID on the BUYPASS authorization host for RSO024 staging and tell us which one. We have replayed canonical test-script rows byte-for-byte across two entry modes, two card brands, three industries, two TermCatCodes, two TxnTypes, and two feature variants, and every one is rejected at BUYPASS with `109 INVALID TERM`. Confirm which MID set (`119068/69/70` Project Profile vs `120414/15/16` test script) is the canonical cert set — see workshop Q4.6.
2. **Sandbox diagnostic (secondary, for completeness).** Pull the Fiserv sandbox portal log for ClientRef `0000700VRSO024` through `0000707VRSO024` (2026-04-13 evening) and the 2026-04-13 afternoon run (`0000500VRSO024` … `0000610VRSO024`) to confirm none of these fired a `"TestCase not found"` annotation (they should not, since they are byte-exact replays).
3. Resolve the DUKPT vs Master Session Encryption discrepancy — see workshop Q2.6.
4. Provide the staging master encryption key if MSE is confirmed as the correct PIN mechanism (see §6).

## Original (2026-04-12) diagnosis — superseded by 2026-04-13 findings

The log below originally attributed 109 INVALID TERM purely to BUYPASS-side provisioning. The 2026-04-13 re-run and the user's clarification that **the staging URL itself is the sandbox and it enforces test-script conformance** means the real picture is more nuanced: the 109 could be coming from the sandbox's TestCase-match check (visible only in the Fiserv sandbox portal UI, not in the UMF response) and/or from the terminal-provisioning layer behind it. We cannot tell from the HTTP response alone — see the clarified implications above. The original asks (BUYPASS provisioning, ticket reset, keyed entry, master key) remain valid, but Fiserv should be asked to look at the portal diagnostic for our specific runs to disambiguate.

---

## Configuration Used

| Parameter | Value |
|---|---|
| GroupID | 20001 |
| MerchID | RCTST1000119069 (Retail/QSR) |
| TermID | 00000003 (Dev Stage TID from Project Profile) |
| TPPID | RSO024 |
| ServiceID | 160 |
| AuthKey | `20001RCTST1000119069\|00000003` |
| DID | 00068124162777614130 (freshly registered and activated) |
| Transaction URL | `https://stg.dw.us.fdcnet.biz/rc` |
| ClientTimeout | 30 |

---

## 1. Service Discovery — OK

**Request:**
```
GET /sd/srsxml.rc HTTP/1.1
Host: stg.dw.us.fdcnet.biz
User-Agent: Softpay SoftPOS v1.0
Cache-Control: no-cache
```

**Response:**
```xml
<Response>
  <Status StatusCode="OK" />
  <ServiceDiscoveryResponse>
    <ServiceProvider>
      <URL>https://stagingsupport.datawire.net/nocportal/SRS.do</URL>
    </ServiceProvider>
  </ServiceDiscoveryResponse>
</Response>
```

---

## 2. SRS Registration — OK (DID Assigned)

**Request:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Request Version="3">
  <ReqClientID>
    <DID></DID>
    <App>RAPIDCONNECTSRS</App>
    <Auth>20001RCTST1000119069|00000003</Auth>
    <ClientRef>0000001VRSO024</ClientRef>
  </ReqClientID>
  <Registration>
    <ServiceID>160</ServiceID>
  </Registration>
</Request>
```

**Response:**
```
StatusCode="OK"
DID assigned: 00068124162777614130
Transaction URLs: https://stg.dw.us.fdcnet.biz/rc
```

---

## 3. SRS Activation — OK

**Request:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Request Version="3">
  <ReqClientID>
    <DID>00068124162777614130</DID>
    <App>RAPIDCONNECTSRS</App>
    <Auth>20001RCTST1000119069|00000003</Auth>
    <ClientRef>0000002VRSO024</ClientRef>
  </ReqClientID>
  <Activation>
    <ServiceID>160</ServiceID>
  </Activation>
</Request>
```

**Response:**
```
StatusCode="OK"
```

---

## 4. Credit Authorization — RespCode 109 "INVALID TERM"

### 4.1 Datawire Envelope (sent to `https://stg.dw.us.fdcnet.biz/rc`)

**Request:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Request Version="3" ClientTimeout="30">
  <ReqClientID>
    <DID>00068124162777614130</DID>
    <App>RAPIDCONNECTVXN</App>
    <Auth>20001RCTST1000119069|00000003</Auth>
    <ClientRef>0000003VRSO024</ClientRef>
  </ReqClientID>
  <Transaction>
    <ServiceID>160</ServiceID>
    <Payload Encoding="cdata"><![CDATA[...UMF payload below...]]></Payload>
  </Transaction>
</Request>
```

**Response:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Response Version="3" xmlns="http://securetransport.dw/rcservice/xml">
  <RespClientID>
    <DID>00068124162777614130</DID>
    <ClientRef>0000003VRSO024</ClientRef>
  </RespClientID>
  <Status StatusCode="OK"></Status>
  <TransactionResponse>
    <ReturnCode>000</ReturnCode>
    <Payload Encoding="cdata"><![CDATA[...UMF response below...]]></Payload>
  </TransactionResponse>
</Response>
```

Datawire transport: **OK** (StatusCode=OK, ReturnCode=000).

### 4.2 UMF Payload (inside CDATA)

**Request (Credit Authorization, Visa $50.00, Contactless EMV):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<GMF xmlns="com/fiserv/Merchant/gmfV15.04">
  <CreditRequest>
    <CommonGrp>
      <PymtType>Credit</PymtType>
      <TxnType>Authorization</TxnType>
      <LocalDateTime>20260412145900</LocalDateTime>
      <TrnmsnDateTime>20260412115900</TrnmsnDateTime>
      <STAN>000001</STAN>
      <RefNum>000000100001</RefNum>
      <OrderNum>ORDER001</OrderNum>
      <TPPID>RSO024</TPPID>
      <TermID>00000003</TermID>
      <MerchID>RCTST1000119069</MerchID>
      <MerchCatCode>5399</MerchCatCode>
      <POSEntryMode>071</POSEntryMode>
      <POSCondCode>00</POSCondCode>
      <TermCatCode>09</TermCatCode>
      <TermEntryCapablt>04</TermEntryCapablt>
      <TxnAmt>000000005000</TxnAmt>
      <TxnCrncy>840</TxnCrncy>
      <TermLocInd>0</TermLocInd>
      <CardCaptCap>1</CardCaptCap>
      <GroupID>20001</GroupID>
    </CommonGrp>
    <CardGrp>
      <Track2Data>4111111111111111=25121011000012345678</Track2Data>
      <CardType>Visa</CardType>
    </CardGrp>
    <AddtlAmtGrp>
      <PartAuthrztnApprvlCapablt>1</PartAuthrztnApprvlCapablt>
    </AddtlAmtGrp>
    <EMVGrp>
      <EMVData>9F2608C2A3F4E5D6B7A8C99F2701809F100706011203A400009F3704AABBCCDD9F3602001C950500000000009A032604109C01005F2A020840820219808407A00000000310109F02060000000050009F03060000000000009F1A0208409F3303E0B8C89F34031E0300</EMVData>
      <CardSeqNum>001</CardSeqNum>
    </EMVGrp>
    <VisaGrp>
      <ACI>Y</ACI>
      <VisaBID>56412</VisaBID>
      <VisaAUAR>000000000000</VisaAUAR>
      <TaxAmtCapablt>1</TaxAmtCapablt>
    </VisaGrp>
  </CreditRequest>
</GMF>
```

**Response (RespCode 109):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<GMF xmlns="com/fiserv/Merchant/gmfV15.04">
  <CreditResponse>
    <CommonGrp>
      <PymtType>Credit</PymtType>
      <TxnType>Authorization</TxnType>
      <LocalDateTime>20260412145900</LocalDateTime>
      <TrnmsnDateTime>20260412115900</TrnmsnDateTime>
      <STAN>000001</STAN>
      <RefNum>000000100001</RefNum>
      <OrderNum>ORDER001</OrderNum>
      <TermID>00000003</TermID>
      <MerchID>RCTST1000119069</MerchID>
      <TxnAmt>000000005000</TxnAmt>
      <TxnCrncy>840</TxnCrncy>
    </CommonGrp>
    <EMVGrp>
      <EMVData>8A023035</EMVData>
    </EMVGrp>
    <VisaGrp>
      <TransID>2314885530818453</TransID>
    </VisaGrp>
    <RespGrp>
      <RespCode>109</RespCode>
      <AddtlRespData>INVALID TERM</AddtlRespData>
      <AthNtwkID>14</AthNtwkID>
      <AthNtwkNm>BUYPASS</AthNtwkNm>
    </RespGrp>
  </CreditResponse>
</GMF>
```

### 4.3 Same result with second attempt ($75.00)

**Request (STAN=000002, RefNum=000000100002, TxnAmt=000000007500):**  
Identical structure, different STAN/RefNum/amount.

**Response:**
```xml
<RespGrp>
  <RespCode>109</RespCode>
  <AddtlRespData>INVALID TERM</AddtlRespData>
  <AthNtwkID>14</AthNtwkID>
  <AthNtwkNm>BUYPASS</AthNtwkNm>
</RespGrp>
```

---

## 5. Previous Attempts (same session)

We also tested with TermID `00000001` (Cert Stage TID) — same result: 109 "INVALID TERM".

**DID registered for TID 00000001:** 00068124111190335863  
**DID registered for TID 00000003:** 00068124162777614130

Both DIDs were registered and activated successfully. Both return 109 on authorization.

---

## 6. Encryption Key Request (Master Session) — Empty Response

We need to exchange a session encryption key before processing PIN Debit transactions. We do not have the master key yet, but tested the EncryptionKeyRequest message structure iteratively to confirm correct XML format.

### 6.1 Attempt 1 — RespCode 940 (field errors)

**Request:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<GMF xmlns="com/fiserv/Merchant/gmfV15.04">
  <AdminRequest>
    <CommonGrp>
      <PymtType>Debit</PymtType>
      <TxnType>EncryptionKeyRequest</TxnType>
      <LocalDateTime>20260412151022</LocalDateTime>
      <TrnmsnDateTime>20260412121022</TrnmsnDateTime>
      <STAN>000001</STAN>
      <RefNum>000000100001</RefNum>
      <TPPID>RSO024</TPPID>
      <TermID>00000003</TermID>
      <MerchID>RCTST1000119069</MerchID>
      <TxnCrncy>840</TxnCrncy>
      <GroupID>20001</GroupID>
    </CommonGrp>
  </AdminRequest>
</GMF>
```

**Response (RespCode 940):**
```xml
<AdminResponse>
  <CommonGrp>
    <TxnType>EncryptionKeyRequest</TxnType>
    <RefNum>000000100001</RefNum>
    <TermID>00000003</TermID>
    <MerchID>RCTST1000119069</MerchID>
  </CommonGrp>
  <RespGrp>
    <RespCode>940</RespCode>
    <ErrorData>RE008 - Field Not Allowed: PymtType|RE008 - Field Not Allowed: TxnCrncy|TC005 - Missing Mandatory Field: EnhKeyFmt|</ErrorData>
  </RespGrp>
</AdminResponse>
```

**Findings:**
- `PymtType` is **NOT allowed** in AdminRequest (RE008)
- `TxnCrncy` is **NOT allowed** in AdminRequest (RE008)
- `EnhKeyFmt` is **mandatory** for EncryptionKeyRequest (TC005)

### 6.2 Attempt 2 — RespCode 001 (EnhKeyFmt value error)

Removed PymtType/TxnCrncy, added `<EnhKeyFmt>3</EnhKeyFmt>` inside CommonGrp.

**Response (RejectResponse, RespCode 001):**
```
EC000 - XML Validation Error: /GMF/AdminRequest/CommonGrp/EnhKeyFmt | EnhKeyFmt | com/fiserv/Merchant/gmfV15.04 | value '3' not in enumeration
```

**Finding:** EnhKeyFmt only accepts `"T"` (TR-31 key block format) per XSD enumeration `EnhKeyFmtType`.

### 6.3 Attempt 3 — Passes validation, empty response

**Request (correct structure):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<GMF xmlns="com/fiserv/Merchant/gmfV15.04">
  <AdminRequest>
    <CommonGrp>
      <TxnType>EncryptionKeyRequest</TxnType>
      <LocalDateTime>20260412151147</LocalDateTime>
      <TrnmsnDateTime>20260412121147</TrnmsnDateTime>
      <STAN>000001</STAN>
      <RefNum>000000100001</RefNum>
      <TPPID>RSO024</TPPID>
      <TermID>00000003</TermID>
      <MerchID>RCTST1000119069</MerchID>
      <GroupID>20001</GroupID>
      <EnhKeyFmt>T</EnhKeyFmt>
    </CommonGrp>
  </AdminRequest>
</GMF>
```

**Datawire Response (transport OK):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Response Version="3" xmlns="http://securetransport.dw/rcservice/xml">
  <RespClientID>
    <DID>00068124162777614130</DID>
    <ClientRef>0000001VRSO024</ClientRef>
  </RespClientID>
  <Status StatusCode="OK"></Status>
  <TransactionResponse>
    <ReturnCode>000</ReturnCode>
    <Payload Encoding="cdata"><![CDATA[<?xml version="1.0" encoding="UTF-8"?>]]></Payload>
  </TransactionResponse>
</Response>
```

**Findings:**
- Datawire transport: **OK** (StatusCode=OK, ReturnCode=000)
- No `RejectResponse` — the AdminRequest XML is now **valid**
- UMF response payload is **empty** (just the XML prolog, no `<GMF>` body)
- This strongly suggests the **master encryption key is not provisioned** for this MID/TID on staging
- The correct AdminRequest structure for EncryptionKeyRequest is now confirmed

---

## 7. Extended MID/TID Probe — 2026-04-13

To isolate whether the 109 decline is MID-specific, TID-specific, or affects all provisioned terminals, we ran SRS registration against all four cert MIDs × TIDs 001/002/003.

### 7.1 SRS Registration Results (12 combos)

| MerchID | TermID | Registration StatusCode | DID assigned |
|---|---|---|---|
| RCTST1000119068 | 00000001 | AccessDenied | *(pre-registered — had DID `00068124106860962610`)* |
| RCTST1000119068 | 00000002 | OK | 00068124124872924252 |
| RCTST1000119068 | 00000003 | OK | 00068124171727158950 |
| RCTST1000119069 | 00000001 | AccessDenied — "Merchant Already Provisioned" | — |
| RCTST1000119069 | 00000002 | OK | 00068124130331624449 |
| RCTST1000119069 | 00000003 | AccessDenied | — |
| RCTST1000119070 | 00000001 | Failed — "Ticket Status is not good" | — |
| RCTST1000119070 | 00000002 | OK | 00068124198885244866 |
| RCTST1000119070 | 00000003 | OK | 00068124218233403916 |
| RCTST0000000065 | 00000001 | OK | 00064257248851026901 |
| RCTST0000000065 | 00000002 | OK | 00064257251724609371 |
| RCTST0000000065 | 00000003 | OK | 00013116138286995719 |

### 7.2 Activation — Only the Original DID Could Be Activated

When we attempted to activate the newly-registered DIDs from §7.1, **all returned `StatusCode="NotFound"`**:

```
RCTST1000119068/00000002: Activation NotFound
RCTST1000119068/00000003: Activation NotFound
RCTST1000119069/00000002: Activation NotFound
RCTST1000119070/00000002: Activation NotFound
RCTST1000119070/00000003: Activation NotFound
RCTST0000000065/00000001: Activation NotFound
RCTST0000000065/00000002: Activation NotFound
RCTST0000000065/00000003: Activation NotFound
```

Only the pre-existing DID `00068124106860962610` (RCTST1000119068 / 00000001, activated 2026-04-12) remained in an active state and reachable.

### 7.3 Re-Registration Blocked — "Ticket Status is not good"

Attempting to re-register the same MID/TIDs on 2026-04-13 returned:

```xml
<Response Version="3">
  <Status StatusCode="Failed">Ticket Status is not good</Status>
  <RegistrationResponse/>
</Response>
```

**Finding:** The Datawire SRS registration ticket for each MID/TID is single-use. Once consumed, it cannot be replayed from the client side.

---

## 8. POSEntryMode Probe — 2026-04-13

Using the one fully-activated combo (RCTST1000119068 / 00000001, DID `00068124106860962610`), we sent the same Visa auth with different POSEntryMode values to learn which modes TPP RSO024 accepts.

| POSEntryMode | Meaning | Host Result |
|---|---|---|
| `011` | Manual / PAN keyed, no CVV | **TP0003 — UMF feature (CommonGrp.POSEntryMode) not found in Rapid Connect for TPP ID (RSO024)** |
| `012` | Manual / PAN keyed with CVV | TP0003 — same as above |
| `051` | EMV contact chip | reached BUYPASS → **109 INVALID TERM** |
| `071` | Contactless EMV | reached BUYPASS → **109 INVALID TERM** |
| `901` | Contactless chip (magstripe mode) | reached BUYPASS → **109 INVALID TERM** |
| `910` / `912` | Contactless variants | reached BUYPASS → **109 INVALID TERM** |

### 8.1 Interpretation

- **Keyed POSEntryMode values `011` / `012` are not configured as a feature of TPP RSO024.** The rejection happens at the Rapid Connect layer (error code TP0003), before routing to an authorization network. This is a TPP configuration issue — the schema accepts the value, but the TPP-level feature enablement does not.
- **All chip/contactless POSEntryMode values reach BUYPASS and are rejected with the same 109 INVALID TERM.** This confirms the 109 is not caused by our choice of entry mode — it is a terminal-provisioning issue on the BUYPASS host.
- We need keyed entry enabled for **initial certification steps** (some cert test cases require PAN-keyed transactions). Even if production rollout is contactless-only, the cert script typically includes keyed flows.

### 8.2 Example — POSEntryMode=011 Rejection

**Request (only POSEntryMode changed from Section 4):**
```xml
<CommonGrp>
  ...
  <POSEntryMode>011</POSEntryMode>
  ...
</CommonGrp>
<CardGrp>
  <AcctNum>4111111111111111</AcctNum>
  <CardExpiryDate>202512</CardExpiryDate>
  <CardType>Visa</CardType>
</CardGrp>
```

**Response:**
```xml
<RespGrp>
  <RespCode>120</RespCode>
  <ErrorData>TP0003 - UMF feature (CommonGrp.POSEntryMode) not found in Rapid Connect for TPP ID (RSO024)</ErrorData>
</RespGrp>
```

### 8.3 Example — POSEntryMode=901 Reaches BUYPASS, Rejected with 109

**Request:**
```xml
<CommonGrp>
  ...
  <POSEntryMode>901</POSEntryMode>
  ...
</CommonGrp>
<CardGrp>
  <Track2Data>4017779995555556=30041200000000001</Track2Data>
  <CardType>Visa</CardType>
</CardGrp>
```

**Response:**
```xml
<RespGrp>
  <RespCode>109</RespCode>
  <AddtlRespData>INVALID TERM</AddtlRespData>
  <AthNtwkID>14</AthNtwkID>
  <AthNtwkNm>BUYPASS</AthNtwkNm>
</RespGrp>
```

Track2Data value `4017779995555556=30041200000000001` is taken directly from the SDK sample code (`TestConst.REQUEST_DEBIT_TRACK2`).

---

## Summary

| Step | Status | Detail |
|---|---|---|
| Service Discovery | OK | SRS URL returned |
| SRS Registration | OK (initial) / **Blocked** (re-try) | DID assigned first time; subsequent registrations return `Ticket Status is not good` |
| SRS Activation | OK for 119068/001 only | All other freshly-registered DIDs return `NotFound` |
| Datawire Transport | OK | StatusCode=OK, ReturnCode=000 |
| UMF XML Validation | OK | No XSD errors |
| **Authorization (chip/contactless PEMs)** | **FAIL** | **RespCode=109, INVALID TERM, AthNtwkNm=BUYPASS** — affects every chip/contactless POSEntryMode tested |
| **Authorization (keyed PEM 011/012)** | **FAIL** | **RespCode=120, TP0003 — POSEntryMode not a supported feature for TPP RSO024** |
| **EncryptionKeyRequest** | **FAIL** | **XML valid, but empty response — master key not provisioned** |

## Questions for Fiserv

### Provisioning (blocks all auth testing)

1. The test MIDs (`RCTST1000119068`, `RCTST1000119069`, `RCTST1000119070`, `RCTST0000000065`) all return **109 INVALID TERM from the BUYPASS network** for every POSEntryMode that reaches the authorization host. Please confirm that at least one MID/TID is provisioned on the BUYPASS host for RSO024 staging, and identify which specific combo we should use.
2. Which TermID should we use for development-stage testing — `00000001`, `00000002`, or `00000003`? The Project Profile lists 001 as Cert Stage and 003 as Dev Stage; we have evidence that neither is authorized on BUYPASS.
3. Is the decline from BUYPASS expected for the staging environment, or should authorization traffic route to a simulator instead?
4. Is there an additional activation step required beyond SRS registration/activation before BUYPASS recognises the terminal?

### SRS registration tickets

5. Our Datawire SRS registration tickets for all four cert MIDs have been consumed — re-registration now returns `"Ticket Status is not good"`. Please **reset the SRS tickets** so we can re-register and test additional MID/TID combinations.
6. Additionally, freshly-registered DIDs (from the §7.1 probe) return `NotFound` on activation. Is there a delay, a separate node, or a manual approval required between registration and activation? The 2026-04-12 DID for 119068/001 activated successfully on the first try, so we know the process works — we need to understand why subsequent DIDs do not.

### POSEntryMode / TPP configuration

7. TPP RSO024 rejects `POSEntryMode=011` and `012` with error `TP0003 - UMF feature (CommonGrp.POSEntryMode) not found`. **Please enable keyed (PAN) entry on TPP RSO024**, at minimum for certification. Some certification test cases require keyed entry, and we need to pass those steps even if production deployment is contactless-only. If modifying RSO024 is not possible, please confirm whether a parallel cert-only TPP is available.
8. Please confirm the complete list of POSEntryMode values that are enabled for TPP RSO024 (e.g., `011`, `012`, `051`, `071`, `901`, `910`, `912`, …).

### Encryption key / PIN Debit

9. We need the **test master encryption key** for Master Session Encryption (PIN Debit). The SDK sample code contains only placeholder values with the comment "should be provided by your support representative." Please provide the staging master key so we can test EncryptionKeyRequest and PIN Debit flows.
10. Our EncryptionKeyRequest now passes XML validation (see §6.3) but returns an **empty UMF payload** (Datawire OK, ReturnCode=000, no `<GMF>` body). Is this because the master key is not provisioned for our test MIDs, or is there an additional setup step required?

### Test data

11. Are the test card numbers from the test script (e.g., `4111111111111111`) valid on staging, or only inside the certification test harness? The SDK `TestConst.REQUEST_DEBIT_TRACK2` value (`4017779995555556=30041200000000001`) also fails with 109, suggesting the issue is terminal-side rather than card-side.
