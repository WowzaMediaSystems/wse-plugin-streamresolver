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

import com.wowza.wms.application.IApplication;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.mediacaster.MediaCasterStreamItem;
import com.wowza.wms.stream.IMediaStream;
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

	private String getStreamOrigin(String requestedStreamName, String requestedAppName, String requestedAppInstanceName)
	{

		if (debug)
		{
			logger.info(ServerListenerLocateSourceStream.MODULE_NAME + "[UDPServer] getStreamOrigin::" + requestedStreamName);
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

				IApplication application = vhost.getApplication(requestedAppName);
				if (application != null)
				{

					IApplicationInstance appInstance = application.getAppInstance(requestedAppInstanceName);
					if (appInstance != null)
					{
						if (appInstance.getMediaCasterStreams() != null)
						{
							MediaCasterStreamItem item = appInstance.getMediaCasterStreams().getMediaCaster(requestedStreamName);
							if (item != null && item.getMediaCaster() != null)
							{
								if (item.getMediaCaster().isStreamIsRunning())
								{
									String server = publicHostName + "/" + requestedAppName + "/" + requestedAppInstanceName + "/" + requestedStreamName;
									if (debug)
										logger.info(ServerListenerLocateSourceStream.MODULE_NAME + "[UDPServer-Mediacaster] server::" + server);
									return server;
								}
							}
						}

						if (appInstance.getStreams() != null)
						{
							IMediaStream stream = appInstance.getStreams().getStream(requestedStreamName);
							if (stream != null)
							{
								String server = publicHostName + "/" + requestedAppName + "/" + requestedAppInstanceName + "/" + requestedStreamName;
								if (debug)
									logger.info(ServerListenerLocateSourceStream.MODULE_NAME + "[UDPServer] server::" + server);
								return server;
							}
						}
					}
				}
			}
		}
		return "Error: Unable to locate Stream";
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
					String[] response = message.split("\\|");
					if (response.length >= 3)
					{
						String requestedStreamName = response[2].trim();
						String requestedAppName = response[0].trim();
						String requestedAppInstanceName = response[1].trim();

						responseString = getStreamOrigin(requestedStreamName, requestedAppName, requestedAppInstanceName);
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
