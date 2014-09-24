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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import org.p2psockets.P2PSocketLeaseManager;
import net.jxta.discovery.DiscoveryService;
import net.jxta.document.AdvertisementFactory;
import net.jxta.id.IDFactory;
import net.jxta.peergroup.PeerGroup;
import net.jxta.peergroup.PeerGroupID;
import net.jxta.pipe.PipeID;
import net.jxta.pipe.PipeService;
import net.jxta.protocol.PipeAdvertisement;
import net.jxta.rendezvous.RendezVousService;
import org.apache.log4j.Logger;





/**
  * This class provides a "Name Service" facade to the
  * JXTA network.  It provides a standard way
  * to resolve a hostname, port, or IP address into a
  * JXTA socket that can be used to communicate with the
  * given endpoint (the 'resolve' set of methods).  It
  * also provides a set of methods to 'bind' a given
  * hostname, port, or IP address together, and returns
  * a JXTA server socket that can be used to service clients
  * that communicate with this endpoint.
  *
  * Implementation Notes:
  *
  * The IP address we generate is created from a random number
  * generator, using no seed.  We use all four bytes.  If the localhost
  * or "any" interfaces are requested, we generate a random IP address
  * and then cache the value so that the same local IP address is always
  * returned for this session (though it will clear out if the JVM is 
  * restarted).  
  *
  * This class has been extensively refactored to port to JXTA 2.3.1.  We used
  * to have an application peer group nested inside the net peer group; inside
  * of this group we would create another peer group for each host name.
  * When I tried to port P2P Sockets to JXTA 2.3.1 I ran into all sorts of
  * strange problems that I could not solve, so to get around them I decided
  * to drastically simplify how P2P Sockets works.  Now we only have one
  * peer group, the net peer group, that we publish all of our JXTA Server
  * Socket ads into.  We set the Name tag for all of these ads as follows:
  * 
  * appName/IPAddress/hostName:port
  * 
  * For example:
  * Paper Airplane/33.66.23.88/www.nike.laborpolicy:80
  *
  * Threading: this class is threadsafe because it is both static
  * and has no private variables.
  *
  * @author Brad GNUberg, bkn3@columbia.edu
  * @version 0.5
  * @todo The following are not currently supported: Multicast, UDP sockets, link local and site local addresses.
  * @todo This code is not checking against the installed security manager.
  * @todo Is not working if the peer itself is a Net Peer Group rendezvous.
  * @todo Think about making the resolve() and bind() methods package-private.  Also think about making some
  * other methods package-private that are only used within the package itself.
  * @todo Get the bind methods to throw an exception if a given port is already taken.  Also create a version
  * of the bind methods that forces a port to be taken even if it is already taken (we may need to modify the
  * constructor in P2PServerSocket to take this flag).
  * @todo Add methods to change the PeerServiceSuffix and PeerServicePrefix.
  */
public class P2PNameService {
    private static final Logger log = Logger.getLogger("org.p2psockets.P2PNameService");
	/** To make it easy to differentiate peer services from peer
	  * group services, all peer domain names must end with this ending. */
	public static final String PEER_SERVICE_SUFFIX = ".peer";

	/** This is an optional prefix, such as "www.", that will be added
	  * to automatically generated peer domain names, such as "www.bradgnuberg.peer". */
	public static final String PEER_SERVICE_PREFIX = "www.";

	/** The field that holds the domain name. */
	public static final String NAME_FIELD = "Name";

	/** Discovery service timeout when searching for advertisements. */
	private static final int TIMEOUT = 2000; 

	/** Maximum number of attempts at discovery when searching for
	  * advertisements. */
	private static final int MAX_ADVERTISEMENT_DISCOVERY_ATTEMPTS = 3; 

	/** Maximum number of attempts when attempting to discover an unused
	  * port. */
	private static final int MAX_PORT_DISCOVERY_ATTEMPTS = 5;

	/** Creates and binds the given P2PSocketAddress to run in the 
	  * PeerGroup 'hostParentPeerGroup'.  This creates and starts the 
	  * server socket that will host requests for the given host and port.
	  * If the port is 0, we search several
	  * times to find an unused port address above 1024
	  * If the host is "localhost", then this server socket
	  * is simply started on the local host with a host name automatically
	  * generated from the peer name, with an ending of ".peer".
	  * If 'endpoint' is null than we just assume the server socket
	  * will run on the local machine as a peer-service.
	  */
	public static JxtaServerSocket bind(P2PSocketAddress endpoint, PeerGroup hostParentPeerGroup) throws IOException {
	  try {	
		if (endpoint == null) {
			endpoint = new P2PSocketAddress(P2PInetAddress.getLocalHost(), 0);
		}

		InetAddress inetAddress = endpoint.getAddress();
		int port = endpoint.getPort();
		byte[] ipAddress = inetAddress.getAddress();
		String host = inetAddress.getHostName();

		// is the inetAddress null, a loopback address, a wildcard address?
		if (inetAddress == null ||
			inetAddress.isLoopbackAddress() ||
			inetAddress.isAnyLocalAddress()) {
			host = P2PInetAddress.getLocalHostName();
                        ipAddress = P2PInetAddress.getLocalHostAddress();
		}
		// is both the domain name and the IP address on the inetAddress object null?
		else if (inetAddress.getHostName() == null &&
				 inetAddress.getAddress() == null) {
			host = P2PInetAddress.getLocalHost().getHostName();
                        
		}
		// else does the inetAddress have a domain name?
		else if (inetAddress.getHostName() != null) {
			// extract the domain name
			host = inetAddress.getHostName();
		}
			
        // if we still have no ip address then generate one from the host name
        if (inetAddress == null && host != null)
            inetAddress = P2PInetAddress.getByAddress(host, null);
        
		// modify the endpoint that was given so that it holds the correct modified
		// information
		endpoint.setHostName(host);

		// modify the endpoint so that it holds the correct port number
		endpoint.setPort(port);
        
        P2PAdvertisementName advName = new P2PAdvertisementName(host, 
                                                                P2PInetAddress.getIPString(ipAddress), 
                                                                port);
        //jxl start
        
		PipeAdvertisement serverSocketAd = createPipeAdvertisement(advName.toString(false), 
                                                                hostParentPeerGroup);
                
		// create the server side of the jxta socket
		JxtaServerSocket serverSide = new JxtaServerSocket(hostParentPeerGroup, serverSocketAd, 50, 0);
                P2PSocketLeaseManager manager = new P2PSocketLeaseManager(hostParentPeerGroup, serverSocketAd, serverSide);
                manager.start();
        
		// strange problems porting to JXTA 2.2 where setting this below doesn't seem to help;
		// we have to do it in the new JxtaServerSocket right above.  If we don't then the socket
		// closes after 60 seconds if it doesn't get a connection right away.
		// 0 represents an infinite timeout. -- Brad Neuberg, bkn3@columbia.edu
		//serverSide.setSoTimeout(30000);
                
                //jxl end

		return serverSide;
	  }
      catch (Exception e) {
		log.error("Exception resolving address "+endpoint , e);
	    throw new IOException(e.toString());
	  }
	}

	/** Resolves the given remote socket address in the given peer group to
	  * a socket that can be used to communicate with this service.
	  */
	public static JxtaSocket resolve(P2PSocketAddress endpoint, PeerGroup hostParentPeerGroup)
					throws IOException {
		try {
			if (endpoint == null)
				throw new IllegalArgumentException("You must provide an endpoint to resolve.");

			InetAddress inetAddress = endpoint.getAddress();
			int port = endpoint.getPort();
			String ipAddress = inetAddress.getHostAddress();
			String host = inetAddress.getHostName();

			// is the inetAddress null, a loopback address, a wildcard address?
			if (inetAddress == null ||
				inetAddress.isLoopbackAddress() ||
				inetAddress.isAnyLocalAddress()) {
				host = P2PInetAddress.getLocalHost().getHostName();
			}
			// is both the domain name and the IP address on the inetAddress object null?
			else if (inetAddress.getHostName() == null &&
					 inetAddress.getAddress() == null) {
				host = P2PInetAddress.getLocalHost().getHostName();
			}
			// else does the inetAddress have a domain name?
			else if (inetAddress.getHostName() != null) {
				// extract the domain name
				host = inetAddress.getHostName();
			}
			else if (inetAddress.getHostName() == null &&
					 inetAddress.getAddress() != null) {
				// search for the host name
				host = findHostName(ipAddress, hostParentPeerGroup);
			} 
	
			// modify the endpoint that was given so that it holds the correct modified
			// information
			endpoint.setHostName(host);

			return resolve(host, port, hostParentPeerGroup);
		}
		catch (Exception e) {
			log.error("Exception resolving "+endpoint,e);
			throw new IOException(e.toString());
		}
	}

	/** Resolves the given host and port name in the given peer group to a 
	  * socket that can be used to communicate with this service. 
	  */
        
	public static JxtaSocket resolve(String host, int port,final PeerGroup hostParentPeerGroup) throws IOException {
		try {	
			if (port < 0 || port > 65535)
				throw new IllegalArgumentException("The port value is out of range: " + port);
            String address = P2PInetAddress.getByAddress(host, null).getHostAddress();
            final P2PAdvertisementName advName = new P2PAdvertisementName(host, address, port);
            
            
            //jxl start
            final long timeout = System.currentTimeMillis()+60*1000;//sixty secconds
            
            final Collection returnSocketHolder = new ArrayList();
            final Collection resolverThreadHolder = new ArrayList();
            final Collection exceptionHolder = new ArrayList();
            
            final Collection advResIdsInProgressHolder = new HashSet();
            
            
            final DiscoveryService disc = hostParentPeerGroup.getDiscoveryService();
            
            while(System.currentTimeMillis() < timeout){
                disc.getRemoteAdvertisements(null,DiscoveryService.ADV,
                    PipeAdvertisement.NameTag,advName.toString(true),Integer.MAX_VALUE);

                Enumeration locals = disc.getLocalAdvertisements(DiscoveryService.ADV,
                    PipeAdvertisement.NameTag, advName.toString(true));

                while(locals.hasMoreElements()){
                    final PipeAdvertisement localPipeAdv = (PipeAdvertisement)locals.nextElement();
                    
                    if(!advResIdsInProgressHolder.contains(localPipeAdv.getID())){
                        advResIdsInProgressHolder.add(localPipeAdv.getID());
                        
                        
                        Thread t = new Thread(){
                            public void run(){
                                try{
                                    returnSocketHolder.add(new JxtaSocket(hostParentPeerGroup,
                                                           null,//no specific peerid
                                                           localPipeAdv,60000,//general TO: 60 seconds
                                                           true));// reliable connection

                                }catch(Exception e){
                                    if(exceptionHolder.isEmpty())
                                        exceptionHolder.add(e);
                                }
                                resolverThreadHolder.remove(this);//this thread
                            }
                        };
                        resolverThreadHolder.add(t);
                        t.start();
                    }
                }
                if(!returnSocketHolder.isEmpty()){
                    break;
                }else{
                    Thread.sleep(500);
                }
            }
            
            
            if(!returnSocketHolder.isEmpty()){
                return (JxtaSocket)returnSocketHolder.iterator().next();
            }else{
                while(exceptionHolder.isEmpty() && !advResIdsInProgressHolder.isEmpty()){
                    Thread.sleep(500);
                }
                if(!exceptionHolder.isEmpty()){
                    throw (Exception)exceptionHolder.iterator().next();
                }else{
                    throw new IOException("No advertiesments were found for "+host+":"+port+". This probably means there is no P2PServerSocket bound on that host/port.");
                }
            }
                        
                }catch(IOException ioe){
                    throw ioe;
           //jxl end
		}catch (Exception e) {
			log.error("Exception resolving "+host+":"+port,e);
			throw new IOException(e.toString());
		}
	}

	/** Resolves the given IP address and port name in the given peer group to a 
	  * socket that can be used to communicate with this service. 
	  */
	public static JxtaSocket resolve(byte[] ipAddress, int port, PeerGroup hostParentPeerGroup) throws IOException {
		String ipString = P2PInetAddress.getByAddress(ipAddress).getHostAddress();

		// if the IP address is for the local host, then simply resolve based
		// on the generated host name of the local host
		if (ipString.equals("127.0.0.1") || ipString.equals("0.0.0.0")) {
			return resolve("localhost", port, hostParentPeerGroup);
		}
		else {
			return resolve(P2PInetAddress.getByAddress(ipAddress).getHostName(), 
                           port, hostParentPeerGroup);
		}	
	}

	/** This method will search on the network to find an
	  * advertisement for the given host and discover the IP
	  * address for that host.  If none is found than null
	  * is returned.  If 'host' is null or has the value 'localhost'
	  * than the local host name is generated and an IP address
	  * is returned for that.
	  */
	public static byte[] findIPAddress(String host) 
						throws IOException, UnknownHostException {
		return findIPAddress(host, P2PNetwork.getPeerGroup());
	}

	/** This method will search on the network to find an
	  * advertisement for the given host and discover the IP
	  * address for that host.  If none is found than null
	  * is returned.  If 'host' is null or has the value 'localhost'
	  * than the local host name is generated and an IP address
	  * is returned for that.
	  */
	public static byte[] findIPAddress(String host, PeerGroup searchInPeerGroup) 
						throws IOException, UnknownHostException {
		InetAddress addr = P2PInetAddress.getByAddress(host, null);
        
        if (addr == null)
            return null;
        
        return addr.getAddress();
	}

	/** This method will search on the network to find an
	  * advertisement for the given IP address and discover the host
	  * name for that IP address.  If none is found than null
	  * is returned. 
	  */
	public static String findHostName(String ipAddress) 
						throws IOException, UnknownHostException {
		return findHostName(ipAddress, P2PNetwork.getPeerGroup());
	}

	/** This method will search on the network to find an
	  * advertisement for the given IP address and discover the host
	  * name for that IP address.  If none is found than null
	  * is returned. 
	  */
	public static String findHostName(String ipAddress, PeerGroup searchInPeerGroup) 
						throws IOException, UnknownHostException {
        P2PAdvertisementName advName = new P2PAdvertisementName(null, ipAddress, -1);
		PipeAdvertisement endpointAd = 
            searchForPipeAdvertisement(advName.toString(true), searchInPeerGroup);
		if (endpointAd != null) {
            P2PAdvertisementName nameField = 
                new P2PAdvertisementName(endpointAd.getName());
            return nameField.getHostName();
		}
		else {
			return null;
		}
	}


	/** Determines if the given host name is already being used on
	  * the peer network. */
	public static boolean isHostNameTaken(String hostName) throws IOException {
		InetAddress inetAddr = P2PInetAddress.getByAddress(hostName, null);
		hostName = inetAddr.getHostName();

		PeerGroup searchInPeerGroup = P2PNetwork.getPeerGroup();
        String address = P2PInetAddress.getByAddress(hostName, null).getHostAddress();
        P2PAdvertisementName advName = new P2PAdvertisementName(hostName, address,
                                                                -1);
		PipeAdvertisement endpointAd = 
            searchForPipeAdvertisement(advName.toString(true), searchInPeerGroup);
		return endpointAd != null;
	}

	/** Determines if the given port for the given host name is already being
	  * used in the peer network.
	  */
	public static boolean isPortTaken(String hostName, int port)
			throws IOException {
		InetAddress inetAddr = P2PInetAddress.getByAddress(hostName, null);
		hostName = inetAddr.getHostName();

		PeerGroup searchInPeerGroup = P2PNetwork.getPeerGroup();
        String address = P2PInetAddress.getByAddress(hostName, null).getHostAddress();
        P2PAdvertisementName advName = new P2PAdvertisementName(hostName, address,
                                                                port);
		PipeAdvertisement portPipe = 
            searchForPipeAdvertisement(advName.toString(true), searchInPeerGroup);
		
		return portPipe != null;
	}

	/** Determines if the given IP address is already taken.
	  */
	public static boolean isIPAddressTaken(String ipAddress) throws IOException {
		InetAddress inetAddr = P2PInetAddress.getByAddress(ipAddress, null);
		ipAddress = inetAddr.getHostAddress();

		PeerGroup searchInPeerGroup = P2PNetwork.getPeerGroup();
        P2PAdvertisementName advName = new P2PAdvertisementName(null, ipAddress, -1);
		PipeAdvertisement pipeAd = searchForPipeAdvertisement(advName.toString(true), 
                                                              searchInPeerGroup);

		return pipeAd != null;
	}

	

	protected static PipeAdvertisement createAndPublishPipe(String advName,
                                                            PeerGroup startServerInGroup) 
                                                                    throws Exception {
		PipeAdvertisement pipeAd = createPipeAdvertisement(advName, 
                                                           startServerInGroup);	

		// publish the pipe advertisement
		DiscoveryService parentDiscovery = startServerInGroup.getDiscoveryService();
		parentDiscovery.publish(pipeAd);
		parentDiscovery.remotePublish(pipeAd);

		return pipeAd;
	}

	protected static PipeAdvertisement createPipeAdvertisement(String advName,
                                                               PeerGroup parentPeerGroup) 
                            throws Exception {
		PipeID serverSocketID = getPipeID(advName, parentPeerGroup);

		PipeAdvertisement serverSocketAd = (PipeAdvertisement)AdvertisementFactory.newAdvertisement(
												PipeAdvertisement.getAdvertisementType());
		serverSocketAd.setPipeID(serverSocketID);
		serverSocketAd.setName(advName);
		serverSocketAd.setType(PipeService.UnicastType);

		return serverSocketAd;
	}
    
    protected static PipeAdvertisement searchForPipeAdvertisement(String advName,
                                                                  PeerGroup parentPeerGroup) {
        // first find the advertisement with this value
		DiscoveryService netDiscovery = parentPeerGroup.getDiscoveryService();
		Enumeration ae = null;
		PipeAdvertisement findMeAdv = null;	
		int count = MAX_ADVERTISEMENT_DISCOVERY_ATTEMPTS;

		// Loop until we discover the ad we are looking for or until we've exhausted the number of
		// attempts
		while (count-- > 0) {
			try {
				ae = netDiscovery.getLocalAdvertisements(DiscoveryService.ADV, 
                                                         NAME_FIELD, advName);
				
				// if we found the advertisement, we are done
				if ((ae != null) && ae.hasMoreElements())
					break;		

				// if we did not find it, send a discovery request
				netDiscovery.getRemoteAdvertisements(null, DiscoveryService.ADV,
								           NAME_FIELD, advName, 1, null);

				// Sleep to allow time for peers to respond to the discovery request
				try {
					Thread.sleep(TIMEOUT);
				}
				catch (InterruptedException ie) {
                                    log.error("Interrupted while spleeping", ie);
                                }
			}
			catch (IOException e) {
				// Found nothing! move on.
			}
		}

		if ((ae != null) && (ae.hasMoreElements())) {
			findMeAdv = (PipeAdvertisement)ae.nextElement();
		}

        return findMeAdv;
	}

	protected static PipeID getPipeID(String advName, PeerGroup parentPeerGroup) throws Exception {
	  byte[] digest = generateHash(advName, null);
	  PeerGroupID parentGroupID = parentPeerGroup.getPeerGroupID();
	  //causes problems if parentGroupID is the Net Peer Group
	  //return new net.jxta.impl.id.UUID.PipeID( (net.jxta.impl.id.UUID.PeerGroupID)parentGroupID, digest);
	  return IDFactory.newPipeID(parentGroupID, digest);
    }

	/** If we create a new P2P server peer group or publish a port ad into
	  * the group then we automatically become a rendezvous server. */
	protected static void becomeRendezvous(PeerGroup inGroup) throws Exception {
		if (inGroup.isRendezvous() == false) {
			RendezVousService rendezvous = inGroup.getRendezVousService();
			rendezvous.startRendezVous();
		}
	}

    protected static byte[] generateHash(String clearTextID, String function) {
 	  String id;
        
	  if (function == null) {
		id = clearTextID;
	  } else {
		id = clearTextID + ":" + function;
	  }
	  byte[] buffer = id.getBytes();
        
	  MessageDigest algorithm = null;
        
	  try {
		algorithm = MessageDigest.getInstance("MD5");
	  } catch (Exception e) {
	  	  log.error("Cannot load MD5 Digest Hash implementation",  e);
		  return null;
        }
       
	  // Generate the digest.
	  algorithm.reset();
	  algorithm.update(buffer);
        
 	  try{
  		byte[] digest1 = algorithm.digest(); 
		return digest1;
	  }catch(Exception de){
		log.error("Failed to creat a digest.", de);
           		return null;
      }
    }  
}