/*
 * J2PClientSocketImpl.java
 *
 * Created on August 27, 2005, 11:00 PM
 *
 */

package org.p2psockets.j2p;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.net.SocketOptions;
import org.p2psockets.P2PInetAddress;
import org.p2psockets.P2PInetAddressException;
import org.p2psockets.P2PNameService;
import org.p2psockets.P2PSocket;
import org.p2psockets.P2PSocketAddress;

/**
 *
 * @author Alex Lynch (jxlynch@users.sf.net)
 */
public class J2PClientSocketImpl extends J2PAbstractSocketImpl{
    
    private P2PSocket socket = null;
    private P2PSocketAddress localSocketAddress = null;
    /** Creates a new instance of J2PClientSocketImpl */
    public J2PClientSocketImpl(){
	socket = new P2PSocket();
	
    }
    
    protected void resetSocket(P2PSocket sock){
	this.socket = sock;
	System.out.println("reseting impl");
	P2PSocketAddress adr = (P2PSocketAddress)sock.getRemoteSocketAddress();
	System.out.println("this.address = "+adr);
	this.address = adr.getAddress();
	this.port = adr.getPort();
    }
    
    protected void accept(SocketImpl socketImpl) throws java.io.IOException {
	throw new IOException("A client socket can't accept!");
    }
    
    protected int available() throws java.io.IOException {
	return socket.getInputStream().available();
    }
    
    protected void bind(java.net.InetAddress inetAddress, int port) throws java.io.IOException {
	System.out.println("Bind to : "+inetAddress+"-"+port);
	try{
	    localSocketAddress = new P2PSocketAddress(inetAddress, port);
	    System.out.println("Really bind to "+localSocketAddress);
	    setOption(SocketOptions.SO_BINDADDR, localSocketAddress.getAddress());
	    this.localport = localSocketAddress.getPort();
	    socket.bind(localSocketAddress);
	}catch(Exception iae){
	    throw new RuntimeException("Exception getting vertual local address :"+iae.getMessage());
	}
    }
    
    protected void close() throws java.io.IOException {
	socket.close();
    }
    
    protected void connect(String str, int port) throws java.io.IOException {
	connect(new P2PSocketAddress(str,port),0);
    }
    
    protected void connect(java.net.InetAddress inetAddress, int port) throws java.io.IOException {
	connect(new P2PSocketAddress(inetAddress,port),0);
    }
    
    protected void connect(java.net.SocketAddress socketAddress, int timeout) throws java.io.IOException {
	System.out.println("CONNECT to : "+socketAddress);
	P2PSocketAddress paddress = null;
	if(InetSocketAddress.class.isInstance(socketAddress)){
	    InetSocketAddress isa = (InetSocketAddress)socketAddress;
	    String hostName = isa.getHostName();
	    if(!hostName.startsWith(P2PNameService.PEER_SERVICE_PREFIX))
		hostName = P2PNameService.PEER_SERVICE_PREFIX+hostName;
	    if(!hostName.endsWith(P2PNameService.PEER_SERVICE_SUFFIX))
		hostName = hostName+P2PNameService.PEER_SERVICE_SUFFIX;
	    
	    paddress = new P2PSocketAddress(hostName, isa.getPort());
	}else if(P2PSocketAddress.class.isInstance(socketAddress)){
	    paddress = (P2PSocketAddress)socketAddress;
	    
	}else{
	    throw new IOException("Cannot handle address of type "+socketAddress.getClass()+" : "+socketAddress);
	}
	socket.connect(paddress, timeout);
    }
    
    
    
    protected java.io.InputStream getInputStream() throws java.io.IOException {
	return socket.getInputStream();
    }
    
    
    protected java.io.OutputStream getOutputStream() throws java.io.IOException {
	return socket.getOutputStream();
    }
    
    protected void listen(int param) throws java.io.IOException {
	throw new IOException("Client socket cannot listen");
    }
    
    protected void sendUrgentData(int param) throws java.io.IOException {
	OutputStream os = socket.getOutputStream();
	os.write(param);
	os.flush();
    }
    
    protected InetAddress getInetAddress(){
	return socket.getInetAddress();
    }
    protected int getPort(){
	return socket.getPort();
    }
}
