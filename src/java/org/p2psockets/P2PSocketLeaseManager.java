/*
 * P2PSocketLeaseManager.java
 *
 * Created on March 13, 2005, 2:22 PM
 */

package org.p2psockets;

import java.lang.ref.WeakReference;
import net.jxta.discovery.DiscoveryService;
import net.jxta.document.Advertisement;
import net.jxta.peergroup.PeerGroup;
import net.jxta.protocol.PipeAdvertisement;

/**
 * Helper class to manage the publication of a jxta Advertisement. LeaseManger will 
 * publish a supplied Advertisement at a regular intervole. This allows the Advertisement (and the service it describres) to 
 * have a small lifetime, so that it will disapear quickly when it is gone.
 * @author Alex Lynch
 */
public final class P2PSocketLeaseManager implements Runnable{
    /**
     * The minimum lease time that may be given the the P2PSocketLeaseManager
     */
    
    private final PeerGroup group;
    private final DiscoveryService disc;
    private final PipeAdvertisement adv;
    private final Thread thread;
    private final WeakReference reference;
    
    private boolean running = false;
    
    /**
     * Creates a new instance of P2PSocketLeaseManager. <CODE>referent</CODE> will be stored 
     * internally by weak reference. When the weak reference is cleared (meaning 
     * <CODE>referent</CODE> has been cleared by the gc) The P2PSocketLeaseManager will stop
     * publishing the advertisement and its thread will exit. Thus best practice
     * is to supply a reference to the service that <CODE>adv</CODE> represents 
     * as the referent.
     * @param group The PeerGroup to publish the <CODE>adv</CODE> in.
     * @param adv The Advertisement to publish
     * @param referent Object to reference.
     */
    public P2PSocketLeaseManager(PeerGroup group,PipeAdvertisement adv,Object referent) {
        this.group = group;
        this.disc = group.getDiscoveryService();
        this.adv = adv;
        this.reference = new WeakReference(referent);
        thread = new Thread(this);
    }
    
    /**
     * Stop the publication thread. It should be possible to restart the thread after a
     * stop but that will depend on the state of the thread.
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
        final long pTime = 10L*60*1000;
        while(running && (reference.get() != null)){
            try{
//                System.out.println("Publishing managed adv");
                adv.setDescription(System.currentTimeMillis()+"");//publication date
                disc.publish(adv,pTime,pTime);
                disc.remotePublish(adv,pTime);
                Thread.sleep(120*1000);
            }catch(Exception ie){
                ie.printStackTrace();
            }
        }
//        System.out.println("Stop Publishing managed adv!!..!!");
    }
    
}
