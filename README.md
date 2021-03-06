# JMeter WebSocket Samplers

JMeter add-on that defines a number of samplers for load testing WebSocket applications.

## Usage

Download the jar from the [downloads](https://bitbucket.org/pjtr/jmeter-websocket-samplers/downloads/) dir, copy it to <jmeter-home>/lib/ext and start JMeter. That's all.

Make sure you're running JMeter with Java 8. Loading the plugin will fail silently if running with Java 7 (or older).

## You can help! Spread the word!

If you like this plugin, if you find it useful, you can help others that might have similar problems or challenges as you had, by spreading the word. Talk, tweet, blog about it; answer questions about how to load-test WebSocket on forums, stackoverflow etc. and let people know this plugin exists. If you think the plugin needs improvement, let the author know (see "feedback" below).

## Features
The WebSocket Samplers plugin provides the following features:

* provides 6 different WebSocket samplers
* samplers do not create additional threads, so large number of JMeter threads can be used,
* support for wss (WebSocket over TLS)
* wss support fully compatible with JMeter's SSLManager, including client certificates
* support for binary WebSocket frames
* assertion for checking binary responses
* integrates with JMeter's Header Manager to set additional HTTP headers on WebScoket upgrade request
* sends cookies defined by JMeter's Cookie Manager with each upgrade request (i.e. the HTTP request that initiates the WebSocket connection)
* many sample JMeter test plans illustrate the various features.

### Samplers

Currently, there are six samplers:

* request-response sampler, for performing a basic request-response exchange,
* ping-pong sampler, for sending a ping and receiving a pong
* close connection sampler, for properly closing a websocket connection
* single-read sampler, for receiving one (text or binary) WebSocket frame
* single-write sampler, for sending one (text or binary) WebSocket frame
* open connection sampler, for _explicitly_ setting up a WebSocket connection.

The request-response sampler is the most commonly used one. With this sampler you can test a request-response exchange, much like an ordinary HTTP request/response. As all other samplers in this plugin, it does not create any thread by itself, but instead performs all communication on the JMeter ThreadGroup thread. This implies that it scales very well, comparable with standard JMeter HTTP sampler.

![Sampler GUI](https://bytebucket.org/pjtr/jmeter-websocket-samplers/raw/master/docs/request-response-sample.png)

The request-response sampler, as well as the single-read and single-write samplers, support both text and binary frames. Unfortunately, JMeter cannot display binary responses in the results viewers, e.g. when using a "View Results Tree" listener element, the "Response data" tab stays empty. There is a work around however: use a "Save Responses to a file" listener (see sample).

For examples of how to use the sampler, see the JMeter .jmx files in the [samples directory](https://bitbucket.org/pjtr/jmeter-websocket-samplers/src/master/samples/?at=master)!

### Connections

Each JMeter (ThreadGroup) thread can have at most one active WebSocket connection. In the sampler, you can indicate whether you want to (re) use the current connection, or create a new one. If you create a new one, the current connection is closed at TCP level, but no WebSocket close frames are sent. If you want to close the connection properly (i.e. send a WebSocket close frame and wait for the close response), use the WebSocket Close sampler. 

There is also a WebSocket Open Connection sampler that only opens the WebSocket connection (i.e. sends an upgrade request) and sends no data once the websocket connection is established.

### WebSockets over TLS

To use the wss (WebSockets over TLS) protocol instead of plain ws, simply select the wss protocol in the Server URL settings. Make sure you also change the port number (e.g. to 443, the default wss port), or you'll get confusing results when trying to set up a TLS connection with a normal HTTP port.

TLS server certificates are accepted without any verification; this is default JMeter behaviour, see for example <http://jmeter.apache.org/usermanual/get-started.html#opt_ssl>.

Using client certificates is also fully supported. It works exactly the same as the default SSL support in JMeter. However, setting it up correctly can be a bit of a challenge; see [jmeter_ssl_with_client_certificates.md](https://bitbucket.org/pjtr/jmeter-websocket-samplers/src/master/jmeter_ssl_with_client_certificates.md) for a step by step guide.


### Binary response assertion

In addition to WebSocket samplers, the plugin also provides an generic JMeter assertion element that can be used for veryfying binary responses. It's usage is pretty straight forward:

![Sampler GUI](https://bytebucket.org/pjtr/jmeter-websocket-samplers/raw/master/docs/binary-assertion-sample.png)

This assertion element is of course very usefull when load testing binary websocket calls, but it is not limited to websocket tests in any way. It can be used with any sampler in the JMeter toolbox. For example, you could use it to check that an image result in a HTTP sampler, is a proper PNG file (see sample).

Note that the assertion element does not check the type of the response: it simply takes the binary value of the response and checks it against the match value provided. In that sense, it is completely analogous to the standard JMeter Response Assertion, except that this one provides a convenient way for specifying a binary match value.

## Status

Even though the project is fairly new, the add-on is fully functional. If you encounter any issues or ambiguities, please report them, see below for contact details.

## Building

Gradle is used as build tool, so execute

    gradle assemble

to build. Almost any version of gradle will do (tested with 2.2). Gradle can also generate IntelliJ Idea project files for you:

    gradle idea


## Feedback

Questions, problems, or other feedback? Please mail the author (peter dot doornbosch) at luminis dot eu, or create an issue at <https://bitbucket.org/pjtr/jmeter-websocket-samplers/issues>. Any feedback is welcome, issues are always taken seriously.


## Acknowledgements

The following people have contributed to this plugin by providing feedback, filing isssues, etc.: Eric Engels, Siarhei Huzau, Victor Komlev, Chitta Ranjan.


## License

This program is open source and licensed under LGPL (see the LICENSE.txt and LICENSE-LESSER.txt files in the distribution). This means that you can use this program for anything you like, and that you can embed it as a library in other applications, even commercial ones. If you do so, the author would appreciate if you include a reference to the original. As of the LGPL license, all modifications and additions to the source code must be published as (L)GPL as well.
