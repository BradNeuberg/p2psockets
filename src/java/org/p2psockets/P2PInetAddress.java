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

import java.net.*;
import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import org.apache.log4j.Logger;


/**
 * This class can store and represent hostnames and
 * IP addresses.
 *
 * Peer and PeerGroup IP addresses are in IPv4 format (i.e. four bytes).
 * We generate this value by cycling over each of the bytes
 * in the hostName string.  At each stop, we add the byte
 * value to the byte value in the 4 byte IP array, also cycling
 * through the 4 byte IP array.
 *
 * None of the bytes can have the values 0, 127, or 255.  To protect
 * against this, we check to see if the generated IP address has the
 * bytes 0, 127, or 255.  If any byte is 0 or 127, we add one to it;
 * if any byte is 255, then we subtract one from it.
 * Todo: We also check to make sure that the address generated is not
 * a multicast address (i.e. the first four bits are 1110).
 *
 * Threading: this object is considered threadsafe because its
 * values are immutable after creation and can't be changed later.  We
 * also mediate access to the class-level variable 'localHostAddress'
 * through synchronized getter/setters to lock if someone is changing
 * this variable.
 *
 * Current Limitations:
 *
 * If an IP address is passed in and no host name is provided,
 * and if a search on the P2P network doesn't resolve the IP address
 * into a domain name, then the host name is automatically set to the
 * IP address "stringified".  Calling getHostName() on the InetAddress
 * instance in the future will not cause the IP address to resolve again,
 * even if that IP address is now bound to a host name.
 *
 * Implementation Notes:
 *
 * Earlier versions of P2PInetAddressOld (1.0 beta 1 release) had to be in
 * the java.net package, in order to subclass java.net.InetAddress which
 * has a package-private constructor.  Because of this, when running any
 * P2P Sockets application, users/programmers had to modify the bootclasspath
 * before running (-Xbootclasspath/a), adding all of our JARs.  This
 * presented serious deployment and security issues.
 *
 * In this new release of P2PInetAddressOld, we are no longer in the java.net
 * package, which means we dont have to modify the bootclasspath.  Instead,
 * after carefully looking at the InetAddress source code, it is possible
 * to "trick" InetAddress into holding the values we need (such as the
 * domain name and IP address) without accessing its internal DNS-based
 * name-service, which would cause it to resolve out to a normal, non-JXTA
 * based TCP/IP network, which is wrong.  If an InetAddress is constructed
 * where it is given the domain name, its IP address, and its canonical domain
 * name, then it will never touch the internal DNS-based name-service.
 * We do exactly this; all of our calls to P2PInetAddressOld construct the
 * three values and use them to set InetAddress.  Since there is no way to
 * touch the canonical domain name (it is private and there are no
 * accessor methods), we have to use reflection to access the package-private
 * field (see http://www.onjava.com/pub/a/onjava/2003/11/12/reflection.html
 * for an article on how we do this).  Most of this work is done in the
 * method toInetAddress().  A client first calls one of the static factory
 * methods, such as getByAddress(); we then construct a P2PInetAddressOld instance,
 * and then ask that instance to give us an InetAddress representation
 * of itself (toInetAddress()).
 *
 * @author Brad GNUberg, bkn3@columbia.edu
 * @version 0.5 - Updated to not need subclassing of java.net.InetAddress
 *
 * @todo The following are not currently supported: Multicast, UDP sockets, link local and site local addresses.
 * @todo This code is not checking against the installed security manager.
 * @todo Technically, the way we work with the loopback address is incorrect.  We might want to
 * rethink exactly what it means in the context of the Jxta peer-to-peer network.
 * @todo Important: add a new method, getByAddress() that takes a boolean at the end on whether
 * to resolve and find any missing information.  Then modify all of our extensions to use this new
 * method instead of the standard getByAddress().
 * @todo Change getAllByName() to resolve one host name into many IP addresses.
 * @todo Make sure that anyLocalAddress is implemented correctly, and if not, implement it.
 */
//jxl modification: implement Serizliable
public class P2PInetAddress implements Serializable{
    private static final Logger log = Logger.getLogger("org.p2psockets.P2PInetAddressOld");
    
    /** If we have generated an IP address for the local host then we
     * cache its value so that the value will remain the same during a single
     * session. All access to this variable should be done with the
     * methods getLocalHostAddress() and setLocalHostAddress() to ensure
     * thread-safety (these two methods are synchronized). */
    private static byte[] localHostAddress;
    
    /** Holds our P2PInetAddressOld instance host name. */
    private String hostName;
    
    /** Holds our P2PInetAddressOld instance IP address. */
    private byte[] ipAddress = null;
    
    
    P2PInetAddress() {
        this(null, null);
    }
    
    P2PInetAddress(String hostName, byte address[]) {
        if (hostName == null && address == null) {
            hostName = getLocalHostName();
            address = getIPAddress(hostName);
        }else if (isLocalHost(hostName)) {
            hostName = getLocalHostName();
        }
        
        // is the address null and the host an IP string?
        if (ipAddress == null && isIPAddress(hostName)) {
            ipAddress = fromIPString(hostName);
        }
        if(ipAddress == null){
            ipAddress = generateIPAddress(hostName);
        }
        
        // if the host is just an IP string than throw its value away
        if (isIPAddress(hostName)) {
            hostName = null;
        }
        
        if (toIPString(ipAddress).equals("127.0.0.1") ||
                toIPString(ipAddress).equals("0.0.0.0")) {
            if (hostName == null) {
                hostName = getLocalHostName();
		ipAddress = generateIPAddress(hostName);
            }
            
        }
        
        this.hostName = hostName;
    }
    
    public static boolean isLocalHost(String host){
        try{
            if(
                    host == null ||
                    host.equals("localhost")||
                    host.equals("127.0.0.1")||
                    host.equals("0.0.0.0") ||
                    host.equals(getLocalHostName())
                    ){
                return true;
            }else if(host.equals(InetAddress.getLocalHost().getHostAddress())){
                log.warn("Warning: specified host "+host+"" +
                        " is the local host ip address. We are going " +
                        "to interpret this as the p2p localhost " +
                        "and not a p2p remote address.");
                return true;
            }else{
                return false;
            }
        }catch(UnknownHostException uhe){
            throw new RuntimeException(uhe);
        }
    }
    /**
     * Create an InetAddress based on the provided host name and IP address
     * No name service is checked for the validity of the address.
     *
     * <p> The host name can either be a machine name, such as
     * "<code>java.sun.com</code>", or a textual representation of its IP
     * address.
     *
     * <p> IPv4 address byte array must be 4 bytes long.
     *
     * @param host the specified host
     * @param addr the raw IP address in network byte order
     * @return  an InetAddress object created from the raw IP address
     * and hostname.
     */
    public static InetAddress getByAddress(String host, byte[] addr)
    throws P2PInetAddressException {
        P2PInetAddress p2pAddr = new P2PInetAddress(host, addr);
        
        return p2pAddr.toInetAddress();
    }
    
    /**
     * Determines the IP address of a host, given the host's name.
     *
     * <p> The host name can either be a machine name, such as
     * "<code>java.sun.com</code>", or a textual representation of its
     * IP address. If a literal IP address is supplied, only the
     * validity of the address format is checked.
     *
     * @param      host   the specified host, or <code>null</code> for the
     *                    local host.
     * @return     an IP address for the given host name.
     * @exception  UnknownHostException  if no IP address for the
     *               <code>host</code> could be found.
     */
    public static InetAddress getByName(String host) throws UnknownHostException {
        try {
            byte[] addr = null;
            
            // make sure someone didn't pass in an IP address as a string
            if (isIPAddress(host)) {
                // check to see if this is the local host or the "any" address; if so
                // use our local peer name to generate the required info
                if (host.equals("127.0.0.1") || host.equals("0.0.0.0")) {
                    host = getLocalHostName();
                    addr = getIPAddress(host);
                } else {
                    InetAddress hostInetAddr = P2PInetAddress.getByAddress(host, null);
                    
                    addr = hostInetAddr.getAddress();
                }
            }
            
            if (isLocalHost(host))
                host = getLocalHostName();
            
            // search the network to see if there is an ip address
            // for the given hostname
            if (addr == null)
                addr = P2PNameService.findIPAddress(host);
            
            // if there is no pre-existing advertisement, just generate an IP address
            if (addr == null)
                addr = getIPAddress(host);
            
            P2PInetAddress p2pAddr = new P2PInetAddress(host, addr);
            
            return p2pAddr.toInetAddress();
        } catch (Exception e) {
            log.error("Exception resolveing address of "+host,e);
            throw new UnknownHostException(e.toString());
        }
    }
    
    /**
     * Given the name of a host, returns an array of its IP addresses,
     * based on the configured name service on the system.
     *
     * <p> The host name can either be a machine name, such as
     * "<code>java.sun.com</code>", or a textual representation of its IP
     * address. If a literal IP address is supplied, only the
     * validity of the address format is checked.
     *
     * @param      host   the name of the host.
     * @return     an array of all the IP addresses for a given host name.
     *
     * @exception  UnknownHostException  if no IP address for the
     *               <code>host</code> could be found.
     */
    public static InetAddress[] getAllByName(String host)
    throws UnknownHostException {
        InetAddress results[] = new InetAddress[1];
        
        results[0] = P2PInetAddress.getByName(host);
        
        return results;
    }
    
    /**
     * Returns an <code>InetAddress</code> object given the raw IP address .
     * The argument is in network byte order: the highest order
     * byte of the address is in <code>getAddress()[0]</code>.
     *
     * <p> This method doesn't block, i.e. no reverse name service lookup
     * is performed.
     *
     * <p> IPv4 address byte array must be 4 bytes long.
     *
     * @param addr the raw IP address in network byte order
     * @return  an InetAddress object created from the raw IP address.
     * @exception  UnknownHostException  if IP address is of illegal length
     */
    public static InetAddress getByAddress(byte[] addr)
    throws P2PInetAddressException {
        P2PInetAddress p2pAddr = new P2PInetAddress(null, addr);
        
        return p2pAddr.toInetAddress();
    }
    
    /**
     * Returns the local host.
     *
     * @return     the IP address of the local host.
     *
     * @exception  UnknownHostException  if no IP address for the
     *               <code>host</code> could be found.
     */
    public static synchronized InetAddress getLocalHost() throws P2PInetAddressException {
        if(localHostCache == null){
            P2PInetAddress p2pAddr = new P2PInetAddress(null, null);
            localHostCache = p2pAddr.toInetAddress();
        }
        return localHostCache;
    }
    private static InetAddress localHostCache = null;
    
    public static InetAddress anyLocalAddress() throws P2PInetAddressException {
        InetAddress results = P2PInetAddress.getByAddress("0.0.0.0", null);
        
        return results;
    }
    
    public static InetAddress loopbackAddress() throws P2PInetAddressException {
        InetAddress results = P2PInetAddress.getByAddress("localhost", null);
        
        return results;
    }
    
    /** Converts this P2PInetAddressOld into a java.net.InetAddress object. */
    protected InetAddress toInetAddress() throws P2PInetAddressException {
        try {
            // make sure we have all the information we need:
            // both a host name and an IP address
            if (hostName == null && ipAddress != null) {
                String ipAddressString = toIPString(ipAddress);
                hostName = P2PNameService.findHostName(ipAddressString);
            }
            
            
            // if no host name at this point then just "stringify" the IP address
            if (hostName == null) {
                hostName = getIPString(ipAddress);
            }
            
            InetAddress results = InetAddress.getByAddress(hostName, ipAddress);
            
            // set this InetAddress object's canonicalHostName field using reflection
            Field canonicalHostNameField = InetAddress.class.getDeclaredField("canonicalHostName");
            canonicalHostNameField.setAccessible(true);
            
//            Field fields[] = InetAddress.class.getDeclaredFields();
//            for (int i = 0; i < fields.length; ++i) {
//                if (fields[i].getName().equals("canonicalHostName")) {
//                    fields[i].setAccessible(true);
//                    canonicalHostNameField = fields[i];
//                    break;
//                }
//            }
            
//            if (canonicalHostNameField == null) {
//                throw new UnknownHostException("canonicalHostName could not be found through " +
//                        "Java reflection.");
//            }
            
            canonicalHostNameField.set(results, hostName);
            
            return results;
        } catch (Exception e) {
            throw new P2PInetAddressException(e);
        }
    }
    
    /** Returns the IP address for a given host name
     * as a series of bytes.
     *
     * If host name is null, the it is assumed that
     * the "any"/wildcard interface is being requested
     * (0.0.0.0).  We resolve this to being the host name
     * and IP address for the localhost.  The same thing
     * is returned for the loop back interface (i.e.
     * the host "localhost" or the address "127.0.0.1").
     */
    protected static byte[] getIPAddress(String hostName) {
        if (isLocalHost(hostName)) {
            hostName = getLocalHostName();
            if (getLocalHostAddress() == null) {
                setLocalHostAddress(generateIPAddress(hostName));
            }
            
            return getLocalHostAddress();
        }
        
        return generateIPAddress(hostName);
    }
    
    /** Returns the IP address for a given host name
     * as a formatted string.
     *
     * If host name is null, the it is assumed that
     * the "any"/wildcard interface is being requested
     * (0.0.0.0).  We resolve this to being the host name
     * and IP address for the localhost.  The same thing
     * is returned for the loop back interface (i.e.
     * the host "localhost" or the address "127.0.0.1").
     */
    protected static String getIPString(String hostName) {
        return toIPString(getIPAddress(hostName));
    }
    
    /** Converts an IP address from a series of bytes to a string. */
    protected static String getIPString(byte[] addr) {
        StringBuffer results = new StringBuffer();
        
        
        try {
            for(int i = 0;i<4;i++){
                int b = addr[i];
                if(b < 0)
                    b+=256;
                results.append(new Integer(b).toString());
                results.append(".");
             
            }
            results.deleteCharAt(results.length()-1);
            return results.toString();
        } catch (NumberFormatException e) {
            // should never happen
            log.error("Exception formating address", e);
            throw new RuntimeException(e.toString());
        }
    }
    
    
    /** Hashes hostnames into an IPv4 format (i.e. four bytes).
     * We generate this value by cycling over each of the bytes
     * in the hostName string.  At each stop, we add the byte
     * value to the byte value in the 4 byte IP array, also cycling
     * through the 4 byte IP array.
     *
     * None of the bytes can have the values 0, 127, or 255.  To protected
     * against this, we check to see if the generated IP address has the
     * bytes 0, 127, or 255.  If any byte is 0 or 127, we add one to it;
     * if any byte is 255, then we subtract one from it.
     */
    protected static byte[] generateIPAddress(String hostName) {
        byte results[] = new byte[4];
        results[0] = 0;
        results[1] = 0;
        results[2] = 0;
        results[3] = 0;
        
        byte hostNameBytes[] = hostName.getBytes();
        
        for (int i = 0; i < hostNameBytes.length; i++) {
            results[i % 4] += hostNameBytes[i];
        }
        
        // make sure we don't have the values 0, 127, or 255
        for (int i = 0; i < 4; i++) {
            if (results[i] == 0 || results[i] == 127)
                results[i] += (byte)1;
            else if (results[i] == 255)
                results[i] -= - (byte)1;
        }
        
        return results;
    }
    
    /** Converts a series of bytes expressing an IP address
     * into a string, such as "33.22.44.12".
     */
    protected static String toIPString(byte[] value) {
        String results;
        if (value == null) {
            results = "0.0.0.0";
        } else {
            results = (value[0] & 0xff) + "." +
                    (value[1] & 0xff) + "." +
                    (value[2] & 0xff) + "." +
                    (value[3] & 0xff);
        }
        
        return results;
    }
    
    /** Converts a dotted IP string into an array of 4 bytes.
     */
    protected static byte[] fromIPString(String ipString) {
        // this is a tricky process; we are using bytes to hold
        // our values, but in Java bytes are signed (i.e. from -127 to 127).
        // when we convert the byte into a string they come out to the correct
        // values because we use boolean arithmetic.
        // we need to convert the string into a short (16 bits), then throw
        // away the high-order 8 bits and turn it back into a byte
        
        byte[] results = new byte[4];
        
        StringTokenizer tk = new StringTokenizer(ipString, ".", false);
        try {
            // go over each character of each byte
            for (int i = 0; i < 4; i++) {
                String ipByte = tk.nextToken();
                results[i] = new Short(ipByte).byteValue();
            }
        } catch (NumberFormatException e) {
            log.error("Exception parsing byte address", e);
            throw new RuntimeException(e.toString());
        }
        
        return results;
    }
    
    /** Checks to see if the given value in 'checkMe' is
     * an IP address.
     */
    protected static boolean isIPAddress(String checkMe) {
        if (checkMe == null)
            return false;
        
        boolean results = false;
        
        StringTokenizer tk = new StringTokenizer(checkMe, ".", false);
        if (tk.countTokens() != 4)
            return false;
        
        // go over each character of each byte; make sure that it
        // is actually a number
        for (int i = 0; i < 4; i++) {
            String ipByte = tk.nextToken();
            
            if (ipByte.length() > 3) // bytes can't be longer than 3 numbers
                return false;
            
            for (int j = 0; j < ipByte.length(); j++) {
                char ipChar = ipByte.charAt(j);
                switch (ipChar) {
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9': break;
                    default: return false;
                }
            }
        }
        
        return true;
    }
    
//    protected static int getIPAddressAsInteger(byte[] addr) {
//        int results = 0;
//
//        results = addr[3] & 0xFF;
//        results |= (addr[2] << 8) & 0xFF00;
//        results |= (addr[1] << 16) & 0xFF0000;
//        results |= (addr[0] << 24) & 0xFF000000;
//
//        return results;
//    }
    
    /** Gets the local host name for this peer, such as "Brad GNUberg".
     */
    protected static String getLocalHostName() {
        return P2PNameService.PEER_SERVICE_PREFIX +
                P2PNetwork.getPeerGroup().getPeerName() +
                P2PNameService.PEER_SERVICE_SUFFIX;
    }
    
    /** This method mediates access to the static localHostAddress variable
     * to help with thread-safety. */
    protected static synchronized byte[] getLocalHostAddress() {
        if(localHostAddress == null){
            localHostAddress = generateIPAddress(getLocalHostName());
        }
        return localHostAddress;
    }
    
    /** This method mediates access to the static localHostAddress variable
     * to help with thread-safety. */
    protected static synchronized void setLocalHostAddress(byte[] addr) {
        localHostAddress = addr;
    }
    
//    /**
//     * Converts the IP address as the integer intAddr to a
//     * byte array.
//     */
//    protected static byte[] getAddress(int intAddr) {
//        byte[] results = new byte[4];
//
//        results[0] = (byte)((intAddr >>> 24) & 0xFF);
//        results[1] = (byte)((intAddr >>> 16) & 0xFF);
//        results[2] = (byte)((intAddr >>> 8) & 0xFF);
//        results[3] = (byte)(intAddr & 0xFF);
//
//        return results;
//    }
    
    //start jxl modification
    public int hashCode(){
        int ipHash = ipAddress[0]^ipAddress[1]^ipAddress[2]^ipAddress[3];
        if(hostName != null){
            return this.hostName.hashCode()^ipHash;
        }else{
            return ipHash;
        }
    }
    public boolean equals(Object o){
        if( this.getClass().isInstance(o)){
            return hashCode() == o.hashCode();
        }//else
        return false;
    }
    //end jxl modificatoin
}
