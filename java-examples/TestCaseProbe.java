import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;

/**
 * Reads canonical test cases directly out of TestTransactions_RSO024.csv and
 * replays them against the Fiserv staging sandbox, so the UMF XML is a
 * byte-exact copy of the test-script row (no hand-transcription drift).
 *
 * For each selected TestCase ID:
 *   1. Pull the raw XML for that row from the CSV.
 *   2. Swap only the fields that must vary per run:
 *        STAN, RefNum, OrderNum, LocalDateTime, TrnmsnDateTime.
 *      Every other field (MerchID, TermID, POSEntryMode, TermCatCode, TxnAmt,
 *      Track2Data, CardType, VisaGrp/MCGrp/AmexGrp contents, AddtlAmtGrp, etc.)
 *      is preserved exactly as the CSV specifies.
 *   3. Route the request to the DID we registered+activated for the row's MerchID.
 *   4. POST to the staging URL, dump the full response.
 *
 * DIDs below are from the 2026-04-13 FullProbe run. Refresh by re-running
 * FullProbe against a MID whose SRS ticket is reset.
 */
public class TestCaseProbe {
    static final String TX_URL = "https://stg.dw.us.fdcnet.biz/rc";
    static final String CSV_PATH =
            "/Users/uygar/codebase/softpay/fiserv-usa-analysis/TestTransactions_RSO024.csv";

    // MerchID -> DID (already registered and activated against staging SRS)
    static final Map<String, String> DID_BY_MID = new LinkedHashMap<>();
    static {
        DID_BY_MID.put("RCTST1000120415", "00068870522555872903"); // Retail,     MCC 5399
        DID_BY_MID.put("RCTST1000120416", "00068870579979596967"); // Supermarket, MCC 5411
        DID_BY_MID.put("RCTST1000120414", "00068870490417436045"); // Restaurant,  MCC 5812
    }

    /**
     * A diverse slice of canonical test cases spanning entry modes, brands,
     * industries, transaction types, and feature groups. Chosen from the survey
     * of TestTransactions_RSO024.csv (2026-04-13).
     */
    static final String[][] CASES = {
        // TestCaseID,     description
        {"200376490010", "Visa contactless Authorization  / Retail     / TermCatCode=09 / plain (baseline)"},
        {"200113500010", "Visa contactless Authorization  / Retail     / TermCatCode=01 / ApplePay wallet"},
        {"200072070010", "Visa swiped (901) Authorization / Retail     / TermCatCode=01"},
        {"200466910010", "MC   contactless Authorization  / Retail     / MCGrp DevTypeInd=01"},
        {"200376410010", "Visa contactless Authorization  / Supermarket/ TermCatCode=09"},
        {"200070230010", "Visa swiped (901) Authorization / Restaurant / TermCatCode=01 (no contactless exists for Restaurant)"},
        {"200151030010", "Visa swiped (901) Refund        / Retail     / unreferenced, RefundType=Online"},
        {"200109600010", "Visa contactless Refund         / Retail     / TermCatCode=01"},
    };

    static int clientRefCounter = 700;

    public static void main(String[] args) throws Exception {
        Map<String, String> caseXmls = loadTestCases(CSV_PATH);
        System.out.println("Loaded " + caseXmls.size() + " test cases from CSV");
        System.out.println();

        for (String[] c : CASES) {
            String tcId = c[0];
            String desc = c[1];
            String raw = caseXmls.get(tcId);
            System.out.println("========== TC " + tcId + " — " + desc + " ==========");
            if (raw == null) {
                System.out.println("SKIP: not found in CSV");
                System.out.println();
                continue;
            }

            String mid = extract(raw, "<MerchID>", "</MerchID>");
            String tid = extract(raw, "<TermID>", "</TermID>");
            String did = DID_BY_MID.get(mid);
            if (did == null) {
                System.out.println("SKIP: no DID registered for MerchID " + mid);
                System.out.println();
                continue;
            }
            String auth = "20001" + mid + "|" + tid;

            String umf = freshenTimestamps(raw);
            String clientRef = String.format("%07dVRSO024", clientRefCounter++);

            String envelope =
                  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Request Version=\"3\" ClientTimeout=\"30\">"
                + "<ReqClientID>"
                + "<DID>" + did + "</DID><App>RAPIDCONNECTVXN</App>"
                + "<Auth>" + auth + "</Auth>"
                + "<ClientRef>" + clientRef + "</ClientRef>"
                + "</ReqClientID>"
                + "<Transaction><ServiceID>160</ServiceID>"
                + "<Payload Encoding=\"cdata\"><![CDATA[" + umf + "]]></Payload>"
                + "</Transaction></Request>";

            System.out.println("MID=" + mid + " TID=" + tid + " DID=" + did + " ClientRef=" + clientRef);
            String resp = post(TX_URL, envelope);
            String umfResp = extractCdata(resp);
            String respCode = extract(umfResp, "<RespCode>", "</RespCode>");
            String addtl   = extract(umfResp, "<AddtlRespData>", "</AddtlRespData>");
            String errData = extract(umfResp, "<ErrorData>", "</ErrorData>");
            String authId  = extract(umfResp, "<AuthID>", "</AuthID>");
            String ntwk    = extract(umfResp, "<AthNtwkNm>", "</AthNtwkNm>");

            System.out.printf("RESULT: RespCode=%s  AddtlRespData=%s  AuthID=%s  Network=%s  Err=%s%n",
                    respCode, addtl, authId, ntwk, errData);
            System.out.println("--- full UMF response ---");
            System.out.println(umfResp);
            System.out.println();
        }
    }

    /** Parses the semicolon-separated CSV where each row is TestCaseID;"XML". */
    static Map<String, String> loadTestCases(String path) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        String text = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        // Lines start with 12-digit TestCase ID + ; + opening quote; XML can span lines
        // until a closing unescaped quote. Use a regex over the whole file.
        Pattern p = Pattern.compile(
                "^(\\d{12});\"((?:[^\"]|\"\")*)\"",
                Pattern.MULTILINE);
        Matcher m = p.matcher(text);
        while (m.find()) {
            String tcId = m.group(1);
            String xml  = m.group(2).replace("\"\"", "\""); // un-escape CSV double quotes
            out.put(tcId, xml.trim());
        }
        return out;
    }

    /** Replaces LocalDateTime, TrnmsnDateTime, STAN, RefNum, and OrderNum with fresh values. */
    static String freshenTimestamps(String xml) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String localDt = LocalDateTime.now().format(fmt);
        String utcDt   = LocalDateTime.now(ZoneOffset.UTC).format(fmt);
        long nano = System.nanoTime();
        String stan    = String.format("%06d", Math.abs(nano / 1000L) % 1000000L);
        String refNum  = String.format("%010d", Math.abs(nano) % 10000000000L);
        String orderNum= String.format("%08d", Math.abs(nano / 10L) % 100000000L);

        xml = replaceTag(xml, "LocalDateTime",  localDt);
        xml = replaceTag(xml, "TrnmsnDateTime", utcDt);
        xml = replaceTag(xml, "STAN",           stan);
        xml = replaceTag(xml, "RefNum",         refNum);
        xml = replaceTag(xml, "OrderNum",       orderNum);
        return xml;
    }

    static String replaceTag(String xml, String tag, String newValue) {
        return xml.replaceAll(
                "<" + tag + ">[^<]*</" + tag + ">",
                "<" + tag + ">" + newValue + "</" + tag + ">");
    }

    static String post(String url, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(40000);
        conn.setRequestProperty("Content-Type", "text/xml");
        conn.setRequestProperty("Accept", "text/xml, multipart/related");
        conn.setRequestProperty("User-Agent", "Softpay SoftPOS v1.0");
        conn.setRequestProperty("Connection", "Keep-Alive");
        conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        InputStream is = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
        String resp = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
        conn.disconnect();
        return resp;
    }

    static String extract(String s, String open, String close) {
        if (s == null) return null;
        int a = s.indexOf(open);
        if (a < 0) return null;
        a += open.length();
        int b = s.indexOf(close, a);
        return b < 0 ? null : s.substring(a, b).trim();
    }

    static String extractCdata(String envelope) {
        int a = envelope.indexOf("<![CDATA[");
        if (a < 0) return "";
        a += 9;
        int b = envelope.indexOf("]]>", a);
        return b < 0 ? "" : envelope.substring(a, b);
    }
}
