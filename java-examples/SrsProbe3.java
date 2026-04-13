import java.io.*;
import java.net.*;

public class SrsProbe3 {
    static final String SRS_URL = "https://stagingsupport.datawire.net/nocportal/SRS.do";
    static final String[][] COMBOS = {
        {"RCTST1000119068", "00000001", "Restaurant Cert"},
        {"RCTST1000119069", "00000001", "Retail Cert"},
        {"RCTST1000119070", "00000001", "Supermarket Cert"},
    };

    public static void main(String[] args) throws Exception {
        for (String[] combo : COMBOS) {
            String mid = combo[0]; String tid = combo[1]; String label = combo[2];
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
            conn.setRequestProperty("Content-Type", "text/xml");
            conn.getOutputStream().write(xml.getBytes("UTF-8"));

            InputStream is = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
            String resp = new String(is.readAllBytes(), "UTF-8");
            System.out.println("--- " + label + " (" + mid + " / " + tid + ") ---");
            System.out.println(resp);
            System.out.println();
            conn.disconnect();
        }
    }
}
