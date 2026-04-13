package com.softpay.fiserv;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Datawire Secure Transport client for Fiserv Rapid Connect.
 *
 * Handles the full Datawire lifecycle:
 *   1. Service Discovery  — HTTP GET to discover SRS endpoint URL
 *   2. Registration        — POST to SRS, obtains Device ID (DID) + transaction URLs
 *   3. Activation          — POST to SRS, activates the DID
 *   4. Transaction         — POST UMF payloads wrapped in Datawire XML envelopes
 *
 * Protocol details:
 *   - XML over HTTPS (TLSv1.2+, TLSv1.3 preferred)
 *   - Datawire XML schema: rcxml.xsd (Version="3")
 *   - UMF payload encoding: CDATA (default) or xml_escape
 *   - ServiceID: 160 (Rapid Connect)
 *
 * References:
 *   - SecureTransport-SRS-RapidConnect.pdf (v1.10, Feb 2025)
 *   - Datawire Parameter Guidelines for Rapid Connect.pdf (v1.2, Feb 2025)
 *   - SDK_RSO024/RCToolkitSampleCode (Java/C# samples)
 *
 * @see <a href="https://stg.dw.us.fdcnet.biz">Datawire Staging</a>
 */
public class DatawireClient {

    // =====================================================================
    // Datawire Environment URLs
    // =====================================================================

    /** Staging: service discovery endpoint */
    public static final String STAGING_SD_URL = "https://stg.dw.us.fdcnet.biz/sd/srsxml.rc";
    /** Production: service discovery endpoint */
    public static final String PROD_SD_URL = "https://prod.dw.us.fdcnet.biz/sd/srsxml.rc";

    // =====================================================================
    // Protocol Constants
    // =====================================================================

    private static final String VERSION = "3";
    private static final String SERVICE_ID = "160";
    private static final String APP_SRS = "RAPIDCONNECTSRS";   // SRS registration/activation
    private static final String APP_TXN = "RAPIDCONNECTVXN";   // Transaction processing
    private static final int DEFAULT_CLIENT_TIMEOUT = 30;       // seconds (staging: 15-40s)
    private static final int HTTP_READ_TIMEOUT_MS = 45_000;     // ClientTimeout + 15s buffer
    private static final int HTTP_CONNECT_TIMEOUT_MS = 10_000;
    private static final int SRS_MAX_RETRIES = 5;
    private static final long SRS_RETRY_INTERVAL_MS = 30_000;   // 30s between SRS retries

    // =====================================================================
    // Configuration (set at construction)
    // =====================================================================

    private final String groupId;
    private final String merchantId;
    private final String terminalId;
    private final String tppId;
    private final String userAgent;
    private final int clientTimeoutSec;
    private final boolean dryRun;

    // =====================================================================
    // State (populated during SRS lifecycle)
    // =====================================================================

    private String did = "";                                     // Empty until SRS registration
    private String srsUrl;                                       // From service discovery
    private final List<String> transactionUrls = new ArrayList<>(); // From registration
    private int txnCounter = 0;                                  // For ClientRef generation

    // =====================================================================
    // Constructor
    // =====================================================================

    /**
     * Creates a Datawire client for a specific merchant terminal.
     *
     * @param groupId    Fiserv Group ID (e.g., "20001")
     * @param merchantId Rapid Connect MID (e.g., "RCTST1000119069")
     * @param terminalId Terminal ID, 8 chars (e.g., "00000001")
     * @param tppId      TPP ID, 6 chars (e.g., "RSO024")
     * @param userAgent  HTTP User-Agent header (e.g., "Softpay SoftPOS v1.0")
     * @param dryRun     If true, prints XML but does not make network calls
     */
    public DatawireClient(String groupId, String merchantId, String terminalId,
                          String tppId, String userAgent, boolean dryRun) {
        this.groupId = groupId;
        this.merchantId = merchantId;
        this.terminalId = terminalId;
        this.tppId = tppId;
        this.userAgent = userAgent;
        this.clientTimeoutSec = DEFAULT_CLIENT_TIMEOUT;
        this.dryRun = dryRun;
    }

    // =====================================================================
    // Step 1: Service Discovery
    // =====================================================================

    /**
     * Discovers the SRS endpoint URL via HTTP GET.
     * Must be called before {@link #register()}.
     *
     * The service discovery URL returns an XML response containing the
     * SRS endpoint URL for registration and activation.
     *
     * @param serviceDiscoveryUrl staging or production SD URL
     * @return the SRS endpoint URL
     */
    public String discoverSrsEndpoint(String serviceDiscoveryUrl) throws Exception {
        log("SERVICE DISCOVERY", "GET " + serviceDiscoveryUrl);

        if (dryRun) {
            this.srsUrl = serviceDiscoveryUrl;
            log("SERVICE DISCOVERY", "[DRY-RUN] SRS URL: " + srsUrl);
            return srsUrl;
        }

        String response = httpGet(serviceDiscoveryUrl);
        log("SERVICE DISCOVERY", "Response:\n" + response);
        this.srsUrl = parseServiceDiscoveryUrl(response);
        log("SERVICE DISCOVERY", "SRS URL: " + srsUrl);
        return srsUrl;
    }

    // =====================================================================
    // Step 2: Registration
    // =====================================================================

    /**
     * Registers this MID/TID with Datawire and obtains a Device ID (DID).
     *
     * The DID is permanent — store it securely and reuse it for all
     * subsequent requests. Never re-register an already-activated DID
     * (will return AccessDenied).
     *
     * Retries automatically on "Retry" status (up to 5 times, 30s apart).
     *
     * @return the assigned DID
     * @throws DatawireException on terminal errors (AuthenticationError, etc.)
     */
    public String register() throws Exception {
        if (srsUrl == null) throw new IllegalStateException("Call discoverSrsEndpoint() first");

        String xml = buildRegistrationXml();
        log("REGISTRATION", "Request:\n" + xml);

        if (dryRun) {
            this.did = "DRY_RUN_DID_20260410";
            this.transactionUrls.add("https://stg.dw.us.fdcnet.biz/rc");
            log("REGISTRATION", "[DRY-RUN] DID: " + did);
            log("REGISTRATION", "[DRY-RUN] Transaction URL: " + transactionUrls.get(0));
            return did;
        }

        for (int attempt = 1; attempt <= SRS_MAX_RETRIES; attempt++) {
            String response = httpPost(srsUrl, xml);
            DatawireResponse dwr = parseDatawireResponse(response);

            if ("OK".equals(dwr.statusCode)) {
                this.did = dwr.did;
                this.transactionUrls.addAll(dwr.urls);
                log("REGISTRATION", "DID assigned: " + did);
                log("REGISTRATION", "Transaction URLs: " + transactionUrls);
                return did;
            } else if ("Retry".equals(dwr.statusCode)) {
                log("REGISTRATION", "Retry " + attempt + "/" + SRS_MAX_RETRIES + " (waiting 30s)");
                Thread.sleep(SRS_RETRY_INTERVAL_MS);
            } else {
                throw new DatawireException("Registration failed: StatusCode=" + dwr.statusCode);
            }
        }
        throw new DatawireException("Registration failed after " + SRS_MAX_RETRIES + " retries");
    }

    // =====================================================================
    // Step 3: Activation
    // =====================================================================

    /**
     * Activates the DID obtained from {@link #register()}.
     * Must be called exactly once after registration.
     *
     * After activation, the DID is ready for transaction processing.
     * The DID remains valid indefinitely unless compromised/deactivated.
     */
    public void activate() throws Exception {
        if (did == null || did.isEmpty()) throw new IllegalStateException("Call register() first");

        String xml = buildActivationXml();
        log("ACTIVATION", "Request:\n" + xml);

        if (dryRun) {
            log("ACTIVATION", "[DRY-RUN] DID activated: " + did);
            return;
        }

        for (int attempt = 1; attempt <= SRS_MAX_RETRIES; attempt++) {
            String response = httpPost(srsUrl, xml);
            DatawireResponse dwr = parseDatawireResponse(response);

            if ("OK".equals(dwr.statusCode)) {
                log("ACTIVATION", "DID activated successfully");
                return;
            } else if ("Retry".equals(dwr.statusCode)) {
                log("ACTIVATION", "Retry " + attempt + "/" + SRS_MAX_RETRIES + " (waiting 30s)");
                Thread.sleep(SRS_RETRY_INTERVAL_MS);
            } else {
                throw new DatawireException("Activation failed: StatusCode=" + dwr.statusCode);
            }
        }
        throw new DatawireException("Activation failed after " + SRS_MAX_RETRIES + " retries");
    }

    // =====================================================================
    // Step 4: Send Transaction
    // =====================================================================

    /**
     * Sends a UMF payload to Fiserv via Datawire Secure Transport.
     *
     * The UMF XML (Authorization, Completion, Void, etc.) is wrapped in a
     * Datawire Transaction envelope with CDATA encoding.
     *
     * @param umfPayload the complete GMF XML (output from FiservUmfMessageBuilder)
     * @return the UMF response XML (extracted from Datawire response), or null in dry-run
     */
    public String sendTransaction(String umfPayload) throws Exception {
        String envelope = buildTransactionXml(umfPayload);
        log("TRANSACTION", "Datawire envelope:\n" + envelope);

        if (dryRun) {
            log("TRANSACTION", "[DRY-RUN] Would POST to: " + getTransactionUrl());
            return null;
        }

        String txnUrl = getTransactionUrl();
        log("TRANSACTION", "POST " + txnUrl);
        String response = httpPost(txnUrl, envelope);
        DatawireResponse dwr = parseDatawireResponse(response);

        if (!"OK".equals(dwr.statusCode)) {
            throw new DatawireException("Datawire error: StatusCode=" + dwr.statusCode
                    + " (ReturnCode=" + dwr.returnCode + ")");
        }

        log("TRANSACTION", "Datawire OK, ReturnCode=" + dwr.returnCode);
        log("TRANSACTION", "Raw Datawire response:\n" + response);
        log("TRANSACTION", "Extracted UMF payload:\n" + dwr.payload);
        return dwr.payload;
    }

    // =====================================================================
    // AuthKey Construction
    // =====================================================================

    /**
     * Builds the Datawire authentication key.
     *
     * Format: {@code AuthKey1|AuthKey2}
     *   AuthKey1 = GroupID + MerchID (up to 32 alphanumeric)
     *   AuthKey2 = TermID zero-padded to 8 characters
     *
     * Example: "20001RCTST1000119069|00000001"
     */
    public String buildAuthKey() {
        String authKey1 = groupId + merchantId;   // e.g., "20001RCTST1000119069"
        String authKey2 = String.format("%8s", terminalId).replace(' ', '0');
        return authKey1 + "|" + authKey2;
    }

    // =====================================================================
    // ClientRef Construction
    // =====================================================================

    /**
     * Generates the next unique ClientRef.
     *
     * Format: tttttttVxxxxxx (14 characters)
     *   ttttttt = 7-digit sequential transaction counter (unique per 24h)
     *   V       = literal separator
     *   xxxxxx  = TPPID (6 characters, e.g., "RSO024")
     *
     * Example: "0000001VRSO024"
     */
    private String nextClientRef() {
        txnCounter++;
        return String.format("%07d", txnCounter) + "V" + tppId;
    }

    // =====================================================================
    // Datawire XML Envelope Builders
    // =====================================================================

    /**
     * Registration XML: DID is empty (not yet assigned).
     * App = RAPIDCONNECTSRS.
     */
    private String buildRegistrationXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Request Version=\"" + VERSION + "\">\n"
                + "  <ReqClientID>\n"
                + "    <DID></DID>\n"
                + "    <App>" + APP_SRS + "</App>\n"
                + "    <Auth>" + buildAuthKey() + "</Auth>\n"
                + "    <ClientRef>" + nextClientRef() + "</ClientRef>\n"
                + "  </ReqClientID>\n"
                + "  <Registration>\n"
                + "    <ServiceID>" + SERVICE_ID + "</ServiceID>\n"
                + "  </Registration>\n"
                + "</Request>";
    }

    /**
     * Activation XML: DID from registration, App = RAPIDCONNECTSRS.
     */
    private String buildActivationXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Request Version=\"" + VERSION + "\">\n"
                + "  <ReqClientID>\n"
                + "    <DID>" + did + "</DID>\n"
                + "    <App>" + APP_SRS + "</App>\n"
                + "    <Auth>" + buildAuthKey() + "</Auth>\n"
                + "    <ClientRef>" + nextClientRef() + "</ClientRef>\n"
                + "  </ReqClientID>\n"
                + "  <Activation>\n"
                + "    <ServiceID>" + SERVICE_ID + "</ServiceID>\n"
                + "  </Activation>\n"
                + "</Request>";
    }

    /**
     * Transaction XML: wraps UMF payload in Datawire envelope with CDATA encoding.
     * App = RAPIDCONNECTVXN, ClientTimeout included.
     *
     * Important: nested CDATA sections are INVALID — ensure the UMF payload
     * does not contain CDATA markers. The FiservUmfMessageBuilder output is safe.
     */
    private String buildTransactionXml(String umfPayload) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Request Version=\"" + VERSION + "\" ClientTimeout=\""
                + clientTimeoutSec + "\">\n"
                + "  <ReqClientID>\n"
                + "    <DID>" + did + "</DID>\n"
                + "    <App>" + APP_TXN + "</App>\n"
                + "    <Auth>" + buildAuthKey() + "</Auth>\n"
                + "    <ClientRef>" + nextClientRef() + "</ClientRef>\n"
                + "  </ReqClientID>\n"
                + "  <Transaction>\n"
                + "    <ServiceID>" + SERVICE_ID + "</ServiceID>\n"
                + "    <Payload Encoding=\"cdata\"><![CDATA[" + umfPayload + "]]></Payload>\n"
                + "  </Transaction>\n"
                + "</Request>";
    }

    // =====================================================================
    // Datawire Response Parsing
    // =====================================================================

    /**
     * Parsed Datawire response envelope.
     * Contains transport-level status and the extracted UMF payload.
     */
    public static class DatawireResponse {
        /** Datawire status: OK, AuthenticationError, Retry, Timeout, etc. */
        public String statusCode;
        public String statusMessage;
        /** Device ID (from RespClientID or RegistrationResponse) */
        public String did;
        /** Echoed ClientRef */
        public String clientRef;
        /** Transaction return code (from TransactionResponse) */
        public String returnCode;
        /** Extracted UMF XML payload (from TransactionResponse) */
        public String payload;
        /** Transaction URLs (from RegistrationResponse) */
        public List<String> urls = new ArrayList<>();
    }

    private DatawireResponse parseDatawireResponse(String xml) throws Exception {
        DatawireResponse resp = new DatawireResponse();
        XMLStreamReader reader = XMLInputFactory.newInstance()
                .createXMLStreamReader(new StringReader(xml));

        String currentElement = null;
        boolean inRespClientID = false;
        boolean inRegistrationResponse = false;
        boolean inTransactionResponse = false;

        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = reader.getLocalName();
                currentElement = name;

                switch (name) {
                    case "Status" -> resp.statusCode = reader.getAttributeValue(null, "StatusCode");
                    case "RespClientID" -> inRespClientID = true;
                    case "RegistrationResponse" -> inRegistrationResponse = true;
                    case "TransactionResponse" -> inTransactionResponse = true;
                }

            } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                String text = reader.getText().trim();
                if (text.isEmpty() || currentElement == null) continue;

                if (inRespClientID) {
                    if ("DID".equals(currentElement)) resp.did = text;
                    else if ("ClientRef".equals(currentElement)) resp.clientRef = text;
                } else if (inRegistrationResponse) {
                    if ("DID".equals(currentElement)) resp.did = text;
                    else if ("URL".equals(currentElement)) resp.urls.add(text);
                } else if (inTransactionResponse) {
                    if ("ReturnCode".equals(currentElement)) resp.returnCode = text;
                    else if ("Payload".equals(currentElement)) {
                        // Append — CDATA content may arrive in multiple events
                        resp.payload = (resp.payload == null) ? text : resp.payload + text;
                    }
                }

            } else if (event == XMLStreamConstants.END_ELEMENT) {
                String name = reader.getLocalName();
                if ("RespClientID".equals(name)) inRespClientID = false;
                else if ("RegistrationResponse".equals(name)) inRegistrationResponse = false;
                else if ("TransactionResponse".equals(name)) inTransactionResponse = false;
                currentElement = null;
            }
        }
        reader.close();
        return resp;
    }

    /**
     * Extracts the SRS URL from a service discovery XML response.
     */
    private String parseServiceDiscoveryUrl(String xml) throws Exception {
        XMLStreamReader reader = XMLInputFactory.newInstance()
                .createXMLStreamReader(new StringReader(xml));

        String url = null;
        boolean inServiceProvider = false;
        String currentElement = null;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                currentElement = reader.getLocalName();
                if ("ServiceProvider".equals(currentElement)) inServiceProvider = true;
            } else if (event == XMLStreamConstants.CHARACTERS) {
                String text = reader.getText().trim();
                if (inServiceProvider && "URL".equals(currentElement) && !text.isEmpty()) {
                    url = text;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("ServiceProvider".equals(reader.getLocalName())) inServiceProvider = false;
                currentElement = null;
            }
        }
        reader.close();

        if (url == null) throw new DatawireException("No SRS URL in service discovery response");
        return url;
    }

    // =====================================================================
    // HTTP Transport
    // =====================================================================

    private String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", userAgent);
        conn.setRequestProperty("Host", url.getHost());
        conn.setRequestProperty("Cache-Control", "no-cache");
        conn.setConnectTimeout(HTTP_CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(HTTP_READ_TIMEOUT_MS);

        return readResponseBody(conn);
    }

    private String httpPost(String urlStr, String body) throws Exception {
        URL url = new URL(urlStr);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "text/xml");
        conn.setRequestProperty("Accept", "text/xml, multipart/related");
        conn.setRequestProperty("User-Agent", userAgent);
        conn.setRequestProperty("Host", url.getHost());
        conn.setRequestProperty("Connection", "Keep-Alive");
        conn.setRequestProperty("Cache-Control", "no-cache");
        conn.setConnectTimeout(HTTP_CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(HTTP_READ_TIMEOUT_MS);

        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));

        try (OutputStream os = conn.getOutputStream()) {
            os.write(bodyBytes);
            os.flush();
        }

        return readResponseBody(conn);
    }

    private String readResponseBody(java.net.HttpURLConnection conn) throws Exception {
        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();

        if (is == null) throw new DatawireException("Empty response body, HTTP " + status);

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString().trim();
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** Returns the first available transaction URL, or falls back to SRS URL. */
    private String getTransactionUrl() {
        return transactionUrls.isEmpty() ? srsUrl : transactionUrls.get(0);
    }

    public String getDid() { return did; }

    /** Set a pre-registered DID (skip SRS registration). */
    public void setDid(String did) {
        this.did = did;
    }

    /** Set a transaction URL directly (skip SRS registration). */
    public void setTransactionUrl(String url) {
        transactionUrls.clear();
        transactionUrls.add(url);
    }

    public List<String> getTransactionUrls() {
        return Collections.unmodifiableList(transactionUrls);
    }

    private void log(String phase, String message) {
        System.out.println("[" + phase + "] " + message);
    }

    // =====================================================================
    // Exception Type
    // =====================================================================

    /**
     * Thrown for Datawire transport-level errors.
     *
     * StatusCode values:
     *   OK, AuthenticationError, UnknownServiceID, Timeout, XMLError,
     *   OtherError, AccessDenied, InvalidMerchant, Failed, Duplicated,
     *   Retry, NotFound, SOAPError, InternalError
     */
    public static class DatawireException extends Exception {
        public DatawireException(String message) { super(message); }
        public DatawireException(String message, Throwable cause) { super(message, cause); }
    }
}
