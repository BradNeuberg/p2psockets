/*
 *This is a modified version of DefaultConfigurator in the jxta/impl distro.
 *
 *  Copyright (c) 2001-2003 Sun Microsystems, Inc.  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1. Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the
 *  distribution.
 *
 *  3. The end-user documentation included with the redistribution,
 *  if any, must include the following acknowledgment:
 *  "This product includes software developed by the
 *  Sun Microsystems, Inc. for Project JXTA."
 *  Alternately, this acknowledgment may appear in the software itself,
 *  if and wherever such third-party acknowledgments normally appear.
 *
 *  4. The names "Sun", "Sun Microsystems, Inc.", "JXTA" and "Project JXTA"
 *  must not be used to endorse or promote products derived from this
 *  software without prior written permission. For written
 *  permission, please contact Project JXTA at http://www.jxta.org.
 *
 *  5. Products derived from this software may not be called "JXTA",
 *  nor may "JXTA" appear in their name, without prior written
 *  permission of Sun.
 *
 *  THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 *  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED.  IN NO EVENT SHALL SUN MICROSYSTEMS OR
 *  ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 *  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 *  USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 *  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 *  OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 *  SUCH DAMAGE.
 *  ====================================================================
 *
 *  This software consists of voluntary contributions made by many
 *  individuals on behalf of Project JXTA.  For more
 *  information on Project JXTA, please see
 *  <http://www.jxta.org/>.
 *
 *  This license is based on the BSD license adopted by the Apache Foundation.
 *
 *  $Id: P2PConfigurator.java,v 1.3 2005/08/26 18:42:53 jxlynch Exp $
 */
package org.p2psockets;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.Enumeration;
import java.util.Iterator;

import java.io.IOException;
import java.util.NoSuchElementException;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import net.jxta.document.Advertisement;
import net.jxta.document.AdvertisementFactory;
import net.jxta.document.MimeMediaType;
import net.jxta.document.StructuredDocument;
import net.jxta.document.StructuredDocumentFactory;
import net.jxta.document.StructuredDocumentUtils;
import net.jxta.document.StructuredTextDocument;
import net.jxta.document.XMLDocument;
import net.jxta.document.XMLElement;
import net.jxta.exception.ConfiguratorException;
import net.jxta.exception.JxtaError;
import net.jxta.impl.endpoint.IPUtils;
import net.jxta.impl.membership.pse.PSEUtils;
import net.jxta.impl.membership.pse.PSEUtils.IssuerInfo;
import net.jxta.impl.peergroup.ConfigDialog;
import net.jxta.impl.peergroup.NullConfigurator;
import net.jxta.impl.protocol.HTTPAdv;
import net.jxta.impl.protocol.PSEConfigAdv;
import net.jxta.impl.protocol.PlatformConfig;
import net.jxta.impl.protocol.RdvConfigAdv;
import net.jxta.impl.protocol.RelayConfigAdv;
import net.jxta.impl.protocol.TCPAdv;
import net.jxta.peergroup.PeerGroup;
import net.jxta.protocol.TransportAdvertisement;

/**
 * This class is adapted from the jxta default platform configurator.
 * @see net.jxta.ext.config.Configurator
 */
public class P2PConfigurator extends NullConfigurator {
    
    /**
     *  Log4J logger
     **/
    private final transient static Logger LOG = Logger.getLogger(P2PConfigurator.class.getName());
    
    /**
     * Configures the platform using the default directory.
     **/
    public P2PConfigurator() throws ConfiguratorException {
        super();
    }
    
    /**
     * Configures the platform using the specified directory.
     **/
    public P2PConfigurator(File homeDir) throws ConfiguratorException {
        super(homeDir);
    }
    
    /**
     *  {@inheritDoc}
     **/
    public PlatformConfig getPlatformConfig() throws ConfiguratorException {
        super.getPlatformConfig();
        
        // See if we need a reconf
        if ( fixAdvertisement() && false) {
            try {
                ConfigDialog ignore = new ConfigDialog( advertisement );
                ignore.untilDone();
            } catch (Throwable t) {
                if (t instanceof JxtaError) {
                    throw (JxtaError) t;
                }
                
                if (LOG.isEnabledFor(Level.WARN))
                    LOG.warn( "Could not initialize graphical config dialog", t );
                
                BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
                
                // clear any type-ahead
                try {
                    while (in.ready()) {
                        in.readLine();
                    }
                } catch (Exception ignored ) { }
                
                System.err.flush();
                System.out.flush();
                System.out.println("The window-based configurator does not seem to be usable.");
                System.out.print("Do you want to stop and edit the current configuration ? [no]: ");
                System.out.flush();
                String answer = "no";
                try {
                    answer = in.readLine();
                } catch (Exception ignored) { }
                
                // this will cover all the cases of the answer yes, while
                // allowing non-gui/batch type scripts to load the platform
                // see platform issue #45
                
                if ("yes".equalsIgnoreCase(answer)) {
                    save();
                    
                    System.out.println("Exiting; edit the file \"" + configFile.getPath() +
                    "\", remove the file \"reconf\", and then launch JXTA again.");
                    
                    throw new JxtaError("Manual Configuration Requested");
                } else {
                    System.out.println("Attempting to continue using the current configuration.");
                }
            }
            
            // Set again in case it changed
            adjustLog4JPriority();
            
            // Save the updated config.
            save();
        }
        
        return advertisement;
    }
    
    /**
     *  {@inheritDoc}
     **/
    public void setReconfigure(boolean reconfigure) {
        File f = new File(jxtaHomeDir, "reconf");
        
        if (reconfigure) {
            try {
                f.createNewFile();
            } catch (IOException ex1) {
                if (LOG.isEnabledFor(Level.ERROR)) {
                    LOG.error("Could not create 'reconf' file", ex1 );
                    LOG.error("Create the file 'reconf' by hand before retrying.");
                }
            }
        } else {
            try {
                f.delete();
            } catch (Exception ex1) {
                if (LOG.isEnabledFor(Level.ERROR)) {
                    LOG.error("Could not remove 'reconf' file", ex1 );
                    LOG.error("Delete the file 'reconf' by hand before retrying.");
                }
            }
        }
    }
    
    /**
     *  {@inheritDoc}
     **/
    public boolean isReconfigure() {
        try {
            boolean forceReconfig;
            
            File file = new File( jxtaHomeDir, "reconf" );
            forceReconfig = file.exists();
            
            if( LOG.isEnabledFor(Level.DEBUG) )
                LOG.debug( "force reconfig : " + forceReconfig );
            
            return forceReconfig;
        } catch (Exception ex1) {
            if (LOG.isEnabledFor(Level.ERROR)) {
                LOG.error("Could not check 'reconf' file. Assuming it exists.", ex1 );
            }
            
            if( LOG.isEnabledFor(Level.INFO) )
                LOG.info( "Reconfig required - error getting 'reconf' file" );
            return true;
        }
    }
    
    /**
     * Makes sure the PlatformConfig is sane. If it is not, fix it.
     * If it does not exist at all, create a default one.
     *
     * @param  adv  Description of Parameter
     *
     * @return      boolean true: manual reconf advised.
     **/
    private boolean fixAdvertisement() {
        
        boolean reconf = true;
        
        try {
            reconf = isReconfigure();
            
            if( reconf && LOG.isEnabledFor(Level.INFO) ) {
                LOG.info( "Reconfig requested - Forced reconfigure" );
            }
            
            if (advertisement == null) {
                reconf = true;
                
                if( LOG.isEnabledFor(Level.INFO) )
                    LOG.info( "Reconfig requested - New PlatformConfig Advertisement" );
                
                advertisement = (PlatformConfig) AdvertisementFactory.newAdvertisement( PlatformConfig.getAdvertisementType() );
                
                advertisement.setDescription("Platform Config Advertisement created by : " + P2PConfigurator.class.getName() );
            }
            
            // Set the peer name
            String peerName = advertisement.getName();
            
            if ((null == peerName) || (0 == peerName.trim().length())) {
                String jpn = System.getProperty("jxta.peer.name", "" );
                
                if(0 != jpn.trim().length()) {
                    peerName = jpn;
                    advertisement.setName(jpn);
                } else {
                    reconf = true;
                    
                    if( LOG.isEnabledFor(Level.INFO) ) {
                        LOG.info( "Reconfig requested - Missing peer name" );
                    }
                }
            }
            
            // Check the HTTP Message Transport parameters.
            StructuredDocument http = (StructuredTextDocument) advertisement.getServiceParam(PeerGroup.httpProtoClassID);
            HTTPAdv httpAdv =  null;
            boolean httpDisabled = false;
            
            if (http != null) {
                try {
                    httpDisabled = http.getChildren("isOff").hasMoreElements();
                    
                    XMLElement param = null;
                    
                    Enumeration httpChilds = http.getChildren( TransportAdvertisement.getAdvertisementType());
                    
                    // get the TransportAdv from either TransportAdv or HttpAdv
                    if( httpChilds.hasMoreElements() ) {
                        param = (XMLElement) httpChilds.nextElement();
                    } else {
                        httpChilds = http.getChildren( HTTPAdv.getAdvertisementType());
                        
                        if( httpChilds.hasMoreElements() ) {
                            param = (XMLElement) httpChilds.nextElement();
                        }
                    }
                    
                    httpAdv = (HTTPAdv) AdvertisementFactory.newAdvertisement( param );
                    
                    String cfMode = httpAdv.getConfigMode();
                    if( cfMode == null )
                        cfMode = "auto";
                    
                    String intf = httpAdv.getInterfaceAddress();
                    
                    if( (null != intf) && (intf.trim().length() < 1) ) {
                        if ( ! isValidInetAddress(intf.trim())) {
                            reconf = true;
                            
                            if (LOG.isEnabledFor(Level.INFO)) {
                                LOG.info("Reconfig requested - invalid interface address");
                            }
                        }
                    }
                } catch (Exception advTrouble) {
                    if( LOG.isEnabledFor(Level.WARN) )
                        LOG.warn( "Reconfig requested - http advertisement corrupted", advTrouble );
                    
                    httpAdv = null;
                }
            }
            
            if (httpAdv == null) {
                reconf = true;
                if( LOG.isEnabledFor(Level.INFO) )
                    LOG.info( "Reconfig requested - http advertisement missing, making a new one." );
                
                httpAdv = (HTTPAdv) AdvertisementFactory.newAdvertisement( HTTPAdv.getAdvertisementType() );
                httpAdv.setProtocol( "http" );
                httpAdv.setPort(9700);
                httpAdv.setProxyEnabled(true);
                httpAdv.setServerEnabled(true);
                httpAdv.setClientEnabled(true);
            }
            
            // Create new param docs that contain the updated advs
            http = StructuredDocumentFactory.newStructuredDocument( MimeMediaType.XMLUTF8, "Parm");
            StructuredDocument httAdvDoc = (StructuredDocument) httpAdv.getDocument(MimeMediaType.XMLUTF8);
            StructuredDocumentUtils.copyElements(
            http,
            http,
            httAdvDoc );
            if (httpDisabled && false) {
                http.appendChild(http.createElement("isOff"));
            }
            advertisement.putServiceParam(PeerGroup.httpProtoClassID, http);
            
            // Check the TCP Message Transport parameters.
            StructuredDocument tcp  = (StructuredTextDocument) advertisement.getServiceParam(PeerGroup.tcpProtoClassID);
            TCPAdv  tcpAdv = null;
            boolean tcpDisabled = false;
            
            if (tcp != null) {
                try {
                    tcpDisabled = tcp.getChildren("isOff").hasMoreElements();
                    
                    XMLElement param = null;
                    
                    Enumeration tcpChilds = tcp.getChildren(TransportAdvertisement.getAdvertisementType());
                    
                    // get the TransportAdv from either TransportAdv or TcpAdv
                    if( tcpChilds.hasMoreElements() ) {
                        param = (XMLElement) tcpChilds.nextElement();
                    } else {
                        tcpChilds = tcp.getChildren(
                        TCPAdv.getAdvertisementType());
                        
                        if( tcpChilds.hasMoreElements() ) {
                            param = (XMLElement) tcpChilds.nextElement();
                        }
                    }
                    
                    tcpAdv = (TCPAdv) AdvertisementFactory.newAdvertisement(param);
                    String cfMode = tcpAdv.getConfigMode();
                    if( cfMode == null )
                        cfMode = "auto";
                    
                    String intf = tcpAdv.getInterfaceAddress();
                    
                    if( (null != intf) && (intf.trim().length() < 1) ) {
                        if ( ! isValidInetAddress(intf.trim())) {
                            reconf = true;
                            
                            if (LOG.isEnabledFor(Level.INFO)) {
                                LOG.info("Reconfig requested - invalid interface address");
                            }
                        }
                    }
                } catch (Exception advTrouble) {
                    if( LOG.isEnabledFor(Level.WARN) )
                        LOG.warn( "Reconfig requested - tcp advertisement corrupted", advTrouble );
                    
                    tcpAdv = null;
                }
            }
            
            if (tcpAdv == null) {
                reconf = true;
                if( LOG.isEnabledFor(Level.INFO) )
                    LOG.info( "Reconfig requested - tcp advertisement missing, making a new one." );
                
                int port = 9701;
                // get the port from a property
                String tcpPort = System.getProperty("jxta.tcp.port");
                if (tcpPort != null) {
                    try {
                        int propertyPort = Integer.parseInt( tcpPort );
                        if( (propertyPort < 65536) && ( propertyPort >= 0 ) )
                            port = propertyPort;
                        else {
                            if( LOG.isEnabledFor(Level.WARN) )
                                LOG.warn( "Property 'jxta.tcp.port' is not a valid port number : " + propertyPort );
                        }
                        
                    } catch ( NumberFormatException ignored ) {
                        if( LOG.isEnabledFor(Level.WARN) )
                            LOG.warn( "Property 'jxta.tcp.port' was not an integer : " + tcpPort );
                    }
                }
                
                tcpAdv = (TCPAdv) AdvertisementFactory.newAdvertisement( TCPAdv.getAdvertisementType() );
                
                tcpAdv.setProtocol( "tcp" );
                tcpAdv.setInterfaceAddress( null );
                tcpAdv.setPort( port );
                tcpAdv.setMulticastAddr( "224.0.1.85" );
                tcpAdv.setMulticastPort( 1234 );
                tcpAdv.setMulticastSize( 16384 );
                tcpAdv.setMulticastState( true );
                tcpAdv.setServer( null );
            }
            
            tcp = StructuredDocumentFactory.newStructuredDocument(MimeMediaType.XMLUTF8, "Parm");
                
            StructuredDocumentUtils.copyElements(
            tcp,
            tcp,
            (StructuredDocument) tcpAdv.getDocument(MimeMediaType.XMLUTF8));
            if (tcpDisabled) {
                tcp.appendChild(tcp.createElement("isOff"));
            }
            advertisement.putServiceParam(PeerGroup.tcpProtoClassID, tcp);
            
            boolean secureNeedsReconf = true;
            
            // Check if PSE Config Adv is initialized.
            try {
                XMLElement param = (XMLElement) advertisement.getServiceParam( PeerGroup.membershipClassID );
                
                if (param != null) {
                    try {
                        Advertisement adv = AdvertisementFactory.newAdvertisement( param );
                        
                        if( adv instanceof PSEConfigAdv ) {
                            secureNeedsReconf = false;
                        }
                    } catch( NoSuchElementException notPSEConfigAdv ) {;}
                }
            } catch (Exception nobigdeal) {
                if( LOG.isEnabledFor(Level.WARN) ) {
                    LOG.warn( "Failure reading PSEConfigAdv. Will make a new one.", nobigdeal );
                }
            }
            
            if( secureNeedsReconf ) {
                // clear existing root cert & private key
                advertisement.removeServiceParam( PeerGroup.membershipClassID );
                
                if( LOG.isEnabledFor(Level.INFO) )
                    LOG.info( "Reconfig requested - PSE wanted config" );
            }
            
            // FIXME 20031001 bondolo@jxta.org See Issue #835
            if( secureNeedsReconf && (null != System.getProperty( "net.jxta.tls.principal" )) && (null != System.getProperty( "net.jxta.tls.password" )) ) {
                if( LOG.isEnabledFor(Level.WARN) ) {
                    LOG.warn( "Bypassing Security Config UI" );
                }
                
                PSEConfigAdv pseConf = (PSEConfigAdv) AdvertisementFactory.newAdvertisement( PSEConfigAdv.getAdvertisementType() );
                
                IssuerInfo info = PSEUtils.genCert( System.getProperty( "net.jxta.tls.principal" ), null );
                
                pseConf.setCertificate( info.cert );
                pseConf.setPrivateKey( info.subjectPkey, System.getProperty( "net.jxta.tls.password" ).toCharArray() );
                // from scratch
                
                XMLDocument pseDoc = (XMLDocument) pseConf.getDocument(MimeMediaType.XMLUTF8);
                
                advertisement.putServiceParam(PeerGroup.membershipClassID, pseDoc );
                
                secureNeedsReconf = false;
            }
            
            reconf |= secureNeedsReconf;
            
            // Check the relay config
            RelayConfigAdv relayConfig = null;
            try {
                XMLElement param = (XMLElement) advertisement.getServiceParam(PeerGroup.relayProtoClassID);
                
                if (param != null) {
                    try {
                        // XXX 20041027 backwards compatibility
                        param.addAttribute( "type", RelayConfigAdv.getAdvertisementType() );
                        
                        Advertisement adv = AdvertisementFactory.newAdvertisement( param );
                        
                        if( adv instanceof RelayConfigAdv ) {
                            relayConfig = (RelayConfigAdv) adv;
                        }
                    } catch( NoSuchElementException notRelayConfigAdv ) {
                        ; // that's ok
                    }
                }
            } catch (Exception failure) {
                if (LOG.isEnabledFor(Level.ERROR))
                    LOG.error("Problem reading relay configuration", failure);
            }
                
            if( null == relayConfig ) {
                // restore default values.
                relayConfig = (RelayConfigAdv) AdvertisementFactory.newAdvertisement( RelayConfigAdv.getAdvertisementType() );

                reconf = true;
                if( LOG.isEnabledFor(Level.INFO) ) {
                    LOG.info( "Reconfig requested - relay advertisement missing, making a new one." );
                }
            }
            
            if( (0 == relayConfig.getSeedingURIs().length) && (0 == relayConfig.getSeedRelays().length)) {
                // add the default relay seeding peer.
                relayConfig.addSeedingURI( "http://rdv.jxtahosts.net/cgi-bin/relays.cgi?2" );
            }
            
            XMLDocument relayDoc = (XMLDocument) relayConfig.getDocument(MimeMediaType.XMLUTF8);
            
            advertisement.putServiceParam(PeerGroup.relayProtoClassID, relayDoc );
            
            // Check Rendezvous Configuration
            RdvConfigAdv rdvAdv = null;
            try {
                XMLElement param = (XMLElement) advertisement.getServiceParam(PeerGroup.rendezvousClassID);
                
                if (param != null) {
                    try {
                        // XXX 20041027 backwards compatibility
                        param.addAttribute( "type", RdvConfigAdv.getAdvertisementType() );
                        
                        Advertisement adv = AdvertisementFactory.newAdvertisement( param );
                        
                        if( adv instanceof RdvConfigAdv ) {
                            rdvAdv = (RdvConfigAdv) adv;
                        }
                    } catch( NoSuchElementException notRdvConfigAdv ) {
                        ; // that's ok
                    }
                }
            } catch (Exception failure) {
                if (LOG.isEnabledFor(Level.ERROR))
                    LOG.error("Problem reading rendezvous configuration", failure);
            }
                        
            if( null == rdvAdv ) {
                // restore default values.
                rdvAdv = (RdvConfigAdv) AdvertisementFactory.newAdvertisement( RdvConfigAdv.getAdvertisementType() );
                            
                reconf = true;
                if( LOG.isEnabledFor(Level.INFO) ) {
                    LOG.info( "Reconfig requested - rendezvous advertisement missing, making a new one." );
                            }
                        }
                        
            if( (0 == rdvAdv.getSeedingURIs().length) && 
                (0 == rdvAdv.getSeedRendezvous().length) && 
                (RdvConfigAdv.RendezVousConfiguration.RENDEZVOUS != rdvAdv.getConfiguration()) && 
                (RdvConfigAdv.RendezVousConfiguration.AD_HOC != rdvAdv.getConfiguration()) &&
                !relayConfig.isClientEnabled() ) {
                // add the default rendezvous seeding peer if we don't know of any rendezvous, aren't in ad-hoc mode or using a relay.
                rdvAdv.addSeedingURI( "http://rdv.jxtahosts.net/cgi-bin/rendezvous.cgi?2" );
            }
                        
            XMLDocument rdvDoc = (XMLDocument) rdvAdv.getDocument(MimeMediaType.XMLUTF8);
                        
            advertisement.putServiceParam(PeerGroup.rendezvousClassID, rdvDoc );
            
            // if no proxy param section, disable it.
            StructuredDocument proxy = (StructuredTextDocument) advertisement.getServiceParam(PeerGroup.proxyClassID);
            
            if( null == proxy ) {
                proxy = (StructuredTextDocument) StructuredDocumentFactory.newStructuredDocument( MimeMediaType.XMLUTF8, "Parm");
                proxy.appendChild(proxy.createElement("isOff"));
                advertisement.putServiceParam(PeerGroup.proxyClassID, proxy);
            }
            
            // Insert a default MessengerQueueSize element if there are no endpoint params.
            XMLDocument endp = (XMLDocument) advertisement.getServiceParam(PeerGroup.endpointClassID);
            
            if( null != endp ) {
                endp = (XMLDocument) StructuredDocumentFactory.newStructuredDocument(MimeMediaType.XMLUTF8, "Parm");
            
                endp.appendChild(endp.createElement("MessengerQueueSize", "20"));
            
                advertisement.putServiceParam(PeerGroup.endpointClassID, endp);
            }
            
            // If we did not modify anything of importance or see anything wrong,
            // leave the adv alone.
        } catch(Exception serious) {
            if (LOG.isEnabledFor(Level.ERROR))
                LOG.error("Trouble while fixing PlatformConfig. Hope for the best.", serious);
        }
        
        return reconf;
    }
    
    private boolean isValidInetAddress(String ia) {
        boolean found = false;
        boolean loopback = false;
        
        ia = (ia != null ? ia.trim() : "");
        
        if ( ia.length() > 0 ) {
            InetAddress[] ias = null;
            
            try {
                ias = InetAddress.getAllByName(ia);
            } catch (Throwable ignored) {
                // Could not verify ? Something's fishy. Let's check further
                // if a reconfig is in order.
            }
            
            for (Iterator la = IPUtils.getAllLocalAddresses(); la.hasNext() && ! found; ) {
                for (int i = 0; i < ias.length; i++) {
                    found |= ias[i].equals((InetAddress)la.next());
                }
            }
            
            loopback = true;
            
            for (int i = 0; i < ias.length; i++) {
                loopback &= ias[i].isLoopbackAddress();
            }
        }
        
        return found || loopback;
    }
}
