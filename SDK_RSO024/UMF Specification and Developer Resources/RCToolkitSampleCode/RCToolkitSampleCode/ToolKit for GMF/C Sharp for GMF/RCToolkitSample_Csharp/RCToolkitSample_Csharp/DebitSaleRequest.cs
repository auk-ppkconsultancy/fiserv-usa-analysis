using System;
using System.Xml.Serialization;
using System.IO;
using System.Xml;

/* The below code will prepare Debit Sale transaction request object populating various 
 * transaction parameters. The parameter values used below are test data and should not used for
 * actual real-time authorization. 
 * */
namespace GlobalMessageFormatter
{
    public class DebitSaleRequest
    {        
        GMFMessageVariants gmfMsgVar = new GMFMessageVariants();
        DebitRequestDetails debitReq = new DebitRequestDetails();

        public DebitSaleRequest()
        {
            /* Based on the GMF XSD file, fields that are mandatory or related to this transaction should be populated.*/

            #region Common Group
            /* Populate values for Common Group */
            CommonGrp cmnGrp = new CommonGrp();

            /* The payment type of the transaction. */
            cmnGrp.PymtType = PymtTypeType.Debit;            
            cmnGrp.PymtTypeSpecified = true;

            /* The type of transaction being performed. */
            cmnGrp.TxnType = TxnTypeType.Sale;
            cmnGrp.TxnTypeSpecified = true;

            /* The local date and time in which the transaction was performed. */
            cmnGrp.LocalDateTime = "20260106050055";

            /* The transmission date and time of the transaction (in GMT/UCT). */
            cmnGrp.TrnmsnDateTime = "20260106050055";

            /* A number assigned by the merchant to uniquely reference the transaction. 
             * This number must be unique within a day per Merchant ID per Terminal ID. */
            cmnGrp.STAN = "100027";

            /* A number assigned by the merchant to uniquely reference a set of transactions. 
             * sThis number must be unique within a day for a given Merchant ID/ Terminal ID. */
            cmnGrp.RefNum = "112233455625";

            /* Order number of the transaction
             */
            cmnGrp.OrderNum = "555566678914";

            /* An ID assigned by Fiserv, for the Third Party Processor or 
             * Software Vendor that generated the transaction. */
            cmnGrp.TPPID = TestConst.REQUEST_TPPID;

            /* A unique ID assigned to a terminal. */
            cmnGrp.TermID = TestConst.REQUEST_TERMID;

            /* A unique ID assigned by Fiserv, to identify the Merchant. */
            cmnGrp.MerchID = TestConst.REQUEST_MERCHID;

            /* An identifier used to indicate the terminal’s account number entry mode 
             * and authentication capability via the Point-of-Service. */
            cmnGrp.POSEntryMode = "901";

            /* An identifier used to indicate the authorization conditions at the Point-of-Service (POS). */
            cmnGrp.POSCondCode = POSCondCodeType.Item00;
            cmnGrp.POSCondCodeSpecified = true;

            /* An identifier used to describe the type of terminal being used for the transaction. */
            cmnGrp.TermCatCode = TermCatCodeType.Item01;
            cmnGrp.TermCatCodeSpecified = true;

            /* An identifier used to indicate the entry mode capability of the terminal. */
            cmnGrp.TermEntryCapablt = TermEntryCapabltType.Item04;
            cmnGrp.TermEntryCapabltSpecified = true;

            /* The amount of the transaction. This may be an authorization amount, 
             * adjustment amount or a reversal amount based on the type of transaction. 
             * It is inclusive of all additional amounts. 
             * It is submitted in the currency represented by the Transaction Currency field.  
             * The field is overwritten in the response for a partial authorization. */
            cmnGrp.TxnAmt = "000000002400";

            /* The numeric currency of the Transaction Amount. */
            cmnGrp.TxnCrncy = "840";

            /* An indicator that describes the location of the terminal. */
            cmnGrp.TermLocInd = TermLocIndType.Item0;
            cmnGrp.TermLocIndSpecified = true;

            /* Indicates whether or not the terminal has the capability to capture the card data. */
            cmnGrp.CardCaptCap = CardCaptCapType.Item1;
            cmnGrp.CardCaptCapSpecified = true;

            /* Indicates Group ID. */
            cmnGrp.GroupID = TestConst.REQUEST_GROUPID;

            debitReq.CommonGrp = cmnGrp;
            #endregion

            #region Card Group
            /* Populate values for Card Group */
            CardGrp crdGrp = new CardGrp();
            /*Track 2 data*/
            crdGrp.Track2Data = "4017779999999011=30041011000013345678";
            debitReq.CardGrp = crdGrp;
            #endregion

            #region PIN Group
            /* Populate values for PIN Group */
            PINGrp pinGroup = new PINGrp();

            /* The PIN Data for the Debit or EBT transaction being submitted.
             * HEXADecimal value need to be entered. */
            pinGroup.PINData = TestConst.REQUEST_DEBIT_PINDATA;

            /* Provides the initialization vector for DUKPT PIN Debit and EBT transactions. */
            pinGroup.KeySerialNumData = TestConst.REQUEST_DEBIT_KEYSERIALNUMDATA;

            debitReq.PINGrp = pinGroup;
            #endregion

            #region Additional Amount Group
            /*  Populate values for Additional Amount Group */
            AddtlAmtGrp addAmtGrp = new AddtlAmtGrp();

            /* An identifier used to indicate whether or not the 
             * terminal/software can support partial authorization approvals.  */
            addAmtGrp.PartAuthrztnApprvlCapablt = PartAuthrztnApprvlCapabltType.Item1;
            addAmtGrp.PartAuthrztnApprvlCapabltSpecified = true;

            /* Creating a generic array of Additional Amount 
             * Group type to sent the data to as an array */
            AddtlAmtGrp[] addAmtGrpArr = new AddtlAmtGrp[2];
            addAmtGrpArr[0] = addAmtGrp;

            debitReq.AddtlAmtGrp = addAmtGrpArr;
            #endregion

            /* Add the data populated object to GMF message variant object */
            gmfMsgVar.Item = debitReq;
        }

        /* Generate Client Ref Number in the format <STAN>|<TPPID>, right justified and left padded with "0" */
        public string GetClientRef()
        {
            string clientRef = string.Empty;

            DebitRequestDetails debitReq = gmfMsgVar.Item as DebitRequestDetails;
            clientRef = debitReq.CommonGrp.STAN + "|" + debitReq.CommonGrp.TPPID;
            clientRef = "00" + clientRef;

            return clientRef;
        }

        /* The method will convert the GMF transaction object into an XML string */
        public String GetXMLData()
        {
            string xmlString = null;
            MemoryStream memoryStream = new MemoryStream();
            XmlSerializer xs = new XmlSerializer(gmfMsgVar.GetType());
            XmlTextWriter xmlTextWriter = new XmlTextWriter(memoryStream, System.Text.Encoding.UTF8);
            xs.Serialize(xmlTextWriter, gmfMsgVar);
            memoryStream = (MemoryStream)xmlTextWriter.BaseStream;
            System.Text.UTF8Encoding encoding = new System.Text.UTF8Encoding();
            xmlString = encoding.GetString(memoryStream.ToArray());
            xmlString = xmlString.Substring(1, xmlString.Length - 1);
            return xmlString;
        }        
    }
}
