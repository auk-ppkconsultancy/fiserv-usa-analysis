package com.softpay.fiserv;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Fiserv Rapid Connect UMF (Universal Message Format) XML message builder.
 *
 * Builds XML requests per the gmfV15.04 schema (UMF_XML_SCHEMA.xsd).
 * All elements must appear in the exact order defined in the XSD.
 *
 * Key rules:
 * - Max message size: 14,336 bytes
 * - No empty XML tags (both <Elem></Elem> and <Elem/> are invalid)
 * - Amounts in minor units, no decimal point (e.g., "000000012050" = $120.50 USD)
 * - STAN: 6-digit, unique per day per MID+TID, range 000001-999999
 * - RefNum: unique per day per MID+TID, must match across subsequent transactions
 */
public class FiservUmfMessageBuilder {

    private static final String GMF_NAMESPACE = "com/fiserv/Merchant/gmfV15.04";
    private static final String TPP_ID = "RSO024";
    private static final String GROUP_ID = "20001";
    private static final String CURRENCY_USD = "840";
    private static final DateTimeFormatter DT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // =========================================================================
    // 1. CREDIT AUTHORIZATION — Contactless EMV with Track2
    // =========================================================================

    /**
     * Builds a Credit Authorization request for a contactless EMV tap.
     *
     * This is the primary transaction flow for Softpay SoftPOS:
     * Customer taps a contactless card → NFC reads Track2 equivalent + EMV data
     * → Authorization sent to Fiserv → Completion sent later to capture.
     *
     * POS Entry Mode "071" = contactless EMV (ICC with contactless interface).
     * POS Entry Mode "901" = contactless magnetic stripe (MSR contactless).
     * For EMV contactless, use "071". For MSR contactless fallback, use "901".
     */
    public static String buildCreditAuthorizationContactless(
            String merchantId,
            String terminalId,
            String stan,
            String refNum,
            String orderNum,
            String merchantCatCode,
            long amountInCents,
            String track2Data,       // e.g., "4005520000000947=25121011000012300000"
            String cardType,         // e.g., "Visa", "MasterCard", "Amex", "Discover", "Diners"
            String emvTlvHexString,  // Concatenated TLV hex from contactless kernel
            String cardSeqNum        // EMV Card Sequence Number (Tag 5F34), nullable
    ) throws Exception {

        StringWriter sw = new StringWriter();
        XMLStreamWriter w = XMLOutputFactory.newInstance().createXMLStreamWriter(sw);

        w.writeStartDocument("UTF-8", "1.0");
        w.writeStartElement("GMF");
        w.writeDefaultNamespace(GMF_NAMESPACE);

        w.writeStartElement("CreditRequest");

        // --- CommonGrp (mandatory, must be first) ---
        w.writeStartElement("CommonGrp");
        writeElement(w, "PymtType", "Credit");
        writeElement(w, "TxnType", "Authorization");
        writeElement(w, "LocalDateTime", localDateTimeNow());
        writeElement(w, "TrnmsnDateTime", utcDateTimeNow());
        writeElement(w, "STAN", stan);
        writeElement(w, "RefNum", refNum);
        writeElement(w, "OrderNum", orderNum);
        writeElement(w, "TPPID", TPP_ID);
        writeElement(w, "TermID", terminalId);
        writeElement(w, "MerchID", merchantId);
        writeElement(w, "MerchCatCode", merchantCatCode);
        writeElement(w, "POSEntryMode", "071");   // Contactless EMV (ICC)
        writeElement(w, "POSCondCode", "00");      // Normal transaction
        writeElement(w, "TermCatCode", "09");       // Mobile POS / mPOS (cellphone/tablet as terminal)
        writeElement(w, "TermEntryCapablt", "04");  // Contactless read capability
        writeElement(w, "TxnAmt", formatAmount(amountInCents));
        writeElement(w, "TxnCrncy", CURRENCY_USD);
        writeElement(w, "TermLocInd", "0");         // On-premises
        writeElement(w, "CardCaptCap", "1");         // Card capture capable
        writeElement(w, "GroupID", GROUP_ID);
        w.writeEndElement(); // CommonGrp

        // --- CardGrp (card data — Track2 from contactless read) ---
        w.writeStartElement("CardGrp");
        writeElement(w, "Track2Data", track2Data);
        writeElement(w, "CardType", cardType);
        w.writeEndElement(); // CardGrp

        // --- AddtlAmtGrp (Partial Authorization capability) ---
        w.writeStartElement("AddtlAmtGrp");
        writeElement(w, "PartAuthrztnApprvlCapablt", "1"); // Supports partial approval
        w.writeEndElement(); // AddtlAmtGrp

        // --- EMVGrp (EMV chip data from contactless kernel) ---
        // EMVData contains concatenated TLV tags as a hex string.
        // The contactless kernel produces this after the tap is complete.
        // Example tags included: 9F26 (App Cryptogram), 9F27 (CID), 9F10 (IAD),
        // 9F37 (Unpredictable Number), 9F36 (ATC), 95 (TVR), 9A (Txn Date),
        // 9C (Txn Type), 5F2A (Currency Code), 82 (AIP), 84 (DF Name/AID),
        // 9F02 (Amount Authorized), 9F03 (Amount Other), 9F1A (Country Code),
        // 9F33 (Terminal Capabilities), 9F34 (CVM Results), 9F35 (Terminal Type),
        // 5F24 (App Expiry Date), 9F09 (App Version Number), 9F41 (Txn Seq Counter)
        w.writeStartElement("EMVGrp");
        writeElement(w, "EMVData", emvTlvHexString);
        if (cardSeqNum != null) {
            writeElement(w, "CardSeqNum", String.format("%03d", Integer.parseInt(cardSeqNum)));
        }
        w.writeEndElement(); // EMVGrp

        // --- VisaGrp / MCGrp / DSGrp / AmexGrp (scheme-specific, mutually exclusive) ---
        // Only ONE of these groups should be present based on CardType.
        writeSchemeGroup(w, cardType, true);

        w.writeEndElement(); // CreditRequest
        w.writeEndElement(); // GMF
        w.writeEndDocument();
        w.flush();

        return sw.toString();
    }

    // =========================================================================
    // 2. DEBIT AUTHORIZATION — Contactless EMV with Track2 + PIN Block
    // =========================================================================

    /**
     * Builds a Debit Authorization request with PIN encryption.
     *
     * PIN Debit requires the PINGrp with encrypted PIN data.
     * Softpay uses Master Session Encryption (HSM) with TR-31 key blocks.
     *
     * The PINGrp contains:
     * - PINData:          16-hex-char encrypted PIN block (e.g., ISO Format 0/ISO-4)
     * - KeySerialNumData: Key Serial Number for DUKPT (20 hex chars), OR
     * - MSKeyID:          Master Session Key ID (for Master Session encryption)
     * - NumPINDigits:     Number of digits in the PIN (optional)
     *
     * For Master Session Encryption:
     *   PINData + MSKeyID are used (no KSN).
     *   The session key is obtained via TxnType=EncryptionKeyRequest.
     *
     * For DUKPT:
     *   PINData + KeySerialNumData are used.
     */
    public static String buildDebitAuthorizationWithPin(
            String merchantId,
            String terminalId,
            String stan,
            String refNum,
            String orderNum,
            String merchantCatCode,
            long amountInCents,
            String track2Data,
            String emvTlvHexString,
            String cardSeqNum,
            // PIN data
            String pinBlock,          // 16 hex chars — encrypted PIN block
            String keySerialNumber,   // 20 hex chars — DUKPT KSN (null if using Master Session)
            String masterSessionKeyId // Master Session Key ID (null if using DUKPT)
    ) throws Exception {

        StringWriter sw = new StringWriter();
        XMLStreamWriter w = XMLOutputFactory.newInstance().createXMLStreamWriter(sw);

        w.writeStartDocument("UTF-8", "1.0");
        w.writeStartElement("GMF");
        w.writeDefaultNamespace(GMF_NAMESPACE);

        w.writeStartElement("DebitRequest");

        // --- CommonGrp ---
        w.writeStartElement("CommonGrp");
        writeElement(w, "PymtType", "Debit");
        writeElement(w, "TxnType", "Authorization");
        writeElement(w, "LocalDateTime", localDateTimeNow());
        writeElement(w, "TrnmsnDateTime", utcDateTimeNow());
        writeElement(w, "STAN", stan);
        writeElement(w, "RefNum", refNum);
        writeElement(w, "OrderNum", orderNum);
        writeElement(w, "TPPID", TPP_ID);
        writeElement(w, "TermID", terminalId);
        writeElement(w, "MerchID", merchantId);
        writeElement(w, "MerchCatCode", merchantCatCode);
        writeElement(w, "POSEntryMode", "071");    // Contactless EMV
        writeElement(w, "POSCondCode", "00");
        writeElement(w, "TermCatCode", "09");
        writeElement(w, "TermEntryCapablt", "04");
        writeElement(w, "TxnAmt", formatAmount(amountInCents));
        writeElement(w, "TxnCrncy", CURRENCY_USD);
        writeElement(w, "TermLocInd", "0");
        writeElement(w, "CardCaptCap", "1");
        writeElement(w, "GroupID", GROUP_ID);
        w.writeEndElement(); // CommonGrp

        // --- CardGrp ---
        w.writeStartElement("CardGrp");
        writeElement(w, "Track2Data", track2Data);
        // Note: CardType is NOT set for Debit — the network determines routing
        w.writeEndElement(); // CardGrp

        // --- PINGrp (PIN encryption data — MUST come after CardGrp per XSD order) ---
        //
        // PINData is the encrypted PIN block (16 hex characters).
        // The PIN block format (ISO-0 or ISO-4) depends on the encryption scheme.
        //
        // For DUKPT encryption:
        //   - PINData: encrypted PIN block using the derived unique key
        //   - KeySerialNumData: 20-hex-char KSN identifying the DUKPT key
        //
        // For Master Session Encryption:
        //   - PINData: encrypted PIN block using the current session key
        //   - MSKeyID: identifies which master session key was used
        //   - The session key is obtained beforehand via EncryptionKeyRequest
        //
        w.writeStartElement("PINGrp");
        writeElement(w, "PINData", pinBlock);
        if (keySerialNumber != null) {
            writeElement(w, "KeySerialNumData", keySerialNumber);
        }
        if (masterSessionKeyId != null) {
            writeElement(w, "MSKeyID", masterSessionKeyId);
        }
        w.writeEndElement(); // PINGrp

        // --- AddtlAmtGrp ---
        w.writeStartElement("AddtlAmtGrp");
        writeElement(w, "PartAuthrztnApprvlCapablt", "1");
        w.writeEndElement(); // AddtlAmtGrp

        // --- EMVGrp ---
        w.writeStartElement("EMVGrp");
        writeElement(w, "EMVData", emvTlvHexString);
        if (cardSeqNum != null) {
            writeElement(w, "CardSeqNum", String.format("%03d", Integer.parseInt(cardSeqNum)));
        }
        w.writeEndElement(); // EMVGrp

        w.writeEndElement(); // DebitRequest
        w.writeEndElement(); // GMF
        w.writeEndDocument();
        w.flush();

        return sw.toString();
    }

    // =========================================================================
    // 3. COMPLETION — Capture a previously authorized transaction
    // =========================================================================

    /**
     * Builds a Completion request to capture a prior Authorization.
     *
     * Since Sale is NOT selected in our project profile, ALL purchases use the
     * dual-message flow: Authorization first, then Completion to capture.
     *
     * The Completion references the original authorization via OrigAuthGrp.
     * The TxnAmt can differ from the original auth (e.g., tip added for Restaurant).
     *
     * For tipping (Restaurant industry):
     *   1. Send Authorization for the subtotal amount
     *   2. Customer adds tip
     *   3. Send Completion with TxnAmt = subtotal + tip
     *   4. Include AddtlAmtGrp with FirstAuthAmt and TotalAuthAmt
     *
     * LIMITATION: This method does not accept scheme-specific echo fields.
     * In production, the scheme group MUST echo the reference from the Auth response:
     *   - Visa:     VisaGrp.TransID       (from Auth VisaGrp.TransID)
     *   - MC:       MCGrp.BanknetData     (from Auth MCGrp.BanknetData)
     *   - Amex:     AmexGrp.AmExTranID    (from Auth AmexGrp.AmExTranID)
     *   - Discover: DSGrp.DiscNRID        (from Auth DSGrp.DiscNRID)
     * Add these as parameters and write them inside writeSchemeGroup() for
     * non-auth calls. Without them, Fiserv may reject the Completion.
     */
    public static String buildCompletion(
            String merchantId,
            String terminalId,
            String stan,
            String refNum,          // MUST match the original Authorization's RefNum
            String orderNum,
            String merchantCatCode,
            long finalAmountInCents, // Final amount including tip (if any)
            String accountNumber,    // PAN (last time we had track2; for completion, use AcctNum)
            String cardExpiryDate,   // Format: MMYY from original auth
            String cardType,
            // Original Authorization response fields
            String origAuthId,       // AuthID from the Authorization response
            String origResponseDate, // Response date from the Authorization response
            String origLocalDateTime,// LocalDateTime of the original Authorization request
            String origTranDateTime, // TrnmsnDateTime of the original Authorization request
            String origStan,         // STAN of the original Authorization
            String origRespCode,     // Response code from the original Authorization (e.g., "000")
            // Tip / Amount tracking
            long firstAuthAmountCents,  // Original authorized amount (before tip)
            long totalAuthAmountCents   // Total authorized (same as firstAuth if no incremental)
    ) throws Exception {

        StringWriter sw = new StringWriter();
        XMLStreamWriter w = XMLOutputFactory.newInstance().createXMLStreamWriter(sw);

        w.writeStartDocument("UTF-8", "1.0");
        w.writeStartElement("GMF");
        w.writeDefaultNamespace(GMF_NAMESPACE);

        w.writeStartElement("CreditRequest");

        // --- CommonGrp ---
        w.writeStartElement("CommonGrp");
        writeElement(w, "PymtType", "Credit");
        writeElement(w, "TxnType", "Completion");
        writeElement(w, "LocalDateTime", localDateTimeNow());
        writeElement(w, "TrnmsnDateTime", utcDateTimeNow());
        writeElement(w, "STAN", stan);
        writeElement(w, "RefNum", refNum);   // Must match original auth
        writeElement(w, "OrderNum", orderNum);
        writeElement(w, "TPPID", TPP_ID);
        writeElement(w, "TermID", terminalId);
        writeElement(w, "MerchID", merchantId);
        writeElement(w, "MerchCatCode", merchantCatCode);
        writeElement(w, "POSEntryMode", "071");
        writeElement(w, "POSCondCode", "00");
        writeElement(w, "TermCatCode", "09");
        writeElement(w, "TermEntryCapablt", "04");
        writeElement(w, "TxnAmt", formatAmount(finalAmountInCents)); // Final amount (with tip)
        writeElement(w, "TxnCrncy", CURRENCY_USD);
        writeElement(w, "TermLocInd", "0");
        writeElement(w, "CardCaptCap", "1");
        writeElement(w, "GroupID", GROUP_ID);
        w.writeEndElement(); // CommonGrp

        // --- CardGrp (for Completion, use AcctNum + CardExpiryDate, not Track2) ---
        w.writeStartElement("CardGrp");
        writeElement(w, "AcctNum", accountNumber);
        writeElement(w, "CardExpiryDate", cardExpiryDate);
        writeElement(w, "CardType", cardType);
        w.writeEndElement(); // CardGrp

        // --- AddtlAmtGrp #1: FirstAuthAmt (original auth amount, pre-tip) ---
        w.writeStartElement("AddtlAmtGrp");
        writeElement(w, "AddAmt", formatAmount(firstAuthAmountCents));
        writeElement(w, "AddAmtCrncy", CURRENCY_USD);
        writeElement(w, "AddAmtType", "FirstAuthAmt");
        w.writeEndElement(); // AddtlAmtGrp

        // --- AddtlAmtGrp #2: TotalAuthAmt (sum of all authorizations) ---
        w.writeStartElement("AddtlAmtGrp");
        writeElement(w, "AddAmt", formatAmount(totalAuthAmountCents));
        writeElement(w, "AddAmtCrncy", CURRENCY_USD);
        writeElement(w, "AddAmtType", "TotalAuthAmt");
        w.writeEndElement(); // AddtlAmtGrp

        // --- Scheme-specific group ---
        // TODO: Pass scheme-specific echo fields (Visa TransID, MC BanknetData,
        //       Amex AmExTranID, Discover DiscNRID) from Auth response.
        //       Currently writes an empty scheme group — Fiserv may reject this.
        writeSchemeGroup(w, cardType, false);

        // --- OrigAuthGrp (references the original Authorization) ---
        w.writeStartElement("OrigAuthGrp");
        writeElement(w, "OrigAuthID", origAuthId);
        writeElement(w, "OrigResponseDate", origResponseDate);
        writeElement(w, "OrigLocalDateTime", origLocalDateTime);
        writeElement(w, "OrigTranDateTime", origTranDateTime);
        writeElement(w, "OrigSTAN", origStan);
        writeElement(w, "OrigRespCode", origRespCode);
        w.writeEndElement(); // OrigAuthGrp

        w.writeEndElement(); // CreditRequest
        w.writeEndElement(); // GMF
        w.writeEndDocument();
        w.flush();

        return sw.toString();
    }

    // =========================================================================
    // 4. VOID / FULL REVERSAL — Cancel a previous transaction
    // =========================================================================

    /**
     * Builds a Void/Full Reversal request using ReversalRequest.
     *
     * Voids must be submitted within 25 minutes of the original transaction
     * (except Credit Authorization and Credit Completion — see 2026 UMF Changes).
     *
     * ReversalInd values:
     *   "Void"    — Merchant-initiated cancellation
     *   "Timeout" — No response received from host (Timeout Reversal / TOR)
     *   "VoidFr"  — Void for suspected fraud
     *   "Partial" — Partial reversal (amount less than original)
     *
     * For Timeout Reversals: scheme-specific reference fields (Visa TransID,
     * MC BanknetData, Amex AmExTranID, Discover NRID) are EXEMPT per 2026 changes.
     *
     * LIMITATION: Same as buildCompletion() — this method does not accept
     * scheme-specific echo fields (Visa TransID, MC BanknetData, etc.).
     * For Void (not TOR), the scheme group MUST echo the reference from the
     * Authorization response. Add these as parameters for production use.
     */
    public static String buildVoidFullReversal(
            String merchantId,
            String terminalId,
            String stan,
            String refNum,           // MUST match the original transaction's RefNum
            String orderNum,
            String merchantCatCode,
            long originalAmountInCents,
            String reversalInd,       // "Void", "Timeout", "VoidFr"
            String track2Data,        // Track2 from the original transaction
            String cardType,
            String emvTlvHexString,   // EMV data (may be from original transaction, nullable)
            // Original Authorization fields
            String origAuthId,
            String origResponseDate,
            String origLocalDateTime,
            String origTranDateTime,
            String origStan,
            String origRespCode
    ) throws Exception {

        StringWriter sw = new StringWriter();
        XMLStreamWriter w = XMLOutputFactory.newInstance().createXMLStreamWriter(sw);

        w.writeStartDocument("UTF-8", "1.0");
        w.writeStartElement("GMF");
        w.writeDefaultNamespace(GMF_NAMESPACE);

        // Note: Reversals use ReversalRequest, not CreditRequest
        w.writeStartElement("ReversalRequest");

        // --- CommonGrp ---
        w.writeStartElement("CommonGrp");
        writeElement(w, "PymtType", "Credit");
        writeElement(w, "ReversalInd", reversalInd);  // "Void" or "Timeout"
        writeElement(w, "TxnType", "Authorization");   // Original transaction type being reversed
        writeElement(w, "LocalDateTime", localDateTimeNow());
        writeElement(w, "TrnmsnDateTime", utcDateTimeNow());
        writeElement(w, "STAN", stan);
        writeElement(w, "RefNum", refNum);              // Same as original
        writeElement(w, "OrderNum", orderNum);
        writeElement(w, "TPPID", TPP_ID);
        writeElement(w, "TermID", terminalId);
        writeElement(w, "MerchID", merchantId);
        writeElement(w, "MerchCatCode", merchantCatCode);
        writeElement(w, "POSEntryMode", "071");
        writeElement(w, "POSCondCode", "00");
        writeElement(w, "TermCatCode", "09");
        writeElement(w, "TermEntryCapablt", "04");
        writeElement(w, "TxnAmt", formatAmount(originalAmountInCents));
        writeElement(w, "TxnCrncy", CURRENCY_USD);
        writeElement(w, "TermLocInd", "0");
        writeElement(w, "CardCaptCap", "1");
        writeElement(w, "GroupID", GROUP_ID);
        w.writeEndElement(); // CommonGrp

        // --- CardGrp ---
        // Reversals must use AcctNum (PAN), NOT Track2Data — Fiserv rejects Track2Data
        // in ReversalRequest with "RE008 - Field Not Allowed: Track2Data"
        w.writeStartElement("CardGrp");
        String pan = extractPanFromTrack2(track2Data);
        writeElement(w, "AcctNum", pan);
        writeElement(w, "CardType", cardType);
        w.writeEndElement(); // CardGrp

        // --- AddtlAmtGrp (TotalAuthAmt for reversals) ---
        w.writeStartElement("AddtlAmtGrp");
        writeElement(w, "AddAmt", formatAmount(originalAmountInCents));
        writeElement(w, "AddAmtCrncy", CURRENCY_USD);
        writeElement(w, "AddAmtType", "TotalAuthAmt");
        w.writeEndElement(); // AddtlAmtGrp

        // --- EMVGrp (if EMV data available from original transaction) ---
        if (emvTlvHexString != null) {
            w.writeStartElement("EMVGrp");
            writeElement(w, "EMVData", emvTlvHexString);
            w.writeEndElement(); // EMVGrp
        }

        // --- Scheme-specific group ---
        // For Timeout Reversals: scheme reference fields are EXEMPT (2026 change)
        // For Voids: must include scheme reference from original response
        // TODO: Pass scheme-specific echo fields (Visa TransID, MC BanknetData,
        //       Amex AmExTranID, Discover DiscNRID) from Auth response.
        //       Currently writes an empty scheme group — Fiserv may reject Void.
        if (!"Timeout".equals(reversalInd)) {
            writeSchemeGroup(w, cardType, false);
        }

        // --- OrigAuthGrp ---
        // For TOR: only OrigSTAN, OrigLocalDateTime, OrigTranDateTime are available
        // (no response was received, so OrigAuthID/OrigRespCode/OrigResponseDate are null)
        w.writeStartElement("OrigAuthGrp");
        if (origAuthId != null) writeElement(w, "OrigAuthID", origAuthId);
        if (origResponseDate != null) writeElement(w, "OrigResponseDate", origResponseDate);
        writeElement(w, "OrigLocalDateTime", origLocalDateTime);
        writeElement(w, "OrigTranDateTime", origTranDateTime);
        writeElement(w, "OrigSTAN", origStan);
        if (origRespCode != null) writeElement(w, "OrigRespCode", origRespCode);
        w.writeEndElement(); // OrigAuthGrp

        w.writeEndElement(); // ReversalRequest
        w.writeEndElement(); // GMF
        w.writeEndDocument();
        w.flush();

        return sw.toString();
    }

    // =========================================================================
    // 5a. UNREFERENCED REFUND — No link to original transaction
    // =========================================================================

    /**
     * Builds an unreferenced Refund request (not linked to a prior auth).
     *
     * Used when the original transaction reference is unavailable (e.g., refund
     * from a different terminal or after batch settlement). No OrigAuthGrp is sent.
     *
     * Note: For MasterCard/Maestro, the RefundType field is MANDATORY
     * per the 2026 UMF Changes when TxnType is "Refund".
     */
    public static String buildRefund(
            String merchantId,
            String terminalId,
            String stan,
            String refNum,
            String orderNum,
            String merchantCatCode,
            long refundAmountInCents,
            String track2Data,
            String cardType,
            String emvTlvHexString
    ) throws Exception {

        StringWriter sw = new StringWriter();
        XMLStreamWriter w = XMLOutputFactory.newInstance().createXMLStreamWriter(sw);

        w.writeStartDocument("UTF-8", "1.0");
        w.writeStartElement("GMF");
        w.writeDefaultNamespace(GMF_NAMESPACE);

        w.writeStartElement("CreditRequest");

        // --- CommonGrp ---
        w.writeStartElement("CommonGrp");
        writeElement(w, "PymtType", "Credit");
        writeElement(w, "TxnType", "Refund");
        writeElement(w, "LocalDateTime", localDateTimeNow());
        writeElement(w, "TrnmsnDateTime", utcDateTimeNow());
        writeElement(w, "STAN", stan);
        writeElement(w, "RefNum", refNum);
        writeElement(w, "OrderNum", orderNum);
        writeElement(w, "TPPID", TPP_ID);
        writeElement(w, "TermID", terminalId);
        writeElement(w, "MerchID", merchantId);
        writeElement(w, "MerchCatCode", merchantCatCode);
        writeElement(w, "POSEntryMode", "071");
        writeElement(w, "POSCondCode", "00");
        writeElement(w, "TermCatCode", "09");
        writeElement(w, "TermEntryCapablt", "04");
        writeElement(w, "TxnAmt", formatAmount(refundAmountInCents));
        writeElement(w, "TxnCrncy", CURRENCY_USD);
        writeElement(w, "TermLocInd", "0");
        writeElement(w, "CardCaptCap", "1");
        writeElement(w, "GroupID", GROUP_ID);
        // RefundType is mandatory for MasterCard/Maestro per 2026 UMF Changes
        if ("MasterCard".equals(cardType)) {
            writeElement(w, "RefundType", "R");  // Full Refund
        }
        w.writeEndElement(); // CommonGrp

        // --- CardGrp ---
        w.writeStartElement("CardGrp");
        writeElement(w, "Track2Data", track2Data);
        writeElement(w, "CardType", cardType);
        w.writeEndElement(); // CardGrp

        // --- EMVGrp ---
        if (emvTlvHexString != null) {
            w.writeStartElement("EMVGrp");
            writeElement(w, "EMVData", emvTlvHexString);
            w.writeEndElement(); // EMVGrp
        }

        // --- Scheme-specific group ---
        writeSchemeGroup(w, cardType, false);

        w.writeEndElement(); // CreditRequest
        w.writeEndElement(); // GMF
        w.writeEndDocument();
        w.flush();

        return sw.toString();
    }

    // =========================================================================
    // 5b. REFERENCED REFUND — Linked to original authorization
    // =========================================================================

    /**
     * Builds a referenced Refund request linked to a prior authorization.
     *
     * A referenced refund includes OrigAuthGrp to tie it back to the original
     * transaction. This gives better interchange rates and reduces chargeback risk.
     *
     * Key rules:
     *   - RefNum is NEW (not the original auth's RefNum)
     *   - OrigAuthGrp echoes fields from the original auth response
     *   - Scheme-specific reference must be echoed (Visa TransID, MC BanknetData, etc.)
     *   - For MasterCard/Maestro: RefundType is mandatory (2026 UMF Change)
     *
     * LIMITATION: Like buildCompletion(), this method does not accept scheme-specific
     * echo fields. For production, add Visa TransID / MC BanknetData / etc. parameters.
     */
    public static String buildReferencedRefund(
            String merchantId,
            String terminalId,
            String stan,
            String refNum,           // NEW RefNum (not original auth's)
            String orderNum,
            String merchantCatCode,
            long refundAmountInCents,
            String track2Data,
            String cardType,
            String emvTlvHexString,
            // Original Authorization fields (from stored auth response)
            String origAuthId,
            String origResponseDate,
            String origLocalDateTime,
            String origTranDateTime,
            String origStan,
            String origRespCode
    ) throws Exception {

        StringWriter sw = new StringWriter();
        XMLStreamWriter w = XMLOutputFactory.newInstance().createXMLStreamWriter(sw);

        w.writeStartDocument("UTF-8", "1.0");
        w.writeStartElement("GMF");
        w.writeDefaultNamespace(GMF_NAMESPACE);

        w.writeStartElement("CreditRequest");

        // --- CommonGrp ---
        w.writeStartElement("CommonGrp");
        writeElement(w, "PymtType", "Credit");
        writeElement(w, "TxnType", "Refund");
        writeElement(w, "LocalDateTime", localDateTimeNow());
        writeElement(w, "TrnmsnDateTime", utcDateTimeNow());
        writeElement(w, "STAN", stan);
        writeElement(w, "RefNum", refNum);
        writeElement(w, "OrderNum", orderNum);
        writeElement(w, "TPPID", TPP_ID);
        writeElement(w, "TermID", terminalId);
        writeElement(w, "MerchID", merchantId);
        writeElement(w, "MerchCatCode", merchantCatCode);
        writeElement(w, "POSEntryMode", "071");
        writeElement(w, "POSCondCode", "00");
        writeElement(w, "TermCatCode", "09");
        writeElement(w, "TermEntryCapablt", "04");
        writeElement(w, "TxnAmt", formatAmount(refundAmountInCents));
        writeElement(w, "TxnCrncy", CURRENCY_USD);
        writeElement(w, "TermLocInd", "0");
        writeElement(w, "CardCaptCap", "1");
        writeElement(w, "GroupID", GROUP_ID);
        if ("MasterCard".equals(cardType)) {
            writeElement(w, "RefundType", "R");  // Full Refund
        }
        w.writeEndElement(); // CommonGrp

        // --- CardGrp ---
        w.writeStartElement("CardGrp");
        writeElement(w, "Track2Data", track2Data);
        writeElement(w, "CardType", cardType);
        w.writeEndElement(); // CardGrp

        // --- EMVGrp ---
        if (emvTlvHexString != null) {
            w.writeStartElement("EMVGrp");
            writeElement(w, "EMVData", emvTlvHexString);
            w.writeEndElement(); // EMVGrp
        }

        // --- Scheme-specific group ---
        // TODO: Pass scheme-specific echo fields (Visa TransID, MC BanknetData,
        //       Amex AmExTranID, Discover DiscNRID) from original Auth response.
        //       Currently writes a minimal scheme group.
        writeSchemeGroup(w, cardType, false);

        // --- OrigAuthGrp (links refund to original authorization) ---
        w.writeStartElement("OrigAuthGrp");
        writeElement(w, "OrigAuthID", origAuthId);
        writeElement(w, "OrigResponseDate", origResponseDate);
        writeElement(w, "OrigLocalDateTime", origLocalDateTime);
        writeElement(w, "OrigTranDateTime", origTranDateTime);
        writeElement(w, "OrigSTAN", origStan);
        writeElement(w, "OrigRespCode", origRespCode);
        w.writeEndElement(); // OrigAuthGrp

        w.writeEndElement(); // CreditRequest
        w.writeEndElement(); // GMF
        w.writeEndDocument();
        w.flush();

        return sw.toString();
    }

    // =========================================================================
    // 6. DCC AUTHORIZATION — Dynamic Currency Conversion
    // =========================================================================

    /**
     * Builds a Credit Authorization with DCC (Dynamic Currency Conversion).
     *
     * DCC is available for Visa and Mastercard only.
     * NOT supported below contactless CVM limit.
     * For contactless: 1st Generate AC cryptogram uses ORIGINAL currency,
     * not the converted DCC currency (per EMV Guide BP 1200).
     *
     * Flow:
     *  1. Read contactless card
     *  2. Determine DCC eligibility (Global Credit AID, not CAID)
     *  3. Get DCC rate from rate provider
     *  4. Present currency choice to cardholder
     *  5. If cardholder selects DCC: send auth with DCCGrp populated
     */
    public static String buildCreditAuthorizationWithDcc(
            String merchantId,
            String terminalId,
            String stan,
            String refNum,
            String orderNum,
            String merchantCatCode,
            long amountInCents,        // Amount in USD (original currency)
            String track2Data,
            String cardType,
            String emvTlvHexString,
            // DCC fields
            long dccAmountMinorUnits,  // Amount in cardholder's currency
            String dccRate,            // Conversion rate (8 digits, e.g., "00012971")
            String dccCurrencyCode,    // ISO 4217 code of cardholder's currency (e.g., "036" for AUD)
            String dccTimeZone         // Timezone offset (e.g., "+05")
    ) throws Exception {

        StringWriter sw = new StringWriter();
        XMLStreamWriter w = XMLOutputFactory.newInstance().createXMLStreamWriter(sw);

        w.writeStartDocument("UTF-8", "1.0");
        w.writeStartElement("GMF");
        w.writeDefaultNamespace(GMF_NAMESPACE);

        w.writeStartElement("CreditRequest");

        // --- CommonGrp ---
        w.writeStartElement("CommonGrp");
        writeElement(w, "PymtType", "Credit");
        writeElement(w, "TxnType", "Authorization");
        writeElement(w, "LocalDateTime", localDateTimeNow());
        writeElement(w, "TrnmsnDateTime", utcDateTimeNow());
        writeElement(w, "STAN", stan);
        writeElement(w, "RefNum", refNum);
        writeElement(w, "OrderNum", orderNum);
        writeElement(w, "TPPID", TPP_ID);
        writeElement(w, "TermID", terminalId);
        writeElement(w, "MerchID", merchantId);
        writeElement(w, "MerchCatCode", merchantCatCode);
        writeElement(w, "POSEntryMode", "071");
        writeElement(w, "POSCondCode", "00");
        writeElement(w, "TermCatCode", "09");
        writeElement(w, "TermEntryCapablt", "04");
        writeElement(w, "TxnAmt", formatAmount(amountInCents));
        writeElement(w, "TxnCrncy", CURRENCY_USD);
        writeElement(w, "TermLocInd", "0");
        writeElement(w, "CardCaptCap", "1");
        writeElement(w, "GroupID", GROUP_ID);
        w.writeEndElement(); // CommonGrp

        // --- CardGrp ---
        w.writeStartElement("CardGrp");
        writeElement(w, "Track2Data", track2Data);
        writeElement(w, "CardType", cardType);
        w.writeEndElement(); // CardGrp

        // --- AddtlAmtGrp ---
        w.writeStartElement("AddtlAmtGrp");
        writeElement(w, "PartAuthrztnApprvlCapablt", "1");
        w.writeEndElement(); // AddtlAmtGrp

        // --- EMVGrp ---
        w.writeStartElement("EMVGrp");
        writeElement(w, "EMVData", emvTlvHexString);
        w.writeEndElement(); // EMVGrp

        // --- Scheme-specific group ---
        writeSchemeGroup(w, cardType, true);

        // --- DCCGrp (Dynamic Currency Conversion) ---
        w.writeStartElement("DCCGrp");
        writeElement(w, "DCCInd", "1");              // DCC is active
        writeElement(w, "DCCTimeZn", dccTimeZone);
        writeElement(w, "DCCAmt", String.valueOf(dccAmountMinorUnits));
        writeElement(w, "DCCRate", dccRate);
        writeElement(w, "DCCCrncy", dccCurrencyCode);
        w.writeEndElement(); // DCCGrp

        w.writeEndElement(); // CreditRequest
        w.writeEndElement(); // GMF
        w.writeEndDocument();
        w.flush();

        return sw.toString();
    }

    // =========================================================================
    // 7. MASTER SESSION ENCRYPTION KEY REQUEST
    // =========================================================================

    /**
     * Requests a new session encryption key from Fiserv.
     *
     * This must be called before processing PIN Debit transactions
     * when using Master Session Encryption. The session key rotates
     * every 24 hours. The response contains the new session key
     * encrypted under the master key.
     */
    public static String buildEncryptionKeyRequest(
            String merchantId,
            String terminalId,
            String stan,
            String refNum
    ) throws Exception {

        StringWriter sw = new StringWriter();
        XMLStreamWriter w = XMLOutputFactory.newInstance().createXMLStreamWriter(sw);

        w.writeStartDocument("UTF-8", "1.0");
        w.writeStartElement("GMF");
        w.writeDefaultNamespace(GMF_NAMESPACE);

        w.writeStartElement("AdminRequest");

        w.writeStartElement("CommonGrp");
        // Note: PymtType and TxnCrncy are NOT allowed in AdminRequest
        // (Fiserv returns RE008 if present)
        writeElement(w, "TxnType", "EncryptionKeyRequest");
        writeElement(w, "LocalDateTime", localDateTimeNow());
        writeElement(w, "TrnmsnDateTime", utcDateTimeNow());
        writeElement(w, "STAN", stan);
        writeElement(w, "RefNum", refNum);
        writeElement(w, "TPPID", TPP_ID);
        writeElement(w, "TermID", terminalId);
        writeElement(w, "MerchID", merchantId);
        writeElement(w, "GroupID", GROUP_ID);
        // EnhKeyFmt is mandatory for EncryptionKeyRequest
        // Only valid value per XSD: "T" (TR-31 key block format)
        writeElement(w, "EnhKeyFmt", "T");
        w.writeEndElement(); // CommonGrp

        w.writeEndElement(); // AdminRequest
        w.writeEndElement(); // GMF
        w.writeEndDocument();
        w.flush();

        return sw.toString();
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Writes a scheme-specific group (VisaGrp, MCGrp, DSGrp, AmexGrp).
     * Only ONE of these groups may be present — they are mutually exclusive
     * in the XSD (xs:choice).
     */
    private static void writeSchemeGroup(XMLStreamWriter w, String cardType, boolean isAuth)
            throws Exception {
        // For non-auth messages (Completion, Void, Refund), scheme echo fields should be
        // passed via parameters. If no echo fields are available, omit the group entirely —
        // Fiserv rejects empty scheme groups (e.g., <VisaGrp></VisaGrp>) with error 904.
        if (!isAuth) return;

        switch (cardType) {
            case "Visa":
                w.writeStartElement("VisaGrp");
                writeElement(w, "ACI", "Y"); // Cardholder present, card present
                writeElement(w, "VisaBID", "56412"); // Bank Identification — from merchant setup
                writeElement(w, "VisaAUAR", "000000000000");
                writeElement(w, "TaxAmtCapablt", "1");
                w.writeEndElement(); // VisaGrp
                break;

            case "MasterCard":
                w.writeStartElement("MCGrp");
                // For initial auth, no specific fields required
                w.writeEndElement(); // MCGrp
                break;

            case "Discover":
            case "Diners": // Diners processed via Discover network
                w.writeStartElement("DSGrp");
                w.writeEndElement(); // DSGrp
                break;

            case "Amex":
                w.writeStartElement("AmexGrp");
                w.writeEndElement(); // AmexGrp
                break;
        }
    }

    /** Formats an amount in cents to a 12-digit zero-padded string (minor units). */
    private static String formatAmount(long cents) {
        return String.format("%012d", cents);
    }

    /** Returns current local date/time as YYYYMMDDhhmmss. */
    private static String localDateTimeNow() {
        return LocalDateTime.now().format(DT_FORMAT);
    }

    /** Returns current UTC date/time as YYYYMMDDhhmmss. */
    private static String utcDateTimeNow() {
        return LocalDateTime.now(ZoneOffset.UTC).format(DT_FORMAT);
    }

    /** Extracts the PAN from a Track2 equivalent string (PAN=expiry...). */
    private static String extractPanFromTrack2(String track2Data) {
        if (track2Data == null) return null;
        int sep = track2Data.indexOf('=');
        return sep > 0 ? track2Data.substring(0, sep) : track2Data;
    }

    /** Writes a simple XML element with text content. Skips if value is null. */
    private static void writeElement(XMLStreamWriter w, String name, String value) throws Exception {
        if (value != null) {
            w.writeStartElement(name);
            w.writeCharacters(value);
            w.writeEndElement();
        }
    }

    // =========================================================================
    // USAGE EXAMPLES
    // =========================================================================

    public static void main(String[] args) throws Exception {

        // --- Example 1: Contactless Credit Authorization (Visa tap) ---
        System.out.println("=== Credit Authorization (Contactless Visa) ===");
        String creditAuth = buildCreditAuthorizationContactless(
                "RCTST1000119069",     // Retail/QSR MID
                "00000003",             // Dev TID
                "000001",               // STAN
                "1234567890",           // RefNum
                "99887766",             // OrderNum
                "5399",                 // MCC: General Merchandise
                5000,                   // $50.00
                // Track2 equivalent data from contactless kernel
                "4761530001111118=25121011000012345678",
                "Visa",
                // EMV TLV hex string — concatenated tags from contactless read
                // Tags: 9F02(amount), 9F03(other amount), 9F26(cryptogram), 9F27(CID),
                //        9F10(IAD), 82(AIP), 84(AID), 9F36(ATC), 95(TVR), 9A(date),
                //        9C(txn type), 5F2A(currency), 9F1A(country), 9F33(capabilities),
                //        9F34(CVM results), 9F37(unpredictable number), 5F24(expiry)
                "9F02060000000050009F03060000000000009F2608AB12CD34EF567890"
                        + "9F2701809F100706010A03A0000082025C0084"
                        + "07A0000000031010" // AID: Visa
                        + "9F3602001C950500000010009A032604079C01005F2A020840"
                        + "9F1A0208409F3303E0B8C89F34031E03009F3704AABBCCDD"
                        + "5F2403251231",
                "01" // Card Sequence Number
        );
        System.out.println(creditAuth);

        // --- Example 2: Debit Authorization with PIN (contactless) ---
        System.out.println("\n=== Debit Authorization with PIN ===");
        String debitAuth = buildDebitAuthorizationWithPin(
                "RCTST1000119069",
                "00000003",
                "000002",
                "1234567891",
                "99887767",
                "5399",
                2500,                   // $25.00
                "5111111111111111=25121011000012345678",
                // EMV data from contactless kernel
                "9F02060000000025009F03060000000000009F26088D9E04EC413B7A4D"
                        + "82025C009F3602001C9F34031E03009F2701808407A0000000041010"
                        + "9F100706010A03A000009F3303E0B8C89F1A0208409A032604079C0100"
                        + "5F2A0208405F24031512319F3704880399F5950500000010009F41030000019F0902008C",
                "01",
                // PIN Block: 16 hex chars — encrypted with DUKPT or Master Session
                // Format: ISO-0 PIN block XORed with PAN, then encrypted
                "1A2B3C4D5E6F7890",
                // DUKPT Key Serial Number (20 hex chars)
                "FFFF9876543210E00001",
                null  // Not using Master Session for this example
        );
        System.out.println(debitAuth);

        // --- Example 3: Completion (capture after auth, with tip) ---
        System.out.println("\n=== Completion with Tip (Restaurant) ===");
        String completion = buildCompletion(
                "RCTST1000119068",      // Restaurant MID
                "00000003",
                "000003",
                "1234567890",           // Same RefNum as original auth!
                "99887766",
                "5812",                 // MCC: Restaurants
                5500,                   // $55.00 (original $50.00 + $5.00 tip)
                "4761530001111118",      // PAN from original transaction
                "2512",                 // Card expiry MMYY
                "Visa",
                // Fields from the original Authorization response:
                "OK1234",               // AuthID from response
                "260407",               // Response date
                "20260407120000",       // Original request LocalDateTime
                "20260407170000",       // Original request TrnmsnDateTime (UTC)
                "000001",               // Original STAN
                "000",                  // Original response code (approved)
                5000,                   // FirstAuthAmt: $50.00 (pre-tip)
                5000                    // TotalAuthAmt: $50.00 (no incremental auths)
        );
        System.out.println(completion);

        // --- Example 4: Void/Full Reversal ---
        System.out.println("\n=== Void Full Reversal ===");
        String voidReversal = buildVoidFullReversal(
                "RCTST1000119069",
                "00000003",
                "000004",
                "1234567890",           // Same RefNum as original
                "99887766",
                "5399",
                5000,                   // Original amount
                "Void",                 // Reversal type
                "4761530001111118=25121011000012345678",
                "Visa",
                null,                   // EMV data (optional for void)
                "OK1234", "260407", "20260407120000", "20260407170000", "000001", "000"
        );
        System.out.println(voidReversal);

        // --- Example 5: Timeout Reversal (TOR) ---
        System.out.println("\n=== Timeout Reversal (TOR) ===");
        String tor = buildVoidFullReversal(
                "RCTST1000119069",
                "00000003",
                "000005",
                "1234567890",           // Same RefNum as timed-out transaction
                "99887766",
                "5399",
                5000,
                "Timeout",              // Timeout reversal — scheme refs are EXEMPT
                "4761530001111118=25121011000012345678",
                "Visa",
                null,
                null, null,             // No auth response received (timed out)
                "20260407120000", "20260407170000", "000001", null
        );
        System.out.println(tor);

        // --- Example 6: Refund ---
        System.out.println("\n=== Refund ===");
        String refund = buildRefund(
                "RCTST1000119069",
                "00000003",
                "000006",
                "9876543210",
                "11223344",
                "5399",
                5000,                   // Refund $50.00
                "4761530001111118=25121011000012345678",
                "Visa",
                null                    // EMV data optional for refund
        );
        System.out.println(refund);

        // --- Example 7: Encryption Key Request (for PIN Debit) ---
        System.out.println("\n=== Encryption Key Request ===");
        String keyReq = buildEncryptionKeyRequest(
                "RCTST1000119069",
                "00000003",
                "000007",
                "9876543211"
        );
        System.out.println(keyReq);
    }
}
