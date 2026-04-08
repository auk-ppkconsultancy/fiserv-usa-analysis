using System;
using System.Xml.Serialization;
using System.IO;
using System.Xml;

/* The below code will prepare Credit transaction request object populating various 
 * transaction parameters. The parameter values used below are test data and should not used for
 * actual real-time authorization. 
 * */

namespace GlobalMessageFormatter
{
    public class CreditSaleRequest
    {
        GMFMessageVariants gmfMsgVar = new GMFMessageVariants();
        CreditRequestDetails creditReq = new CreditRequestDetails();

        public CreditSaleRequest()
        {
            /* Based on the GMF XSD file, fields that are mandatory or related to this transaction should be populated.*/
            #region Common Group
            /* Populate values for Common Group */
            CommonGrp cmnGrp = new CommonGrp();

            /* The payment type of the transaction. */
            cmnGrp.PymtType = PymtTypeType.Credit;
            cmnGrp.PymtTypeSpecified = true;

            /* The type of transaction being performed. */
            cmnGrp.TxnType = TxnTypeType.Sale;
            cmnGrp.TxnTypeSpecified = true;

            /* The local date and time in which the transaction was performed. */
            cmnGrp.LocalDateTime = "20260114042651";

            /* The transmission date and time of the transaction (in GMT/UCT). */
            cmnGrp.TrnmsnDateTime = "20260114042651";

            /* A number assigned by the merchant to uniquely reference the transaction. 
             * This number must be unique within a day per Merchant ID per Terminal ID. */
            cmnGrp.STAN = "100003";

            /* A number assigned by the merchant to uniquely reference a set of transactions. */             
            cmnGrp.RefNum = "15000150150";

            /* A number assigned by the merchant to uniquely reference a transaction order sequence. */
            cmnGrp.OrderNum = "12000500";


            /* An ID assigned by Fiserv, for the Third Party Processor or 
             * Software Vendor that generated the transaction. */
            cmnGrp.TPPID = TestConst.REQUEST_TPPID;	

            /* A unique ID assigned to a terminal. */
            cmnGrp.TermID = TestConst.REQUEST_TERMID;		

            /* A unique ID assigned by Fiserv, to identify the Merchant. */
            cmnGrp.MerchID = TestConst.REQUEST_MERCHID;		

            /* An identifier used to indicate the terminal’s account number entry mode 
             * and authentication capability via the Point-of-Service. */
            cmnGrp.POSEntryMode = "011";

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
            cmnGrp.TxnAmt = "0000083184";

            /* The numeric currency of the Transaction Amount. */
            cmnGrp.TxnCrncy = "840";

            /* An indicator that describes the location of the terminal. */
            cmnGrp.TermLocInd = TermLocIndType.Item1;
            cmnGrp.TermLocIndSpecified = true;

            /* Indicates whether or not the terminal has the capability to capture the card data. */
            cmnGrp.CardCaptCap = CardCaptCapType.Item1;
            cmnGrp.CardCaptCapSpecified = true;

            /* Indicates Group ID. */
            cmnGrp.GroupID = TestConst.REQUEST_GROUPID; 

            creditReq.CommonGrp = cmnGrp;
            #endregion

            #region Card Group
            /* Populate values for Card Group */
            CardGrp crdGrp = new CardGrp();

            /* The account number of the card for which the transaction is being performed. */
            crdGrp.AcctNum = "5424180273333333";

            /* An identifier used to indicate the card type. */
            crdGrp.CardType = CardTypeType.MasterCard;
            crdGrp.CardTypeSpecified = true;

            /* The expiration date of the card being used for the transaction. */
            crdGrp.CardExpiryDate = "20300430";

            creditReq.CardGrp = crdGrp;
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
            AddtlAmtGrp[] addAmtGrpArr = new AddtlAmtGrp[1];
            addAmtGrpArr[0] = addAmtGrp;

            creditReq.AddtlAmtGrp = addAmtGrpArr;            
            #endregion

            #region MasterCard Group
            /*  Populate values for MasterCard Group */
            MCGrp masterCardGrp = new MCGrp();
            masterCardGrp.FinAuthInd = FinAuthIndType.Item1;
            masterCardGrp.FinAuthIndSpecified = true;
            
            creditReq.Item = masterCardGrp;            
            #endregion

            #region Customer info Group
            /*Create custmoer info group*/
            CustInfoGrp custinfo = new CustInfoGrp();
            /* Set card holder billing address*/
            custinfo.AVSBillingAddr = "1307 Walt Whitman road";
            /*Set card holder billing postal code.*/
            custinfo.AVSBillingPostalCode = "11747";

            creditReq.CustInfoGrp = custinfo;
            #endregion
            
            /* Add the data populated object to GMF message variant object */
            gmfMsgVar.Item = creditReq;
        }

        /* Generate Client Ref Number in the format <STAN>|<TPPID>, right justified and left padded with "0" */
        public string GetClientRef()
        {
            string clientRef = string.Empty;
            
            CreditRequestDetails creditReq = gmfMsgVar.Item as CreditRequestDetails;
            clientRef = creditReq.CommonGrp.STAN + "|" + creditReq.CommonGrp.TPPID;
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

