import java.io.*;
import java.net.*;
import java.time.*;
import java.time.format.*;

/**
 * Fresh register + activate + auth probe for a single MID/TID.
 * Used to verify end-to-end SRS flow (including that activation actually works).
 */
public class FullProbe {
    static final String SRS_URL = "https://stagingsupport.datawire.net/nocportal/SRS.do";
    static final String TX_URL  = "https://stg.dw.us.fdcnet.biz/rc";

    // Combos to test: exclude 119068/001 (already activated from main runner)
    static final String[][] TARGETS = {
        {"RCTST1000119068", "00000002"},
        {"RCTST1000119070", "00000002"},
        {"RCTST0000000065", "00000001"},
    };

    static int clientRefCounter = 500;

    public static void main(String[] args) throws Exception {
        for (String[] t : TARGETS) {
            String mid = t[0], tid = t[1];
            String auth = "20001" + mid + "|" + tid;
            System.out.println("========== " + mid + " TID=" + tid + " ==========");

            // Fresh register
            String regResp = register(auth);
            System.out.println("REGISTER response:");
            System.out.println(regResp);
            String regStatus = extract(regResp, "StatusCode=\"", "\"");
            String did = extract(regResp, "<DID>", "</DID>");
            if (did == null || did.isEmpty() || !"OK".equals(regStatus)) {
                System.out.println("SKIP: Registration " + regStatus);
                System.out.println();
                continue;
            }
            System.out.println("DID = " + did);

            // Short wait, then activate
            Thread.sleep(3000);
            String actResp = activate(did, auth);
            System.out.println("ACTIVATE response:");
            System.out.println(actResp);
            String actStatus = extract(actResp, "StatusCode=\"", "\"");
            if (!"OK".equals(actStatus)) {
                System.out.println("SKIP: Activation " + actStatus);
                System.out.println();
                continue;
            }

            // Send auth with Track2Data + POSEntryMode=901 (contactless chip)
            Thread.sleep(2000);
            String txnResp = sendAuth(did, auth, mid, tid);
            String umfPayload = extractCdataPayload(txnResp);
            String respCode = extract(umfPayload, "<RespCode>", "</RespCode>");
            String respData = extract(umfPayload, "<AddtlRespData>", "</AddtlRespData>");
            String errData  = extract(umfPayload, "<ErrorData>", "</ErrorData>");
            String authId   = extract(umfPayload, "<AuthID>", "</AuthID>");

            System.out.printf("AUTH: RespCode=%s %s%sAuthID=%s%n",
                    respCode, respData == null ? "" : "\"" + respData + "\" ",
                    errData == null ? "" : "ERR=\"" + errData + "\" ",
                    authId == null ? "-" : authId);
            System.out.println();
        }
    }

    static String register(String auth) throws Exception {
        String clientRef = String.format("%07dVRSO024", clientRefCounter++);
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Request Version=\"3\"><ReqClientID>"
            + "<DID></DID><App>RAPIDCONNECTSRS</App>"
            + "<Auth>" + auth + "</Auth>"
            + "<ClientRef>" + clientRef + "</ClientRef>"
            + "</ReqClientID><Registration><ServiceID>160</ServiceID></Registration></Request>";
        return httpPost(SRS_URL, xml);
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
            + "<POSEntryMode>901</POSEntryMode>"
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
