/*
 * This code and all components (c) Copyright 2006 - 2020, Wowza Media Systems, LLC. All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */
package com.wowza.wms.plugin.streamresolver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wowza.util.JSON;
import com.wowza.util.StringUtils;
import com.wowza.wms.application.ApplicationInstance;
import com.wowza.wms.application.IApplication;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.stream.IMediaStream;
import com.wowza.wms.stream.MediaStreamMap;
import com.wowza.wms.vhost.IVHost;
import com.wowza.wms.vhost.VHostSingleton;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UDPServer
{
    private final int port;
    private final WMSLogger logger;
    private final boolean debug;
    private final String publicHostName;

    public UDPServer(int port, String host, WMSLogger logger, boolean debug)
    {
        this.logger = logger;
        this.port = port;
        this.debug = debug;
        publicHostName = host;
        listenForRequests();
    }

    private String getStreamOrigin(String streamName, String appName, String appInstanceName)
    {
        /*
         * returns json object
         * {
         * 		server: "192.168.1.50/live/_definst_/myStream",
         * 		packetizers: ["cupertinostreamingpacketizer", "mpegdashstreamingpacketizer"]
         * }
         * or error
         * {error: "Unable to locate Stream"}
         */
        try
        {
            Map<String, Object> jsonMap = new HashMap<>();

            if (debug)
                logger.info(ServerListenerLocateSourceStream.MODULE_NAME + "[UDPServer] getStreamOrigin::" + streamName);

            @SuppressWarnings("unchecked")
            List<String> vhostNames = VHostSingleton.getVHostNames();
            for (String vhostName : vhostNames)
            {
                IVHost vhost = VHostSingleton.getInstance(vhostName);

                if (vhost.isApplicationLoaded(appName))
                {
                    IApplication application = vhost.getApplication(appName);
                    if (application != null)
                    {
                        IApplicationInstance appInstance = application.getAppInstance(appInstanceName);
                        if (appInstance != null)
                        {
                            // AppInstances will stay loaded until there is at least 1 valid connection.
                            // Increment the connection count to allow the appInstance to shut down if there are no other connection attempts.
                            if (appInstance.getClientCountTotal() <= 0)
                            {
                                appInstance.incClientCountTotal();
                                ((ApplicationInstance) appInstance).setClientRemoveTime(System.currentTimeMillis());
                            }

                            MediaStreamMap streams = appInstance.getStreams();
                            boolean found = false;
                            IMediaStream stream = streams.getStream(streamName);
                            if (stream != null)
                            {
                                found = true;
                                String serverStr = publicHostName + "/" + appName + "/" + appInstanceName + "/" + streamName;
                                jsonMap.put("server", serverStr);
                                String packetizerNames = stream.getLiveStreamPacketizerList();
                                if (!StringUtils.isEmpty(packetizerNames))
                                {
                                    List<String> packetizers = new ArrayList<>();
                                    for (String packetizerName : packetizerNames.split("[|,]"))
                                    {
                                        packetizerName = packetizerName.trim();
                                        if (streams.getLiveStreamPacketizer(streamName, packetizerName, false) != null)
                                        {
                                            packetizers.add(packetizerName);
                                        }
                                    }
                                    if (!packetizers.isEmpty())
                                        jsonMap.put("packetizers", packetizers);
                                }
                            }

                            if (found)
                            {


                                String json = new ObjectMapper().writeValueAsString(jsonMap);
                                if (debug)
                                    logger.info(ServerListenerLocateSourceStream.MODULE_NAME + "[UDPServer] getStreamOrigin::" + json);
                                return json;
                            }
                        }
                    }
                }
            }
        } catch (Exception e)
        {
            logger.error(ServerListenerLocateSourceStream.MODULE_NAME + "[UDPServer] getStreamOrigin:: Exception Thrown", e);
        }
        return "{error: \"Unable to locate Stream\"}";
    }

    private void listenForRequests()
    {
        DatagramSocket serverSocket;
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
                byte[] sendData;

                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                serverSocket.receive(receivePacket);
                String message = new String(receivePacket.getData(), receivePacket.getOffset(), receivePacket.getLength());
                if (debug)
                    logger.info(ServerListenerLocateSourceStream.MODULE_NAME + "[UDPServer] listenForRequests::received message::" + message + " from server::" + receivePacket.getSocketAddress());

                String responseString = "";
                try
                {
                    JSON json = new JSON(message);
                    String streamName = json.getString("streamName");
                    String appName = json.getString("appName");
                    String appInstanceName = json.getString("appInstanceName");

                    responseString = getStreamOrigin(streamName, appName, appInstanceName);
                } catch (Exception ex)
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
                } catch (Exception ex)
                {
                    logger.error(ServerListenerLocateSourceStream.MODULE_NAME + "[UDPServer] listenForRequests::IP-Exception::" + ex.getMessage(), ex);
                }
            }
        } catch (Exception ex)
        {
            logger.error(ServerListenerLocateSourceStream.MODULE_NAME + "[UDPServer] Exception::" + ex.getMessage(), ex);
        }
    }
}
