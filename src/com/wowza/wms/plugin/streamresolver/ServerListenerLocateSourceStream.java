/*
 * This code and all components (c) Copyright 2006 - 2017, Wowza Media Systems, LLC.  All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */
package com.wowza.wms.plugin.streamresolver;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.server.IServer;
import com.wowza.wms.server.IServerNotify2;

public class ServerListenerLocateSourceStream implements IServerNotify2
{
	public static String MODULE_NAME = "ServerListenerLocateSourceStream";
	public static String MODULE_PROPERTY_PREFIX = "wowzaSourceStream";
	private static final int _UDP_PORT = 9777;
	private static final boolean _DEBUG = false;
	private Thread udpListenerThread;

	@Override
	public void onServerCreate(IServer server)
	{

	}

	@Override
	public void onServerInit(IServer server)
	{
		try
		{
			WMSLoggerFactory.getLogger(getClass()).info(MODULE_NAME + ".onServerInit starting up server listener..");
			boolean debug = server.getProperties().getPropertyBoolean(MODULE_PROPERTY_PREFIX + "UDPListenerDebug", ServerListenerLocateSourceStream._DEBUG);
			if (WMSLoggerFactory.getLogger(getClass()).isDebugEnabled())
				debug = true;
			int udpPort = server.getProperties().getPropertyInt(MODULE_PROPERTY_PREFIX + "UDPListenerPort", ServerListenerLocateSourceStream._UDP_PORT);
			String publicHostName = server.getProperties().getPropertyStr(MODULE_PROPERTY_PREFIX + "HostName", getInternalIPAddress());

			if (publicHostName != null)
			{
				udpListenerThread = new Thread(new UDPListener(udpPort, publicHostName, debug));
				udpListenerThread.start();
				WMSLoggerFactory.getLogger(getClass()).info(MODULE_NAME + ".onServerInit starting UDP Server..");
			}
			else
			{
				WMSLoggerFactory.getLogger(getClass()).info(MODULE_NAME + ".onServerInit[publicHostName] " + publicHostName);
			}
		}
		catch (Exception ex)
		{
			WMSLoggerFactory.getLogger(getClass()).info(MODULE_NAME + ".onServerInit[Exception]", ex);
		}
	}

	@Override
	public void onServerShutdownComplete(IServer server)
	{

	}

	@Override
	public void onServerShutdownStart(IServer server)
	{
		try
		{
			if (udpListenerThread != null)
			{
				WMSLoggerFactory.getLogger(getClass()).info(MODULE_NAME + ".onServerShutdownStart shutting down..");
				udpListenerThread.interrupt();
			}
		}
		catch (Exception ex)
		{
			WMSLoggerFactory.getLogger(getClass()).info(MODULE_NAME + ".onServerShutdownStart[Exception]", ex);
		}
	}

	@Override
	public void onServerConfigLoaded(IServer server)
	{

	}

	private class UDPListener implements Runnable
	{
		private int udpPort;
		private boolean debug;
		private String host;

		public UDPListener(int port, String host, boolean debug)
		{
			udpPort = port;
			this.debug = debug;
			this.host = host;
		}

		@Override
		public void run()
		{
			new UDPServer(udpPort, host, WMSLoggerFactory.getLogger(getClass()), debug);
		}

	}
	
	private String getInternalIPAddress()
	{
		String retVal=null;
		try
		{
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements())
			{
				NetworkInterface current = interfaces.nextElement();
				if (!current.isUp() || current.isLoopback() || current.isVirtual())
				{
					continue;
				}
				Enumeration<InetAddress> addresses = current.getInetAddresses();
				while (addresses.hasMoreElements())
				{
					InetAddress current_addr = addresses.nextElement();
					if (current_addr.isLoopbackAddress())
					{
						continue;
					}
					if (current_addr instanceof Inet4Address)
					{
						if (retVal == null)
						{
							retVal = current_addr.getHostAddress();
						}
					}
					/*
					else if (current_addr instanceof Inet6Address)
					{
						if (retVal == null)  //we prefer ipv4, so don't overwrite it
						{
							retVal = current_addr.getHostAddress();
						}
					}
					*/
				}
			}
		}
		catch (SocketException e)
		{
			WMSLoggerFactory.getLogger(getClass()).warn(MODULE_NAME + ".getInternalIpAddress Problem probing network interfaces", e);
		}
		if(retVal==null)
		{
			retVal="127.0.0.1";
		}
		return retVal;
	}

}
