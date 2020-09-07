/*
 * This code and all components (c) Copyright 2006 - 2020, Wowza Media Systems, LLC.  All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */
package com.wowza.wms.plugin.streamresolver;

import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.server.IServer;
import com.wowza.wms.server.ServerNotifyBase;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class ServerListenerLocateSourceStream extends ServerNotifyBase
{
    public static String MODULE_NAME = "ServerListenerLocateSourceStream";
    public static String MODULE_PROPERTY_PREFIX = "wowzaSourceStream";
    private static final int _UDP_PORT = 9777;
    private static final boolean _DEBUG = WMSLoggerFactory.getLogger(ServerListenerLocateSourceStream.class).isDebugEnabled();
    private static final WMSLogger logger = WMSLoggerFactory.getLogger(ServerListenerLocateSourceStream.class);
    private Thread udpListenerThread;

    @Override
    public void onServerInit(IServer server)
    {
        try
        {
            logger.info(MODULE_NAME + ".onServerInit starting up server listener.. Build #49");
            boolean debug = server.getProperties().getPropertyBoolean(MODULE_PROPERTY_PREFIX + "UDPListenerDebug", ServerListenerLocateSourceStream._DEBUG);
            int udpPort = server.getProperties().getPropertyInt(MODULE_PROPERTY_PREFIX + "UDPListenerPort", ServerListenerLocateSourceStream._UDP_PORT);
            String publicHostName = server.getProperties().getPropertyStr(MODULE_PROPERTY_PREFIX + "HostName", getInternalIPAddress());

            if (publicHostName != null)
            {
                udpListenerThread = new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        new UDPServer(udpPort, publicHostName, logger, debug);
                    }
                });
                udpListenerThread.start();
                logger.info(MODULE_NAME + ".onServerInit starting UDP Server..");
            } else
            {
                logger.info(MODULE_NAME + ".onServerInit[publicHostName] cannot resolve host name");
            }
        } catch (Exception ex)
        {
            logger.info(MODULE_NAME + ".onServerInit[Exception]", ex);
        }
    }

    @Override
    public void onServerShutdownStart(IServer server)
    {
        try
        {
            if (udpListenerThread != null)
            {
                logger.info(MODULE_NAME + ".onServerShutdownStart shutting down..");
                udpListenerThread.interrupt();
            }
        } catch (Exception ex)
        {
            logger.info(MODULE_NAME + ".onServerShutdownStart[Exception]", ex);
        }
    }

    private String getInternalIPAddress()
    {
        String retVal = null;
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
        } catch (SocketException e)
        {
            logger.warn(MODULE_NAME + ".getInternalIpAddress Problem probing network interfaces", e);
        }
        if (retVal == null)
        {
            retVal = "127.0.0.1";
        }
        return retVal;
    }

}
