/** 
  * Copyright (c) 2003 by Bradley Keith Neuberg, bkn3@columbia.edu.
  *
  * Redistributions in source code form must reproduce the above copyright and this condition.
  *
  * The contents of this file are subject to the Sun Project JXTA License Version 1.1 (the "License"); 
  * you may not use this file except in compliance with the License. A copy of the License is available 
  * at http://www.jxta.org/jxta_license.html.
  */

package test.org.p2psockets;

import java.io.*;
import java.net.*;

import org.p2psockets.*;

import net.jxta.peergroup.*;
import net.jxta.exception.*;
import net.jxta.protocol.*;

/** This testing class is split into two main pieces: one part that starts a set of servers, and one piece
  * that starts a set of clients.  The servers are started by calling this class with the command-line 
  * parameter "--server" in one window; after this class finishes this class should be called again but with
  * the command-line parameter "--client".  The server portion starts up a large number of threads, each with a
  * ServerSocket that can handle all of the requests the client will make.  The client portion starts up its own
  * servers, which are needed so that the loopback address "localhost" can resolve correctly back to the client.
  * The client also makes a series of calls to the servers.  This class also incldues some very simple assertion
  * methods, such as assertEqual.
  * @todo Robustly test the equals() method on our different custom classes.
  * @todo Robustly check the host name, InetAddress, etc. values returned from a ServerSocket and set
  * on the client Socket returned from ServerSocket.accept().
  * @todo Test to see if this works when done in the context of a custom application peer group
  * versus the Net Peer Group.
  */
public class TestPackage {
	// inner class for creating server threads
	class ServerThread extends Thread {
		ServerSocket serverSocket;

		public ServerThread(ServerSocket serverSocket) throws Exception {
			this.serverSocket = serverSocket;
		}

		public void run() {
			try {
				while (true) {
					System.out.println("--Waiting for clients to accept...");
					Socket client = serverSocket.accept();
					System.out.println("--Client accepted");
					DataInputStream in = new DataInputStream(client.getInputStream());
					DataOutputStream out = new DataOutputStream(client.getOutputStream());

					if (serverSocket.getInetAddress().getHostName() != null &&
						serverSocket.getInetAddress().getHostName().equals("www.server.shutdown")) {
						System.out.println("\n\n**** ALL TESTS PASSED CORRECTLY ****");
						System.exit(0);
					}

					String results = in.readUTF();
					System.out.println("--Printout from server: " + results);
					assertEqual(results, "hello server world");
					out.writeUTF("hello client world");
                    out.flush();
					client.close();
				}
			}
			catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
	}

	// this inner class starts a server thread, but this server
	// is special: it is meant to communicate the port number one should communicate
	// on in order to access a server that was started on a random port.
	class InfoServerThread extends ServerThread {
		int portToCommunicate;

		/** Starts a server named "www.servers.info" on the port infoPort.  The
		  * port number that will be communicated is portToCommunicate. 
		  */
		public InfoServerThread(int infoPort, int portToCommunicate) throws Exception {
			super(new P2PServerSocket("www.servers.info", infoPort));
			this.portToCommunicate = portToCommunicate;
		}

		public void run() {
			try {
				while (true) {
					Socket client = serverSocket.accept();
					DataInputStream in = new DataInputStream(client.getInputStream());
					DataOutputStream out = new DataOutputStream(client.getOutputStream());

					out.writeInt(portToCommunicate);
                    out.flush();
					client.close();
				}
			}
			catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
	}
			
	public static void main(String args[]) {
		TestPackage t = new TestPackage();

		if (args[0].equals("--client"))
			t.startClients();
		else if (args[0].equals("--server"))
			t.startServers();
	}

	protected void startClients() {
		try {
			// sign in and initialize the JXTA network
			P2PNetwork.signin("clientpeer", "clientpeerpassword", "TestCoreNetwork");

			// start up socket servers that can satisfy loopback/"localhost" sockets
			startLocalHostServers();

			// now test each permutation of setting up and connecting to the socket
            String siteName;
            Socket s;
            byte[] ipAddress;
            InetAddress s_addr;
            P2PSocketAddress s_socketAddr;
            boolean exceptionHappened = false;
            byte wildcard[] = new byte[4];
            byte loopback[] = new byte[4];

            // P2PSocket(String host, int port) - normal peer group host and normal port
				System.out.println("P2PSocket(String host, int port) - normal peer group host and normal port\n");
				siteName = "www.boobah.cat";
				s = new P2PSocket(siteName, 80);
				s_addr = P2PInetAddress.getByName(siteName);
				s_socketAddr = new P2PSocketAddress(siteName, 80);
				handleSocket(s, s_addr, siteName, 80, false, s_socketAddr, siteName+":80",
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									0, true, new P2PSocketAddress(P2PInetAddress.getLocalHost(), 0));

			// P2PSocket(String host, int port) - normal peer host and normal port
				System.out.println("P2PSocket(String host, int port) - normal peer host and normal port\n");
				siteName = "www.serverpeer.peer";
				s = new P2PSocket(siteName, 80);
				s_addr = P2PInetAddress.getByName(siteName);
				s_socketAddr = new P2PSocketAddress(siteName, 80);
				handleSocket(s, s_addr, siteName, 80, false, s_socketAddr, siteName+":80",
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									0, true, new P2PSocketAddress(P2PInetAddress.getLocalHost(), 0));

			// P2PSocket(String host, int port) - "localhost" and normal port
				System.out.println("P2PSocket(String host, int port) - \"localhost\" and normal port");
				siteName = "localhost";
				s = new P2PSocket(siteName, 80);
				s_addr = P2PInetAddress.getByName(siteName);
				s_socketAddr = new P2PSocketAddress(siteName, 80);
				siteName = P2PInetAddress.getLocalHost().getHostName();
				handleSocket(s, s_addr, siteName, 80, false, s_socketAddr, siteName+":80",
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									0, true, new P2PSocketAddress(P2PInetAddress.getLocalHost(), 0));

			// P2PSocket(String host, int port) - normal peer group host and port 0; should throw exception
				System.out.println("P2PSocket(String host, int port) - normal peer group host and port 0; should throw exception\n");
				siteName = "www.boobah.cat";
				exceptionHappened = false;
				try {
					s = new P2PSocket(siteName, 0);
				}
				catch (Exception e) {
					exceptionHappened = true;
				}
				assertTrue(exceptionHappened);

			// P2PSocket(String host, int port) - normal host and port -1; should throw exception
				System.out.println("P2PSocket(String host, int port) - normal host and port -1; should throw exception\n");
				siteName = "www.boobah.cat";
				exceptionHappened = false;
				try {
					s = new P2PSocket(siteName, -1);
				}
				catch (Exception e) {
					exceptionHappened = true;
				}
				assertTrue(exceptionHappened);

			// P2PSocket(String host, int port) - null host and port; should be localhost
				System.out.println("P2PSocket(String host, int port) - null host and port; should be localhost\n");
				s = new P2PSocket((String)null, 80);
				s_addr = P2PInetAddress.getByName(null);
				s_socketAddr = new P2PSocketAddress((String)null, 80);
				siteName = P2PInetAddress.getLocalHost().getHostName();
				handleSocket(s, s_addr, siteName, 80, false, s_socketAddr, siteName+":80",
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									0, true, new P2PSocketAddress(P2PInetAddress.getLocalHost(), 0));

			// P2PSocket(String host, int port) - unknown peer group host and normal port; should timeout
				System.out.println("P2PSocket(String host, int port) - unknown peer group host and normal port; should timeout\n");
				siteName = "www.badhost.foo";
				exceptionHappened = false;
				try {
					s = new P2PSocket(siteName, 77);
				}
				catch (Exception e) {
					exceptionHappened = true;
				}
				assertTrue(exceptionHappened);

			// P2PSocket(String host, int port) - unknown peer host and normal port; should timeout
				System.out.println("P2PSocket(String host, int port) - unknown peer host and normal port; should timeout\n");
				siteName = "www.badhost.peer";
				exceptionHappened = false;
				try {
					s = new P2PSocket(siteName, 77);
				}
				catch (Exception e) {
					exceptionHappened = true;
				}
				assertTrue(exceptionHappened);

			// P2PSocket(String host, int port) - known peer group host and incorrect port; should timeout
				System.out.println("P2PSocket(String host, int port) - known peer group host and incorrect port; should timeout\n");
				siteName = "www.boobah.cat";
				exceptionHappened = false;
				try {
					s = new P2PSocket(siteName, 777);
				}
				catch (Exception e) {
					exceptionHappened = true;
				}
				assertTrue(exceptionHappened);

			// P2PSocket(String host, int port) - known peer host and incorrect port; should timeout
				System.out.println("P2PSocket(String host, int port) - known peer host and incorrect port; should timeout\n");
				siteName = "www.serverpeer.peer";
				exceptionHappened = false;
				try {
					s = new P2PSocket(siteName, 5000);
				}
				catch (Exception e) {
					exceptionHappened = true;
				}
				assertTrue(exceptionHappened);

			// P2PSocket(InetAddress address, int port) - 
				// normal peer group host name, null IP address, normal port
				System.out.println("P2PSocket(InetAddress address, int port) - normal peer group host name, null IP address, normal port\n");
				siteName = "www.boobah.cat";
				s_addr = P2PInetAddress.getByAddress(siteName, null);
				s = new P2PSocket(s_addr, 81);
				s_socketAddr = new P2PSocketAddress(s_addr, 81);
				handleSocket(s, s_addr, siteName, 81, false, s_socketAddr, siteName+":81",
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									0, true, new P2PSocketAddress(P2PInetAddress.getLocalHost(), 0));

			// P2PSocket(InetAddress address, int port) - 
				// normal peer host name, null IP address, normal port
				System.out.println("P2PSocket(InetAddress address, int port) - normal peer host name, null IP address, normal port\n");
				siteName = "www.serverpeer.peer";
				s_addr = P2PInetAddress.getByAddress(siteName, null);
				s = new P2PSocket(s_addr, 81);
				s_socketAddr = new P2PSocketAddress(s_addr, 81);
				handleSocket(s, s_addr, siteName, 81, false, s_socketAddr, siteName+":81",
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									0, true, new P2PSocketAddress(P2PInetAddress.getLocalHost(), 0));

			// P2PSocket(InetAddress address, int port) - 
				// null domain name, normal peer group IP address, normal port
				System.out.println("P2PSocket(InetAddress address, int port) - null domain name, normal peer group IP address, normal port\n");
				ipAddress = P2PNameService.findIPAddress("www.boobah.cat"); // get the IP that was generated
				s_addr = P2PInetAddress.getByAddress(null, ipAddress);
				s = new P2PSocket(s_addr, 80);
				s_socketAddr = new P2PSocketAddress(s_addr, 80);
				siteName = P2PNameService.findHostName(s_addr.getHostAddress()); // do a reverse lookup
				handleSocket(s, s_addr, siteName, 80, false, s_socketAddr, siteName+":80",
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									0, true, new P2PSocketAddress(P2PInetAddress.getLocalHost(), 0));

			// P2PSocket(InetAddress address, int port) - 
				// null domain name, normal peer IP address, normal port
				System.out.println("P2PSocket(InetAddress address, int port) - null domain name, normal peer IP address, normal port\n");
				ipAddress = P2PNameService.findIPAddress("www.serverpeer.peer"); // get the IP that was generated
				s_addr = P2PInetAddress.getByAddress(null, ipAddress);
				s = new P2PSocket(s_addr, 82);
				s_socketAddr = new P2PSocketAddress(s_addr, 82);
				siteName = P2PNameService.findHostName(s_addr.getHostAddress()); // do a reverse lookup

				handleSocket(s, s_addr, siteName, 82, false, s_socketAddr, siteName+":82",
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									0, true, new P2PSocketAddress(P2PInetAddress.getLocalHost(), 0));

			// P2PSocket(InetAddress address, int port) - 
				// null domain name, null IP address, normal port - should default to localhost
				System.out.println("P2PSocket(InetAddress address, int port) - null domain name, null IP address, normal port - should default to localhost\n");
				s_addr = P2PInetAddress.getByAddress(null, null);
				s = new P2PSocket(s_addr, 80);
				s_socketAddr = new P2PSocketAddress(s_addr, 80);
				siteName = P2PInetAddress.getLocalHost().getHostName();

				handleSocket(s, s_addr, siteName, 80, false, 
									s_socketAddr, siteName+":80",
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									0, true, new P2PSocketAddress(P2PInetAddress.getLocalHost(), 0));

			// P2PSocket(InetAddress address, int port) - 
				// "localhost" domain name, null IP address, normal port
				System.out.println("P2PSocket(InetAddress address, int port) - \"localhost\" domain name, null IP address, normal port\n");
				s_addr = P2PInetAddress.getByAddress("localhost", null);
				s = new P2PSocket(s_addr, 80);
				s_socketAddr = new P2PSocketAddress(s_addr, 80);
				siteName = P2PInetAddress.getLocalHost().getHostName();

				handleSocket(s, s_addr, siteName, 80, false, 
									s_socketAddr, siteName+":80",
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									0, true, new P2PSocketAddress(P2PInetAddress.getLocalHost(), 0));

			// P2PSocket(InetAddress address, int port) - 
				// null domain name, loopback IP address, normal port
				System.out.println("P2PSocket(InetAddress address, int port) - null domain name, loopback IP address, normal port\n");
				s_addr = P2PInetAddress.getByAddress("127.0.0.1", null);
				s = new P2PSocket(s_addr, 80);
				s_socketAddr = new P2PSocketAddress(s_addr, 80);
				siteName = P2PInetAddress.getLocalHost().getHostName();

				handleSocket(s, s_addr, siteName, 80, false, 
									s_socketAddr, siteName+":80",
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									0, true, new P2PSocketAddress(P2PInetAddress.getLocalHost(), 0));

			// P2PSocket(InetAddress address, int port) - 
				// null domain name, wildcard IP address, normal port
				System.out.println("P2PSocket(InetAddress address, int port) - null domain name, wildcard IP address, normal port\n");
				wildcard = new byte[4];
				wildcard[0] = 0;
				wildcard[1] = 0;
				wildcard[2] = 0;
				wildcard[3] = 0;
				s_addr = P2PInetAddress.getByAddress(null, wildcard);
				s = new P2PSocket(s_addr, 80);
				s_socketAddr = new P2PSocketAddress(s_addr, 80);
				siteName = P2PInetAddress.getLocalHost().getHostName();

				handleSocket(s, s_addr, siteName, 80, false, 
									s_socketAddr, siteName+":80",
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									0, true, new P2PSocketAddress(P2PInetAddress.getLocalHost(), 0));

			// P2PSocket(InetAddress address, int port) - null address, normal port - should be loopback
				System.out.println("P2PSocket(InetAddress address, int port) - null address, normal port - should be loopback\n");
				loopback = new byte[4];
				loopback[0] = 127;
				loopback[1] = 0;
				loopback[2] = 0;
				loopback[3] = 1;
				s_addr = P2PInetAddress.getByAddress(null, loopback);
				s = new P2PSocket((String)null, 80);
				s_socketAddr = new P2PSocketAddress(s_addr, 80);
				siteName = P2PInetAddress.getLocalHost().getHostName();

				handleSocket(s, s_addr, siteName, 80, false, 
									s_socketAddr, siteName+":80",
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									0, true, new P2PSocketAddress(P2PInetAddress.getLocalHost(), 0));

			// P2PSocket(InetAddress address, int port) - 
				// normal peer domain name, normal IP address, 0 port; should throw exception
				System.out.println("P2PSocket(InetAddress address, int port) - normal peer domain name, normal IP address, 0 port; should throw exception\n");
				byte[] ipAddressBytes = new byte[4];
				ipAddressBytes[0] = 56;
				ipAddressBytes[1] = 32;
				ipAddressBytes[2] = 77;
				ipAddressBytes[3] = 89;
				s_addr = P2PInetAddress.getByAddress("www.serverpeer.peer", ipAddressBytes);
				exceptionHappened = false;
				try {
					s = new P2PSocket(s_addr, 0);
				}
				catch (Exception e) {
					exceptionHappened = true;
				}
				assertTrue(exceptionHappened);

			// P2PSocket(InetAddress address, int port) - 
				// normal peer group domain name, normal IP address, 0 port; should throw exception
				System.out.println("P2PSocket(InetAddress address, int port) - normal peer group domain name, normal IP address, 0 port; should throw exception\n");
				ipAddressBytes = new byte[4];
				ipAddressBytes[0] = 56;
				ipAddressBytes[1] = 32;
				ipAddressBytes[2] = 77;
				ipAddressBytes[3] = 89;
				s_addr = P2PInetAddress.getByAddress("www.boobah.cat", ipAddressBytes);
				exceptionHappened = false;
				try {
					s = new P2PSocket(s_addr, 0);
				}
				catch (Exception e) {
					exceptionHappened = true;
				}
				assertTrue(exceptionHappened);

			// P2PSocket(InetAddress address, int port) - 
				// "localhost" domain name, normal IP address, 0 port; should throw exception
				System.out.println("P2PSocket(InetAddress address, int port) - \"localhost\" domain name, normal IP address, 0 port; should throw exception\n");
				s_addr = P2PInetAddress.getByAddress("localhost", loopback);
				exceptionHappened = false;
				try {
					s = new P2PSocket(s_addr, 0);
				}
				catch (Exception e) {
					exceptionHappened = true;
				}
				assertTrue(exceptionHappened);

			// P2PSocket(InetAddress address, int port) - 
				// "localhost" domain name, null IP address, 0 port; should throw exception
				System.out.println("P2PSocket(InetAddress address, int port) - \"localhost\" domain name, null IP address, 0 port; should throw exception\n");
				s_addr = P2PInetAddress.getByAddress("localhost", null);
				exceptionHappened = false;
				try {
					s = new P2PSocket(s_addr, 0);
				}
				catch (Exception e) {
					exceptionHappened = true;
				}
				assertTrue(exceptionHappened);

			// P2PSocket(InetAddress address, int port) - 
				// null domain name, loopback IP address, 0 port; should throw exception
				System.out.println("P2PSocket(InetAddress address, int port) - null domain name, loopback IP address, 0 port; should throw exception\n");
				s_addr = P2PInetAddress.getByAddress(null, loopback);
				exceptionHappened = false;
				try {
					s = new P2PSocket(s_addr, 0);
				}
				catch (Exception e) {
					exceptionHappened = true;
				}
				assertTrue(exceptionHappened);

			// P2PSocket(InetAddress address, int port) - 
				// null domain name, wildcard IP address, 0 port; should throw exception
				System.out.println("P2PSocket(InetAddress address, int port) - null domain name, wildcard IP address, 0 port; should throw exception\n");
				s_addr = P2PInetAddress.getByAddress(null, wildcard);
				exceptionHappened = false;
				try {
					s = new P2PSocket(s_addr, 0);
				}
				catch (Exception e) {
					exceptionHappened = true;
				}
				assertTrue(exceptionHappened);


			// P2PSocket() using bind and connect
				// null IP address, normal peer group host and normal port
				System.out.println("P2PSocket() using bind and connect - null IP address, normal peer group host and normal port\n");
				siteName = "www.boobah.cat";
				s = new P2PSocket();
				// bind the local address
				s.bind(null);
				// connect to the remote address
				s_addr = P2PInetAddress.getByAddress(siteName, null);
				s_socketAddr = new P2PSocketAddress(s_addr, 80);
				s.connect(s_socketAddr);

				handleSocket(s, s_addr, siteName, 80, false, 
									s_socketAddr, siteName+":80",
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									0, true, new P2PSocketAddress(P2PInetAddress.getLocalHost(), 0));
				

			// P2PSocket() using bind and connect
				// null IP address, normal peer host and normal port
				System.out.println("P2PSocket() using bind and connect - null IP address, normal peer host and normal port\n");
				siteName = "www.serverpeer.peer";
				s = new P2PSocket();
				// bind the local address
				s.bind(null);
				// connect to the remote address
				s_addr = P2PInetAddress.getByAddress(siteName, null);
				s_socketAddr = new P2PSocketAddress(s_addr, 80);
				s.connect(s_socketAddr);

				handleSocket(s, s_addr, siteName, 80, false, 
									s_socketAddr, siteName+":80",
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									0, true, new P2PSocketAddress(P2PInetAddress.getLocalHost(), 0));

			// P2PSocket() using bind and connect
				// null IP address, "localhost" and normal port
				System.out.println("P2PSocket() using bind and connect - null IP address, \"localhost\" and normal port\n");
				siteName = P2PInetAddress.getLocalHost().getHostName();
				s = new P2PSocket();
				// bind the local address
				s.bind(null);
				// connect to the remote address
				s_addr = P2PInetAddress.getByAddress("localhost", null);
				s_socketAddr = new P2PSocketAddress(s_addr, 80);
				s.connect(s_socketAddr);

				handleSocket(s, s_addr, siteName, 80, false, 
									s_socketAddr, siteName+":80",
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									0, true, new P2PSocketAddress(P2PInetAddress.getLocalHost(), 0));

			// P2PSocket() using bind and connect
				// null IP address, null host and port; should be localhost
				System.out.println("P2PSocket() using bind and connect - null IP address, null host and port; should be localhost\n");
				siteName = P2PInetAddress.getLocalHost().getHostName();
				s = new P2PSocket();
				// bind the local address
				s.bind(new P2PSocketAddress("localhost", 80));
				// connect to the remote address
				s_addr = P2PInetAddress.getByAddress("localhost", null);
				s_socketAddr = new P2PSocketAddress((String)null, 80);
				s.connect(s_socketAddr);

				handleSocket(s, s_addr, siteName, 80, false, 
									s_socketAddr, siteName+":80",
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									80, false, new P2PSocketAddress(P2PInetAddress.getLocalHost(), 80));

			// P2PSocket() using bind and connect
				// null IP address, unknown peer group host and normal port; should timeout
				System.out.println("P2PSocket() using bind and connect - null IP address, unknown peer group host and normal port; should timeout\n");
				siteName = "www.unknownhost.foo";
				s = new P2PSocket();
				// bind the local address
				s.bind(new P2PSocketAddress("www.clientpeer.peer", 80));
				// connect to the remote address
				s_addr = P2PInetAddress.getByAddress("localhost", null);
				s_socketAddr = new P2PSocketAddress(siteName, 80);
				exceptionHappened = false;
				try {
					s.connect(s_socketAddr);
				}
				catch (Exception e) {
					exceptionHappened = true;
				}
				assertTrue(exceptionHappened);

			// P2PSocket() using bind and connect
				// null IP address, known peer group host and incorrect port; should timeout
				System.out.println("P2PSocket() using bind and connect - null IP address, known peer group host and incorrect port; should timeout\n");
				siteName = "www.boobah.cat";
				s = new P2PSocket();
				// bind the local address
				s.bind(new P2PSocketAddress("www.clientpeer.peer", 777));
				// connect to the remote address
				s_addr = P2PInetAddress.getByAddress("localhost", null);
				s_socketAddr = new P2PSocketAddress(siteName, 777);
				exceptionHappened = false;
				try {
					s.connect(s_socketAddr);
				}
				catch (Exception e) {
					exceptionHappened = true;
				}
				assertTrue(exceptionHappened);

			// P2PSocket() using bind and connect
				// null domain name, normal peer group IP address, normal port
				System.out.println("P2PSocket() using bind and connect - null domain name, normal peer group IP address, normal port\n");
				s = new P2PSocket();
				// bind the local address
				s.bind(new P2PSocketAddress("www.clientpeer.peer", 0));
				// connect to the remote address
				s_addr = P2PInetAddress.getByAddress("238.39.22.221", null);
				s_socketAddr = new P2PSocketAddress(s_addr, 80);
				s.connect(s_socketAddr);
				siteName = P2PNameService.findHostName(s_addr.getHostAddress());
				handleSocket(s, s_addr, siteName, 80, false, 
									s_socketAddr, siteName+":80",
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									0, true, new P2PSocketAddress("www.clientpeer.peer", 0));

			// P2PSocket() using bind and connect
				// null domain name, normal peer group IP address, normal port
				System.out.println("P2PSocket() using bind and connect - null domain name, normal peer group IP address, normal port\n");
				s = new P2PSocket();
				// bind the local address
				s.bind(new P2PSocketAddress("www.clientpeer.peer", 0));
				// connect to the remote address
				s_addr = P2PInetAddress.getByAddress("118.158.193.121", null);

				s_socketAddr = new P2PSocketAddress(s_addr, 80);
				s.connect(s_socketAddr);
				siteName = P2PNameService.findHostName(s_addr.getHostAddress());

				handleSocket(s, s_addr, siteName, 80, false, 
									s_socketAddr, siteName+":80",
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									0, true, new P2PSocketAddress("www.clientpeer.peer", 0));

			// P2PSocket() using bind and connect
				// null domain name, loopback IP address, normal port
				System.out.println("P2PSocket() using bind and connect - null domain name, loopback IP address, normal port\n");
				siteName = P2PInetAddress.getLocalHost().getHostName();
				s = new P2PSocket();
				// bind the local address
				s.bind(null);
				// connect to the remote address
				s_addr = P2PInetAddress.getByAddress(null, loopback);
				s_socketAddr = new P2PSocketAddress(s_addr, 80);
				s.connect(s_socketAddr);

				handleSocket(s, s_addr, siteName, 80, false, 
									s_socketAddr, siteName+":80",
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									0, true, new P2PSocketAddress(P2PInetAddress.getLocalHost(), 0));

			// P2PSocket() using bind and connect
				// null domain name, wildcard IP address, normal port
				System.out.println("P2PSocket() using bind and connect - null domain name, wildcard IP address, normal port\n");
				siteName = P2PInetAddress.getLocalHost().getHostName();
				s = new P2PSocket();
				// bind the local address
				s.bind(null);
				// connect to the remote address
				s_addr = P2PInetAddress.getByAddress(null, wildcard);
				s_socketAddr = new P2PSocketAddress(s_addr, 80);
				s.connect(s_socketAddr);

				handleSocket(s, s_addr, siteName, 80, false, 
									s_socketAddr, siteName+":80",
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									0, true, new P2PSocketAddress(P2PInetAddress.getLocalHost(), 0));


			// P2PSocket(String host, int port, InetAddress localAddr, int localPort) - 
				// normal peer group host, normal port, null localAddr, 0 localPort
				System.out.println("P2PSocket(String host, int port, InetAddress localAddr, int localPort) - normal peer group host, normal port, null localAddr, null localPort\n");
				siteName = "www.boobah.cat";
				s = new P2PSocket(siteName, 80, null, 0);
				s_addr = P2PInetAddress.getByAddress(siteName, null);
				s_socketAddr = new P2PSocketAddress(siteName, 80);

				handleSocket(s, s_addr, siteName, 80, false, s_socketAddr, siteName+":80",
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									0, true, new P2PSocketAddress(P2PInetAddress.getLocalHost(), 0));

			// P2PSocket(String host, int port, InetAddress localAddr, int localPort) - 
				// "localhost" host, normal port 95, null localAddr, null localPort
				System.out.println("P2PSocket(String host, int port, InetAddress localAddr, int localPort) - \"localhost\" host, normal port, null localAddr, null localPort\n");
				siteName = "www.clientpeer.peer";
				s = new P2PSocket("localhost", 95, null, 0);
				s_addr = P2PInetAddress.getByAddress(siteName, null);
				s_socketAddr = new P2PSocketAddress(siteName, 95);

				handleSocket(s, s_addr, siteName, 95, false, s_socketAddr, siteName+":95",
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									0, true, new P2PSocketAddress(P2PInetAddress.getLocalHost(), 0));

			// P2PSocket(String host, int port, InetAddress localAddr, int localPort) - 
				// normal peer group host, normal port, non-null localAddr, non-null localPort;
				// local info should NOT be ignored
				System.out.println("P2PSocket(String host, int port, InetAddress localAddr, int localPort) - normal peer group host, normal port, non-null localAddr, non-null localPort - local info should NOT be ignored\n");
				siteName = "www.boobah.cat";
				s = new P2PSocket(P2PInetAddress.getByAddress(siteName, null), 80, 
										 P2PInetAddress.getLocalHost(), 0);
				s_addr = P2PInetAddress.getByAddress(siteName, null);
				s_socketAddr = new P2PSocketAddress(siteName, 80);

				handleSocket(s, s_addr, siteName, 80, false, s_socketAddr, siteName+":80",
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									0, true, new P2PSocketAddress("www.clientpeer.peer", 0));

			// connect to servers that were started with 0
				System.out.println("connect to servers that were started with 0\n");

				// connect to the info server to get the random port number,
				// and then connect to the actual server with the port number returned
				for (int i = 1; i <= 4; i++) {
					System.out.println("Connecting to info server on port " + i +
									   " to retrieve the random port number...");
					s = new P2PSocket("www.servers.info", i);
					DataInputStream in = new DataInputStream(s.getInputStream());
					int port = in.readInt();
					System.out.println("Info server returned port " + port);
					s.close();
					System.out.println("Connecting to www.serverpeer.peer:"+port+"...");
					s = new P2PSocket("www.serverpeer.peer", port);
					s_addr = P2PInetAddress.getByAddress("www.serverpeer.peer", null);
					siteName = "www.serverpeer.peer";
					s_socketAddr = new P2PSocketAddress(s_addr, port);

					handleSocket(s, s_addr, siteName, port, false, s_socketAddr, siteName+":"+port,
									P2PInetAddress.getLocalHost(), P2PInetAddress.getLocalHost().getHostName(), 
									0, true, new P2PSocketAddress("www.clientpeer.peer", 0));
				}

				// test the isHostNameTaken, isPortTaken, and isIPAddressTaken methods
				assertFalse(P2PNameService.isHostNameTaken("www.badname.foo"));
				assertTrue(P2PNameService.isHostNameTaken("www.boobah.cat"));
				assertTrue(P2PNameService.isHostNameTaken(null));
				assertTrue(P2PNameService.isHostNameTaken("localhost"));

				assertTrue(P2PNameService.isPortTaken("www.boobah.cat", 80));
				assertFalse(P2PNameService.isPortTaken("www.boobah.cat", 7823));
				assertTrue(P2PNameService.isPortTaken(null, 80));
				assertFalse(P2PNameService.isPortTaken(null, 2365));
				assertTrue(P2PNameService.isPortTaken("localhost", 80));
				assertFalse(P2PNameService.isPortTaken("localhost", 2365));
				assertFalse(P2PNameService.isPortTaken("www.badname.foo", 80));

				assertTrue(P2PNameService.isIPAddressTaken("238.39.22.221"));
				assertTrue(P2PNameService.isIPAddressTaken("118.158.193.121"));
				assertTrue(P2PNameService.isIPAddressTaken(null));
				assertTrue(P2PNameService.isIPAddressTaken("localhost"));
				assertFalse(P2PNameService.isIPAddressTaken("99.34.67.255"));

				// call a special server on the server side to tell it
				// that things are now finished
				s = new P2PSocket("www.server.shutdown", 80);
				s.close();

				System.out.println("\n\n**** ALL TESTS PASSED CORRECTLY ****");
				System.exit(0);

		}
		catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	protected void startServers() {
		try {
			// sign into the JXTA network
			P2PNetwork.signin("serverpeer", "serverpeerpassword", "TestCoreNetwork");

			// create the server sockets we need to satisfy client requests

			// P2PServerSocket(int port, int backlog, SocketAddress addr)
				// www.boobah.cat - 80
			System.out.println("P2PServerSocket(int port, int backlog, SocketAddress addr) - www.boobah.cat - 80\n");
			InetAddress server_addr = P2PInetAddress.getByAddress("www.boobah.cat", null);
			ServerSocket server = new P2PServerSocket(80, -1, server_addr);
			ServerThread serverThread = new ServerThread(server);
			serverThread.start();

			// P2PServerSocket(int port, int backlog, InetAddress bindAddr)
				// www.serverpeer.peer - random port number (info port 1)
			System.out.println("P2PServerSocket(int port, int backlog, SocketAddress addr) - www.serverpeer.peer - random port number\n");
			server = new P2PServerSocket(0, -1, null);
			serverThread = new ServerThread(server);
			serverThread.start();
			InfoServerThread infoThread = new InfoServerThread(1, server.getLocalPort());
			infoThread.start();

			// P2PServerSocket(int port, int backlog, InetAddress bindAddr)
				// www.serverpeer.peer - random port number (info port 2)
			System.out.println("P2PServerSocket(int port, int backlog, SocketAddress addr) - www.serverpeer.peer - random port number\n");
			server = new P2PServerSocket(0, -1, P2PInetAddress.getLocalHost());
			serverThread = new ServerThread(server);
			serverThread.start();
			infoThread = new InfoServerThread(2, server.getLocalPort());
			infoThread.start();
			
			// P2PServerSocket(int port, int backlog, SocketAddress addr)
				// P2PServerSocket(80, P2PInetAddress.getByAddress("www.nike.laborpolicy", "238.39.22.221"))
			byte[] ipAddressBytes = new byte[4];
			ipAddressBytes[0] = (byte)238;
			ipAddressBytes[1] = 39;
			ipAddressBytes[2] = 22;
			ipAddressBytes[3] = (byte)221;
			System.out.println("P2PServerSocket(int port, int backlog, SocketAddress addr) - P2PServerSocket(80, P2PInetAddress.getByAddress(\"www.nike.laborpolicy\", \"43.22.54.89\"))\n");
			server = new P2PServerSocket(80, -1, P2PInetAddress.getByAddress("www.nike.laborpolicy", ipAddressBytes));
			serverThread = new ServerThread(server);
			serverThread.start();


			// P2PServerSocket(int port)
				// www.serverpeer.peer - port 80
			System.out.println("P2PServerSocket(int port) - localhost - port 80\n");
			server = new P2PServerSocket(80);
			serverThread = new ServerThread(server);
			serverThread.start();

			// P2PServerSocket(int port)
				// www.serverpeer.peer - random port number (info port 3)
			System.out.println("P2PServerSocket(int port) - www.serverpeer.peer - random port number\n");
			server = new P2PServerSocket(0);
			serverThread = new ServerThread(server);
			serverThread.start();
			infoThread = new InfoServerThread(3, server.getLocalPort());
			infoThread.start();


			// P2PServerSocket()
				// www.serverpeer.peer - 82
			System.out.println("P2PServerSocket() - www.serverpeer.peer - 82\n");
			server = new P2PServerSocket();
			server.bind(new P2PSocketAddress(82));
			serverThread = new ServerThread(server);
			serverThread.start();

			// P2PServerSocket()
				// www.serverpeer.peer - 81
			System.out.println("P2PServerSocket() - www.serverpeer.peer - 81\n");
			server = new P2PServerSocket();
			server.bind(new P2PSocketAddress(81));
			serverThread = new ServerThread(server);
			serverThread.start();

			// P2PServerSocket()
				// www.boobah.cat - 81
			System.out.println("P2PServerSocket() - www.boobah.cat - 81\n");
			server = new P2PServerSocket();
			server.bind(new P2PSocketAddress("www.boobah.cat", 81));
			serverThread = new ServerThread(server);
			serverThread.start();

			// P2PServerSocket()
				// null - random port number (info port 4)
			System.out.println("P2PServerSocket() - null - random port number\n");
			server = new P2PServerSocket();
			server.bind(null);
			serverThread = new ServerThread(server);
			serverThread.start();
			infoThread = new InfoServerThread(4, server.getLocalPort());
			infoThread.start();

			// P2PServerSocket()
				// 118.158.193.121 (www.acme.company) - 80
			System.out.println("P2PServerSocket() - 118.158.193.121 (www.acme.company) - 80\n");
			server = new P2PServerSocket();
			ipAddressBytes[0] = (byte)118;
			ipAddressBytes[1] = (byte)158;
			ipAddressBytes[2] = (byte)193;
			ipAddressBytes[3] = (byte)121;
			server.bind(new P2PSocketAddress(P2PInetAddress.getByAddress("www.acme.company", ipAddressBytes), 80));
			serverThread = new ServerThread(server);
			serverThread.start();	

			// this server is called to signal that things should be shut down
			server_addr = P2PInetAddress.getByAddress("www.server.shutdown", null);
			server = new P2PServerSocket(80, -1, server_addr);
		    serverThread = new ServerThread(server);
			serverThread.start();

		}
		catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	protected void handleSocket(Socket socket, 
								InetAddress correctRemoteAddress, String correctRemoteHost, int correctRemotePort,
								boolean isRandomRemotePort, P2PSocketAddress correctRemoteSocketAddress,
								String correctRemoteString,
								InetAddress correctLocalAddress, String correctLocalHost, int correctLocalPort,
								boolean isRandomLocalPort, P2PSocketAddress correctLocalSocketAddress) throws Exception {
		// check remote endpoint values
		InetAddress remoteInetAddress = socket.getInetAddress();
		assertEqual(remoteInetAddress, correctRemoteAddress);
		assertEqual(remoteInetAddress.getHostName(), correctRemoteHost);
		if (isRandomRemotePort) {
			assertNotEqual(socket.getPort(), 0);
			assertNotEqual(socket.getPort(), -1);
		}
		else {
			assertEqual(socket.getPort(), correctRemotePort);
		}	
		assertNotNull(socket.getRemoteSocketAddress());
		assertEqual(socket.getRemoteSocketAddress(), correctRemoteSocketAddress);
		if (isRandomRemotePort) {
			assertNotEqual(((P2PSocketAddress)socket.getRemoteSocketAddress()).getPort(), 0);
			assertNotEqual(((P2PSocketAddress)socket.getRemoteSocketAddress()).getPort(), -1);
		}
		else {
			assertEqual(((P2PSocketAddress)socket.getRemoteSocketAddress()).getPort(), correctRemotePort);
		}

		// check local endpoint values
		InetAddress localInetAddress = socket.getLocalAddress();
		assertEqual(localInetAddress, correctLocalAddress);
		assertEqual(localInetAddress.getHostName(), correctLocalHost);
		if (isRandomLocalPort) {
			assertNotEqual(socket.getLocalPort(), 0);
			assertNotEqual(socket.getLocalPort(), -1);
		}
		else {
			assertEqual(socket.getLocalPort(), correctLocalPort);
		}	
		assertNotNull(socket.getLocalSocketAddress());
		if (isRandomLocalPort == false) {
			assertEqual(socket.getLocalSocketAddress(), correctLocalSocketAddress);
		}
		if (isRandomLocalPort) {
			assertNotEqual(((P2PSocketAddress)socket.getLocalSocketAddress()).getPort(), 0);
			assertNotEqual(((P2PSocketAddress)socket.getLocalSocketAddress()).getPort(), -1);
		}
		else {
			assertEqual(((P2PSocketAddress)socket.getLocalSocketAddress()).getPort(), correctLocalPort);
		}

		// toString
		assertEqual(socket.getRemoteSocketAddress().toString(), correctRemoteString);

		// connection status flags
		assertTrue(socket.isBound());
		assertFalse(socket.isClosed());
		assertTrue(socket.isConnected());
		assertFalse(socket.isInputShutdown());
		assertFalse(socket.isOutputShutdown());

		// test sending and receiving data
		DataInputStream in = new DataInputStream(socket.getInputStream());
		DataOutputStream out = new DataOutputStream(socket.getOutputStream());
		out.writeUTF("hello server world");
        out.flush();
		System.out.println("Wrote request");
		String results = in.readUTF();
		System.out.println(results);
		assertEqual(results, "hello client world");
		socket.shutdownInput();
		socket.shutdownOutput();
		
		// make sure reading and writing is shutdown now
		boolean ioWorking = true;
		try {	
			out.writeUTF("this should not work");
            out.flush();
		}
		catch (Exception e) {
			ioWorking = false;
		}	
		assertFalse(ioWorking);

		ioWorking = true;
		try {	
			in.readUTF();
		}
		catch (Exception e) {
			ioWorking = false;
		}	
		assertFalse(ioWorking);

		// connection status flags
		assertTrue(socket.isBound());
		assertFalse(socket.isClosed());
		assertTrue(socket.isConnected());
		assertTrue(socket.isInputShutdown());
		assertTrue(socket.isOutputShutdown());

		// close()

		socket.close();

		// connection status flags
		assertTrue(socket.isBound());
		assertTrue(socket.isClosed());
		assertTrue(socket.isConnected());
		assertTrue(socket.isInputShutdown());
		assertTrue(socket.isOutputShutdown());
	}

	/** Starts up socket servers that can satisfy loopback/"localhost" sockets. The
	  * client portion of the testing code calls this method.
	  */
	protected void startLocalHostServers() throws Exception {
		InetAddress server_addr = P2PInetAddress.getLocalHost();
		ServerSocket server = new P2PServerSocket(80, -1, server_addr);
		ServerThread localHostThread = new ServerThread(server);
		localHostThread.start();

		server_addr = P2PInetAddress.getLocalHost();
		server = new P2PServerSocket(95, -1, server_addr);
		localHostThread = new ServerThread(server);
		localHostThread.start();
	}

	protected void assertEqual(Object obj1, Object obj2) {
		if (obj1 == null && obj2 == null) {
		}
		else if (obj1 == null && obj2 != null) {
			System.out.println("assertEqual(null, " + obj2.toString() + ") failed");
			System.out.println("!!!! TEST FAILED !!!!");
            try { throw new RuntimeException(); } catch (Exception e) { e.printStackTrace(); }
			System.exit(1);
		}	
		else if (obj1 != null && obj2 == null) {
			System.out.println("assertEqual(" + obj1.toString() + ", null) failed");
			System.out.println("!!!! TEST FAILED !!!!");
            try { throw new RuntimeException(); } catch (Exception e) { e.printStackTrace(); }
			System.exit(1);
		}
		else if (obj1.equals(obj2) == false) {
			System.out.println("assertEqual(" + obj1.toString() + "," + obj2.toString()+") failed");
			System.out.println("!!!! TEST FAILED !!!!");
            try { throw new RuntimeException(); } catch (Exception e) { e.printStackTrace(); }
			System.exit(1);
		}
	}

	protected void assertEqual(boolean val1, boolean val2) {
		if (val1 != val2) {
			System.out.println("assertEqual(" + val1 + "," + val2 + ") failed");
			System.out.println("!!!! TEST FAILED !!!!");
            try { throw new RuntimeException(); } catch (Exception e) { e.printStackTrace(); }
			System.exit(1);
		}
	}

	protected void assertEqual(int int1, int int2) {
		if (int1 != int2) {
			System.out.println("assertEqual(" + int1 + "," + int2 + ") failed");
			System.out.println("!!!! TEST FAILED !!!!");
            try { throw new RuntimeException(); } catch (Exception e) { e.printStackTrace(); }
			System.exit(1);
		}
	}

	protected void assertNotEqual(Object obj1, Object obj2) {
		if (obj1 == null && obj2 == null) {
			System.out.println("assertNotEqual(null, null) failed");
			System.out.println("!!!! TEST FAILED !!!!");
            try { throw new RuntimeException(); } catch (Exception e) { e.printStackTrace(); }
			System.exit(1);
		}
		else if (obj1 == null && obj2 != null) {
		}	
		else if (obj1 != null && obj2 == null) {
		}
		else if (obj1.equals(obj2)) {
			System.out.println("assertNotEqual(" + obj1.toString() + "," + obj2.toString()+") failed");
			System.out.println("!!!! TEST FAILED !!!!");
            try { throw new RuntimeException(); } catch (Exception e) { e.printStackTrace(); }
			System.exit(1);
		}
	}

	protected void assertNotEqual(boolean val1, boolean val2) {
		if (val1 == val2) {
			System.out.println("assertNotEqual(" + val1 + "," + val2 + ") failed");
			System.out.println("!!!! TEST FAILED !!!!");
            try { throw new RuntimeException(); } catch (Exception e) { e.printStackTrace(); }
			System.exit(1);
		}
	}

	protected void assertNotEqual(int int1, int int2) {
		if (int1 == int2) {
			System.out.println("assertNotEqual(" + int1 + "," + int2 + ") failed");
			System.out.println("!!!! TEST FAILED !!!!");
            try { throw new RuntimeException(); } catch (Exception e) { e.printStackTrace(); }
			System.exit(1);
		}
	}

	protected void assertNull(Object obj1) {
		if (obj1 != null) {
			System.out.println("assertNull(" + obj1.toString() + ") failed");
			System.out.println("!!!! TEST FAILED !!!!");
            try { throw new RuntimeException(); } catch (Exception e) { e.printStackTrace(); }
			System.exit(1);
		}
	}

	protected void assertNotNull(Object obj1) {
		if (obj1 == null) {
			System.out.println("assertNotNull() failed");
			System.out.println("!!!! TEST FAILED !!!!");
            try { throw new RuntimeException(); } catch (Exception e) { e.printStackTrace(); }
			System.exit(1);
		}
	}

	protected void assertTrue(boolean val1) {
		if (val1 != true) {
			System.out.println("assertTrue() failed");
			System.out.println("!!!! TEST FAILED !!!!");
            try { throw new RuntimeException(); } catch (Exception e) { e.printStackTrace(); }
			System.exit(1);
		}
	}

	protected void assertFalse(boolean val1) {
		if (val1 == true) {
			System.out.println("assertFalse() failed");
			System.out.println("!!!! TEST FAILED !!!!");
            try { throw new RuntimeException(); } catch (Exception e) { e.printStackTrace(); }
			System.exit(1);
		}
	}
}
