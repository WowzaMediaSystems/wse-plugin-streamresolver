# ModuleStreamResolver
The **ModuleStreamResolver** module for [Wowza Streaming Engine™ media server software](https://www.wowza.com/products/streaming-engine) enables edge servers in a live stream repeater (origin/edge) configuration to dynamically resolve client requests for streams to multiple origin servers.

## Prerequisites
Wowza Streaming Engine 4.0.0 or later is required.

## Installation
Copy the **wse-plugin-streamresolver.jar** file to your Wowza Streaming Engine **[install-dir]/lib/** folder. Do this on all origin and edge servers in your live stream repeater configuration.

## Wowza Streaming Engine edge server configuration
On each edge server in your live stream repeater setup, add the following module definition to your application configuration. See [Configure modules](https://www.wowza.com/forums/content.php?625-How-to-get-started-as-a-Wowza-Streaming-Engine-Manager-administrator#configModules) for details.

**Name** | **Description** | **Fully Qualified Class Name**
-----|-------------|---------------------------
ModuleStreamResolver | Resolves streams from origin dynamically. | com.wowza.wms.plugin.streamresolver.ModuleStreamResolver

## Wowza Streaming Engine origin server configuration
On each origin server in your live stream repeater setup, add the following server listener definition to your media server. See [Configure server listeners](https://www.wowza.com/forums/content.php?625-How-to-get-started-as-a-Wowza-Streaming-Engine-Manager-administrator#configListeners) for details.

| **Fully Qualified Class Name** |
|----------------------------|
| com.wowza.wms.example.module.ServerListenerLocateSourceStream |

## Wowza Streaming Engine edge server properties
After enabling the module on each edge server, you can adjust the default settings by adding the following properties to your application. See [Configure properties](https://www.wowza.com/forums/content.php?625-How-to-get-started-as-a-Wowza-Streaming-Engine-Manager-administrator#configProperties) for details.

**Path** | **Name** | **Type** | **Value** | **Notes**
-----|------|------|-------|------
Root/Application | wowzaResolverConfTargetPath | String | [path-to-origins.txt] | Path to a file where you define a list of origin servers (one per line). If not used, your **Application.xml** file configuration takes precedence. (default: not set)
Root/Application | wowzaResolverOriginServers | String | [server1.com,server2.com] | If the **wowzaResolverConfTargetPath** property isn't set, you can use this property to define a comma-delimited list of origin servers. (default: not set)
Root/Application | wowzaResolverProtocol | String | [protocol] | Protocol to use when connecting to origin servers (rtmp or wowz). (default: **rtmp**)
Root/Application | wowzaResolverUDPClientPort | Integer | [port] | UDP port to use for edge/origin communication. (default: **9777**)
Root/Application | wowzaResolverUDPClientTimeout | Integer | [milliseconds] | Time (in milliseconds) before timing out the connection to an origin server. (default: **2000**)
Root/Application | wowzaResolverOriginApplicationName | String | [originAppName] | (Optional) By default, the edge server application looks for streams on the origin server application with the same name. Use this property to define a different application name on the origin server in which to look for streams. (default: uses requested edge application name)
Root/Application | wowzaResolverOriginApplicationInstanceName | String | \_definst\_ | (Optional) By default, the edge server application looks for streams on the origin server application with the same application instance name. Use this property to define a different application instance name on the origin server in which to look for streams. (default: uses requested edge application instance name)
Root/Application | wowzaResolverOriginStreamName | String | [stream-name] | (Optional) By default, the edge server application looks for streams on the origin server application/application instance with the same stream name. Use this property to define a different stream name on the origin server to look for. (default: uses stream name requested from edge).

## Wowza Streaming Engine origin server properties
After enabling the server listener on each origin server, you can adjust the default settings by adding the following properties to your application. See [Configure properties](https://www.wowza.com/forums/content.php?625-How-to-get-started-as-a-Wowza-Streaming-Engine-Manager-administrator#configProperties) for details.

**Path** | **Name** | **Type** | **Value** | **Notes**
-----|------|------|-------|------
Root/Application | wowzaSourceStreamUDPListenerDebug | Boolean | false | Debug inbound connections to your origin server from edge requests. (default: **false**)
Root/Application | wowzaSourceStreamUDPListenerPort | Integer | [port] | UDP port to use for origin/edge communication. (default: **9777**)
Root/Application | wowzaSourceStreamHostName | String | [public-hostname.com] | The origin server's public hostname. (default: **null**)

## Usage
When a client/player requests a stream from an edge server, the edge will first look for existing streams that it has already requested for a match. If none are found, it will then query the defined list of origin servers to return the requested stream. The edge server will connect to the origin server that responds first with a valid stream, and then serve that stream to the client/player.

## More resources
[Wowza Streaming Engine Server-Side API Reference](https://www.wowza.com/resources/WowzaStreamingEngine_ServerSideAPI.pdf)

[How to extend Wowza Streaming Engine using the Wowza IDE](https://www.wowza.com/forums/content.php?759-How-to-extend-Wowza-Streaming-Engine-using-the-Wowza-IDE)

Wowza Media Systems™ provides developers with a platform to create streaming applications and solutions. See [Wowza Developer Tools](https://www.wowza.com/resources/developers) to learn more about our APIs and SDK.

## Contact
[Wowza Media Systems, LLC](https://www.wowza.com/contact)

## License
This code is distributed under the [Wowza Public License](https://github.com/WowzaMediaSystems/wse-plugin-streamresolver/blob/master/LICENSE.txt).
