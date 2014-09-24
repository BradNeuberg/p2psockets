/*
 * J2PAbstractSocketImpl.java
 *
 * Created on August 27, 2005, 11:14 PM
 *
 */

package org.p2psockets.j2p;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketImpl;
import java.util.HashMap;

/**
 *
 * @author Alex Lynch (jxlynch@users.sf.net)
 */
public abstract class J2PAbstractSocketImpl extends SocketImpl{
    
    private final HashMap optionMap;
    /** Creates a new instance of J2PAbstractSocketImpl */
    public J2PAbstractSocketImpl() {
        optionMap = new HashMap();
    }

    public Object getOption(int param) throws java.net.SocketException {
        return optionMap.get(new Integer(param));
    }

    public void setOption(int param, Object obj) throws java.net.SocketException {
        optionMap.put(new Integer(param),obj);
    }
    
    
    protected void create(boolean param) throws java.io.IOException {
        if(!param)
            throw new IOException("Cannot create a non-streaming socket");
    }
}
