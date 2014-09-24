/*
 * Copyright (c) 1998, 1999, 2000, 2001, 2002, 2003 Fredrik Widlert, Robert 
 * Olofsson, Alexander La Torre, Hakan Andersson, Nina Odling, Vicky 
 * Carlsson, Malin Blomqvist, Linnea Lofstedt and Radim Kolar.
 * All rights reserved. 
 *
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions 
 * are met: 
 *
 * 1. Redistributions of source code must retain the above copyright 
 *    notice, this list of conditions and the following disclaimer. 
 *
 * 2. Redistributions in binary form must reproduce the above copyright 
 *    notice, this list of conditions and the following disclaimer in the 
 *    documentation and/or other materials provided with the distribution. 
 *
 * 3. Neither the name of the authors nor the names of its contributors 
 *    may be used to endorse or promote products derived from this software 
 *    without specific prior written permission. 
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHORS AND CONTRIBUTORS ``AS IS'' AND 
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHORS OR CONTRIBUTORS BE LIABLE 
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL 
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS 
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT 
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY 
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF 
 * SUCH DAMAGE. 
 */

package org.scache;

import java.io.*;
import java.util.*;
import java.net.*;

import org.p2psockets.*;

/** This class acts as a proxy between ordinary HTTP requests
  * from a web browser and the JXTA peer-to-peer network.
  */
public class P2PToWebProxy {
	private static String PROXY_PORT = "proxy.port";
	private static String CACHE_DIR = "proxy.cache.dir";
	protected String userName;
	protected String password;
	protected int port;
	protected String applicationGroupName;
	protected String cacheDir;

	public P2PToWebProxy(String userName, String password, String applicationGroupName, int port) {
		this.userName = userName;
		this.password = password;
		this.port = port;
		this.applicationGroupName = applicationGroupName;
	}

	public P2PToWebProxy(String userName, String password, String applicationGroupName, int port,
						 String cacheDir) {
		this(userName, password, applicationGroupName, port);
		this.cacheDir = cacheDir;
	}

	/** Starts the P2P to Web proxy and signs into the JXTA network.
	  */
	public void start() throws Exception {
		start(true);
	}

	/** Starts the P2P to Web proxy.
	  *
	  * @param signIntoJXTA If true, we sign into JXTA; if false, we assume that this user
	  * is already signed into JXTA.
	  */
	public void start(boolean signIntoJXTA) throws Exception {
		// parse things into a form the scache can understand "on the command-line"
		String args[];
		if (cacheDir == null) {
			args = new String[6];
		}
		else {
			args = new String[8];
		}
		args[0] = "--username";
		args[1] = userName;
		args[2] = "--password";
		args[3] = password;
		args[4] = "--port";
		args[5] = new Integer(port).toString();
        
		if (cacheDir != null) {
			args[6] = "--cachedir";
			args[7] = cacheDir;
		}
        
		// parse command line arguments, bkn3@columbia.edu
		String newArgs[] = handleArguments(args);

		// sign in and initialize the JXTA network
		if (signIntoJXTA) {
			P2PNetwork.signin(userName, password, applicationGroupName);
		}

		// create the actual proxy that does the hard work
		System.out.println("Creating P2P to Web proxy...");
		scache.main(newArgs);
		System.out.println("P2P to Web proxy created.");
	}

	public static void main(String args[]) {
		try {
			// parse command line arguments, bkn3@columbia.edu
			String newArgs[] = handleArguments(args);

			// sign in and initialize the JXTA network
            // FIXME: Be able to pass this in and configure it
			P2PNetwork.signin("clientpeer", "clientpeerpassword");

			// create the actual proxy that does the hard work
			System.out.println("Creating proxy...");
			scache.main(newArgs);
			System.out.println("Proxy created.");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	/** Returns out if the user want's the given 'thing',
	  * such as "--help", "--serverpeer", or "--clientpeer"
	  * @argument The argument to check for, without dashes. Example "help".
	  * @author Brad GNUberg, bkn3@columbia.edu
	  */
	private static boolean userWants(String argumentName, String args[]) {
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("--" + argumentName)) {
				return true;
			}
		}

		return false;
	}

	/** Prints out the available command-line options.
	  * @author Brad GNUberg, bkn3@columbia.edu
	  */
	private static void printHelp() {
		System.out.println("This class starts the P2PToWebProxy.");
		System.out.println("The P2PToWebProxy gateways requests from clients, such as web browsers,");
		System.out.println("that are not written in Java into the P2P Sockets JXTA network and back");
		System.out.println("again.");
		System.out.println();
		System.out.println("Options:");
		System.out.println("\t--help - print out help on using the P2PToWebProxy");
		System.out.println();
		System.out.println("\t--port - use the given port for this P2P proxy");
		System.out.println("\t\tExample: --port 8080");
		System.out.println();
		System.out.println("\t--network - the peer network to sign into");
		System.out.println("\t\tExample: --network \"Test Network\"");
		System.out.println();
		System.out.println("\t--username - the JXTA username of this peer");
		System.out.println("\t\tExample: --username BradGNUberg");
		System.out.println();
		System.out.println("\t--password - the JXTA password of this peer");
		System.out.println("\t\tExample: --username mypassword");
		System.out.println();
		System.out.println("\t--clientpeer - indicates that this peer is a client peer.");
		System.out.println("\t               This will use the pre-configured client in p2psockets/test/clientpeer.");
		System.out.println("\t               You do not need to give the username or password if this is used.");
		System.out.println();
		System.out.println("\t--serverpeer - indicates that this peer is a server peer.");
		System.out.println("\t               This will use the pre-configured server in p2psockets/test/serverpeer.");
		System.out.println("\t               You do not need to give the username or password if this is used.");
		System.out.println();
		System.out.println("\t--cachedir - the location to store the proxies web cache.");
		System.out.println();

		System.exit(0);
	}

	/** Parses out a string argument from the command-line and uses this value
	  * to set a system property.
	  *	@argument argumentName The argument name to look for, such as "--hostname" or
	  *	"--network".
	  *	@argument systemPropertyName The system property that will be set for this argument
	  *	instead of giving it on the command-line, such as "jetty.host" or "p2psockets.network".
	  *	@argument arg The array of String args[] that were passed in on the command-line.
	  * @author Brad GNUberg, bkn3@columbia.edu
	  */
	private static void handleArgument(String argumentName, String systemPropertyName, String[] arg) {
		String argumentValue = null;

		for (int i = 0; i < arg.length; i++) {

			if (arg[i].equals(argumentName)) {

				// make sure the user actually gave an argument value
				if ( (i + 1) == arg.length ||			// no value and we are the last argument!
					 arg[i + 1].startsWith("--")) {		// no value was given, just another argument name!
					System.out.println("Please provide a value for " + argumentName);
					printHelp();
				}
				else {
					argumentValue = arg[i + 1];
				}
			}
		}

		// at this point, we either have an argument value, or the argumentName wasn't given on the
		// command-line (i.e. they want to use what the system property already is)
		if (argumentValue != null) {
			System.setProperty(systemPropertyName, argumentValue);
		}
	}

	/** Handles each of the arguments given on the command-line.
	  * @returns An array of new arguments that will be passed on to
	  * the underlying Smart Cache implementation; we set these in the
	  * Smart Cache format.
	  * @author Brad GNUberg, bkn3@columbia.edu
	  */
	private static String[] handleArguments(String arg[]) {
		List scacheArguments = new ArrayList();

		if (userWants("help", arg)) {
			printHelp();
		}

		// these are friendly flags that do alot of the hard work of
		// specifying where to find Jxta configuration files, usernames, passwords,
		// etc.
		String peerType = null;
		if (userWants("clientpeer", arg)) {
			peerType = "clientpeer";
		}
		else if (userWants("serverpeer", arg)) {
			peerType = "serverpeer";
		}

		if (peerType != null) {
			String newArguments[] = new String[arg.length + 4];
			
			newArguments[arg.length] = "--username";
			newArguments[arg.length + 1] = peerType;

			newArguments[arg.length + 2] = "--password";
			newArguments[arg.length + 3] = peerType + "password";

			System.arraycopy(arg, 0, newArguments, 0, arg.length);
			
			arg = newArguments;

			System.setProperty(P2PNetwork.JXTA_HOME, "./test/" + peerType + "/.jxta");
		}

		handleArgument("--port", PROXY_PORT, arg);

		handleArgument("--network", P2PNetwork.P2PSOCKETS_NETWORK, arg);
			
		handleArgument("--username", P2PNetwork.JXTA_USERNAME, arg);
		handleArgument("--password", P2PNetwork.JXTA_PASSWORD, arg);
	
		handleArgument("--cachedir", CACHE_DIR, arg);

		/** At this point, the only thing we will be passing through should be -port
		  * and -cachedir if the user wants to specify that. */

		String proxyPort = System.getProperty(PROXY_PORT);
		if (proxyPort != null) {			
			scacheArguments.add("-port");
			scacheArguments.add(proxyPort);
		}

		String cacheDir = System.getProperty(CACHE_DIR);
		if (cacheDir != null) {
			scacheArguments.add("-cachedir");
			scacheArguments.add(cacheDir + File.separator + "store");
		}
        
        scacheArguments.add("-nocache");

		System.out.println("scacheArguments="+scacheArguments);
		String results[] = new String[scacheArguments.size()];
		for (int i = 0; i < scacheArguments.size(); i++) {
			results[i] = (String)scacheArguments.get(i);
		}

		return results;
	}
}