package dw.socket;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

/* This class implements basic socket connection interfaces like connect, close ,open etc.
 * */
 
public class TcpSocket {

	/* Socket class instance*/
	private Socket hostSocket;
	/* Input stream instance*/
	private BufferedInputStream bufferedInputStream;
	/* Input stream instance*/
	private BufferedOutputStream bufferedOutputStream;

	/* Open a  socket connection with given server host and port*/
	public Socket connect(String host, int port)
			{		
		try {
			/* Open a  socket connection with given server host and port*/
			hostSocket = new Socket(host, port);
			/* Input stream of the opened  socket connection*/
			bufferedInputStream = new BufferedInputStream(hostSocket.getInputStream());
			/* Output stream of the opened  socket connection*/
			bufferedOutputStream = new BufferedOutputStream(hostSocket.getOutputStream());

		} catch (UnknownHostException e) {
			
			System.out.println("Exception: " + e.toString());
		} catch (IOException e) {
			System.out.println("Exception: " + e.toString());
		}		
		return hostSocket;
	}
	/* Input stream of the opened  socket connection*/
	public BufferedInputStream getBufferedInputStream() {
		return bufferedInputStream;
	}
	/* Output stream of the opened  socket connection*/
	public BufferedOutputStream getBufferedOutputStream() {
		return bufferedOutputStream;

	}
	/* Close the opened  socket connection*/
	public void close(BufferedInputStream inputStream, BufferedOutputStream outputStream) {		
		try {
			if (inputStream != null) {
				inputStream.close();
			}

			if (outputStream != null) {
				outputStream.close();
			}

		} catch (Exception e) {
			System.out.println("Exception: " + e.toString());
		} finally {
			hostSocket = null;
			bufferedInputStream = null;
			bufferedOutputStream = null;
		}		
	}
	/* Determine whether socket connection is already open */
	public boolean isOpen() {
		if (hostSocket == null)
			return false;
		else
			return true;
	}

}
