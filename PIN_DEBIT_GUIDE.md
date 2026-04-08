# PIN Debit & Key Management Implementation Guide

**Project:** RSO024 — Softpay by Softpay ApS  
**Protocol:** Fiserv Rapid Connect UMF v15.04.5  
**Date:** April 2026

---

## Table of Contents

1. [Overview](#1-overview)
2. [PIN Debit vs. PINless POS Debit](#2-pin-debit-vs-pinless-pos-debit)
3. [Encryption Methods](#3-encryption-methods)
4. [Master Session Encryption Lifecycle](#4-master-session-encryption-lifecycle)
5. [DUKPT Encryption](#5-dukpt-encryption)
6. [PIN Block Construction](#6-pin-block-construction)
7. [PINGrp Field Mapping](#7-pingrp-field-mapping)
8. [Online PIN vs. CDCVM Decision Tree](#8-online-pin-vs-cdcvm-decision-tree)
9. [PINless POS Debit](#9-pinless-pos-debit)
10. [Debit Network Routing](#10-debit-network-routing)
11. [Transaction Flows](#11-transaction-flows)
12. [Key Management: Test vs. Production](#12-key-management-test-vs-production)
13. [SoftPOS / MPoC Considerations](#13-softpos--mpoc-considerations)
14. [Open Questions](#14-open-questions)

---

## 1. Overview

PIN Debit and PINless POS Debit are both **selected** in the RSO024 Project Profile. This requires:

| Feature | Selected | UMF Message Type | Key Requirement |
|---|---|---|---|
| **PIN Debit** | Yes | `DebitRequest` / `DebitResponse` | PIN encryption (DUKPT or Master Session) |
| **PINless POS Debit** | Yes | `PinlessDebitRequest` / `PinlessDebitResponse` | No PIN encryption needed |
| **PIN Debit Refund** | Yes | `DebitRequest` with `TxnType=Refund` | — |
| **Master Session Encryption** | Yes | `AdminRequest` (`EncryptionKeyRequest`) | HSM, 24h key rotation |
| **Key Block TR-31** | Yes | Part of key exchange | ANSI standard key wrapping |

**Why PIN Debit?** Enables debit routing for cost optimization (Durbin Amendment compliance). Debit interchange is typically lower than credit for many transaction sizes.

---

## 2. PIN Debit vs. PINless POS Debit

| Aspect | PIN Debit | PINless POS Debit |
|---|---|---|
| **Cardholder Verification** | Online PIN entry | No PIN required |
| **UMF Message** | `DebitRequest` | `PinlessDebitRequest` |
| **PINGrp Required** | Yes (PINData + key identifier) | No |
| **PIN Encryption** | Required (DUKPT or Master Session) | Not applicable |
| **Card Types** | Debit cards with PIN support | Debit cards eligible for PINless routing |
| **Transaction Limits** | No limit (PIN verified) | Subject to PINless threshold (network-dependent) |
| **Contactless Support** | Requires online PIN on device | CDCVM or below CVM limit |
| **Use Case** | Higher-value debit, PIN-preferring cards | Lower-value transactions, debit routing optimization |
| **PymtType** | `Debit` | `PLDebit` |
| **Settlement** | Dual-message (Auth + Completion) | Dual-message (Auth + Completion) |

---

## 3. Encryption Methods

Two PIN encryption methods are supported in the project profile:

### 3.1 Master Session Encryption (Selected)

| Attribute | Detail |
|---|---|
| **Mechanism** | Pre-shared master key encrypts randomly generated session keys |
| **Key Rotation** | Every 24 hours |
| **HSM** | Required on Softpay backend |
| **Key Exchange** | `TxnType=EncryptionKeyRequest` via `AdminRequest` |
| **Key Block** | TR-31 format (ANSI standard) |
| **Key Identifier** | `MSKeyID` field in `PINGrp` (up to 10 alphanumeric chars) |
| **PIN Block** | Encrypted using current session key |

### 3.2 DUKPT (Derived Unique Key Per Transaction)

| Attribute | Detail |
|---|---|
| **Mechanism** | Each transaction uses a unique key derived from a Base Derivation Key (BDK) |
| **Key Rotation** | Automatic — new key per transaction via KSN counter |
| **BDK Provisioning** | Must be injected/provisioned on device or backend |
| **Key Identifier** | `KeySerialNumData` field in `PINGrp` (20 alphanumeric chars) |
| **KSN Counter** | 21-bit counter within the Key Serial Number; ~1M transactions per BDK |
| **PIN Block** | Encrypted using the derived unique key |

### 3.3 Which to Use?

| Scenario | Recommended Method | Reason |
|---|---|---|
| **Backend-to-Fiserv** (CloudSwitch) | Master Session | Backend manages HSM; single key rotation point |
| **Device-level encryption** (AppSwitch) | DUKPT | Per-device BDK; no central key management needed per transaction |
| **Softpay Architecture** | **TBD — Needs Fiserv confirmation** | Depends on where PIN encryption occurs (SDK on device vs. backend) |

> **Open Question:** Fiserv must confirm which method they require/recommend for SoftPOS and whether both are available during testing.

---

## 4. Master Session Encryption Lifecycle

### 4.1 Initial Key Setup

```
┌──────────┐                              ┌──────────┐
│  Softpay │                              │  Fiserv  │
│  Backend │                              │   Host   │
└─────┬────┘                              └─────┬────┘
      │                                         │
      │  1. Master Key provisioned (out-of-band) │
      │ ◄──────────────────────────────────────► │
      │                                         │
      │  2. AdminRequest                        │
      │     TxnType=EncryptionKeyRequest        │
      │  ──────────────────────────────────────► │
      │                                         │
      │  3. AdminResponse                       │
      │     Contains encrypted session keys:    │
      │     - PINEncrptWrkKey                   │
      │     - MsgEncrptWrkKey (if applicable)   │
      │     - MACWrkKey (if MAC selected)       │
      │  ◄────────────────────────────────────── │
      │                                         │
      │  4. Decrypt session keys using          │
      │     master key; store in HSM            │
      │                                         │
      │  5. Ready for PIN Debit transactions    │
      │                                         │
```

### 4.2 Key Request Message

```xml
<?xml version="1.0" encoding="UTF-8"?>
<GMF xmlns="com/fiserv/Merchant/gmfV15.04">
  <AdminRequest>
    <CommonGrp>
      <PymtType>Debit</PymtType>
      <TxnType>EncryptionKeyRequest</TxnType>
      <LocalDateTime>20260407120000</LocalDateTime>
      <TrnmsnDateTime>20260407170000</TrnmsnDateTime>
      <STAN>000001</STAN>
      <RefNum>1234567890</RefNum>
      <TPPID>RSO024</TPPID>
      <TermID>00000001</TermID>
      <MerchID>RCTST1000119068</MerchID>
      <TxnCrncy>840</TxnCrncy>
      <GroupID>20001</GroupID>
    </CommonGrp>
  </AdminRequest>
</GMF>
```

### 4.3 Key Response (Expected Structure)

The `AdminResponse` returns the session keys encrypted under the master key in TR-31 key block format:

| Response Field | Description |
|---|---|
| `PINEncrptWrkKey` | Session key for PIN block encryption (max 16 chars) |
| `MsgEncrptWrkKey` | Session key for message encryption (max 16 chars, if applicable) |
| `MACWrkKey` | Session key for MAC generation (max 16 chars, if MAC selected — not selected for RSO024) |
| `RespCode` | `000` = key exchange successful |
| `MSKeyID` | Key identifier to include in subsequent `PINGrp` |

### 4.4 Key Rotation Schedule

```
Day 1, 00:00 UTC
├── Send EncryptionKeyRequest → receive Session Key A
├── Process PIN Debit transactions using Key A (MSKeyID = "A")
│
Day 2, 00:00 UTC
├── Send EncryptionKeyRequest → receive Session Key B
├── Process PIN Debit transactions using Key B (MSKeyID = "B")
│
Day 3, 00:00 UTC
├── Send EncryptionKeyRequest → receive Session Key C
└── ...
```

**Rules:**
- Request new key **before** the 24-hour window expires
- Implement a timer/scheduler for automated rotation
- Handle key request failure gracefully (retry with backoff)
- Old session key remains valid until Fiserv invalidates it (grace period TBD)
- The `MSKeyID` from the response must be echoed in every `PINGrp` until the next rotation

---

## 5. DUKPT Encryption

### 5.1 Key Serial Number (KSN) Structure

The KSN is a 20-hex-character (10-byte) value:

```
┌─────────────────────────┬──────────────────┬─────────────┐
│  BDK Identifier (5 bytes)│ Device ID (19 bits)│Counter (21 bits)│
│   Bytes 1-5             │  Bits 1-19       │  Bits 20-40 │
└─────────────────────────┴──────────────────┴─────────────┘
                          ◄───── Bytes 6-10 ────────────────►
```

- **BDK Identifier:** Identifies which Base Derivation Key was used
- **Device ID:** Unique per terminal/device
- **Counter:** Increments with each transaction (21 bits = ~2,097,152 transactions max)

### 5.2 DUKPT Key Derivation Flow

```
BDK (Base Derivation Key)
  │
  ├── IPEK (Initial PIN Encryption Key) — derived from BDK + KSN
  │     │
  │     ├── Future Key 1 (KSN counter = 1)
  │     ├── Future Key 2 (KSN counter = 2)
  │     ├── ...
  │     └── Future Key N (KSN counter = N)
  │
  └── Each future key encrypts one PIN block
```

### 5.3 KSN Counter Management

| Aspect | Detail |
|---|---|
| **Counter Size** | 21 bits (~2M transactions) |
| **Increment** | +1 per PIN-bearing transaction |
| **Exhaustion** | When counter overflows, new BDK must be provisioned |
| **Tracking** | Device must persist current KSN counter across restarts |
| **Synchronization** | Counter is part of KSN sent to Fiserv; Fiserv derives same key |

### 5.4 BDK Provisioning for SoftPOS

> **Open Question:** How are BDKs provisioned for SoftPOS devices? Standard POS terminals receive BDKs via physical key injection. For SoftPOS, this must be done securely via software, likely through:
> 1. Secure key download from Fiserv via API
> 2. Key injection through Softpay's backend
> 3. HSM-to-HSM transfer

---

## 6. PIN Block Construction

### 6.1 ISO Format 0 (ISO-0 / ISO 9564 Format 0)

ISO-0 is the most common PIN block format. It XORs the PIN field with the PAN field:

```
PIN Field (8 bytes):
┌──────┬──────────────────┬──────────────────────────────────────┐
│ 0x0N │ PIN digits (padded│ 0xF padding to fill 8 bytes         │
│      │ with 0xF)        │                                      │
└──────┴──────────────────┴──────────────────────────────────────┘
  N = number of PIN digits

PAN Field (8 bytes):
┌──────┬──────────────────────────────────────────────────┬──────┐
│ 0x00 │ 0x00 │ 0x00 │ 0x00 │ rightmost 12 PAN digits   │      │
│      │      │      │      │ (excluding check digit)    │      │
└──────┴──────┴──────┴──────┴────────────────────────────┴──────┘

Clear PIN Block = PIN Field XOR PAN Field
Encrypted PIN Block = Encrypt(Clear PIN Block, encryption key)
```

**Example:**
```
PIN: 1234 (4 digits)
PAN: 4761530001111118

PIN Field:  041234FFFFFFFFFF
PAN Field:  0000476153000111

Clear Block: 04125E9EFFFFFFEE
                    ↓ Encrypt with DUKPT-derived key or session key
Encrypted:  A1B2C3D4E5F60718  (16 hex chars → PINData field)
```

### 6.2 ISO Format 4 (ISO-4 / ISO 9564 Format 4)

ISO-4 is the newer AES-based format:

| Feature | ISO-0 | ISO-4 |
|---|---|---|
| **Algorithm** | 3DES | AES-128/192/256 |
| **PIN Block Size** | 8 bytes (64 bits) | 16 bytes (128 bits) |
| **PAN Binding** | XOR with PAN | Uses PAN in derivation |
| **Random Padding** | No | Yes (improved security) |
| **Hex Output** | 16 chars | 32 chars |

> **Open Question:** Fiserv must confirm which PIN block format is required. The XSD defines `PINData` as `Len16HexString` (exactly 16 hex chars = 8 bytes), which suggests **ISO Format 0** (3DES-based). ISO Format 4 produces 32 hex chars and would not fit this field.

---

## 7. PINGrp Field Mapping

### 7.1 XSD Definition

```xml
<xs:complexType name="PINGrp">
  <xs:sequence>
    <xs:element ref="PINData" minOccurs="0" />           <!-- 16 hex chars, encrypted PIN block -->
    <xs:element ref="KeySerialNumData" minOccurs="0" />  <!-- 20 alphanumeric, DUKPT KSN -->
    <xs:element ref="KeyOffset" minOccurs="0" />          <!-- 4 alphanumeric -->
    <xs:element ref="KeyMgtData" minOccurs="0" />         <!-- max 36 hex chars -->
    <xs:element ref="MSKeyID" minOccurs="0" />            <!-- max 10 alphanumeric, Master Session Key ID -->
    <xs:element ref="NumPINDigits" minOccurs="0" />       <!-- 4, 5, 6, 7, 8, 9, 10, 11, or 12 -->
    <xs:element ref="EnhKeyFmt" minOccurs="0" />          <!-- "T" (TR-31) -->
    <xs:element ref="EnhKeyMgtData" minOccurs="0" />      <!-- max 256 chars -->
    <xs:element ref="EnhKeyChkDig" minOccurs="0" />       <!-- max 6 hex chars -->
    <xs:element ref="EnhKeySlot" minOccurs="0" />         <!-- "1" or "2" -->
  </xs:sequence>
</xs:complexType>
```

### 7.2 Field Usage by Encryption Method

#### DUKPT Encryption

| Field | Required | Value |
|---|---|---|
| `PINData` | **Yes** | 16-hex-char encrypted PIN block |
| `KeySerialNumData` | **Yes** | 20-char DUKPT Key Serial Number |
| `NumPINDigits` | Conditional | Number of PIN digits (4-12) |
| `MSKeyID` | No | Not used for DUKPT |
| `KeyOffset` | No | — |
| `KeyMgtData` | No | — |
| `EnhKeyFmt` | No | — |
| `EnhKeyMgtData` | No | — |
| `EnhKeyChkDig` | No | — |
| `EnhKeySlot` | No | — |

```xml
<PINGrp>
  <PINData>A1B2C3D4E5F60718</PINData>
  <KeySerialNumData>FFFF9876543210E00001</KeySerialNumData>
</PINGrp>
```

#### Master Session Encryption

| Field | Required | Value |
|---|---|---|
| `PINData` | **Yes** | 16-hex-char encrypted PIN block |
| `MSKeyID` | **Yes** | Session key identifier from `EncryptionKeyRequest` response |
| `NumPINDigits` | Conditional | Number of PIN digits (4-12) |
| `KeySerialNumData` | No | Not used for Master Session |
| `KeyOffset` | No | — |
| `KeyMgtData` | No | — |
| `EnhKeyFmt` | No | — |
| `EnhKeyMgtData` | No | — |
| `EnhKeyChkDig` | No | — |
| `EnhKeySlot` | No | — |

```xml
<PINGrp>
  <PINData>D7E8F9A0B1C2D3E4</PINData>
  <MSKeyID>SESS000001</MSKeyID>
</PINGrp>
```

#### TR-31 Enhanced Key Format

When `EnhKeyFmt = "T"` (TR-31), the enhanced fields are used:

| Field | Required | Value |
|---|---|---|
| `PINData` | **Yes** | Encrypted PIN block |
| `EnhKeyFmt` | **Yes** | `"T"` (TR-31 format) |
| `EnhKeyMgtData` | **Yes** | TR-31 key block data (up to 256 chars) |
| `EnhKeyChkDig` | Conditional | Key check digit (up to 6 hex chars) |
| `EnhKeySlot` | Conditional | `"1"` or `"2"` — identifies key slot |

```xml
<PINGrp>
  <PINData>D7E8F9A0B1C2D3E4</PINData>
  <EnhKeyFmt>T</EnhKeyFmt>
  <EnhKeyMgtData>[TR-31 key block header + encrypted key]</EnhKeyMgtData>
  <EnhKeyChkDig>A1B2C3</EnhKeyChkDig>
  <EnhKeySlot>1</EnhKeySlot>
</PINGrp>
```

---

## 8. Online PIN vs. CDCVM Decision Tree

```
Card presented via NFC tap
│
├── Is it a Digital Wallet (Apple Pay / Google Pay / Samsung Pay)?
│   ├── YES → CDCVM (consumer authenticated on their device)
│   │         No PIN required. PINGrp is NOT included.
│   │         POS Entry Mode: 071 (contactless EMV)
│   │         CVM Results: Tag 9F34 indicates CDCVM
│   │
│   └── NO → Physical debit card tapped
│       │
│       ├── Is the amount BELOW the contactless CVM limit?
│       │   ├── YES → No CVM required (tap-and-go)
│       │   │         PINGrp is NOT included.
│       │   │         Route as PINless POS Debit if eligible.
│       │   │
│       │   └── NO → Amount is ABOVE the CVM limit
│       │       │
│       │       ├── Is the card eligible for PINless POS Debit routing?
│       │       │   ├── YES → Route as PINless (no PIN)
│       │       │   │         PINGrp is NOT included.
│       │       │   │
│       │       │   └── NO → Online PIN required
│       │       │             Prompt cardholder for PIN on SoftPOS device.
│       │       │             Encrypt PIN block.
│       │       │             Include PINGrp in DebitRequest.
│       │       │
│       │       └── NOTE: Debit networks do NOT support offline PIN
│       │                 in CDCVM mode. Only online PIN is valid.
│       │
│       └── Contactless CVM Limits (US):
│           | Visa       | Variable (brand-configured) |
│           | Mastercard | $100                        |
│           | Amex       | $50                         |
│           | Discover   | $25                         |
```

**Key Rules:**
- **CDCVM transactions** are always sent as dual-message (Auth + Completion)
- **Debit networks do NOT support offline PIN** for contactless
- **Terminal must NOT prompt for offline PIN** on contactless transactions (per EMV Implementation Guide)
- For SoftPOS, CDCVM is the preferred CVM for digital wallets — no PIN pad interaction needed

---

## 9. PINless POS Debit

### 9.1 Overview

PINless POS Debit routes debit transactions over debit networks **without** requiring a PIN. This is used when:
- The transaction amount is below the PINless threshold
- The merchant is enrolled for PINless debit
- The card is eligible for PINless routing

### 9.2 Message Structure

PINless debit uses a different message type:

```xml
<GMF xmlns="com/fiserv/Merchant/gmfV15.04">
  <PinlessDebitRequest>
    <CommonGrp>
      <PymtType>PLDebit</PymtType>      <!-- PINless Debit -->
      <TxnType>Authorization</TxnType>
      <!-- ... standard CommonGrp fields ... -->
    </CommonGrp>
    <CardGrp>
      <Track2Data>4017779991113335=25121011000012345678</Track2Data>
      <!-- No CardType — network determines routing -->
    </CardGrp>
    <!-- NO PINGrp — no PIN required -->
    <EMVGrp>
      <EMVData>...</EMVData>
    </EMVGrp>
  </PinlessDebitRequest>
</GMF>
```

### 9.3 Key Differences from PIN Debit

| Aspect | PIN Debit (`DebitRequest`) | PINless (`PinlessDebitRequest`) |
|---|---|---|
| `PymtType` | `Debit` | `PLDebit` |
| `PINGrp` | Required (PINData + key ID) | Not included |
| `CardType` | Not set (network routes) | Not set (network routes) |
| Encryption overhead | PIN block encryption required | None |
| CVM | Online PIN | None (or CDCVM for wallets) |

---

## 10. Debit Network Routing

### 10.1 How Routing Works

Fiserv determines the debit network based on:
1. **Card BIN/IIN** — identifies the issuing bank and card program
2. **AID** — for EMV transactions, the Application Identifier determines the network
3. **Merchant configuration** — priority routing set by Fiserv

### 10.2 US Common Debit AIDs

| AID | Network |
|---|---|
| `A0000000042203` | Maestro |
| `A0000006200620` | DNA (Discover Network Alliance) |
| `A0000001524010` | Discover Debit |
| `A0000000980840` | Visa US Common Debit |
| `A0000003330101` | UnionPay |
| `A0000000025040` | Amex Debit |

### 10.3 Dual-AID / Co-Badged Cards

Many US debit cards have both a credit AID (e.g., Visa credit) and a debit AID (e.g., Visa US Common Debit). The terminal must:

1. **Present both AIDs** to the card during EMV selection
2. **Allow cardholder choice** if the card supports multiple applications (per Durbin Amendment)
3. **Route based on selected AID** — if debit AID is selected, send as `DebitRequest`; if credit AID, send as `CreditRequest`

> For SoftPOS contactless, AID selection typically happens automatically based on kernel configuration and cardholder preference settings.

---

## 11. Transaction Flows

### 11.1 PIN Debit Authorization + Completion

```
┌──────────┐                              ┌──────────┐
│  Softpay │                              │  Fiserv  │
│  Device  │                              │   Host   │
└─────┬────┘                              └─────┬────┘
      │  1. Cardholder taps debit card            │
      │     (above CVM limit, not PINless)        │
      │                                           │
      │  2. Prompt for PIN on SoftPOS device      │
      │     (Softpay certified PIN component)     │
      │                                           │
      │  3. Encrypt PIN block                     │
      │     (DUKPT or Master Session)             │
      │                                           │
      │  4. DebitRequest                          │
      │     PymtType=Debit                        │
      │     TxnType=Authorization                 │
      │     PINGrp: PINData + KeySerialNumData    │
      │  ──────────────────────────────────────►   │
      │                                           │
      │  5. DebitResponse                         │
      │     RespCode=000 (approved)               │
      │     AuthID, ResponseDate, STAN, etc.      │
      │  ◄──────────────────────────────────────   │
      │                                           │
      │  6. Store response fields for Completion  │
      │                                           │
      │  7. DebitRequest                          │
      │     PymtType=Debit                        │
      │     TxnType=Completion                    │
      │     OrigAuthGrp: echoed from step 5       │
      │  ──────────────────────────────────────►   │
      │                                           │
      │  8. DebitResponse (Completion confirmed)  │
      │  ◄──────────────────────────────────────   │
```

### 11.2 PINless Debit Authorization + Completion

```
┌──────────┐                              ┌──────────┐
│  Softpay │                              │  Fiserv  │
│  Device  │                              │   Host   │
└─────┬────┘                              └─────┬────┘
      │  1. Cardholder taps debit card            │
      │     (below PINless threshold OR eligible) │
      │                                           │
      │  2. No PIN prompt needed                  │
      │                                           │
      │  3. PinlessDebitRequest                   │
      │     PymtType=PLDebit                      │
      │     TxnType=Authorization                 │
      │     (no PINGrp)                           │
      │  ──────────────────────────────────────►   │
      │                                           │
      │  4. PinlessDebitResponse                  │
      │     RespCode=000                          │
      │  ◄──────────────────────────────────────   │
      │                                           │
      │  5. PinlessDebitRequest                   │
      │     PymtType=PLDebit                      │
      │     TxnType=Completion                    │
      │     OrigAuthGrp: echoed from step 4       │
      │  ──────────────────────────────────────►   │
      │                                           │
      │  6. PinlessDebitResponse (confirmed)      │
      │  ◄──────────────────────────────────────   │
```

### 11.3 Master Session Key Exchange (Before First PIN Debit)

```
┌──────────┐                              ┌──────────┐
│  Softpay │                              │  Fiserv  │
│  Backend │                              │   Host   │
└─────┬────┘                              └─────┬────┘
      │                                           │
      │  Pre-requisite: Master Key provisioned    │
      │  (out-of-band, during onboarding)         │
      │                                           │
      │  1. AdminRequest                          │
      │     TxnType=EncryptionKeyRequest          │
      │  ──────────────────────────────────────►   │
      │                                           │
      │  2. AdminResponse                         │
      │     RespCode=000                          │
      │     PINEncrptWrkKey (encrypted session key)│
      │     MSKeyID (key identifier)              │
      │  ◄──────────────────────────────────────   │
      │                                           │
      │  3. Decrypt PINEncrptWrkKey using         │
      │     master key → session key              │
      │                                           │
      │  4. Store session key + MSKeyID in HSM    │
      │                                           │
      │  5. Use session key for all PIN blocks    │
      │     until next rotation (24 hours)        │
      │                                           │
      │  ... 24 hours later ...                   │
      │                                           │
      │  6. Repeat steps 1-4                      │
      │  ──────────────────────────────────────►   │
```

### 11.4 PIN Debit Refund

```xml
<GMF xmlns="com/fiserv/Merchant/gmfV15.04">
  <DebitRequest>
    <CommonGrp>
      <PymtType>Debit</PymtType>
      <TxnType>Refund</TxnType>
      <!-- Standard fields -->
    </CommonGrp>
    <CardGrp>
      <Track2Data>4017779991113335=25121011000012345678</Track2Data>
    </CardGrp>
    <!-- PINGrp NOT typically required for refunds -->
    <OrigAuthGrp>
      <!-- Echo fields from original authorization -->
      <OrigAuthID>OK5678</OrigAuthID>
      <OrigResponseDate>260407</OrigResponseDate>
      <OrigLocalDateTime>20260407120000</OrigLocalDateTime>
      <OrigTranDateTime>20260407170000</OrigTranDateTime>
      <OrigSTAN>000005</OrigSTAN>
      <OrigRespCode>000</OrigRespCode>
    </OrigAuthGrp>
  </DebitRequest>
</GMF>
```

---

## 12. Key Management: Test vs. Production

### 12.1 Environment Separation

| Aspect | Test / Sandbox | Production |
|---|---|---|
| **Master Key** | Test master key (provided by Fiserv for development) | Production master key (separate, more secure provisioning) |
| **DUKPT BDK** | Test BDK | Production BDK |
| **Session Keys** | Generated from test master key | Generated from production master key |
| **MID/TID** | `RCTST1000119068/69/70` / `001-003` | Production MID/TID (different format) |
| **Keys Interchangeable?** | **No** — test and production keys are completely separate |

### 12.2 Key Provisioning Steps

| Step | Responsibility | Action |
|---|---|---|
| 1. Request keys | Softpay | Contact Fiserv to request test encryption keys |
| 2. Master key delivery | Fiserv | Provides test master key (secure channel) |
| 3. HSM setup | Softpay | Load master key into HSM |
| 4. Key exchange | Softpay | Send `EncryptionKeyRequest` to obtain session keys |
| 5. Validate | Both | Run test transactions to verify PIN encryption |
| 6. Production keys | Fiserv | Separate provisioning process for production |

### 12.3 TR-31 Key Block Format

TR-31 is an ANSI standard for secure key exchange:

```
Key Block Header (16 bytes):
┌──────────────────────────────────────────────────────┐
│ Version ID │ Block Length │ Key Usage │ Algorithm │   │
│   (1 byte) │  (4 digits)  │ (2 chars) │ (1 char)  │...│
└──────────────────────────────────────────────────────┘

Followed by:
- Key derivation data
- Encrypted key data
- MAC (message authentication code)
```

The `EnhKeyFmt = "T"` flag in `PINGrp` indicates TR-31 format is being used.

---

## 13. SoftPOS / MPoC Considerations

### 13.1 PIN on COTS Challenge

Traditional PIN entry happens on a dedicated, tamper-resistant PIN pad (PED). SoftPOS/COTS devices do **not** have a PED. Instead:

| Aspect | Traditional POS | SoftPOS (Softpay) |
|---|---|---|
| **PIN Entry Device** | Hardware PIN pad (PED) | Software-based PIN pad (MPoC certified) |
| **Tamper Protection** | Physical tamper switches | Software attestation, secure enclave |
| **PIN Encryption** | Done inside PED hardware | Done in secure PIN component on device |
| **Key Storage** | Tamper-resistant security module | Secure element / TEE / software-based |
| **Certification** | PCI PTS | PCI MPoC (Mobile Payments on COTS) |

### 13.2 Softpay's Certified PIN Component

Softpay provides a **certified PIN pad** as part of the SoftPOS SDK:
- Stable digit positions (prevents shoulder surfing)
- Card and PIN data handled separately
- PIN data never stored on device
- Attestation-based device integrity checks
- Backend follows PCI DSS and MPoC standards

### 13.3 Integration Architecture

The PIN encryption flow for SoftPOS depends on where encryption occurs:

**Option A: Device-Level Encryption (DUKPT)**
```
[Softpay SDK PIN Component] → encrypt PIN → [PINData + KSN] → send in DebitRequest
```
- BDK must be provisioned to each device
- KSN counter managed per-device
- No backend HSM needed for PIN

**Option B: Backend Encryption (Master Session)**
```
[Softpay SDK PIN Component] → secure channel → [Softpay Backend HSM] → encrypt PIN → [PINData + MSKeyID] → send in DebitRequest
```
- Master key in backend HSM
- Session key from `EncryptionKeyRequest`
- PIN must transit securely from device to backend

> **Critical Decision:** Softpay and Fiserv must agree on which architecture to use. This impacts security certification, device provisioning, and the integration approach.

### 13.4 MPoC Implications

| Concern | Detail |
|---|---|
| **PCI MPoC Standard** | Softpay is already MPoC certified — but the Fiserv integration must not break compliance |
| **PIN in Transit** | If using backend encryption, the PIN must be encrypted before leaving the device (point-to-point) |
| **Key Injection** | DUKPT BDK injection on COTS is more complex than on traditional POS |
| **Attestation** | Device integrity must be verified before allowing PIN entry |
| **Screen Capture** | Device must prevent screen capture during PIN entry |
| **Secure Keyboard** | PIN pad must use a separate secure input method |

---

## 14. Open Questions

### 14.1 Critical (Block Development)

| # | Question | Context |
|---|---|---|
| 1 | What PIN encryption method does Fiserv require for SoftPOS — DUKPT, AES-DUKPT, or Master Session? | Both Master Session and DUKPT appear supported; need to know which is mandatory/preferred |
| 2 | What PIN block format is required — ISO Format 0 or ISO Format 4? | `PINData` field is 16 hex chars, suggesting ISO-0 (8 bytes), but need confirmation |
| 3 | How are DUKPT BDKs provisioned for SoftPOS devices (if DUKPT is used)? | Traditional key injection not possible on COTS; need secure software method |
| 4 | Is online PIN supported for **contactless** debit on SoftPOS? | Some networks may not support online PIN for contactless; need per-network confirmation |
| 5 | What is the process for obtaining test encryption keys for Sandbox? | Need test master key or test BDK before PIN Debit development can begin |
| 6 | Does the `EncryptionKeyRequest` response return the session key in the `PINEncrptWrkKey` field? | Need to confirm the exact response structure for Master Session key exchange |
| 7 | Where should PIN encryption occur — on the device (DUKPT) or on the backend (Master Session)? | Architecture decision that affects security certification |

### 14.2 Important (Block Certification)

| # | Question | Context |
|---|---|---|
| 8 | How does Fiserv handle KSN counter management for DUKPT on SoftPOS? | Need counter sync/reset procedures |
| 9 | What happens when a DUKPT BDK's counter is exhausted? | Need re-keying process |
| 10 | What is the grace period when a Master Session key expires? | If the 24h rotation fails, how long is the old key valid? |
| 11 | Are there test BDKs and test master keys already provisioned for RSO024? | May need to request them explicitly |
| 12 | Does Softpay's MPoC certification satisfy Fiserv's requirements for PIN entry on COTS? | May need additional certification or review |
| 13 | What are the PINless POS Debit thresholds by network? | Need per-network limits to determine routing |
| 14 | Are separate `EncryptionKeyRequest` calls needed per MID/TID combination? | Affects key management at scale |

### 14.3 Phase 2

| # | Question | Context |
|---|---|---|
| 15 | Can AES-DUKPT be used as an alternative to standard DUKPT? | AES-DUKPT is listed in the XSD (`AESDUKPT` enum value) |
| 16 | Is Debit Pre-Authorization relevant for SoftPOS? | Listed as available but not selected |
| 17 | Can the PIN encryption method be changed post-certification? | Future-proofing |

---

## Appendix A — XSD Field Type Reference

| Field | XSD Type | Format |
|---|---|---|
| `PINData` | `Len16HexString` | Exactly 16 hex chars (`[0-9A-F]{16}`) |
| `KeySerialNumData` | `Len20AN` | Exactly 20 alphanumeric chars (`[0-9A-Za-z]{20}`) |
| `KeyOffset` | `Len4AN` | Exactly 4 alphanumeric chars |
| `KeyMgtData` | `Max36HexString` | Up to 36 hex chars |
| `MSKeyID` | `Len10AN` | Up to 10 alphanumeric chars |
| `NumPINDigits` | `NumPINDigitsType` | Enum: `4`, `5`, `6`, `7`, `8`, `9`, `10`, `11`, `12` |
| `EnhKeyFmt` | `EnhKeyFmtType` | Enum: `T` (TR-31) |
| `EnhKeyMgtData` | `Max256ANS` | Up to 256 chars |
| `EnhKeyChkDig` | `Max6HexString` | Up to 6 hex chars |
| `EnhKeySlot` | `EnhKeySlotType` | Enum: `1`, `2` |

## Appendix B — Java Code Reference

See [FiservUmfMessageBuilder.java](java-examples/FiservUmfMessageBuilder.java) for:
- `buildDebitAuthorizationWithPin()` — PIN Debit with DUKPT or Master Session
- `buildEncryptionKeyRequest()` — Master Session key exchange via AdminRequest

See [FiservResponseParser.java](java-examples/FiservResponseParser.java) for:
- Response parsing including `AuthID`, `ResponseDate`, `STAN` needed for Completion and OrigAuthGrp

## Appendix C — RCToolkitSampleCode Confirmation

The Fiserv RCToolkitSampleCode (C# and Java) confirms several PIN Debit implementation details:

| Finding | Source | Detail |
|---|---|---|
| **DUKPT is the demonstrated method** | `DebitSaleRequest.cs` / `DebitRequest.java` | PINGrp populated with `PINData` + `KeySerialNumData` (DUKPT pattern) |
| **PINData format** | `TestConst.cs` / `TestConst.java` | `"99A14CA1B65D821B"` — 16 hex chars confirming `Len16HexString` and ISO Format 0 |
| **KeySerialNumData format** | `TestConst.cs` / `TestConst.java` | `"F8765432100015200578"` — 20 chars confirming `Len20AN` |
| **POSEntryMode for Debit** | `DebitSaleRequest.cs` / `DebitRequest.java` | `901` (contactless EMV) |
| **Partial Auth enabled** | Both Credit and Debit samples | `PartAuthrztnApprvlCapablt = "1"` (enabled by default) |
| **Track2Data used** | `DebitSaleRequest.cs` / `DebitRequest.java` | Track 2 data sent in CardGrp for swiped debit |

**Implication:** The sample code uses DUKPT (not Master Session) for PIN encryption, which aligns with the most common card-present deployment. The 16-hex-char PINData confirms ISO Format 0. Master Session Encryption is selected in the project profile but may be intended for key management/session encryption rather than per-transaction PIN encryption.

---

*This guide was prepared based on the RSO024 SDK documentation (UMF v15.04.5, XSD schema, EMV Implementation Guide, Portal Definitions, and RCToolkitSampleCode). All open questions should be resolved during the technical workshop with Fiserv before PIN Debit development begins.*
