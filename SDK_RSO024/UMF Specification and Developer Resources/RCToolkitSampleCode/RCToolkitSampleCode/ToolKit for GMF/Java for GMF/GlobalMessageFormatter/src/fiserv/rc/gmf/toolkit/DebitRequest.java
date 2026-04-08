package fiserv.rc.gmf.toolkit;

import java.io.Serializable;
import java.io.StringWriter;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import fiserv.rc.gmf.toolkit.proxy.*;

/* The below code will prepare Debit Sale transaction request object populating various 
 * transaction parameters. The parameter values used below are test data and should not used for
 * actual real-time authorization. 
 * */
public class DebitRequest implements Serializable {
	
	private static final long serialVersionUID = 1L;
	/* This class is generated from XSD file. */
	GMFMessageVariants gmfmv = new GMFMessageVariants();
	/* This class is generated from XSD file. It will create one Debit Request*/
	DebitRequestDetails debitReqDtl = new DebitRequestDetails();

	public DebitRequest() {
		/* Assigning value to the objects 
		 * This class CommonGrp is generated from XSD file.
		 * Transaction elements inside this common group will be present to all transactions.
		/* Common Group */
		CommonGrp cmnGrp = new CommonGrp();
		/* The payment type of the transaction. */
		cmnGrp.setPymtType(PymtTypeType.DEBIT);		
		/* The type of transaction being performed. */
		cmnGrp.setTxnType(TxnTypeType.SALE);
		/* The local date and time in which the transaction was performed. */
		cmnGrp.setLocalDateTime("20260128080808");
		/* The transmission date and time of the transaction (in GMT/UCT). */
		cmnGrp.setTrnmsnDateTime("20260128080808");
		/* A number assigned by the merchant to uniquely reference the transaction. 
         * This number must be unique within a day per Merchant ID per Terminal ID. */
		cmnGrp.setSTAN("100027");
		/* A number assigned by the merchant to uniquely reference a set of transactions. */
		cmnGrp.setRefNum("202601281234");
		/* A number assigned by the merchant to uniquely reference a transaction order sequence. */
		cmnGrp.setOrderNum("202601281234");		
		/* An ID assigned by Fiserv, for the Third Party Processor or 
         * Software Vendor that generated the transaction. */
		cmnGrp.setTPPID(TestConst.REQUEST_TPPID);		//TPP ID value will be assigned by Fiserv

		/* A unique ID assigned to a terminal. */
		cmnGrp.setTermID(TestConst.REQUEST_TERMID);	// Terminal ID value will be assigned by Fiserv

		/* A unique ID assigned by Fiserv, to identify the Merchant. */
		cmnGrp.setMerchID(TestConst.REQUEST_MERCHID);		// Merchant ID value will be assigned by Fiserv	
		
		//cmnGrp.setMerchID("XXXXX0000000000");		// Merchant ID value will be assigned by Fiserv	
		/* An identifier used to indicate the terminal�s account number entry mode 
         * and authentication capability via the Point-of-Service. */
		cmnGrp.setPOSEntryMode("901");
		/* An identifier used to indicate the authorization conditions at the Point-of-Service (POS). */
		cmnGrp.setPOSCondCode("00");
		/* An identifier used to describe the type of terminal being used for the transaction. */
		cmnGrp.setTermCatCode("01");
		/* An identifier used to indicate the entry mode capability of the terminal. */
		cmnGrp.setTermEntryCapablt("04");
		/* The amount of the transaction. This may be an authorization amount, 
         * adjustment amount or a reversal amount based on the type of transaction. 
         * It is inclusive of all additional amounts. 
         * It is submitted in the currency represented by the Transaction Currency field.  
         * The field is overwritten in the response for a partial authorization. */
		cmnGrp.setTxnAmt("1061");

		/* The numeric currency of the Transaction Amount. */
		cmnGrp.setTxnCrncy("840");
		/* An indicator that describes the location of the terminal. */
		cmnGrp.setTermLocInd("0");
		/* Indicates whether or not the terminal has the capability to capture the card data. */
		cmnGrp.setCardCaptCap("1");
		/* Indicates Group ID. */
		cmnGrp.setGroupID(TestConst.REQUEST_GROUPID); //Group ID value will be assigned by Fiserv.
		
		/*
		 * Assign the Common Group object to the property of DebitSaleRequest
		 * object
		 */
		debitReqDtl.setCommonGrp(cmnGrp);

		/* This class is generated from UMF_Schema_V1.1.14.xsd.*/
		/* Card Group */
		/* Populate values for Card Group */
		CardGrp cardGrp = new CardGrp();		
		/* The track-2 data of the card for which the transaction is being performed. */
		cardGrp.setTrack2Data(TestConst.REQUEST_DEBIT_TRACK2);
		
		/*
		 * Assign the Card Group object to the property of DebitSaleRequest
		 * object
		 */
		debitReqDtl.setCardGrp(cardGrp);

		/* This class is generated from UMF_Schema_V1.1.14.xsd.*/
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
		List<AddtlAmtGrp> addtlAmtGr = debitReqDtl.getAddtlAmtGrp();
		addtlAmtGr.add(addAmtGrp);
		
		
		/**/
		/*OfferGrp offrgrp = new OfferGrp();
		offrgrp.setPOSOfferCap("");
		/**/
		
		/* This class is generated from XSD file.*/
		/* PIN Group */
		/* Populate values for PIN Group */
		PINGrp pinGrp = new PINGrp();
		/* PIN data value*/
		pinGrp.setPINData(TestConst.REQUEST_DEBIT_PINDATA);
		
		/*Serial Number data value*/
		pinGrp.setKeySerialNumData(TestConst.REQUEST_DEBIT_KEYSERIALNUMDATA);		
		
		/*
		 * Assign the PIN Group object to the property of DebitSaleRequest
		 * object
		 */
		debitReqDtl.setPINGrp(pinGrp);
				
		/* Add the Debit request object to GMF message variant object */
		gmfmv.setDebitRequest(debitReqDtl);
	}
	/* Generate Client Ref Number in the format <STAN>|<TPPID>, right justified and left padded with "0" */
	public String GetClientRef()
	{
		String clientRef = "";
		
		DebitRequestDetails debitReq = gmfmv.getDebitRequest();
		clientRef = debitReq.getCommonGrp().getSTAN() + "|" + debitReq.getCommonGrp().getTPPID();	
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
