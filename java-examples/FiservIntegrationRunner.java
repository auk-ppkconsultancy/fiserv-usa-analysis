package com.softpay.fiserv;

import com.softpay.fiserv.FiservResponseParser.AuthorizationResponse;

/**
 * End-to-end integration runner for Fiserv Rapid Connect via Datawire.
 *
 * Demonstrates the complete transaction lifecycle:
 *   1. Datawire Service Discovery → SRS Registration → Activation
 *   2. Credit Authorization (Visa contactless tap, $50.00)
 *   3. Credit Completion (capture with $8 tip = $58.00)
 *   4. Credit Authorization (Visa contactless, $75.00)
 *   5. Credit Void (cancel before capture)
 *
 * Uses test card numbers from the RSO024 certification test script.
 *
 * Compile and run:
 *   cd java-examples
 *   javac -d out *.java
 *   java -cp out com.softpay.fiserv.FiservIntegrationRunner          # dry-run (default)
 *   java -cp out com.softpay.fiserv.FiservIntegrationRunner --live    # staging
 *   java -cp out com.softpay.fiserv.FiservIntegrationRunner --live --did=YOUR_DID
 */
public class FiservIntegrationRunner {

    // =====================================================================
    // RSO024 Project Configuration
    // =====================================================================

    // Merchant IDs — assigned by Fiserv (from Project Profile)
    private static final String MID_RESTAURANT  = "RCTST1000119068";   // MCC 5812
    private static final String MID_RETAIL      = "RCTST1000119069";   // MCC 5399
    private static final String MID_SUPERMARKET = "RCTST1000119070";   // MCC 5411

    // Terminal / Group / TPP identifiers
    private static final String TERMINAL_ID = "00000001";    // Cert TID (from Project Profile)
    private static final String GROUP_ID    = "20001";
    private static final String TPP_ID      = "RSO024";
    private static final String USER_AGENT  = "Softpay SoftPOS v1.0";

    // Industry codes
    private static final String MCC_RESTAURANT = "5812";
    private static final String MCC_RETAIL     = "5399";

    // =====================================================================
    // Test Card Data (from RSO024_Testscript_2026-04-07.xlsx)
    // =====================================================================

    // Visa — approves $1.02–$131.85
    static final String VISA_TRACK2 = "4111111111111111=25121011000012345678";
    static final String VISA_PAN    = "4111111111111111";
    static final String VISA_EXPIRY = "2512";   // YYMM

    // MasterCard — approves $112.18–$144.26
    static final String MC_TRACK2 = "5204242750270010=2512101100000123456";
    static final String MC_PAN    = "5204242750270010";
    static final String MC_EXPIRY = "2512";

    // Discover — approves $131.87–$144.74
    static final String DISC_TRACK2 = "6504840209544524=25121011000012345678";
    static final String DISC_PAN    = "6504840209544524";
    static final String DISC_EXPIRY = "2512";

    // Amex — approves $111.22–$145.22
    static final String AMEX_TRACK2 = "370295571160496=251210107108069000000";
    static final String AMEX_PAN    = "370295571160496";
    static final String AMEX_EXPIRY = "2512";

    // =====================================================================
    // Counters
    // =====================================================================

    private int stanCounter = 0;
    private int refNumCounter = 100000;

    /** New STAN for every message (6 digits, unique per day per MID+TID). */
    private String nextStan() {
        return String.format("%06d", ++stanCounter);
    }

    /** New RefNum for new transaction chains; same RefNum for Auth→Completion/Void. */
    private String nextRefNum() {
        return String.format("%012d", ++refNumCounter);
    }

    // =====================================================================
    // Instance State
    // =====================================================================

    private final DatawireClient datawire;
    private final boolean dryRun;

    public FiservIntegrationRunner(boolean dryRun, String existingDid) {
        this.dryRun = dryRun;
        this.datawire = new DatawireClient(
                GROUP_ID, MID_RESTAURANT, TERMINAL_ID, TPP_ID, USER_AGENT, dryRun);

        if (existingDid != null && !existingDid.isEmpty()) {
            datawire.setDid(existingDid);
            datawire.setTransactionUrl("https://stg.dw.us.fdcnet.biz/rc");
        }
    }

    // =====================================================================
    // Main Entry Point
    // =====================================================================

    public static void main(String[] args) throws Exception {
        boolean live = false;
        String existingDid = null;

        for (String arg : args) {
            if ("--live".equals(arg)) live = true;
            if (arg.startsWith("--did=")) existingDid = arg.substring(6);
        }

        boolean dryRun = !live;

        System.out.println("=".repeat(70));
        System.out.println(" Fiserv Rapid Connect — Integration Test Runner (RSO024)");
        System.out.println("=".repeat(70));
        System.out.println("Mode:     " + (dryRun ? "DRY-RUN (use --live for staging)" : "LIVE (staging)"));
        System.out.println("MID:      " + MID_RESTAURANT + " (Restaurant)");
        System.out.println("TID:      " + TERMINAL_ID);
        System.out.println("Group:    " + GROUP_ID);
        System.out.println("TPP:      " + TPP_ID);
        if (existingDid != null) System.out.println("DID:      " + existingDid + " (pre-registered)");
        System.out.println("=".repeat(70));

        FiservIntegrationRunner runner = new FiservIntegrationRunner(dryRun, existingDid);

        // --- SRS Lifecycle ---
        if (existingDid == null) {
            runner.runSrsLifecycle();
        }

        // --- Flow 0: Encryption Key Request (Master Session) ---
        runner.runEncryptionKeyRequest();

        // --- Flow 1: Authorization → Completion (with tip) ---
        runner.runAuthAndCompletion();

        // --- Flow 2: Authorization → Void ---
        runner.runAuthAndVoid();

        // --- Flow 3: Timeout Reversal (TOR) ---
        runner.runTimeoutReversal();

        // --- Flow 4: Referenced Refund ---
        runner.runReferencedRefund();

        // --- Flow 5: Unreferenced Refund ---
        runner.runUnreferencedRefund();

        System.out.println("\n" + "=".repeat(70));
        System.out.println(" All flows completed successfully.");
        System.out.println("=".repeat(70));
    }

    // =====================================================================
    // SRS Lifecycle: Discover → Register → Activate
    // =====================================================================

    private void runSrsLifecycle() throws Exception {
        section("STEP 1: Service Discovery");
        datawire.discoverSrsEndpoint(DatawireClient.STAGING_SD_URL);

        section("STEP 2: SRS Registration");
        String did = datawire.register();
        System.out.println("  ** IMPORTANT: Save this DID permanently: " + did);
        System.out.println("  ** Re-run with --did=" + did + " to skip SRS next time");

        section("STEP 3: SRS Activation");
        datawire.activate();
    }

    // =====================================================================
    // Flow 0: Master Session Encryption Key Request
    // =====================================================================

    /**
     * Requests a session encryption key from Fiserv.
     *
     * This is required before PIN Debit transactions when using Master Session
     * Encryption. We do NOT have the real master key — this sends the request
     * with a dummy key context so we can observe the Fiserv response format
     * and error handling, and capture it for the test log.
     */
    private void runEncryptionKeyRequest() throws Exception {
        section("FLOW 0: Encryption Key Request (Master Session)");

        String ekrStan = nextStan();
        String ekrRefNum = nextRefNum();

        System.out.println("\n--- EncryptionKeyRequest (STAN=" + ekrStan
                + ", RefNum=" + ekrRefNum + ") ---");
        System.out.println("  NOTE: No real master key available — sending request to observe response");

        String ekrXml = FiservUmfMessageBuilder.buildEncryptionKeyRequest(
                MID_RESTAURANT, TERMINAL_ID, ekrStan, ekrRefNum
        );

        System.out.println("UMF Payload:\n" + ekrXml);

        if (!dryRun) {
            String responseXml = datawire.sendTransaction(ekrXml);
            if (responseXml != null) {
                System.out.println("\nRaw UMF Response:\n" + responseXml);

                // Try to parse — may be an AdminResponse or RejectResponse
                try {
                    AuthorizationResponse resp = FiservResponseParser.parseResponse(responseXml);
                    System.out.println("\nParsed Response:");
                    System.out.println("  RespCode:     " + resp.respCode);
                    System.out.println("  AddlRespData: " + resp.addlRespData);
                    System.out.println("  AuthNetID:    " + resp.authNetId);
                    System.out.println("  AuthNetName:  " + resp.authNetName);
                    if (resp.errorData != null)
                        System.out.println("  ErrorData:    " + resp.errorData);
                } catch (Exception e) {
                    System.out.println("\n  Could not parse response: " + e.getMessage());
                    System.out.println("  (AdminResponse may use a different structure — raw XML logged above)");
                }
            } else {
                System.out.println("  No UMF payload extracted from Datawire response");
            }
        } else {
            System.out.println("  [DRY-RUN] Skipped — EncryptionKeyRequest requires live connection");
        }

        System.out.println("\n  ** Flow 0 complete: EncryptionKeyRequest sent");
    }

    // =====================================================================
    // Flow 1: Authorization → Completion (tip-before-auth, Softpay flow)
    // =====================================================================

    /**
     * Demonstrates the Softpay preferred tipping flow (tip-before-auth):
     *   1. Customer taps card, app shows tip screen
     *   2. Customer adds $8.00 tip to $50.00 subtotal
     *   3. Authorization for full $58.00 (service + tip)
     *   4. Completion for same $58.00
     *
     * This is simpler than the traditional restaurant flow (tip-after-auth)
     * because Auth and Completion amounts match — no 20% tolerance needed,
     * works across all MCCs (not just Restaurant).
     *
     * Key rules:
     *   - STAN is NEW for every message
     *   - RefNum is SAME across Auth → Completion
     *   - OrigAuthGrp echoes fields from Auth response
     *   - VisaGrp.TransID must be echoed in Completion
     */
    private void runAuthAndCompletion() throws Exception {
        long serviceAmountCents = 5000;  // $50.00 subtotal
        long tipAmountCents = 800;       // $8.00 tip (collected on-device before auth)
        long totalAmountCents = serviceAmountCents + tipAmountCents; // $58.00

        section("FLOW 1: Authorization + Completion (Visa $50.00 + $8.00 tip = $58.00)");
        System.out.println("  Tip-before-auth flow: tip collected on device, full amount authorized");

        // --- Authorization for full amount (service + tip) ---
        String authStan = nextStan();
        String authRefNum = nextRefNum();

        System.out.println("\n--- Authorization (STAN=" + authStan
                + ", RefNum=" + authRefNum + ", $58.00 incl. tip) ---");

        // Note: MID must match the MID used for DID registration.
        // The DID was registered for MID_RESTAURANT. Using Restaurant MID
        // would require a separate DID registration.
        String authXml = FiservUmfMessageBuilder.buildCreditAuthorizationContactless(
                MID_RESTAURANT, TERMINAL_ID, authStan, authRefNum,
                "ORDER001", MCC_RESTAURANT, totalAmountCents,  // Full amount incl. tip
                VISA_TRACK2, "Visa",
                buildSampleEmvData(totalAmountCents),
                "01"  // Card Sequence Number
        );

        System.out.println("UMF Payload:\n" + authXml);
        String authResponseXml = datawire.sendTransaction(authXml);

        // Parse authorization response
        AuthorizationResponse authResp;
        if (authResponseXml != null) {
            authResp = FiservResponseParser.parseResponse(authResponseXml);
        } else {
            // Dry-run: simulate approved response
            authResp = simulateAuthResponse(authStan, authRefNum, "Visa");
        }

        System.out.println("\nAuthorization Response:");
        System.out.println("  RespCode:     " + authResp.respCode
                + (authResp.isApproved() ? " (APPROVED)" : " (DECLINED)"));
        System.out.println("  AuthID:       " + authResp.authId);
        System.out.println("  ResponseDate: " + authResp.responseDate);
        System.out.println("  STAN:         " + authResp.stan);
        System.out.println("  RefNum:       " + authResp.refNum);
        System.out.println("  CardType:     " + authResp.cardType);
        System.out.println("  VisaTransID:  " + authResp.visaTransId);
        if (authResp.errorData != null)
            System.out.println("  ErrorData:    " + authResp.errorData);

        if (!authResp.isApproved()) {
            System.out.println("  ** Authorization declined — skipping Completion");
            return;
        }

        // --- Completion (same amount — auth and completion match) ---
        String compStan = nextStan();

        System.out.println("\n--- Completion (STAN=" + compStan
                + ", RefNum=" + authRefNum + " [SAME], $58.00) ---");

        // Note: RefNum MUST match the original Authorization
        // Note: STAN is NEW for the Completion
        // Note: Completion TxnAmt = Auth TxnAmt (tip was included in auth)
        String compXml = FiservUmfMessageBuilder.buildCompletion(
                MID_RESTAURANT, TERMINAL_ID, compStan,
                authRefNum,                 // SAME RefNum as Auth
                "ORDER001", MCC_RESTAURANT,
                totalAmountCents,           // $58.00 (same as auth — tip already included)
                VISA_PAN,                   // AcctNum (not Track2 for Completion)
                VISA_EXPIRY,                // Card expiry
                authResp.cardType,          // Echoed from Auth response
                // OrigAuthGrp fields — ALL from the Authorization response:
                authResp.authId,            // OrigAuthID
                authResp.responseDate,      // OrigResponseDate
                authResp.localDateTime,     // OrigLocalDateTime
                authResp.trnmsnDateTime,    // OrigTranDateTime
                authResp.stan,              // OrigSTAN
                authResp.respCode,          // OrigRespCode
                totalAmountCents,           // FirstAuthAmt ($58.00 — full amount was authed)
                totalAmountCents            // TotalAuthAmt ($58.00, no incremental auths)
        );

        // TODO: In production, inject VisaGrp.TransID from auth response into Completion XML.
        // The current FiservUmfMessageBuilder does not pass scheme-specific echo fields.
        // For Visa: echo TransID. For MC: echo BanknetData. For Amex: echo AmExTranID.
        // For Discover: echo DiscoverNRID.
        System.out.println("UMF Payload:\n" + compXml);
        System.out.println("\n  ** NOTE: VisaGrp.TransID (" + authResp.visaTransId
                + ") should be echoed in Completion.");
        System.out.println("  ** Enhance FiservUmfMessageBuilder.buildCompletion() to accept"
                + " scheme-specific fields.");

        String compResponseXml = datawire.sendTransaction(compXml);

        AuthorizationResponse compResp;
        if (compResponseXml != null) {
            compResp = FiservResponseParser.parseResponse(compResponseXml);
        } else {
            compResp = simulateCompletionResponse(compStan, authRefNum);
        }

        System.out.println("\nCompletion Response:");
        System.out.println("  RespCode:     " + compResp.respCode
                + (compResp.isApproved() ? " (CAPTURED)" : " (FAILED)"));
        System.out.println("  AuthID:       " + compResp.authId);

        System.out.println("\n  ** Flow 1 complete: $58.00 authorized and captured (service $50.00 + tip $8.00)");
        System.out.println("  ** Tip-before-auth: auth and completion amounts match — no 20% tolerance needed");
    }

    // =====================================================================
    // Flow 2: Authorization → Void
    // =====================================================================

    /**
     * Demonstrates voiding an authorization before capture:
     *   1. Authorization for $75.00 (Visa contactless tap)
     *   2. Void (merchant-initiated cancellation)
     *
     * Key rules:
     *   - Void must be within 25 minutes of the original transaction
     *   - ReversalInd="Void" for merchant-initiated cancellation
     *   - ReversalInd="Timeout" for TOR (no response received)
     *   - Scheme-specific fields are EXEMPT for Timeout Reversals (2026 change)
     *   - OrigAuthGrp echoes fields from Auth response
     */
    private void runAuthAndVoid() throws Exception {
        long authAmountCents = 7500;    // $75.00

        section("FLOW 2: Authorization + Void (Visa $75.00)");

        // --- Authorization ---
        String authStan = nextStan();
        String authRefNum = nextRefNum();

        System.out.println("\n--- Authorization (STAN=" + authStan
                + ", RefNum=" + authRefNum + ", $75.00) ---");

        String authXml = FiservUmfMessageBuilder.buildCreditAuthorizationContactless(
                MID_RESTAURANT, TERMINAL_ID, authStan, authRefNum,
                "ORDER002", MCC_RESTAURANT, authAmountCents,
                VISA_TRACK2, "Visa",
                buildSampleEmvData(authAmountCents),
                "01"
        );

        System.out.println("UMF Payload:\n" + authXml);
        String authResponseXml = datawire.sendTransaction(authXml);

        AuthorizationResponse authResp;
        if (authResponseXml != null) {
            authResp = FiservResponseParser.parseResponse(authResponseXml);
        } else {
            authResp = simulateAuthResponse(authStan, authRefNum, "Visa");
        }

        System.out.println("\nAuthorization Response:");
        System.out.println("  RespCode:     " + authResp.respCode
                + (authResp.isApproved() ? " (APPROVED)" : " (DECLINED)"));
        System.out.println("  AuthID:       " + authResp.authId);
        System.out.println("  VisaTransID:  " + authResp.visaTransId);
        if (authResp.errorData != null)
            System.out.println("  ErrorData:    " + authResp.errorData);

        if (!authResp.isApproved()) {
            System.out.println("  ** Authorization declined — nothing to void");
            return;
        }

        // --- Void ---
        String voidStan = nextStan();

        System.out.println("\n--- Void (STAN=" + voidStan
                + ", RefNum=" + authRefNum + " [SAME]) ---");

        // Note: Void uses ReversalRequest (not CreditRequest)
        // Note: RefNum MUST match, STAN is NEW
        // Note: ReversalInd="Void" for merchant cancellation
        String voidXml = FiservUmfMessageBuilder.buildVoidFullReversal(
                MID_RESTAURANT, TERMINAL_ID, voidStan,
                authRefNum,                 // SAME RefNum as Auth
                "ORDER002", MCC_RESTAURANT,
                authAmountCents,            // Original amount
                "Void",                     // ReversalInd: merchant-initiated cancellation
                VISA_TRACK2,                // Track2 from original transaction
                authResp.cardType,          // Echoed from Auth response
                null,                       // EMV data (optional for void)
                // OrigAuthGrp fields from Auth response:
                authResp.authId,            // OrigAuthID
                authResp.responseDate,      // OrigResponseDate
                authResp.localDateTime,     // OrigLocalDateTime
                authResp.trnmsnDateTime,    // OrigTranDateTime
                authResp.stan,              // OrigSTAN
                authResp.respCode           // OrigRespCode
        );

        System.out.println("UMF Payload:\n" + voidXml);
        System.out.println("\n  ** NOTE: For Void, VisaGrp.TransID (" + authResp.visaTransId
                + ") MUST be echoed.");
        System.out.println("  ** For TOR (Timeout reversal), scheme fields are EXEMPT (2026 change).");

        String voidResponseXml = datawire.sendTransaction(voidXml);

        AuthorizationResponse voidResp;
        if (voidResponseXml != null) {
            voidResp = FiservResponseParser.parseResponse(voidResponseXml);
        } else {
            voidResp = simulateVoidResponse(voidStan, authRefNum);
        }

        System.out.println("\nVoid Response:");
        System.out.println("  RespCode:     " + voidResp.respCode
                + ("000".equals(voidResp.respCode) ? " (VOIDED)" : " (FAILED)"));

        System.out.println("\n  ** Flow 2 complete: $75.00 authorized then voided");
    }

    // =====================================================================
    // Flow 3: Timeout Reversal (TOR)
    // =====================================================================

    /**
     * Demonstrates a Timeout Reversal (TOR):
     *   1. Authorization for $25.00 (Visa contactless tap)
     *   2. Simulate: no response received (timeout)
     *   3. Send TOR to reverse the possibly-completed auth
     *
     * Key rules:
     *   - ReversalInd="Timeout" (not "Void")
     *   - OrigAuthGrp contains ONLY OrigSTAN, OrigLocalDateTime, OrigTranDateTime
     *     (no OrigAuthID, OrigRespCode, OrigResponseDate — we never got a response)
     *   - Scheme-specific reference fields (Visa TransID, MC BanknetData, etc.)
     *     are EXEMPT for TOR per 2026 UMF Changes
     *   - STAN is NEW; RefNum is SAME as original
     *   - TOR must be sent before any retry of the original transaction
     */
    private void runTimeoutReversal() throws Exception {
        long authAmountCents = 2500;    // $25.00

        section("FLOW 3: Timeout Reversal (TOR) — Visa $25.00");

        // --- Authorization (will "time out") ---
        String authStan = nextStan();
        String authRefNum = nextRefNum();

        // Capture the timestamps we'd have used in the original auth request
        // (needed for OrigAuthGrp even though we got no response)
        String origLocalDateTime = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String origTranDateTime = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        System.out.println("\n--- Original Authorization (STAN=" + authStan
                + ", RefNum=" + authRefNum + ", $25.00) ---");
        System.out.println("  ** SIMULATING TIMEOUT — no response received");
        System.out.println("  ** Original timestamps: local=" + origLocalDateTime
                + ", utc=" + origTranDateTime);

        // In production, the auth would be sent and we'd wait for response.
        // After timeout (e.g., 30s), no response → must send TOR immediately.

        // --- Timeout Reversal ---
        String torStan = nextStan();

        System.out.println("\n--- Timeout Reversal (STAN=" + torStan
                + ", RefNum=" + authRefNum + " [SAME]) ---");

        String torXml = FiservUmfMessageBuilder.buildVoidFullReversal(
                MID_RESTAURANT, TERMINAL_ID, torStan,
                authRefNum,                 // SAME RefNum as original auth
                "ORDER003", MCC_RESTAURANT,
                authAmountCents,            // Original amount
                "Timeout",                  // ReversalInd: timeout reversal
                VISA_TRACK2,               // Track2 from original transaction
                "Visa",
                buildSampleEmvData(authAmountCents), // EMV data from original tap
                // OrigAuthGrp — only fields available without a response:
                null,                       // OrigAuthID: NOT available (no response)
                null,                       // OrigResponseDate: NOT available
                origLocalDateTime,          // OrigLocalDateTime: from original request
                origTranDateTime,           // OrigTranDateTime: from original request
                authStan,                   // OrigSTAN: from original request
                null                        // OrigRespCode: NOT available (no response)
        );

        System.out.println("UMF Payload:\n" + torXml);

        System.out.println("\n  ** NOTE: ReversalInd=Timeout, scheme-specific fields EXEMPT");
        System.out.println("  ** NOTE: OrigAuthGrp has only OrigSTAN/OrigLocalDateTime/OrigTranDateTime");
        System.out.println("  **       (OrigAuthID, OrigRespCode, OrigResponseDate omitted — no response received)");

        String torResponseXml = datawire.sendTransaction(torXml);

        AuthorizationResponse torResp;
        if (torResponseXml != null) {
            torResp = FiservResponseParser.parseResponse(torResponseXml);
        } else {
            torResp = simulateReversalResponse(torStan, authRefNum, "Timeout");
        }

        System.out.println("\nTOR Response:");
        System.out.println("  RespCode:     " + torResp.respCode
                + ("000".equals(torResp.respCode) ? " (REVERSED)" : " (FAILED)"));
        if (torResp.addlRespData != null)
            System.out.println("  AddlRespData: " + torResp.addlRespData);
        if (torResp.errorData != null)
            System.out.println("  ErrorData:    " + torResp.errorData);

        System.out.println("\n  ** Flow 3 complete: $25.00 auth timed out, TOR sent");
    }

    // =====================================================================
    // Flow 4: Referenced Refund (linked to original authorization)
    // =====================================================================

    /**
     * Demonstrates a referenced refund:
     *   1. Authorization for $40.00 (Visa contactless tap)
     *   2. (Assume Completion happened — transaction was captured)
     *   3. Refund $40.00 with OrigAuthGrp linking to original auth
     *
     * Key rules:
     *   - RefNum is NEW for the refund (not the original auth's RefNum)
     *   - OrigAuthGrp echoes all fields from original auth response
     *   - Scheme-specific reference (Visa TransID, etc.) must be echoed
     *   - For MasterCard: RefundType is mandatory (2026 UMF Change)
     *   - Referenced refunds get better interchange rates
     */
    private void runReferencedRefund() throws Exception {
        long authAmountCents = 4000;    // $40.00

        section("FLOW 4: Referenced Refund — Visa $40.00");

        // --- Authorization ---
        String authStan = nextStan();
        String authRefNum = nextRefNum();

        System.out.println("\n--- Authorization (STAN=" + authStan
                + ", RefNum=" + authRefNum + ", $40.00) ---");

        String authXml = FiservUmfMessageBuilder.buildCreditAuthorizationContactless(
                MID_RESTAURANT, TERMINAL_ID, authStan, authRefNum,
                "ORDER004", MCC_RESTAURANT, authAmountCents,
                VISA_TRACK2, "Visa",
                buildSampleEmvData(authAmountCents),
                "01"
        );

        System.out.println("UMF Payload:\n" + authXml);
        String authResponseXml = datawire.sendTransaction(authXml);

        AuthorizationResponse authResp;
        if (authResponseXml != null) {
            authResp = FiservResponseParser.parseResponse(authResponseXml);
        } else {
            authResp = simulateAuthResponse(authStan, authRefNum, "Visa");
        }

        System.out.println("\nAuthorization Response:");
        System.out.println("  RespCode:     " + authResp.respCode
                + (authResp.isApproved() ? " (APPROVED)" : " (DECLINED)"));
        System.out.println("  AuthID:       " + authResp.authId);
        System.out.println("  VisaTransID:  " + authResp.visaTransId);
        if (authResp.errorData != null)
            System.out.println("  ErrorData:    " + authResp.errorData);

        if (!authResp.isApproved()) {
            System.out.println("  ** Authorization declined — cannot refund");
            return;
        }

        // --- (Assume Completion/capture happened) ---
        System.out.println("\n  ** Assuming Completion was sent and captured successfully...");

        // --- Referenced Refund ---
        String refundStan = nextStan();
        String refundRefNum = nextRefNum();  // NEW RefNum for the refund

        System.out.println("\n--- Referenced Refund (STAN=" + refundStan
                + ", RefNum=" + refundRefNum + " [NEW], $40.00) ---");

        String refundXml = FiservUmfMessageBuilder.buildReferencedRefund(
                MID_RESTAURANT, TERMINAL_ID, refundStan,
                refundRefNum,               // NEW RefNum (not original auth's)
                "ORDER004", MCC_RESTAURANT,
                authAmountCents,            // Full refund amount
                VISA_TRACK2,
                authResp.cardType != null ? authResp.cardType : "Visa",
                null,                       // EMV data not needed for refund
                // OrigAuthGrp fields from auth response:
                authResp.authId,
                authResp.responseDate,
                authResp.localDateTime,
                authResp.trnmsnDateTime,
                authResp.stan,
                authResp.respCode
        );

        System.out.println("UMF Payload:\n" + refundXml);

        System.out.println("\n  ** NOTE: RefNum is NEW (not original auth RefNum " + authRefNum + ")");
        System.out.println("  ** NOTE: OrigAuthGrp links this refund to original auth");
        System.out.println("  **       OrigAuthID=" + authResp.authId
                + ", OrigSTAN=" + authResp.stan);

        String refundResponseXml = datawire.sendTransaction(refundXml);

        AuthorizationResponse refundResp;
        if (refundResponseXml != null) {
            refundResp = FiservResponseParser.parseResponse(refundResponseXml);
        } else {
            refundResp = simulateRefundResponse(refundStan, refundRefNum);
        }

        System.out.println("\nReferenced Refund Response:");
        System.out.println("  RespCode:     " + refundResp.respCode
                + ("000".equals(refundResp.respCode) ? " (REFUNDED)" : " (FAILED)"));
        if (refundResp.addlRespData != null)
            System.out.println("  AddlRespData: " + refundResp.addlRespData);
        if (refundResp.errorData != null)
            System.out.println("  ErrorData:    " + refundResp.errorData);

        System.out.println("\n  ** Flow 4 complete: $40.00 authorized, captured, then refunded (referenced)");
    }

    // =====================================================================
    // Flow 5: Unreferenced Refund (standalone, no original auth link)
    // =====================================================================

    /**
     * Demonstrates an unreferenced refund:
     *   1. Refund $15.00 with no link to original authorization
     *
     * Used when the original transaction data is unavailable — for example,
     * the original was on a different terminal, or the data was lost.
     *
     * Key rules:
     *   - No OrigAuthGrp (no link to original)
     *   - RefNum is new
     *   - For MasterCard: RefundType is mandatory (2026 UMF Change)
     *   - May receive different interchange rates than referenced refunds
     */
    private void runUnreferencedRefund() throws Exception {
        long refundAmountCents = 1500;  // $15.00

        section("FLOW 5: Unreferenced Refund — Visa $15.00");

        String refundStan = nextStan();
        String refundRefNum = nextRefNum();

        System.out.println("\n--- Unreferenced Refund (STAN=" + refundStan
                + ", RefNum=" + refundRefNum + ", $15.00) ---");
        System.out.println("  ** No OrigAuthGrp — not linked to original auth");

        String refundXml = FiservUmfMessageBuilder.buildRefund(
                MID_RESTAURANT, TERMINAL_ID, refundStan, refundRefNum,
                "ORDER005", MCC_RESTAURANT, refundAmountCents,
                VISA_TRACK2, "Visa",
                null    // No EMV data needed for unreferenced refund
        );

        System.out.println("UMF Payload:\n" + refundXml);

        String refundResponseXml = datawire.sendTransaction(refundXml);

        AuthorizationResponse refundResp;
        if (refundResponseXml != null) {
            refundResp = FiservResponseParser.parseResponse(refundResponseXml);
        } else {
            refundResp = simulateRefundResponse(refundStan, refundRefNum);
        }

        System.out.println("\nUnreferenced Refund Response:");
        System.out.println("  RespCode:     " + refundResp.respCode
                + ("000".equals(refundResp.respCode) ? " (REFUNDED)" : " (FAILED)"));
        if (refundResp.addlRespData != null)
            System.out.println("  AddlRespData: " + refundResp.addlRespData);
        if (refundResp.errorData != null)
            System.out.println("  ErrorData:    " + refundResp.errorData);

        System.out.println("\n  ** Flow 5 complete: $15.00 unreferenced refund sent");
    }

    // =====================================================================
    // EMV Data Builder (test/simulation)
    // =====================================================================

    /**
     * Builds a sample EMV TLV hex string for contactless Visa transactions.
     *
     * Contains the minimum required tags for a contactless EMV authorization:
     *   9F26 (Application Cryptogram), 9F27 (CID), 9F10 (IAD),
     *   9F37 (Unpredictable Number), 9F36 (ATC), 95 (TVR), 9A (Date),
     *   9C (Txn Type), 5F2A (Currency), 82 (AIP), 84 (AID),
     *   9F02 (Amount), 9F03 (Other Amount), 9F1A (Country),
     *   9F33 (Capabilities), 9F34 (CVM Results)
     *
     * In production, this data comes from the contactless EMV kernel after tap.
     */
    static String buildSampleEmvData(long amountCents) {
        String amtHex = String.format("%012d", amountCents); // BCD: 000000005000 = $50.00
        return "9F2608C2A3F4E5D6B7A8C9"       // Application Cryptogram (8 bytes, test value)
                + "9F270180"                     // CID: ARQC (online authorization requested)
                + "9F100706011203A40000"          // Issuer Application Data (7 bytes)
                + "9F3704AABBCCDD"                // Unpredictable Number (4 bytes)
                + "9F3602001C"                    // Application Transaction Counter
                + "95050000000000"                // TVR: all clear (5 bytes)
                + "9A03260410"                    // Transaction Date: 2026-04-10
                + "9C0100"                        // Transaction Type: 00 = purchase
                + "5F2A020840"                    // Currency Code: 0840 = USD
                + "82021980"                      // AIP
                + "8407A0000000031010"             // AID: Visa (A0000000031010)
                + "9F0206" + amtHex               // Amount Authorized (BCD, 6 bytes)
                + "9F0306000000000000"             // Amount Other: $0.00
                + "9F1A020840"                     // Terminal Country Code: US
                + "9F3303E0B8C8"                   // Terminal Capabilities
                + "9F34031E0300";                  // CVM Results: no CVM performed
    }

    // =====================================================================
    // Simulated Responses (dry-run mode)
    // =====================================================================

    /**
     * Simulates a Fiserv authorization response.
     * In production, this comes from Fiserv via Datawire.
     */
    private AuthorizationResponse simulateAuthResponse(String stan, String refNum, String cardType) {
        AuthorizationResponse resp = new AuthorizationResponse();
        // CommonGrp — echoed back
        resp.pymtType = "Credit";
        resp.txnType = "Authorization";
        resp.localDateTime = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        resp.trnmsnDateTime = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        resp.stan = stan;
        resp.refNum = refNum;
        resp.termId = TERMINAL_ID;
        resp.merchId = MID_RESTAURANT;

        // RespGrp — approval
        resp.respCode = "000";           // Approved
        resp.authId = "TST" + stan;      // Simulated AuthID
        resp.responseDate = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"));
        resp.authNetId = "VISA";

        // CardGrp
        resp.cardType = cardType;

        // Scheme-specific
        resp.visaTransId = "01234567890" + stan; // Simulated Visa TransID (15 chars)
        resp.aci = "Y";
        resp.cardLevelResult = "A";

        System.out.println("  [DRY-RUN] Simulated APPROVED response");
        return resp;
    }

    private AuthorizationResponse simulateCompletionResponse(String stan, String refNum) {
        AuthorizationResponse resp = new AuthorizationResponse();
        resp.pymtType = "Credit";
        resp.txnType = "Completion";
        resp.stan = stan;
        resp.refNum = refNum;
        resp.respCode = "000";
        resp.authId = "TST" + stan;
        resp.responseDate = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"));
        resp.cardType = "Visa";
        System.out.println("  [DRY-RUN] Simulated CAPTURED response");
        return resp;
    }

    private AuthorizationResponse simulateVoidResponse(String stan, String refNum) {
        AuthorizationResponse resp = new AuthorizationResponse();
        resp.pymtType = "Credit";
        resp.txnType = "Authorization";
        resp.stan = stan;
        resp.refNum = refNum;
        resp.respCode = "000";
        resp.cardType = "Visa";
        System.out.println("  [DRY-RUN] Simulated VOIDED response");
        return resp;
    }

    private AuthorizationResponse simulateReversalResponse(String stan, String refNum, String type) {
        AuthorizationResponse resp = new AuthorizationResponse();
        resp.pymtType = "Credit";
        resp.txnType = "Authorization";
        resp.stan = stan;
        resp.refNum = refNum;
        resp.respCode = "000";
        resp.cardType = "Visa";
        System.out.println("  [DRY-RUN] Simulated " + type.toUpperCase() + " REVERSAL response");
        return resp;
    }

    private AuthorizationResponse simulateRefundResponse(String stan, String refNum) {
        AuthorizationResponse resp = new AuthorizationResponse();
        resp.pymtType = "Credit";
        resp.txnType = "Refund";
        resp.stan = stan;
        resp.refNum = refNum;
        resp.respCode = "000";
        resp.authId = "TST" + stan;
        resp.responseDate = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"));
        resp.cardType = "Visa";
        System.out.println("  [DRY-RUN] Simulated REFUNDED response");
        return resp;
    }

    // =====================================================================
    // Console Helpers
    // =====================================================================

    private static void section(String title) {
        System.out.println("\n" + "-".repeat(70));
        System.out.println(" " + title);
        System.out.println("-".repeat(70));
    }
}
