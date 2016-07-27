# StreamResolver
The **ModuleStreamResolver** module for [Wowza Streaming Engine™ media server software](https://www.wowza.com/products/streaming-engine) enables edge servers in a live stream repeater (origin/edge) configuration to dynamically resolve client requests for streams to multiple origin servers.

## Prerequisites
Wowza Streaming Engine 4.0.0 or later is required.

## Usage
When a client/player requests a stream from an edge server, the edge will first look for existing streams that it has already requested for a match. If none are found, it will then query the defined list of origin servers to return the requested stream. The edge server will connect to the origin server that responds first with a valid stream, and then serve that stream to the client/player.

## More resources
[Wowza Streaming Engine Server-Side API Reference](https://www.wowza.com/resources/WowzaStreamingEngine_ServerSideAPI.pdf)

[How to extend Wowza Streaming Engine using the Wowza IDE](https://www.wowza.com/forums/content.php?759-How-to-extend-Wowza-Streaming-Engine-using-the-Wowza-IDE)

Wowza Media Systems™ provides developers with a platform to create streaming applications and solutions. See [Wowza Developer Tools](https://www.wowza.com/resources/developers) to learn more about our APIs and SDK.

To use the compiled version of this module, see [How to dynamically resolve edge server stream requests to origin servers (StreamResolver)](https://www.wowza.com/forums/content.php?815-How-to-dynamically-resolve-edge-server-stream-requests-to-origin-servers-%28ModuleStreamResolver%29).

## Contact
[Wowza Media Systems, LLC](https://www.wowza.com/contact)

## License
This code is distributed under the [Wowza Public License](https://github.com/WowzaMediaSystems/wse-plugin-streamresolver/blob/master/LICENSE.txt).

![alt tag](http://wowzalogs.com/stats/githubimage.php?plugin=wse-plugin-streamresolver)