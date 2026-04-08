using System;
using System.Collections.Generic;
using System.Text;
using Fiserv.RapidConnect.Datawire.Soap;
using System.Net;

/* The below class shows the way to send transaction request data to data wire,
 * and receive response from data wire using SOAP protocol.  
 * */
namespace GlobalMessageFormatter
{
    class SoapHandler
    {
        public SoapHandler()
        {
        }
        /* The below method will take the XML request and returns the XML response received from Data wire.
	     * */
        public string SendMessage(string gmfRequest, string clientRef)
        {
            string response = "";
            /* Create the instance of the RequestType that is a class generated from the Rapid connect Transaction 
		     * Service WSDL file [rc.wsdl]*/
            RequestType requestType = new RequestType();
            /* Set Client timeout*/
            requestType.ClientTimeout = "30";
            /* Create the instance of the RequestType that is a class generated from the Rapid connect Transaction 
		     * Service WSDL file [rc.wsdl]*/
            ReqClientIDType reqClientIDType = new ReqClientIDType();
            /* Set App value*/
            reqClientIDType.App = "RAPIDCONNECTVXN";
            /* Set Auth value*/
            reqClientIDType.Auth = TestConst.HTTP_AUTHID; 
            /* Set clientRef value*/
            reqClientIDType.ClientRef = clientRef;
            /* Set DID value*/
            reqClientIDType.DID = TestConst.HTTP_DID; 
            /* Set requestclienttype ojbect to request type*/
            requestType.ReqClientID = reqClientIDType;

            /* Create the instance of the TransactionType that is a class generated from the Rapid connect Transaction 
		     * Service WSDL file [rc.wsdl]*/
            TransactionType transactionType = new TransactionType();
            /* Create the instance of the PayloadType that is a class generated from the Rapid connect Transaction 
		     * Service WSDL file [rc.wsdl]*/
            PayloadType payloadType = new PayloadType();
            /* Set pay load data*/
            payloadType.Encoding = PayloadTypeEncoding.cdata;
            /* Set pay load type as the actual XML request*/
            payloadType.Value = gmfRequest; //Set payload - actual xml request
            /*set pay load of the transaction type object */
            transactionType.Payload = payloadType;
            /* Set Service ID of the tranasction type object*/
            transactionType.ServiceID = "160";
            /* Set transction value of the requet type object*/
            requestType.Transaction = transactionType;
            /* Set version of the request type object */
            requestType.Version  = "3";

            // using System.Net;
            ServicePointManager.Expect100Continue = true;
            //ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls;
            ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072;

            String gmfResponse = null;
            /* Create the instance of the rcService that is a class generated from the Rapid connect Transaction 
		     * Service WSDL file [rc.wsdl]*/
            rcService service = new rcService();
            /* set the URL*/
            service.Url = "https://stg.dw.us.fdcnet.biz/rc";
            /*Execute the transaction to send the data.*/
            ResponseType responseType = service.rcTransaction(requestType);

            /* Parse the response*/
            if (responseType != null && responseType.Status != null
                && responseType.Status.StatusCode != null)
            {
                if (responseType.Status.StatusCode.Equals("OK"))
                {
                    if (responseType.TransactionResponse != null
                            && responseType.TransactionResponse.Payload != null
                            && responseType.TransactionResponse.Payload.Encoding != null)
                    {
                        if (responseType.TransactionResponse.Payload.Encoding == PayloadTypeEncoding.cdata)
                                
                        {
                            gmfResponse = responseType.TransactionResponse
                                    .Payload.Value;
                        }
                        else if(responseType.TransactionResponse.Payload.Encoding == PayloadTypeEncoding.xml_escape)
                        {
                            gmfResponse = responseType.TransactionResponse.Payload.Value
                                    .Replace("&gt;", ">")
                                    .Replace("&lt;", "<")
                                    .Replace("&amp;", "&");                                    
                        }
                    }
                }
            }
            else
            {

            }
            /*Return the response*/
            response = gmfResponse;
            return response;
        }
    }
}
