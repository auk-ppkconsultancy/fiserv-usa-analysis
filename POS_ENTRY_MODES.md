# POS Entry Modes & Terminal Configuration Reference

**Fiserv Rapid Connect -- Project RSO024, UMF v15.04**
**Softpay SoftPOS Integration**
**Last updated:** 2026-04-13 (reconciled against `TestTransactions_RSO024.csv`, 424 cases)

This document is a practical developer reference for constructing the correct POS Entry Mode,
terminal capability fields, and related identifiers when sending transactions from a Softpay
SoftPOS device to Fiserv Rapid Connect.

---

## 0. Verified Truth from the Official Test Script (2026-04-13)

The 424-case certification test script `TestTransactions_RSO024.csv` **only uses two POSEntryMode values**:

| POSEntryMode | Meaning | Count | TermCatCode observed |
|---|---|---|---|
| `911` | Contactless (EMV + MSR combined path) | 250 | `09` |
| `901` | Swiped (track data from magnetic stripe) | 173 | `01` or `09` |

Additional invariants observed across **every** test case:

- `POSCondCode = 00`
- `TermEntryCapablt = 01`
- `TermLocInd = 0`
- `CardCaptCap = 1`
- `TPPID = RSO024`
- `GroupID = 20001`
- `TermID = 00000001`
- `TermClassCode` (Debit tests only) is either `mPOSCPoCNoPIM` or `mPOSCPoCPIN`

**Digital Wallet:** Only `DigWltProgType=ApplePay` appears in the test script (134 cases). Google Pay and Samsung Pay are listed in the Project Profile but not exercised by cert tests.

**Consequence for implementation:** The more granular contactless codes often discussed in UMF training material (`071`, `072`, `076`, `910`, `912`, etc.) are **not** the codes Fiserv's sandbox TestCase matcher expects for TPP RSO024. Probes sent with POSEntryMode=`000`, `071`, `910`, or `912` will receive `109 INVALID TERM` because they do not match any official test case. **Use `911` for contactless and `901` for swiped.**

The sections below preserve the broader taxonomy for reference, but anything that conflicts with the table above is superseded by the test script.

---

## Table of Contents

1. [SoftPOS Decision Tree](#1-softpos-decision-tree)
2. [Recommended Values for Softpay SoftPOS](#2-recommended-values-for-softpay-softpos)
3. [Terminal Configuration for SoftPOS](#3-terminal-configuration-for-softpos)
4. [Digital Wallet Detection](#4-digital-wallet-detection)
5. [Card Type Determination (BIN Range Lookup)](#5-card-type-determination-bin-range-lookup)
6. [Complete Reference Tables](#6-complete-reference-tables)
7. [Implementation Notes & Edge Cases](#7-implementation-notes--edge-cases)

---

## 1. SoftPOS Decision Tree

Use this decision flow every time a customer presents a card or device at the Softpay NFC reader.
The output is the 3-character `POSEntryMode` value plus any supplementary digital wallet fields.

```
Customer taps card/device via NFC
|
+-- Is the read a full EMV chip transaction (contactless ICC)?
|   |
|   +-- YES --> Account Number Entry Mode = "07"
|   |   |
|   |   +-- Is it a digital wallet (Apple Pay / Google Pay / Samsung Pay)?
|   |   |   |
|   |   |   +-- YES --> POSEntryMode = "07" + PIN digit (see below)
|   |   |   |           Also set:
|   |   |   |             DigWltInd = "Passthru"
|   |   |   |             DigWltProgType = "ApplePay" | "AndroidPay" | "SamsungPay"
|   |   |   |           (See Section 4 for wallet detection logic)
|   |   |   |
|   |   |   +-- NO --> POSEntryMode = "07" + PIN digit (see below)
|   |   |              (Standard contactless EMV chip card)
|   |   |
|   |   +-- Determine PIN capability (3rd digit):
|   |       |
|   |       +-- mPOS software-based PIN is enabled --> 3rd digit = "6"
|   |       +-- Hardware PIN entry is available     --> 3rd digit = "1"
|   |       +-- No PIN for this transaction         --> 3rd digit = "2"
|   |
|   |   RESULT EXAMPLES:
|   |     "071" = Contactless ICC + PIN entry capable
|   |     "072" = Contactless ICC + No PIN entry capability
|   |     "076" = Contactless ICC + mPOS software PIN
|   |
+-- Is it a contactless magnetic stripe (MSD) read?
|   |
|   +-- YES --> Account Number Entry Mode = "91"
|   |   |
|   |   +-- Same PIN digit logic as above
|   |
|   |   RESULT EXAMPLES:
|   |     "911" = Contactless MSR + PIN entry capable
|   |     "912" = Contactless MSR + No PIN entry capability
|   |     "916" = Contactless MSR + mPOS software PIN
|   |
|   NOTE: MSD-mode contactless is legacy. Most modern cards and all digital wallets
|   use contactless ICC (07). MSD may still appear with older prepaid cards.
|
+-- Did the contactless read fail and the operator manually keys the PAN?
    |
    +-- YES --> POSEntryMode = "791" or "792"
                (EMV fallback to manual entry)
    
    NOTE: On a SoftPOS device there is no magnetic stripe reader, so fallback
    code "80" (EMV fallback to mag stripe) does NOT apply. If the NFC tap fails,
    the only fallback option is manual key entry ("79x") or retry the tap.
```

**Summary: The two primary POSEntryMode values for Softpay SoftPOS are `"071"`, `"072"`, and `"076"`.**
Most contactless transactions from physical cards and digital wallets will use Account Number
Entry Mode `07` (Contactless ICC Read).

---

## 2. Recommended Values for Softpay SoftPOS

> **These values are the ones accepted by TPP RSO024 per the 2026-04-13 test script. Anything else returns `109 INVALID TERM` or `TP0003`.**

### Primary Transaction Scenarios (verified against `TestTransactions_RSO024.csv`)

| Scenario | POSEntryMode | POSCondCode | TermCatCode | TermEntryCapablt | TermClassCode | DigWltProgType |
|---|---|---|---|---|---|---|
| Contactless card tap / digital wallet | `911` | `00` | `09` | `01` | `mPOSCPoCNoPIN` (no PIN) or `mPOSCPoCPIN` (with PIN) | (omit unless wallet; then `ApplePay`) |
| Apple Pay tap | `911` | `00` | `09` | `01` | as above | `ApplePay` |
| Google Pay tap | `911` | `00` | `09` | `01` | as above | *not exercised in cert tests — see workshop Q* |
| Samsung Pay tap | `911` | `00` | `09` | `01` | as above | *not exercised in cert tests — see workshop Q* |
| Swiped (not applicable to SoftPOS devices, but permitted by TPP) | `901` | `00` | `01` or `09` | `01` | — | — |

### Field-by-Field Rationale

| Field | Recommended Value | Why |
|---|---|---|
| `POSEntryMode` (1st-2nd digits) | `07` | Softpay reads the card via NFC using contactless EMV (ICC) kernel |
| `POSEntryMode` (3rd digit) | `1`, `2`, or `6` | `6` if mPOS software PIN is enabled; `1` if traditional PIN capable; `2` if no PIN for this txn type |
| `POSCondCode` | `00` | Cardholder present, card present -- standard face-to-face tap scenario |
| `TermCatCode` | `09` | Mobile POS (mPOS) -- cellphone or tablet acting as a terminal |
| `TermEntryCapablt` | `06` | Proximity/contactless chip (NFC/RFID) -- the primary capability of SoftPOS |

### PIN Digit Selection Logic

| Condition | 3rd Digit | Notes |
|---|---|---|
| Softpay is configured for mPOS software-based PIN entry (MPoC PIN pad) | `6` | **Preferred for SoftPOS.** This is the dedicated mPOS software PIN code. |
| Softpay has PIN capability but PIN was not required for this transaction | `1` | Terminal CAN accept PIN even though it was not used. Reports capability. |
| PIN entry is not available or not applicable (e.g., credit below CVM limit) | `2` | No PIN entry capability for this transaction. |
| PIN pad is temporarily inoperative | `3` | Use only if the software PIN component has failed/crashed. |

**Important:** For Softpay SoftPOS, the recommended 3rd digit is `6` (mPOS Software-based PIN Entry
Capability) when PIN debit is enabled. This signals to Fiserv that PIN is collected via a certified
software-based PIN entry solution rather than a hardware PIN pad. If PIN is not supported or not
needed, use `2`.

---

## 3. Terminal Configuration for SoftPOS

These values should be set once during terminal provisioning and sent consistently on every
transaction from the Softpay device.

### Fixed Terminal Parameters

| UMF Field | Value | Description |
|---|---|---|
| `TermCatCode` | `09` | Mobile POS (mPOS cellphone/tablet) |
| `TermEntryCapablt` | `06` | Proximity terminal -- contactless chip/RFID |
| `POSCondCode` | `00` | Cardholder present, card present (default for tap) |

### When to Override Terminal Entry Capability

| Situation | TermEntryCapablt |
|---|---|
| Normal NFC contactless ICC tap | `06` (Proximity -- contactless chip/RFID) |
| Contactless MSD-grade read (rare, legacy cards) | `11` (Proximity -- contactless magnetic stripe) |
| Hybrid terminal with NFC + mag stripe + chip | `12` (Hybrid) -- **not applicable to pure SoftPOS** |
| Manual key entry fallback | `10` (Manual entry only) |

### Fields NOT Applicable to SoftPOS

The following `TermEntryCapablt` values should **never** be sent from a Softpay SoftPOS device:

- `02` (Magnetic stripe only) -- SoftPOS has no mag stripe reader
- `03` (Magnetic stripe and key entry) -- no mag stripe
- `04` (Mag stripe + key entry + chip) -- no mag stripe or contact chip reader
- `08` (Chip only) -- SoftPOS does not have a contact chip reader
- `09` (Chip and magnetic stripe) -- neither hardware component exists

---

## 4. Digital Wallet Detection

When a customer taps with Apple Pay, Google Pay, or Samsung Pay, the contactless kernel reads
the same EMV data as a physical card, but specific EMV tags reveal the wallet type. The Softpay
kernel must extract these indicators and populate the `DigWltProgType` and `DigWltInd` fields.

### Detection Method: EMV Tag 9F6E (Form Factor Indicator)

Tag 9F6E is the primary mechanism for identifying mobile/wearable form factors:

| Byte Position | Meaning |
|---|---|
| Byte 1 (bits 8-5) | Consumer Payment Device Form Factor: `1000` = phone/tablet, `0010` = watch/wristband, `0011` = card |
| Byte 1 (bits 4-1) | Consumer Payment Device Features: presence of passcode, signature, etc. |
| Byte 2 | Consumer Payment Device Technology and additional data |

**Practical detection approach:**

1. **Check for DPAN / Token PAN.** Digital wallets use device-specific tokenized PANs (DPANs)
   rather than the physical card PAN. The presence of EMV tag 9F26 (Application Cryptogram)
   combined with token-range BINs indicates a wallet.

2. **Check tag 9F6E (Form Factor Indicator).** If byte 1 high nibble indicates phone/tablet
   (`0x8x`) or wearable (`0x2x`), it is a digital wallet.

3. **Check tag 9F6C (Application Version Number -- Terminal) or kernel ID.** Different wallet
   implementations activate different contactless kernels:
   - **Visa (Apple Pay, Google Pay, Samsung Pay):** Visa payWave kernel -- look for AID `A0000000031010`
   - **Mastercard (Apple Pay, Google Pay, Samsung Pay):** Mastercard PayPass kernel -- look for AID `A0000000041010`

4. **Check tag DF8101 or proprietary tags** returned by the contactless kernel that indicate
   mobile device type.

### Wallet-Specific Indicators

| Wallet | DigWltProgType | DigWltInd | How to Detect |
|---|---|---|---|
| Apple Pay | `ApplePay` | `Passthru` | Tag 9F6E indicates phone/watch form factor; Visa/MC token AID; Apple-specific tag DF8101 may be present |
| Google Pay | `AndroidPay` | `Passthru` | Tag 9F6E indicates phone form factor; Android-specific indicators; note the UMF value is `AndroidPay` (legacy name) |
| Samsung Pay | `SamsungPay` | `Passthru` | Tag 9F6E indicates phone form factor; Samsung-specific Device Type Indicator |
| Click to Pay | `ClickToPay` | `Passthru` | Not applicable to NFC tap -- this is an e-commerce flow |
| Merchant tokenized | `MerchToken` | `Staged` | Token provided by merchant, not from NFC |

**Key rule:** For NFC-based digital wallets, `DigWltInd` is always `Passthru` (pass-through digital
wallet that stores the cryptogram/Token Block from the card scheme). The `Staged` value is for
wallets like PayPal that use a staged funding model and would not appear in a SoftPOS NFC tap.

### Fallback Detection (When Tag 9F6E Is Absent)

Some older kernel configurations may not return tag 9F6E. In these cases:

1. Check if the PAN falls in a known token range (Visa token BINs start with specific ranges
   assigned by the scheme; Mastercard token ranges likewise).
2. Check the Application PAN Sequence Number (tag 5F34) -- tokens often have specific sequence
   numbers.
3. If uncertain, send the transaction without `DigWltProgType` / `DigWltInd`. Fiserv will
   still process it, but interchange optimization may be lost.

---

## 5. Card Type Determination (BIN Range Lookup)

The `CardType` field in the UMF message must match the card brand. Softpay determines this from
the Primary Account Number (PAN) using BIN (Bank Identification Number) ranges. The contactless
kernel also provides the AID which can be used as a secondary check.

### BIN Range Table

| Card Brand | CardType Value | BIN Range / Pattern | PAN Length | AID Prefix |
|---|---|---|---|---|
| Visa | `Visa` | Starts with `4` | 16 | `A000000003` |
| Mastercard | `MasterCard` | `510000`-`559999`, `222100`-`272099` | 16 | `A000000004` |
| American Express | `Amex` | Starts with `34` or `37` | 15 | `A000000025` |
| Discover | `Discover` | `6011`, `644`-`649`, `65` (see details below) | 16 | `A000000152` |
| Diners Club | `Diners` | `300`-`305`, `3095`, `36`, `38` | 14 or 16 | `A000000152` |
| JCB | `JCB` | `3528`-`3589` | 16 | `A000000065` |
| Maestro (Intl) | `MaestroInt` | Various (typically `50`, `56`-`69`) | 12-19 | `A000000004` |
| UnionPay | `UnionPay` | Starts with `62` | 16-19 | `A000000333` |

### Discover BIN Ranges (Detailed)

Discover has the most complex BIN structure. Match in this order:

| Sub-Range | Pattern |
|---|---|
| `6011` | 6011 0000 0000 0000 - 6011 9999 9999 9999 |
| `644` - `649` | 6440 0000 0000 0000 - 6499 9999 9999 9999 |
| `65` | 6500 0000 0000 0000 - 6599 9999 9999 9999 |

Note: Diners Club cards in the `36` range may also be processed on the Discover network.
Check your Fiserv merchant configuration for Diners/Discover routing rules.

### BIN Lookup Priority

```
1. Check AID from contactless kernel first (most reliable for scheme identification)
2. If AID is ambiguous or shared (e.g., Discover/Diners), fall back to BIN range check
3. For dual-branded cards, the contactless kernel's AID selection determines the network
4. Map the result to the Fiserv CardType enum value
```

### CardType Values -- Complete List

**Credit:**
`Amex`, `Diners`, `Discover`, `JCB`, `MaestroInt`, `MasterCard`, `Visa`, `UnionPay`

**Prepaid:**
`PPayCL` (Prepaid Closed Loop), `GiftCard`, `Prepaid`

**Private Label:**
`PvtLabl`, `Exxon`, `SpeedPass`, `Shell`, `ValeroUCC`, `GenProp`

**Fleet:**
`Fleet`, `MCFleet`, `VisaFleet`, `Voyager`, `Wex`, `WexOTR`, `NGFC`, `FleetCor`, `FleetOne`, `Comdata`

**SoftPOS relevance:** Softpay will primarily encounter `Visa`, `MasterCard`, `Amex`, `Discover`,
`Diners`, `JCB`, and `MaestroInt` for credit/debit. Digital wallets tokenize the underlying card
but the `CardType` should reflect the **network** (Visa/Mastercard/Amex), not the wallet.

---

## 6. Complete Reference Tables

### 6.1 POS Entry Mode (POSEntryMode) -- 3 Characters

#### Account Number Entry Mode (1st and 2nd digits)

| Code | Description | SoftPOS Use |
|---|---|---|
| `00` | Unspecified | No |
| `01` | Manual / Key entered | Fallback only |
| `03` | Barcode | No |
| `04` | OCR | No |
| `05` | Integrated Circuit Read (CVV data reliable) | No (contact chip) |
| `07` | **Contactless ICC Read** / Canadian Debit In-App or In-Browser | **PRIMARY** |
| `08` | AMEX Digital Wallet | Rare -- AMEX-specific |
| `09` | MasterCard Remote Chip | No (e-commerce) |
| `10` | Credential on File | No (recurring/stored) |
| `79` | EMV fallback to manual entry | Fallback only |
| `80` | EMV fallback to magnetic stripe | **Not applicable** (no mag stripe reader) |
| `82` | Contactless Mobile Commerce / Discover InApp | Possible for Discover wallets |
| `86` | EMV switched from contactless to contact | **Not applicable** (no contact reader) |
| `90` | Magnetic Stripe Track Read | **Not applicable** |
| `91` | Contactless Magnetic Stripe Read | Legacy MSD tap |
| `95` | Integrated Circuit Read (CVV data unreliable) | No (contact chip) |

#### PIN Authentication Capability (3rd digit)

| Code | Description | SoftPOS Use |
|---|---|---|
| `0` | Unspecified | Avoid -- always specify |
| `1` | PIN entry capability | Yes, if PIN is supported |
| `2` | No PIN entry capability | Yes, for no-PIN transactions |
| `3` | PIN pad inoperative | Only if software PIN has failed |
| `4` | PIN verified by terminal device | Offline PIN (rare for contactless) |
| `5` | Traditional terminal not available | No |
| `6` | **mPOS Software-based PIN Entry Capability** | **PREFERRED for SoftPOS** |

### 6.2 POS Condition Code (POSCondCode) -- 2 Characters

| Code | Description | SoftPOS Use |
|---|---|---|
| `00` | Cardholder present, card present | **PRIMARY** -- standard tap |
| `01` | Cardholder present, unspecified | Rarely needed |
| `02` | Cardholder present, unattended device | If Softpay runs on unattended kiosk |
| `03` | Cardholder present, suspect fraud | If fraud suspected |
| `04` | MITs (Recurring/Installment/Unscheduled) | Not for tap transactions |
| `05` | Cardholder present, card not present | Referenced refunds |
| `06` | Cardholder present, identity verified | If ID was checked |
| `08` | Card not present -- MOTO | Not applicable |
| `59` | Card not present -- eCommerce | Not applicable |
| `71` | Cardholder present, mag stripe could not be read | **Not applicable** |

### 6.3 Terminal Category Code (TermCatCode) -- 2 Characters

| Code | Description | SoftPOS Use |
|---|---|---|
| `00` | Unspecified | No |
| `01` | Electronic Payment Terminal | No |
| `05` | Automated Fuel Dispenser (AFD) | No |
| `06` | Unattended Customer Terminal | Possible for kiosk mode |
| `07` | Ecommerce, Customer Present | No |
| `08` | Mobile Terminal (Transponder/wireless) | Alternative to 09 |
| `09` | **Mobile POS (mPOS cellphone/tablet)** | **PRIMARY** |
| `12` | Electronic Cash Register | No |
| `13` | IVR | No |
| `17` | Ticket Machine | No |
| `18` | Call Center Operator | No |

### 6.4 Terminal Entry Capability (TermEntryCapablt) -- 2 Characters

| Code | Description | SoftPOS Use |
|---|---|---|
| `00` | Unspecified | No |
| `01` | Terminal not used (eCommerce) | No |
| `02` | Magnetic stripe only | No |
| `03` | Magnetic stripe and key entry | No |
| `04` | Magnetic stripe, key entry, and chip | No |
| `05` | Barcode | No |
| `06` | **Proximity terminal -- contactless chip/RFID** | **PRIMARY** |
| `07` | OCR | No |
| `08` | Chip only (contact) | No |
| `09` | Chip and magnetic stripe | No |
| `10` | Manual entry only | Fallback only |
| `11` | Proximity terminal -- contactless magnetic stripe | MSD fallback |
| `12` | Hybrid (Mag stripe + ICC + contactless) | No (SoftPOS is contactless-only) |
| `13` | Terminal does not read card data | No |

### 6.5 Digital Wallet Program Type (DigWltProgType)

| Value | Description | SoftPOS Relevance |
|---|---|---|
| `AndroidPay` | Google Pay (legacy enum name) | Yes -- Google Pay NFC taps |
| `ApplePay` | Apple Pay | Yes -- Apple Pay NFC taps |
| `SamsungPay` | Samsung Pay | Yes -- Samsung Pay NFC taps |
| `MerchToken` | Merchant-provisioned token | No -- not from NFC |
| `ClickToPay` | Click to Pay (online) | No -- e-commerce only |
| `BankPay` | Bank-issued digital wallet | Rare -- check with Fiserv |

### 6.6 Digital Wallet Indicator (DigWltInd)

| Value | Description | When to Use |
|---|---|---|
| `Passthru` | Pass-through Digital Wallet (stores cryptogram/Token Block) | **All NFC wallet taps** (Apple Pay, Google Pay, Samsung Pay) |
| `Staged` | Staged Digital Wallet (e.g., PayPal-style funding) | **Not applicable** to NFC SoftPOS |

---

## 7. Implementation Notes & Edge Cases

### 7.1 Contactless EMV (07) vs. Contactless MSD (91)

- **Contactless ICC (`07`)** is the standard for all modern chip cards and all digital wallets.
  The card/device runs the full EMV kernel, generates an Application Cryptogram (tag 9F26),
  and provides rich EMV data.
- **Contactless MSD (`91`)** is a legacy mode where the card emulates a magnetic stripe over NFC.
  It provides Track 2 equivalent data but no cryptogram. Some older prepaid cards may still
  operate in this mode.
- **Softpay should always attempt contactless ICC first.** Only fall back to MSD if the kernel
  indicates MSD-only mode was selected during application selection.

### 7.2 EMV Fallback on SoftPOS

Since SoftPOS has **no magnetic stripe reader and no contact chip reader**, the standard EMV
fallback scenarios are limited:

| Fallback Scenario | Standard Terminal | SoftPOS Terminal |
|---|---|---|
| Contactless fails, insert chip | Use contact chip (`05`) | **Not possible** -- retry tap or manual entry |
| Chip fails, swipe mag stripe | Use mag stripe (`80`) | **Not possible** |
| Chip fails, key enter PAN | Use manual (`79`) | Possible but discouraged |
| NFC tap fails entirely | Retry or fallback | Retry tap; if repeated failure, use `791`/`792` |

**Recommendation:** On NFC failure, prompt the customer to retry the tap (up to 3 attempts).
Only offer manual key entry (`79x`) as a last resort, and only if your merchant configuration
permits it. Never send `80` or `86` from a SoftPOS device.

### 7.3 PIN Handling for Debit

- Fiserv Rapid Connect supports both PIN debit and PINless POS debit.
- For **PIN debit**, Softpay collects the PIN via its MPoC-certified software PIN pad and sends
  the encrypted PIN block. Use 3rd digit `6` in POSEntryMode.
- For **PINless POS debit**, no PIN is collected. Use 3rd digit `2`. The PINless POS Debit Flag
  field must also be set in the UMF message. Refer to the UMF specification for the
  `PINlessPOSDebitFlag` and `PINlessPOSDebitInd` fields.
- Debit Authorization transactions are only supported for merchants sending Settlement
  Indicator value of `1` or `2`.

### 7.4 Partial Authorization

Partial Authorization is mandated by card associations. When enabled:

- The POS must prompt for a second form of tender for the remaining balance.
- Cash Back amounts are excluded from partial authorization responses.
- Void/Full Reversal capability is required (customer may choose not to complete the purchase).
- Supported for Credit (Visa, MC, Amex, Discover, Diners, JCB) and Debit (Authorization, Sale).

### 7.5 Contactless Transaction Limits

Contactless EMV transactions may be subject to CVM limits that vary by country and card scheme.
For US domestic transactions:

- **Visa:** CVM limit typically $250 (above this, online PIN or CDCVM required)
- **Mastercard:** CVM limit typically $250
- **Amex:** CVM limit typically $200
- **Discover:** CVM limit typically $200

Digital wallets (Apple Pay, Google Pay, Samsung Pay) use **Consumer Device CVM (CDCVM)** -- the
device biometric or passcode -- and are generally exempt from these limits, allowing higher-value
contactless transactions without a separate PIN.

### 7.6 Fields to NEVER Send from SoftPOS

| Field/Value | Reason |
|---|---|
| `POSEntryMode = "05x"` | Contact ICC -- SoftPOS has no contact chip reader |
| `POSEntryMode = "80x"` | EMV fallback to mag stripe -- no mag stripe reader |
| `POSEntryMode = "86x"` | Switched from contactless to contact -- no contact reader |
| `POSEntryMode = "90x"` | Magnetic stripe read -- no mag stripe reader |
| `TermEntryCapablt = "02"` | Magnetic stripe only |
| `TermEntryCapablt = "04"` | Mag + key + chip |
| `TermEntryCapablt = "08"` | Contact chip only |
| `TermEntryCapablt = "09"` | Contact chip + mag stripe |
| `TermEntryCapablt = "12"` | Hybrid (mag + chip + contactless) |

### 7.7 Quick Copy-Paste: Typical Softpay Transaction Fields

**Standard credit card contactless tap (no PIN):**
```
POSEntryMode:    "072"
POSCondCode:     "00"
TermCatCode:     "09"
TermEntryCapablt: "06"
```

**Debit card contactless tap (mPOS software PIN):**
```
POSEntryMode:    "076"
POSCondCode:     "00"
TermCatCode:     "09"
TermEntryCapablt: "06"
```

**Apple Pay tap:**
```
POSEntryMode:    "071"
POSCondCode:     "00"
TermCatCode:     "09"
TermEntryCapablt: "06"
DigWltInd:       "Passthru"
DigWltProgType:  "ApplePay"
```

**Google Pay tap:**
```
POSEntryMode:    "071"
POSCondCode:     "00"
TermCatCode:     "09"
TermEntryCapablt: "06"
DigWltInd:       "Passthru"
DigWltProgType:  "AndroidPay"
```

**Samsung Pay tap:**
```
POSEntryMode:    "071"
POSCondCode:     "00"
TermCatCode:     "09"
TermEntryCapablt: "06"
DigWltInd:       "Passthru"
DigWltProgType:  "SamsungPay"
```

**Referenced refund (card not present):**
```
POSEntryMode:    "012"
POSCondCode:     "05"
TermCatCode:     "09"
TermEntryCapablt: "06"
```

---

**Document version:** 1.0
**Last updated:** 2026-04-07
**Source specifications:** Fiserv Rapid Connect UMF v15.04 (RSO024), EMV QRG v12.04.5, Retail/QSR QRG v10.02.9
