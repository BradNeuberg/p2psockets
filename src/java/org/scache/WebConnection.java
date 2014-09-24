package org.scache;

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
// Id: WebConnection.java,v 1.7 2001/09/02 13:37:22 robo Exp

import java.io.*;
import java.net.*;
import java.util.*;

import org.p2psockets.*;

/** A class to handle a open connection to the Internet.
 *  @modified Brad GNUberg, bkn3@columbia.edu - Added support for JXTA P2P sockets
 */
public class WebConnection 
{
    public  Socket socket;
    public  DataInputStream webis;
    public  DataOutputStream webos;
    public  InetAddress ia;
    public  int port;
    public  long releasedAt;
    public  boolean keepalive;

    /** Create a new WebConnection to the given InetAddress and port.
     * @param InetAddress the computer to connect to.
     * @param port the port number to connect to.
     */
    public WebConnection (InetAddress ia, int port) throws IOException {
	this.ia = ia; 
	this.port = port;
	
	if(port<=0 || port >65535) throw new java.net.UnknownHostException("Port number out of range");
        if(ia==null) throw new java.net.UnknownHostException("Host not found in WebConnection");
	socket=null;
	reconnect();
    }

    // fake WC for file input
    public WebConnection(DataInputStream is)
    {
        keepalive=false;
        webis=is;
    }

    public void reconnect() throws IOException
    {
	keepalive=false;
	releasedAt=0;

	if(socket!=null)
	    try
	    {
		socket.close();
	    }
	catch (Exception ignore)
	    {
	    }
	
	// added by Brad GNUberg, bkn3@columbia.edu -- adds support for Jxta P2P Sockets
	String hostName = ia.getHostName();
	byte address[] = ia.getAddress();
	if (hostName == null)
		socket = new P2PSocket(P2PInetAddress.getByAddress(null, address), port);
	else
		socket = new P2PSocket(hostName, port);

	socket.setTcpNoDelay(true); // disable  nagle
	
	webos = new DataOutputStream(
		new BufferedOutputStream(
		    socket.getOutputStream () , 2048
		                  ) ); 
		                   
	webis = new DataInputStream (
		new BufferedInputStream (
		    socket.getInputStream () , 2048
		                    )   );
    }

    /** Close the connection.
     */
    public void close () throws IOException {
	try {
	    if(webos!=null) webos.close ();
	    if(webis!=null) webis.close ();
	    if(socket!=null) socket.close ();
	} catch (IOException e) {
	    // System.out.println("couldnt close WebConnection: " + e.getMessage ());
	    throw e;
	}
	finally
	{
	    webos = null;
	    webis = null;
	    socket = null;
	    ia = null;
	    releasedAt = 0;
	    keepalive = false;
	}
    }
    
    /** Get the InetAddress that this WebConnection is connected to.
     * @return the InetAddress.
     */
    public InetAddress getInetAddress () {
	return ia;
    }
    
    /** Get the port number this WebConnection is connected to.
     * @return the port number.
     */
    public int getPort () {
	return port;
    }

    /** Mark this WebConnection as released at current time.
     */
    public void setReleased () {
	setReleased (System.currentTimeMillis());
    }

    /** Mark this WebConnection as released at given time.
     * @param d the time that this WebConnection is released.
     */
    public void setReleased (long d) {
	releasedAt = d;
	keepalive=false;
    }
    
    /** Get the time that this WebConnection was released.
     */
    public long getReleasedAt () {
	return releasedAt;
    }

    /** Get the InputStream.
     * @return an DataInputStream.
     */
    public DataInputStream getInputStream () {
	return webis;
    }
    
    /** Get the OutputStream of this WebConnection.
     * @return an DataOutputStream.
     */
    public DataOutputStream getOutputStream () {
	return webos;
    }

    /** Get the keepalive value of this WebConnection.
     * @return true if this WebConnection may be reused.
     */
    public boolean getKeepAlive () {
	return keepalive;
    }

    public void setKeepAlive (boolean ka) {
	keepalive=ka;
    }

    public String toString()
    {
	if(ia!=null) 
	    return ia.getHostAddress () + ":" + port;
	else
	    return "UNKNOWN/Closed";
    }
}
