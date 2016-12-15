/*
 * This code and all components (c) Copyright 2006 - 2016, Wowza Media Systems, LLC. All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */
package com.wowza.wms.plugin.streamresolver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.wowza.util.JSON;
import com.wowza.util.StringUtils;
import com.wowza.util.XMLUtils;
import com.wowza.wms.application.*;
import com.wowza.wms.bootstrap.Bootstrap;
import com.wowza.wms.client.IClient;
import com.wowza.wms.httpstreamer.cupertinostreaming.httpstreamer.HTTPStreamerSessionCupertino;
import com.wowza.wms.httpstreamer.model.HTTPStreamerItem;
import com.wowza.wms.httpstreamer.model.IHTTPStreamerSession;
import com.wowza.wms.httpstreamer.mpegdashstreaming.httpstreamer.HTTPStreamerSessionMPEGDash;
import com.wowza.wms.httpstreamer.sanjosestreaming.httpstreamer.HTTPStreamerSessionSanJose;
import com.wowza.wms.httpstreamer.smoothstreaming.httpstreamer.HTTPStreamerSessionSmoothStreamer;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.mediacaster.IMediaCaster;
import com.wowza.wms.mediacaster.IMediaCasterValidateMediaCaster;
import com.wowza.wms.mediacaster.MediaCaster;
import com.wowza.wms.mediacaster.MediaCasterItem;
import com.wowza.wms.mediacaster.MediaCasterNotifyBase;
import com.wowza.wms.mediacaster.MediaCasterStreamId;
import com.wowza.wms.mediacaster.MediaCasterStreamItem;
import com.wowza.wms.mediacaster.wowza.LiveMediaStreamReceiver;
import com.wowza.wms.module.ModuleBase;
import com.wowza.wms.rtp.model.RTPSession;
import com.wowza.wms.stream.IMediaReader;
import com.wowza.wms.stream.IMediaStream;
import com.wowza.wms.stream.IMediaStreamPlay;
import com.wowza.wms.stream.MediaStream;
import com.wowza.wms.stream.MediaStreamNameAliasProviderBase;
import com.wowza.wms.util.ModuleUtils;

import edu.emory.mathcs.backport.java.util.concurrent.locks.WMSReadWriteLock;

public class ModuleStreamResolver extends ModuleBase
{
	class AliasProvider extends MediaStreamNameAliasProviderBase
	{
		@Override
		public String resolvePlayAlias(IApplicationInstance appInstance, String name)
		{
			String streamName = name;
			String streamExt = MediaStream.BASE_STREAM_EXT;
			if (streamName != null)
			{
				String[] streamDecode = ModuleUtils.decodeStreamExtension(streamName, streamExt);
				streamName = streamDecode[0];
				streamExt = streamDecode[1];

				if (appInstance.getMediaReaderContentType(streamExt) == IMediaReader.CONTENTTYPE_MEDIALIST)
					return name;
			}
			String ret = getStreamName(streamName);
			// AppInstances will stay loaded intil there is at least 1 valid connection. 
			// Increment the connection count to allow the appInstnace to shut down if there are no other connection attempts.
			if(ret == null && appInstance.getClientCountTotal() <= 0)
			{
				appInstance.incClientCountTotal();
				((ApplicationInstance)appInstance).setClientRemoveTime(System.currentTimeMillis());
			}
			return ret;
		}

		@Override
		public String resolvePlayAlias(IApplicationInstance appInstance, String name, IClient client)
		{
			String streamName = name;
			String streamExt = MediaStream.BASE_STREAM_EXT;
			if (streamName != null)
			{
				String[] streamDecode = ModuleUtils.decodeStreamExtension(streamName, streamExt);
				streamName = streamDecode[0];
				streamExt = streamDecode[1];

				if (appInstance.getMediaReaderContentType(streamExt) == IMediaReader.CONTENTTYPE_MEDIALIST)
					return name;
			}
			String ret = getStreamName(streamName);
			// AppInstances will stay loaded intil there is at least 1 valid connection. 
			// Increment the connection count to allow the appInstnace to shut down if there are no other connection attempts.
			if(ret == null && appInstance.getClientCountTotal() <= 0)
			{
				appInstance.incClientCountTotal();
				((ApplicationInstance)appInstance).setClientRemoveTime(System.currentTimeMillis());
			}
			return ret;
		}

		@Override
		public String resolvePlayAlias(IApplicationInstance appInstance, String name, IHTTPStreamerSession httpSession)
		{
			String streamName = name;
			String streamExt = MediaStream.BASE_STREAM_EXT;
			if (streamName != null)
			{
				String[] streamDecode = ModuleUtils.decodeStreamExtension(streamName, streamExt);
				streamName = streamDecode[0];
				streamExt = streamDecode[1];

				if (appInstance.getMediaReaderContentType(streamExt) == IMediaReader.CONTENTTYPE_MEDIALIST)
					return name;
			}
			String packetizer = null;
			String repeater = null;
			if(httpSession != null && httpSession.getHTTPStreamerAdapter() != null)
			{
				HTTPStreamerItem item = httpSession.getHTTPStreamerAdapter().getHTTPStreamerItem();
				if(item != null)
				{
					packetizer = item.getLiveStreamPacketizer();
					repeater = item.getLiveStreamRepeater();
				}
			}
			
			if(packetizer == null)
				packetizer = resolvePacketizer(httpSession);
			if(repeater == null)
				repeater = resolveRepeater(httpSession);
			
			String ret = getStreamName(streamName, packetizer, repeater);
			// AppInstances will stay loaded intil there is at least 1 valid connection. 
			// Increment the connection count to allow the appInstnace to shut down if there are no other connection attempts.
			if(ret == null && appInstance.getClientCountTotal() <= 0)
			{
				appInstance.incClientCountTotal();
				((ApplicationInstance)appInstance).setClientRemoveTime(System.currentTimeMillis());
			}
			return ret;
		}

		private String resolvePacketizer(IHTTPStreamerSession httpSession)
		{
			String ret = null;
			
			if(httpSession instanceof HTTPStreamerSessionCupertino)
				ret = "cupertinostreamingpacketizer";
			else if(httpSession instanceof HTTPStreamerSessionSanJose)
				ret = "sanjosestreamingpacketizer";
			else if(httpSession instanceof HTTPStreamerSessionSmoothStreamer)
				ret = "smoothstreamingpacketizer";
			else if(httpSession instanceof HTTPStreamerSessionMPEGDash)
				ret = "mpegdashstreamingpacketizer";
			
			return ret;
		}

		private String resolveRepeater(IHTTPStreamerSession httpSession)
		{
			String ret = null;
			
			if(httpSession instanceof HTTPStreamerSessionCupertino)
				ret = "cupertinostreamingrepeater";
			else if(httpSession instanceof HTTPStreamerSessionSanJose)
				ret = "sanjosestreamingrepeater";
			else if(httpSession instanceof HTTPStreamerSessionSmoothStreamer)
				ret = "smoothstreamingrepeater";
			else if(httpSession instanceof HTTPStreamerSessionMPEGDash)
				ret = "mpegdashstreamingrepeater";
			
			return ret;
		}

		@Override
		public String resolvePlayAlias(IApplicationInstance appInstance, String name, RTPSession rtpSession)
		{
			String streamName = name;
			String streamExt = MediaStream.BASE_STREAM_EXT;
			if (streamName != null)
			{
				String[] streamDecode = ModuleUtils.decodeStreamExtension(streamName, streamExt);
				streamName = streamDecode[0];
				streamExt = streamDecode[1];

				if (appInstance.getMediaReaderContentType(streamExt) == IMediaReader.CONTENTTYPE_MEDIALIST)
					return name;
			}
			String ret = getStreamName(streamName);
			// AppInstances will stay loaded intil there is at least 1 valid connection. 
			// Increment the connection count to allow the appInstnace to shut down if there are no other connection attempts.
			if(ret == null && appInstance.getClientCountTotal() <= 0)
			{
				appInstance.incClientCountTotal();
				((ApplicationInstance)appInstance).setClientRemoveTime(System.currentTimeMillis());
			}
			return ret;
		}
		
		private String getStreamName(String name)
		{
			return getStreamName(name, null, null);
		}

		private String getStreamName(String name, String packetizer, String repeater)
		{
			name = appInstance.getProperties().getPropertyStr(MODULE_PROPERTY_PREFIX + "OriginStreamName", name);
			String nameContext = packetizer != null ? packetizer + "_" + name : name;

			if (debug)
				logger.info(ModuleStreamResolver.MODULE_NAME + ".getStreamName[" + nameContext + "] ");
			
			MediaCasterStreamItem mediaCasterStreamItem = appInstance.getMediaCasterStreams().getMediaCaster(name, packetizer, repeater);
			IMediaCaster mediaCaster = null;
			if(mediaCasterStreamItem != null)
			{
				mediaCaster = mediaCasterStreamItem.getMediaCaster();
				if(mediaCaster != null)
				{
					int timeoutReason = mediaCaster.getStreamTimeoutReason();
					if(timeoutReason == MediaCaster.STREAMTIMEOUTREASON_GOOD)
						return nameContext;
				}
			}
			String url = lookupURL(nameContext, packetizer);
			if(!StringUtils.isEmpty(url) && mediaCaster != null && mediaCaster instanceof LiveMediaStreamReceiver)
				((LiveMediaStreamReceiver)mediaCaster).resolveURL();
			
			return StringUtils.isEmpty(url) ? null : nameContext;
		}
		
		@Override
		public String resolveStreamAlias(IApplicationInstance appInstance, String name)
		{
			String url = "";
			synchronized(lock)
			{
				url = urls.get(name);
			}
			return StringUtils.isEmpty(url) ? "" : url;
		}

		@Override
		public String resolveStreamAlias(IApplicationInstance appInstance, String name, IMediaCaster mediaCaster)
		{
			String url = "";
			synchronized(lock)
			{
				url = urls.get(name);
			}
			return StringUtils.isEmpty(url) ? "" : url;
		}
	}

	class MediaCasterListener extends MediaCasterNotifyBase
	{
		@Override
		public void onMediaCasterCreate(IMediaCaster mediaCaster)
		{
			System.out.println("+++++++++++++++++++++++++++++++++++++onMediaCasterCreate: " + mediaCaster);
			// Detect missing url and shut down mediaCaster otherwise we have to wait for it to time out.
			if (mediaCaster instanceof LiveMediaStreamReceiver && !((LiveMediaStreamReceiver)mediaCaster).isTryConnect())
			{
				final LiveMediaStreamReceiver liveMediaStreamReceiver = (LiveMediaStreamReceiver)mediaCaster;
				System.out.println("onMediaCasterCreate shutting down mediaCaster");
				appInstance.getVHost().getThreadPool().execute(new Runnable() {

					@Override
					public void run()
					{
						MediaCasterStreamItem item = liveMediaStreamReceiver.getMediaCasterStreamItem();
						appInstance.getMediaCasterStreams().remove(item);
						item.shutdown(false);
					}
				});
			}
		}

		@Override
		public void onRegisterPlayer(IMediaCaster mediaCaster, IMediaStreamPlay player)
		{
			String mediaCasterId = mediaCaster.getMediaCasterId();
			if (mediaCaster instanceof LiveMediaStreamReceiver)
			{
				synchronized(lock)
				{
					List<IMediaStreamPlay> localPlayers = players.get(mediaCasterId);
					if (localPlayers == null)
					{
						localPlayers = new ArrayList<IMediaStreamPlay>();
						players.put(mediaCasterId, localPlayers);
					}
					localPlayers.add(player);
				}
			}
		}

		@Override
		public void onUnRegisterPlayer(IMediaCaster mediaCaster, IMediaStreamPlay player)
		{
			String mediaCasterId = mediaCaster.getMediaCasterId();
			if (mediaCaster instanceof LiveMediaStreamReceiver)
			{
				synchronized(lock)
				{
					List<IMediaStreamPlay> localPlayers = players.get(mediaCasterId);
					if (localPlayers != null)
					{
						localPlayers.remove(player);
					}
				}
			}
		}

		@Override
		public void onConnectSuccess(IMediaCaster mediaCaster)
		{
			if (mediaCaster instanceof LiveMediaStreamReceiver)
			{
				if(debug)
				{
					getLogger().info(ModuleStreamResolver.MODULE_NAME + "onConnectSuccess name: " + mediaCaster.getMediaCasterId());
					getLogger().info(ModuleStreamResolver.MODULE_NAME + "onConnectSuccess isTryConnect: " + ((LiveMediaStreamReceiver)mediaCaster).isTryConnect());
					getLogger().info(ModuleStreamResolver.MODULE_NAME + "onConnectSuccess isValid: " + mediaCaster.getMediaCasterStreamItem().isValid());
				}
				LiveMediaStreamReceiver liveMediaStreamReceiver = (LiveMediaStreamReceiver)mediaCaster;

				int retryCount = liveMediaStreamReceiver.getProperties().getPropertyInt("connectRetryCount", 0);
				
				if(++retryCount > maxRetries)
				{
					System.out.println("onConnectSuccess calling resolveMediaCasterURL: " + retryCount);
					resolveMediaCasterURL(liveMediaStreamReceiver);
				}
				
				liveMediaStreamReceiver.getProperties().setProperty("connectRetryCount", retryCount);
			}
		}

		@Override
		public void onConnectFailure(final IMediaCaster mediaCaster)
		{
			if (mediaCaster instanceof LiveMediaStreamReceiver)
			{
				System.out.println("onConnectFalure calling resolveMediaCasterURL");
				resolveMediaCasterURL((LiveMediaStreamReceiver)mediaCaster);
			}
		}
		
		private void resolveMediaCasterURL(final LiveMediaStreamReceiver mediaCaster)
		{
			appInstance.getVHost().getThreadPool().execute(new Runnable() {

				@Override
				public void run()
				{
					mediaCaster.getProperties().remove("connectRetryCount");
					
					MediaCasterStreamId streamId = MediaCasterItem.parseIdString(mediaCaster.getMediaCasterId());
					String streamName = streamId.getName();
					String packetizer = streamId.getLiveStreamPacketizer();
					
					if (debug)
						logger.info(ModuleStreamResolver.MODULE_NAME + " **Resolving stream name: " + streamName);
					String newURL = lookupURL(streamName, packetizer);
					if(StringUtils.isEmpty(newURL))
					{
						if (debug)
							logger.info(ModuleStreamResolver.MODULE_NAME + " **Shutting down players: " + streamName);
						shutdownPlayers(mediaCaster);
						mediaCaster.setTryConnect(false);
					}
					else
					{
						mediaCaster.resolveURL();
					}
				}
			});
		}
		
		private void shutdownPlayers(final IMediaCaster mediaCaster)
		{
			System.out.println("Shutting down MediaCasterPlayers. Waiting for lock");
			String mediaCasterId = mediaCaster.getMediaCasterId();
			List<IMediaStreamPlay> localPlayers = null;
			synchronized(lock)
			{
				localPlayers = players.remove(mediaCasterId);
			}
			if (localPlayers != null)
			{
				WMSReadWriteLock writeLock = appInstance.getClientsLockObj();
				writeLock.writeLock().lock();
				try
				{
					System.out.println("Shutting down MediaCasterPlayers. Got lock");
					for (IMediaStreamPlay player : localPlayers)
					{
						IMediaStream stream = player.getParent();
						if (stream != null)
						{
							if (stream.getClient() != null)
							{
								stream.getClient().setShutdownClient(true);
							}
							if (stream.getRTPStream() != null)
							{
								appInstance.getVHost().getRTPContext().shutdownRTPSession(stream.getRTPStream().getSession());
							}
							if (stream.getHTTPStreamerSession() != null)
							{
								stream.getHTTPStreamerSession().rejectSession();
								stream.getHTTPStreamerSession().setDeleteSession();
							}
						}
					}
				}
				catch (Exception e)
				{
					getLogger().error("ModuleStreamResolver.MediaCasterListener.shutdownMediaCaster exception: " + e.getMessage(), e);
				}
				finally
				{
					writeLock.writeLock().unlock();
				}
			}
		}

		

		@Override
		public void onMediaCasterDestroy(IMediaCaster mediaCaster)
		{
			System.out.println("+++++++++++++++++++++++++++++++++++++onMediaCasterDestroy: " + mediaCaster);
			String mediaCasterId = mediaCaster.getMediaCasterId();
			String streamName = MediaCasterItem.parseIdString(mediaCasterId).getName();
			if (mediaCaster instanceof LiveMediaStreamReceiver)
			{
				synchronized(lock)
				{
					urls.remove(streamName);
					players.remove(mediaCasterId);
				}
			}
		}

		@Override
		public void onStreamStart(IMediaCaster mediaCaster)
		{
			if (mediaCaster instanceof LiveMediaStreamReceiver)
			{
				((LiveMediaStreamReceiver)mediaCaster).getProperties().remove("connectRetryCount");
			}
		}
		
		@Override
		public void onStreamStop(IMediaCaster mediaCaster)
		{
			if (mediaCaster instanceof LiveMediaStreamReceiver && mediaCaster.getStreamTimeoutReason() != IMediaCaster.STREAMTIMEOUTREASON_GOOD)
					resolveMediaCasterURL((LiveMediaStreamReceiver)mediaCaster);
		}

	}
	
	class Lookup extends Thread
	{
		long startTime;
		String streamName;
		String name;
		String packetizer;
		String url;
		
		Lookup(String streamName, String packetizer)
		{
			startTime = System.currentTimeMillis();
			this.streamName = streamName;
			if(packetizer != null && streamName.startsWith(packetizer))
			{
				name = streamName.substring(packetizer.length() + 1);
			}
			else
			{
				name = streamName;
			}
			this.packetizer = packetizer;
		}
		
		public void run()
		{
			final List<Future<String>> futures = new ArrayList<Future<String>>();
			final CompletionService<String> ecs = new ExecutorCompletionService<String>(Executors.newCachedThreadPool());

			try
			{
				String hostNames = getNewURLs();

				if (debug)
					logger.info("**Checking hostnames ... " + hostNames);

				if (!StringUtils.isEmpty(hostNames))
				{
					String[] hosts = hostNames.split(",");
					for (String host : hosts)
					{
						futures.add(ecs.submit(new StreamRequest(host, name, packetizer, defaultApplicationInstanceName, defaultApplicationName, port, timeout)));
					}

					// wait for first positive result to return.
					while (!futures.isEmpty())
					{
						Future<String> result = ecs.take();
						futures.remove(result);
						url = result.get();
						if (url != null)
						{
							if (!url.toLowerCase().startsWith("rtmp") && !url.toLowerCase().startsWith("wowz"))
							{
								url = protocol + "://" + url;
								url = url.trim();
								synchronized(lock)
								{
									urls.put(streamName, url);
								}
							}
							break;
						}
					}
				}
			}
			catch (Exception e)
			{

			}
			finally
			{
				if (!futures.isEmpty())
				{
					for(Future<String> future : futures)
					{
						future.cancel(true);
					}
					futures.clear();
				}
				synchronized(lock)
				{
					lookups.remove(streamName);
				}
			}
		}
	}

	class StreamRequest implements Callable<String>
	{
		private String appName;
		private String appInstance;
		private String streamName;
		private String packetizer;
		private String host;
		private int port;
		private int udpTimeout;

		public StreamRequest(String host, String streamName, String packetizer, String appInstance, String appName, int port, int udpTimeout)
		{
			this.streamName = streamName;
			this.packetizer = packetizer;
			this.appInstance = appInstance;
			this.appName = appName;
			this.port = port;
			this.udpTimeout = udpTimeout;
			this.host = host;
		}

		private String sendUDPMessage()
		{
			if (debug)
				logger.info(MODULE_NAME + "[sendUDPMessage] this.host:: " + host + " :: this.port :: " + port);
			UDPClient udp = new UDPClient(host, port, udpTimeout, logger, debug);

			String remoteStreamName = streamName;

			if (remoteStreamName.contains("/"))
			{
				String[] parts = remoteStreamName.split("/");
				remoteStreamName = parts[parts.length - 1].trim();
			}

			Message message = new Message();
			message.streamName = remoteStreamName;
			message.appInstance = appInstance;
			message.appName = appName;
			message.packetizer = packetizer;

			String response = udp.send(message);

			if (debug)
				logger.info(MODULE_NAME + "[sendUDPMessage] response from host :: " + host + " :: " + response);

			if (response != null && response.length() > 0)
			{
				try
				{
					JSON json = new JSON(response);
					if (!json.containsKey("error"))
					{
						String server = json.getString("server");
						return server;
					}
				}
				catch (Exception e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			return null;
		}

		@Override
		public String call() throws Exception
		{
			return sendUDPMessage();
		}
	}

	public static String MODULE_NAME = "ModuleStreamResolver";
	public static String MODULE_PROPERTY_PREFIX = "wowzaResolver";
	private static final int _UDP_PORT = 9777;
	private static final int _UDP_REQUEST_TIMEOUT = 2000;
	private static final String _PROTOCOL = "rtmp";

	private Object lock = new Object();
	private Map<String, Lookup> lookups = new HashMap<String, Lookup>();
	private Map<String, String> urls = new HashMap<String, String>();
	private Map<String, List<IMediaStreamPlay>> players = new HashMap<String, List<IMediaStreamPlay>>();
	private int port;
	private int timeout;
	private int maxRetries = 2;
	private String protocol = "";
	private IApplicationInstance appInstance;
	private String targetPath = null;
	private boolean useExternalFile = false;
	private long lastFileModification = 0L;
	private String lastOriginSetting = "";
	private WMSLogger logger = null;
	private boolean debug = false;
	private String defaultApplicationName;
	private String defaultApplicationInstanceName;

	public void onAppStart(IApplicationInstance appInstance)
	{
		this.appInstance = appInstance;
		logger = WMSLoggerFactory.getLoggerObj(appInstance);
		port = appInstance.getProperties().getPropertyInt(MODULE_PROPERTY_PREFIX + "UDPClientPort", _UDP_PORT);
		timeout = appInstance.getProperties().getPropertyInt(MODULE_PROPERTY_PREFIX + "UDPClientTimeout", _UDP_REQUEST_TIMEOUT);
		protocol = appInstance.getProperties().getPropertyStr(MODULE_PROPERTY_PREFIX + "Protocol", _PROTOCOL);
		maxRetries = appInstance.getProperties().getPropertyInt(MODULE_PROPERTY_PREFIX + "ConnectMaxRetries", maxRetries);
		debug = appInstance.getProperties().getPropertyBoolean(MODULE_PROPERTY_PREFIX + "DebugLog", debug);
		if(logger.isDebugEnabled())
			debug = true;
		if(debug)
			MediaCaster.debugLog = true;

		if (appInstance.getProperties().containsKey(MODULE_PROPERTY_PREFIX + "ConfTargetPath"))
		{
			useExternalFile = true;
			targetPath = appInstance.decodeStorageDir(appInstance.getProperties().getPropertyStr(MODULE_PROPERTY_PREFIX + "ConfTargetPath", null));
		}

		defaultApplicationName = appInstance.getProperties().getPropertyStr(MODULE_PROPERTY_PREFIX + "OriginApplicationName", appInstance.getApplication().getName());
		defaultApplicationInstanceName = appInstance.getProperties().getPropertyStr(MODULE_PROPERTY_PREFIX + "OriginApplicationInstanceName", appInstance.getName());		
		
//		appInstance.getVHost().getMediaCasterList().getMediaCasterDef("liverepeater").setBaseClass("com.wowza.wms.plugin.streamresolver.StreamResolverLiveMediaStreamReceiver");
//		appInstance.getVHost().getMediaCasterList().getMediaCasterDef("httprepeater").setBaseClass("com.wowza.wms.plugin.streamresolver.StreamResolverLiveMediaStreamReceiver");
//		appInstance.setMediaCasterValidator(new Validator(appInstance.getMediaCasterValidator()));
		
		appInstance.setStreamNameAliasProvider(new AliasProvider());
		appInstance.addMediaCasterListener(new MediaCasterListener());
	}

	private String getNewURLs()
	{
		if (useExternalFile)
		{
			return getNewURLsFromConfig();
		}
		return getNewPropertyURLs();
	}

	private String getNewURLsFromConfig()
	{
		long lastModified = 0L;

		try
		{
			if (targetPath == null || targetPath.isEmpty())
			{
				logger.error(ModuleStreamResolver.MODULE_NAME + ".getNewURLsFromConfig could not find valid target file " + targetPath);
				return null;
			}

			File checkForFile = new File(targetPath);
			if (!checkForFile.exists())
			{
				logger.error(ModuleStreamResolver.MODULE_NAME + ".getNewURLsFromConfig could not find valid target file " + targetPath);
				return null;
			}

			lastModified = checkForFile.lastModified();
			if (lastFileModification != 0L && lastFileModification == lastModified)
			{
				return lastOriginSetting;
			}
		}
		catch (Exception ex)
		{
			logger.error(ModuleStreamResolver.MODULE_NAME + ".getNewURLsFromConfig Exception", ex);
			return null;
		}

		String urls = "";
		FileInputStream in = null;
		BufferedReader br = null;
		try
		{
			in = new FileInputStream(targetPath);
			br = new BufferedReader(new InputStreamReader(in));
			String urlItem = "";

			while ((urlItem = br.readLine()) != null)
			{
				urlItem = urlItem.trim();
				if (!urlItem.startsWith("#") && urlItem.length() > 0)
				{
					urls += urlItem + ",";
				}
			}
			urls = urls.replaceAll("[\\,\\s]+$", "");
		}
		catch (Exception e)
		{
			logger.info(ModuleStreamResolver.MODULE_NAME + ".getNewURLsFromConfig Exception", e);
		}
		finally
		{
			try
			{
				if (br != null)
					br.close();
			}
			catch (IOException e)
			{
			}
			br = null;
			try
			{
				if (in != null)
					in.close();
			}
			catch (IOException e)
			{
			}
			in = null;
		}

		logger.info(ModuleStreamResolver.MODULE_NAME + ".getNewURLsFromConfig " + urls);
		lastFileModification = lastModified;
		lastOriginSetting = urls;

		return urls;
	}

	private String getNewPropertyURLs()
	{

		String fileURL = Bootstrap.getServerHome(Bootstrap.CONFIGHOME) + "/conf/" + appInstance.getApplication().getName() + "/Application.xml";
        
        if (debug){
            logger.info(MODULE_NAME + ".getNewURLs: fileURL " + fileURL);
        }

		if (fileURL != null)
		{
			try
			{
				DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder domBuilder = domFactory.newDocumentBuilder();
				Document doc = domBuilder.parse(fileURL);

				XPathFactory factory = XMLUtils.newXPathFactory();
				factory.newXPath();

				if (doc != null)
				{
					Element root = doc.getDocumentElement();
					XMLUtils.getVersion(root);

					String propertiesXPath = "/Root/Application/Properties/Property";
					WMSProperties properties = new WMSProperties();
					XMLUtils.loadConfigProperies(root, propertiesXPath, properties);
					return properties.getPropertyStr(MODULE_PROPERTY_PREFIX + "OriginServers", null);
				}
			}
			catch (Exception e)
			{
				logger.error(MODULE_NAME + "getNewURLs: error parsing app config file: (" + fileURL + ")", e);
			}
		}

		return appInstance.getProperties().getPropertyStr(MODULE_PROPERTY_PREFIX + "OriginServers", null);
	}


	private String lookupURL(String nameContext, String packetizer)
	{
		Lookup lookup = null;		
		synchronized(lock)
		{
			lookup = lookups.get(nameContext);
			if(lookup == null)
			{
				lookup = new Lookup(nameContext, packetizer);
				lookup.start();
				lookups.put(nameContext, lookup);
			}
			else if(urls.containsKey(nameContext))
			{
				return urls.get(nameContext);
			}
		}
		try
		{
			lookup.join(timeout);
		}
		catch (InterruptedException e)
		{
			
		}
		
		return StringUtils.isEmpty(lookup.url) ? "" : lookup.url;
	}
}
