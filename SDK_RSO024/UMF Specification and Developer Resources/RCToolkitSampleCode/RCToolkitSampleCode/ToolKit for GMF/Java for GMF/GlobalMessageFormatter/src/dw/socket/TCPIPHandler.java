package dw.socket;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.SocketException;

import fiserv.rc.gmf.toolkit.TestConst;


/* This class implements message formatting before sending to host, response parsing etc.
 * */
public class TCPIPHandler {
	BufferedInputStream bufferedInputStream;
	BufferedOutputStream bufferedOutputStream;
	Socket socket;
	TcpSocket client;
	/* Send message to output socket stream*/
	private void sendMessage(byte[] msg, BufferedOutputStream outputStream)	throws IOException
	{
		try {
			outputStream.write(msg);
			outputStream.flush();

		} catch (IOException ioException) {
			System.out.println("Exception: " + ioException.toString());
		}		
	}
	/* Read  data from  output buffer*/
	private String receive(Reader bufferRead)
	{
		String chunk = "";
		try {
			char[] buffer = new char[1024];
			int i = 0;
			while ((i = bufferRead.read(buffer)) > -1) {
				chunk = new String(buffer, 0, i);				
				return chunk;

			}

		} catch (IOException e) {
			System.out.println("Exception: " + e.toString());
		}		
		return chunk;
	}

	public byte[] concat(byte[]... arrays) {
		
		// Determine the length of the result array
		int totalLength = 0;
		for (int i = 0; i < arrays.length; i++) {
			totalLength += arrays[i].length;
		}

		// create the result array
		byte[] result = new byte[totalLength];

		// copy the source arrays into the result array
		int currentIndex = 0;
		for (int i = 0; i < arrays.length; i++) {
			System.arraycopy(arrays[i], 0, result, currentIndex,
					arrays[i].length);
			currentIndex += arrays[i].length;
		}

		return result;
}
	/*Format the TCP message*/	 
private byte[] tcpMessage(String gmfRequest) throws UnsupportedEncodingException {
		
		
		/*Convert to hex string */
		String length = Integer.toHexString(gmfRequest.length());
		
		/*Format it to 4 bytes */
		if (length.length() == 1)
			length = "000" + length;
		if (length.length() == 2)
			length = "00" + length;
		if (length.length() == 3)
			length = "0" + length;
		/*Prepare Header*/
		String header = new String(new char[] { 0X02, 0X46, 0X44, 0X02 });
		header = "" + header;
		/*Prepare Trailer*/
		String trailer = new String(new char[] { 0X03, 0X46, 0X44, 0X03 });
		trailer = "" + trailer;
		char firstByte;
		byte[] secondByte;
		/*Convert first two digit of Length into byte*/
		firstByte = (char) (Integer.parseInt(length.trim().substring(0, 2), 16));
		secondByte = new byte[1];
		/*Convert next two digit of Length into byte*/
		secondByte[0] = (byte) (Integer.parseInt(length.substring(2, 4), 16));
		
		//String secondByteASCII = new String(secondByte,"ISO-8859-1");
        byte[] message = null;
		//message = header + firstByte + secondByteASCII + gmfRequest + trailer;
        message = concat((header+firstByte).getBytes(), secondByte, (gmfRequest+trailer).getBytes());
		//sendTransactionTCPSocketLogger.info("<< tcpMessage()");
		return message;

	}
	/*Send message to host*/
	public String SendMessage(String gmfRequest)
	{
		String gmfResponse = null;
		/*Host IP*/
		String ipaddress = TestConst.TCP_HOST;

		/*Port*/
		int portNumber = TestConst.TCP_PORT;

		try {
			/*Open TCP socket instance*/
			client = new TcpSocket();
			/*Check if it is open*/
			if (!client.isOpen()) {
				/*Open the socket stream connecting to host with proper port.*/
				socket = client.connect(ipaddress,
						portNumber);
				/*Set timeout value*/
				socket.setSoTimeout(45000);
				/*Input stream*/
				bufferedInputStream = new BufferedInputStream(
						socket.getInputStream());
				/*Output stream*/
				bufferedOutputStream = new BufferedOutputStream(
						socket.getOutputStream());
			}
			
			// 2. get Input and Output streams
			bufferedInputStream = new BufferedInputStream(
					socket.getInputStream());
			bufferedOutputStream = new BufferedOutputStream(
					socket.getOutputStream());
			/*Create the TCP message format*/
			byte[] message = null;
			message = tcpMessage(gmfRequest);
			sendMessage(message, bufferedOutputStream);
			bufferedInputStream = new BufferedInputStream(
					socket.getInputStream());
			Reader bufRead = new InputStreamReader(bufferedInputStream);
			/*Read the response from buffer*/
			gmfResponse = receive(bufRead);	
			/*Parse the response*/
			int startindex = gmfResponse.indexOf("<?xml");
			int endindex = gmfResponse.indexOf("</GMF>");
			gmfResponse = gmfResponse.substring(startindex, endindex + 6 );

		} catch (SocketException e) {
			System.out.println("Exception: " + e.toString());
		} catch (IOException e) {
			System.out.println("Exception: " + e.toString());
		} finally {
			// 4: Closing connection
			client.close(bufferedInputStream, bufferedOutputStream);
		}		
		/*Return the response*/
		return gmfResponse;
	}
}
