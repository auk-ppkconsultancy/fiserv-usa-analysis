import java.io.*;
import java.net.*;

public class SrsProbe {
    static final String SRS_URL = "https://stagingsupport.datawire.net/nocportal/SRS.do";
    static final String[][] COMBOS = {
        {"RCTST1000119068", "00000001"}, // Restaurant, Cert
        {"RCTST1000119068", "00000002"}, // Restaurant, Dev 2
        {"RCTST1000119068", "00000003"}, // Restaurant, Dev 3
        {"RCTST1000119069", "00000001"}, // Retail, Cert
        {"RCTST1000119069", "00000002"}, // Retail, Dev 2
        {"RCTST1000119069", "00000003"}, // Retail, Dev 3
        {"RCTST1000119070", "00000001"}, // Supermarket, Cert
        {"RCTST1000119070", "00000002"}, // Supermarket, Dev 2
        {"RCTST1000119070", "00000003"}, // Supermarket, Dev 3
    };

    public static void main(String[] args) throws Exception {
        for (String[] combo : COMBOS) {
            String mid = combo[0];
            String tid = combo[1];
            String auth = "20001" + mid + "|" + tid;
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Request Version=\"3\"><ReqClientID>"
                + "<DID></DID><App>RAPIDCONNECTSRS</App>"
                + "<Auth>" + auth + "</Auth>"
                + "<ClientRef>0000001VRSO024</ClientRef>"
                + "</ReqClientID><Registration><ServiceID>160</ServiceID></Registration></Request>";

            HttpURLConnection conn = (HttpURLConnection) new URL(SRS_URL).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.getOutputStream().write(xml.getBytes("UTF-8"));

            InputStream is = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
            String resp = new String(is.readAllBytes(), "UTF-8");

            // Extract StatusCode
            String status = "?";
            int idx = resp.indexOf("StatusCode=\"");
            if (idx >= 0) {
                int end = resp.indexOf("\"", idx + 12);
                status = resp.substring(idx + 12, end);
            }
            // Extract DID if present
            String didVal = "-";
            int didIdx = resp.indexOf("<DID>");
            if (didIdx >= 0) {
                int didEnd = resp.indexOf("</DID>", didIdx);
                didVal = resp.substring(didIdx + 5, didEnd);
            }

            System.out.printf("MID=%-16s TID=%s  -> %s  DID=%s%n", mid, tid, status, didVal);
            conn.disconnect();
        }
    }
}
