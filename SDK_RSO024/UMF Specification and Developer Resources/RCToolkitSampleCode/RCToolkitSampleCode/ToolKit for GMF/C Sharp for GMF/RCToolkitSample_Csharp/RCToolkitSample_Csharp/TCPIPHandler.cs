using System;
using System.Collections.Generic;
using System.Text;
using System.Net;
using System.Net.Sockets;

namespace GlobalMessageFormatter
{
    public class TCPIPHandler
    {        

        public TCPIPHandler()
        {
        }
        public string SendMessage(string gmfMessage)
        {
            /* Response that will be returned */
            string response = "";

            /* Prepare the header and trailer bytes of the request message */
            int len = gmfMessage.Length;
            string strlen = len.ToString("X");

            /* Byte array to hold final TCP request message, including 6 byte header and 4 byte trailer */
            byte[] outBytes = new byte[6 + len + 4];

            if (strlen.Length == 1)
                strlen = "000" + strlen;
            if (strlen.Length == 2)
                strlen = "00" + strlen;
            if (strlen.Length == 3)
                strlen = "0" + strlen;

            /* Header bytes - [STX][F][D][STX]*/
            byte[] header = new byte[] { 0X02, 0X46, 0X44, 0X02 };
            /* Trailer bytes - [ETX][F][D][ETX]*/
            byte[] trailer = new byte[] { 0X03, 0X46, 0X44, 0X03 };
            
            /* Convert first two hex string of message length into char */
            string s1 = strlen.Substring(0, 2);
            string s2 = strlen.Substring(2, 2);
            int i1 = Convert.ToInt32(s1, 16);
            int i2 = Convert.ToInt32(s2, 16);

            /* Copy header bytes to final TCP request message */
            Buffer.BlockCopy(header, 0, outBytes, 0, 4);
            outBytes[4] = (byte)i1; //copy first byte length to TCP request
            outBytes[5] = (byte)i2; //copy second byte length to TCP request

            /* Copy GMF payload in bytes into final TCP request message, starting after first 6 positions */
            int idx = 6;
            Buffer.BlockCopy(System.Text.Encoding.UTF8.GetBytes(gmfMessage), 0, outBytes, idx, len);

            idx = idx + len; //increment byte array index

            /* Copy trailer bytes to final TCP request message */
            Buffer.BlockCopy(trailer, 0, outBytes, idx, 4);

            try
            {
                /* Buffer to read Response data from the Remote connection */
                byte[] data = new byte[1024 * 4];

                /* IP Address */
                string address = TestConst.TCP_HOST; 
                IPAddress ipAddress = IPAddress.Parse(address);

                /* Create the Remote End Point IP - "XX.XXX.XX.XXX" and Port - XXXXX */
                IPEndPoint endPoint = new IPEndPoint(ipAddress, TestConst.TCP_PORT);

                /* Create a TCP/IP  socket */
                Socket senderSock = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);

                /* Connect the socket to the remote endpoint */
                senderSock.Connect(endPoint);
                senderSock.SendTimeout = 45000;
                senderSock.ReceiveTimeout= 45000;                

                /* Send the data through the socket */
                int bytesSent = senderSock.Send(outBytes);

                /* Receive the response from the remote device */
                int bytesRec = senderSock.Receive(data);
                response = ASCIIEncoding.UTF8.GetString(data);

                /* Release the socket */
                senderSock.Shutdown(SocketShutdown.Both);
                senderSock.Close();
            }
            catch (ArgumentNullException ane)
            {
                Console.WriteLine("TCP Exception" + ane.ToString());
            }
            catch (SocketException se)
            {
                Console.WriteLine("TCP Exception" + se.ToString());
            }
            catch (Exception e)
            {
                Console.WriteLine("TCP Exception" + e.ToString());
            }

            /* Parse the response and take only the XML response only */
            if(response.Length > 6)
                response = response.Substring(6, response.Length - 6);
            string csEndTag = "</GMF>";
            int iLoc = response.IndexOf(csEndTag);
            if (iLoc > 0)
            {
                response = response.Substring(0, iLoc + csEndTag.Length);
            }
            return response;
        }
    }
}
