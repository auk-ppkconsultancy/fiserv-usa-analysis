import java.io.*;
import java.net.*;

public class SrsProbe2 {
    static final String SRS_URL = "https://stagingsupport.datawire.net/nocportal/SRS.do";

    public static void main(String[] args) throws Exception {
        // Try one combo and print the full response
        String auth = "20001RCTST1000119068|00000001";
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

        System.out.println("HTTP Status: " + conn.getResponseCode());
        InputStream is = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
        String resp = new String(is.readAllBytes(), "UTF-8");
        System.out.println("Full response:\n" + resp);
        conn.disconnect();
    }
}
