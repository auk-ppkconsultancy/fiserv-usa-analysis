import java.io.*;
import java.net.*;
import java.time.*;
import java.time.format.*;

public class TidProbe {
    static final String SRS_URL = "https://stagingsupport.datawire.net/nocportal/SRS.do";
    static final String TX_URL  = "https://stg.dw.us.fdcnet.biz/rc";

    // MID/TID -> DID mapping from SRS probe
    static final String[][] COMBOS = {
        {"RCTST1000119068", "00000001", "00068124106860962610"},
        {"RCTST1000119068", "00000002", "00068124124872924252"},
        {"RCTST1000119068", "00000003", "00068124171727158950"},
        {"RCTST1000119069", "00000002", "00068124130331624449"},
        {"RCTST1000119070", "00000002", "00068124198885244866"},
        {"RCTST1000119070", "00000003", "00068124218233403916"},
        {"RCTST0000000065", "00000001", "00064257248851026901"},
        {"RCTST0000000065", "00000002", "00064257251724609371"},
        {"RCTST0000000065", "00000003", "00013116138286995719"},
    };

    // POSEntryMode candidates (per schema: first 2 chars from enum list, 3rd char 0-6)
    static final String[] POS_ENTRY_MODES = {"011", "901", "051", "071", "910", "912", "000"};

    static int clientRefCounter = 100;

    public static void main(String[] args) throws Exception {
        for (String[] combo : COMBOS) {
            String mid = combo[0], tid = combo[1], did = combo[2];
            String auth = "20001" + mid + "|" + tid;

            // Step 1: Activate DID (retry on transient NotFound / Retry)
            String actStatus = "?";
            for (int i = 0; i < 3; i++) {
                String activateResp = activate(did, auth);
                actStatus = extract(activateResp, "StatusCode=\"", "\"");
                if ("OK".equals(actStatus) || "AccessDenied".equals(actStatus)) break;
                Thread.sleep(2000);
            }

            // Only the first (already-activated) combo actually reaches the auth host.
            // For that one, cycle POSEntryModes to discover TPP-supported values.
            String[] modesToTry = (combo == COMBOS[0]) ? POS_ENTRY_MODES : new String[]{"011"};

            for (String pem : modesToTry) {
                String txnResp = sendAuth(did, auth, mid, tid, pem);
                String umfPayload = extractCdataPayload(txnResp);
                String respCode = extract(umfPayload, "<RespCode>", "</RespCode>");
                String respData = extract(umfPayload, "<AddtlRespData>", "</AddtlRespData>");
                String errData  = extract(umfPayload, "<ErrorData>", "</ErrorData>");
                String authId   = extract(umfPayload, "<AuthID>", "</AuthID>");

                System.out.printf("%-17s TID=%s PEM=%s | Act=%-12s RespCode=%-3s %s%s AuthID=%s%n",
                        mid, tid, pem, actStatus,
                        respCode == null ? "?" : respCode,
                        respData == null ? "" : "\"" + respData + "\" ",
                        errData == null ? "" : "ERR=\"" + errData + "\" ",
                        authId == null ? "-" : authId);
            }
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

    static String sendAuth(String did, String auth, String mid, String tid, String posEntryMode) throws Exception {
        String clientRef = String.format("%07dVRSO024", clientRefCounter++);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String localDt = LocalDateTime.now().format(fmt);
        String utcDt = LocalDateTime.now(ZoneOffset.UTC).format(fmt);
        long stanMicro = System.nanoTime() / 1000L;
        String stan = String.format("%06d", Math.abs(stanMicro) % 1000000);
        String refNum = String.format("%012d", Math.abs(stanMicro) % 1000000000000L);

        // Simple credit auth, Visa test card, $1.50
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
            + "<MerchCatCode>5812</MerchCatCode>"
            + "<POSEntryMode>" + posEntryMode + "</POSEntryMode>"
            + "<POSCondCode>00</POSCondCode>"
            + "<TermCatCode>09</TermCatCode>"
            + "<TermEntryCapablt>04</TermEntryCapablt>"
            + "<TxnAmt>000000000150</TxnAmt>"
            + "<TxnCrncy>840</TxnCrncy>"
            + "<TermLocInd>0</TermLocInd>"
            + "<CardCaptCap>1</CardCaptCap>"
            + "<GroupID>20001</GroupID>"
            + "</CommonGrp>"
            + "<CardGrp>"
            + "<Track2Data>4017779995555556=30041200000000001</Track2Data>"
            + "<CardType>Visa</CardType>"
            + "</CardGrp>"
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
