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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImplFactory;
import org.apache.log4j.Logger;



/**
 * This class implements server sockets. A server socket waits for
 * requests to come in over the network. It performs some operation
 * based on that request, and then possibly returns a result to the requester.
 *
 * Instead of being attached to a particular peer group ServerSockets can also
 * be attached to a single specific peer.  The name's of these peer ServerSockets
 * are generated from the peer name, such as "BradGNUberg", and always have the ending
 * ".peer", resulting in "BradGNUberg.peer".
 *
 * To "start" a web-site named "www.nike.laborpolicy" on "P2P port" 80, you
 * would use the following:
 *
 * ServerSocket myServer = new P2PServerSocket("www.nike.laborpolicy", 80);
 * Socket client = myServer.accept();
 * InputStream in = client.getInputStream();
 * OutputStream out = client.getOutputStream();
 * // do something
 * client.close();
 *
 * Note that you must call P2PNetwork.signin(username, password) before using the Sockets and
 * ServerSockets.  You must also call P2PNetwork.setApplicationName() to the
 * peer name you want all domain requests to be scoped to.
 *
 * Threading: This class should only be used within one thread.  However, the Socket that
 * is returned from the accept() method can safely be passed to another thread if it is
 * only used within that thread.
 *
 * @author Brad GNUberg, bkn3@columbia.edu
 * @version 0.5
 * @todo NIO channels are not supported.  Custom sockets using the SocketImplFactory is also not supported
 *		 or used.  Security checks are not made.
 * @todo Talk more about the new implementation we are using that doesn't
 * have scoped peer groups, but instead puts everything into the Net Peer
 * Group.
 */
public class P2PServerSocket extends java.net.ServerSocket {
    private static final Logger log = Logger.getLogger("org.p2psockets.P2PServerSocket");
    //jxl modification : remove ANONYMOUS_PEER_NAME
    
    /** This is a random number that has no real meaning; we need is for getSoReceiveBufferSize(),
     * which has no corresponding Jxta value. */
    protected static final int RECEIVE_BUFFER_SIZE = 5000;
    
    /** The default timeout for how long to wait for clients.  We wait for infinity (i.e. 0). */
    protected static final int defaultSoTimeout = 0;
    
    /** The JxtaServerSocket that we wrap the does all the hard work. */
    protected JxtaServerSocket jxtaServerSocket;
    
    protected P2PSocketAddress socketAddress;
    
    private boolean serverSocketCreated = false;
    private boolean serverSocketBound = false;
    private boolean serverSocketClosed = false;
    
    /**
     * Creates an unbound server socket.
     *
     * @exception IOException IO error when opening the socket.
     */
    public P2PServerSocket() throws IOException {
    }
    
    /**
     * Creates a server socket, bound to the specified port. A port of
     * <code>0</code> creates a socket on any free port.
     *
     * @param      port  the port number, or <code>0</code> to use any
     *                   free port.
     *
     * @exception  IOException  if an I/O error occurs when opening the socket.
     */
    public P2PServerSocket(int port) throws IOException {
        this(port, 0, null);
    }
    
    /**
     * Creates a server socket and binds it to the specified local port
     * number, with the specified backlog.
     * A port number of <code>0</code> creates a socket on any
     * free port.
     *
     * @param      port     the specified port, or <code>0</code> to use
     *                      any free port.
     * @param      backlog  the maximum length of the queue.  This parameter
     *						is not used and is ignored.
     *
     * @exception  IOException  if an I/O error occurs when opening the socket.
     */
    public P2PServerSocket(int port, int backlog) throws IOException {
        this(port, backlog, null);
    }
    
    /**
     * Creates a server socket and binds it to the specified port
     * number and host name
     */
    public P2PServerSocket(String host, int port) throws IOException {
        this(port, 0, P2PInetAddress.getByAddress(host, null));
    }
    
    /**
     * Create a server with the specified port, listen backlog, and
     * local IP address to bind to.
     *
     * The port must be between 0 and 65535, inclusive.
     *
     * @param port the local TCP port
     * @param backlog the listen backlog.  This parameter
     * is not used and is ignored.
     * @param bindAddr the InetAddress the server will bind to.
     *
     * @throws  IOException if an I/O error occurs when opening the socket.
     */
    public P2PServerSocket(int port, int backlog, InetAddress bindAddr) throws IOException {
        super.setSoTimeout(defaultSoTimeout);
        backlog = 0;
        
        if (bindAddr == null)
            socketAddress = new P2PSocketAddress(port);
        else
            socketAddress = new P2PSocketAddress(bindAddr, port);
        
        bind(socketAddress);
    }
    
    /**
     *
     * Binds the <code>ServerSocket</code> to a specific address
     * (IP address and port number).
     * <p>
     * If the address is <code>null</code>, then the system will pick up
     * an ephemeral port and a valid local address to bind the socket.
     * <p>
     * @param	endpoint	The IP address & port number to bind to.
     *
     * @throws	IOException if the bind operation fails, or if the socket
     *			   is already bound.
     * @throws  IllegalArgumentException if endpoint is a
     *          SocketAddress subclass not supported by this socket
     */
    public void bind(SocketAddress endpoint) throws IOException {
        if (isClosed())
            throw new SocketException("The socket is closed");
        
        if (isBound())
            throw new SocketException("The server socket is already bound");
        
        if (endpoint != null && (endpoint instanceof P2PSocketAddress) == false) {
            throw new IllegalArgumentException("endpoint must be a P2PSocketAddress");
        }
        
        if (endpoint == null) {
            socketAddress = new P2PSocketAddress(0);
        } else {
            socketAddress = (P2PSocketAddress)endpoint;
        }
        
        jxtaServerSocket = P2PNameService.bind(socketAddress, P2PNetwork.getPeerGroup());
        jxtaServerSocket.setSoTimeout(defaultSoTimeout);
        
        serverSocketBound = true;
        serverSocketCreated = true;
    }
    
    /**
     *
     * Binds the <code>ServerSocket</code> to a specific address
     * (IP address and port number).
     * <p>
     * If the address is <code>null</code>, then the system will pick up
     * an ephemeral port and a valid local address to bind the socket.
     *
     * @param	endpoint	The IP address & port number to bind to.
     * @param	backlog		The listen backlog length.  This parameter
     *						is currently ignored.
     * @throws	IOException if the bind operation fails, or if the socket
     *			   is already bound.
     * @throws  IllegalArgumentException if endpoint is a
     *          SocketAddress subclass not supported by this socket
     */
    public void bind(SocketAddress endpoint, int backlog) throws IOException {
        bind(endpoint);
    }
    
    /**
     * Returns the local address of this server socket.
     *
     * @return  the address to which this socket is bound,
     *          or <code>null</code> if the socket is unbound.
     */
    public InetAddress getInetAddress() {
        if (!isBound())
            return null;
        
        return socketAddress.getAddress();
    }
    
    /**
     * Returns the port on which this socket is listening.
     *
     * @return  the port number to which this socket is listening or
     *	        -1 if the socket is not bound yet.
     */
    public int getLocalPort() {
        if (!isBound())
            return -1;
        
        return socketAddress.getPort();
    }
    
    /**
     * Returns the address of the endpoint this socket is bound to, or
     * <code>null</code> if it is not bound yet.
     *
     * @return a <code>SocketAddress</code> representing the local endpoint of this
     *	       socket, or <code>null</code> if it is not bound yet.
     */
    
    public SocketAddress getLocalSocketAddress() {
        if (!isBound())
            return null;
        
        return socketAddress;
    }
    
    /**
     * Listens for a connection to be made to this socket and accepts
     * it. The method blocks until a connection is made.
     *
     * @exception  IOException  if an I/O error occurs when waiting for a
     *               connection.
     * @exception  SocketTimeoutException if a timeout was previously set with setSoTimeout and
     *             the timeout has been reached.
     *
     * @return the new Socket
     */
    public Socket accept() throws IOException {
        if (isClosed())
            throw new SocketException("The server socket is closed");
        
        if (!isBound())
            throw new SocketException("The server socket is not bound yet");
        
        try {
            JxtaSocket clientJxtaSocket = (JxtaSocket)jxtaServerSocket.accept();
            
            //start jxl modification
            ObjectInputStream ois =
                    new ObjectInputStream(clientJxtaSocket.getInputStream());
            
            P2PSocketAddress remoteSocketAddress = (P2PSocketAddress)ois.readObject();
            //dont close ois or we'll close the socket
            //end jxl modificatoin
	    System.out.println("ACCEPT: Remote address "+remoteSocketAddress);
            String remoteHost = remoteSocketAddress.getAddress().getHostName();
            int remotePort = remoteSocketAddress.getPort();
            
	    System.out.println("ACCEPT: this.socketAddress "+socketAddress);
            InetAddress localAddress = socketAddress.getAddress();
            int localPort = socketAddress.getPort();
            
            P2PSocket clientSocket = new P2PSocket(clientJxtaSocket, remoteHost, remotePort,
                    localAddress, localPort);
            return clientSocket;
        } catch (Exception e) {
            log.error("Exception accepting incoming socket",e);
            throw new IOException(e.toString());
        }
    }
    
    /**
     * Closes this socket.
     *
     * @exception  IOException  if an I/O error occurs when closing the socket.
     */
    public void close() throws IOException {
        if (isClosed())
            return;
        serverSocketClosed = true;
    }
    
    /**
     * Enable/disable SO_TIMEOUT with the specified timeout, in
     * milliseconds.  With this option set to a non-zero timeout,
     * a call to accept() for this ServerSocket
     * will block for only this amount of time.  If the timeout expires,
     * a <B>java.net.SocketTimeoutException</B> is raised, though the
     * ServerSocket is still valid.  The option <B>must</B> be enabled
     * prior to entering the blocking operation to have effect.  The
     * timeout must be > 0.
     * A timeout of zero is interpreted as an infinite timeout.
     * @param timeout the specified timeout, in milliseconds
     * @exception SocketException if there is an error in
     * the underlying protocol, such as a TCP error.
     * @since   JDK1.1
     * @see #getSoTimeout()
     */
    public void setSoTimeout(int timeout) throws SocketException {
        if (isClosed())
            throw new SocketException("The server socket is closed");
        
        if (timeout < 0) {
            timeout = 0;
        }
        jxtaServerSocket.setSoTimeout(timeout);
    }
    
    /**
     * Retrive setting for SO_TIMEOUT.  0 returns implies that the
     * option is disabled (i.e., timeout of infinity).
     * @return the SO_TIMEOUT value
     * @exception IOException if an I/O error occurs
     * @since   JDK1.1
     * @see #setSoTimeout(int)
     */
    public int getSoTimeout() throws IOException {
        if (isClosed())
            throw new SocketException("The server socket is closed");
        
        return jxtaServerSocket.getSoTimeout();
    }
    
    public void setReuseAddress(boolean on) throws SocketException {
    }
    
    public boolean getReuseAddress() throws SocketException {
        return false;
    }
    
    /**
     * Returns the implementation address and implementation port of
     * this socket as a <code>String</code>.
     *
     * @return  a string representation of this socket.
     */
    public String toString() {
        if (!isBound())
            return "P2PServerSocket[unbound]";
        
        return "P2PServerSocket[socketAddress="+socketAddress.toString()+"]";
    }
    
    public static void setSocketFactory(SocketImplFactory fac) throws IOException {
    }
    
    public void setReceiveBufferSize(int size) throws SocketException {
    }
    
    public int getReceiveBufferSize()
    throws SocketException {
        return RECEIVE_BUFFER_SIZE;
    }
    
    public boolean isBound() { return serverSocketBound; }
    
    public boolean isClosed() { return serverSocketClosed; }
    
    
    //jxl modification start
    //I'm adding equals() and hashCode() these are
    //based on the socketAddress
    public int hashCode(){
        return this.socketAddress.hashCode();
    }
    public boolean equals(Object o){
        if(this.getClass().isInstance(o)){
            return hashCode() == o.hashCode();
        }//else
        return false;
    }
    //jxl modification end
    
}