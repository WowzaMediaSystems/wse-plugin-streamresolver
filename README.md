# StreamResolver
The **ModuleStreamResolver** module for [Wowza Streaming Engine™ media server software](https://www.wowza.com/products/streaming-engine) enables edge servers in a live stream repeater (origin/edge) configuration to dynamically resolve client requests for streams to multiple origin servers.

This repo include a [compiled version](/lib/wse-plugin-streamresolver.jar).

## Prerequisites
Wowza Streaming Engine 4.0.0 or later is required.

## Usage
When a client/player requests a stream from an edge server, the edge will first look for existing streams that it has already requested for a match. If none are found, it will then query the defined list of origin servers to return the requested stream. The edge server will connect to the origin server that responds first with a valid stream, and then serve that stream to the client/player.

## More resources
To use the compiled version of this module, see [Dynamically resolve edge server stream requests to origin servers with a Wowza Streaming Engine Java module](https://www.wowza.com/docs/how-to-dynamically-resolve-edge-server-stream-requests-to-origin-servers-modulestreamresolver).

[Wowza Streaming Engine Server-Side API Reference](https://www.wowza.com/resources/serverapi/)

[How to extend Wowza Streaming Engine using the Wowza IDE](https://www.wowza.com/docs/how-to-extend-wowza-streaming-engine-using-the-wowza-ide)

Wowza Media Systems™ provides developers with a platform to create streaming applications and solutions. See [Wowza Developer Tools](https://www.wowza.com/developer) to learn more about our APIs and SDK.

## Contact
[Wowza Media Systems, LLC](https://www.wowza.com/contact)

## License
This code is distributed under the [Wowza Public License](https://github.com/WowzaMediaSystems/wse-plugin-streamresolver/blob/master/LICENSE.txt).
