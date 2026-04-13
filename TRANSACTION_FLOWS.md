# Transaction Flows Reference

**Project:** Softpay SoftPOS -- Fiserv Rapid Connect Integration
**Project ID (TPP ID):** RSO024
**UMF Version:** 15.04 (v15.04.5, effective February 25, 2026)
**Settlement Model:** Host Capture (Fiserv auto-settles via periodic batch runs every X minutes)
**Purchase Flow:** Dual-message only (Authorization + Completion) -- Sale intentionally excluded (see rationale below)

---

## Table of Contents

1. [Standard Purchase Flow (Contactless Credit)](#1-standard-purchase-flow-contactless-credit)
2. [Restaurant Purchase with Tip](#2-restaurant-purchase-with-tip)
3. [Debit Purchase with PIN](#3-debit-purchase-with-pin)
4. [Digital Wallet (Apple Pay / Google Pay / Samsung Pay)](#4-digital-wallet-apple-pay--google-pay--samsung-pay)
5. [Void / Full Reversal](#5-void--full-reversal)
6. [Timeout Reversal (TOR)](#6-timeout-reversal-tor)
7. [Refund](#7-refund)
8. [DCC Flow](#8-dcc-flow)
9. [Master Session Encryption Key Request](#9-master-session-encryption-key-request)
10. [Fields to Store from Authorization Response](#10-fields-to-store-from-authorization-response)
11. [STAN & RefNum Management](#11-stan--refnum-management)
12. [Timeout Handling Rules](#12-timeout-handling-rules)

---

## 1. Standard Purchase Flow (Contactless Credit)

Every purchase in RSO024 follows the dual-message pattern: an Authorization captures the
approval and an AuthID, then a Completion captures the funds for settlement.

**Why dual-message (confirmed with Fiserv):** Host Capture runs periodic batch cycles (every
X minutes), sweeping all completed transactions into settlement. With single-message Sale,
transactions would be captured and batched almost immediately — leaving no void window.
Softpay requires at least 15 minutes for voids. The dual-message flow gives Softpay control
over when to send the Completion: voids can be sent any time before Completion, and the
Completion is only sent when the transaction is final.

### Sequence Diagram

```
Cardholder            Softpay App              Fiserv Rapid Connect
    |                      |                            |
    |--- Tap Card -------->|                            |
    |                      |                            |
    |                      |--- CreditRequest --------->|
    |                      |    CommonGrp:              |
    |                      |      PymtType=Credit       |
    |                      |      TxnType=Authorization |
    |                      |      TPPID=RSO024          |
    |                      |      MerchID=<MID>         |
    |                      |      TermID=<TID>          |
    |                      |      GroupID=20001         |
    |                      |      POSEntryMode=071      |
    |                      |      POSCondCode=00        |
    |                      |      TermCatCode=01        |
    |                      |      STAN=000123           |
    |                      |      RefNum=100000000001   |
    |                      |      TxnAmt=000000005000   |
    |                      |      TxnCrncy=840          |
    |                      |      LocalDateTime=...     |
    |                      |      TrnmsnDateTime=...    |
    |                      |    CardGrp:               |
    |                      |      Track2Data=<T2>       |
    |                      |      CardType=Visa         |
    |                      |    EMVGrp:                |
    |                      |      EMVData=<TLV tags>    |
    |                      |    VisaGrp: (if Visa)     |
    |                      |                            |
    |                      |<-- CreditResponse ---------|
    |                      |    CommonGrp:              |
    |                      |      STAN=000123 (echoed)  |
    |                      |      RefNum=100000000001   |
    |                      |      LocalDateTime=...     |
    |                      |      TrnmsnDateTime=...    |
    |                      |    RespGrp:               |
    |                      |      RespCode=000          |
    |                      |      AuthID=OK1234         |
    |                      |      ResponseDate=0407     |
    |                      |      AuthNetID=...         |
    |                      |    VisaGrp:               |
    |                      |      TransID=<15-char>     |
    |                      |    CardGrp:               |
    |                      |      CardType=Visa         |
    |                      |                            |
    |<-- "Approved" -------|                            |
    |                      |                            |
    |                      |  ** STORE FROM RESPONSE ** |
    |                      |  AuthID, ResponseDate,     |
    |                      |  RespCode, STAN, RefNum,   |
    |                      |  LocalDateTime,            |
    |                      |  TrnmsnDateTime,           |
    |                      |  TransID (Visa),           |
    |                      |  BanknetData (MC),         |
    |                      |  AmExTranID (Amex),        |
    |                      |  NRID (Discover),          |
    |                      |  CardType, AuthNetID       |
    |                      |                            |
    |                      |                            |
    |   ... Completion     |                            |
    |   (immediately or    |                            |
    |    later, e.g. EOD)  |                            |
    |                      |                            |
    |                      |--- CreditRequest --------->|
    |                      |    CommonGrp:              |
    |                      |      PymtType=Credit       |
    |                      |      TxnType=Completion    |
    |                      |      STAN=000124 (NEW)     |
    |                      |      RefNum=100000000001   |
    |                      |        (SAME as auth)      |
    |                      |      TxnAmt=000000005000   |
    |                      |      POSEntryMode=071      |
    |                      |      <other CommonGrp>     |
    |                      |    CardGrp:               |
    |                      |      CardType=Visa         |
    |                      |    OrigAuthGrp:            |
    |                      |      OrigAuthID=OK1234     |
    |                      |      OrigResponseDate=0407 |
    |                      |      OrigRespCode=000      |
    |                      |      OrigSTAN=000123       |
    |                      |      OrigLocalDateTime=... |
    |                      |      OrigTranDateTime=...  |
    |                      |    VisaGrp:               |
    |                      |      TransID=<same 15-chr> |
    |                      |                            |
    |                      |<-- CreditResponse ---------|
    |                      |    RespGrp:               |
    |                      |      RespCode=000          |
    |                      |                            |
    |                      |  >> Host Capture:          |
    |                      |  >> Fiserv auto-settles    |
    |                      |                            |
```

### Key Points

- **POSEntryMode=071** -- Contactless EMV (chip data read via NFC). Other values:
  `901` = contactless magnetic stripe, `051` = contact EMV chip.
- **RefNum is IDENTICAL** in Authorization and Completion.
- **STAN is DIFFERENT** -- each message gets a new STAN (000123 for auth, 000124 for completion).
- **OrigAuthGrp** in the Completion echoes fields from the Authorization response.
- **Scheme-specific groups** (VisaGrp, MCGrp, AmexGrp, DSGrp) must echo the values
  returned in the auth response.
- **TxnAmt in Completion** may differ from auth if tip or adjustment applies (see Section 2).
- **Host Capture** -- after the Completion response with RespCode=000, the transaction
  enters the next periodic batch cycle for settlement. No BatchClose required.
- **Void window strategy** -- voids of Authorizations (before Completion) are effectively
  unlimited. Voids of Completions must be submitted before the next batch run.

---

## 2. Purchase with Tip

Two tipping flows are supported. **Flow 2A (tip-before-auth)** is the Softpay preferred flow
and works across all industries. **Flow 2B (tip-after-auth)** is the traditional restaurant
model where the tip is added after authorization.

### 2A. Tip-Before-Auth (Softpay Preferred)

The app collects the tip **before** sending the authorization. The full amount (service + tip)
is authorized and completed for the same amount. This is simpler, avoids the 20% tolerance
dependency, and works for any MCC.

```
Cardholder            Softpay App              Fiserv Rapid Connect
    |                      |                            |
    |--- Tap Card -------->|                            |
    |   (subtotal=$50.00)  |                            |
    |                      |                            |
    | STEP 1: App shows tip screen                      |
    |                      |                            |
    |--- Add $8.00 tip --->|                            |
    |                      |                            |
    | STEP 2: Authorize full amount (service + tip)     |
    |                      |--- CreditRequest --------->|
    |                      |    TxnType=Authorization   |
    |                      |    TxnAmt=000000005800     |
    |                      |      ($50.00 + $8.00 tip)  |
    |                      |    STAN=000201             |
    |                      |    RefNum=200000000001     |
    |                      |    MerchCatCode=5812       |
    |                      |    <CardGrp, EMVGrp, etc.> |
    |                      |                            |
    |                      |<-- CreditResponse ---------|
    |                      |    RespCode=000            |
    |                      |    AuthID=TIP567           |
    |                      |    ResponseDate=0407       |
    |                      |    TransID=<scheme ref>    |
    |                      |                            |
    |<-- "Approved $58.00" |                            |
    |                      |                            |
    | STEP 3: Completion (same amount)                  |
    |                      |--- CreditRequest --------->|
    |                      |    TxnType=Completion      |
    |                      |    TxnAmt=000000005800     |
    |                      |      (SAME as auth)        |
    |                      |    STAN=000202 (NEW)       |
    |                      |    RefNum=200000000001     |
    |                      |      (SAME as auth)        |
    |                      |    OrigAuthGrp:            |
    |                      |      OrigAuthID=TIP567     |
    |                      |      OrigRespCode=000      |
    |                      |      OrigSTAN=000201       |
    |                      |      OrigResponseDate=0407 |
    |                      |      OrigLocalDateTime=... |
    |                      |      OrigTranDateTime=...  |
    |                      |    AdditlAmtGrp:           |
    |                      |      AddlAmtType=          |
    |                      |        FirstAuthAmt        |
    |                      |      AddlAmt=             |
    |                      |        000000005800        |
    |                      |    AdditlAmtGrp:           |
    |                      |      AddlAmtType=          |
    |                      |        TotalAuthAmt        |
    |                      |      AddlAmt=             |
    |                      |        000000005800        |
    |                      |    <scheme group echoed>   |
    |                      |                            |
    |                      |<-- CreditResponse ---------|
    |                      |    RespCode=000            |
    |                      |                            |
    |<-- Receipt: $58.00 --|                            |
    |   Subtotal: $50.00   |                            |
    |   Tip:       $8.00   |                            |
    |   Total:    $58.00   |                            |
    |                      |                            |
```

#### Key Points (Flow 2A)

- **Tip is collected on-device before the authorization.** The protocol sees only the total amount.
- **Auth TxnAmt = Completion TxnAmt** — no tolerance needed, no risk of exceeding 20%.
- **FirstAuthAmt = TotalAuthAmt = Completion TxnAmt** (all $58.00 in this example).
- **Works for any MCC** — not limited to Restaurant (5812). Can be used with Retail (5399), Supermarket (5411), etc.
- **`TipAmt` in AddtlAmtGrp** is optional — can be sent for downstream reporting if desired.

### 2B. Tip-After-Auth (Traditional Restaurant)

The authorization is sent for the subtotal only. The tip is added later (e.g., on a paper
receipt), and the Completion carries the adjusted total. Requires Restaurant MCC and the
20% card brand tolerance rule applies.

```
Cardholder            Softpay App              Fiserv Rapid Connect
    |                      |                            |
    |--- Tap Card -------->|                            |
    |   (subtotal=$50.00)  |                            |
    |                      |                            |
    | STEP 1: Authorize subtotal                        |
    |                      |--- CreditRequest --------->|
    |                      |    TxnType=Authorization   |
    |                      |    TxnAmt=000000005000     |
    |                      |    STAN=000201             |
    |                      |    RefNum=200000000001     |
    |                      |    MerchCatCode=5812       |
    |                      |      (Restaurant)          |
    |                      |    <CardGrp, EMVGrp, etc.> |
    |                      |                            |
    |                      |<-- CreditResponse ---------|
    |                      |    RespCode=000            |
    |                      |    AuthID=TIP567           |
    |                      |    ResponseDate=0407       |
    |                      |    TransID=<scheme ref>    |
    |                      |                            |
    |<-- "Approved $50.00" |                            |
    |                      |                            |
    | STEP 2: Cardholder adds tip                       |
    |                      |                            |
    |--- Add $8.00 tip --->|                            |
    |                      |                            |
    | STEP 3: Completion with tip                       |
    |                      |--- CreditRequest --------->|
    |                      |    TxnType=Completion      |
    |                      |    TxnAmt=000000005800     |
    |                      |      ($50.00 + $8.00 tip)  |
    |                      |    STAN=000202 (NEW)       |
    |                      |    RefNum=200000000001     |
    |                      |      (SAME as auth)        |
    |                      |    OrigAuthGrp:            |
    |                      |      OrigAuthID=TIP567     |
    |                      |      OrigRespCode=000      |
    |                      |      OrigSTAN=000201       |
    |                      |      OrigResponseDate=0407 |
    |                      |      OrigLocalDateTime=... |
    |                      |      OrigTranDateTime=...  |
    |                      |    AdditlAmtGrp:           |
    |                      |      AddlAmtType=          |
    |                      |        FirstAuthAmt        |
    |                      |      AddlAmt=             |
    |                      |        000000005000        |
    |                      |    AdditlAmtGrp:           |
    |                      |      AddlAmtType=          |
    |                      |        TotalAuthAmt        |
    |                      |      AddlAmt=             |
    |                      |        000000005000        |
    |                      |    <scheme group echoed>   |
    |                      |                            |
    |                      |<-- CreditResponse ---------|
    |                      |    RespCode=000            |
    |                      |                            |
    |<-- Receipt: $58.00 --|                            |
    |   Subtotal: $50.00   |                            |
    |   Tip:       $8.00   |                            |
    |   Total:    $58.00   |                            |
    |                      |                            |
```

#### Key Points (Flow 2B)

- **MerchCatCode=5812** (Restaurants & Eating Places) or another Restaurant MCC.
- **TxnAmt in Completion = subtotal + tip** — the host settles this final amount.
- **Completion exceeds Authorization** — card brand 20% tolerance rule applies.
- **AdditlAmtGrp** — per 2026 UMF Changes, for Completion transactions:
  - `FirstAuthAmt` = the initial authorization amount ($50.00 = 000000005000).
  - `TotalAuthAmt` = the total amount that was authorized. If no incremental
    authorizations were made, TotalAuthAmt equals FirstAuthAmt.
- **Up to 6 AdditlAmtGrp** occurrences are allowed per message.
- **Tip adjustment must be submitted before merchant cut-off** for the day.
- **Settlement amount = Completion amount** ($58.00), not the authorization amount ($50.00).
- The Restaurant QRG (`RESTAURANT_QRG.pdf`) provides additional industry-specific guidance.

### Choosing Between Flow 2A and 2B

| | Flow 2A (Tip-Before-Auth) | Flow 2B (Tip-After-Auth) |
|---|---|---|
| **When to use** | Default for Softpay SoftPOS | Table-service with paper receipts |
| **Tip timing** | Before authorization | After authorization, before completion |
| **Auth amount** | Full (service + tip) | Subtotal only |
| **Completion amount** | Same as auth | Higher than auth (includes tip) |
| **20% tolerance** | Not needed | Required (Restaurant MCC) |
| **Industry restriction** | Any MCC | Restaurant MCCs only |
| **Complexity** | Simpler — amounts match | More complex — amounts differ |

---

## 3. Debit Purchase with PIN

PIN Debit uses `DebitRequest`/`DebitResponse` instead of `CreditRequest`/`CreditResponse`.
The Master Session encryption key must be established before the first PIN Debit transaction
of the day.

### Sequence Diagram

```
Softpay App              Fiserv Rapid Connect
    |                            |
    | STEP 0: Key Request (periodic -- every 24 hours)
    |                            |
    | (See Section 9 for full    |
    |  EncryptionKeyRequest flow)|
    |                            |
    | Session key is now active  |
    |                            |

Cardholder            Softpay App              Fiserv Rapid Connect
    |                      |                            |
    |--- Tap Debit Card -->|                            |
    |                      |                            |
    | STEP 1: Card presents debit AID (CAID)            |
    |   Softpay kernel selects Common Debit AID         |
    |   Amount is above CVM limit --> PIN required      |
    |                      |                            |
    | STEP 2: PIN Entry on SoftPOS device               |
    |                      |                            |
    |--- Enter PIN ------->|                            |
    |   (Softpay certified |                            |
    |    PIN pad component) |                            |
    |                      |                            |
    | STEP 3: Encrypt PIN block with session key        |
    |   PIN block format: ISO-0 or ISO-4 (TBD)         |
    |   Encrypted under current Master Session key      |
    |                      |                            |
    | STEP 4: Send Debit Authorization                  |
    |                      |--- DebitRequest ----------->|
    |                      |    CommonGrp:              |
    |                      |      PymtType=Debit        |
    |                      |      TxnType=Authorization |
    |                      |      POSEntryMode=071      |
    |                      |      STAN=000301           |
    |                      |      RefNum=300000000001   |
    |                      |      TxnAmt=000000007500   |
    |                      |      TxnCrncy=840          |
    |                      |    CardGrp:               |
    |                      |      Track2Data=<T2>       |
    |                      |      CardType=<debit net>  |
    |                      |    PINGrp:                |
    |                      |      PINData=<encrypted    |
    |                      |        PIN block, hex>     |
    |                      |      MSKeyID=<key ID from  |
    |                      |        EncryptionKeyReq>   |
    |                      |      NumPINDigits=4        |
    |                      |    EMVGrp:                |
    |                      |      EMVData=<TLV tags>    |
    |                      |                            |
    |                      |<-- DebitResponse ----------|
    |                      |    RespGrp:               |
    |                      |      RespCode=000          |
    |                      |      AuthID=DBT789         |
    |                      |      ResponseDate=0407     |
    |                      |    <scheme refs>           |
    |                      |                            |
    |<-- "Approved" -------|                            |
    |                      |                            |
    |                      |  ** STORE response fields **
    |                      |                            |
    | STEP 5: Debit Completion                          |
    |                      |--- DebitRequest ----------->|
    |                      |    CommonGrp:              |
    |                      |      PymtType=Debit        |
    |                      |      TxnType=Completion    |
    |                      |      STAN=000302 (NEW)     |
    |                      |      RefNum=300000000001   |
    |                      |        (SAME as auth)      |
    |                      |      TxnAmt=000000007500   |
    |                      |    CardGrp:               |
    |                      |      CardType=<debit net>  |
    |                      |    OrigAuthGrp:            |
    |                      |      OrigAuthID=DBT789     |
    |                      |      OrigRespCode=000      |
    |                      |      OrigSTAN=000301       |
    |                      |      OrigResponseDate=0407 |
    |                      |      OrigLocalDateTime=... |
    |                      |      OrigTranDateTime=...  |
    |                      |    (PINGrp NOT sent in     |
    |                      |     Completion)            |
    |                      |                            |
    |                      |<-- DebitResponse ----------|
    |                      |    RespCode=000            |
    |                      |                            |
```

### Key Points

- **PINGrp fields:**
  - `PINData` -- the encrypted PIN block (hexadecimal).
  - `MSKeyID` -- Master Session Key ID obtained from the EncryptionKeyRequest response.
    Used when Master Session encryption is selected (as in RSO024).
  - `KeySerialNumData` -- not used by Softpay (DUKPT only; Softpay uses Master Session Encryption).
  - `NumPINDigits` -- number of digits the cardholder entered.
- **PIN is only sent in the Authorization**, not in the Completion.
- **PINless POS Debit** is also selected -- for PINless transactions, the `PINGrp` is omitted
  entirely and the flow follows the credit pattern but uses `DebitRequest`.
- **Debit network routing** -- Fiserv routes to the appropriate debit network (STAR, NYCE,
  Pulse, etc.) based on the card's AID and BIN.
- **Common Debit AID (CAID)** -- if present on the card, CAID must be auto-selected per
  EMV Implementation Guide RQ 1300.
- **Void window for debit** -- must be within 25 minutes of original transaction.

---

## 4. Digital Wallet (Apple Pay / Google Pay / Samsung Pay)

Digital wallet transactions use the same contactless NFC tap flow as standard EMV
contactless, with additional fields identifying the wallet type. CDCVM (Consumer Device CVM)
is used for cardholder verification -- no PIN prompt is needed on the SoftPOS device.

### Sequence Diagram

```
Cardholder            Softpay App              Fiserv Rapid Connect
(with phone/watch)         |                            |
    |                      |                            |
    |--- Tap Device ------>|                            |
    |   (NFC)              |                            |
    |                      |                            |
    | Softpay kernel reads contactless data:            |
    |   - DPAN (Device PAN) or tokenized PAN            |
    |   - Track 2 equivalent data                       |
    |   - EMV tags including:                           |
    |     Tag 9F6E (Form Factor Indicator)              |
    |       identifies device type (phone, watch, etc.) |
    |   - CDCVM verified by cardholder's device         |
    |     (Face ID, fingerprint, device PIN)            |
    |   - No PIN prompt on SoftPOS terminal             |
    |                      |                            |
    |                      |--- CreditRequest --------->|
    |                      |    CommonGrp:              |
    |                      |      PymtType=Credit       |
    |                      |      TxnType=Authorization |
    |                      |      POSEntryMode=071      |
    |                      |      STAN=000401           |
    |                      |      RefNum=400000000001   |
    |                      |      TxnAmt=000000003500   |
    |                      |    CardGrp:               |
    |                      |      Track2Data=<DPAN T2>  |
    |                      |      CardType=Visa         |
    |                      |    EMVGrp:                |
    |                      |      EMVData=<TLV tags     |
    |                      |        incl. 9F6E>         |
    |                      |    EcommGrp:              |
    |                      |      DigWltInd=Passthru    |
    |                      |      DigWltProgType=       |
    |                      |        ApplePay            |
    |                      |    VisaGrp: (if Visa)     |
    |                      |                            |
    |                      |<-- CreditResponse ---------|
    |                      |    RespCode=000            |
    |                      |    AuthID=WAL456           |
    |                      |    TransID=<scheme ref>    |
    |                      |                            |
    |<-- "Approved" -------|                            |
    |                      |                            |
    |   ... Completion (same as standard flow) ...      |
    |                      |                            |
    |                      |--- CreditRequest --------->|
    |                      |    TxnType=Completion      |
    |                      |    STAN=000402 (NEW)       |
    |                      |    RefNum=400000000001     |
    |                      |      (SAME as auth)        |
    |                      |    OrigAuthGrp={...}       |
    |                      |    EcommGrp:              |
    |                      |      DigWltInd=Passthru    |
    |                      |      DigWltProgType=       |
    |                      |        ApplePay            |
    |                      |                            |
    |                      |<-- CreditResponse ---------|
    |                      |    RespCode=000            |
    |                      |                            |
```

### DigWltProgType Values

| Wallet       | DigWltProgType Value |
|-------------|---------------------|
| Apple Pay    | `ApplePay`          |
| Google Pay   | `AndroidPay`        |
| Samsung Pay  | `SamsungPay`        |

### Key Points

- **DigWltInd=Passthru** -- the wallet transaction passes through Fiserv to the card network.
  Softpay acts as a passthrough for the DPAN and cryptogram.
- **CDCVM** -- Consumer Device Cardholder Verification Method. The cardholder authenticates
  on their own device (biometrics, device passcode). The SoftPOS terminal must NOT prompt
  for offline PIN on contactless transactions (per EMV Guide RQ 4100).
- **All CDCVM transactions are dual-message** (per EMV Implementation Guide).
- **Tag 9F6E (Form Factor Indicator)** -- mandatory for contactless transactions. Identifies
  the device form factor (phone, watch, band, etc.). For non-Fleet: send only if byte 3
  bit 8 = 0.
- **No PIN entry** -- even if the transaction amount exceeds CVM limits, CDCVM satisfies
  the CVM requirement for wallet transactions.
- **EcommGrp is sent in both Authorization and Completion** with the same wallet identifiers.
- **Track2Data** contains the DPAN (Device PAN), not the actual card PAN.

---

## 5. Void / Full Reversal

Voids use `ReversalRequest` / `ReversalResponse` -- NOT `CreditRequest`. The `ReversalInd`
field distinguishes void from timeout reversal.

### Sequence Diagram

```
Softpay App              Fiserv Rapid Connect
    |                            |
    | Original transaction was   |
    | previously authorized      |
    | (and possibly completed).  |
    |                            |
    | Merchant/cardholder        |
    | requests cancellation.     |
    |                            |
    |--- ReversalRequest ------->|
    |    CommonGrp:              |
    |      PymtType=Credit       |
    |      TxnType=Authorization |
    |        (original TxnType)  |
    |      STAN=000501 (NEW)     |
    |      RefNum=100000000001   |
    |        (SAME as original)  |
    |      TxnAmt=000000005000   |
    |        (original amount)   |
    |      POSEntryMode=071      |
    |      LocalDateTime=...     |
    |      TrnmsnDateTime=...    |
    |    CardGrp:               |
    |      CardType=Visa         |
    |    ReversalInd=Void        |
    |    OrigAuthGrp:            |
    |      OrigAuthID=OK1234     |
    |      OrigResponseDate=0407 |
    |      OrigRespCode=000      |
    |      OrigSTAN=000123       |
    |      OrigLocalDateTime=... |
    |      OrigTranDateTime=...  |
    |    VisaGrp:               |
    |      TransID=<same value   |
    |        from auth response> |
    |    (MCGrp, AmexGrp, DSGrp  |
    |     as applicable)         |
    |                            |
    |<-- ReversalResponse -------|
    |    RespGrp:               |
    |      RespCode=000          |
    |                            |
```

### Timing Rules (2026 UMF Changes -- Appendix D)

| Scenario                          | Void Window                                      |
|----------------------------------|--------------------------------------------------|
| Credit/Debit/PLDebit/EBT Auth    | Within 25 minutes of original                    |
| Credit Authorization             | Any time before Completion or BatchSettleDetail   |
| Credit Completion                | Before merchant cut-off for the day (2026 change) |
| Debit routed Credit Auth         | Within 25 minutes                                |

### Key Points

- **Use `ReversalRequest`** -- never send a void as a `CreditRequest`.
- **ReversalInd=Void** -- distinguishes this from a Timeout reversal.
- **RefNum must match** the original transaction's RefNum.
- **STAN is NEW** -- every message gets a unique STAN.
- **OrigAuthGrp must be fully populated** from the stored authorization response.
- **Scheme-specific references must be echoed:**
  - Visa: `VisaGrp.TransID`
  - MasterCard: `MCGrp.BanknetData`
  - Amex: `AmexGrp.AmExTranID`
  - Discover: `DSGrp.DiscoverNRID`
- **Void of Refund** -- use `ReversalRequest` with the original Refund's details to cancel a
  refund. Same rules apply.
- **Completion added to Void support** -- per 2026 UMF Changes, Completion is now in the
  Void/Full Reversal support column for Visa, Amex, Discover, JCB, Diners, MasterCard,
  and UnionPay.

---

## 6. Timeout Reversal (TOR)

When no response is received for a transaction within the timeout window, the terminal must
send a Timeout Reversal to ensure the transaction is not left in an indeterminate state.

### Sequence Diagram

```
Softpay App              Fiserv Rapid Connect
    |                            |
    | STEP 1: Original request   |
    |                            |
    |--- CreditRequest --------->|
    |    TxnType=Authorization   |
    |    STAN=000601             |
    |    RefNum=600000000001     |
    |    TxnAmt=000000004200     |
    |                            |
    |    ... no response ...     |
    |    ... timeout expires ... |
    |                            |
    | STEP 2: Send TOR           |
    |                            |
    |--- ReversalRequest ------->|
    |    CommonGrp:              |
    |      PymtType=Credit       |
    |      TxnType=Authorization |
    |        (original TxnType)  |
    |      STAN=000602 (NEW)     |
    |      RefNum=600000000001   |
    |        (SAME as timed-out  |
    |         transaction)       |
    |      TxnAmt=000000004200   |
    |        (original amount)   |
    |      LocalDateTime=...     |
    |      TrnmsnDateTime=...    |
    |    CardGrp:               |
    |      CardType=Visa         |
    |    ReversalInd=Timeout      |
    |    OrigAuthGrp:            |
    |      OrigSTAN=000601       |
    |      OrigLocalDateTime=... |
    |      OrigTranDateTime=...  |
    |      (OrigAuthID=OMITTED   |
    |       -- no response       |
    |       received)            |
    |      (OrigRespCode=OMITTED)|
    |      (OrigResponseDate=    |
    |       OMITTED)             |
    |    VisaGrp:               |
    |      TransID=EXEMPT        |
    |      (2026 change:         |
    |       scheme-specific refs |
    |       are EXEMPT for TOR)  |
    |                            |
    |<-- ReversalResponse -------|
    |    RespCode=000            |
    |                            |
```

### TOR Retry Logic

```
Softpay App              Fiserv Rapid Connect
    |                            |
    | TOR also times out...      |
    |                            |
    |--- ReversalRequest ------->|
    |    (retry TOR)             |
    |    ReversalInd=Timeout      |
    |    STAN=000603 (NEW)       |
    |    RefNum=600000000001     |
    |      (SAME -- always same  |
    |       RefNum for this txn) |
    |    OrigAuthGrp:            |
    |      OrigSTAN=000601       |
    |        (original txn STAN, |
    |         NOT the TOR STAN)  |
    |                            |
    |<-- ReversalResponse -------|
    |    RespCode=000            |
    |                            |
    | If TOR retry also times    |
    | out: retry again up to N   |
    | times. After max retries:  |
    | log for manual             |
    | reconciliation.            |
    |                            |
```

### Late Response Scenario

```
Softpay App              Fiserv Rapid Connect
    |                            |
    |--- CreditRequest --------->|
    |    STAN=000601             |
    |    RefNum=600000000001     |
    |                            |
    |    ... timeout ...         |
    |                            |
    |--- ReversalRequest ------->|
    |    ReversalInd=Timeout      |
    |    STAN=000602             |
    |    RefNum=600000000001     |
    |                            |
    |<-- CreditResponse ---------|  <-- LATE response to
    |    (original auth response)|      original request
    |                            |
    |<-- ReversalResponse -------|  <-- TOR response
    |    RespCode=000            |
    |                            |
    | CONFLICT: Both responses   |
    | received. The TOR already  |
    | reversed the auth.         |
    | --> Log for manual         |
    |     reconciliation.        |
    | --> Do NOT send a          |
    |     Completion for the     |
    |     late auth response.    |
    |                            |
```

### Key Points -- 2026 UMF Changes for TOR

- **Scheme-specific references are EXEMPT for Timeout Reversals** (per 2026 UMF Changes):
  - Visa `TransID` -- exempt
  - MasterCard `BanknetData` -- exempt
  - Amex `AmExTranID` -- exempt
  - Discover `DiscoverNRID` -- exempt
- This exemption makes sense because TORs are sent when no response was received, so
  these scheme-specific values are not available.
- **OrigAuthGrp in TOR** -- only include fields that are known:
  - `OrigSTAN` -- the STAN of the original (timed-out) transaction.
  - `OrigLocalDateTime` -- from the original request.
  - `OrigTranDateTime` -- from the original request.
  - Do NOT include `OrigAuthID`, `OrigRespCode`, `OrigResponseDate` (not received).
- **TOR testing is mandatory** before entering the Certification stage.
  See `Timeout_Reversal_Testing_QRG.pdf`.
- **TORVoid** -- Timeout Reversal of a Void message. Uses `ReversalInd=TORVoid`.
  Per 2026 changes: for TORVoid, the `OrigLocalDateTime` must contain the Original Local
  Date and Time from the Void message being reversed, not the original transaction.
  **TORVoid is only applicable to Prepaid Closed Loop.**

---

## 7. Refund

Refunds use `CreditRequest` with `TxnType=Refund` -- NOT `ReversalRequest`. A refund
credits funds back to the cardholder and can be referenced (linked to original) or
unreferenced.

### Sequence Diagram

```
Softpay App              Fiserv Rapid Connect
    |                            |
    | REFERENCED REFUND          |
    | (linked to original auth)  |
    |                            |
    |--- CreditRequest --------->|
    |    CommonGrp:              |
    |      PymtType=Credit       |
    |      TxnType=Refund        |
    |      STAN=000701 (NEW)     |
    |      RefNum=700000000001   |
    |        (NEW RefNum for the |
    |         refund -- NOT the  |
    |         original auth's    |
    |         RefNum)            |
    |      TxnAmt=000000005000   |
    |      POSEntryMode=901      |
    |      POSCondCode=00        |
    |      TxnCrncy=840          |
    |    CardGrp:               |
    |      Track2Data=<T2>       |
    |      CardType=Visa         |
    |    OrigAuthGrp:            |
    |      OrigAuthID=OK1234     |
    |      OrigRespCode=000      |
    |      OrigSTAN=000123       |
    |      OrigResponseDate=0407 |
    |      OrigLocalDateTime=... |
    |      OrigTranDateTime=...  |
    |    VisaGrp:               |
    |      TransID=<from orig>   |
    |                            |
    |<-- CreditResponse ---------|
    |    RespCode=000            |
    |                            |

    | UNREFERENCED REFUND        |
    | (no original auth link)    |
    |                            |
    |--- CreditRequest --------->|
    |    CommonGrp:              |
    |      PymtType=Credit       |
    |      TxnType=Refund        |
    |      STAN=000702           |
    |      RefNum=700000000002   |
    |      TxnAmt=000000002500   |
    |    CardGrp:               |
    |      Track2Data=<T2>       |
    |      CardType=MasterCard   |
    |    (NO OrigAuthGrp)        |
    |    MCGrp:                 |
    |      RefundType=<mandatory>|
    |                            |
    |<-- CreditResponse ---------|
    |    RespCode=000            |
    |                            |
```

### 2026 UMF Change: MasterCard RefundType

Per the 2026 UMF Changes, the `RefundType` field is **mandatory** when:
- `TxnType=Refund`, AND
- `CardType=MasterCard` or `CardType=MaestroInt`

This field was not previously required. Implementations must now include it for all
MasterCard/Maestro refund transactions.

### Refund Variants

| Refund Type          | Message Type     | PymtType | Notes                                     |
|---------------------|-----------------|----------|-------------------------------------------|
| Credit Refund        | CreditRequest    | Credit   | Standard credit card refund               |
| Online Refund        | CreditRequest    | Credit   | Online refund authorization (selected)    |
| PIN Debit Refund     | DebitRequest     | Debit    | Refund for PIN Debit (selected); requires PINGrp |
| Void of Refund       | ReversalRequest  | Credit   | Cancel a previously submitted refund      |

### Key Points

- **Refund gets a NEW RefNum** -- unlike Completion and Void, a refund is a new transaction.
- **Referenced vs. Unreferenced:**
  - Referenced: includes `OrigAuthGrp` linking back to the original authorization. Preferred.
  - Unreferenced: no `OrigAuthGrp`. Used when the original transaction data is not available.
- **PIN Debit Refund** uses `DebitRequest` with `TxnType=Refund` and `PymtType=Debit`.
- **Void of Refund** -- to cancel a refund, send a `ReversalRequest` with `ReversalInd=Void`
  referencing the refund transaction. Must be within 25 minutes.
- **2026 change for Refund:** When `TxnType=Refund`, the Bill Payment Transaction Indicator
  and `POSCondCode` with value '04' should NOT be sent.

---

## 8. DCC Flow

Dynamic Currency Conversion allows foreign cardholders to pay in their home currency.
DCC is selected for RSO024 and applies to **Visa and Mastercard only**.

### Sequence Diagram

```
Cardholder            Softpay App              Fiserv Rapid Connect
(foreign card)             |                            |
    |                      |                            |
    |--- Tap Card -------->|                            |
    |                      |                            |
    | STEP 1: Read card and determine DCC eligibility   |
    |                      |                            |
    |   Softpay checks:                                 |
    |   - Card has Global Credit AID (not CAID)         |
    |     CAID transactions are NOT DCC eligible         |
    |     (per EMV Guide BP 1100)                       |
    |   - Do NOT use Tag 9F42 (Application Currency     |
    |     Code) for DCC eligibility                     |
    |   - Card is Visa or Mastercard                    |
    |   - Transaction is above contactless CVM limit    |
    |     DCC is NOT supported below CVM limit          |
    |     (per EMV Guide RQ 4201)                       |
    |                      |                            |
    | STEP 2: Get DCC rate from rate provider            |
    |                      |                            |
    |   Softpay queries DCC rate provider:              |
    |   - Card BIN / issuer country                     |
    |   - Cardholder currency (e.g., EUR, GBP)          |
    |   - Exchange rate + markup                        |
    |   - Conversion date                              |
    |                      |                            |
    | STEP 3: Display currency choice to cardholder     |
    |                      |                            |
    |<-- "Pay in EUR or    |                            |
    |     USD?" -----------|                            |
    |                      |                            |
    |--- Selects EUR ----->|                            |
    |   (home currency)    |                            |
    |                      |                            |
    | STEP 4: Send Authorization with DCCGrp            |
    |                      |                            |
    |   NOTE (Contact EMV): Tag 5F2A (Transaction       |
    |   Currency Code) must be set to the DCC currency  |
    |   BEFORE 1st Generate AC.                         |
    |                      |                            |
    |   NOTE (Contactless): The 1st Generate AC         |
    |   cryptogram uses the ORIGINAL currency (USD),    |
    |   not the converted currency (per BP 1200).       |
    |                      |                            |
    |                      |--- CreditRequest --------->|
    |                      |    CommonGrp:              |
    |                      |      PymtType=Credit       |
    |                      |      TxnType=Authorization |
    |                      |      TxnAmt=<DCC amount    |
    |                      |        in home currency    |
    |                      |        minor units>        |
    |                      |      TxnCrncy=978 (EUR)    |
    |                      |      STAN=000801           |
    |                      |      RefNum=800000000001   |
    |                      |    CardGrp:               |
    |                      |      Track2Data=<T2>       |
    |                      |      CardType=Visa         |
    |                      |    EMVGrp:                |
    |                      |      EMVData=<TLV tags>    |
    |                      |    DCCGrp:                |
    |                      |      <DCC conversion rate> |
    |                      |      <original cardholder  |
    |                      |        currency code>      |
    |                      |      <conversion date>     |
    |                      |      <original txn amount  |
    |                      |        in USD>             |
    |                      |    VisaGrp: (if Visa)     |
    |                      |                            |
    |                      |<-- CreditResponse ---------|
    |                      |    RespCode=000            |
    |                      |    AuthID=DCC901           |
    |                      |                            |
    |<-- "Approved         |                            |
    |     EUR 42.50" ------|                            |
    |                      |                            |
    | STEP 5: If cardholder selects USD (declines DCC)  |
    |   --> Send normal auth WITHOUT DCCGrp             |
    |   --> TxnCrncy=840 (USD)                          |
    |                      |                            |
    | STEP 6: Completion references DCC authorization   |
    |                      |                            |
    |                      |--- CreditRequest --------->|
    |                      |    TxnType=Completion      |
    |                      |    TxnCrncy=978 (EUR)      |
    |                      |    TxnAmt=<DCC amount>     |
    |                      |    STAN=000802 (NEW)       |
    |                      |    RefNum=800000000001     |
    |                      |      (SAME as auth)        |
    |                      |    OrigAuthGrp={...}       |
    |                      |    DCCGrp={same as auth}   |
    |                      |                            |
    |                      |<-- CreditResponse ---------|
    |                      |    RespCode=000            |
    |                      |                            |
```

### Key Points

- **DCC eligibility rules:**
  - Only Visa and Mastercard. Not Amex, Discover, Diners Club.
  - Only Global Credit AID. If only Common Debit AID (CAID) is present, the card is NOT DCC
    eligible.
  - NOT supported below the contactless CVM limit (per RQ 4201).
  - Do NOT use Tag 9F42 (Application Currency Code) for eligibility determination (per BP 1100).
- **Contactless cryptogram note:** For contactless transactions, the 1st Generate AC
  cryptogram is generated with the ORIGINAL currency (USD), not the converted DCC
  currency (per BP 1200). This differs from contact EMV where Tag 5F2A is set to the DCC
  currency before 1st Generate AC.
- **Settlement** occurs in the DCC currency. Merchant funding remains in USD.
- **Rate provider** -- open question: whether Fiserv provides the DCC rate feed or Softpay
  integrates with a third-party provider.
- **Receipt requirements** -- DCC transactions likely require showing both the original (USD)
  and converted amounts on the receipt.

---

## 9. Master Session Encryption Key Request

The Master Session encryption key is used to encrypt PIN blocks for PIN Debit transactions.
The key must be refreshed every 24 hours via an `AdminRequest`.

### Sequence Diagram

```
Softpay App              Fiserv Rapid Connect
    |                            |
    | Triggered:                 |
    |   - At startup / app init  |
    |   - Every 24 hours         |
    |   - Before first PIN Debit |
    |     transaction of the day |
    |                            |
    |--- AdminRequest ---------->|
    |    CommonGrp:              |
    |      PymtType=Credit       |
    |      TxnType=              |
    |        EncryptionKeyRequest|
    |      TPPID=RSO024          |
    |      MerchID=<MID>         |
    |      TermID=<TID>          |
    |      GroupID=20001         |
    |      STAN=000901           |
    |      RefNum=900000000001   |
    |      LocalDateTime=...     |
    |      TrnmsnDateTime=...    |
    |                            |
    |<-- AdminResponse ----------|
    |    RespGrp:               |
    |      RespCode=000          |
    |    <Key data:>             |
    |      New session key       |
    |        encrypted under     |
    |        pre-shared master   |
    |        key                 |
    |      MSKeyID=<key ID>      |
    |      Key check value       |
    |      TR-31 key block       |
    |        format              |
    |                            |
    | Softpay decrypts session   |
    | key using master key.      |
    | Stores session key and     |
    | MSKeyID for use in PINGrp. |
    |                            |
    | Session key is now active  |
    | for the next 24 hours.     |
    |                            |
```

### Key Points

- **TR-31 Key Block** is selected in the project profile -- the session key is wrapped in
  an ANSI TR-31 key block format for secure transmission.
- **Master key** is a pre-shared key established during the onboarding/boarding process.
  It does not change frequently.
- **Session key** changes every 24 hours. The `MSKeyID` from the response must be
  included in every `PINGrp.MSKeyID` field for PIN Debit transactions.
- **Key check value** -- used to verify the session key was decrypted correctly.
- **AdminRequest** does not require card data, EMV data, or amounts.
- **If the key request fails:** retry before processing any PIN Debit transactions.
  PIN Debit transactions cannot proceed without a valid session key.
- **PINless POS Debit** does not require the session key (no PIN encryption needed).

---

## 10. Fields to Store from Authorization Response

After every successful Authorization, the following fields must be stored for use in
subsequent messages (Completion, Void, TOR).

| Field | Source Group | Store? | Echo in Completion? | Echo in Void? | Echo in TOR? |
|-------|-------------|--------|---------------------|---------------|--------------|
| AuthID | RespGrp | Yes | Yes (`OrigAuthGrp.OrigAuthID`) | Yes (`OrigAuthGrp.OrigAuthID`) | No (not received) |
| ResponseDate | RespGrp | Yes | Yes (`OrigAuthGrp.OrigResponseDate`) | Yes (`OrigAuthGrp.OrigResponseDate`) | No (not received) |
| RespCode | RespGrp | Yes | Yes (`OrigAuthGrp.OrigRespCode`) | Yes (`OrigAuthGrp.OrigRespCode`) | No (not received) |
| STAN | CommonGrp | Yes | Yes (`OrigAuthGrp.OrigSTAN`) | Yes (`OrigAuthGrp.OrigSTAN`) | Yes (`OrigAuthGrp.OrigSTAN`) |
| LocalDateTime | CommonGrp | Yes | Yes (`OrigAuthGrp.OrigLocalDateTime`) | Yes (`OrigAuthGrp.OrigLocalDateTime`) | Yes (`OrigAuthGrp.OrigLocalDateTime`) |
| TrnmsnDateTime | CommonGrp | Yes | Yes (`OrigAuthGrp.OrigTranDateTime`) | Yes (`OrigAuthGrp.OrigTranDateTime`) | Yes (`OrigAuthGrp.OrigTranDateTime`) |
| RefNum | CommonGrp | Yes | Yes (must match in `CommonGrp.RefNum`) | Yes (must match in `CommonGrp.RefNum`) | Yes (must match in `CommonGrp.RefNum`) |
| Visa TransID | VisaGrp | Yes | Yes (`VisaGrp.TransID`) | Yes (`VisaGrp.TransID`) | No (**EXEMPT** per 2026 change) |
| MC BanknetData | MCGrp | Yes | Yes (`MCGrp.BanknetData`) | Yes (`MCGrp.BanknetData`) | No (**EXEMPT** per 2026 change) |
| Amex AmExTranID | AmexGrp | Yes | Yes (`AmexGrp.AmExTranID`) | Yes (`AmexGrp.AmExTranID`) | No (**EXEMPT** per 2026 change) |
| Discover NRID | DSGrp | Yes | Yes (`DSGrp.DiscoverNRID`) | Yes (`DSGrp.DiscoverNRID`) | No (**EXEMPT** per 2026 change) |
| CardType | CardGrp | Yes | Yes (`CardGrp.CardType`) | Yes (`CardGrp.CardType`) | Yes (`CardGrp.CardType`) |
| AuthNetID | RespGrp | Yes | No | No | No |

### Storage Rules

1. **Always store every field listed above** from every authorization response, regardless of
   whether a Completion is expected immediately.
2. **Scheme-specific fields** (TransID, BanknetData, AmExTranID, DiscoverNRID) -- store
   whichever is returned based on the card type. Only one scheme group is populated per
   transaction.
3. **AuthNetID** -- store for reconciliation and reporting purposes but it is not echoed in
   subsequent messages.
4. **TOR exemption** (2026 change) -- scheme-specific references and response-derived fields
   (AuthID, ResponseDate, RespCode) are exempt for Timeout Reversals because they were
   never received.
5. **Persistence** -- fields must survive app restarts and device reboots. Store in persistent
   local storage and sync to backend. Completions may be sent hours after authorization
   (e.g., Restaurant end-of-shift).

---

## 11. STAN & RefNum Management

### STAN (System Trace Audit Number)

| Rule | Detail |
|------|--------|
| Format | 6 numeric digits |
| Range | 000001 -- 999999 |
| Uniqueness | Unique per day per MID+TID combination |
| Increment | Sequential: 000001, 000002, ..., 999999 |
| Rollover | When 999999 is reached, roll over to 000001 |
| Scope | Each message gets a **new** STAN, including retries and TORs |
| TOR STAN | The TOR gets a new STAN; `OrigAuthGrp.OrigSTAN` references the original |

### RefNum (Reference Number)

| Rule | Detail |
|------|--------|
| Format | Up to 12 numeric characters (per sample payloads; spec allows up to 22 alphanumeric) |
| Uniqueness | Unique per day per MID+TID combination |
| Transaction chain | **SAME** RefNum across: Authorization --> Completion --> Void |
| Timeout | If a transaction times out, the TOR uses the **SAME** RefNum as the original |
| Refund | A Refund gets a **NEW** RefNum (it is a new transaction) |
| Void of Refund | The Void of Refund uses the **SAME** RefNum as the Refund being voided |

### Transaction Chain Examples

```
SCENARIO A: Normal Purchase
  Authorization   STAN=000001  RefNum=100000000001
  Completion      STAN=000002  RefNum=100000000001  (SAME)

SCENARIO B: Purchase with Void of Authorization
  Authorization   STAN=000003  RefNum=100000000002
  Void            STAN=000004  RefNum=100000000002  (SAME)

SCENARIO C: Purchase with Timeout
  Authorization   STAN=000005  RefNum=100000000003
  (timeout -- no response)
  TOR             STAN=000006  RefNum=100000000003  (SAME)
                               OrigSTAN=000005

SCENARIO D: TOR also times out
  Authorization   STAN=000005  RefNum=100000000003
  (timeout)
  TOR attempt 1   STAN=000006  RefNum=100000000003  (SAME)
  (timeout)
  TOR attempt 2   STAN=000007  RefNum=100000000003  (SAME)
                               OrigSTAN=000005 (always the original)

SCENARIO E: Refund then Void of Refund
  (original auth)  STAN=000008  RefNum=100000000004
  (completion)     STAN=000009  RefNum=100000000004
  Refund           STAN=000010  RefNum=200000000001  (NEW RefNum)
  Void of Refund   STAN=000011  RefNum=200000000001  (SAME as refund)
```

### Implementation Notes

- **STAN counter** -- maintain a single atomic counter per MID+TID. Increment before
  building each message. Thread-safe access is required if multiple transactions can
  be in flight concurrently.
- **RefNum generator** -- generate a unique value per transaction chain. A simple
  incrementing counter works. Ensure the value is stored persistently so it survives
  restarts within the same business day.
- **Day boundary** -- STAN and RefNum uniqueness is per day. At midnight (local time or
  UTC -- confirm with Fiserv), counters can theoretically reset, but rolling counters
  that never reset are also acceptable as long as uniqueness is maintained within any
  24-hour period.
- **Certification TID** -- during certification, all transactions must use TID 00000001.

---

## 12. Timeout Handling Rules

### Decision Flowchart

```
Send Request (Auth, Completion, Refund, Void, etc.)
    |
    v
Start timer (X seconds)
    |
    v
Response received within X seconds?
    |
    +--- YES --> Process response normally
    |              |
    |              v
    |            RespCode=000? --> Success
    |            RespCode=xxx? --> Handle decline/error
    |
    +--- NO (timeout) --> Send Timeout Reversal (TOR)
                            |
                            v
                          Build ReversalRequest:
                            - ReversalInd=Timeout
                            - SAME RefNum as original
                            - NEW STAN
                            - OrigAuthGrp.OrigSTAN = original STAN
                            - Scheme refs: EXEMPT (2026)
                            |
                            v
                          TOR response received?
                            |
                            +--- YES --> TOR successful
                            |              |
                            |              v
                            |            Transaction is reversed.
                            |            Inform cardholder: "Transaction
                            |            cancelled, please try again."
                            |
                            +--- NO (TOR also timed out)
                                           |
                                           v
                                         Retry TOR:
                                           - NEW STAN
                                           - SAME RefNum
                                           - OrigSTAN = original txn STAN
                                           |
                                           v
                                         Max retries reached?
                                           |
                                           +--- NO --> Retry TOR
                                           |
                                           +--- YES --> Log for manual
                                                        reconciliation.
                                                        Alert operations
                                                        team.
```

### Rules Summary

| Rule | Detail |
|------|--------|
| Timeout trigger | No response within ClientTimeout + buffer (recommended: 30s ClientTimeout + 15s buffer = 45s total read timeout) |
| TOR message | `ReversalRequest` with `ReversalInd=Timeout` |
| RefNum in TOR | **SAME** as the timed-out transaction |
| STAN in TOR | **NEW** unique STAN |
| OrigSTAN in TOR | STAN of the **original** timed-out transaction (not a prior TOR retry) |
| Scheme refs in TOR | **EXEMPT** per 2026 UMF Changes (TransID, BanknetData, AmExTranID, DiscoverNRID) |
| OrigAuthGrp in TOR | Only include fields from the original REQUEST (STAN, LocalDateTime, TrnmsnDateTime). Omit fields from the RESPONSE (AuthID, RespCode, ResponseDate) since no response was received. |
| TOR retry | If TOR times out, retry with new STAN, same RefNum, same OrigSTAN |
| Max TOR retries | 3 attempts max (per Datawire Compliance Test Form) |
| Max transaction retries | 3 automated retries max (Simple and InitiateSession only) |
| After max retries | Log transaction for manual reconciliation |
| **TOR priority** | **ALWAYS send TOR before retrying original transaction** (Datawire compliance requirement) |
| **TOR failure** | **If TOR fails, DO NOT retry the original transaction** (Datawire compliance requirement) |
| Late response | If original response arrives after TOR is sent, log conflict for manual reconciliation. Do NOT process the late response (do not send Completion). |
| TOR for Void | `ReversalInd=TORVoid` -- **Prepaid Closed Loop only** |
| Never do | Never send both an original transaction retry AND a TOR for the same RefNum |
| Session error | Terminate session on error, then start a new session (do not retry within session) |
| Datawire vs host errors | Datawire retry guidance applies only to Datawire-generated errors; host processor error handling is integrator's responsibility |
| TOR testing | **Mandatory** before entering Certification stage. Use `Timeout_Reversal_Testing_QRG.pdf`. |

### Timeout Values (Confirmed from Datawire Documentation)

| Parameter | Staging | Production | Notes |
|-----------|---------|------------|-------|
| **ClientTimeout** | 15-40s | 15-35s | Sent in Datawire envelope; uniform for all transaction types |
| **Recommended ClientTimeout** | **30s** | **30s** | Per sample code and Datawire guidelines |
| **Application Read Timeout** | **45s** | **45s** | Must be a few seconds LONGER than ClientTimeout (sample code: 45s) |
| **SRS Retry Interval** | 30s | 30s | Between SRS retry attempts |

**Notes:**
- ClientTimeout below the minimum (15s) will result in errors.
- ClientTimeout above the maximum defaults to the maximum allowed value.
- The ClientTimeout tells Datawire how long to wait for the host response; the application read timeout is how long Softpay waits for Datawire's response (must be longer to allow Datawire to return an error if the host doesn't respond).
- No per-transaction-type differentiation — the same ClientTimeout applies to authorizations, completions, reversals, TORs, and key requests.

Source: `Datawire Parameter Guidelines for Rapid Connect.pdf`, `Datawire Compliance Test Form` (v3.4.7), `RCToolkitSampleCode`

---

## Quick Reference: Message Type Selection

| Scenario | Request Type | TxnType | ReversalInd | RefNum |
|----------|-------------|---------|-------------|--------|
| Credit Authorization | CreditRequest | Authorization | -- | New |
| Credit Completion | CreditRequest | Completion | -- | Same as auth |
| Debit Authorization | DebitRequest | Authorization | -- | New |
| Debit Completion | DebitRequest | Completion | -- | Same as auth |
| Credit Refund | CreditRequest | Refund | -- | New |
| PIN Debit Refund | DebitRequest | Refund | -- | New |
| Online Refund | CreditRequest | Refund | -- | New |
| Void of Auth | ReversalRequest | Authorization | Void | Same as auth |
| Void of Completion | ReversalRequest | Completion | Void | Same as auth |
| Void of Refund | ReversalRequest | Refund | Void | Same as refund |
| TOR of Auth | ReversalRequest | Authorization | Timeout | Same as auth |
| TOR of Completion | ReversalRequest | Completion | Timeout | Same as auth |
| TOR of Void | ReversalRequest | (original) | TORVoid | Same as void (Prepaid only) |
| Encryption Key Req | AdminRequest | EncryptionKeyRequest | -- | New |
| Digital Wallet Auth | CreditRequest | Authorization | -- | New (+ EcommGrp) |
| DCC Auth | CreditRequest | Authorization | -- | New (+ DCCGrp) |

---

## POS Entry Mode Reference

| Code | Entry Method | Softpay Usage |
|------|-------------|---------------|
| 071 | Contactless EMV (chip data via NFC) | **Primary** -- standard contactless tap |
| 901 | Contactless magnetic stripe | Fallback -- MSR data read via NFC |
| 051 | Contact EMV chip | Not applicable (no chip reader on SoftPOS) |
| 011 | Manual key entry | Not selected |
| 801 | Swiped (magnetic stripe) | Selected in profile but not applicable to SoftPOS |

---

*Document generated for Softpay integration team. Based on UMF v15.04.5 (February 25, 2026),
2026 UMF Changes document, EMV Implementation Guide v2025, RSO024 Project Profile, and
Rapid Connect SDK documentation.*
