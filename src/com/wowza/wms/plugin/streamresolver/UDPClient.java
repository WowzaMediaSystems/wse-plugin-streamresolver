/*
 * This code and all components (c) Copyright 2006 - 2016, Wowza Media Systems, LLC. All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */
package com.wowza.wms.plugin.streamresolver;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import com.wowza.wms.logging.WMSLogger;

public class UDPClient {
	private String host;
	private int hostPort;
	private int localPort;
	private WMSLogger logger;
	private int timeout = 2000;

	public UDPClient(String server, int port, WMSLogger logger){
		String[] parts = server.split(":");
		this.host = parts[0];
		if(parts.length > 1)
			this.hostPort = Integer.parseInt(parts[1]);
		else
			this.hostPort = port;
		this.localPort = port;
		this.logger = logger;
	}

	public UDPClient(String server, int port, int timeout, WMSLogger logger){
		String[] parts = server.split(":");
		this.host = parts[0];
		if(parts.length > 1)
			this.hostPort = Integer.parseInt(parts[1]);
		else
			this.hostPort = port;
		this.localPort = port;
		this.timeout = timeout;
		this.logger = logger;
	}

	public String send(Message message){
			try{
		      DatagramSocket clientSocket = new DatagramSocket(this.localPort);
		      clientSocket.setSoTimeout(this.timeout);

		      InetAddress ipAddress = InetAddress.getByName(this.host);

		      byte[] sendData = new byte[1024];
		      byte[] receiveData = new byte[1024];

		      String messageStr = message.toString();

		      this.logger.info(ServerListenerLocateSourceStream.MODULE_NAME+"[UDPClient]Sending UDP Message :: "+messageStr + " to server :: " + this.host + ":" + ipAddress);

		      sendData = messageStr.getBytes();
		      DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipAddress, this.hostPort);
		      clientSocket.send(sendPacket);
		      DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
		      clientSocket.receive(receivePacket);

		      String responseString = new String(receivePacket.getData());
		      this.logger.info(ServerListenerLocateSourceStream.MODULE_NAME+"[UDPClient] FROM SERVER:" + responseString + " from server::" + receivePacket.getSocketAddress());

		      clientSocket.close();
		      return responseString;
			}
			catch(Exception ex){
			      this.logger.info(ServerListenerLocateSourceStream.MODULE_NAME+"[UDPClient]Exception :" + ex.getMessage());
			}
			return null;
	   }
}
