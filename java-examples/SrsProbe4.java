import java.io.*;
import java.net.*;

public class SrsProbe4 {
    static final String SRS_URL = "https://stagingsupport.datawire.net/nocportal/SRS.do";
    static final String TX_URL  = "https://stg.dw.us.fdcnet.biz/rc";

    public static void main(String[] args) throws Exception {
        String[] mids = {"RCTST1000119068", "RCTST1000119069", "RCTST1000119070", "RCTST0000000065"};
        String[] tids = {"00000001", "00000002", "00000003"};

        int clientRefCounter = 1;

        for (String mid : mids) {
            for (String tid : tids) {
                String auth = "20001" + mid + "|" + tid;
                String clientRef = String.format("%07dVRSO024", clientRefCounter++);

                String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Request Version=\"3\"><ReqClientID>"
                    + "<DID></DID><App>RAPIDCONNECTSRS</App>"
                    + "<Auth>" + auth + "</Auth>"
                    + "<ClientRef>" + clientRef + "</ClientRef>"
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
                conn.disconnect();

                String status = extract(resp, "StatusCode=\"", "\"");
                String did = extract(resp, "<DID>", "</DID>");
                if (did == null || did.isEmpty()) did = "-";
                String msg = extract(resp, "StatusCode=\"" + status + "\">", "</Status>");
                if (msg == null) msg = "";

                System.out.printf("%-17s %-10s | %-14s %-22s DID=%s%n",
                        mid, tid, status, msg, did);
            }
        }
    }

    static String extract(String s, String open, String close) {
        int a = s.indexOf(open);
        if (a < 0) return null;
        a += open.length();
        int b = s.indexOf(close, a);
        if (b < 0) return null;
        return s.substring(a, b);
    }
}
