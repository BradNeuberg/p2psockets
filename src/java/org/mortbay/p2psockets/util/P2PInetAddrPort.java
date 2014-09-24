// ===========================================================================
// Copyright (c) 1996 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id: P2PInetAddrPort.java,v 1.1 2004/01/15 10:26:57 BradNeuberg Exp $
// ---------------------------------------------------------------------------

package org.mortbay.p2psockets.util;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.p2psockets.P2PInetAddress;

import org.mortbay.util.*;

/* ======================================================================== */
/** InetAddress and Port.
 */
public class P2PInetAddrPort extends InetAddrPort
{
    /* ------------------------------------------------------------ */
    public final static String __0_0_0_0 = "0.0.0.0";

    /* ------------------------------------------------------------ */
    private InetAddress jxtaAddr=null;
    private boolean jxtaAddrIsHost=false;
    private int jxtaPort=0;

    /* ------------------------------------------------------------------- */
    public P2PInetAddrPort()
    {}

    /* ------------------------------------------------------------ */
    /** Constructor for a port on all local host address.
     * @param port 
     */
    public P2PInetAddrPort(int port)
    {
        jxtaPort=port;
    }
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @param addr 
     * @param port 
     */
    public P2PInetAddrPort(InetAddress addr, int port)
    {
        jxtaAddr=convertInetAddress(addr);
        jxtaPort=port;
    }
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @param addr 
     * @param port 
     */
    public P2PInetAddrPort(String host, int port)
        throws java.net.UnknownHostException
    {
        setHost(host);
        setPort(port);
    }
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @param P2PInetAddrPort String of the form "addr:port"
     */
    public P2PInetAddrPort(String inetAddrPort)
        throws java.net.UnknownHostException
    {
        int c = inetAddrPort.indexOf(':');
        if (c>=0)
        {
            String addr=inetAddrPort.substring(0,c);
            if (addr.indexOf('/')>0)
                addr=addr.substring(addr.indexOf('/')+1);
            inetAddrPort=inetAddrPort.substring(c+1);
        
            if (addr.length()>0 && ! __0_0_0_0.equals(addr))
            {
                jxtaAddrIsHost=!Character.isDigit((addr.charAt(0)));   
                this.jxtaAddr=P2PInetAddress.getByAddress(addr, null);
            }
        }
        
        jxtaPort = Integer.parseInt(inetAddrPort); 
    }
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @param inetAddrPort String of the form "addr:port"
     */
    public P2PInetAddrPort(InetAddrPort address)
    {
        if (address!=null)
        {
			InetAddress inetAddress = convertInetAddress(address.getInetAddress());

            jxtaAddr=inetAddress;
            jxtaPort=address.getPort();
        }
    }
    
    /* ------------------------------------------------------------ */
    /** Get the Host.
     * @return The IP address
     */
    public String getHost()
    {
        if (jxtaAddr==null)
            return __0_0_0_0;
        
        return jxtaAddrIsHost?jxtaAddr.getHostName():jxtaAddr.getHostAddress();
    }
    
    /* ------------------------------------------------------------ */
    /** Set the Host.
     * @param host 
     * @exception java.net.UnknownHostException 
     */
    public void setHost(String host)
        throws java.net.UnknownHostException
    {
        jxtaAddr=null;
        // commented out, bkn3@columbia.edu
        /*if (host!=null)
        {
            if (host.indexOf('/')>0)
                host=host.substring(0,host.indexOf('/'));
            jxtaAddrIsHost=!Character.isDigit((host.charAt(0)));
            jxtaAddr=InetAddress.getByAddress(host, null);
        }*/
		jxtaAddr = P2PInetAddress.getByAddress(host, null);
    }
    
    /* ------------------------------------------------------------ */
    /** Get the IP address.
     * @return The IP address
     */
    public InetAddress getInetAddress()
    {
        return jxtaAddr;
    }
    
    /* ------------------------------------------------------------ */
    /** Set the IP address.
     * @param addr The IP address
     */
    public void setInetAddress(InetAddress addr)
    {
        jxtaAddrIsHost=false;
        jxtaAddr=addr;
    }

    /* ------------------------------------------------------------ */
    /** Get the port.
     * @return The port number
     */
    public int getPort()
    {
        return jxtaPort;
    }
    
    /* ------------------------------------------------------------ */
    /** Set the port.
     * @param port The port number
     */
    public void setPort(int port)
    {
        jxtaPort=port;
    }
    
    
    /* ------------------------------------------------------------------- */
    public String toString()
    {
        return getHost()+':'+jxtaPort;
    }

    /* ------------------------------------------------------------ */
    /** Clone the P2PInetAddrPort.
     * @return A new instance.
     */
    public Object clone()
    {
        return new P2PInetAddrPort(this);
    }

    /* ------------------------------------------------------------ */
    /** Hash Code.
     * @return hash Code.
     */
    public int hashCode()
    {
        return jxtaPort+((jxtaAddr==null)?0:jxtaAddr.hashCode());
    }
    
    /* ------------------------------------------------------------ */
    /** Equals.
     * @param o 
     * @return True if is the same address and port.
     */
    public boolean equals(Object o)
    {
        if (o==null)
            return false;
        if (o==this)
            return true;
        if (o instanceof P2PInetAddrPort)
        {
            P2PInetAddrPort addr=(P2PInetAddrPort)o;
            return addr.jxtaPort==jxtaPort &&
                ( addr.jxtaAddr==jxtaAddr ||
                  addr.jxtaAddr!=null && addr.jxtaAddr.equals(jxtaAddr));
        }
        return false;
    }

	/** Converts an InetAddress into a P2PInetAddress. */
	protected InetAddress convertInetAddress(InetAddress addr) {
		if (addr != null) {
			String hostName = addr.getHostName();
			byte address[] = addr.getAddress();

			try {
				addr = P2PInetAddress.getByAddress(hostName, address);
			}
			catch (UnknownHostException e) {
				e.printStackTrace();
			}
		}

		return addr;
	}
}

    




