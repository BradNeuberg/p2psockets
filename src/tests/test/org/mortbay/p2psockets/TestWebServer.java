package test.org.mortbay.p2psockets;

import java.io.*;
import java.net.*;

import org.p2psockets.*;

import org.mortbay.util.*;
import org.mortbay.http.*;
import org.mortbay.p2psockets.util.*;
import org.mortbay.p2psockets.http.*;
import org.mortbay.jetty.*;
import org.mortbay.jetty.servlet.*;
import org.mortbay.http.handler.*;
import org.mortbay.servlet.*;  

/** This class tests setting up a Jxta-based web server. */
public class TestWebServer {
  public static void main(String args[]) throws Exception {
	String JETTY_HOME = System.getProperty("JETTY_HOME");
        System.out.println("JETTY_HOME is "+JETTY_HOME);
	if (JETTY_HOME == null) {
		throw new RuntimeException("You must provide a JETTY_HOME system variable "
								   + "that is the full path, such as "
								   + "c:/p2psockets/support-packages/jetty");
	}

	// initialize JXTA
	// sign in and initialize the JXTA network
	P2PNetwork.signin(args[0], args[1]);
   
    // Create the server
    Server server=new Server();
      
    // Create a port listener
	System.out.println("Starting web server for " + args[2] + "...");
    SocketListener listener=new P2PSocketListener();
	listener.setInetAddress(P2PInetAddress.getByAddress(args[2], null));
	listener.setPort(80);
    server.addListener(listener);

    // Create a context 
    HttpContext context = new HttpContext();
    context.setContextPath("/");
    server.addContext(context);
      
    // Create a servlet container
    ServletHandler servlets = new ServletHandler();
    context.addHandler(servlets);

    // Map a servlet onto the container
    servlets.addServlet("Test","/Test/*","test.org.mortbay.p2psockets.TestServlet");
	servlets.addServlet("Dump","/Dump/*","org.mortbay.servlet.Dump");
        
        //**//jxl start
	server.addWebApplication("/", JETTY_HOME + "/jetty/JSPWiki.war");

	// Serve static content from the context
    context.setResourceBase(JETTY_HOME+"/../");
    //**//jxl end
    
    context.addHandler(new ResourceHandler());

    // Start the http server
    server.start();
  }
}