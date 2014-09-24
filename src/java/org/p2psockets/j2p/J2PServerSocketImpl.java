/*
 * J2PServerSocketImpl.java
 *
 * Created on August 27, 2005, 11:00 PM
 *
 */

package org.p2psockets.j2p;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketImpl;
import org.p2psockets.P2PServerSocket;
import org.p2psockets.P2PSocket;
import org.p2psockets.P2PSocketAddress;

/**
 *
 * @author Alex Lynch (jxlynch@users.sf.net)
 */
public class J2PServerSocketImpl extends J2PAbstractSocketImpl{
    
    private final P2PServerSocket server;
    /** Creates a new instance of J2PServerSocketImpl */
    public J2PServerSocketImpl() {
        try{
            server = new P2PServerSocket();
        }catch(IOException ioe){
            throw new RuntimeException(ioe);
        }
    }
    
    protected void accept(SocketImpl socketImpl) throws java.io.IOException {
        J2PClientSocketImpl impl = (J2PClientSocketImpl)socketImpl;
        System.out.println("Server address is "+server.getLocalSocketAddress());
        P2PSocket sock = (P2PSocket)server.accept();
        System.out.println("New socket R "+sock.getRemoteSocketAddress());
        System.out.println("New socket RA "+((P2PSocketAddress)sock.getRemoteSocketAddress()).getAddress());
        System.out.println("New socket L "+sock.getLocalSocketAddress());
        
        impl.resetSocket(sock);
    }
    
    protected int available() throws java.io.IOException {
        throw new IOException("A server socket does not have any available bytes");
    }
    
    protected void bind(java.net.InetAddress inetAddress, int port) throws java.io.IOException {
        server.bind(new P2PSocketAddress(inetAddress, port));
    }
    
    protected void close() throws java.io.IOException {
        server.close();
    }
    
    protected void connect(String str, int param) throws java.io.IOException {
        throw new IOException("A server socket does not connect");
    }
    
    protected void connect(java.net.InetAddress inetAddress, int param) throws java.io.IOException {
        throw new IOException("A server socket does not connect");
    }
    
    protected void connect(java.net.SocketAddress socketAddress, int param) throws java.io.IOException {
        throw new IOException("A server socket does not connect");
    }
    
    
    
    protected java.io.InputStream getInputStream() throws java.io.IOException {
        throw new IOException("A server socket does not have an input stream");
    }
    
    
    
    protected java.io.OutputStream getOutputStream() throws java.io.IOException {
        throw new IOException("A server socket does not have an output stream");
    }
    
    protected void listen(int param) throws java.io.IOException {
        //not implemented
    }
    
    protected void sendUrgentData(int param) throws java.io.IOException {
        throw new IOException("A server socket cannot send urgent data");
    }
    
    protected InetAddress getInetAddress(){
        return server.getInetAddress();
    }
    protected int getPort(){
        return server.getLocalPort();
    }
    
    
}
