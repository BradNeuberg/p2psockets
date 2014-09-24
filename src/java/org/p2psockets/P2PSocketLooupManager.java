/*
 * P2PSocketLooupManager.java
 *
 * Created on March 13, 2005, 2:29 PM
 */

package org.p2psockets;

import java.io.IOException;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import net.jxta.discovery.DiscoveryService;
import net.jxta.peergroup.PeerGroup;
import net.jxta.protocol.PipeAdvertisement;

/**
 * This class implements an active Advertisement hunter. It caches advs local so 
 * that they can be quickly retrieved a connection time.
 * @author Alex Lynch
 */
public final class P2PSocketLooupManager implements Runnable{
    
    private final DiscoveryService disc;
    private final String searchString;
    private final Thread thread;
    
    private boolean running = false;
    /**
     * Creates a new instance of P2PSocketLooupManager
     * @param group The peer group to search in
     * @param networkName The networkName space to seach in.
     */
    public P2PSocketLooupManager(PeerGroup group, String networkName) {
        disc = group.getDiscoveryService();
        thread = new Thread(this);
        searchString = networkName+"*";
    }
    
    /**
     * Stop the thread
     */
    public void stop(){
        running = false;
    }
    /**
     * Start the thread
     */
    public void start(){
        running = true;
        thread.start();
    }
    
    public void run(){
        while(running){
            try{
                disc.getRemoteAdvertisements(null,DiscoveryService.ADV,PipeAdvertisement.NameTag,searchString,
                        Integer.MAX_VALUE);
                Thread.sleep(7*1000);
                
                //I want to use paramaterization!!!!
                HashMap advMap = new HashMap();
                try{
                    Enumeration e = getAdvertisements();
                    while(e.hasMoreElements()){
                        PipeAdvertisement adv = (PipeAdvertisement)e.nextElement();
                        
                        //if the advertisement is not four minutes old.
                        if(Long.parseLong(adv.getDescription()) > System.currentTimeMillis()- 4*60*1000){
                            PipeAdvertisement other = (PipeAdvertisement)advMap.get(adv.getID());
                            if(other == null){
                                advMap.put(adv.getID(),adv);
                                disc.publish(adv);//we do this becuase an adv with the same id may have been flushed
                            }else{
                                Date otherDate = new Date(Long.parseLong(other.getDescription()));
                                Date currentDate = new Date(Long.parseLong(adv.getDescription()));
                                if(currentDate.before(otherDate)){
                                    //clean out the old adv, add the new.
                                    advMap.put(adv.getID(), adv);
                                    disc.flushAdvertisement(other);
                                    disc.publish(adv);
                                }
                            }
                        }else{
                            disc.flushAdvertisement(adv);
                        }
                    }
                }catch(IOException ioe){
                    System.err.println("Exception while processing adv cache. We'll continue any way...");
                    ioe.printStackTrace();
                }
            }catch(InterruptedException ie){
                ie.printStackTrace();
            }
        }
    }
    
    
    /**
     * Helper method to get the cached advs
     * @throws java.io.IOException 
     * @return An Enumeration of PipeAdvertisements representing the active server nodes on the
     * network. This comes straight from the jxta DiscoveryServer
     */
    public Enumeration getAdvertisements() throws IOException{
        return disc.getLocalAdvertisements(DiscoveryService.ADV, PipeAdvertisement.NameTag, searchString);
    }
}
