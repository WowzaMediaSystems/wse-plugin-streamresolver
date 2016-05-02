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

import com.wowza.util.StringUtils;
import com.wowza.util.SystemUtils;
import com.wowza.util.XMLUtils;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.application.WMSProperties;
import com.wowza.wms.bootstrap.Bootstrap;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.mediacaster.IMediaCaster;
import com.wowza.wms.mediacaster.MediaCasterNotifyBase;
import com.wowza.wms.mediacaster.wowza.LiveMediaStreamReceiver;
import com.wowza.wms.module.ModuleBase;
import com.wowza.wms.stream.IMediaReader;
import com.wowza.wms.stream.IMediaStreamNameAliasProvider;
import com.wowza.wms.stream.MediaStream;
import com.wowza.wms.util.ModuleUtils;

public class ModuleStreamResolver extends ModuleBase implements IMediaStreamNameAliasProvider
{
	public static String MODULE_NAME = "ModuleStreamResolver";
	public static String MODULE_PROPERTY_PREFIX = "wowzaResolver";
	private static final int _UDP_PORT = 9777;
	private static final int _UDP_REQUEST_TIMEOUT = 2000;
	private static final String _PROTOCOL = "rtmp";

	private Map<String, List<String>> urls = new HashMap<String, List<String>>();
	private Object lock = new Object();
	private int port;
	private int timeout;
	private String protocol = "";
	private IApplicationInstance appInstance;
	private String targetPath = null;
	private boolean useExternalFile = false;
	private long lastFileModification = 0L;
	private String lastOriginSetting = "";

	public void onAppStart(IApplicationInstance appInstance)
	{
		this.appInstance = appInstance;
		this.port = appInstance.getProperties().getPropertyInt(MODULE_PROPERTY_PREFIX + "UDPClientPort", _UDP_PORT);
		this.timeout = appInstance.getProperties().getPropertyInt(MODULE_PROPERTY_PREFIX + "UDPClientTimeout", _UDP_REQUEST_TIMEOUT);
		this.protocol = appInstance.getProperties().getPropertyStr(MODULE_PROPERTY_PREFIX + "Protocol", _PROTOCOL);

		if(appInstance.getProperties().containsKey(MODULE_PROPERTY_PREFIX + "ConfTargetPath")){
			this.useExternalFile = true;
			this.targetPath = appInstance.decodeStorageDir(appInstance.getProperties().getPropertyStr(MODULE_PROPERTY_PREFIX + "ConfTargetPath", null));
		}

		appInstance.setStreamNameAliasProvider(this);
		appInstance.addMediaCasterListener(new MediaCasterListener());
	}


	private String getNewURLs(){
		if(this.useExternalFile){
			return this.getNewURLsFromConfig();
		}
		return this.getNewPropertyURLs();
	}

	private String getNewURLsFromConfig(){
		long lastModified = 0L;

		try{
			if(this.targetPath == null || this.targetPath.isEmpty()){
				getLogger().error(ModuleStreamResolver.MODULE_NAME+".getNewURLsFromConfig could not find valid target file "+this.targetPath);
				return null;
			}

			File checkForFile = new File(this.targetPath);
			if(!checkForFile.exists()){
				getLogger().error(ModuleStreamResolver.MODULE_NAME+".getNewURLsFromConfig could not find valid target file "+this.targetPath);
				return null;
			}

			lastModified = checkForFile.lastModified();
			if(lastFileModification!= 0L && lastFileModification == lastModified){
				return lastOriginSetting;
			}
		}
		catch(Exception ex){
			getLogger().error(ModuleStreamResolver.MODULE_NAME+".getNewURLsFromConfig Exception",ex);
			return null;
		}

		String urls = "";
		FileInputStream in = null;
		BufferedReader br = null;
		try {
			in = new FileInputStream(this.targetPath);
			br = new BufferedReader(new InputStreamReader(in));
			String urlItem = "";

			while ((urlItem = br.readLine()) != null) {
				urlItem = urlItem.trim();
				if (!urlItem.startsWith("#") && urlItem.length() > 0) {
					urls+=urlItem+",";
				}
			}
			urls = urls.replaceAll("[\\,\\s]+$","");
		}
		catch (Exception e) {
			getLogger().info(ModuleStreamResolver.MODULE_NAME+".getNewURLsFromConfig Exception",e);
		}
		finally {
			try {
				if(br != null)
					br.close();
			} catch (IOException e) {
			}
			br = null;
			try {
				if(in != null)
					in.close();
			} catch (IOException e) {
			}
			in = null;
		}

		getLogger().error(ModuleStreamResolver.MODULE_NAME+".getNewURLsFromConfig "+urls);
		lastFileModification = lastModified;
		lastOriginSetting = urls;

		return urls;
	}

	private String getNewPropertyURLs()
	{

		String fileURL = Bootstrap.getServerHome(Bootstrap.CONFIGHOME) + "/conf/" + appInstance.getApplication().getName() + "/Application.xml";
		WMSLoggerFactory.getLogger(getClass()).error(MODULE_NAME + "getNewURLs: fileURL " + fileURL);

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
				WMSLoggerFactory.getLogger(getClass()).error(MODULE_NAME + "getNewURLs: error parsing app config file: (" + fileURL + ")", e);
			}
		}

		return appInstance.getProperties().getPropertyStr(MODULE_PROPERTY_PREFIX + "OriginServers", null);
	}

	private boolean isStreamAvaliable(final String name)
	{
		boolean available = false;
		final List<Future<String>> futures = new ArrayList<Future<String>>();
		final CompletionService<String> ecs = new ExecutorCompletionService<String>(Executors.newCachedThreadPool());
		final List<String> originUrls = new ArrayList<String>();

		String hostNames = this.getNewURLs();

		getLogger().info("**Checking hostnames ... "+hostNames);

		String applicationName = appInstance.getProperties().getPropertyStr(MODULE_PROPERTY_PREFIX + "OriginApplicationName", appInstance.getApplication().getName());
		String applicationInstanceName = appInstance.getProperties().getPropertyStr(MODULE_PROPERTY_PREFIX + "OriginApplicationInstanceName", appInstance.getName());
		String streamName = appInstance.getProperties().getPropertyStr(MODULE_PROPERTY_PREFIX + "OriginStreamName", name);
		if (!StringUtils.isEmpty(hostNames))
		{
			String[] hosts = hostNames.split(",");
			try
			{
				for (String host : hosts)
				{
					futures.add(ecs.submit(new StreamRequest(host, streamName, applicationInstanceName, applicationName, port, timeout)));
				}

				// wait for first positive result to return.
				while (!futures.isEmpty())
				{
					Future<String> result = ecs.take();
					futures.remove(result);
					String url = result.get();
					if (url != null)
					{
						synchronized(lock)
						{
							originUrls.add(url);
							urls.put(name, originUrls);
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
									String url = result.get();
									if (url != null)
									{
										synchronized(lock)
										{
											originUrls.add(url);
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
			}
		}
		return available;
	}

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

			if(appInstance.getMediaReaderContentType(streamExt) == IMediaReader.CONTENTTYPE_MEDIALIST)
				return name;
		}
		return this.getStreamName(streamName);
	}

	@Override
	public String resolveStreamAlias(IApplicationInstance appInstance, String name)
	{
		return this.getOriginURLs(name);
	}

	private String getStreamName(String name){

		if (appInstance.getStreams().getStream(name) == null)
		{
			if (!isStreamAvaliable(name)){
				getLogger().info(ModuleStreamResolver.MODULE_NAME+".getStreamName() stream not available");
				return null;
			}
		}
		getLogger().info(ModuleStreamResolver.MODULE_NAME+".getStreamName["+name+"] ");
		return name;
	}

	private String getOriginURLs(String name){
		String ret = "";
		synchronized(lock)
		{
			List<String> originUrls = urls.get(name);
			if(originUrls!=null){
				for (int i = 0; i < originUrls.size(); i++)
				{
					if (i >= 1){
						ret += "|";
					}
					String url = originUrls.get(i);
					if(!url.startsWith("rtmp") && !url.startsWith("wowz")){
						url = this.protocol+"://"+url;
						url = url.trim();
					}
					ret += url;
				}
			}
		}
		getLogger().info(ModuleStreamResolver.MODULE_NAME+".getOriginURLs "+ret);
		return ret;
	}

	private class MediaCasterListener extends MediaCasterNotifyBase
	{
		@Override
		public void onConnectStart(IMediaCaster mediaCaster)
		{
			String name = mediaCaster.getMediaCasterId();
			if (mediaCaster instanceof LiveMediaStreamReceiver)
			{
				getLogger().info(ModuleStreamResolver.MODULE_NAME+"**Resolving stream name: "+mediaCaster.getStream().getName());
				((LiveMediaStreamReceiver)mediaCaster).resolveURL();
				synchronized(lock)
				{
					int idx = ((LiveMediaStreamReceiver)mediaCaster).getLiveMediaStreamURLIndex();
					List<String> originUrls = urls.get(name);
					if (originUrls != null && !originUrls.isEmpty())
					{
						((LiveMediaStreamReceiver)mediaCaster).setLiveMediaStreamURLIndex(idx % originUrls.size());
					}
				}
			}
		}

		@Override
		public void onMediaCasterDestroy(IMediaCaster mediaCaster)
		{
			String name = mediaCaster.getMediaCasterId();
			synchronized(lock)
			{
				urls.remove(name);
			}
		}
	}

	private class StreamRequest implements Callable<String>
	{
		private String appName;
		private String appInstance;
		private String streamName;
		private String host;
		private int port;
		private int udpTimeout;

		public StreamRequest(String host, String streamName, String appInstance, String appName, int port, int udpTimeout)
		{
			this.streamName = streamName;
			this.appInstance = appInstance;
			this.appName = appName;
			this.port = port;
			this.udpTimeout = udpTimeout;
			this.host = host;
		}

		private String sendUDPMessage()
		{
			getLogger().info(MODULE_NAME + "[sendUDPMessage] this.host:: "+this.host+" :: this.port :: " + this.port);
			UDPClient udp = new UDPClient(this.host, this.port, this.udpTimeout, WMSLoggerFactory.getLogger(getClass()));

			String remoteStreamName = this.streamName;

			if (remoteStreamName.contains("/"))
			{
				String[] parts = remoteStreamName.split("/");
				remoteStreamName = parts[parts.length - 1].trim();
			}

			Message message = new Message();
			message.streamName = remoteStreamName;
			message.appInstance = appInstance;
			message.appName = appName;

			String responseMessage = udp.send(message);

			getLogger().info(MODULE_NAME + "[sendUDPMessage] response from host :: " + this.host + " :: " + responseMessage);

			if (responseMessage != null && responseMessage.length() > 0 && !responseMessage.toLowerCase().startsWith("error"))
			{
				return responseMessage;
			}
			return null;
		}

		@Override
		public String call() throws Exception
		{
			return sendUDPMessage();
		}
	}
}
