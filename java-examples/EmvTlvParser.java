package com.softpay.fiserv;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EMV TLV (Tag-Length-Value) parser and builder for Fiserv Rapid Connect.
 *
 * The EMVGrp.EMVData field in the UMF XML contains concatenated TLV-encoded
 * EMV tags as a single hex string. This class helps parse and build that string.
 *
 * Example EMVData from a Fiserv test transaction:
 * "01379F03060000000000009F26088D9E04EC413B7A4D82025C00..."
 *
 * The format is: [LengthPrefix][Tag1][Len1][Val1][Tag2][Len2][Val2]...
 * The first 4 hex characters ("0137") appear to be a total length prefix.
 *
 * Standard TLV encoding:
 * - Tag: 1 or 2 bytes (2 bytes if first byte bits 1-5 are all 1s, e.g., 9F__)
 * - Length: 1 byte if < 0x80; 2+ bytes if >= 0x80 (BER-TLV encoding)
 * - Value: [Length] bytes
 *
 * Key EMV Tags for Fiserv Contactless:
 *
 * | Tag  | Name                            | Len   | Source   |
 * |------|---------------------------------|-------|----------|
 * | 82   | Application Interchange Profile | 2     | Card     |
 * | 84   | Dedicated File Name (AID)       | 5-16  | Card     |
 * | 95   | Terminal Verification Results    | 5     | Terminal |
 * | 9A   | Transaction Date (YYMMDD)       | 3     | Terminal |
 * | 9C   | Transaction Type                | 1     | Terminal |
 * | 5F24 | Application Expiration Date     | 3     | Card     |
 * | 5F2A | Transaction Currency Code       | 2     | Terminal |
 * | 9F02 | Amount, Authorised              | 6     | Terminal |
 * | 9F03 | Amount, Other                   | 6     | Terminal |
 * | 9F06 | Application Identifier (AID)    | 5-16  | Terminal |
 * | 9F09 | Application Version Number      | 2     | Terminal |
 * | 9F10 | Issuer Application Data         | var   | Card     |
 * | 9F1A | Terminal Country Code           | 2     | Terminal |
 * | 9F1E | IFD Serial Number               | 8     | Terminal |
 * | 9F26 | Application Cryptogram          | 8     | Card     |
 * | 9F27 | Cryptogram Information Data     | 1     | Card     |
 * | 9F33 | Terminal Capabilities           | 3     | Terminal |
 * | 9F34 | CVM Results                     | 3     | Terminal |
 * | 9F35 | Terminal Type                   | 1     | Terminal |
 * | 9F36 | Application Transaction Counter | 2     | Card     |
 * | 9F37 | Unpredictable Number            | 4     | Terminal |
 * | 9F41 | Transaction Sequence Counter    | 2-4   | Terminal |
 * | 9F6E | Form Factor Indicator           | 4     | Card     |
 */
public class EmvTlvParser {

    /**
     * Parses a concatenated TLV hex string into a map of Tag → Value (hex).
     *
     * Handles both 1-byte tags (e.g., 82, 95) and 2-byte tags (e.g., 9F26, 5F2A).
     * Handles 1-byte and 2-byte BER-TLV length encoding.
     *
     * @param tlvHex the full EMVData hex string (may include a length prefix)
     * @return ordered map of tag (hex uppercase) → value (hex uppercase)
     */
    public static Map<String, String> parseTlv(String tlvHex) {
        Map<String, String> tags = new LinkedHashMap<>();
        String hex = tlvHex.toUpperCase();
        int pos = 0;

        // Some Fiserv EMVData strings start with a 4-char total length prefix.
        // Detect this by checking if the first 4 chars represent a valid length
        // that matches the remaining data length.
        if (hex.length() >= 4) {
            int declaredLen = Integer.parseInt(hex.substring(0, 4), 16);
            if (declaredLen * 2 == hex.length() - 4) {
                pos = 4; // Skip the length prefix
            }
        }

        while (pos < hex.length() - 3) {
            // Parse tag (1 or 2 bytes)
            int tagByte = Integer.parseInt(hex.substring(pos, pos + 2), 16);
            String tag;
            if ((tagByte & 0x1F) == 0x1F) {
                // Two-byte tag
                tag = hex.substring(pos, pos + 4);
                pos += 4;
            } else {
                // One-byte tag
                tag = hex.substring(pos, pos + 2);
                pos += 2;
            }

            if (pos + 2 > hex.length()) break;

            // Parse length (BER-TLV)
            int lenByte = Integer.parseInt(hex.substring(pos, pos + 2), 16);
            pos += 2;
            int length;
            if ((lenByte & 0x80) != 0) {
                int numLenBytes = lenByte & 0x7F;
                length = Integer.parseInt(hex.substring(pos, pos + numLenBytes * 2), 16);
                pos += numLenBytes * 2;
            } else {
                length = lenByte;
            }

            // Parse value
            int valueEnd = pos + length * 2;
            if (valueEnd > hex.length()) break;
            String value = hex.substring(pos, valueEnd);
            pos = valueEnd;

            tags.put(tag, value);
        }

        return tags;
    }

    /**
     * Builds a concatenated TLV hex string from a map of Tag → Value (hex).
     *
     * This produces the value for the EMVGrp.EMVData XML element.
     * Tags must be added in the order expected by the kernel/processor.
     *
     * @param tags ordered map of tag (hex) → value (hex)
     * @param includeLengthPrefix if true, prepends a 4-char hex length prefix
     * @return the concatenated TLV hex string
     */
    public static String buildTlv(Map<String, String> tags, boolean includeLengthPrefix) {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, String> entry : tags.entrySet()) {
            String tag = entry.getKey().toUpperCase();
            String value = entry.getValue().toUpperCase();
            int valueByteLen = value.length() / 2;

            sb.append(tag);

            // BER-TLV length encoding
            if (valueByteLen < 0x80) {
                sb.append(String.format("%02X", valueByteLen));
            } else if (valueByteLen <= 0xFF) {
                sb.append("81");
                sb.append(String.format("%02X", valueByteLen));
            } else {
                sb.append("82");
                sb.append(String.format("%04X", valueByteLen));
            }

            sb.append(value);
        }

        if (includeLengthPrefix) {
            int totalBytes = sb.length() / 2;
            return String.format("%04X", totalBytes) + sb;
        }

        return sb.toString();
    }

    /**
     * Builds a typical EMVData hex string for a Softpay contactless transaction.
     *
     * This helper creates the TLV data from individual tag values as they would
     * come from the contactless EMV kernel after a successful tap.
     */
    public static String buildContactlessEmvData(
            long amountAuthorizedCents,  // Tag 9F02
            long amountOtherCents,       // Tag 9F03 (usually 0)
            byte[] applicationCryptogram, // Tag 9F26 (8 bytes from kernel)
            byte cryptogramInfoData,      // Tag 9F27 (1 byte: 80=ARQC, 40=TC, 00=AAC)
            byte[] issuerAppData,         // Tag 9F10 (variable, from card)
            byte[] appInterchangeProfile, // Tag 82 (2 bytes from card)
            byte[] aidBytes,              // Tag 84 (5-16 bytes, e.g., A0000000031010 for Visa)
            byte[] atc,                   // Tag 9F36 (2 bytes, Application Transaction Counter)
            byte[] tvr,                   // Tag 95 (5 bytes, Terminal Verification Results)
            byte[] txnDate,               // Tag 9A (3 bytes, YYMMDD)
            byte txnType,                 // Tag 9C (1 byte, 00=purchase)
            byte[] currencyCode,          // Tag 5F2A (2 bytes, e.g., 0840 for USD)
            byte[] countryCode,           // Tag 9F1A (2 bytes, e.g., 0840 for US)
            byte[] termCapabilities,      // Tag 9F33 (3 bytes)
            byte[] cvmResults,            // Tag 9F34 (3 bytes)
            byte termType,                // Tag 9F35 (1 byte)
            byte[] unpredictableNumber,   // Tag 9F37 (4 bytes)
            byte[] appExpiryDate,         // Tag 5F24 (3 bytes, YYMMDD)
            byte[] appVersionNum,         // Tag 9F09 (2 bytes)
            byte[] txnSeqCounter,         // Tag 9F41 (2-4 bytes)
            byte[] formFactorIndicator    // Tag 9F6E (4 bytes, mandatory for contactless)
    ) {
        Map<String, String> tags = new LinkedHashMap<>();

        tags.put("9F02", String.format("%012X", amountAuthorizedCents));
        tags.put("9F03", String.format("%012X", amountOtherCents));
        tags.put("9F26", bytesToHex(applicationCryptogram));
        tags.put("9F27", String.format("%02X", cryptogramInfoData));
        tags.put("9F10", bytesToHex(issuerAppData));
        tags.put("82", bytesToHex(appInterchangeProfile));
        tags.put("84", bytesToHex(aidBytes));
        tags.put("9F36", bytesToHex(atc));
        tags.put("95", bytesToHex(tvr));
        tags.put("9A", bytesToHex(txnDate));
        tags.put("9C", String.format("%02X", txnType));
        tags.put("5F2A", bytesToHex(currencyCode));
        tags.put("9F1A", bytesToHex(countryCode));
        tags.put("9F33", bytesToHex(termCapabilities));
        tags.put("9F34", bytesToHex(cvmResults));
        tags.put("9F35", String.format("%02X", termType));
        tags.put("9F37", bytesToHex(unpredictableNumber));
        tags.put("5F24", bytesToHex(appExpiryDate));
        if (appVersionNum != null) {
            tags.put("9F09", bytesToHex(appVersionNum));
        }
        if (txnSeqCounter != null) {
            tags.put("9F41", bytesToHex(txnSeqCounter));
        }
        if (formFactorIndicator != null) {
            tags.put("9F6E", bytesToHex(formFactorIndicator));
        }

        return buildTlv(tags, true);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    // =========================================================================
    // USAGE EXAMPLE
    // =========================================================================

    public static void main(String[] args) {
        // Parse an EMVData string from a Fiserv test transaction
        String emvDataFromFiserv =
                "01379F03060000000000009F26088D9E04EC413B7A4D82025C00"
                        + "9F3602001C9F34031E03009F02060000000001009F27018084"
                        + "07A00000000310109F100706010A03A000009F0902008C9F33"
                        + "03E0B8C89F1A0208409F1E08353130303138393639A031109"
                        + "089F350122950500000010005F2A0208405F24031512319F41"
                        + "030000019C01009F3704880399F5";

        System.out.println("=== Parsed EMV Tags ===");
        Map<String, String> parsed = parseTlv(emvDataFromFiserv);
        for (Map.Entry<String, String> entry : parsed.entrySet()) {
            String tagName = TAG_NAMES.getOrDefault(entry.getKey(), "Unknown");
            System.out.printf("  Tag %s (%s): %s%n", entry.getKey(), tagName, entry.getValue());
        }

        // Rebuild it
        System.out.println("\n=== Rebuilt TLV ===");
        String rebuilt = buildTlv(parsed, true);
        System.out.println(rebuilt);
    }

    /** Human-readable tag names for debugging. */
    private static final Map<String, String> TAG_NAMES = Map.ofEntries(
            Map.entry("82", "App Interchange Profile"),
            Map.entry("84", "Dedicated File Name (AID)"),
            Map.entry("95", "Terminal Verification Results"),
            Map.entry("9A", "Transaction Date"),
            Map.entry("9C", "Transaction Type"),
            Map.entry("5F24", "App Expiration Date"),
            Map.entry("5F2A", "Transaction Currency Code"),
            Map.entry("9F02", "Amount Authorised"),
            Map.entry("9F03", "Amount Other"),
            Map.entry("9F06", "Application Identifier (AID)"),
            Map.entry("9F09", "App Version Number"),
            Map.entry("9F10", "Issuer Application Data"),
            Map.entry("9F1A", "Terminal Country Code"),
            Map.entry("9F1E", "IFD Serial Number"),
            Map.entry("9F26", "Application Cryptogram"),
            Map.entry("9F27", "Cryptogram Info Data"),
            Map.entry("9F33", "Terminal Capabilities"),
            Map.entry("9F34", "CVM Results"),
            Map.entry("9F35", "Terminal Type"),
            Map.entry("9F36", "App Transaction Counter"),
            Map.entry("9F37", "Unpredictable Number"),
            Map.entry("9F41", "Transaction Sequence Counter"),
            Map.entry("9F6E", "Form Factor Indicator")
    );
}
