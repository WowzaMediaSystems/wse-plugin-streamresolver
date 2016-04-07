# wse-plugin-streamresolver

StreamResolver is a Wowza Streaming Engine module that will dynamically determine where a streams origin is from a corresponding edge.

## Prerequisites

Wowza Streaming Engine 4.0.0 or later.

## Installation

Copy `wse-plugin-streamresolver.jar` to your Wowza Streaming Engine `[install-dir]/lib/` folder.

## Configuration

   ### Edge Configuration

   Add the following Module Definition to your Application configuration. See [Configure modules](http://www.wowza.com/forums/content.php?625-How-to-get-started-as-a-Wowza-Streaming-Engine-Manager-administrator#configModules) for details.
   
   Name | Description | Fully Qualified Class Name
   -----|-------------|---------------------------
   ModuleStreamResolver | Resolve streams from origin dynamically. | com.wowza.wms.plugin.streamresolver.ModuleStreamResolver
   
   ### Origin Configuration

   Add the following server listener definition to your Server. See [Configure server listeners](https://www.wowza.com/forums/content.php?625-How-to-get-started-as-a-Wowza-Streaming-Engine-Manager-administrator#configListeners) for details.
   
   | Fully Qualified Class Name |
   |----------------------------|
   | com.wowza.wms.example.module.ServerListenerLocateSourceStream |

## Properties

   ### Edge Properties

   Adjust the default settings by adding the following properties to your application. See [Configure properties](http://www.wowza.com/forums/content.php?625-How-to-get-started-as-a-Wowza-Streaming-Engine-Manager-administrator#configProperties) for details.

   Path | Name | Type | Value | Notes
   -----|------|------|-------|------
   Root/Application | wowzaResolverConfTargetPath | String | [/path/to/origins.txt] | Path to a file where you can define your origin servers (one per line). If not used, your application xml configuration will take precedence. (default: not set).
   Root/Application | wowzaResolverOriginServers | String | [server1.com,server2.com] | If your wowzaResolverConfTargetPath is not set, define a comma delimited list of origins. (default: not set).
   Root/Application | wowzaResolverProtocol | String | [protocol] | Protocol to use when connecting to origins (rtmp or wowz) (default: rtmp).
   Root/Application | wowzaResolverUDPClientPort | Integer | [port] | UDP Port to use for client/origin communication. (default: 9777).
   Root/Application | wowzaResolverUDPClientTimeout | Integer | [milleseconds] | Number of milliseconds before timing out the connection to the given origin. (default: 2000).
   Root/Application | wowzaResolverOriginApplicationName | String | [originAppName] | (Optional) If edge is looking for streams on the origin that are not the same application name, you can define the new app name here.(default: uses requested application name from the edge).
   Root/Application | wowzaResolverOriginApplicationInstanceName | String | [_definst_] | (Optional) If edge is looking for streams on the origin that are not the same application instance name, you can define the new  name here.(default: uses requested app instance name from the edge).
   Root/Application | wowzaResolverOriginStreamName | String | [nameOfStream] | (Optional) If edge is looking for streams on the origin that are not the same stream name, you can define the new  name here.(default: uses requested stream name from edge).

   ### Origin Properties

   Adjust the default settings by adding the following properties to your application. See [Configure properties](http://www.wowza.com/forums/content.php?625-How-to-get-started-as-a-Wowza-Streaming-Engine-Manager-administrator#configProperties) for details.
   
   Path | Name | Type | Value | Notes
   -----|------|------|-------|------
   Root/Application | wowzaSourceStreamUDPListenerDebug | Boolean | [false] | Debug inbound connections to your origin from edge requests. (default: false).
   Root/Application | wowzaSourceStreamUDPListenerPort | Integer | [port] | UDP Port to use for client/origin communication. (default: 9777).
   Root/Application | wowzaSourceStreamHostName | String | [publichostname.com] | Public host name of the origin. (default: null).

## Usage

When the request from a client/player occurs on an edge, the edge will first look for existing streams that it has requested.  If none are found, it will then query known origins to return the given stream.  Whichever origin responds first with a valid stream, the edge will connect with it and serve up that stream.

## API Reference

[Wowza Streaming Engine Server-Side API Reference](http://www.wowza.com/resources/WowzaStreamingEngine_ServerSideAPI.pdf)

## Contact
Wowza Media Systems, LLC

Wowza Media Systems provides developers with a platform to create streaming applications and solutions. See [Wowza Developer Tools](https://www.wowza.com/resources/developers) to learn more about our APIs and SDK.

## License

TODO: Add legal text here or LICENSE.txt file
