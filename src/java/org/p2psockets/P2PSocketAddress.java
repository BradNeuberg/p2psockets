/**
 * Copyright (c) 2003 by Bradley Keith Neuberg, bkn3@columbia.edu.
 *
 * Redistributions in source code form must reproduce the above copyright and this condition.
 *
 * The contents of this file are subject to the Sun Project JXTA License Version 1.1 (the "License");
 * you may not use this file except in compliance with the License. A copy of the License is available
 * at http://www.jxta.org/jxta_license.html.
 */

package org.p2psockets;

import java.net.*;
import java.util.*;
import org.apache.log4j.Logger;

/**
 * This class implements a P2P Socket Address (Jxta host name + port number + IP Address).
 *
 * @author Brad GNUberg, bkn3@columbia.edu
 * @version 0.5
 */
public class P2PSocketAddress extends SocketAddress {
    private static final Logger log = Logger.getLogger("org.p2psockets.P2PSocketAddress");
    
    private String host = null;
    private InetAddress inetAddr = null;
    private int port;
    
    /**
     * Creates a socket address where the IP address is the wildcard address
     * and the port number a specified value.
     * <p>
     * A valid port value is between 0 and 65535.
     * A port number of <code>zero</code> will let the system pick up an
     * ephemeral port in a <code>bind</code> operation.
     * <p>
     * @param	port	The port number
     * @throws IllegalArgumentException if the port parameter is outside the specified
     * range of valid port values.
     */
    public P2PSocketAddress(int port) {
	this((InetAddress)null, port);
    }
    
    /**
     *
     * Creates a socket address from an IP address and a port number.
     * <p>
     * A valid port value is between 0 and 65535.
     * A port number of <code>zero</code> will let the system pick up an
     * ephemeral port in a <code>bind</code> operation.
     * <P>
     * A <code>null</code> address will assign the <i>wildcard</i> address.
     * <p>
     * @param	inetAddr	The IP address
     * @param	port	The port number
     * @throws IllegalArgumentException if the port parameter is outside the specified
     * range of valid port values.
     */
    public P2PSocketAddress(InetAddress inetAddr, int port) {
	if (port < 0 || port > 65535)
	    throw new IllegalArgumentException("The port give is out of range: " + port);
	
	if (port == 0)
	    port = generateRandomPort();
	
	this.port = port;
	
	if (inetAddr == null) {
	    try {
		this.inetAddr = P2PInetAddress.getLocalHost();
	    } catch (UnknownHostException e) {
		inetAddr = null;
	    }
	} else {
	    if(P2PInetAddress.isLocalHost(inetAddr.getHostName())){
		try{
		    inetAddr = P2PInetAddress.getLocalHost();
		}catch(P2PInetAddressException p2piae){
		    log.error("Unexpected error getting vertual localhost",p2piae);
		    throw new RuntimeException(p2piae);
		}
	    }
	    this.inetAddr = inetAddr;
	}
	
	this.host = this.inetAddr.getHostName();
	
    }
    
    /**
     *
     * Creates a socket address from a host and a port number.
     * <p>
     * An attempt will be made to resolve the host into an InetAddress.
     * If that attempt fails, the address will be flagged as <I>unresolved</I>.
     * <p>
     * A valid port value is between 0 and 65535.
     * A port number of <code>zero</code> will let the system pick up an
     * ephemeral port in a <code>bind</code> operation.
     * <P>
     * @param	host the Host name
     * @param	port	The port number
     * @throws IllegalArgumentException if the port parameter is outside the range
     * of valid port values, or if the host parameter is <TT>null</TT>.
     * @see	#isUnresolved()
     */
    public P2PSocketAddress(String host, int port) {
	//System.out.println("Constructor address "+host+":"+port);
	if (port < 0 || port > 65535)
	    throw new IllegalArgumentException("The port given is out of range: " + port);
	
	
	if (P2PInetAddress.isLocalHost(host)) {
	    try {
		inetAddr = P2PInetAddress.getLocalHost();
		host = inetAddr.getHostName();
	    } catch (UnknownHostException e) {
		log.error("Exception creating address for "+host+":"+port, e);
		throw new RuntimeException(e.toString());
	    }
	} else {
	    try {
		inetAddr = P2PInetAddress.getByAddress(host, null);
		this.host = this.inetAddr.getHostName();
	    } catch(UnknownHostException e) {
		//e.printStackTrace();
		//System.out.println("Unknown host!!");
		this.host = host;
		inetAddr = null;
	    }
	}
	
	if (port == 0)
	    port = generateRandomPort();
	
	this.port = port;
    }
    
    /**
     * Gets the port number.
     *
     * @return the port number.
     */
    public final int getPort() {
	return port;
    }
    
    /**
     *
     * Gets the <code>InetAddress</code>.
     *
     * @return the InetAdress or <code>null</code> if it is unresolved.
     */
    public final InetAddress getAddress() {
	return inetAddr;
    }
    
    /**
     * Gets the <code>host</code>.
     *
     * @return	the host part of the address.
     */
    public final String getHostName() {
	if (host != null)
	    return host;
	
	if (inetAddr != null)
	    return inetAddr.getHostName();
	
	return null;
    }
    
    public void setPort(int port) {
	this.port = port;
    }
    
    public void setHostName(String host) {
	this.host = host;
    }
    
    public String toString() {
	if (host != null) {
	    return host + ":" + port;
	} else if (inetAddr != null && inetAddr.getHostAddress() != null) {
	    return inetAddr.getHostAddress() + ":" + port;
	} else {
	    return "unresolved";
	}
    }
    
    public final boolean equals(Object obj) {
	if (!(obj instanceof P2PSocketAddress))
	    return false;
	
	P2PSocketAddress compareMe = (P2PSocketAddress)obj;
	boolean results = false;
	
	if (compareMe.getAddress() != null &&
	inetAddr != null) {
	    results = compareMe.getAddress().equals(inetAddr);
	} else if (compareMe.getHostName() != null &&
	host != null) {
	    results = compareMe.getHostName().equals(host);
	}
	
	return results & (compareMe.getPort() == port);
    }
    
    public final int hashCode() {
	if (inetAddr != null)
	    return inetAddr.hashCode() + port;
	
	if (host != null)
	    return host.hashCode() + port;
	
	return port;
    }
    
    /** This method generates a random port number.  This is used because
     * in some cases we don't 'care' what the local or remote port number is,
     * such as when we are a client talking to a server and want to know
     * what port the client is communicating on locally: the client is
     * not even communicating through a port, since they are using JXTA.
     * We still need a value though and simply create this number using
     * this method. */
    protected int generateRandomPort() {
	Random randomGenerator = new Random();
	
	int port = randomGenerator.nextInt() % 65536;
	
	// make negative numbers positive
	if (port < 0)
	    port = port * (-1);
	
	if (port <= 1024)
	    port += 1024;
	
	return port;
    }
}

