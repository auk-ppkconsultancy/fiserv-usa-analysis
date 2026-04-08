package fiserv.rc.gmf.toolkit;
import dw.socket.*;

/* This below program demonstrates the procedure of performing various payment transactions
 * through Datawire using different protocols SOAP, HTTP POST or TCP/IP. Please use either SOAP 
 * or HTTP POST  protocol to process transaction through Datawire over the Internet.  
 * TCP/IP is to be used for VPN or leased line.
 */
public class GMFClient {

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		/*Transaction request in XML format */
		String requestString = "";
		
		/*Transaction response in XML format received from Data wire */
		String responseString = "";
		
		/*Create Authorization request for a sample Credit Sale transaction.*/
		CreditRequest creditSaleReq = new CreditRequest();
		
		/*Generate Client Ref Number in the format <STAN>|<TPPID>, right justified and left padded with "0" */
		String clientRef = creditSaleReq.GetClientRef();
		
		requestString = creditSaleReq.GetXMLData();		
		requestString = requestString.replaceAll("gmfMessageVariants", "GMF");
		System.out.println("GMF Credit Request == "+requestString);
		
		//Send data using HTTP POST protocol
		try
		{
			responseString = new HTTPPostHandler().SendMessage(requestString, clientRef);
		}
		catch(Exception e)
		{
			System.out.println("HTTP Exception: " + e.toString());
		}
		System.out.println("Successful HTTP POST Credit response: " + "\n" + responseString + "\n");		
		//Send data using SOAP protocol 
		try
		{
			responseString = new SOAPHandler().SendMessage(requestString, clientRef);	
		}
		catch(Exception e)
		{
			System.out.println( "SOAP Exception: " + e.toString());
		}
		System.out.println("Successful SOAP Credit response: " + "\n"  + responseString + "\n");
		
		//Send data using TCP/IP		
		try
		{
			responseString = new TCPIPHandler().SendMessage(requestString);	
		}
		catch(Exception e)
		{
			System.out.println( "TCP/IP Exception: " + e.toString());
		}
		System.out.println("Successful TCP/IP Credit response: " + "\n"  + responseString + "\n");
		
		/*Create Authorization request for a sample Debit Sale transaction.*/
		DebitRequest debitSaleReq = new DebitRequest();
		
		/*Generate Client Ref Number in the format <STAN>|<TPPID>, right justified and left padded with "0" */
		clientRef = debitSaleReq.GetClientRef();
		
		requestString = debitSaleReq.GetXMLData();		
		requestString = requestString.replaceAll("gmfMessageVariants", "GMF");
		System.out.println("GMF Debit Request == "+requestString);
		//Send data using HTTP POST protocol		
		try
		{
			responseString = new HTTPPostHandler().SendMessage(requestString, clientRef);
		}
		catch(Exception e)
		{
			System.out.println("HTTP Exception: " + e.toString());
		}
		System.out.println("Successful HTTP POST Debit response: " + "\n" + responseString + "\n");
		
		//Send data using SOAP protocol		
		try
		{
			responseString = new SOAPHandler().SendMessage(requestString, clientRef);
		}
		catch(Exception e)
		{
			System.out.println("SOAP Exception: " + e.toString());
		}
		System.out.println("Successful SOAP Debit response: " + responseString + "\n");
		
		//Send data using TCP/IP		
		try
		{
			responseString = new TCPIPHandler().SendMessage(requestString);	
		}
		catch(Exception e)
		{
			System.out.println( "TCP/IP Exception: " + e.toString());
		}
		System.out.println("Successful TCP/IP Debit response: " + "\n"  + responseString + "\n");
		
		System.out.println("Exit Java toolkit sample code... " );
	}
}