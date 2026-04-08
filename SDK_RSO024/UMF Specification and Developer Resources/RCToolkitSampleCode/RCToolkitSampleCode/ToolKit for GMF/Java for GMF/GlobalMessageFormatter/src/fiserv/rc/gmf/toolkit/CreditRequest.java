package fiserv.rc.gmf.toolkit;

import java.io.Serializable;
import java.io.StringWriter;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import fiserv.rc.gmf.toolkit.proxy.*;

/* The below code will prepare Credit Sale transaction request object populating various 
 * transaction parameters. The parameter values used below are test data and should not used for
 * actual real-time authorization. 
 * */
public class CreditRequest implements Serializable {
	
	private static final long serialVersionUID = 1L;
	/* This class is generated from XSD file */
	GMFMessageVariants gmfmv = new GMFMessageVariants();
	/* This class is generated from XSD file */
	CreditRequestDetails creditReqDtl = new CreditRequestDetails();

	public CreditRequest() {
		/* Assigning value to the objects 
		 * This class CommonGrp is generated from XSD file.
		 * Transaction elements inside this common group will be present to all transactions.
		/* Common Group */
		CommonGrp cmnGrp = new CommonGrp();
		/* The payment type of the transaction. */
		cmnGrp.setPymtType(PymtTypeType.CREDIT);		
		/* The type of transaction being performed. */
		cmnGrp.setTxnType(TxnTypeType.AUTHORIZATION);
		/* The local date and time in which the transaction was performed. */
		cmnGrp.setLocalDateTime("20260128070707");
		/* The transmission date and time of the transaction (in GMT/UCT). */
		cmnGrp.setTrnmsnDateTime("20260128070707");
		/* A number assigned by the merchant to uniquely reference the transaction. 
         * This number must be unique within a day per Merchant ID per Terminal ID. */
		cmnGrp.setSTAN("100002");
		/* A number assigned by the merchant to uniquely reference a set of transactions. */
		cmnGrp.setRefNum("20260101012");
		/* A number assigned by the merchant to uniquely reference a transaction order sequence. */
		cmnGrp.setOrderNum("12345678");		
		/* An ID assigned by Fiserv, for the Third Party Processor or 
         * Software Vendor that generated the transaction. */
		cmnGrp.setTPPID(TestConst.REQUEST_TPPID);	

		/* A unique ID assigned to a terminal. */		
		cmnGrp.setTermID(TestConst.REQUEST_TERMID);	

		/* A unique ID assigned by Fiserv, to identify the Merchant. */		
		cmnGrp.setMerchID(TestConst.REQUEST_MERCHID);		

		/* An identifier used to indicate the terminal�s account number entry mode 
         * and authentication capability via the Point-of-Service. */
		cmnGrp.setPOSEntryMode("011");
		/* An identifier used to indicate the authorization conditions at the Point-of-Service (POS). */
		cmnGrp.setPOSCondCode("59");
		/* An identifier used to describe the type of terminal being used for the transaction. */
		cmnGrp.setTermCatCode("00");
		/* An identifier used to indicate the entry mode capability of the terminal. */
		cmnGrp.setTermEntryCapablt("04");
		/* The amount of the transaction. This may be an authorization amount, 
         * adjustment amount or a reversal amount based on the type of transaction. 
         * It is inclusive of all additional amounts. 
         * It is submitted in the currency represented by the Transaction Currency field.  
         * The field is overwritten in the response for a partial authorization. */
		cmnGrp.setTxnAmt("0000000868");
		/* The numeric currency of the Transaction Amount. */
		cmnGrp.setTxnCrncy("840");
		/* An indicator that describes the location of the terminal. */
		cmnGrp.setTermLocInd("0");
		/* Indicates whether or not the terminal has the capability to capture the card data. */
		cmnGrp.setCardCaptCap("1");
		/* Indicates Group ID. */
		cmnGrp.setGroupID(TestConst.REQUEST_GROUPID); 
		
		
		/*
		 * Assign the Common Group object to the property of CreditSaleRequest
		 * object
		 */
		creditReqDtl.setCommonGrp(cmnGrp);

		/* This class is generated from XSD file */
		/* Card Group */
		/* Populate values for Card Group */
		CardGrp cardGrp = new CardGrp();		
		/* The account number of the card for which the transaction is being performed. */
		cardGrp.setAcctNum("36185900055556");
		/* The expiration date of the card being used for the transaction. */ 
		cardGrp.setCardExpiryDate("20300430");
		/* An identifier used to indicate the card type. */
		cardGrp.setCardType(CardTypeType.DISCOVER);
		cardGrp.setCCVInd(CCVIndType.PRVDED);
		cardGrp.setCCVData("123");
		/*
		 * Assign the Card Group object to the property of CreditSaleRequest
		 * object
		 */
		creditReqDtl.setCardGrp(cardGrp);

		/* This class is generated from XSD file.*/
		/* Addtl Amount Group */
		/*  Populate values for Addtl Amount Group */
		AddtlAmtGrp addAmtGrp = new AddtlAmtGrp();
		/* An identifier used to indicate whether or not the 
         * terminal/software can support partial authorization approvals.  */ 
		addAmtGrp.setPartAuthrztnApprvlCapablt("1"); 

		/*
		 * Getting the reference object of the AddtlAmtGrp list and add the
		 * AddtlAmtGrp object to the list
		 */
		List<AddtlAmtGrp> addtlAmtGr = creditReqDtl.getAddtlAmtGrp();
		addtlAmtGr.add(addAmtGrp);
		
		
		
		
//		VisaGrp visaGrp = new VisaGrp();
//		/* ACI Type value*/
//		visaGrp.setACI("Y");
//		/*Visa BID value*/
//		visaGrp.setVisaBID("12365");
//		/*Visa AUAR value*/
//		visaGrp.setVisaAUAR("000000000000");
//		/*Visa TAX Amount capability indicator value*/
//		visaGrp.setTaxAmtCapablt("1");
//		
//		/*
//		 * Assign the Visa Group object to the property of CreditSaleRequest
//		 * object
//		 */
//		creditReqDtl.setVisaGrp(visaGrp);
		
				
		/* This class is generated from XSD file */
		/* ECommerce Group */
		/* Populate values for ECommerce Group */
		
		EcommGrp ecommGrp = new EcommGrp();	
		/*ECommerce Transaction Indicator capability indicator value*/
		ecommGrp.setEcommTxnInd("01");
		
		/*
		 * Assign the ECommerce Group object to the property of CreditSaleRequest
		 * object
		 */
		
		creditReqDtl.setEcommGrp(ecommGrp);
				
		/* This class is generated from XSD file.*/
		/* CustInfoGrp Group */
		/* Populate values for CustInfoGrp Group */
		CustInfoGrp custInfoGrp = new CustInfoGrp();
		/*Billing address of the card holder*/
		custInfoGrp.setAVSBillingAddr("1307 Walt Whitman road");
		/*Postal ZIP Code of the card holder*/
		custInfoGrp.setAVSBillingPostalCode("11747");
		
		/*
		 * Assign the CustInfoGrp Group object to the property of CreditSaleRequest
		 * object
		 */
		creditReqDtl.setCustInfoGrp(custInfoGrp);
		/* Add the credit request object to GMF message variant object */
		gmfmv.setCreditRequest(creditReqDtl);
	}
	
	/* Generate Client Ref Number in the format <STAN>|<TPPID>, right justified and left padded with "0" */
	public String GetClientRef()
	{
		String clientRef = "";
		
		CreditRequestDetails creditReq = gmfmv.getCreditRequest();
		clientRef = creditReq.getCommonGrp().getSTAN() + "|" + creditReq.getCommonGrp().getTPPID();	
		clientRef = "00" + clientRef;		
		
		return clientRef;
	}
	
	/* The below method will transform the transaction request object to an XML string in UTF-8 encoding.
	 * It will convert gmfmv object into serialized XML data which will be sent to Data wire.  
	 * */
	public String GetXMLData() {
		StringWriter stringWriter = new StringWriter();
		String returnValue = "";
		try {
			JAXBContext context = null;
			Marshaller marshaller = null;
			context = JAXBContext.newInstance(GMFMessageVariants.class);
			marshaller = context.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
			marshaller.marshal(gmfmv, stringWriter);

			returnValue = stringWriter.toString();
		} catch (JAXBException j) {
			System.out.println("GetXMLData Exception: " + j.toString());
			j.printStackTrace();
		} catch (Exception e) {
			System.out.println("GetXMLData Exception: " + e.toString());
			e.printStackTrace();
		}
		return returnValue;
	}
}
