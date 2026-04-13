# Fiserv Rapid Connect Response Code & Error Handling Reference

**Project:** RSO024 (Softpay SoftPOS)  
**UMF Version:** 15.04.5 (February 25, 2026)  
**Source:** UMF Specification Appendix A (Chapter 18), Response Group (Chapter 4)  
**Last Updated:** April 7, 2026

---

## Table of Contents

- [1. Error Handling Decision Tree](#1-error-handling-decision-tree)
- [2. Response Code Quick Reference Table](#2-response-code-quick-reference-table)
  - [2.1 Approved](#21-approved)
  - [2.2 Declined - Card / Issuer](#22-declined---card--issuer)
  - [2.3 Declined - Referral](#23-declined---referral)
  - [2.4 Declined - Amount](#24-declined---amount)
  - [2.5 Declined - Transaction](#25-declined---transaction)
  - [2.6 System Errors](#26-system-errors)
  - [2.7 Retry-Eligible Codes](#27-retry-eligible-codes)
  - [2.8 Prepaid](#28-prepaid)
  - [2.9 Fleet](#29-fleet)
  - [2.10 Check](#210-check)
  - [2.11 TransArmor](#211-transarmor)
  - [2.12 Money Transfer](#212-money-transfer)
  - [2.13 Debit](#213-debit)
  - [2.14 Bill Payment](#214-bill-payment)
  - [2.15 DCC](#215-dcc)
  - [2.16 3D Secure](#216-3d-secure)
  - [2.17 Other](#217-other)
- [3. Softpay-Specific Response Handling](#3-softpay-specific-response-handling)
  - [3.1 Common Approval Codes](#31-common-approval-codes)
  - [3.2 Common Contactless Decline Codes](#32-common-contactless-decline-codes)
  - [3.3 PIN-Related Errors](#33-pin-related-errors)
  - [3.4 Key Management Errors](#34-key-management-errors)
  - [3.5 Timeout and System Errors](#35-timeout-and-system-errors)
  - [3.6 Duplicate Transaction](#36-duplicate-transaction)
  - [3.7 Void / Reversal Issues](#37-void--reversal-issues)
- [4. Authorizing Network IDs](#4-authorizing-network-ids)
- [5. RejectResponse Error Codes](#5-rejectresponse-error-codes)
- [6. Implementation Notes](#6-implementation-notes)

---

## 1. Error Handling Decision Tree

```
Response Received from Fiserv
|
+-- Normal Response (CreditResponse / DebitResponse / ReversalResponse / etc.)
|   |
|   +-- RespCode = "000" --> APPROVED
|   |   +-- Store: AuthID, ResponseDate, SettlementDate
|   |   +-- Store scheme refs: TransID (Visa), BanknetData (MC),
|   |   |   AmExTranID (Amex), DiscoverNRID (Discover)
|   |   +-- Display "Approved" to cardholder
|   |   +-- Print receipt with mandatory EMV fields (AID, TVR, IAD, ARQC)
|   |   +-- For dual-message: queue Completion with original AuthID
|   |   +-- Return success to POS
|   |
|   +-- RespCode = "002" --> PARTIAL APPROVAL
|   |   +-- Read approved amount from TxnAmt in response
|   |   +-- Display approved partial amount to cardholder
|   |   +-- Merchant decides:
|   |   |   +-- ACCEPT partial: complete transaction for approved amount
|   |   |   +-- REJECT partial: send Void with ORIGINAL amount
|   |   |       (ReversalInd="Void", TxnAmt = original requested amount)
|   |   +-- If voiding: use same RefNum as original request
|   |   +-- Remaining balance: request different payment method
|   |
|   +-- RespCode starts with "0" (other than 000/002) --> SPECIAL APPROVAL
|   |   +-- "003" = VIP Approval --> treat as approved (full amount)
|   |   +-- "785" = No reason to decline --> treat as approved ($0 verification)
|   |   +-- "701" = EMV Key Load approved --> key management success
|   |   +-- "703" = EMV Key Load approved --> key management success
|   |   +-- Store AuthID and scheme refs as with "000"
|   |
|   +-- RespCode = "1xx" --> DECLINED (Card / Issuer)
|   |   +-- Display generic decline message to cardholder
|   |   +-- Do NOT reveal specific reason to cardholder (security)
|   |   +-- Do NOT retry automatically
|   |   +-- Log full RespCode + AddlRespData for troubleshooting
|   |   +-- Special sub-cases:
|   |       +-- 105, 107, 108 = Referral --> "Call issuer" (voice auth)
|   |       +-- 704, 767 = Pickup card --> card may be stolen/lost
|   |       +-- 100 = Do not honor --> generic issuer decline
|   |       +-- 119 = Transaction not permitted --> card restrictions
|   |
|   +-- RespCode = "5xx" --> DECLINED (System / Processor)
|   |   +-- Check if RETRY-ELIGIBLE:
|   |   |   +-- YES (505, 508, 511, 516, 524): wait 5-10 seconds, retry ONCE
|   |   |   +-- NO  (500, 504, 509, 514, 517, 520): do NOT retry
|   |   +-- 532, 533, 534 = Key/encryption errors:
|   |   |   +-- Check Master Session key setup and MSKeyID
|   |   |   +-- Request new session key via EncryptionKeyRequest
|   |   |   +-- Log and escalate to Softpay key management
|   |   +-- 542 = PIN tries exceeded --> card is locked for PIN
|   |   +-- Display "Transaction cannot be processed" to cardholder
|   |
|   +-- RespCode = "9xx" --> SYSTEM ERROR
|   |   +-- 902 = Invalid transaction --> check message format/fields
|   |   +-- 904 = Format error --> check XML structure against XSD
|   |   +-- 906 = System error --> retry once; if still fails, send TOR
|   |   +-- 909 = System malfunction --> retry once; if still fails, send TOR
|   |   +-- 911 = Issuer timeout --> retry once; if still fails, send TOR
|   |   +-- 913 = Duplicate transaction --> check STAN/RefNum uniqueness
|   |   |   +-- STAN must be unique per day per MID+TID (000001-999999)
|   |   |   +-- RefNum must be unique per day per MID+TID
|   |   +-- 914 = Settlement already occurred:
|   |   |   +-- Cannot Void --> use Refund instead
|   |   |   +-- Original transaction has been settled/cleared
|   |   +-- 915 = Transaction not found --> check OrigAuthGrp fields
|   |   +-- 916 = Original auth not found --> verify OrigAuthGrp:
|   |   |   +-- OrigAuthID, OrigRespDate, OrigLocalDateTime,
|   |   |   +-- OrigTrnmsnDateTime, OrigSTAN, OrigRespCode
|   |   +-- 920 = Security violation --> check credentials, TLS
|   |
|   +-- RespCode = other (2xx, 3xx, 4xx, 7xx, 8xx) --> CATEGORY-SPECIFIC
|       +-- Look up code in Section 2 tables below
|       +-- Most 2xx/7xx/8xx = decline (display decline to cardholder)
|       +-- 3xx = prepaid-specific (not applicable for standard SoftPOS)
|       +-- 4xx = TransArmor/token errors (check token setup)
|
+-- No Response (TIMEOUT -- no response within configured timeout)
|   +-- IMMEDIATELY send Timeout Reversal (TOR):
|   |   +-- ReversalRequest with ReversalInd = "Timeout"
|   |   +-- Use SAME RefNum as original transaction
|   |   +-- Scheme ref fields (TransID, BanknetData, AmExTranID,
|   |   |   DiscoverNRID) are EXEMPT for TOR (2026 UMF change)
|   |   +-- Include OrigAuthGrp with original transaction details
|   +-- If TOR response = "000": reversal confirmed, transaction voided
|   +-- If TOR also times out:
|   |   +-- Retry TOR up to 3 times with exponential backoff
|   |   +-- Log transaction for manual reconciliation
|   |   +-- Flag in Softpay backend for settlement review
|   +-- NEVER assume original transaction was declined on timeout
|   +-- NEVER retry the original transaction after sending TOR
|
+-- RejectResponse received
    +-- Message was MALFORMED or INVALID (not sent to processor)
    +-- Read ErrorData field for specific error detail
    +-- Common ErrorData values:
    |   +-- "001" = Schema validation failure (XML invalid per XSD)
    |   +-- "904" = Format error (field value out of range)
    |   +-- Check field order (must match XSD sequence)
    |   +-- Check empty tags (not allowed: <Tag></Tag> or <Tag />)
    |   +-- Check XML reserved chars (use &amp; &lt; &gt; etc.)
    +-- Fix the message and resend
    +-- Do NOT send TOR for RejectResponse (message never reached host)
    +-- Log full RejectResponse XML for debugging
```

---

## 2. Response Code Quick Reference Table

The `RespCode` field is returned in `RespGrp.RespCode` (alphanumeric, max 3 characters). All codes below are from UMF Appendix A (Chapter 18.1).

### 2.1 Approved

| Code | Description | Retryable | Action |
|------|-------------|-----------|--------|
| 000 | Approved | N/A | Store AuthID and scheme refs; proceed to Completion for dual-message |
| 002 | Partial Approval | N/A | Display partial amount; merchant accepts or voids for remaining balance |
| 003 | VIP Approval | N/A | Treat as full approval |
| 701 | EMV Key Load Approved | N/A | Key loaded successfully; continue processing |
| 703 | EMV Key Load Approved | N/A | Key loaded successfully; continue processing |
| 785 | No Reason to Decline | N/A | Treat as approved; typically for $0 verification transactions |

### 2.2 Declined - Card / Issuer

| Code | Description | Retryable | Action |
|------|-------------|-----------|--------|
| 100 | Do Not Honor | No | Generic decline; display decline to cardholder |
| 101 | Expired Card | No | Card expired; request different card |
| 102 | Suspected Fraud | No | Decline; do not retry; log for fraud review |
| 104 | Restricted Card | No | Card restricted by issuer |
| 109 | Invalid Merchant | No | MID not recognized by issuer; check MID setup |
| 114 | Invalid Account Number | No | Card number invalid; request different card |
| 116 | Insufficient Funds | No | Insufficient balance; request different payment |
| 117 | Incorrect PIN | No | PIN entered incorrectly; allow retry per PIN try counter |
| 118 | No Card Record | No | Issuer has no record of this card |
| 119 | Transaction Not Permitted | No | Card not allowed for this transaction type |
| 120 | Transaction Not Permitted (terminal) | No | Transaction type not allowed for this terminal |
| 121 | Exceeds Withdrawal Limit | No | Amount exceeds card's withdrawal limit |
| 122 | Security Violation | No | Potential security issue; decline |
| 123 | Exceeds Withdrawal Frequency | No | Too many transactions in period |
| 124 | Violation of Law | No | Transaction violates regulations |
| 125 | Card Being Used in Off Hours | No | Card restricted to specific hours (new in 2026) |
| 129 | Suspected Counterfeit Card | No | Decline; log for fraud review |
| 131 | Invalid Expiration Date | No | Expiry date mismatch; request different card |
| 132 | Invalid PIN Format | No | PIN block format error; check PIN encryption |
| 134 | Invalid Track Data | No | Track 2 data corrupt or invalid |
| 208 | Lost Card | No | Card reported lost; do not return card |
| 209 | Stolen Card | No | Card reported stolen; do not return card |
| 224 | Revocation of All Authorizations | No | All auths revoked by issuer |
| 237 | No Sharing | No | Card cannot be used at this merchant |
| 238 | Card Being Used Differently | No | Unusual card usage pattern |
| 500 | Decline | No | Generic processor decline |
| 504 | Do Not Honor | No | Processor-level do not honor |
| 509 | Over Limit | No | Exceeds processor-defined limit |
| 514 | Invalid Account Number | No | Account not found at processor level |
| 517 | Invalid Amount | No | Amount rejected by processor |
| 704 | Pickup Card | No | Card should be retained (if possible); likely stolen/lost |
| 767 | Pickup Card (special condition) | No | Card should be retained; special condition flagged |

### 2.3 Declined - Referral

| Code | Description | Retryable | Action |
|------|-------------|-----------|--------|
| 105 | Refer to Card Issuer | No | Voice authorization required; call issuer |
| 107 | Refer to Card Issuer (special) | No | Voice authorization required; special condition |
| 108 | Refer to Card Issuer (special) | No | Voice authorization required; special condition |
| 708 | Honor with ID | No | Requires cardholder identification verification |
| 724 | Unable to Authorize | No | Issuer cannot authorize; try alternate method |
| 727 | PIN Required | No | PIN entry required for this transaction |
| 728 | PIN Not Selected | No | PIN CVM required but not used |
| 792 | Decline New Card Issued | No | Old card declined; new card has been issued |

### 2.4 Declined - Amount

| Code | Description | Retryable | Action |
|------|-------------|-----------|--------|
| 110 | Invalid Amount | No | Amount field malformed or out of range |
| 152 | Amount Exceeds Limit | No | Exceeds per-transaction limit |
| 222 | Amount Error | No | Amount calculation error |
| 521 | Insufficient Funds | No | Insufficient balance (processor-level) |
| 522 | Card Expired | No | Expired card (processor-level check) |
| 528 | Exceeds Withdrawal Limit | No | Exceeds allowed withdrawal amount |
| 770 | Amount Over Maximum | No | Exceeds maximum allowed transaction amount |
| 771 | Amount Under Minimum | No | Below minimum allowed transaction amount |

### 2.5 Declined - Transaction

| Code | Description | Retryable | Action |
|------|-------------|-----------|--------|
| 103 | Invalid Merchant (transaction) | No | Merchant not authorized for this transaction type |
| 106 | Allowable PIN Tries Exceeded | No | PIN locked; cannot retry PIN |
| 210 | Decline for CVV Failure | No | CVV/CVC verification failed |
| 212 | Invalid Transaction | No | Transaction type not valid for this card/merchant |
| 213 | Invalid Amount (transaction) | No | Amount not valid for this transaction |
| 214 | Invalid Card Number (transaction) | No | Card number not valid for this transaction |
| 215 | No Such Issuer | No | Issuer not found in routing tables |
| 216 | Decline - No Action Taken | No | Generic decline, no further info |
| 217 | Decline - File Temporarily Unavailable | Yes | Issuer file offline; may retry after delay |
| 218 | Decline - No Account | No | Account not found at issuer |
| 219 | Decline - RE-ENTER | No | Data entry error; re-enter transaction data |
| 244 | Decline - Use Chip | No | Chip card must use chip reader (not applicable for SoftPOS contactless) |
| 250 | Decline - Pick Up | No | Card pickup requested |
| 251 | Decline - Pick Up (special) | No | Card pickup requested; special condition |
| 252 | Decline - Pick Up | No | Card pickup requested |
| 253 | Decline - Pick Up (special) | No | Card pickup requested; special condition |
| 512 | Transaction Not Permitted | No | Processor does not allow this transaction type |
| 513 | Invalid PIN (transaction) | No | PIN validation failed at processor |
| 518 | Transaction Not Permitted (terminal) | No | Terminal not authorized for this transaction |
| 519 | Transaction Not Permitted (cardholder) | No | Cardholder not authorized for this transaction |
| 520 | Decline | No | Generic processor decline |
| 529 | Do Not Honor | No | Processor-level do not honor |
| 913 | Duplicate Transaction | No | STAN/RefNum not unique; generate new STAN/RefNum |
| 914 | Original Transaction Has Been Settled | No | Cannot void; use Refund instead |
| 915 | Transaction Not Found | No | Referenced transaction not on file |
| 916 | Original Authorization Not Found | No | OrigAuthGrp references invalid; verify all fields |
| 950 | Violation of Business Arrangement | No | Merchant/processor agreement issue |
| 954 | CCV Verification Failure | No | Card verification value mismatch |
| 958 | Transaction Not Permitted (terminal capability) | No | Terminal capability mismatch |
| 959 | Transaction Not Permitted (service) | No | Service not permitted |

### 2.6 System Errors

| Code | Description | Retryable | Action |
|------|-------------|-----------|--------|
| 001 | Schema Validation Error | No | Fix XML per XSD; check field order, data types |
| 111 | Card Not Supported | No | Card brand/type not configured for this MID |
| 112 | Invalid Transaction Type | No | TxnType not valid for this PymtType/card combo |
| 113 | Invalid Amount | No | Amount field format error |
| 130 | Invalid Format | No | Message format error |
| 133 | Expired Card | No | Card expiration date check failed |
| 135 | Invalid Terminal | No | TID not recognized; check TID configuration |
| 136 | Inactive Terminal | No | TID exists but is deactivated |
| 150 | Invalid Card Verification Value | No | CVV format error |
| 233 | Decline | No | System-level decline |
| 515 | Function Not Supported | No | Requested feature not enabled for this MID |
| 523 | Invalid Encryption Data | Yes | Encryption data malformed; check key setup |
| 524 | Encryption Key Synchronization Error | Yes | Key out of sync; request new key (EncryptionKeyRequest) |
| 525 | Invalid Encryption Data (duplicate) | No | Encryption error |
| 526 | Invalid Encryption Data (key) | No | Wrong encryption key used |
| 527 | Invalid Encryption Data (format) | No | Encryption format error |
| 530 | PIN Required | No | Transaction requires PIN but PINGrp not sent |
| 532 | Invalid Encryption Key | No | Encryption key not valid; request new session key (EncryptionKeyRequest) |
| 533 | Encryption Key Sync Error | No | Key out of sync; request new session key (EncryptionKeyRequest) |
| 534 | Encryption Error | No | Host decryption failed; check MSKeyID and session key validity |
| 542 | PIN Tries Exceeded | No | Card locked for PIN entry |
| 601 | Velocity Filter | No | Too many transactions in time window |
| 602 | Maximum Sale Filter | No | Amount exceeds max sale filter |
| 603 | Maximum Refund Filter | No | Amount exceeds max refund filter |
| 604 | Duplicate Filter | No | Duplicate transaction detected by filter |
| 722 | Invalid Debit Card | No | Card not valid for debit processing |
| 740 | Invalid Crypto | No | Cryptogram validation failed |
| 800 | Fleet Error | No | Fleet-specific processing error |
| 902 | Invalid Transaction | No | Transaction format not recognized by host |
| 903 | Re-Enter Transaction | No | Host requests re-entry; check all fields |
| 904 | Format Error | No | Field format does not match specification |
| 905 | Acquirer Not Supported | No | Acquirer configuration issue |
| 906 | System Error | Yes | Host system error; retry once, then TOR |
| 907 | Card Issuer or Switch Inoperative | Yes | Issuer offline; retry once, then TOR |
| 908 | Transaction Destination Not Found | No | Routing error; check card type and MID config |
| 909 | System Malfunction | Yes | Host malfunction; retry once, then TOR |
| 911 | Issuer Timeout | Yes | Issuer did not respond; retry once, then TOR |
| 920 | Security Violation | No | Authentication/credential error |
| 921 | Security Violation (repeat) | No | Repeated security failures |
| 923 | Request In Progress | No | Previous request still processing; wait |
| 924 | Limit Exceeded | No | Rate or volume limit exceeded |
| 940 | Fraud Decline | No | Fraud detection triggered |
| 941 | Fraud Decline (repeat) | No | Repeated fraud flags |
| 963 | Acquirer Channel Failure | No | Communication channel issue |

### 2.7 Retry-Eligible Codes

These codes indicate a transient condition where a **single retry** after a short delay (5-10 seconds) may succeed. Never retry more than once automatically.

| Code | Description | Retry Strategy |
|------|-------------|----------------|
| 505 | Decline (transient) | Retry once after 5s |
| 508 | Decline (transient) | Retry once after 5s |
| 511 | Decline (transient) | Retry once after 5s |
| 516 | Decline (transient) | Retry once after 5s |
| 524 | Encryption Key Sync Error | Request new key via EncryptionKeyRequest, then retry |
| 909 | System Malfunction | Retry once after 5-10s; if fails again, send TOR |
| 911 | Issuer Timeout | Retry once after 5-10s; if fails again, send TOR |
| 920 | Security Violation | Only retry after verifying credentials; typically not transient |

### 2.8 Prepaid

| Code | Description | Retryable | Action |
|------|-------------|-----------|--------|
| 151 | Insufficient Funds (prepaid) | No | Suggest partial auth or different payment |
| 153 | Exceeds Card Limit | No | Card-level limit reached |
| 154 | Card Already Active | No | Activation not needed |
| 155 | Card Not Active | No | Card needs activation first |
| 156 | Maximum Value Exceeded | No | Load amount exceeds max card value |
| 157 | Invalid Card | No | Prepaid card not recognized |
| 300 | Success (prepaid) | N/A | Prepaid operation successful |
| 301 | Invalid Card Number (prepaid) | No | Prepaid card number invalid |
| 302 | Card Not Active (prepaid) | No | Card needs activation |
| 303 | Card Already Active (prepaid) | No | Already activated |
| 304 | Card Not Found | No | Prepaid card not on file |
| 305 | Insufficient Balance (prepaid) | No | Not enough prepaid balance |
| 306 | Invalid Expiration (prepaid) | No | Prepaid card expired |
| 307 | Invalid Amount (prepaid) | No | Amount not valid for prepaid |
| 308 | Invalid Transaction (prepaid) | No | Transaction type not valid for prepaid |
| 309 | Card Restricted (prepaid) | No | Prepaid card restricted |
| 310 | Invalid Merchant (prepaid) | No | Merchant not valid for prepaid |
| 311 | Invalid Currency (prepaid) | No | Currency not supported for prepaid |
| 312 | Card In Use | No | Prepaid card already in active transaction |
| 313 | Invalid PIN (prepaid) | No | Prepaid PIN incorrect |
| 314 | Maximum Loads Exceeded | No | Too many loads in period |
| 315 | Maximum Unloads Exceeded | No | Too many unloads in period |
| 316 | Invalid Amount (over max) | No | Amount over prepaid maximum |
| 317 | Card is Void | No | Prepaid card has been voided |
| 318 | Card Locked | No | Prepaid card is locked |
| 319 | Card Lost/Stolen | No | Prepaid card reported lost/stolen |
| 320 | Card Suspended | No | Prepaid card suspended |
| 321 | Card Damaged | No | Prepaid card flagged as damaged |
| 322 | Invalid Region | No | Region restriction on prepaid card |
| 323 | Invalid Transaction Count | No | Transaction count limit reached |
| 324 | Bad Track Data (prepaid) | No | Track data unreadable |
| 325 | Card Not Allowed | No | Card type not allowed |
| 326 | Invalid Issue Date | No | Issue date error |
| 327 | Pending Activation | No | Card activation in progress |
| 328 | Card Already Loaded | No | Duplicate load attempt |
| 329 | Card Not Loaded | No | Card has no balance |
| 330 | Maximum Transaction Amount | No | Single transaction limit reached |
| 331 | Maximum Daily Amount | No | Daily spending limit reached |
| 332 | Maximum Monthly Amount | No | Monthly spending limit reached |
| 333 | Maximum Yearly Amount | No | Yearly spending limit reached |
| 334 | Maximum Lifetime Amount | No | Lifetime spending limit reached |
| 335 | Maximum Daily Transaction Count | No | Daily transaction count limit |
| 336 | Maximum Monthly Transaction Count | No | Monthly transaction count limit |
| 337 | Maximum Yearly Transaction Count | No | Yearly transaction count limit |
| 338 | Maximum Lifetime Transaction Count | No | Lifetime transaction count limit |
| 339 | Blocked MCC | No | MCC restricted for this prepaid card |
| 340 | Blocked Merchant | No | Merchant restricted for this prepaid card |
| 341 | Invalid Activation Amount | No | Activation amount not valid |
| 342 | Invalid Reload Amount | No | Reload amount not valid |
| 343 | Account Temporarily Unavailable | Yes | Prepaid system temporary issue |
| 344 | Exceeds Number of Allowed Accounts | No | Account limit reached |
| 345 | Account Closed | No | Prepaid account closed |
| 355 | Prepaid Not Enabled | No | Prepaid feature not enabled for MID |
| 356-385 | Various Prepaid Errors | No | Prepaid-specific error conditions |

### 2.9 Fleet

| Code | Description | Retryable | Action |
|------|-------------|-----------|--------|
| 548-569 | Fleet Validation Errors | No | Fleet card data validation failures |
| 570-577 | Fleet Restriction Errors | No | Fleet card usage restrictions |
| 801-840 | Fleet Processing Errors | No | Fleet-specific processing errors |

> **Note:** Fleet codes are not applicable to Softpay SoftPOS integration (Fleet payment type not selected in RSO024).

### 2.10 Check

| Code | Description | Retryable | Action |
|------|-------------|-----------|--------|
| 501 | Check Decline | No | Check transaction declined |
| 502 | Check Processing Error | No | Check processing failure |
| 506 | Check Not Supported | No | Check type not supported |
| 539-541 | Check Validation Errors | No | Check data validation failures |
| 721 | Check Decline | No | Check declined by processor |
| 723 | Check Decline (special) | No | Check special condition decline |
| 726 | Check Error | No | Check processing error |
| 729 | Check Decline (fraud) | No | Check flagged for fraud |
| 731 | Check Decline (account) | No | Check account issue |

> **Note:** Check codes are not applicable to Softpay SoftPOS integration (Check payment type not selected in RSO024).

### 2.11 TransArmor

| Code | Description | Retryable | Action |
|------|-------------|-----------|--------|
| 402 | TransArmor Service Unavailable | Yes | TA service temporarily down; retry after delay |
| 403 | TransArmor Invalid Token | No | Token not found or expired; re-tokenize |
| 404 | TransArmor Invalid Data | No | TransArmor encryption data invalid |

> **Note:** TransArmor is not selected in RSO024 project profile. These codes may still appear if token-based transactions are attempted.

### 2.12 Money Transfer

| Code | Description | Retryable | Action |
|------|-------------|-----------|--------|
| 578-588 | Money Transfer Errors | No | Various money transfer decline/error conditions |

> **Note:** Money Transfer is not enabled in RSO024.

### 2.13 Debit

| Code | Description | Retryable | Action |
|------|-------------|-----------|--------|
| 414 | Invalid PIN | No | Debit PIN validation failed; allow cardholder to re-enter if PIN tries remain |

### 2.14 Bill Payment

| Code | Description | Retryable | Action |
|------|-------------|-----------|--------|
| 790 | Bill Payment Decline | No | Bill payment declined |
| 791 | Bill Payment Error | No | Bill payment processing error |

> **Note:** Bill Payment is not selected in RSO024.

### 2.15 DCC

| Code | Description | Retryable | Action |
|------|-------------|-----------|--------|
| 350 | DCC Not Available | No | DCC not available for this card/transaction |
| 351 | DCC Currency Not Supported | No | Cardholder's currency not supported for DCC |

### 2.16 3D Secure

| Code | Description | Retryable | Action |
|------|-------------|-----------|--------|
| 531 | 3DS Authentication Failed | No | 3D Secure authentication failure |

> **Note:** 3DS is for e-commerce; not applicable for Softpay card-present SoftPOS.

### 2.17 Other

| Code | Description | Retryable | Action |
|------|-------------|-----------|--------|
| 220 | Decline | No | Generic decline |
| 221 | Decline | No | Generic decline |
| 222 | Amount Error | No | Amount calculation error |
| 223 | Decline | No | Generic decline |
| 224 | Revocation of All Authorizations | No | All auths revoked |
| 225-232 | Various Declines | No | Card/issuer specific declines |
| 242-248 | Various Declines | No | Card/issuer specific declines |
| 254 | Decline | No | Generic decline |
| 430 | Decline | No | Generic decline |
| 503 | Decline | No | Generic decline |
| 507 | Decline | No | Generic decline |
| 510 | Decline | No | Generic decline |
| 702 | Account/Card Error | No | Card data error at network level |
| 772 | Decline (network) | No | Network-level decline |
| 773 | Decline (network) | No | Network-level decline |
| 774 | Decline (network) | No | Network-level decline |
| 775 | Decline (network) | No | Network-level decline |
| 776 | Decline (network) | No | Network-level decline |
| 782 | Decline (network) | No | Network-level decline |
| 806 | Decline | No | Generic decline |
| 826 | Decline | No | Generic decline |
| 827 | Decline | No | Generic decline |
| 942 | Decline (fraud) | No | Fraud-related decline |
| 944 | Decline (fraud) | No | Fraud-related decline |

---

## 3. Softpay-Specific Response Handling

This section provides terminal-level guidance for codes most likely encountered in a contactless SoftPOS card-present environment.

### 3.1 Common Approval Codes

#### 000 - Approved

```
Terminal Action:
  1. Store from RespGrp: AuthID, RespDate, SettlDate, AddlRespData
  2. Store scheme-specific refs:
     - Visa:     VisaGrp.TransID
     - MC:       MCGrp.BanknetData
     - Amex:     AmexGrp.AmExTranID
     - Discover: DSGrp.DiscoverNRID
  3. Store AuthNetID and AuthNetName for receipt
  4. Display "APPROVED" on terminal screen
  5. Generate receipt with mandatory EMV fields
  6. For dual-message (RSO024 default):
     - Queue Completion message
     - Completion must reference OrigAuthGrp fields from this auth
  7. Send EMV scripts to card if present in response (Tag 71/72)
```

#### 002 - Partial Approval

```
Terminal Action:
  1. Compare requested TxnAmt with response TxnAmt
  2. Calculate remaining balance: requested - approved
  3. Display to merchant:
     "PARTIAL APPROVAL: $XX.XX of $YY.YY approved"
     "Accept partial payment? [Yes/No]"
  4. If merchant ACCEPTS:
     - Complete for the approved amount
     - Prompt for second payment method for remaining balance
  5. If merchant DECLINES:
     - Send Void (ReversalInd="Void") using original TxnAmt
     - Same RefNum as original request
     - Prompt for alternate payment method for full amount
  6. IMPORTANT: Partial Approval support is generally required for
     card-present environments (especially with PIN Debit and prepaid)
```

#### 003 - VIP Approval

```
Terminal Action:
  1. Treat identically to code 000
  2. Store all the same fields
  3. Display "APPROVED" (not "VIP APPROVED") to cardholder
  4. Log the VIP status internally for reporting
```

### 3.2 Common Contactless Decline Codes

#### 100 - Do Not Honor

```
Terminal Action:
  1. Display "DECLINED" to cardholder
  2. Do NOT display specific reason (security)
  3. Suggest trying another card or payment method
  4. Do NOT retry automatically
  5. Log: RespCode=100, AddlRespData, AuthNetID
```

#### 101 - Expired Card

```
Terminal Action:
  1. Display "CARD EXPIRED" or "DECLINED" to cardholder
  2. Suggest using a different card
  3. Note: Contactless cards may have expired since last use
```

#### 116 - Insufficient Funds

```
Terminal Action:
  1. If Partial Auth enabled (RespCode 002 would come instead):
     - Should not see 116 if partial auth is properly configured
  2. Display "DECLINED - INSUFFICIENT FUNDS" to cardholder
  3. Suggest lower amount or different payment method
  4. Do NOT retry with same amount
```

#### 117 - Incorrect PIN

```
Terminal Action:
  1. Check PIN try counter (EMV Tag 9F17) if available
  2. If tries remain: allow cardholder to re-enter PIN
  3. If no tries remain: decline transaction
  4. Display "INCORRECT PIN - PLEASE TRY AGAIN" (if retries available)
  5. After max retries: "DECLINED" and suggest different payment
  6. Log: increment internal PIN fail counter per card
```

#### 119 - Transaction Not Permitted

```
Terminal Action:
  1. Display "DECLINED" to cardholder
  2. Common cause: card has restrictions (geographic, transaction type,
     time-of-day, merchant category)
  3. Suggest trying a different card
  4. Do NOT retry
```

#### 120 - Transaction Not Permitted (Terminal)

```
Terminal Action:
  1. Display "DECLINED" to cardholder
  2. Check terminal configuration (TID, TermCatCode, POSEntryMode)
  3. May indicate SoftPOS terminal type not accepted by issuer
  4. Log and escalate if recurring for same card brand
```

#### 500 - Decline (Generic Processor)

```
Terminal Action:
  1. Display "DECLINED" to cardholder
  2. Generic processor-level decline
  3. Do NOT retry
  4. Suggest alternate payment method
```

### 3.3 PIN-Related Errors

#### 106 - Allowable PIN Tries Exceeded

```
Terminal Action:
  1. Display "PIN LOCKED" or "DECLINED" to cardholder
  2. Card is now PIN-locked at the issuer
  3. Do NOT allow further PIN attempts
  4. Suggest non-PIN payment (e.g., contactless below CVM limit)
  5. Cardholder must contact their bank to reset PIN
```

#### 117 - Incorrect PIN

```
Terminal Action:
  See Section 3.2 above (117 - Incorrect PIN)
```

#### 530 - PIN Required

```
Terminal Action:
  1. Transaction was sent without PIN but PIN is required
  2. For SoftPOS contactless above CVM limit: prompt for PIN entry
  3. Re-submit transaction with PINGrp populated
  4. If SoftPOS cannot capture PIN: decline transaction
  5. Check: is PINGrp correctly populated for PIN Debit?
```

### 3.4 Key Management Errors

#### 532 - Invalid Encryption Key

```
Terminal Action:
  1. Master Session encryption key is invalid or has been revoked
  2. Request new session key via EncryptionKeyRequest (TxnType=EncryptionKeyRequest)
  3. Do NOT retry original transaction until key is refreshed
  4. Log: include MSKeyID in error report
  5. If key refresh fails: escalate to Softpay key management
  6. Merchant impact: all PIN Debit transactions will fail until resolved
```

#### 533 - Encryption Key Sync Error

```
Terminal Action:
  1. Session key is out of sync with host (MSKeyID mismatch)
  2. Request new session key via EncryptionKeyRequest
  3. After new key: retry original transaction with fresh MSKeyID
  4. Common cause: session key expired (24h rotation) or app restart
  5. Prevention: check key age before each PIN Debit transaction
```

#### 534 - Encryption Error

```
Terminal Action:
  1. Host could not decrypt the PIN block
  2. Check: correct Master Session key in use (MSKeyID matches active session)?
  3. Check: PIN block format matches Fiserv requirement (ISO Format 0)?
  4. Request new session key via EncryptionKeyRequest
  5. If persistent: escalate -- may indicate master key mismatch between environments
```

#### 542 - PIN Tries Exceeded

```
Terminal Action:
  1. Card is locked for PIN at the processor level
  2. Identical handling to code 106
  3. Display "DECLINED" to cardholder
  4. Cardholder must contact their issuing bank
```

### 3.5 Timeout and System Errors

#### 906 - System Error

```
Terminal Action:
  1. Fiserv host encountered a system error
  2. RETRY ONCE after 5-10 second delay
  3. If retry also returns 906:
     - Send Timeout Reversal (TOR) for safety
     - ReversalInd = "Timeout"
     - Display "UNABLE TO PROCESS - PLEASE TRY AGAIN" to cardholder
  4. Log: timestamp, RefNum, STAN for reconciliation
```

#### 909 - System Malfunction

```
Terminal Action:
  1. Fiserv system malfunction (broader than 906)
  2. RETRY ONCE after 5-10 second delay
  3. If retry also fails:
     - Send TOR
     - Display "SYSTEM UNAVAILABLE - PLEASE TRY AGAIN LATER"
  4. Consider: if multiple 909s in sequence, the host may be down
  5. Implement circuit breaker: after 3 consecutive 909s,
     pause transactions for 30 seconds before retrying
```

#### 911 - Issuer Timeout

```
Terminal Action:
  1. The issuer (cardholder's bank) did not respond to Fiserv
  2. RETRY ONCE after 5-10 second delay
  3. If retry also returns 911:
     - Send TOR
     - Display "UNABLE TO REACH YOUR BANK - PLEASE TRY AGAIN"
  4. This is issuer-specific; other card brands may still work
  5. Log: note the card brand/issuer for pattern detection
```

#### No Response (Timeout)

```
Terminal Action:
  1. No response received within configured timeout
     (recommended: 30-60 seconds for authorization)
  2. IMMEDIATELY send Timeout Reversal (TOR):
     - Message: ReversalRequest
     - ReversalInd = "Timeout"
     - RefNum = SAME as original transaction
     - Include full OrigAuthGrp:
       - OrigAuthID (leave empty if no auth response received)
       - OrigRespDate (leave empty if no response)
       - OrigLocalDateTime = original LocalDateTime
       - OrigTrnmsnDateTime = original TrmnsnDateTime
       - OrigSTAN = original STAN
       - OrigRespCode (leave empty if no response)
     - Scheme ref fields (TransID, BanknetData, AmExTranID,
       DiscoverNRID) are EXEMPT for TOR (per 2026 UMF change)
  3. If TOR succeeds (RespCode 000): reversal confirmed
  4. If TOR also times out:
     - Retry TOR up to 3 times with exponential backoff:
       1st retry: 10s, 2nd: 30s, 3rd: 60s
     - If all TOR retries fail: log for manual reconciliation
     - Flag transaction in Softpay backend as "TOR_UNRESOLVED"
  5. NEVER assume the original transaction was declined
  6. NEVER retry the original transaction after sending TOR
  7. Display "UNABLE TO PROCESS" to cardholder
```

### 3.6 Duplicate Transaction

#### 913 - Duplicate Transaction

```
Terminal Action:
  1. STAN or RefNum matches a recently processed transaction
  2. Check STAN generation:
     - Must be unique per day per MID+TID
     - Range: 000001-999999
     - Reset daily or use incrementing counter
  3. Check RefNum generation:
     - Must be unique per day per MID+TID
     - Max 22 alphanumeric characters
  4. Generate new STAN and RefNum, then resubmit
  5. Do NOT reuse STAN/RefNum from a previous transaction
  6. Prevention: use atomic counter persisted to disk
     to survive app restarts/crashes
```

### 3.7 Void / Reversal Issues

#### 414 - Invalid PIN (Debit Void)

```
Terminal Action:
  1. PIN verification failed during a debit void/reversal
  2. For void of PIN Debit: PIN may be required again
  3. Allow cardholder to re-enter PIN
  4. If PIN tries exhausted: cannot void via PIN Debit
  5. Alternative: process as credit refund if applicable
```

#### 914 - Original Transaction Has Been Settled

```
Terminal Action:
  1. Cannot Void -- the original transaction has already been settled
  2. This means the daily settlement cut-off has passed
  3. SOLUTION: Use Refund (TxnType=Refund) instead of Void
  4. Refund requires:
     - OrigAuthGrp with original transaction references
     - Full card data or token
     - New STAN and RefNum
  5. Display to merchant: "Transaction already settled. Processing refund."
  6. Note: Voids must be submitted within 25 minutes of original
     (except Credit Completion voids: before merchant cut-off)
```

#### 915 - Transaction Not Found

```
Terminal Action:
  1. The referenced original transaction is not on file
  2. Verify OrigAuthGrp fields match the original transaction exactly:
     - OrigAuthID
     - OrigRespDate
     - OrigLocalDateTime
     - OrigTrnmsnDateTime
     - OrigSTAN
     - OrigRespCode
  3. Common causes:
     - Wrong MID/TID combination
     - Incorrect date/time in OrigAuthGrp
     - Original was already reversed
  4. Log and investigate; do not retry blindly
```

#### 916 - Original Authorization Not Found

```
Terminal Action:
  1. Similar to 915 but specific to authorization lookup failure
  2. Verify ALL OrigAuthGrp fields:
     - OrigAuthID = AuthID from original auth response
     - OrigRespDate = RespDate from original auth response
     - OrigLocalDateTime = LocalDateTime from original auth request
     - OrigTrnmsnDateTime = TrmnsnDateTime from original auth request
     - OrigSTAN = STAN from original auth request
     - OrigRespCode = RespCode from original auth response
  3. Also verify scheme-specific fields in Completion/Void:
     - Visa: TransID must match original
     - MC: BanknetData must match original
     - Amex: AmExTranID must match original
     - Discover: DiscoverNRID must match original
  4. If fields are correct: original may have expired or been
     reversed by the host automatically
```

---

## 4. Authorizing Network IDs

The `AuthNetID` field in `RespGrp` identifies the network that authorized the transaction. The `AuthNetName` field provides the human-readable name. Both should be stored for receipt printing and reconciliation.

Source: UMF Appendix A, Section 18.5 (Authorizing Network ID)

| AuthNetID | Network Name | Notes |
|-----------|-------------|-------|
| 01 | Visa | Primary Visa network |
| 02 | MasterCard | Primary Mastercard network |
| 03 | Discover | Discover Financial Services |
| 04 | Diners Club | Processed through Discover |
| 05 | American Express (Amex) | Amex network |
| 06 | JCB | Japan Credit Bureau |
| 07 | UnionPay | China UnionPay |
| 08 | Star | PIN Debit network (Star) |
| 09 | NYCE | PIN Debit network (NYCE) |
| 10 | Pulse | PIN Debit network (Pulse) |
| 11 | Accel | PIN Debit network (Accel) |
| 12 | AFFN (Armed Forces Financial Network) | Military debit network |
| 13 | Interlink | Visa PIN Debit (Interlink) |
| 14 | Maestro | Mastercard debit (Maestro) |
| 15 | Star SE (Star Southeast) | Regional PIN Debit |
| 16 | Star NE (Star Northeast) | Regional PIN Debit |
| 17 | Star W (Star West) | Regional PIN Debit |
| 18 | Jeanie | PIN Debit network |
| 19 | CU24 (Credit Union 24) | Credit union ATM/debit |
| 20 | Shazam | PIN Debit network (Shazam) |
| 21 | EBT | Electronic Benefits Transfer |
| 22 | Visa ReadyLink | Visa reloadable prepaid |
| 23 | MasterCard MoneySend | MC money transfer |
| 24 | PAVD (Visa Direct) | Visa push payments |
| 25 | Nets (Denmark) | Danish debit network |
| 26 | BancNet | Philippines debit network |
| 27 | Alaska Option | Regional PIN Debit |
| 28 | MoneyPass | Surcharge-free ATM network |

> **Note:** For Softpay RSO024 integration, the most commonly returned values will be:
> - **01** (Visa) for Visa credit/debit
> - **02** (MasterCard) for Mastercard credit/debit
> - **03** (Discover) for Discover
> - **04** (Diners Club) for Diners
> - **05** (Amex) for American Express
> - **08-20** for PIN Debit routed through various debit networks
>
> The `AuthNetID` and `AuthNetName` should be:
> - Stored for each transaction for reconciliation
> - Printed on the receipt when required (especially for CAID/dual-AID transactions)
> - Used to determine which scheme-specific response fields to read

---

## 5. RejectResponse Error Codes

A `RejectResponse` is returned when the message is invalid and was **not forwarded to the processor**. The error details appear in the `RespGrp.ErrorData` field.

| ErrorData | Description | Resolution |
|-----------|-------------|------------|
| 001 | Schema Validation Failure | XML does not conform to `UMF_XML_SCHEMA.xsd`; validate against XSD before sending |
| 002 | Invalid Payment Type | `PymtType` value not recognized; check enum values |
| 003 | Invalid Transaction Type | `TxnType` not valid for this PymtType |
| 004 | Missing Mandatory Field | Required field not present; check field matrix for this TxnType |
| 005 | Invalid Field Value | Field value out of range or wrong data type |
| 006 | Invalid MID/TID | MerchID or TermID not recognized; check credentials |
| 007 | Invalid TPP ID | TPPID does not match project (must be `RSO024`) |
| 008 | Message Too Large | Exceeds 14,336 byte limit; reduce optional fields |
| 904 | Format Error | General format error; check field lengths, types, and order |

**Key Points:**
- Do NOT send a TOR for a RejectResponse -- the message never reached the processor
- Fix the message content and resend
- Validate all XML against the XSD before transmission
- Check that elements appear in the correct order (per XSD sequence)
- Ensure no empty tags (`<Tag></Tag>` or `<Tag />` are both invalid)

---

## 6. Implementation Notes

### 6.1 Response Code Field Details

| XML Path | Field | Type | Length | Description |
|----------|-------|------|--------|-------------|
| `RespGrp.RespCode` | Response Code | an | 3 | The primary response code (see tables above) |
| `RespGrp.AuthID` | Authorization ID | an | 6 | Authorization code from issuer (store for Completion/Void) |
| `RespGrp.RespDate` | Response Date | N | 6 | MMDDYY (store for OrigAuthGrp) |
| `RespGrp.AddlRespData` | Additional Response Data | ans | 40 | Supplementary info (may contain issuer message) |
| `RespGrp.SettlDate` | Settlement Date | N | 4 | MMDD (expected settlement date) |
| `RespGrp.AuthNetID` | Authorizing Network ID | an | 2 | Network that authorized (see Section 4) |
| `RespGrp.AuthNetName` | Authorizing Network Name | ans | 30 | Human-readable network name |
| `RespGrp.ErrorData` | Error Data | ans | 80 | Error details for RejectResponse |
| `RespGrp.RtInd` | Routing Indicator | an | 2 | Routing path used |
| `RespGrp.SigInd` | Signature Indicator | an | 1 | Whether signature is required |

### 6.2 Fields to Store from Every Approved Response

For every approved transaction (RespCode 000, 002, 003, 785), the Softpay terminal MUST persist the following fields for use in subsequent transactions (Completion, Void, Reversal):

**From RespGrp:**
- `AuthID` -- needed in `OrigAuthGrp.OrigAuthID`
- `RespDate` -- needed in `OrigAuthGrp.OrigRespDate`
- `RespCode` -- needed in `OrigAuthGrp.OrigRespCode`
- `AuthNetID` -- needed in `OrigAuthGrp.OrigAuthNetID`
- `SettlDate` -- for reconciliation
- `AddlRespData` -- for logging/debugging

**From Request (echo back for OrigAuthGrp):**
- `LocalDateTime` -- needed in `OrigAuthGrp.OrigLocalDateTime`
- `TrmnsnDateTime` -- needed in `OrigAuthGrp.OrigTrnmsnDateTime`
- `STAN` -- needed in `OrigAuthGrp.OrigSTAN`

**Scheme-Specific (from response):**
- Visa: `VisaGrp.TransID` -- mandatory in Completion/Void (except TOR)
- MC: `MCGrp.BanknetData` -- mandatory in Completion/Void (except TOR)
- Amex: `AmexGrp.AmExTranID` -- mandatory in Completion/Void (except TOR)
- Discover: `DSGrp.DiscoverNRID` -- mandatory in Completion/Void (except TOR)

### 6.3 Void Timing Rules (from Appendix D)

| Transaction Type | Void Window |
|------------------|------------|
| Authorization (Credit) | Within 25 minutes of original |
| Completion (Credit) | Before merchant cut-off for the day |
| Refund | Within 25 minutes of original |
| PIN Debit Authorization | Within 25 minutes of original |

If the void window has expired, expect RespCode **914** (Settlement Already Occurred). Use a Refund instead.

### 6.4 Response Handling Priority Order

When processing a response, evaluate in this order:

1. **Check for RejectResponse** -- if yes, fix message, do NOT send TOR
2. **Check for timeout (no response)** -- if yes, send TOR immediately
3. **Read RespCode** -- determine category (approved / declined / system)
4. **Read AuthID** -- store if present (even on some declines)
5. **Read scheme-specific fields** -- TransID, BanknetData, etc.
6. **Read AddlRespData** -- additional context for logging
7. **Read AuthNetID/AuthNetName** -- for receipt and routing confirmation
8. **Read EMV response data** -- XCodeRespCode, Processing Indicator/Information
9. **Apply terminal action** per decision tree above

### 6.5 Cardholder-Facing Messages

Map response codes to generic, cardholder-safe messages. Never expose raw response codes or internal error details to the cardholder.

| Internal Condition | Cardholder Display |
|---|---|
| RespCode 000, 003, 785 | "APPROVED" |
| RespCode 002 | "PARTIALLY APPROVED - $XX.XX" |
| RespCode 100, 500, 504, 509, 520, 529 | "DECLINED" |
| RespCode 101, 131, 133, 522 | "CARD EXPIRED" |
| RespCode 116, 121, 151, 521, 528 | "INSUFFICIENT FUNDS" |
| RespCode 117, 414, 513 | "INCORRECT PIN" |
| RespCode 106, 542 | "PIN LOCKED" |
| RespCode 102, 129, 208, 209 | "DECLINED" (do not reveal fraud/lost/stolen) |
| RespCode 105, 107, 108 | "PLEASE CONTACT YOUR BANK" |
| RespCode 119, 120, 512, 518, 519 | "TRANSACTION NOT PERMITTED" |
| RespCode 530, 727, 728 | "PIN REQUIRED" |
| RespCode 906, 909, 911 | "UNABLE TO PROCESS - PLEASE TRY AGAIN" |
| RespCode 913 | "PLEASE TRY AGAIN" (system will generate new STAN) |
| RespCode 914 | (merchant-facing only) "ALREADY SETTLED - USE REFUND" |
| Timeout / no response | "UNABLE TO PROCESS" |
| RejectResponse | "UNABLE TO PROCESS" |
| All other declines | "DECLINED" |
