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
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.application.WMSProperties;
import com.wowza.wms.bootstrap.Bootstrap;
import com.wowza.wms.client.IClient;
import com.wowza.wms.httpstreamer.model.HTTPStreamerItem;
import com.wowza.wms.httpstreamer.model.IHTTPStreamerSession;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.mediacaster.IMediaCaster;
import com.wowza.wms.mediacaster.MediaCasterItem;
import com.wowza.wms.mediacaster.MediaCasterNotifyBase;
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
			return lookupStreamName(streamName);
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
			return lookupStreamName(streamName);
		}

		@Override
		public String resolvePlayAlias(IApplicationInstance appInstance, String name, IHTTPStreamerSession httpSession)
		{
			HTTPStreamerItem item = httpSession.getHTTPStreamerAdapter().getHTTPStreamerItem();
			String packetizer = item.getLiveStreamPacketizer();
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
			return lookupStreamName(streamName, packetizer);
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
			return lookupStreamName(streamName);
		}

		@Override
		public String resolveStreamAlias(IApplicationInstance appInstance, String name)
		{
			return getOriginURLs(name);
		}

		@Override
		public String resolveStreamAlias(IApplicationInstance appInstance, String name, IMediaCaster mediaCaster)
		{
			return getOriginURLs(name);
		}
	}

	class MediaCasterListener extends MediaCasterNotifyBase
	{
		@Override
		public void onMediaCasterCreate(IMediaCaster mediaCaster)
		{
			if (mediaCaster instanceof LiveMediaStreamReceiver && !((LiveMediaStreamReceiver)mediaCaster).isTryConnect())
			{
				shutdownMediaCaster(mediaCaster);
			}
		}

		@Override
		public void onRegisterPlayer(IMediaCaster mediaCaster, IMediaStreamPlay player)
		{
			String mediaCasterId = mediaCaster.getMediaCasterId();
			if (mediaCaster instanceof LiveMediaStreamReceiver)
			{
				synchronized(players)
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
				synchronized(players)
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
		public void onConnectStart(IMediaCaster mediaCaster)
		{
			String streamName = MediaCasterItem.parseIdString(mediaCaster.getMediaCasterId()).getName();
			if (mediaCaster instanceof LiveMediaStreamReceiver)
			{
				if (debug)
					logger.info(ModuleStreamResolver.MODULE_NAME + "**Resolving stream name: " + streamName);

				((LiveMediaStreamReceiver)mediaCaster).resolveURL();
				synchronized(urls)
				{
					int idx = ((LiveMediaStreamReceiver)mediaCaster).getLiveMediaStreamURLIndex();
					List<String> originList = urls.get(streamName);
					if (originList != null && !originList.isEmpty())
					{
						((LiveMediaStreamReceiver)mediaCaster).setLiveMediaStreamURLIndex(idx % originList.size());
					}
				}
			}
		}

		@Override
		public void onConnectFailure(IMediaCaster mediaCaster)
		{
			String streamName = MediaCasterItem.parseIdString(mediaCaster.getMediaCasterId()).getName();
			if (mediaCaster instanceof LiveMediaStreamReceiver)
			{
				synchronized(urls)
				{
					int idx = ((LiveMediaStreamReceiver)mediaCaster).getLiveMediaStreamURLIndex();
					List<String> originList = urls.get(streamName);
					if (originList != null && originList.size() > idx)
					{
						originList.remove(idx);
						((LiveMediaStreamReceiver)mediaCaster).resolveURL();
						if (originList.isEmpty())
						{
							urls.remove(streamName);
							shutdownMediaCaster(mediaCaster);
						}
					}
				}
			}
		}

		@Override
		public void onMediaCasterDestroy(IMediaCaster mediaCaster)
		{
			String mediaCasterId = mediaCaster.getMediaCasterId();
			String streamName = MediaCasterItem.parseIdString(mediaCasterId).getName();
			if (mediaCaster instanceof LiveMediaStreamReceiver)
			{
				synchronized(urls)
				{
					urls.remove(streamName);
				}
				synchronized(players)
				{
					players.remove(mediaCasterId);
				}
			}
		}

		@Override
		public void onStreamStop(IMediaCaster mediaCaster)
		{
			String streamName = MediaCasterItem.parseIdString(mediaCaster.getMediaCasterId()).getName();
			if (mediaCaster instanceof LiveMediaStreamReceiver)
			{
				synchronized(urls)
				{
					int idx = ((LiveMediaStreamReceiver)mediaCaster).getLiveMediaStreamURLIndex();
					List<String> originList = urls.get(streamName);
					if (originList != null && originList.size() > idx)
					{
						originList.remove(idx);
						if (originList.isEmpty())
						{
							urls.remove(streamName);
							shutdownMediaCaster(mediaCaster);
						}
					}
				}
			}
		}

		private void shutdownMediaCaster(final IMediaCaster mediaCaster)
		{
			appInstance.getVHost().getThreadPool().execute(new Runnable()
			{

				@Override
				public void run()
				{
					String mediaCasterId = mediaCaster.getMediaCasterId();
					MediaCasterStreamItem item = mediaCaster.getMediaCasterStreamItem();
					WMSReadWriteLock lock = appInstance.getMediaCasterStreams().getLock();
					lock.writeLock().lock();
					try
					{
						synchronized(players)
						{
							List<IMediaStreamPlay> localPlayers = players.remove(mediaCasterId);
							if (localPlayers != null)
							{
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
						}
						appInstance.getMediaCasterStreams().remove(item);
						item.setValid(false);
						item.shutdown(false);
					}
					catch (Exception e)
					{
						getLogger().error("ModuleStreamResolver.MediaCasterListener.shutdownMediaCaster exception: " + e.getMessage(), e);
					}
					finally
					{
						lock.writeLock().unlock();
					}
				}
			});
		}
	}

	private class StreamRequest implements Callable<String>
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

	private Map<String, List<String>> lookups = new HashMap<String, List<String>>();
	private Map<String, List<String>> urls = new HashMap<String, List<String>>();
	private Map<String, List<IMediaStreamPlay>> players = new HashMap<String, List<IMediaStreamPlay>>();
	private int port;
	private int timeout;
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
		debug = appInstance.getProperties().getPropertyBoolean(MODULE_PROPERTY_PREFIX + "DebugLog", debug);
		if (logger.isDebugEnabled())
			debug = true;
		;

		if (appInstance.getProperties().containsKey(MODULE_PROPERTY_PREFIX + "ConfTargetPath"))
		{
			useExternalFile = true;
			targetPath = appInstance.decodeStorageDir(appInstance.getProperties().getPropertyStr(MODULE_PROPERTY_PREFIX + "ConfTargetPath", null));
		}

		defaultApplicationName = appInstance.getProperties().getPropertyStr(MODULE_PROPERTY_PREFIX + "OriginApplicationName", appInstance.getApplication().getName());
		defaultApplicationInstanceName = appInstance.getProperties().getPropertyStr(MODULE_PROPERTY_PREFIX + "OriginApplicationInstanceName", appInstance.getName());

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
		logger.error(MODULE_NAME + "getNewURLs: fileURL " + fileURL);

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

	private boolean isStreamAvailable(final String streamName, final List<String> originList, final String packetizer)
	{
		boolean available = false;
		String nameContext = packetizer != null ? packetizer + "_" + streamName : streamName;
		final List<Future<String>> futures = new ArrayList<Future<String>>();
		final CompletionService<String> ecs = new ExecutorCompletionService<String>(Executors.newCachedThreadPool());

		String hostNames = getNewURLs();

		if (debug)
			logger.info("**Checking hostnames ... " + hostNames);

		if (!StringUtils.isEmpty(hostNames))
		{
			String[] hosts = hostNames.split(",");
			try
			{
				for (String host : hosts)
				{
					futures.add(ecs.submit(new StreamRequest(host, streamName, packetizer, defaultApplicationInstanceName, defaultApplicationName, port, timeout)));
				}

				// wait for first positive result to return.
				while (!futures.isEmpty())
				{
					Future<String> result = ecs.take();
					futures.remove(result);
					String origin = result.get();
					if (origin != null)
					{
						synchronized(originList)
						{
							originList.add(origin);
						}
						synchronized(urls)
						{
							urls.put(nameContext, originList);

						}
						available = true;
						break;
					}
				}
			}
			catch (Exception e)
			{

			}
			finally
			{
				// move ecs execution to own thread to finish up.
				if (available && !futures.isEmpty())
				{
					appInstance.getVHost().getThreadPool().execute(new Runnable()
					{

						@Override
						public void run()
						{
							while (!futures.isEmpty())
							{
								try
								{
									Future<String> result = ecs.take();
									futures.remove(result);
									String origin = result.get();
									if (origin != null)
									{
										synchronized(originList)
										{
											originList.add(origin);
										}
									}
								}
								catch (Exception e)
								{
									break;
								}
							}
						}
					});
				}
				synchronized(originList)
				{
					lookups.remove(nameContext);
					originList.notifyAll();
				}
			}
		}
		return available;
	}

	private String lookupStreamName(String name)
	{
		return lookupStreamName(name, null);
	}

	private String lookupStreamName(String name, String packetizer)
	{
		name = appInstance.getProperties().getPropertyStr(MODULE_PROPERTY_PREFIX + "OriginStreamName", name);
		String nameContext = packetizer != null ? packetizer + "_" + name : name;

		List<String> originList = null;
		boolean newLookup = false;

		synchronized(urls)
		{
			originList = urls.get(nameContext);
			if (originList != null)
				return nameContext;

			originList = lookups.get(nameContext);
			if (originList == null)
			{
				originList = new ArrayList<String>();
				lookups.put(nameContext, originList);
				newLookup = true;
			}
		}

		if (!newLookup)
		{
			synchronized(originList)
			{
				try
				{
					originList.wait();
				}
				catch (InterruptedException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		else
		{
			if (!isStreamAvailable(name, originList, packetizer))
				return null;
		}

		synchronized(originList)
		{
			if (originList.isEmpty())
			{
				if (debug)
					logger.warn(ModuleStreamResolver.MODULE_NAME + ".getStreamName() stream not available[" + nameContext + "]");
				return null;
			}
		}
		if (debug)
			logger.info(ModuleStreamResolver.MODULE_NAME + ".getStreamName[" + nameContext + "] ");
		return nameContext;
	}

	private String getOriginURLs(String nameContext)
	{
		String ret = "";
		List<String> originList = null;
		List<String> originListCopy = new ArrayList<String>();
		synchronized(urls)
		{
			originList = urls.get(nameContext);
		}
		if (originList != null)
		{
			synchronized(originList)
			{
				originListCopy.addAll(originList);
			}
		}
		if (!originListCopy.isEmpty())
		{
			for (int i = 0; i < originListCopy.size(); i++)
			{
				if (i >= 1)
				{
					ret += "|";
				}
				String url = originListCopy.get(i);
				if (!url.startsWith("rtmp") && !url.startsWith("wowz"))
				{
					url = protocol + "://" + url;
					url = url.trim();
				}
				ret += url;
			}
		}
		if (debug)
		{
			logger.info(ModuleStreamResolver.MODULE_NAME + ".getOriginURLs " + ret);
		}
		return ret;
	}
}
