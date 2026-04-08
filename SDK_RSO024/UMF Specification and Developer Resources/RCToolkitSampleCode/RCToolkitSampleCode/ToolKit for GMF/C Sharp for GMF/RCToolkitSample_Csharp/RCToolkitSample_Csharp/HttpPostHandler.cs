using System;
using System.Collections.Generic;
using System.Text;
using Fiserv.RapidConnect.Datawire.Xml;
using System.Xml.Serialization;
using System.IO;
using System.Net;

/* The below class shows the way to send transaction request data to data wire,
 * and receive response from data wire using HTTP POST protocol.  
 * */
namespace GlobalMessageFormatter
{
    class HttpPostHandler
    {

        public HttpPostHandler()
        {
        }
        /* The below method will take the XML request and returns the XML response received from Data wire.
	     * */
        public string SendMessage(string gmfRequest, string clientRef)
        {
            /*Response that will be returned.*/
            string response = "";
            /* Create the instance of the Request that is a class generated from the Rapid connect Transaction 
		     * Service Schema file [rc.xsd]*/
            Request gmfTransactionRequest = new Request();
            /* Create the instance of the TransactionType that is a class generated from the Rapid connect Transaction 
		     * Service Schema file [rc.xsd]*/
            TransactionType transactionType = new TransactionType();
            /* Create the instance of the PayloadType that is a class generated from the Rapid connect Transaction 
		     * Service Schema file [rc.xsd]*/
            PayloadType payloadType = new PayloadType();
            /* Set the encoding type*/
            payloadType.Encoding = PayloadTypeEncoding.cdata;
            /* Set the transaction request as pay load type value*/
            payloadType.Value = gmfRequest;
            /* Set transaction type pay load*/
            transactionType.Payload = payloadType;
            /*Set service ID*/
            transactionType.ServiceID = "160";
            /*Set the transaction object value*/
            gmfTransactionRequest.Transaction = transactionType;

            /* Create the instance of the ReqClientIDType that is a class generated from the Rapid connect Transaction 
		     * Service Schema file [rc.xsd]*/
            ReqClientIDType reqClientIDType = new ReqClientIDType();
            /* Set App value*/
            reqClientIDType.App = "RAPIDCONNECTVXN";
            /* Set Auth value*/
            reqClientIDType.Auth = TestConst.HTTP_AUTHID; 
            /* Set clientRef value*/
            reqClientIDType.ClientRef = clientRef;
            /* Set DID value*/
            reqClientIDType.DID = TestConst.HTTP_DID; 
            /* Set  ReqClientID value of the request object*/
            gmfTransactionRequest.ReqClientID = reqClientIDType;
            /* Set clientTimeout value of the request object*/
            gmfTransactionRequest.ClientTimeout = "30";
            /* Set version of the request object*/
            gmfTransactionRequest.Version = "3";

            /*The XML request data to be posted to Rapid Connect Transaction Service URL*/
            string requestXML = null;
            /* The below section will transform the request ojbect into an xml request.*/
            MemoryStream memoryStream = new MemoryStream();
            XmlSerializer xs = new XmlSerializer(gmfTransactionRequest.GetType());
            System.Xml.XmlTextWriter xmlTextWriter = new System.Xml.XmlTextWriter(memoryStream, System.Text.Encoding.UTF8);
            xs.Serialize(xmlTextWriter, gmfTransactionRequest);
            memoryStream = (MemoryStream)xmlTextWriter.BaseStream;
            System.Text.UTF8Encoding encoding = new System.Text.UTF8Encoding();
            requestXML = encoding.GetString(memoryStream.ToArray());


            // using System.Net;
            ServicePointManager.Expect100Continue = true;
            //ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls;
            ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072;

            // Use SecurityProtocolType.Ssl3 if needed for compatibility reasons
            /* URL that will consume the XML request Data */
            String url = "https://stg.dw.us.fdcnet.biz/rc";
            /* Instantiate the WebRequest object.*/
            WebRequest request = WebRequest.Create(url);        
            /* Set the method type*/
            request.Method = "POST";
            byte[] byteArray = Encoding.UTF8.GetBytes(requestXML);            
            request.ContentType = "text/xml";            
            request.ContentLength = byteArray.Length;            
            Stream dataStream = request.GetRequestStream();
            /* Write the byte stream*/
            dataStream.Write(byteArray, 0, byteArray.Length);            
            dataStream.Close();
            
            /* Receive the response*/
            WebResponse webresponse = request.GetResponse();            
            dataStream = webresponse.GetResponseStream();            
            StreamReader reader = new StreamReader(dataStream);            
            string responseFromDatawire = reader.ReadToEnd();            
            /* Clean up the streams. */
            reader.Close();
            dataStream.Close();
            webresponse.Close();
            /* Deserialize the received response string and get the Response object.*/
            /* Construct the temporary Response object topass the type*/
            Response r = new Response();            
            XmlSerializer xmlSerializer = new XmlSerializer(r.GetType());
            StringReader stringReader = new StringReader(responseFromDatawire);
            System.Xml.XmlTextReader xmlReader = new System.Xml.XmlTextReader(stringReader);
            Response objres = (Response) xmlSerializer.Deserialize(xmlReader);
            /* Parse the Response object and validate the response*/

            PayloadTypeEncoding encodetype;
 
            if (objres != null && objres.Status != null && objres.Status.StatusCode != null)
            {
                if(objres.Status.StatusCode.Equals("OK",StringComparison.CurrentCultureIgnoreCase))
                {
                    response = objres.TransactionResponse.Payload.Value;
                    encodetype = objres.TransactionResponse.Payload.Encoding;                    
                    if (encodetype == PayloadTypeEncoding.xml_escape)
                    {
                        response = response.Replace("&gt;", ">").Replace("&lt;", "<").Replace("&amp;", "&");
                    }
                }
            }            
            /*Send the response*/            
            return response;
        }
    }
}
