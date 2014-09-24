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
// Id: ConnectionHandler.java,v 1.11 2003/01/04 15:12:22 robo Exp

import java.io.*;
import java.net.*;
import java.util.*;

/** A class to handle the connections to the net. Should reuse connections if possible.
 */
public final class ConnectionHandler extends Thread
{
    public  static boolean trace_keepalive;

    // The available connections.
    private static Hashtable activeConnections;

    // The cleaner thread.
    private static Thread cleaner;

    // the keepalivetime.
    public static long keepalivetime = 25000;
    
    /** Create a new ConnectionHandler for use. 
     */
    public final static void init() {
        if(activeConnections!=null) return; //
        activeConnections = new Hashtable ();
        cleaner = new ConnectionHandler();
        cleaner.setDaemon (true);
        cleaner.start ();
    }

    /** Check if the cleaner of this ConnectionHandler is running.
     */
    public final static boolean isCleanerRunning () {
        if(cleaner!=null)
            if(cleaner.isAlive())
                return true;
        return false;
    }

    /** Get the connections keept.
     *  @return an Enumeration of Vectors with WebConnections.
     */
    public final static Enumeration getConnections () {
        return activeConnections.elements ();
    }

    /** Get the addresses we have connections to.
     */
    public final static Enumeration getAddresses () {
        return activeConnections.keys ();
    }

    /** Get the pool for an Address.
     *  NOTE! synchronize on the pool if you are taking connections from it.
     */
    public final static Vector getPool (String a) {
        return (Vector)activeConnections.get (a);
    }
    
    /** Get a WebConnection for the given IP:Port
     * @return a WebConnection.
     */
    public final static WebConnection getConnection (InetAddress ia, int port) throws IOException {  
        WebConnection wc = null;
	String a;
        if(ia==null) throw new java.net.UnknownHostException("Host not found");
        a = ia.getHostAddress() + ":" + port;
        Vector pool = (Vector)activeConnections.get (a);
	if(pool==null) return new WebConnection(ia,port);
        synchronized (pool) {
                if (pool.size () > 0) {
                    wc = (WebConnection)pool.elementAt (pool.size () - 1);
                    pool.removeElementAt (pool.size () - 1);
		    if(trace_keepalive)
		    {
	               System.out.println("[TRACE "+Thread.currentThread().getName()+"] * Getting connection from pool to "+a);
		    }
                } else {
                    wc = new WebConnection (ia, port);
                }
        return wc;
        }       
    }
    
    /** Return a WebConnection to the pool so that it may be reused.
     * @param wc the WebConnection to return.
     */
    public final static void releaseConnection (WebConnection wc) {
        if (!wc.keepalive || keepalivetime<=0)
            try
            {
                wc.close();
            }
            catch  (IOException e) {}
            finally
            {
              return;
            }
        String a = wc.ia.getHostAddress () + ":" + wc.port;
        wc.setReleased ();
	if(trace_keepalive)
	{
	   System.out.println("[TRACE "+Thread.currentThread().getName()+"] * Puting "+a+" to pool.");
	}
        synchronized (activeConnections) {
            Vector pool = (Vector)activeConnections.get (a);
            if (pool == null) {
                pool = new Vector ();
                activeConnections.put (a, pool);
            }
            pool.addElement (wc);
        } 
    }
    
    /** Set the keep alive time for this handler.
     * @param milis the keep alive time in miliseconds.
     */
    public static void setKeepaliveTime (long milis) {
        keepalivetime = milis;
    }
    
    /** Get the current keep alive time.
     * @return the keep alive time in miliseconds.
     */
    public static long getKeepaliveTime () {
        return keepalivetime;
    }
    
    /** The cleaner thread.
     */
    public void run () {
	long d;
	int vsize;
	Object key;
	if(keepalivetime<=0) return;
        while (!isInterrupted()) {
            d=System.currentTimeMillis() - keepalivetime;
            Enumeration e = activeConnections.keys ();
            while (e.hasMoreElements ()) {
		key = e.nextElement();
                Vector v = (Vector)activeConnections.get(key);
                synchronized (v) {
                    vsize = v.size ();
                    for (int i = vsize - 1; i >= 0; i--) {
                        WebConnection wc = (WebConnection)v.elementAt (i);
			if ( wc.releasedAt == 0 )
			{
			    System.out.println("[Internal error] Someone closed a released connection.");
			}
                        if ( wc.releasedAt < d ) {
                            v.removeElementAt (i);
			    if(trace_keepalive)
			       System.out.println("[Pool cleaner] * Closing connection to "+wc+" TTL exceed by "+((d-wc.getReleasedAt())/1000L)+"s.");
                            try
                            {
                               wc.close ();
                            }
                            catch (IOException ignore)
                            {}
                        }
                    }
                }
		if(vsize==0)
		    synchronized(activeConnections)
		    {
			activeConnections.remove(key);
		    }
            }
            try {
                sleep (keepalivetime); 
            } catch (InterruptedException ie) {
                // ignore
            }
        }       
    }

}
