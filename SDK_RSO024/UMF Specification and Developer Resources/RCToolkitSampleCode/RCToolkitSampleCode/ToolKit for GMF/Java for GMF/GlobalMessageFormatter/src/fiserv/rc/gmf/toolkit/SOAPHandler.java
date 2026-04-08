package fiserv.rc.gmf.toolkit;

import dw.securetransport.rcservice.soap.PayloadType;
import dw.securetransport.rcservice.soap.RcPortType;
import dw.securetransport.rcservice.soap.RcService;
import dw.securetransport.rcservice.soap.ReqClientIDType;
import dw.securetransport.rcservice.soap.RequestType;
import dw.securetransport.rcservice.soap.ResponseType;
import dw.securetransport.rcservice.soap.TransactionType;
import java.math.BigInteger;
import javax.xml.ws.BindingProvider;
/* The below class shows the way to send transaction request data to data wire,
 * and receive response from data wire using SOAP protocol.  
 * */
public class SOAPHandler {
	
	/* The below method will take the XML request and returns the XML response received from Data wire.
	 * */
	public String SendMessage(String gmfrequest, String clientRef)
	{
		/* Create the instance of the RequestType that is a class generated from the Rapid connect Transaction 
		 * Service WSDL file [rc.wsdl]*/
		RequestType requestType = new RequestType();
		/*Set client timeout value of request object.*/
		requestType.setClientTimeout(new BigInteger("30"));
		
		/* Create the instance of the ReqClientIDType that is a class generated from the Rapid connect Transaction 
		 * Service WSDL file [rc.wsdl]*/
		ReqClientIDType reqClientIDType = new ReqClientIDType();
		/*Set App parameter value.*/
		reqClientIDType.setApp("RAPIDCONNECTVXN");
		/*Set Auth parameter value.*/
		reqClientIDType.setAuth(TestConst.HTTP_AUTHID);	
		/*Set clientRef parameter value.*/
		reqClientIDType.setClientRef(clientRef);
		/*Set DID parameter value.*/
		reqClientIDType.setDID(TestConst.HTTP_DID);				
		/*Assign ReqClientID value to the requesttype object*/
		requestType.setReqClientID(reqClientIDType);
		/* Create the instance of the TransactionType that is a class generated from the Rapid connect Transaction 
		 * Service WSDL file [rc.wsdl]*/
		TransactionType transactionType = new TransactionType();
		/* Create the instance of the PayloadType that is a class generated from the Rapid connect Transaction 
		 * Service WSDL file [rc.wsdl]*/
		PayloadType payloadType = new PayloadType();
		/*Set encoding of the pay load data- the request XML data*/
		payloadType.setEncoding("cdata");
		/*Set the XML request value as pay load */
		payloadType.setValue(gmfrequest); //Set payload - actual xml request
		/*Set the pay load type*/
		transactionType.setPayload(payloadType);
		/*Set service ID*/
		transactionType.setServiceID("160");
		/*Set transaction type*/
		requestType.setTransaction(transactionType);
		/*Set version number of the request*/
		requestType.setVersion("3");
		
		/*The response to be returned.*/
		String gmfResponse = null;
		/* Create the instance of the RcService that is a class generated from the Rapid connect Transaction 
		 * Service WSDL file [rc.wsdl]*/
		RcService rcService = new RcService();
		/* Create the instance of the RcPortType that is a class generated from the Rapid connect Transaction 
		 * Service WSDL file [rc.wsdl]*/
		RcPortType port = rcService.getRcServicePort();
		/*The URL that will receive the XML request data.*/ 
		String endPointURL = "https://stg.dw.us.fdcnet.biz/rc";
		/*Bind the URL using Binding Provider*/
		((BindingProvider) port).getRequestContext().put(
				BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endPointURL);

		/*Perform actual send operation for the request.*/
		ResponseType responseType = port.rcTransaction(requestType);
		
		/*Parse the response received from the URL*/
		if (responseType != null && responseType.getStatus() != null
				&& responseType.getStatus().getStatusCode() != null) {
			if (responseType.getStatus().getStatusCode().equals("OK")) {
				if (responseType.getTransactionResponse() != null
						&& responseType.getTransactionResponse().getPayload() != null
						&& responseType.getTransactionResponse().getPayload()
								.getEncoding() != null) {
					if (responseType.getTransactionResponse().getPayload()
							.getEncoding().equals("cdata")) {
						/*Extract pay load - the response from data wire for cdata encoding.*/
						gmfResponse = responseType.getTransactionResponse()
								.getPayload().getValue();
					} else if (responseType.getTransactionResponse()
							.getPayload().getEncoding()
							.equalsIgnoreCase("xml_escape")) {
						/*Extract pay load - the response from data wire for mxml_escape encoding.*/			
						gmfResponse = responseType.getTransactionResponse()
								.getPayload().getValue()
								.replaceAll("&gt;", ">")
								.replaceAll("&lt;", "<")
								.replaceAll("&amp;", "&");
					}
				}
			}
		} else {
			
		}
		//Return the response.
		return gmfResponse;		
	}

}
