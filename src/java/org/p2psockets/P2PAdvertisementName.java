package org.p2psockets;

import java.util.*;

/**
 * This class's responsibility is to encapsulate how we build up our
 * advertisement names when we publish a JXTA server socket or look for one.
 *
 * Our current way of building up these names are as follows:
 * 
 * appName/IPAddress,hostName:port
 * 
 * where appName is the Application Name for the application, set in
 * P2PNetwork.setApplicationName.
 * 
 * For example:
 * Paper Airplane/33.66.23.88,www.nike.laborpolicy:80
 * 
 * It also has the responsibility of building up how we wildcard
 * advertisement names.  If toString is passed the value true, such
 * as toString(true), then we insert wildcards into the search string.
 * 
 * The Sun implementation of JXTA supports wildcards in value names when
 * searching for advertisements; unfortunately, it does not support
 * double wildcards.  This discrepancy means that the wildcard design is a
 * bit more complex.  There are basicly five different kinds of searches
 * that are needed, broken down by methods located in P2PNameService:
 * 
 * findIPAddress - For this method, we simply generate the associated IP address
 * algorithmically from the host name rather than search on the network.
 * 
 * findHostName - For this method, we use the search string 
 * "appName/ipAddress/*".
 * 
 * isHostNameTaken - For this method, we algorithmically generate
 * the IP address from the host name and use the following search string:
 * "appName/ipAddress/hostName:*".
 * 
 * isPortTaken -For this method, we algorithmically generate the IP address
 * from the host name and use the following search string:
 * "appName/ipAddress,hostName:*".
 * 
 * resolve - Two possibilities exist here.  Either we have a host name or
 * we only have an ip address.  If we only have an IP address, then we use
 * the following search string: "appName/ipAddress/*".  If we have a host
 * name then we algorithmically generate the IP address and use the search
 * string "appName/ipAddress,hostName:port".
 * 
 * @author BradNeuberg
 */
class P2PAdvertisementName {
    protected String hostName;
    protected int port;
    protected String ipAddress;
    
    /** Creates a new P2PAdvertisementName.  Any of these values can be
     *  null, while port can be -1 to indicate that no port is set.
     */
    protected P2PAdvertisementName(String hostName, String ipAddress,
                                   int port) {
        setHostName(hostName);
        setIPAddress(ipAddress);
        setPort(port);  
    }
    
    /** Creates a new P2PAdvertisementName from the given P2PSocketAddress.
     */
    protected P2PAdvertisementName(P2PSocketAddress addr) {
        this.hostName = addr.getHostName();
        this.port = addr.getPort();
        if (addr.getAddress() != null)
            this.ipAddress = addr.getAddress().getHostAddress();
    }
    
    /** Creates a P2PAdvertisementName using a string formatted as follows:
     *  appName/hostName/IPAddress:port
     *  FIXME: Should we allow people to pass in addresses that might have
     *  wildcards or incomplete values?
     */
    protected P2PAdvertisementName(String advName) {
        
        StringTokenizer tk = new StringTokenizer(advName, "/,:", false);
        
        if (tk.countTokens() != 4) {
            String message = "This constructor must have a string formatted "
                            + "as follows:\n"
                            + "appName/hostName/IPAddress/port";
            throw new RuntimeException(message);
        }
        
        tk.nextToken(); // application name
        
        setIPAddress(tk.nextToken().trim());
        setHostName(tk.nextToken().trim());
        setPort(tk.nextToken().trim()); 
    }
    
    public String getHostName() { return hostName; }
    public String getIPAddress() { return ipAddress; }
    public int getPort() { return port; }
    
     /** This method generates a name that can be used in a JXTA advertisement
     *  to advertise the existence of this address.  It is built up as follows:
     *  
     *  appName/IPAddress/hostName:port
     * 
     *  For example:
     *  Paper Airplane/33.66.23.88/www.nike.laborpolicy:80
     */
    public String toString() {
        try {
            return toString(false);
        }
        catch (P2PInetAddressException e) {
            // Should only happen due to programmer error
            throw new IllegalArgumentException(e.toString());
        }
    }
    
    /** A version of toString that turns null or -1 values into wildcard
     *  characters.
     *  @param useWildcards If true, nulls and minus ones for port values
     *  are changed into a wildcard character; if false then they are not
     *  and just become blank spaces.
     */
    public String toString(boolean useWildcards) throws P2PInetAddressException {
        StringBuffer results = new StringBuffer();

        String appName = P2PNetwork.getApplicationName();
        String hostName = getHostName();
        String ipAddress = getIPAddress();
        int port = getPort();
        
        if (useWildcards)
            return toWildcardString(appName, hostName, ipAddress, port);
        
        if (appName != null)
            results.append(appName);
        else
            results.append(" ");
        
        results.append("/");
        
        if (ipAddress != null)
            results.append(ipAddress);
        else
            results.append(" ");
        
        results.append(",");
        
        if (hostName != null)
            results.append(hostName);
        else
            results.append(" ");
        
        results.append(":");
        
        if (port != -1)
            results.append(port);
        else
            results.append(" ");

        return results.toString();
    }
    
    private String toWildcardString(String appName, 
                                    String hostName, 
                                    String ipAddress, 
                                    int port) throws P2PInetAddressException {
        StringBuffer results = new StringBuffer();
        results.append(appName);
        results.append("/");
        
       if (ipAddress != null && hostName == null) {
           // FIXME: Will this cause a problem if we have a port?
           results.append(ipAddress);
           results.append(",*");
           
           return results.toString();
       }
       else if (ipAddress == null && hostName != null) {
           ipAddress = P2PInetAddress.getByAddress(hostName, null).getHostAddress();
       }
       
       results.append(ipAddress);
       results.append(",");
       
       results.append(hostName);
       results.append(":");
   
       if (port != -1)
           results.append(port);
       else
           results.append("*");
       
       return results.toString();
    }
    
    private void setHostName(String hostName) {
        if (hostName == null || hostName.trim().equals("") 
            || hostName.trim().equals("*"))
            this.hostName = null;
        else
            this.hostName = hostName;
    }
    
    private void setIPAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.trim().equals("") 
            || ipAddress.trim().equals("*"))
            this.ipAddress = null;
        else
            this.ipAddress = ipAddress;
    }
    
    private void setPort(String portString) {
        if (portString != null && portString.trim().equals("") == false 
            && portString.trim().equals("*") == false) {
            this.port = Integer.parseInt(portString);
        }
        else {
            this.port = -1;
        }
    }
    
    private void setPort(int port) {
        this.port = port;
    }
}
