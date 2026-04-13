import java.io.*;
import java.net.*;
import java.time.*;
import java.time.format.*;

/**
 * Send TC 200376490010 (Visa Contactless Auth) against already-registered DIDs
 * and dump the full response so we can see the Recommendation / AddtlRespData.
 */
public class TxnOnlyProbe {
    static final String TX_URL = "https://stg.dw.us.fdcnet.biz/rc";

    // { MID, TID, DID, MCC, description }
    static final String[][] COMBOS = {
        {"RCTST1000120415", "00000001", "00068870522555872903", "5399", "Retail"},
        {"RCTST1000120416", "00000001", "00068870579979596967", "5411", "Supermarket"},
        {"RCTST1000120414", "00000001", "00068870490417436045", "5812", "Restaurant"},
    };

    static int clientRefCounter = 600;

    public static void main(String[] args) throws Exception {
        for (String[] c : COMBOS) {
            String mid = c[0], tid = c[1], did = c[2], mcc = c[3], desc = c[4];
            String auth = "20001" + mid + "|" + tid;
            System.out.println("========== " + mid + " (" + desc + " MCC=" + mcc + ") ==========");
            String resp = sendAuth(did, auth, mid, tid, mcc);
            System.out.println(resp);
            System.out.println();
        }
    }

    static String sendAuth(String did, String auth, String mid, String tid, String mcc) throws Exception {
        String clientRef = String.format("%07dVRSO024", clientRefCounter++);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String localDt = LocalDateTime.now().format(fmt);
        String utcDt = LocalDateTime.now(ZoneOffset.UTC).format(fmt);
        long stanMicro = System.nanoTime() / 1000L;
        String stan = String.format("%06d", Math.abs(stanMicro) % 1000000);
        String refNum = String.format("%012d", Math.abs(stanMicro) % 1000000000000L);

        // Byte-for-byte replay of TC 200376490010 (Visa contactless Auth).
        // Only STAN/RefNum/OrderNum/timestamps vary.
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
            + "<OrderNum>90604914</OrderNum>"
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

        HttpURLConnection conn = (HttpURLConnection) new URL(TX_URL).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(40000);
        conn.setRequestProperty("Content-Type", "text/xml");
        conn.setRequestProperty("Accept", "text/xml, multipart/related");
        conn.setRequestProperty("User-Agent", "Softpay SoftPOS v1.0");
        conn.setRequestProperty("Connection", "Keep-Alive");
        conn.getOutputStream().write(envelope.getBytes("UTF-8"));
        InputStream is = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
        String resp = is == null ? "" : new String(is.readAllBytes(), "UTF-8");
        conn.disconnect();
        return resp;
    }
}
