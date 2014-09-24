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

import java.io.*;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.Random;

import net.jxta.peergroup.*;
import net.jxta.rendezvous.*;
import org.apache.log4j.Logger;


/**
 * This class signs us into the JXTA network.
 * It is meant to model the JXTA network as a whole.
 *
 * By default, we re-profile this peer every time it starts
 * up using the JXTA Profiler.  To turn off this behavior,
 * call the signin() method with the fourth boolean argument
 * set to false to indicate not to peform profiling.
 *
 * Threading: the entire object is locked if
 * any methods are called to mediate access
 * to the netPeerGroup.
 * We are assuming net.jxta.peergroup.PeerGroup
 * is thread-safe.
 *
 * This class has been revised to allow the user to specify what peer group to
 * use. If no PeerGroup is specified, then this class will use the netPeerGroup.
 * Alex Lynch (jxlynch@users.sf.net)
 *
 * @author Brad GNUberg, bkn3@columbia.edu
 * @version 0.7
 *
 * @todo Doesn't seem to work correctly if the peer itself is a Net Peer Group rendezvous.
 * @todo Create a constructor for initialize that takes an application string; this will
 * hash the string into a peer group ID, and then find or create an application level
 * peer group.  We should also have an initialize() method that takes a PeerGroup object,
 * which will find or create the given peer group.  This first method is for those programmers
 * who don't know anything about Jxta, while the second is for those who know more.
 */
public class P2PNetwork {
    static{
        System.setProperty("log4j.configuration", P2PNetwork.class.getClassLoader().getResource("p2ps.logging.properties").toString());
    }
    private static final Logger log = Logger.getLogger("org.p2psockets.P2PNetwork");
    
    private static P2PSocketLooupManager lookupAddapter = null;
    
    /** The system property name for setting the P2P network to sign into and initialize. */
    public static String P2PSOCKETS_NETWORK = "p2psockets.network";
    /** The system property name for the username of this Jxta peer.*/
    public static String JXTA_USERNAME = "net.jxta.tls.principal";
    /** The system property name for the password of this Jxta peer.*/
    public static String JXTA_PASSWORD = "net.jxta.tls.password";
    
    /** The system property name that specifies where to find the Jxta configuration
     * files. */
    public static String JXTA_HOME = "JXTA_HOME";
    
    private static PeerGroup peerGroup;
    
    /** The name to use for scoping all server sockets and sockets. */
    private static String applicationName;
    
    private static boolean signedIn = false;
    
    /**
     * Determines if an account exists for the given username.
     * @deprecated No replacement. This method never had a real implementation, and is now obsolete.
     */
    public static boolean accountExists(String userName) throws Exception {
        // FIXME: See if an account exists
        /*
                String jxtaHome = System.getProperty("JXTA_HOME", "." + File.separator + ".jxta");
                File homeDir = new File(jxtaHome);
                PSEConfig pseConfig = new PSEConfig(homeDir);
         
                return pseConfig.principalIsIssuer(userName, null);
         */
        throw new RuntimeException("not implemented");
    }
    
    /**
     * Removes any old JXTA account and creates a new one, profiling the peer and signing
     * them into the JXTA network.  This version of createAccount() uses the username and password
     * given in the shell variables net.jxta.tls.principal and net.jxta.tls.password.
     * @deprecated Use autoSignin
     */
    public static void createAccount() throws Exception {
        String userName = System.getProperty(JXTA_USERNAME);
        String password = System.getProperty(JXTA_PASSWORD);
        
        if (userName == null || password == null) {
            throw new RuntimeException("You must provide a JXTA username and password by setting "
                    + JXTA_USERNAME + " and " + JXTA_PASSWORD);
        }
        
        createAccount(userName, password);
    }
    
    /**
     * Removes any old JXTA account and creates a new one, profiling the peer and signing
     * them into the JXTA network.  This version of createAccount() uses the
     * application peer group name given in the shell variable p2psockets.network.
     * @deprecated Use autoSignin
     */
    public static void createAccount(String userName, String password) throws Exception {
        String appName = System.getProperty(P2PSOCKETS_NETWORK);
        if (appName == null) {
            throw new RuntimeException("You must either provide the system property "
                    + P2PSOCKETS_NETWORK + " when running your software, "
                    + "or code your application to use "
                    + "P2PNetwork.createAccount(String userName, String password, "
                    + "String applicationName)");
        }
        
        createAccount(userName, password, appName);
    }
    /**
     * 
     * @deprecated Use autoSignin
     */
    public static synchronized void createAccount(String username,String password,String appName)
    throws Exception{
        createAccount(username,password,appName, null);
    }
    
    /**
     * Removes any old JXTA account and creates a new one, profiling the peer and signing
     * them into the JXTA network.
     * @deprecated Use autoSignin
     */
    public static synchronized void createAccount(String userName, String password,
            String appName, PeerGroup group) throws Exception {
        
        // make sure we aren't already signed in
        if (signedIn) {
            return;
        }
        
        // remove old .jxta installation
        String jxtaHome = System.getProperty("JXTA_HOME", "." + File.separator + ".jxta");
        log.info("[Creating Account] JXTA_HOME = " + jxtaHome);
        File jxtaDir = new File(jxtaHome);
        // make sure we actually have a value
        if (jxtaDir != null && !jxtaDir.equals("") && jxtaDir.toString().indexOf(".jxta") != -1) {
            log.info("Deleting JXTA configuration directory at " + jxtaDir + "...");
            jxtaDir.delete();
        }
        
        applicationName = appName;
        
        // set the username and the password
        System.setProperty(JXTA_USERNAME, userName);
        System.setProperty(JXTA_PASSWORD, password);
        
        // Profile this peer
        net.jxta.ext.config.Configurator config =
                new net.jxta.ext.config.Configurator(userName, userName, password);
        boolean result = config.save();
        if (result == false)
            throw new Exception("Unable to save JXTA configuration");
        
        peerGroup = (group == null ? PeerGroupFactory.newNetPeerGroup(): group);
        
        // wait around until we contact a rendezvous server
        if (peerGroup.isRendezvous() == false)
            contactRendezVous(peerGroup);
        
        signedIn = true;
    }
    
    /**
     * 
     * @deprecated Use autoSignin
     */
    public static synchronized void signin() throws Exception{
        signin(null);
    }
    /**
     * 
     * @deprecated Use autoSignin
     */
    public static synchronized void signin(PeerGroup group) throws Exception {
        String userName = System.getProperty(JXTA_USERNAME);
        String password = System.getProperty(JXTA_PASSWORD);
        
        if (userName == null || password == null) {
            throw new RuntimeException("You must provide a JXTA username and password by setting "
                    + JXTA_USERNAME + " and " + JXTA_PASSWORD);
        }
        
        signin(userName, password,group);
    }
    
    /**
     * 
     * @deprecated Use autoSignin
     */
    public static void signin(String userName, String password) throws Exception{
        signin(userName,password,(PeerGroup)null);
    }
    
    /**
     * 
     * @deprecated Use autoSignin
     */
    public static void signin(String userName, String password,PeerGroup group) throws Exception {
        String appName = System.getProperty(P2PSOCKETS_NETWORK);
        if (appName == null) {
            throw new RuntimeException("You must either provide the system property "
                    + P2PSOCKETS_NETWORK + " when running your software, "
                    + "or code your application to use "
                    + "P2PNetwork.signin(String userName, String password, "
                    + "String applicationName)");
        }
        
        signin(userName, password, appName, true,group);
    }
    
    /**
     * 
     * @deprecated Use autoSignin
     */
    public static void signin(String userName, String password, String appName)
    throws Exception {
        signin(userName,password,appName,null);
    }
    
    /**
     * 
     * @deprecated Use autoSignin
     */
    public static void signin(String userName, String password, String appName, PeerGroup group)
    throws Exception {
        signin(userName, password, appName, true, group);
    }
    
    /**
     * 
     * @deprecated Use autoSignin
     */
    public static synchronized void signin(String userName, String password,
            String appName, boolean createIfNotExist) throws Exception {
        signin(userName,password,appName,createIfNotExist, null);
    }
    /**
     * 
     * @param userName The username to use for signing in.
     * @param password The password to use for signing in.
     * @param appName A unique string identifying your application.
     * @param createIfNotExist Whether to create this account if it doesn't exist.
     * @deprecated Use autoSignin
     */
    public static synchronized void signin(String userName, String password,
            String appName, boolean createIfNotExist, PeerGroup group) throws Exception {
        // make sure we aren't already signed in
        if (signedIn) {
            return;
        }
        
        // see if there is a .jxta configuration directory yet; if not, then create an account
        // if the programmer wants this
        String jxtaHome = System.getProperty("JXTA_HOME", "." + File.separator + ".jxta");
        log.info("[Signing In] Using JXTA_HOME = " + jxtaHome);
        File homeDir = new File(jxtaHome);
        if (createIfNotExist == true && homeDir.exists() == false) {
            log.info("No JXTA account exists; creating one for " + userName + "...");
            createAccount(userName, password, appName);
            return;
        }
        
        applicationName = appName;
        
        // FIXME: make sure the username and password given are correct
        /*
        PSEConfig pseConfig = new PSEConfig(homeDir);
         
        if (!pseConfig.principalIsIssuer(userName, null) ||
            !pseConfig.validPasswd(password)) {
            throw new Exception("Invalid username or password given");
        }*/
        
        // set the username and the password
        System.setProperty(JXTA_USERNAME, userName);
        System.setProperty(JXTA_PASSWORD, password);
        
        // Profile this peer
        net.jxta.ext.config.Configurator config =
                new net.jxta.ext.config.Configurator(userName, userName, password);
        boolean result = config.save();
        if (result == false)
            throw new Exception("Unable to save JXTA configuration");
        
        
        peerGroup = (group == null ? PeerGroupFactory.newNetPeerGroup() : group);
        
        // wait around until we contact a rendezvous server
        if (peerGroup.isRendezvous() == false)
            contactRendezVous(peerGroup);
        
        signedIn = true;
        
        
    }
    
    /**
     * This method is for experience users only. It is not recomended. 
     * This method method circumvents all p2ps configuration and utilizes the specified peer group as the
     * container group.
     * @param networkName networkName space to seclude this app from others.
     * @param existentGroup Pre-existing peer group to use.
     * @throws java.lang.Exception If there is an exception while trying to reach the peer group's RendezVous
     */
    public static synchronized void belatedSignin(PeerGroup existentGroup, String networkName) throws Exception{
        if(signedIn){
            return;
        }
        peerGroup = existentGroup;
        applicationName = networkName;
        
         // wait around until we contact a rendezvous server
        if (peerGroup.isRendezvous() == false)
            contactRendezVous(peerGroup);
    
        lookupAddapter = new P2PSocketLooupManager(peerGroup,applicationName);
        lookupAddapter.start();
        
        
        signedIn = true;
    }
    
    /**
     * Signin using complete peer configuration. No gui is shown to the user. Further
     * Tcp port confilcts are handled, and multiple apps may be run in the same dir.
     * 
     * NOTE: peers configured with this method will have a randomly generated virtual
     * hostname. If you require a specific hostname use the two arg autoSignin.
     * @param appName 
     * @throws java.lang.Exception 
     */
    public static synchronized void autoSignin(String appName) throws Exception{
        Random r = new Random();
        byte[] bytes = new byte[32];
        r.nextBytes(bytes);                                       //these chars are reserved
        autoSignin("Random hostName ["+new String(bytes).replaceAll("[/,:]", "_")+"]", appName);
    }
    /**
     * Signin using complete peer configuration. No gui is shown to the user. Further
     * Tcp port confilcts are handled, and multiple apps may be run in the same dir.
     * @param peerName The virtual hostname of the configured peer.
     * @param appName 
     * @throws java.lang.Exception 
     */
    public static synchronized void autoSignin(String peerName, String appName) throws Exception {
        // make sure we aren't already signed in
        if (signedIn) {
            return;
        }
        if(peerName == null || appName == null){
            throw new IllegalArgumentException("Neither peerName nor appName may be null");
        }
        
        log.info("[Auto Signin]");
        
        File dir = File.createTempFile("jxta",".d");
        dir.delete();
        System.setProperty("JXTA_HOME",dir.getAbsolutePath());
        System.setProperty("jxta.peer.name", peerName);
        
        int tcpPort = 9700;
        while(true){
            try{
                tcpPort++;
                ServerSocket ss = new ServerSocket(tcpPort);
                ss.close();
                break;
            }catch(IOException ioe){}
        }
        System.setProperty("jxta.tcp.port", ""+tcpPort);
        System.setProperty(JXTA_USERNAME, "userName");
        System.setProperty(JXTA_PASSWORD, "password");
        
        PeerGroupFactory.setConfiguratorClass(P2PConfigurator.class);
        
        
        applicationName = appName;
        
        
        peerGroup = PeerGroupFactory.newNetPeerGroup();
        
        // wait around until we contact a rendezvous server
        if (peerGroup.isRendezvous() == false)
            contactRendezVous(peerGroup);
    
        lookupAddapter = new P2PSocketLooupManager(peerGroup,applicationName);
        lookupAddapter.start();
        
        signedIn = true;
        
        
    }
    /** Signs us out of the P2P network.
     * FIXME: Actually do something here.
     */
    public static void signOff() throws Exception {
        signedIn = false;
        throw new RuntimeException("not implemented");
    }
    
    /**
     * Get the number of server:port nodes beleived to be active on this network.
     */
    public static int getNetworkNodeCount()throws IOException{
        if(lookupAddapter != null){
            return Collections.list(lookupAddapter.getAdvertisements()).size();
        }else{
            return -1;
        }
    }
    /**
     *Get the PeerGroup used by P2PSockets, this may be the netPeerGroup.
     *This method has been deprecated as this class now allows the option of
     *specifying what PeerGroup to use. If this option is not taken, the
     *netPeerGroup will be used.
     *
     *The replacement for this method is getPeerGroup(). The return from
     *this method will be the same as getPeerGroup().
     *@deprecated
     */
    public static synchronized PeerGroup getNetPeerGroup() {
        return getPeerGroup();
    }
    
    /**
     *This method returns the PeerGroup that was used to initialize P2PSockets.
     *If the version of signin() used was one that takes a PeerGroup param
     *then this method will return that PeerGroup.
     *Otherwise it will retun the netPeerGroup object from PeerGroupFactory.
     */
    public static synchronized PeerGroup getPeerGroup(){
        if (peerGroup == null) {
            throw new RuntimeException("You must call P2PNetwork.signin() before "
                    + " attempting to use this class");
        }
        
        return peerGroup;
    }
    /**
     * This method can be used to change the networkName (also called the applicationName)
     * after the peer has been configured. Great care should be applied when using this method.
     */
    public static synchronized void setApplicationName(String appName) {
        applicationName = appName;
    }
    
    public static synchronized String getApplicationName() { return applicationName; }
    
    protected static void contactRendezVous(PeerGroup inGroup) throws Exception {
        // contact a rendezvous server
        RendezVousService rdv = inGroup.getRendezVousService();
        log.info("Waiting for RendezVous Connection.");
        System.out.flush();
        
        int timeSoFar = 0;
        while (!rdv.isConnectedToRendezVous() && timeSoFar < 120000) { // 2 minutes
            Thread.currentThread().sleep(1000);
            timeSoFar += 1000;
        }
        
        if (!rdv.isConnectedToRendezVous()) {
            throw new RuntimeException("No RendezVous found for the peer group " + inGroup.getPeerGroupName());
            //printTo.write("Starting a RendezVous on this peer\n");
            //printTo.flush();
            //netRdv.startRendezVous();
        } else {
            log.info("Finished connecting to RendezVous.");
        }
    }
}

