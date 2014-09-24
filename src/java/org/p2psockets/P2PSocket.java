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
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImplFactory;
import java.net.UnknownHostException;
import org.apache.log4j.Logger;




/**
 * This class implements client sockets (also called just
 * "sockets"). A socket is an endpoint for communication
 * between two machines.
 *
 * Threading: this class is not threadsafe and should only be used from one
 * thread.  It is unclear if using the socket's input stream in one thread and
 * the output stream in another thread will be threadsafe; it is also unclear
 * if the original java.net.Sockets supports this.
 * 
 * @author Brad GNUberg, bkn3@columbia.edu.
 * @version 0.5
 * 
 * @todo Create JXTA sockets that are similar to UDP-style sockets.
 * @todo We don't use the SocketFactory stuff.
 * @todo Support timeout values for sockets.
 * @todo Try to somehow support the standard 'So' socket options, like SoKeepAlive.
 * @todo Determine if java.net.Sockets support using the socket's input and output streams in
 * different threads.  The close functionality should be made threadsafe, since it is
 * in the original java.net.Sockets.
 * @todo Talk more about the new implementation we are using that doesn't
 * have scoped peer groups, but instead puts everything into the Net Peer
 * Group.
 */
public class P2PSocket extends java.net.Socket {
    private static final Logger log = Logger.getLogger("org.p2psockets.P2PSocket");
	/** The remote end of the socket connection. */
	protected P2PSocketAddress remoteSocketAddress;

	/** The local end of the socket connection. */
	protected P2PSocketAddress localSocketAddress;

	/** The JXTA socket that actually does all the hard work. */
	protected JxtaSocket jxtaSocket;

	protected InputStream socketIn;

	protected OutputStream socketOut;

	/** This is a random number that has no real meaning; we need is for getSoSendBufferSize(),
	  * which has no corresponding JXTA value. */
	protected static final int SEND_BUFFER_SIZE = 5000;

	private boolean socketCreated = false;
    private boolean socketBound = false;
    private boolean socketConnected = false;
    private boolean socketClosed = false;
    private boolean socketShutIn = false;
    private boolean socketShutOut = false;

    /**
     * Creates an unconnected socket.
	 */
    public P2PSocket() {
    }

    /**
     * Creates a stream socket and connects it to the specified port
     * number on the named host.
	 *
     * @param      host   the host name.
     * @param      port   the port number.
     *
     * @exception  UnknownHostException if the IP address of 
     * the host could not be determined.
     *
     * @exception  IOException  if an I/O error occurs when creating the socket.
     */
    public P2PSocket(String host, int port)
		throws UnknownHostException, IOException {
		if (port <= 0)
			throw new IllegalArgumentException("Sockets can not have a port of 0");
                //System.out.println("creating address for "+host+":"+port);
		remoteSocketAddress = new P2PSocketAddress(host, port);
		localSocketAddress = new P2PSocketAddress(P2PInetAddress.getLocalHost(), 0);

		connectSocket();
    }

    /**
     * Creates a stream socket and connects it to the specified port
     * number at the specified IP address.
     * 
     * @param      address   the IP address.
     * @param      port      the port number.
     * @exception  IOException  if an I/O error occurs when creating the socket.
     */
    public P2PSocket(InetAddress address, int port) throws IOException {
		if (port <= 0)
			throw new IllegalArgumentException("Sockets can not have a port of 0");

		remoteSocketAddress = new P2PSocketAddress(address, port);
		localSocketAddress = new P2PSocketAddress(P2PInetAddress.getLocalHost(), 0);

		connectSocket();
    }

    /**
     * Creates a socket and connects it to the specified remote host on
     * the specified remote port. The Socket will also bind() to the local
     * address and port supplied.
	 *
     * @param host the name of the remote host
     * @param port the remote port
     * @param localAddr the local address the socket is socketBound to.  This value
	 * is ignored, since JXTA peers are not multihomed.
     * @param localPort the local port the socket is socketBound to
     * @exception  IOException  if an I/O error occurs when creating the socket.
     */
    public P2PSocket(String host, int port, InetAddress localAddr,
		  int localPort) throws IOException {
		if (port <= 0)
			throw new IllegalArgumentException("Sockets can not have a port of 0");

		remoteSocketAddress = new P2PSocketAddress(host, port);
		localSocketAddress = new P2PSocketAddress(P2PInetAddress.getLocalHost(), localPort);

		connectSocket();
    }

    /**
     * Creates a socket and connects it to the specified remote address on
     * the specified remote port. The Socket will also bind() to the local
     * address and port supplied.
	 *
     * @param address the remote address
     * @param port the remote port
     * @param localAddr the local address the socket is socketBound to.  This value
	 * is ignored, since JXTA peers are not multihomed.
     * @param localPort the local port the socket is socketBound to
     * @exception  IOException  if an I/O error occurs when creating the socket.
     */
    public P2PSocket(InetAddress address, int port, InetAddress localAddr,
							int localPort) throws IOException {
		if (port <= 0)
			throw new IllegalArgumentException("Sockets can not have a port of 0");

		remoteSocketAddress = new P2PSocketAddress(address, port);
		localSocketAddress = new P2PSocketAddress(P2PInetAddress.getLocalHost(), localPort);

		connectSocket();
    }

    /**
     * Creates a stream socket and connects it to the specified port
     * number on the named host.  Non-stream sockets (i.e. UDP sockets)
	 * are not currently supported.
     *
     * @param      host     the host name.
     * @param      port     the port number.
     * @param      stream   a <code>boolean</code> indicating whether this is
     *                      a stream socket or a datagram socket.
     * @exception  IOException  if an I/O error occurs when creating the socket.
     */
    public P2PSocket(String host, int port, boolean stream) throws IOException {
		this(host, port);
    }

    /**
     * Creates a socket and connects it to the specified port number at
     * the specified IP address. Non-stream sockets (i.e. UDP sockets)
	 * are not currently supported.
     *
     * @param      host     the IP address.
     * @param      port      the port number.
     * @param      stream    if <code>true</code>, create a stream socket;
     *                       otherwise, create a datagram socket.
     * @exception  IOException  if an I/O error occurs when creating the socket.
     */
    public P2PSocket(InetAddress address, int port, boolean stream) throws IOException {
		this(address, port);
    }

	/** Used by P2PServerSocket to create the socket returned by the accept() method. */
	protected P2PSocket(JxtaSocket clientJxtaSocket, String remoteHost, int remotePort,
								   InetAddress localAddress, int localPort) throws IOException {
		remoteSocketAddress = new P2PSocketAddress(remoteHost, remotePort);
		localSocketAddress = new P2PSocketAddress(localAddress, localPort);

		jxtaSocket = clientJxtaSocket;		

		socketIn = jxtaSocket.getInputStream();
		socketOut = jxtaSocket.getOutputStream();

		socketConnected = true;
		socketBound = true;
	}
		

    private void connectSocket() throws IOException {
	    bind(localSocketAddress);
            //System.out.println("Connect to "+remoteSocketAddress);
		connect(remoteSocketAddress);
    }

    /**
     * Connects this socket to the server.
     *
     * @param	endpoint the <code>P2PSocketAddress</code>
     * @throws	IOException if an error occurs during the connection
     * @throws  IllegalArgumentException if endpoint is null or is a
     *          SocketAddress subclass not supported by this socket
     */
    public void connect(SocketAddress endpoint) throws IOException {
		connect(endpoint, 0);
    }

    /**
     * Connects this socket to the server with a specified timeout value.
     * A timeout of zero is interpreted as an infinite timeout. The connection
     * will then block until established or an error occurs.
     *
     * @param	endpoint the <code>SocketAddress</code>
     * @param	timeout  the timeout value to be used in milliseconds.
	 * Timeout values are not currently supported by P2P Sockets.
     * @throws	IOException if an error occurs during the connection
     * @throws	SocketTimeoutException if timeout expires before connecting
     * @throws  IllegalArgumentException if endpoint is null or is a
     *          SocketAddress subclass not supported by this socket
     */
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
		if (endpoint == null)
			throw new IllegalArgumentException("The remote address can not be null");

		if (isClosed())
			throw new SocketException("The socket is closed");

		if (isConnected())
			throw new SocketException("The socket is already connected");

		if (!(endpoint instanceof P2PSocketAddress))
			throw new IllegalArgumentException("Unsupported address type; only P2PSocketAddress is supported");

		remoteSocketAddress = (P2PSocketAddress) endpoint;

		jxtaSocket = P2PNameService.resolve(remoteSocketAddress, P2PNetwork.getPeerGroup());		
		socketIn = jxtaSocket.getInputStream();
		socketOut = jxtaSocket.getOutputStream();
                
                socketClosed = false; //jxl modification
		socketConnected = true;
		socketBound = true;
                
                //start jxl modification
                ObjectOutputStream oos = new ObjectOutputStream(socketOut);
                oos.writeObject(localSocketAddress);
                oos.flush();
                //don't close oos, or we'll close the socket out
                //end jxl modifiacation
    }

    /**
     * Binds the socket to a local address.
     * <P>
     * If the address is <code>null</code>, then the system will pick up
     * an ephemeral port and a valid local address to bind the socket.
     *
     * @param	bindpoint the <code>SocketAddress</code> to bind to
     * @throws	IOException if the bind operation fails, or if the socket
     *			   is already bound.
     * @throws  IllegalArgumentException if bindpoint is a
     *          SocketAddress subclass not supported by this socket
     */
    public void bind(SocketAddress bindpoint) throws IOException {
		if (isClosed())
			throw new SocketException("The socket is closed");

		if (isBound())
			throw new SocketException("The socket is already bound");

		if (bindpoint != null && (!(bindpoint instanceof P2PSocketAddress)))
			throw new IllegalArgumentException("Unsupported address type; must use P2PSocketAddress");

		if (bindpoint == null)
			localSocketAddress = new P2PSocketAddress(P2PInetAddress.getLocalHost(), 0);
		else
			localSocketAddress = (P2PSocketAddress)bindpoint;

		socketBound = true;
    }

    /**
     * Returns the address to which the socket is connected.
     *
     * @return  the remote IP address to which this socket is connected,
     *		or <code>null</code> if the socket is not connected.
     */
    public InetAddress getInetAddress() {
		if (!isConnected())
			return null;

		return remoteSocketAddress.getAddress();
    }

    /**
     * Gets the local address to which the socket is bound.
     *
     * @return the local address to which the socket is bound or 
     *	       <code>InetAddress.anyLocalAddress()</code>
     *	       if the socket is not bound yet.
     */
    public InetAddress getLocalAddress() {
		if (!isBound()) {
			try {
				return P2PInetAddress.anyLocalAddress();
			}
			catch (P2PInetAddressException e) {
				// we shouldn't get an exception here unless there is an internal programming error
				log.error("Exception retrieving local address", e);
			}
		}

		return localSocketAddress.getAddress();
    }

    /**
     * Returns the remote port to which this socket is connected.
     *
     * @return  the remote port number to which this socket is connected, or
     *	        0 if the socket is not connected yet.
     */
    public int getPort() {
		if (!isConnected())
			return 0;

		return remoteSocketAddress.getPort();
    }

    /**
     * Returns the local port to which this socket is bound.
     *
     * @return  the local port number to which this socket is bound or -1
     *	        if the socket is not bound yet.
     */
    public int getLocalPort() {
		if (!isBound())
			return -1;

		return localSocketAddress.getPort();
    }

    /**
     * Returns the address of the endpoint this socket is connected to, or
     * <code>null</code> if it is unconnected.
     * @return a <code>SocketAddress</code> reprensenting the remote endpoint of this
     *	       socket, or <code>null</code> if it is not connected yet.
     */
    public SocketAddress getRemoteSocketAddress() {
		if (!isConnected())
			return null;

		return remoteSocketAddress;
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

		return localSocketAddress;
    }

    /**
     * Returns an input stream for this socket.
     *
     * @return     an input stream for reading bytes from this socket.
     * @exception  IOException  if an I/O error occurs when creating the
     *             input stream, the socket is closed, the socket is
     *             not socketConnected, or the socket input has been shutdown
     *             using {@link #shutdownInput()}
     */
    public InputStream getInputStream() throws IOException {
		if (isClosed())
			throw new SocketException("The socket is closed");

		if (!isConnected())
			throw new SocketException("The socket is not connected");

		if (isInputShutdown())
			throw new SocketException("The socket input is shutdown");

		return socketIn;
    }

    /**
     * Returns an output stream for this socket.
     *
     * @return     an output stream for writing bytes to this socket.
     * @exception  IOException  if an I/O error occurs when creating the
     *               output stream or if the socket is not connected.
     */
    public OutputStream getOutputStream() throws IOException {
		if (isClosed())
			throw new SocketException("The socket is closed");

		if (!isConnected())
			throw new SocketException("The socket is not connected");

		if (isOutputShutdown())
			throw new SocketException("The socket output is shutdown");

		return socketOut;
    }

    public void setTcpNoDelay(boolean on) throws SocketException {
    }

    public boolean getTcpNoDelay() throws SocketException {
		return false;
    }

    public void setSoLinger(boolean on, int linger) throws SocketException {
    }

    public int getSoLinger() throws SocketException {
		return -1;
    }

    public void sendUrgentData (int data) throws IOException  {
		throw new SocketException ("Urgent data not supported");
    }

    public void setOOBInline(boolean on) throws SocketException {
    }

    public boolean getOOBInline() throws SocketException {
		return false;
    }

    public void setSoTimeout(int timeout) throws SocketException {
		if (isClosed())
			throw new SocketException("The socket is closed");

		if (timeout < 0)
		  throw new IllegalArgumentException("Timeout value can not be negative");

        if (isBound())
            return;
        
		jxtaSocket.setSoTimeout(timeout);
    }

    public int getSoTimeout() throws SocketException {
		try {
			return jxtaSocket.getSoTimeout();
		}
		catch (IOException e) {
			log.error("Exception retrieving SoTimeout",e);
			throw new SocketException(e.toString());
		}
    }

    public void setSendBufferSize(int size)
		throws SocketException {	
    }

    public int getSendBufferSize() throws SocketException {
		return SEND_BUFFER_SIZE;
    }

    public void setReceiveBufferSize(int size)
    throws SocketException{
    }

    public int getReceiveBufferSize()
    throws SocketException{
		return SEND_BUFFER_SIZE;
    }

    public void setKeepAlive(boolean on) throws SocketException {
    }

    public boolean getKeepAlive() throws SocketException {
		return false;
    }

    public void setTrafficClass(int tc) throws SocketException {
    }

    public int getTrafficClass() throws SocketException {
        return 0;
    }

    public void setReuseAddress(boolean on) throws SocketException {
    }

    public boolean getReuseAddress() throws SocketException {
		return false;
    }

    /**
     * Closes this socket.
     * <p>
     * Any thread currently blocked in an I/O operation upon this socket
     * will throw a {@link SocketException}.
     * <p>
     * Once a socket has been closed, it is not available for further networking
     * use (i.e. can't be reconnected or rebound). A new socket needs to be
     * created.
     *
     * @exception  IOException  if an I/O error occurs when closing this socket.
     */
    public void close() throws IOException {
    		if (isClosed())
			return;

		if (socketCreated)
			jxtaSocket.close();

	    socketClosed = true;
    }

    /**
     * Places the input stream for this socket at "end of stream".
     * Any data sent to the input stream side of the socket is acknowledged
     * and then silently discarded.
     * <p>
     * If you read from a socket input stream after invoking 
     * shutdownInput() on the socket, the stream will return EOF.
     *
     * @exception IOException if an I/O error occurs when shutting down this
     * socket.
     */
    public void shutdownInput() throws IOException
    {
		if (isClosed())
			throw new SocketException("The socket is closed");

		if (!isConnected())
			throw new SocketException("The socket is not connected");

		if (isInputShutdown())
			throw new SocketException("The socket input is already shutdown");

		socketIn.close();

		socketShutIn = true;
    }
    
    /**
     * Disables the output stream for this socket.
     * For a TCP socket, any previously written data will be sent
     * followed by TCP's normal connection termination sequence.
     *
     * If you write to a socket output stream after invoking 
     * shutdownOutput() on the socket, the stream will throw 
     * an IOException.
     *
     * @exception IOException if an I/O error occurs when shutting down this
     * socket.
     */
    public void shutdownOutput() throws IOException
    {
		if (isClosed())
			throw new SocketException("The socket is closed");

		if (!isConnected())
			throw new SocketException("The socket is not connected");

		if (isOutputShutdown())
			throw new SocketException("The socket output is already shutdown");

		socketOut.flush();
		socketOut.close();

		socketShutOut = true;
    }

    public boolean isConnected() { return socketConnected; }

	public boolean isBound() { return socketBound; }

	public boolean isClosed() { return socketClosed; }

	public boolean isInputShutdown() { return socketShutIn; }

	public boolean isOutputShutdown() { return socketShutOut; }

    /**
     * Converts this socket to a <code>String</code>.
     *
     * @return  a string representation of this socket.
     */
    public String toString() {
	    StringBuffer results = new StringBuffer();

		if (isConnected())
			results.append("Socket: connected");
		else
			results.append("Socket");

        String remoteSocketAddressStr = null, localSocketAddressStr = null;
        if (remoteSocketAddress != null)
            remoteSocketAddressStr = remoteSocketAddress.toString();
        
        if (localSocketAddress != null)
            localSocketAddressStr = localSocketAddress.toString();
        
		results.append("[remoteSocketAddress="+remoteSocketAddressStr+
					   ", localSocketAddress="+localSocketAddressStr+"]");
		
		return results.toString();
    }

    public static void setSocketImplFactory(SocketImplFactory fac)
		throws IOException {
    }
    
     //jxl modification start
    //I'm adding equals() and hashCode() these are 
    //based on the remoteSocketAddress
    public int hashCode(){
        return this.remoteSocketAddress.hashCode();
    }
    public boolean equals(Object o){
        if(this.getClass().isInstance(o)){
            return hashCode() == o.hashCode();
        }//else
        return false;
    }
    //jxl modification end
}
