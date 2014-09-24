/*
 * J2PSocketImplFactory_Old.java
 *
 * Created on August 27, 2005, 8:39 PM
 *
 */

package org.p2psockets.j2p;

import java.lang.reflect.Constructor;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketImpl;
import java.net.SocketImplFactory;
import org.p2psockets.P2PNetwork;

/**
 *
 * @author Alex Lynch (jxlynch@users.sf.net)
 */
public class J2PSocketImplFactory implements SocketImplFactory{
    
    public static void install(String hostName, String networkName) throws Exception{
        if(networkName == null)
            networkName = "j2proxy";
        
        Socket.setSocketImplFactory(new J2PSocketImplFactory(false));
        ServerSocket.setSocketFactory(new J2PSocketImplFactory(true));
        
        if(hostName != null){
            P2PNetwork.autoSignin(hostName,networkName);
        }else{
            P2PNetwork.autoSignin(networkName);
        }
        
    }
    
    private final Constructor ctor;
    private final boolean server;
    /**
     * Creates a new instance of J2PSocketImplFactory_Old
     */
    private J2PSocketImplFactory(boolean server) {
        this.server = server;
        try{
            Class c = Class.forName("java.net.PlainSocketImpl");
            ctor = c.getDeclaredConstructor(new Class[0]);
            ctor.setAccessible(true);
        }catch(Exception e){
            throw new RuntimeException(e);
        }
        
    }
    
    public java.net.SocketImpl createSocketImpl() {
        if(!useRealSocket()){
            //new RuntimeException().printStackTrace();
            //System.out.println("\n\n\n");
            if(server)
                return new J2PServerSocketImpl();
            else
                return new J2PClientSocketImpl();
        }else{
            try{
                return (SocketImpl)ctor.newInstance(new Object[0]);
            } catch(Exception e){
                throw new RuntimeException(e);
            }
        }
    }
    
    private boolean useRealSocket(){
        
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        for(int i = 0;i<trace.length;i++){
            StackTraceElement elem =trace[i];
            if(isJxtaOrP2psClass(elem.getClassName()))
                return true;
        }
        return false;
    }
    
    
    private boolean isJxtaOrP2psClass(String className){
        boolean ret = 
                className.startsWith("net.jxta.impl.")|| 
                (className.startsWith("org.p2psockets.") && !className.startsWith("org.p2psockets.j2p") );
	
	//if(ret)
	 //   System.out.println("Real socket for "+className);
	
	return ret;
        
    }
    
    
    
}
