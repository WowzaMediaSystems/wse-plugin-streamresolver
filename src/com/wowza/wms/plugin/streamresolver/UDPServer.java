/*
 * This code and all components (c) Copyright 2006 - 2016, Wowza Media Systems, LLC. All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */
package com.wowza.wms.plugin.streamresolver;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Iterator;
import java.util.List;

import com.wowza.util.JSON;
import com.wowza.util.StringUtils;
import com.wowza.wms.application.IApplication;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.stream.MediaStreamMap;
import com.wowza.wms.vhost.IVHost;
import com.wowza.wms.vhost.VHostSingleton;

public class UDPServer
{
	private int port;
	private WMSLogger logger;
	private boolean debug = false;
	private String publicHostName = "";

	public UDPServer(int port, String host, WMSLogger logger, boolean debug)
	{
		this.logger = logger;
		this.port = port;
		this.debug = debug;
		publicHostName = host;
		listenForRequests();
	}

	private String getStreamOrigin(String streamName, String appName, String appInstanceName, String packetizer)
	{
		/*
		 * returns json object
		 * {server: "192.168.1.50/live/_definst_/myStream"}
		 */

		if (debug)
		{
			logger.info(ServerListenerLocateSourceStream.MODULE_NAME + "[UDPServer] getStreamOrigin::" + streamName);
		}

		@SuppressWarnings("unchecked")
		List<String> vhostNames = VHostSingleton.getVHostNames();
		if (vhostNames.size() > 0)
		{
			Iterator<String> vhostIterator = vhostNames.iterator();
			while (vhostIterator.hasNext())
			{
				String vhostName = vhostIterator.next();
				IVHost vhost = VHostSingleton.getInstance(vhostName);
				
				if(vhost.isApplicationLoaded(appName))
				{
					IApplication application = vhost.getApplication(appName);
					if (application != null)
					{
						IApplicationInstance appInstance = application.getAppInstance(appInstanceName);
						if (appInstance != null)
						{
							MediaStreamMap streams = appInstance.getStreams();
							if ((!StringUtils.isEmpty(packetizer) && streams.getLiveStreamPacketizer(streamName, packetizer, false) != null) || streams.getStream(streamName) != null)
							{
								String server = "{server: \"" + publicHostName + "/" + appName + "/" + appInstanceName + "/" + streamName + "\"}";
	
								if (debug)
									logger.info(ServerListenerLocateSourceStream.MODULE_NAME + "[UDPServer] server::" + server);
								return server;
							}
						}
					}
				}
			}
		}
		return "{error: \"Unable to locate Stream\"}";
	}

	private void listenForRequests()
	{
		DatagramSocket serverSocket = null;
		try
		{
			if (debug)
			{
				logger.info(ServerListenerLocateSourceStream.MODULE_NAME + "[UDPServer] listenForRequests on port " + port);
			}

			serverSocket = new DatagramSocket(port);
			while (true)
			{
				byte[] receiveData = new byte[1024];
				byte[] sendData = new byte[1024];

				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				serverSocket.receive(receivePacket);
				String message = new String(receivePacket.getData());
				if (debug)
					logger.info(ServerListenerLocateSourceStream.MODULE_NAME + "[UDPServer] listenForRequests::received message::" + message + " from server::" + receivePacket.getSocketAddress());

				String responseString = "";
				try
				{
					JSON json = new JSON(message);
					if (json != null)
					{
						String streamName = json.getString("streamName");
						String appName = json.getString("appName");
						String appInstanceName = json.getString("appInstanceName");
						String packetizer = json.getString("packetizerName", "");

						responseString = getStreamOrigin(streamName, appName, appInstanceName, packetizer);
					}
				}
				catch (Exception ex)
				{
					logger.error(ServerListenerLocateSourceStream.MODULE_NAME + "[UDPServer] listenForRequests::JSON-Exception::" + ex.getMessage(), ex);
				}

				try
				{
					InetAddress IPAddress = receivePacket.getAddress();
					int port = receivePacket.getPort();

					sendData = responseString.getBytes();

					DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, port);
					serverSocket.send(sendPacket);
					if (debug)
						logger.info(ServerListenerLocateSourceStream.MODULE_NAME + "[UDPServer] listenForRequests::responseString::" + responseString + " to server::" + receivePacket.getSocketAddress());
				}
				catch (Exception ex)
				{
					logger.error(ServerListenerLocateSourceStream.MODULE_NAME + "[UDPServer] listenForRequests::IP-Exception::" + ex.getMessage(), ex);
				}
			}
		}
		catch (Exception ex)
		{
			logger.error(ServerListenerLocateSourceStream.MODULE_NAME + "[UDPServer] Exception::" + ex.getMessage(), ex);
		}
	}
}
