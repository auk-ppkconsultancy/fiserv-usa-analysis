import java.io.*;
import java.net.*;
import java.time.*;
import java.time.format.*;

/**
 * Replays an official certification test case against one or more
 * (MID, TID) combinations that have already been SRS-registered+activated.
 *
 * Updated 2026-04-13:
 *
 *   The RSO024 sandbox's TestCase matcher validates each incoming transaction
 *   against the 424 rows in TestTransactions_RSO024.csv. If (MCC, PymtType,
 *   TxnType, Amount, POSEntryMode, Encryption, Token, PAN) does not match any
 *   TestCase row, the host returns:
 *
 *       Response: 109 INVALID TERM
 *       Recommendation: TestCase not found. Please check the parameters: ...
 *
 *   Prior versions of this probe cycled arbitrary POSEntryModes ("011", "051",
 *   "071", "910", "912", "000") and used an unrelated PAN/amount, which is why
 *   every request returned 109 INVALID TERM regardless of TPP provisioning.
 *
 *   The fix: send payloads that byte-for-byte match a real TestCase row. This
 *   probe replays TC 200376490010 (Visa Contactless Authorization):
 *
 *       MerchID        RCTST1000120415
 *       MCC            5399          (Retail)
 *       POSEntryMode   911           (contactless)
 *       TermCatCode    09
 *       TermEntryCapablt 01
 *       TxnAmt         000000000314  ($3.14)
 *       PymtType       Credit
 *       TxnType        Authorization
 *       Track2Data     4005520000000921=25121011000012300000
 *       CardType       Visa
 *       ACI=Y, VisaBID=56412, VisaAUAR=000000000000, TaxAmtCapablt=1
 *       PartAuthrztnApprvlCapablt=1
 *
 *   Populate COMBOS with freshly-registered DIDs for these MIDs before running.
 */
public class TidProbe {
    static final String SRS_URL = "https://stagingsupport.datawire.net/nocportal/SRS.do";
    static final String TX_URL  = "https://stg.dw.us.fdcnet.biz/rc";

    // Test-script MIDs (embedded in every <MerchID> in TestTransactions_RSO024.csv).
    // Fill in DIDs after running FullProbe / SrsProbe against each entry.
    // TID is always 00000001 per the test script.
    static final String[][] COMBOS = {
        // { MID, TID, DID } — DIDs below are placeholders; update after fresh SRS.
        {"RCTST1000120415", "00000001", ""},  // Retail, MCC 5399
        {"RCTST1000120416", "00000001", ""},  // Supermarket, MCC 5411
        {"RCTST1000120414", "00000001", ""},  // Restaurant, MCC 5812
    };

    static int clientRefCounter = 100;

    public static void main(String[] args) throws Exception {
        for (String[] combo : COMBOS) {
            String mid = combo[0], tid = combo[1], did = combo[2];
            if (did == null || did.isEmpty()) {
                System.out.printf("SKIP %s TID=%s — no DID set (run FullProbe first)%n", mid, tid);
                continue;
            }
            String auth = "20001" + mid + "|" + tid;

            // Ensure DID is activated (idempotent: "AccessDenied" means already active).
            String actStatus = "?";
            for (int i = 0; i < 3; i++) {
                String activateResp = activate(did, auth);
                actStatus = extract(activateResp, "StatusCode=\"", "\"");
                if ("OK".equals(actStatus) || "AccessDenied".equals(actStatus)) break;
                Thread.sleep(2000);
            }

            // Single, exact replay of TC 200376490010.
            String txnResp = sendAuth(did, auth, mid, tid);
            String umfPayload = extractCdataPayload(txnResp);
            String respCode = extract(umfPayload, "<RespCode>", "</RespCode>");
            String respData = extract(umfPayload, "<AddtlRespData>", "</AddtlRespData>");
            String errData  = extract(umfPayload, "<ErrorData>", "</ErrorData>");
            String authId   = extract(umfPayload, "<AuthID>", "</AuthID>");

            System.out.printf("%-17s TID=%s | Act=%-12s RespCode=%-3s %s%s AuthID=%s%n",
                    mid, tid, actStatus,
                    respCode == null ? "?" : respCode,
                    respData == null ? "" : "\"" + respData + "\" ",
                    errData == null ? "" : "ERR=\"" + errData + "\" ",
                    authId == null ? "-" : authId);
        }
    }

    static String activate(String did, String auth) throws Exception {
        String clientRef = String.format("%07dVRSO024", clientRefCounter++);
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Request Version=\"3\"><ReqClientID>"
            + "<DID>" + did + "</DID><App>RAPIDCONNECTSRS</App>"
            + "<Auth>" + auth + "</Auth>"
            + "<ClientRef>" + clientRef + "</ClientRef>"
            + "</ReqClientID><Activation><ServiceID>160</ServiceID></Activation></Request>";
        return httpPost(SRS_URL, xml);
    }

    static String sendAuth(String did, String auth, String mid, String tid) throws Exception {
        String clientRef = String.format("%07dVRSO024", clientRefCounter++);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String localDt = LocalDateTime.now().format(fmt);
        String utcDt = LocalDateTime.now(ZoneOffset.UTC).format(fmt);
        long stanMicro = System.nanoTime() / 1000L;
        String stan = String.format("%06d", Math.abs(stanMicro) % 1000000);
        String refNum = String.format("%012d", Math.abs(stanMicro) % 1000000000000L);

        // MCC follows MID (per the test script).
        String mcc;
        if ("RCTST1000120414".equals(mid))      mcc = "5812";
        else if ("RCTST1000120416".equals(mid)) mcc = "5411";
        else                                     mcc = "5399";

        // Byte-for-byte replay of TC 200376490010 (Visa Contactless Authorization).
        String umf = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<GMF xmlns=\"com/fiserv/Merchant/gmfV15.04\">"
            + "<CreditRequest>"
            + "<CommonGrp>"
            + "<PymtType>Credit</PymtType>"
            + "<TxnType>Authorization</TxnType>"
            + "<LocalDateTime>" + localDt + "</LocalDateTime>"
            + "<TrnmsnDateTime>" + utcDt + "</TrnmsnDateTime>"
            + "<STAN>" + stan + "</STAN>"
            + "<RefNum>" + refNum + "</RefNum>"
            + "<OrderNum>PROBE001</OrderNum>"
            + "<TPPID>RSO024</TPPID>"
            + "<TermID>" + tid + "</TermID>"
            + "<MerchID>" + mid + "</MerchID>"
            + "<MerchCatCode>" + mcc + "</MerchCatCode>"
            + "<POSEntryMode>911</POSEntryMode>"
            + "<POSCondCode>00</POSCondCode>"
            + "<TermCatCode>09</TermCatCode>"
            + "<TermEntryCapablt>01</TermEntryCapablt>"
            + "<TxnAmt>000000000314</TxnAmt>"
            + "<TxnCrncy>840</TxnCrncy>"
            + "<TermLocInd>0</TermLocInd>"
            + "<CardCaptCap>1</CardCaptCap>"
            + "<GroupID>20001</GroupID>"
            + "</CommonGrp>"
            + "<CardGrp>"
            + "<Track2Data>4005520000000921=25121011000012300000</Track2Data>"
            + "<CardType>Visa</CardType>"
            + "</CardGrp>"
            + "<AddtlAmtGrp>"
            + "<PartAuthrztnApprvlCapablt>1</PartAuthrztnApprvlCapablt>"
            + "</AddtlAmtGrp>"
            + "<VisaGrp>"
            + "<ACI>Y</ACI>"
            + "<VisaBID>56412</VisaBID>"
            + "<VisaAUAR>000000000000</VisaAUAR>"
            + "<TaxAmtCapablt>1</TaxAmtCapablt>"
            + "</VisaGrp>"
            + "</CreditRequest></GMF>";

        String envelope = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Request Version=\"3\" ClientTimeout=\"30\">"
            + "<ReqClientID>"
            + "<DID>" + did + "</DID><App>RAPIDCONNECTVXN</App>"
            + "<Auth>" + auth + "</Auth>"
            + "<ClientRef>" + clientRef + "</ClientRef>"
            + "</ReqClientID>"
            + "<Transaction><ServiceID>160</ServiceID>"
            + "<Payload Encoding=\"cdata\"><![CDATA[" + umf + "]]></Payload>"
            + "</Transaction></Request>";

        return httpPost(TX_URL, envelope);
    }

    static String httpPost(String url, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Content-Type", "text/xml");
        conn.setRequestProperty("Accept", "text/xml, multipart/related");
        conn.setRequestProperty("User-Agent", "Softpay SoftPOS v1.0");
        conn.setRequestProperty("Connection", "Keep-Alive");
        conn.getOutputStream().write(body.getBytes("UTF-8"));
        InputStream is = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "";
        String resp = new String(is.readAllBytes(), "UTF-8");
        conn.disconnect();
        return resp;
    }

    static String extract(String s, String open, String close) {
        if (s == null) return null;
        int a = s.indexOf(open);
        if (a < 0) return null;
        a += open.length();
        int b = s.indexOf(close, a);
        if (b < 0) return null;
        return s.substring(a, b).trim();
    }

    static String extractCdataPayload(String envelope) {
        int a = envelope.indexOf("<![CDATA[");
        if (a < 0) return "";
        a += 9;
        int b = envelope.indexOf("]]>", a);
        return b < 0 ? "" : envelope.substring(a, b);
    }
}
