package com.softpay.fiserv;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parser for Fiserv Rapid Connect UMF XML responses.
 *
 * Extracts the fields needed for:
 * - Determining approval/decline (RespCode)
 * - Building subsequent transactions (Completion, Void) via OrigAuthGrp
 * - Scheme-specific reference data (Visa TransID, MC BanknetData, etc.)
 * - EMV data for 2nd Generate AC and receipt printing
 *
 * Response structure:
 *   <GMF>
 *     <CreditResponse> | <DebitResponse> | <RejectResponse> | ...
 *       <CommonGrp>...</CommonGrp>
 *       <CardGrp>...</CardGrp>
 *       <RespGrp>...</RespGrp>        ← approval/decline info
 *       <EMVGrp>...</EMVGrp>          ← issuer EMV response data
 *       <VisaGrp>...</VisaGrp>        ← or MCGrp/DSGrp/AmexGrp
 *     </CreditResponse>
 *   </GMF>
 */
public class FiservResponseParser {

    /**
     * Fields extracted from an authorization response that are needed
     * for building Completion, Void, and Reversal requests.
     */
    public static class AuthorizationResponse {
        // CommonGrp echoed fields
        public String pymtType;
        public String txnType;
        public String localDateTime;
        public String trnmsnDateTime;
        public String stan;
        public String refNum;
        public String termId;
        public String merchId;
        public String txnAmt;

        // RespGrp fields
        public String respCode;         // "000" = approved, "002" = partial, "05x" = declined
        public String authId;           // Authorization ID — needed for Completion/Void
        public String responseDate;     // Settlement/response date
        public String addlRespData;     // Additional response data
        public String authNetId;        // Authorizing network ID
        public String authNetName;      // Authorizing network name
        public String signInd;          // Signature required indicator
        public String errorData;        // Error details (for RejectResponse)

        // Visa-specific (VisaGrp)
        public String visaTransId;      // Must echo in Completion/Void (exempt for TOR)
        public String aci;
        public String cardLevelResult;
        public String sourceReasonCode;

        // MasterCard-specific (MCGrp)
        public String banknetData;      // Must echo in Completion/Void (exempt for TOR)

        // Discover-specific (DSGrp)
        public String discoverNrid;     // Must echo in Completion/Void (exempt for TOR)

        // Amex-specific (AmexGrp)
        public String amexTranId;       // Must echo in Completion/Void (exempt for TOR)

        // EMVGrp response
        public String emvRespData;      // Issuer response EMV data (Tag 91, scripts, etc.)

        // CardGrp response
        public String cardType;

        public boolean isApproved() {
            return "000".equals(respCode);
        }

        public boolean isPartialApproval() {
            return "002".equals(respCode);
        }

        public boolean isDeclined() {
            return respCode != null && !isApproved() && !isPartialApproval();
        }

        @Override
        public String toString() {
            return String.format(
                    "AuthResponse{respCode=%s, authId=%s, refNum=%s, stan=%s, cardType=%s, "
                            + "visaTransId=%s, banknetData=%s, discoverNrid=%s, amexTranId=%s}",
                    respCode, authId, refNum, stan, cardType,
                    visaTransId, banknetData, discoverNrid, amexTranId
            );
        }
    }

    /**
     * Parses a Fiserv UMF XML response into an AuthorizationResponse object.
     *
     * Works for CreditResponse, DebitResponse, and RejectResponse.
     */
    public static AuthorizationResponse parseResponse(String xml) throws Exception {
        AuthorizationResponse resp = new AuthorizationResponse();
        XMLStreamReader reader = XMLInputFactory.newInstance()
                .createXMLStreamReader(new StringReader(xml));

        String currentGroup = null;
        String currentElement = null;

        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = reader.getLocalName();

                // Track which group we're in
                if (name.endsWith("Grp") || name.equals("RejectResponse")) {
                    currentGroup = name;
                }
                currentElement = name;

            } else if (event == XMLStreamConstants.CHARACTERS && currentElement != null) {
                String text = reader.getText().trim();
                if (text.isEmpty()) continue;

                // CommonGrp fields
                if ("CommonGrp".equals(currentGroup)) {
                    switch (currentElement) {
                        case "PymtType": resp.pymtType = text; break;
                        case "TxnType": resp.txnType = text; break;
                        case "LocalDateTime": resp.localDateTime = text; break;
                        case "TrnmsnDateTime": resp.trnmsnDateTime = text; break;
                        case "STAN": resp.stan = text; break;
                        case "RefNum": resp.refNum = text; break;
                        case "TermID": resp.termId = text; break;
                        case "MerchID": resp.merchId = text; break;
                        case "TxnAmt": resp.txnAmt = text; break;
                    }
                }

                // RespGrp fields
                if ("RespGrp".equals(currentGroup)) {
                    switch (currentElement) {
                        case "RespCode": resp.respCode = text; break;
                        case "AuthID": resp.authId = text; break;
                        case "ResponseDate": resp.responseDate = text; break;
                        case "AddlRespData": resp.addlRespData = text; break;
                        case "AuthNetID": resp.authNetId = text; break;
                        case "AuthNetName": resp.authNetName = text; break;
                        case "SignInd": resp.signInd = text; break;
                        case "ErrorData": resp.errorData = text; break;
                    }
                }

                // CardGrp
                if ("CardGrp".equals(currentGroup)) {
                    if ("CardType".equals(currentElement)) resp.cardType = text;
                }

                // VisaGrp
                if ("VisaGrp".equals(currentGroup)) {
                    switch (currentElement) {
                        case "TransID": resp.visaTransId = text; break;
                        case "ACI": resp.aci = text; break;
                        case "CardLevelResult": resp.cardLevelResult = text; break;
                        case "SourceReasonCode": resp.sourceReasonCode = text; break;
                    }
                }

                // MCGrp
                if ("MCGrp".equals(currentGroup)) {
                    if ("BanknetData".equals(currentElement)) resp.banknetData = text;
                }

                // DSGrp
                if ("DSGrp".equals(currentGroup)) {
                    if ("DiscNRID".equals(currentElement)) resp.discoverNrid = text;
                }

                // AmexGrp
                if ("AmexGrp".equals(currentGroup)) {
                    if ("AmExTranID".equals(currentElement)) resp.amexTranId = text;
                }

                // EMVGrp
                if ("EMVGrp".equals(currentGroup)) {
                    if ("EMVData".equals(currentElement)) resp.emvRespData = text;
                }

                // RejectResponse error
                if ("RejectResponse".equals(currentGroup)) {
                    if ("ErrorData".equals(currentElement)) resp.errorData = text;
                }

            } else if (event == XMLStreamConstants.END_ELEMENT) {
                String name = reader.getLocalName();
                if (name.endsWith("Grp") || name.equals("RejectResponse")) {
                    currentGroup = null;
                }
                currentElement = null;
            }
        }

        reader.close();
        return resp;
    }

    // =========================================================================
    // USAGE EXAMPLE: Full Transaction Flow
    // =========================================================================

    /**
     * Demonstrates the full dual-message flow:
     *  1. Send Authorization
     *  2. Parse response, extract OrigAuthGrp fields
     *  3. Build Completion using response fields
     */
    public static void main(String[] args) throws Exception {
        // Simulated Authorization response from Fiserv
        String authResponseXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <GMF xmlns="com/fiserv/Merchant/gmfV15.04">
                  <CreditResponse>
                    <CommonGrp>
                      <PymtType>Credit</PymtType>
                      <TxnType>Authorization</TxnType>
                      <LocalDateTime>20260407120000</LocalDateTime>
                      <TrnmsnDateTime>20260407170000</TrnmsnDateTime>
                      <STAN>000001</STAN>
                      <RefNum>1234567890</RefNum>
                      <TermID>00000003</TermID>
                      <MerchID>RCTST1000119069</MerchID>
                      <TxnAmt>000000005000</TxnAmt>
                    </CommonGrp>
                    <CardGrp>
                      <CardType>Visa</CardType>
                    </CardGrp>
                    <RespGrp>
                      <RespCode>000</RespCode>
                      <AuthID>OK1234</AuthID>
                      <ResponseDate>260407</ResponseDate>
                      <AuthNetID>VISA</AuthNetID>
                    </RespGrp>
                    <VisaGrp>
                      <TransID>0123456789012345</TransID>
                      <ACI>Y</ACI>
                      <CardLevelResult>A</CardLevelResult>
                    </VisaGrp>
                  </CreditResponse>
                </GMF>
                """;

        // Step 1: Parse the Authorization response
        AuthorizationResponse authResp = parseResponse(authResponseXml);
        System.out.println("=== Authorization Response ===");
        System.out.println(authResp);
        System.out.println("Approved: " + authResp.isApproved());

        // Step 2: If approved, build a Completion to capture the transaction
        if (authResp.isApproved()) {
            System.out.println("\n=== Building Completion ===");

            // Use the response fields to populate OrigAuthGrp
            String completion = FiservUmfMessageBuilder.buildCompletion(
                    authResp.merchId,
                    authResp.termId,
                    "000010",                   // New STAN for the Completion
                    authResp.refNum,            // Same RefNum as original
                    "99887766",
                    "5399",
                    5000,                       // Same amount (no tip in retail)
                    "4761530001111118",          // PAN
                    "2512",                     // Expiry
                    authResp.cardType,
                    // OrigAuthGrp fields from the response:
                    authResp.authId,            // "OK1234"
                    authResp.responseDate,      // "260407"
                    authResp.localDateTime,     // From original request
                    authResp.trnmsnDateTime,    // From original request
                    authResp.stan,              // Original STAN
                    authResp.respCode,          // "000"
                    5000,                       // FirstAuthAmt
                    5000                        // TotalAuthAmt
            );

            System.out.println(completion);
        }
    }
}
