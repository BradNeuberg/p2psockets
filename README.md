#P2P Sockets

#Note: The P2P Sockets is no longer maintained or supported. It is present here for archival purposes.

##Overview

P2P Sockets makes it easy to write peer-to-peer applications based on JXTA. P2P Sockets allows programmers to gain much of the power of JXTA, such as NAT and firewall traversal, without being exposed to its complexity. It does this through ports of popular software projects, such as a web server and web services stack, to work on the JXTA peer-to-peer network. This includes a web server (Jetty) that can receive requests and serve content over the peer-to-peer network; a servlet and JSP engine (Jetty and Jasper) that allows existing servlets and JSPs to serve P2P clients; an XML-RPC client and server (Apache XML-RPC) for accessing and exposing P2P XML-RPC endpoints; an HTTP/1.1 client (Apache Commons HTTP-Client) that can access P2P web servers; a gateway (Smart Cache) to make it possible for existing browsers to access P2P web sites; and a WikiWiki (JSPWiki) that can be used to host WikiWikis on your local machine that other peers can access and edit through the P2P network. P2P Sockets also introduces implementations of java.net.Socket and java.net.ServerSocket that can work on the JXTA network as well as a simple, light-weight, distributed, human-friendly, and non-secure DNS system. For example, to have a peer expose a simple service that can be reached by other peers, you would use the following code:

	// create a service with the name "www.nike.laborpolicy" on virtual port 100
	java.net.ServerSocket server = new P2PServerSocket("www.nike.laborpolicy", 100); // port 100
	// wait for a client to connect
	java.net.Socket client = server.accept();
	// client accepted; now communicate
	InputStream in = client.getInputStream();
	OutputStream out = client.getOutputStream();
	// do application specific work now

The client side is just as simple:

	// connect to a peer offering the service at "www.nike.laborpolicy" on virtual port 100
	java.net.Socket socket = new P2PSocket("www.nike.laborpolicy", 100);
	// now start communicating
	InputStream in = socket.getInputStream();
	OutputStream out = socket.getOutputStream();

Peers can now host and consume services even if they are normally unreachable, such as if they are behind NAT or firewalls, and can publish services to P2P Socket's lightweight distributed domain name system without having to configure or deploy customized DNS servers.

Two tutorials are available to get you started:
* [Introduction to Peer-to-Peer Sockets](https://web.archive.org/web/20060202063104/http://p2psockets.jxta.org/docs/tutorials/1.html)
* [How to Create Peer-to-Peer Web Servers, Servlets, JSPs, and XML-RPC Clients and Servers](https://web.archive.org/web/20060705224704/http://p2psockets.jxta.org/docs/tutorials/2.html)

There are also more tutorials in [www/docs/tutorials](www/docs/tutorials).

The original web site for P2P Sockets is [available here](https://web.archive.org/web/20060503110718/http://p2psockets.jxta.org).

##Technical Details

Are you interested in:
* returning the end-to-end principle to the Internet?
* an alternative peer-to-peer domain name system that bypasses ICANN and Verisign, is completely decentralized, and responds to updates much quicker than standard DNS?
* an Internet where everyone can create and consume network services, even if they have a dynamic IP address or no IP address, are behind a Network Address Translation (NAT) device, or blocked by an ISP's firewall?
* a web where every peer can automatically start a web server, host an XML-RPC service, and more and quickly make these available to other peers?
easily adding peer-to-peer functionality to your Java socket and server socket applications?
* having your servlets and Java Server Pages work on a peer-to-peer network for increased reliability, easier maintenence, and exciting new end-user functionality?
* playing with a cool technology?

If you answered yes to any of the above, then welcome to the Peer-to-Peer Sockets project! The Peer-to-Peer Sockets Project reimplements Java's standard Socket, ServerSocket, and InetAddress classes to work on a peer-to-peer network rather than on the standard TCP/IP network. "Aren't standard TCP/IP sockets and server sockets already peer-to-peer?" some might ask. Standard TCP/IP sockets and server sockets are theoretically peer-to-peer but in practice are not due to firewalls, Network Address Translation (NAT) devices, and political and technical issues with the Domain Name System (DNS).

The P2P Sockets project deals with these issues by re-implementing the standard java.net classes on top of the Jxta peer-to-peer network. Jxta is an open-source project that creates a peer-to-peer overlay network that sits on top of TCP/IP. Ever peer on the network is given an IP-address like number, even if they are behind a firewall or don't have a stable IP address. Super-peers on the Jxta network run application-level routers which store special information such as how to reach peers, how to join sub-groups of peers, and what content peers are making available. Jxta application-level relays can proxy requests between peers that would not normally be able to communicate due to firewalls or NAT devices. Peers organize themselves into Peer Groups, which scope all search requests and act as natural security containers. Any peer can publish and create a peer group in a decentralized way, and other peers can search for and discover these peer groups using other super-peers. Peers communicate using Pipes, which are very similar to Unix pipes. Pipes abstract the exact way in which two peers communicate, allowing peers to communicate using other peers as intermediaries if they normally would not be able to communicate due to network partitioning.

Jxta is an extremely powerful framework. However, it is not an easy framework to learn, and porting existing software to work on Jxta is not for the faint-of-heart. P2P Sockets effectively hides Jxta by creating a thin illusion that the peer-to-peer network is actually a standard TCP/IP network. If a peer wishes to become a server they simply create a P2P server socket with the domain name they want and the port other peers should use to contact them. P2P clients open socket connections to hosts that are running services on given ports. Hosts can be resolved either by domain name, such as "www.nike.laborpolicy", or by IP address, such as "44.22.33.22". Behind the scenes these resolve to JXTA primitives rather than being resolved through DNS or TCP/IP. For example, the host name "www.nike.laborpolicy" is actually the NAME field of a Jxta Peer Group Advertisement. P2P sockets and server sockets work exactly the same as normal TCP/IP sockets and server sockets.

The benefits of taking this approach are many-fold. First, programmers can easily leverage their knowledge of standard TCP/IP sockets and server sockets to work on the Jxta peer-to-peer network without having to learn about Jxta. Second, all of the P2P Sockets code subclasses standard java.net objects, such as java.net.Socket, so existing network applications can quickly be ported to work on a peer-to-peer network. The P2P Sockets project already includes a large amount of software ported to use the peer-to-peer network, including a web server (Jetty) that can receive requests and serve content over the peer-to-peer network; a servlet and JSP engine (Jetty and Jasper) that allows existing servlets and JSPs to serve P2P clients; an XML-RPC client and server (Apache XML-RPC) for accessing and exposing P2P XML-RPC endpoints; an HTTP/1.1 client (Apache Commons HTTP-Client) that can access P2P web servers; a gateway (Smart Cache) to make it possible for existing browsers to access P2P web sites; and a WikiWiki (JSPWiki) that can be used to host WikiWikis on your local machine that other peers can access and edit through the P2P network. Even better, all of this software works and looks exactly as it did before being ported. The P2P Sockets abstraction is so strong that porting each of these pieces of software took as little as 30 minutes to several hours. Everything included in the P2P sockets project is open-source, mostly under BSD-type licenses, and cross-platform due to being written in Java.

Because P2P Sockets are based on Jxta, they can easily do things that ordinary server sockets and sockets can't handle. First, creating server sockets that can fail-over and scale is easy with P2P Sockets. Many different peers can start server sockets for the same host name and port, such as "www.nike.laborpolicy" on port 80. When a client opens a P2P socket to "www.nike.laborpolicy" on port 80, they will randomly connect to one of the machines that is hosting this port. All of these server peers might be hosting the same web site, for example, making it very easy to partition client requests across different server peers or to recover from losing one server peer. This is analagous to DNS round-robining, where one host name will resolve to many different IP addresses to help with load-balancing. Second, since P2P Sockets don't use the DNS system, host names can be whatever you wish them to. You can create your own fanciful endings, such as "www.boobah.cat" or "www.cynthia.goddess", or application-specific host names, such as "Brad GNUberg" or "Fidget666" for an instant messaging system. Third, the service ports for a given host name can be distributed across many different peers around the world. For example, imagine that you have a virtual host for "www.nike.laborpolicy". One peer could be hosting port 80, to serve web pages; another could be hosting port 2000, for instant messaging, and a final peer could be hosting port 3000 for peers to subscribe to real-time RSS updates. Hosts now become decentralized coalitions of peers working together to serve requests.

##License Information

The P2P Sockets Core, P2P Sockets Examples, and P2P Sockets Testing Classes are under the Sun Project JXTA
Software License, which is based on the Apache Software License Version 1.1.  A copy of the License
is available at http://www.jxta.org/jxta_license.html

This product includes software developed by the Apache Software Foundation (http://www.apache.org/) under the
Apache Source License, which is available at http://www.opensource.org/licenses/apachepl.php.  The following
pieces of software are under this license: Apache HTTP/1.1 Client, P2P Sockets HTTP/1.1 Extensions,
Apache XML-RPC Client and Server Libraries, the P2P Sockets XML-RPC Client and Server Extensions, and extra Ant
tasks taken from the Ant-Contrib Project (http://ant-contrib.sourceforge.net/).  P2P Sockets includes custom Ant
tasks, such as the Release task, that are also under the Apache Software License.

This product includes software under the Jetty License, which is basicly a variant of the Artistic License.  The Jetty
License is available at http://www.mortbay.org/jetty/LICENSE.html.  The following pieces of software are under
this license: the Jetty HTTP Server and the P2P Sockets Jetty Extensions.

This product includes software released under the GNU Public License, which is available at http://www.gnu.org/copyleft/gpl.html.
The following pieces of software are under this license: Smart Cache and the P2P Sockets P2PToWebProxy Extensions to
Smart Cache.  The P2P Sockets P2PToWebProxy framework makes calls to the P2P Sockets Core, which is under a BSD-type
license.  GPLed code is allowed to call code that does not place further restrictions on the user's rights, which BSD
does not do.

This product includes software released under the Limited/Lesser GNU Public License, which is available at
http://www.gnu.org/copyleft/lesser.html.  The following pieces of software are under this license: JSPWiki.

This product includes software developed by Sun Microsystems, Inc. for Project JXTA.

##Version and Change Notes

To find out the version of this release and information about it, see [www/CHANGENOTES.html](www/CHANGENOTES.html).

##Contributors

###Brad Neuberg
* Created P2P Sockets
* Initial project management
* Architecture and coding

###Alex Lynch
* Became project manager after Brad; no longer managing project
* Improved reliability
* Many bug fixes

###Mohamed Abdelaziz
* Had idea on how to hide JXTA beneath java.net.Socket APIs
* Helped with design work on the JXTA Profiler

###James Todd
* Project Management
* ext:config design and coding
* Helped with design work on the JXTA Profiler
* Refactored JXTA Profiler and integrated it back into JXTA

###Kevin Lahoda
* Did a huge build system overhaul
* Bug testing

###Jean-Christophe Hugly
* Helped with design work on the JXTA Profiler

###Vikas Sodhani
* Bug testing

###John Mitchell (jfm_NO_SPAM_SPAM_IS_EVIL@minioak.com)
* Submitted bug patch for P2PNetwork class

##Building

You must have the following software installed to build P2P Sockets:
	* JDK 1.4+
	* Ant 1.6.0

You must set the following environment variables to build the source:
	//I'm not sure if this is true any longer ???
	* JAVA_HOME - The location of your Java SDK

Type 'ant help' for information on available Ant tasks.

##Downloads

###Stable Releases

* P2P Sockets 2.0-r2 - [p2psockets-2.0-r2.zip](https://web.archive.org/web/20060503110718/http://p2psockets.jxta.org/releases/stable/p2psockets-2_0-r2.zip) - Corrects bug in virtual ip address representation.
* P2P Sockets 2.0 - [p2psockets-2.0.zip](https://web.archive.org/web/20060503110718/http://p2psockets.jxta.org/releases/stable/p2psockets-2_0.zip) - Major new release. Includes complete auto profiling for peers and an active node lookup system.
* P2P Sockets 1.2 - [p2psockets-1_2.zip](https://web.archive.org/web/20060503110718/http://p2psockets.jxta.org/releases/stable/p2psockets-1_2.zip) - Important new release for P2P Sockets, 1.2 features great improvements in speed and reliability. Many bugs have been driven from the I/O code to make this the most dependable P2PSockets release yet.

###Unstable Releases

* P2P Sockets 1.1.4 - [p2psockets-1_1_4.zip](https://web.archive.org/web/20060503110718/http://p2psockets.jxta.org/releases/unstable/p2psockets-1_1_4.zip) - Major new release for P2P Sockets, 1.1.4. A critical bug was preventing P2P Sockets from working with the latest version of JXTA and the Sun JXTA public rendezvous servers; this has now been fixed. P2P Sockets is now ported to JXTA 2.3.1; is significantly faster both in startup and in resolving P2P sockets, server sockets, and web sites; embeds the JXTA profiler rather than its own for automatic peer configuration; features a much refactored simpler and easier internal design; features completely revamped and up-to-date documentation, tutorials, and presentations; has ant tasks for easily starting P2P WikiWikis; and is now ported to the 2.0.2 version of Apache Commons HTTP-Client.
* P2P Sockets 1.1.2 - [p2psockets-1_1_2-2004-03-02.zip](https://web.archive.org/web/20060503110718/http://p2psockets.jxta.org/releases/unstable/p2psockets-1_1_2-2004-03-02.zip) - Offers a few bug fixes and additions to the P2PNetwork class.
* P2P Sockets 1.1.1 - [p2psockets-1_1_1-2004-02-09.zip](https://web.archive.org/web/20060503110718/http://p2psockets.jxta.org/releases/unstable/p2psockets-1_1_1-2004-02-09.zip) - Offers significant new features in ease of deployment, upgrades to other key packages used by P2P Sockets (such as JXTA and Jetty), and new features to help with P2P Socket's project management. This release won't perform autoconfiguration correctly until a JXTA bootstrap peer is deployed, which should happen in either a few days or possibly a few weeks. Some documentation tasks remain to be finished (such as upgrading the tutorials on how to use this new release).
