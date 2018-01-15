/*
 * This code and all components (c) Copyright 2006 - 2018, Wowza Media Systems, LLC. All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */
package com.wowza.wms.plugin.streamresolver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import com.wowza.wms.logging.WMSLogger;

public class UDPClient
{
	private String host;
	private int port;
	private WMSLogger logger;
	private int timeout = 2000;
	private boolean debug = false;

//	public UDPClient(String server, int port, WMSLogger logger){
//		this.host = server;
//		this.port = port;
//		this.logger = logger;
//	}

	public UDPClient(String server, int port, int timeout, WMSLogger logger, boolean debug)
	{
		host = server;
		this.port = port;
		this.timeout = timeout;
		this.logger = logger;
		this.debug = debug;
	}

	public String send(Message message)
	{
		DatagramSocket clientSocket = null;
		try
		{
			clientSocket = new DatagramSocket();
			clientSocket.setSoTimeout(timeout);

			InetAddress ipAddress = InetAddress.getByName(host);

			byte[] sendData = new byte[1024];
			byte[] receiveData = new byte[1024];

			String messageStr = message.toString();
			if (debug)
				logger.info(ServerListenerLocateSourceStream.MODULE_NAME + "[UDPClient]Sending UDP Message :: " + messageStr + " to server :: " + host + ":" + ipAddress);

			sendData = messageStr.getBytes();
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipAddress, port);
			clientSocket.send(sendPacket);
			DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
			clientSocket.receive(receivePacket);

			String responseString = new String(receivePacket.getData());
			if (debug)
				logger.info(ServerListenerLocateSourceStream.MODULE_NAME + "[UDPClient] FROM SERVER:" + responseString + " from server::" + receivePacket.getSocketAddress());

			return responseString;
		}
		catch (SocketTimeoutException ste)
		{
			logger.warn(ServerListenerLocateSourceStream.MODULE_NAME + "[UDPClient] timeout connecting to server :" + host);
		}
		catch (IOException ioe)
		{
			logger.warn(ServerListenerLocateSourceStream.MODULE_NAME + "[UDPClient] problem connecting to server :" + host + ": " + ioe.getMessage());
		}
		catch (Exception ex)
		{
			logger.error(ServerListenerLocateSourceStream.MODULE_NAME + "[UDPClient]Exception :" + ex.getMessage(), ex);
		}
		finally
		{
			if (clientSocket != null)
				clientSocket.close();
		}
		return null;
	}
}
