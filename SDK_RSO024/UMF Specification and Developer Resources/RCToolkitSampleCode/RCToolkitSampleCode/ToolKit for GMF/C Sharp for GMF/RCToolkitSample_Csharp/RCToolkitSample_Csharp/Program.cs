using System;
using System.Xml.Serialization;
using System.IO;
using System.Xml;

namespace GlobalMessageFormatter
{
    /* This below program demonstrates the procedure of performing various payment transactions
     * through Datawire using different protocols SOAP, HTTP POST or TCP/IP. Please use either SOAP or HTTP POST     
     * protocol to process transaction through Datawire over the Internet.  TCP/IP is to be used for VPN or leased line.
     */
    class Program
    {
        static void Main(string[] args)
        {            
            /*Transaction request in xml format */
            String xmlSerializedTransReq = string.Empty;

            /*Transaction response in xml format received from Datawire */
            String xmlSerializedTransResp = string.Empty;

            /*Create Authorization request for a sample Credit Sale transaction.*/
            CreditSaleRequest CreditSaleReq = new CreditSaleRequest();

            /* Generate formatted XML data from above auth transaction request.*/
            xmlSerializedTransReq = CreditSaleReq.GetXMLData();

            /* Generate Client Ref Number in the format <STAN>|<TPPID>, right justified and left padded with "0" */
            string clientRef = CreditSaleReq.GetClientRef();

            /* Send data using SOAP protocol to Datawire*/
            xmlSerializedTransResp = new SoapHandler().SendMessage(xmlSerializedTransReq, clientRef);

            /*Print response in console.*/
            Console.WriteLine("Credit Request " + "\n" + xmlSerializedTransReq + "\n");
            Console.Write("Credit Sale Response using SOAP = " + "\n" + xmlSerializedTransResp + "\n");
            Console.Write("Please enter any key... " + "\n" + "\n");
            Console.ReadKey(false);

            /* Send data using HTTP POST protocol to Datawire*/
            xmlSerializedTransResp = new HttpPostHandler().SendMessage(xmlSerializedTransReq, clientRef);

            /*Print response in console.*/
            Console.Write("Credit Sale Response using HTTP POST = " + "\n" + xmlSerializedTransResp + "\n");            
            Console.Write("Please enter any key... " + "\n" + "\n");            
            Console.ReadKey(false);

            /* Send data using TCP/IP protocol to RC server */
            xmlSerializedTransResp = new TCPIPHandler().SendMessage(xmlSerializedTransReq);

            /*Print response in console.*/
            Console.Write("Credit Sale Response using TCP/IP = " + "\n" + xmlSerializedTransResp + "\n");
            Console.Write("Please enter any key... " + "\n" + "\n");
            Console.ReadKey(false);

            /*Create Authorization request for a sample Debit Sale transaction.*/
            DebitSaleRequest DebitSaleReq = new DebitSaleRequest();

            /* Generate formatted XML data from above auth transaction request.*/
            xmlSerializedTransReq = DebitSaleReq.GetXMLData();

            /* Send data using SOAP protocol to Datawire*/
            xmlSerializedTransResp = new SoapHandler().SendMessage(xmlSerializedTransReq, clientRef);

            /*Print response in console.*/
            Console.WriteLine("Debit Request = " + "\n" + xmlSerializedTransReq + "\n");
            Console.Write("Debit Sale Response using SOAP = " + "\n" + xmlSerializedTransResp + "\n");
            Console.Write("Please enter any key... " + "\n" + "\n");
            Console.ReadKey(false);

            /* Send data using SOAP protocol to Datawire*/
            xmlSerializedTransResp = new HttpPostHandler().SendMessage(xmlSerializedTransReq, clientRef);

            /*Print response in console.*/
            Console.Write("Debit Sale Response using HTTP POST = " + "\n" + xmlSerializedTransResp + "\n");
            Console.Write("Please enter any key... " + "\n" + "\n");
            Console.ReadKey(false);

            /* Send data using TCP/IP protocol to RC server */
            xmlSerializedTransResp = new TCPIPHandler().SendMessage(xmlSerializedTransReq);

            /*Print response in console.*/
            Console.Write("Debit Sale Response using TCPIP = " + "\n" + xmlSerializedTransResp + "\n");
            Console.Write("Please enter any key... " + "\n" + "\n");
            Console.ReadKey(false);
        }        
    }
}
