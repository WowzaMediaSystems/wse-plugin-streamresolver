package com.wowza.wms.example.module.utils;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.plugin.collection.serverlistener.ServerListenerLocateSourceStream;

public class UDPClient {
	private String host;
	private int port;
	private WMSLogger logger;
	private int timeout = 2000;
	
	public UDPClient(String server, int port, WMSLogger logger){
		this.host = server;
		this.port = port;
		this.logger = logger;
	}
	
	public UDPClient(String server, int port, int timeout, WMSLogger logger){
		this.host = server;
		this.port = port;
		this.timeout = timeout;
		this.logger = logger;
	}
	
	public String send(Message message){
			try{
		      DatagramSocket clientSocket = new DatagramSocket();
		      clientSocket.setSoTimeout(this.timeout);
		      
		      InetAddress IPAddress = InetAddress.getByName(this.host);
		      
		      byte[] sendData = new byte[1024];
		      byte[] receiveData = new byte[1024];
		      
		      String messageStr = message.toString();
		      
		      this.logger.info(ServerListenerLocateSourceStream.MODULE_NAME+"[UDPClient]Sending UDP Message :: "+messageStr);
		      
		      sendData = messageStr.getBytes();
		      DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, this.port);
		      clientSocket.send(sendPacket);
		      DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
		      clientSocket.receive(receivePacket);
		      
		      String responseString = new String(receivePacket.getData());
		      this.logger.info(ServerListenerLocateSourceStream.MODULE_NAME+"[UDPClient] FROM SERVER:" + responseString);
		      
		      clientSocket.close();
		      return responseString;
			}
			catch(Exception ex){
			      this.logger.info(ServerListenerLocateSourceStream.MODULE_NAME+"[UDPClient]Exception :" + ex.getMessage());
			}
			return null;
	   }
}
