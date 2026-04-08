package fiserv.rc.gmf.toolkit;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;

import dw.securetransport.rcservice.xml.ObjectFactory;
import dw.securetransport.rcservice.xml.PayloadType;
import dw.securetransport.rcservice.xml.ReqClientIDType;
import dw.securetransport.rcservice.xml.Request;
import dw.securetransport.rcservice.xml.Response;
import dw.securetransport.rcservice.xml.TransactionType;

import fiserv.rc.gmf.toolkit.TestConst;

/* The below class shows the way to send transaction request data to data wire,
 * and receive response from data wire using HTTP POST protocol.  
 * */
public class HTTPPostHandler {

	/* The below method will take the XML request and returns the XML response received from Data wire.
	 * */
	@SuppressWarnings("deprecation")
	public String SendMessage(String gmfrequest, String clientRef)
	{
		/*Response that will be returned.*/
		String response = "";
		/* Create the instance of the Request that is a class generated from the Rapid connect Transaction 
		 * Service Schema file [rc.xsd]*/
		ObjectFactory factory = new ObjectFactory();
		Request gmfTransactionRequest = factory.createRequest();
		
		/* Create the instance of the TransactionType that is a class generated from the Rapid connect Transaction 
		 * Service Schema file [rc.xsd]*/
		TransactionType transactionType = factory.createTransactionType();
		/*Set the service ID parameter.*/
		transactionType.setServiceID("160");
		
		/* Create the instance of PayloadType that is a class generated from the Rapid connect Transaction 
		 * Service Schema file [rc.xsd]*/
		PayloadType payloadType = new PayloadType();
		/* Set encoding type*/
		payloadType.setEncoding("cdata");
		/* Assign the XML request data as the pay load value*/
		payloadType.setValue(gmfrequest);
		/* Set transaction type pay load value*/
		transactionType.setPayload(payloadType);
		/* Assign transaction type object to the transaction request.*/
		gmfTransactionRequest.setTransaction(transactionType);
		/* Create the instance of ReqClientIDType that is a class generated from the Rapid connect Transaction 
		 * Service Schema file [rc.xsd]*/
		ReqClientIDType reqClientIDType = new ReqClientIDType();
		/* Set the App parameter value*/	
		reqClientIDType.setApp("RAPIDCONNECTVXN");
		/* Set the Auth parameter value*/
		reqClientIDType.setAuth(TestConst.HTTP_AUTHID);		

		//reqClientIDType.setAuth("XXXXX0000000000|00000000");		
		/* Set the clientRef value*/
		reqClientIDType.setClientRef(clientRef); //give value later
		/* Set the DID value*/
		reqClientIDType.setDID(TestConst.HTTP_DID);				
		
		/*Assign ReqClientID object value to transaction request*/
		gmfTransactionRequest.setReqClientID(reqClientIDType);
		/*Set client timeout value*/ 
		gmfTransactionRequest.setClientTimeout(new BigInteger("30"));
		/*Set version value*/
		gmfTransactionRequest.setVersion("3");
		
		JAXBContext jaxBConText;
		String gmffomattedRequest = "";
		//Transform the gmfTransactionRequest object into XML string.
		try {
				jaxBConText = JAXBContext
						.newInstance("dw.securetransport.rcservice.xml");
		
			StringWriter strWriter = new StringWriter();
	
			Marshaller jaxbmarshaller = jaxBConText.createMarshaller();
			jaxbmarshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
	
			jaxbmarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			jaxbmarshaller.marshal(gmfTransactionRequest, strWriter);
			gmffomattedRequest = strWriter.toString();
		}
		catch (JAXBException e) {			
			System.out.println("SendMessage Exception: " + e.toString());
			e.printStackTrace();
		}
		
		/* URL that will consume the transaction request.*/
		final String postURL = "https://stg.dw.us.fdcnet.biz/rc";
		/*Instantiate the POST method*/
		final PostMethod post = new PostMethod(postURL); 
		/*Instantiate the HTTP client*/														
		final HttpClient httpclient = new HttpClient();		
		/*Set various parameters of HTTP requet header*/
		post.setRequestHeader("User-Agent", "TRR-Formatter");
		post.setRequestHeader("Host", postURL);
		post.setRequestHeader("Connection", "Keep-Alive");
		post.setRequestHeader("Cache-Control", "no-cache");
		post.setRequestHeader("Content-Length",
		Integer.toString(gmffomattedRequest.length()));
		post.setRequestHeader("Content-type", "text/xml");
		/*Set the request body with the XML transaction request*/
		post.setRequestBody(gmffomattedRequest);

		try {
			/*Call executeMethod to post the data to the designated URL.*/
			final int returnCode = httpclient.executeMethod(post);
	
			/*Do error handling and parse the response before returning.*/
			if (returnCode == HttpStatus.SC_NOT_IMPLEMENTED) {
				System.err
				.println("The Post method is not implemented by this URI");			
			} else if (returnCode == HttpStatus.SC_OK) {
							
				response = createXMLHttpResponse(post
									.getResponseBodyAsString());
				}
			} 
			catch (IOException e) {
				System.out.println("SendMessage Exception: " + e.toString());					 
			} finally {
				post.releaseConnection();
			}
		//Return the response
		return response;
	}
	/*The below method takes XML response received after post method execution.
	 * and build Response object; then extract pay load data that is actual 
	 * transaction response received from Data Wire.*/
	public String createXMLHttpResponse(String xml)
	{		
		ClassLoader classLoader = dw.securetransport.rcservice.xml.ObjectFactory.class
				.getClassLoader();
		String payload = null;
		try {
			JAXBContext context = JAXBContext.newInstance(
					"dw.securetransport.rcservice.xml", classLoader);
			Unmarshaller jaxbUnmarshaller = context.createUnmarshaller();
			StringReader responseReader = new StringReader(xml);
			/*Un-marshal the XML response and build Response object.*/
			Response response = (Response) jaxbUnmarshaller
					.unmarshal(responseReader);
			if (response!=null && response.getStatus() != null
					&& response.getStatus().getStatusCode() != null) {
				if (response.getStatus().getStatusCode().equalsIgnoreCase("OK")) {
					if (response.getTransactionResponse() != null
							&& response.getTransactionResponse().getPayload() != null
							&& response.getTransactionResponse().getPayload()
									.getEncoding() != null) {
						if (response.getTransactionResponse().getPayload()
								.getEncoding().equals("cdata")) {
							/*Extract pay load data that is the transaction response for cdata type encoded message.*/
							payload = response.getTransactionResponse()
									.getPayload().getValue();
						} else if (response.getTransactionResponse()
								.getPayload().getEncoding()
								.equalsIgnoreCase("xml_escape")) {
							/*Extract pay load data that is the transaction response for xml_escape type encoded message.*/
							payload = response.getTransactionResponse()
									.getPayload().getValue()
									.replaceAll("&gt;", ">")
									.replaceAll("&lt;", "<")
									.replaceAll("&amp;", "&");
						}
					}
				}
			} else {
				
			}
		} catch (JAXBException e) {
			System.out.println("createXMLHttpResponse Exception: " + e.toString());
		}		
		//Return the transaction response.
		return payload;
	}

}
