/** 
 * Copyright (c) 2003 by Bradley Keith Neuberg, bkn3@columbia.edu.
 *
 * Redistributions in source code form must reproduce the above copyright and this condition.
 *
 * The contents of this file are subject to the Sun Project JXTA License Version 1.1 (the "License"); 
 * you may not use this file except in compliance with the License. A copy of the License is available 
 * at http://www.jxta.org/jxta_license.html.
 */

import java.io.*;
import java.net.*;

import org.p2psockets.*;

import org.mortbay.util.*;
import org.mortbay.http.*;
import org.mortbay.p2psockets.jetty.*;
import org.mortbay.p2psockets.util.*;
import org.mortbay.p2psockets.http.*;
import org.mortbay.jetty.*;
import org.mortbay.jetty.servlet.*;
import org.mortbay.http.handler.*;
import org.mortbay.servlet.*; 

/** This class tests setting up a peer-to-peer web server. */
public class ExampleP2PWebServer {
  public static void main(String args[]) throws Exception {
    String host = System.getProperty("host", "www.boobah.cat");
    String network = System.getProperty("network", "TestNetwork");
    String userName = System.getProperty("username", "serverpeer");
    String password = System.getProperty("password", "serverpeerpassword");
    int port = Integer.parseInt(System.getProperty("--port", "80"));
    
    // initialize and sign into the peer-to-peer network; profile this peer and create it
	// if it doesn't exist
    P2PNetwork.signin(userName, password, network, true);
  
    // Create the server
    Server server = new P2PServer();
     
    // Create a port listener
    System.out.println("Starting web server for " + host + " on port " 
                       + port + " using P2P network " + network + "...");
    SocketListener listener=new P2PSocketListener();
    listener.setInetAddress(P2PInetAddress.getByAddress(host, null));
    listener.setPort(port);
    server.addListener(listener);

    // Create a context
    HttpContext context = new HttpContext();
    context.setContextPath("/");
    server.addContext(context);
     
    // Create a servlet container
    ServletHandler servlets = new ServletHandler();
    context.addHandler(servlets);

    // Map a servlet onto the container
    servlets.addServlet("Dump","/Dump/*","org.mortbay.servlet.Dump");
    server.addWebApplication("/", System.getProperty("p2psockets_home") +
							 "/webapps/jetty/JSPWiki.war");
    
    // Serve dynamic content from /webapps directory
    server.addWebApplication("/webapps", System.getProperty("p2psockets_home") +
                             "/webapps");
     
    // Serve static content from the context
    context.setResourceBase(System.getProperty("p2psockets_home") +
							"/docroot");
    context.addHandler(new ResourceHandler());

    // Start the http server
    server.start();
  }
}
