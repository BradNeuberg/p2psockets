/*
 *  $Id: JxtaSocket.java,v 1.4 2005/08/25 16:45:53 jxlynch Exp $
 *
 *  Copyright (c) 2001 Sun Microsystems, Inc.  All rights reserved.
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
 *  4. The names "Sun", "Sun Microsystems, Inc.", "JXTA" and "Project JXTA" must
 *  not be used to endorse or promote products derived from this
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
 *  DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
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
 */
package org.p2psockets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImplFactory;
import java.util.Collections;
import java.util.Iterator;

import net.jxta.credential.Credential;
import net.jxta.document.AdvertisementFactory;
import net.jxta.document.Document;
import net.jxta.document.MimeMediaType;
import net.jxta.document.StructuredDocument;
import net.jxta.document.StructuredDocumentFactory;
import net.jxta.endpoint.ByteArrayMessageElement;
import net.jxta.endpoint.EndpointAddress;
import net.jxta.endpoint.EndpointService;
import net.jxta.endpoint.InputStreamMessageElement;
import net.jxta.endpoint.StringMessageElement;
import net.jxta.endpoint.Message;
import net.jxta.endpoint.MessageElement;
import net.jxta.endpoint.Messenger;
import net.jxta.id.ID;
import net.jxta.peer.PeerID;
import net.jxta.impl.util.UnbiasedQueue;
import net.jxta.impl.util.pipe.reliable.OutgoingMsgrAdaptor;
import net.jxta.impl.util.pipe.reliable.ReliableInputStream;
import net.jxta.impl.util.pipe.reliable.ReliableOutputStream;
import net.jxta.impl.util.pipe.reliable.FixedFlowControl;
import net.jxta.impl.util.pipe.reliable.Defs;
import net.jxta.membership.MembershipService;
import net.jxta.peergroup.PeerGroup;
import net.jxta.pipe.InputPipe;
import net.jxta.pipe.OutputPipe;
import net.jxta.pipe.OutputPipeEvent;
import net.jxta.pipe.OutputPipeListener;
import net.jxta.pipe.PipeMsgEvent;
import net.jxta.pipe.PipeMsgListener;
import net.jxta.pipe.PipeService;
import net.jxta.protocol.PeerAdvertisement;
import net.jxta.protocol.PipeAdvertisement;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import net.jxta.socket.*;


/**
 *  JxtaSocket is a bi-directional Pipe, that behaves very much like a
 *  Socket, it creates an InputPipe and listens for pipe connection request.
 *  JxtaSocket defines its own protocol. requests arrive as a JXTA Message
 *  with the following elements :
 *
 * <p>
 *  &lt;Cred> Credentials which can be used to determine trust &lt;/Cred>
 * <p>
 *  &lt;reqPipe> requestor's pipe advertisement &lt;/reqPipe>
 * <p>
 *  &lt;remPipe> Remote pipe advertisement &lt;/remPipe>
 * <p>
 *  &lt;reqPeer> Remote peer advertisement &lt;/remPeer>
 * <p>
 *  &lt;stream> determine whether the connection is reliable, or not &lt;/stream>
 * <p>
 *  &lt;close> close request &lt;/close>
 * <p>
 *  &lt;data> Data &lt;/data>
 * <p>
 *
 */
public class JxtaSocket extends Socket implements PipeMsgListener, OutputPipeListener {
    private final static Logger LOG = Logger.getLogger(JxtaSocket.class.getName());
    protected PeerGroup group;
    protected PipeAdvertisement pipeAdv;
    protected PipeAdvertisement myPipeAdv;
    protected PipeService pipeSvc;
    protected PeerID peerid;
    protected InputPipe in;
    protected OutputPipe connectOutpipe;
    protected Messenger msgr;
    protected InputStream stream;
    protected int timeout = 60000;
    protected int windowSize = 20;
    protected final String closeLock  = new String("closeLock");
    protected final String acceptLock = new String("acceptLock");
    protected final String instrLock  = new String("instrLock");
    protected final String finalLock  = new String("finalLock");
    protected boolean closed = false;
    protected boolean bound = false;
    protected final UnbiasedQueue queue = UnbiasedQueue.synchronizedQueue(new UnbiasedQueue(100, false));
    protected Credential credential = null;
    protected StructuredDocument credentialDoc = null;
    protected StructuredDocument myCredentialDoc = null;
    // reliable is the default mode of operation
    protected boolean isStream = true;
    protected OutgoingMsgrAdaptor outgoing = null;
    protected ReliableInputStream ris = null;
    protected ReliableOutputStream ros = null;
    protected boolean waiting;
    private int outputBufferSize = 16384;
    private boolean osCreated = false;
    private InputStream currentMsgStream = null;
    
    /**
     *  Constructor for the JxtaSocket, this constructor does not establish a connection
     *  use this constructor when altering the default parameters, and options of the socket
     *  by default connections are unreliable, and the default timeout is 60 seconds
     *  to alter a connection a call to create(true) changes the connection to a reliable one.
     */
    public JxtaSocket() { }
    
    
    /**
     *  Constructor for the JxtaSocket, this constructor does not establish a connection
     *  use this constructor when altering the default parameters, and options of the socket
     *  by default connections are unreliable, and the default timeout is 60 seconds
     *  to alter a connection a call to create(true) changes the connection to a reliable one.
     *
     *
     *@param  group            group context
     *@param  msgr             lightweight output pipe
     *@param  pipe             PipeAdvertisement
     *@param  credDoc          remote node's crendetial StructuredDocument
     *@exception  IOException  if an io error occurs
     */
    protected JxtaSocket(PeerGroup group,
            Messenger msgr,
            PipeAdvertisement pipe,
            StructuredDocument credDoc,
            boolean isStream) throws IOException {
        
        if (msgr == null ) {
            throw new IOException("Null Messenger");
        }
        this.group = group;
        this.pipeAdv = pipe;
        this.credentialDoc = credDoc;
        this.pipeSvc = group.getPipeService();
        this.in = pipeSvc.createInputPipe(pipe, this);
        this.msgr = msgr;
        this.isStream = isStream;
        
        if (isStream) {
            // Force the creation of the inputStream now. So that we do not
            // need a queue, which only benefit would be to store messages
            // until someone calls getInputStream.
            createRis();
            // FIXME: need to delete the input pipe if createRis failed.
            // can it fail ?
        }
        setBound();
    }
    
    
    /**
     *  Create a JxtaSocket to any peer listening on pipeAdv
     *
     *@param  group            group context
     *@param  pipeAd             PipeAdvertisement
     *@exception  IOException  if an io error occurs
     */
    public JxtaSocket(PeerGroup group,
            PipeAdvertisement pipeAd) throws IOException {
        
        this.group = group;
        this.pipeAdv = pipeAd;
        connect(group, pipeAd);
    }
    
    /**
     *  Create a JxtaSocket to any peer listening on pipeAdv
     *  this attempts establish a connection to specified
     *  pipe within the context of the specified group within
     *  timeout specified in milliseconds
     *
     *@param  group            group context
     *@param  pipeAd           PipeAdvertisement
     *@param  timeout          JxtaSocket timeout in milliseconds
     *@exception  IOException  if an io error occurs
     */
    public JxtaSocket(PeerGroup group,
            PipeAdvertisement pipeAd,
            int timeout) throws IOException {
        
        this.group = group;
        this.pipeAdv = pipeAd;
        this.timeout = timeout;
        connect(group, pipeAd, timeout);
    }
    
    /**
     *  Create a JxtaSocket to any peer listening on pipeAdv
     *  this attempts establish a connection to specified
     *  pipe within a context of group and within timeout specified in milliseconds
     *
     *@param  group            group context
     *@param  peerid           peer to connect to
     *@param  pipeAd           PipeAdvertisement
     *@param  timeout          JxtaSocket timeout in milliseconds
     *@exception  IOException  if an io error occurs
     */
    public JxtaSocket(PeerGroup group,
            PeerID peerid,
            PipeAdvertisement pipeAd,
            int timeout) throws IOException {
        
        this.group = group;
        this.pipeAdv = pipeAd;
        this.timeout = timeout;
        connect(group, peerid, pipeAd, timeout);
    }
    
    /**
     *  Create a JxtaSocket to any peer listening on pipeAdv
     *  this attempts establish a connection to specified
     *  pipe within a context of group and within timeout specified in milliseconds
     *
     *@param  group            group context
     *@param  peerid           peer to connect to
     *@param  pipeAd             PipeAdvertisement
     *@param  timeout          JxtaSocket timeout in milliseconds
     *@exception  IOException  if an io error occurs
     */
    public JxtaSocket(PeerGroup group,
            PeerID peerid,
            PipeAdvertisement pipeAd,
            int timeout,
            boolean stream) throws IOException {
        
        this.group = group;
        this.pipeAdv = pipeAd;
        this.timeout = timeout;
        this.isStream = stream;
        
        connect(group, peerid, pipeAd, timeout);
    }
    /**
     *  Creates either a stream or a datagram socket. default is a datagram
     *
     *@param  stream           if <code>true</code>, create a stream socket;
     *      otherwise, create a datagram socket.
     *@exception  IOException  if an I/O error occurs while creating the
     *      socket.
     */
    public void create(boolean stream) throws IOException {
        if (isBound()) {
            throw new IOException("Socket already connected, it is not possible to change connection type");
        }
        this.isStream = stream;
    }
    
    /**
     * Unsupported operation, an IOException will be thrown
     */
    public void bind(SocketAddress address) throws IOException {
        throw new IOException("Unsupported operation, use java.net.Socket instead");
    }
    
    /**
     * Unsupported operation, an IOException will be thrown
     */
    public void connect(SocketAddress address) throws IOException {
        throw new IOException("Unsupported operation, use java.net.Socket instead");
    }
    
    /**
     * Unsupported operation, an IOException will be thrown
     */
    public void connect(SocketAddress address, int i) throws IOException {
        throw new IOException("Unsupported operation, use java.net.Socket instead");
    }
    
    /**
     *  Connects to a remote JxtaSocket on any peer within the default timeout of 60 seconds
     *
     *@param  group            group context
     *@param  pipeAd           PipeAdvertisement
     *@exception  IOException  if an io error occurs
     */
    public void connect(PeerGroup group, PipeAdvertisement pipeAd) throws IOException {
        connect(group, pipeAd, timeout);
    }
    
    /**
     *  Connects to a remote JxtaSocket on any peer within a timeout specified in milliseconds
     *
     *@param  group            group context
     *@param  pipeAd             PipeAdvertisement
     *@exception  IOException  if an io error occurs
     */
    public void connect(PeerGroup group, PipeAdvertisement pipeAd, int timeout) throws IOException {
        connect(group, null, pipeAd, timeout);
    }
    
    /**
     *  Connects to a remote JxtaSocket on a specific peer within a timeout specified in milliseconds
     *
     *@param  group            group context
     *@param  peerid           peer to connect to
     *@param  pipeAd             PipeAdvertisement
     *@param  timeout          timeout in milliseconds
     *@exception  IOException  if an io error occurs
     */
    public void connect(PeerGroup group, PeerID peerid, PipeAdvertisement pipeAd, int timeout) throws IOException {
        if (pipeAd.getType() != null && pipeAd.getType().equals(PipeService.PropagateType)) {
            throw new IOException("Propagate pipe advertisements are not supported");
        }
        
        this.group = group;
        this.pipeAdv = pipeAd;
        this.timeout = timeout;
        pipeSvc = group.getPipeService();
        myPipeAdv = JxtaServerSocket.newInputPipe(group, pipeAd);
        this.in = pipeSvc.createInputPipe(myPipeAdv, this);
        this.peerid = peerid;
        Message openMsg = createOpenMessage(group, myPipeAdv);
        ////System.out.println("JxtaSocket attempt connect");
        // create the output pipe and send this message
        // Need to retry the call to createOutputPipe. If there is no
        // rendezvous yet and the destination is not reachable by mcast,
        // then createOutputPipe has no effect.
        // We repeat it with exponential delays.
        
        long delay = 2000;
        if (peerid == null) {
            pipeSvc.createOutputPipe(pipeAd, this);
        } else {
            pipeSvc.createOutputPipe(pipeAd, Collections.singleton(peerid), this);
        }
        while(true) {
            try {
                synchronized(acceptLock) {
                    // check connectOutpipe within lock to prevent a race with modification.
                    if (connectOutpipe != null) {
                        break;
                    }
                    
                    // If initial TO is exactly 0 we never get to here.
                    // If this delay exceeds remaining timeout, wait
                    // for the remainder.
                    if (delay >= timeout) {
                        delay = timeout;
                    }
                    
                    // We take an initial TO < 0 as meaning. No wait.
                    if (delay < 0) {
                        break;
                    }
                    try{
                        connectOutpipe = pipeSvc.createOutputPipe(pipeAd, delay);
                        ////System.out.println("Sneeky good.");
                    }catch(IOException e){
                        ////System.out.println("Sneeky no good.");
                    }
                    //acceptLock.wait(delay);
                    timeout -= delay;
                    
                    if (timeout <= 0) {
                        // If we've exhausted the initial TO, leave.
                        // So we never end-up with wait(<=0) unless the
                        // initial TO was exactly 0.
                        break;
                    }
                    
                    // Next delay will be twice as long, remaining TO
                    // permitting.
                    delay *= 2;
                }
            } catch (Exception ie) {
                if (LOG.isEnabledFor(Level.DEBUG)) {
                    LOG.debug("Interrupted", ie);
                }
            }
        }
        if (connectOutpipe == null) {
            throw new IOException("connection timeout");
        }
        
        // send connect message
        waiting = true;
//        //System.out.println("JxtaSocket sent open message ");
        while(!connectOutpipe.send(openMsg)){
            try{
                Thread.sleep(20);
            }catch(InterruptedException ie){ie.printStackTrace();}
        }
        //wait for the second op
        try {
            synchronized (finalLock) {
                if(waiting) {
                    finalLock.wait(timeout);
                }
                if (msgr == null) {
                    throw new IOException("connection timeout");
                }
            }
        } catch (InterruptedException ie) {
            if (LOG.isEnabledFor(Level.DEBUG)) {
                LOG.debug("Interrupted", ie);
            }
        }
        setBound();
    }
    
    
    /**
     *  obtain the cred doc from the group object
     *
     *@param  group  group context
     *@return        The credDoc value
     */
    protected static StructuredDocument getCredDoc(PeerGroup group) {
        try {
            MembershipService membership = group.getMembershipService();
            Credential credential = membership.getDefaultCredential();
            if (credential != null) {
                return credential.getDocument(MimeMediaType.XMLUTF8);
            }
        } catch (Exception e) {
            if (LOG.isEnabledFor(Level.WARN)) {
                LOG.warn("failed to get credential", e);
            }
        }
        return null;
    }
    /**
     *  get the remote credential doc
     *  @return Credential StructuredDocument
     */
    public StructuredDocument getCredentialDoc() {
        return credentialDoc;
    }
    
    /**
     *  Sets the connection credential doc
     *  If no credentials are set, the default group credential will be used
     *  @param doc Credential StructuredDocument
     */
    public  void setCredentialDoc(StructuredDocument doc) {
        this.myCredentialDoc = doc;
    }
    
    
    /**
     *  Create a connection request message
     *
     *@param  group   group context
     *@param  pipeAd  pipe advertisement
     *@return         the Message  object
     */
    protected Message createOpenMessage(PeerGroup group, PipeAdvertisement pipeAd) throws IOException {
        
        Message msg = new Message();
        PeerAdvertisement peerAdv = group.getPeerAdvertisement();
        if (myCredentialDoc == null) {
            myCredentialDoc = getCredDoc(group);
        }
        if (myCredentialDoc == null && pipeAd.getType().equals(PipeService.UnicastSecureType)) {
            throw new IOException("No credentials established to initiate a secure connection");
        }
        if (LOG.isEnabledFor(Level.DEBUG)) {
            LOG.debug("Requesting connection [isStream] :"+isStream);
        }
        try {
            if (myCredentialDoc != null) {
                msg.addMessageElement(JxtaServerSocket.nameSpace,
                        new InputStreamMessageElement(JxtaServerSocket.credTag,
                        MimeMediaType.XMLUTF8,
                        (InputStream) myCredentialDoc.getStream(), null));
            }
            
            msg.addMessageElement(JxtaServerSocket.nameSpace,
                    new InputStreamMessageElement(JxtaServerSocket.reqPipeTag,
                    MimeMediaType.XMLUTF8,
                    (InputStream) ((Document)
                    (pipeAd.getDocument(MimeMediaType.XMLUTF8))).getStream(), null));
            String streamStr;
            if (isStream) {
                streamStr = "true";
            } else {
                streamStr = "false";
            }
            msg.addMessageElement(JxtaServerSocket.nameSpace,
                    new StringMessageElement(JxtaServerSocket.streamTag,
                    streamStr,
                    null));
            msg.addMessageElement(JxtaServerSocket.nameSpace,
                    new InputStreamMessageElement(JxtaServerSocket.remPeerTag,
                    MimeMediaType.XMLUTF8,
                    (InputStream) ((Document)
                    (peerAdv.getDocument(MimeMediaType.XMLUTF8))).getStream(), null));
            return msg;
        } catch (Throwable t) {
            if (LOG.isEnabledFor(Level.DEBUG)) {
                LOG.debug("error getting element stream", t);
            }
            return null;
        }
    }
    
    
    /**
     *  Sets the maximum queue length for incoming connection indications (a
     *  request to connect) to the <code>count</code> argument. If a
     *  connection indication arrives when the queue is full, the connection
     *  is refused.
     *
     *@param  backlog          the maximum length of the queue.
     *@exception  IOException  if an I/O error occurs when creating the
     *      queue.
     */
    public void listen(int backlog) throws IOException {
        queue.setMaxQueueSize(backlog);
    }
    
    /**
     *  Sets the bound attribute of the JxtaServerSocket object
     */
    private void setBound() {
        bound = true;
    }
    
    /**
     * Returns the binding state of the JxtaServerSocket.
     *
     * @return    true if the ServerSocket successfully bound to an address
     */
    public boolean isBound() {
        return bound;
    }
    
    // Internal routine. Assumes all the necessary conditions are
    // fullfilled. Called only by code that knows what it's doing.
    
    private void createRis() {
        
        if(outgoing == null) {
            outgoing = new OutgoingMsgrAdaptor(msgr, timeout);
        }
        if (ris == null) {
            ris = new ReliableInputStream(outgoing, timeout);
        }
    }
    
    /**
     *  Returns the OutputStream buffer size
     *
     *@return           returns output buffer size
     */
    public synchronized int getOutputStreamBufferSize() {
        return outputBufferSize;
    }
    
    /**
     *  Sets the OutputStream buffer size
     *  this operation is only valid prior to any call to getOutputStream
     *
     */
    public synchronized void setOutputStreamBufferSize(int size) throws IOException {
        if (size < 1) {
            throw new IllegalArgumentException("negative/zero buffer size");
        }
        if (osCreated) {
            throw new IOException("Can not reset buffersize, OutputStream is already created");
        }
        outputBufferSize = size;
    }
    
    /**
     *  Returns an input stream for this socket.
     *
     *@return                  a stream for reading from this socket.
     *@exception  IOException  if an I/O error occurs when creating the
     *      input stream.
     */
    public InputStream getInputStream() throws IOException {
        checkState();
        if (isStream) {
            
            if(outgoing == null) {
                outgoing = new OutgoingMsgrAdaptor(msgr, timeout);
            }
            
            if (ris == null) {
                // isBound should already have returned false, so this is
                // only a bug detection feature.
                throw new IOException("Reliable stream not initialized");
            }
        }
        return new JxtaSocketInputStream(this);
    }
    
    /**
     *  Returns an output stream for this socket.
     *
     *@return                  an output stream for writing to this socket.
     *@exception  IOException  if an I/O error occurs when creating the
     *      output stream.
     */
    public OutputStream getOutputStream() throws IOException {
        checkState();
        if (isStream) {
            if(outgoing == null) {
                outgoing = new OutgoingMsgrAdaptor(msgr, timeout);
            }
            if (ros == null) {
                ros = new ReliableOutputStream(outgoing, new FixedFlowControl(windowSize));
            }
        }
        osCreated = true;
        return new JxtaSocketOutputStream(this, outputBufferSize);
    }
    
    /**
     *  Closes this socket.
     *
     *@exception  IOException  if an I/O error occurs when closing this
     *      socket.
     */
    public void close() throws IOException {
        
        synchronized (closeLock) {
            if (closed) {
                return;
            }
            sendClose();
            //jxl modification start
            if(getSoLinger() != -1){
                synchronized (closeLock){
                    try{
                        closeLock.wait(getSoLinger());
                    }catch(InterruptedException ie){
                        ie.printStackTrace();
                    }
                }
            }
            //jxl modification end
            bound = false;
            closed = true;
        }
        
        // This is called when closure is initiated on this side.
        // First make sure whatever is in the output queue is sent
        // If it cannot be sent within the specified timeout, throw an
        // IOException.
        
        // isStream is currently synonymous with isReliable. For reliable
        // streams, the outgoing side that decides to close, must perform
        // lingering. For now, we assume that it no-longer cares for incoming
        // data. In other words, the side that does not decide to close just
        // tears down everything without a care for the contens of its output
        // queue.
        if (isStream) {
            long quitAt = System.currentTimeMillis() + timeout;
            while (true) {
                if (ros == null) {
                    // Nothing to worry about.
                    break;
                }
                
                // ros will not take any new message, now.
                ros.setClosing();
                if (ros.getMaxAck() == ros.getSeqNumber()) {
                    break;
                }
                
                // By default wait forever.
                long left = 0;
                
                // If timeout is not zero. Then compute the waiting time
                // left.
                if (timeout != 0) {
                    left = quitAt - System.currentTimeMillis();
                    if (left < 0) {
                        // Too late
                        closeCommon();
                        throw new IOException("Close timeout");
                    }
                }
                
                try {
                    if (!ros.isQueueEmpty()) {
                        if (LOG.isEnabledFor(Level.DEBUG)) {
                            LOG.debug("Waiting for Output stream queue event");
                        }
                        ros.waitQueueEvent(left);
                    }
                    break;
                } catch (InterruptedException ie) {
                    // give up, then.
                    throw new IOException("Close interrupted");
                }
            }
            
            // We are initiating the close. We do not want to receive
            // anything more. So we can close the ris right away.
            ris.close();
        }
        
        // Lingering done, if needed. We can tell the other side it can close,
        // and then tear down everything.
        
        // FIXME: jice@jxta.org - 20030707 :
        // in stream mode, that message should go through ros as well.
        // All in all, it would be simpler to have two different
        // implementations of JxtaSocket. One for datagrams and the other
        // for reliable stream.
        closeCommon();
        if (LOG.isEnabledFor(Level.DEBUG)) {
            LOG.debug("Socket closed");
        }
    }
    
    /** This is called when closure is initiated on the remote side.
     *  output is closed without any precaution since the remote side is
     *  no longer interrested, but let the input drain, so that all the packets
     *  the other side flushed before closing have been actually delivered.
     *  If needed, ris will be close itself when read first return -1.
     */
    protected void closeFromRemote() throws IOException {
        synchronized (closeLock) {
            if (closed) {
                return;
            }
            bound = false;
            closed = true;
        }
        if (LOG.isEnabledFor(Level.DEBUG)) {
            LOG.debug("Received a close request");
        }
        closeCommon();
    }
    
    /**
     * In stream mode, closes everything but the input
     * stream. closeFromRemote() leaves it open until EOF is
     * reached. That is, until all messages in the input queue have
     * been read. At which point, ris will close() itself.
     */
    protected void closeCommon() throws IOException {
        if (LOG.isEnabledFor(Level.DEBUG)) {
            LOG.debug("Shutting down socket");
        }
        queue.interrupt();
        if (isStream) {
            // close the reliable streams.
            if (ros != null) {
                ros.close();
            }
            if (ris != null) {
                ris.setClosing();
            }
        }
        // close pipe, messenger, and queue
        in.close();
        msgr.close();
        queue.close();
    }
    
    
    /**
     *  Sets the inputPipe attribute of the JxtaSocket object
     *
     *@param  in  The new inputPipe value
     */
    protected void setInputPipe(InputPipe in) {
        this.in = in;
    }
    
    
    /**
     *  we got a message
     *
     *@param  event  the message event
     */
    public void pipeMsgEvent(PipeMsgEvent event) {
        
        Message message = event.getMessage();
        if (message == null) {
            return;
        }
        
        // look for a remote pipe answer
        MessageElement element = (MessageElement)
        message.getMessageElement(JxtaServerSocket.nameSpace, JxtaServerSocket.remPipeTag);
        
        if (element != null) {
            // connect response
            try {
                PeerAdvertisement peerAdv = null;
                InputStream in = element.getStream();
                PipeAdvertisement pa = (PipeAdvertisement)
                AdvertisementFactory.newAdvertisement(element.getMimeType(), in);
                element = message.getMessageElement(JxtaServerSocket.nameSpace, JxtaServerSocket.remPeerTag);
                if (element != null) {
                    in = element.getStream();
                    peerAdv = (PeerAdvertisement)
                    AdvertisementFactory.newAdvertisement(element.getMimeType(), in);
                } else {
                    return;
                }
                
                element = message.getMessageElement(JxtaServerSocket.nameSpace, JxtaServerSocket.credTag);
                if (element != null) {
                    in = element.getStream();
                    credentialDoc = (StructuredDocument)
                    StructuredDocumentFactory.newStructuredDocument(element.getMimeType(), in);
                }
                
                element = message.getMessageElement(JxtaServerSocket.nameSpace, JxtaServerSocket.streamTag);
                if (element != null) {
                    isStream = (element.toString().equals("true"));
                }
                msgr = lightweightOutputPipe(group, pa, peerAdv);
                if (msgr == null) {
                    // let the connection attempt timeout
                    if (LOG.isEnabledFor(Level.ERROR)) {
                        LOG.error("Unable to obtain a back messenger");
                    }
                    return;
                }
                if (isStream) {
                    // Create the input stream right away, otherwise
                    // the first few messages from remote will be lost, unless
                    // we use an intermediate queue.
                    // FIXME: it would be even better if we could create the
                    // input stream BEFORE having the output pipe resolved, but
                    // that would force us to have the MsrgAdaptor block
                    // until we can give it the real pipe or msgr... later.
                    createRis();
                }
                
                synchronized (finalLock) {
                    waiting = false;
                    finalLock.notifyAll();
                }
            } catch (IOException e) {
                if (LOG.isEnabledFor(Level.ERROR)) {
                    LOG.error("failed to process response message", e);
                }
            }
        }
        
        // look for close request
        element = (MessageElement)
        message.getMessageElement(JxtaServerSocket.nameSpace, JxtaServerSocket.closeTag);
        if (element != null) {
            if (element.toString().equals("close")) {
                try {
                    
                    sendCloseACK();
                    closeFromRemote();
                } catch (IOException ie) {
                    if (LOG.isEnabledFor(Level.ERROR)) {
                        LOG.error("failed during closeFromRemote", ie);
                    }
                }
            } else if (element.toString().equals("closeACK")) {
                if (LOG.isEnabledFor(Level.DEBUG)) {
                    LOG.debug("Received a close acknowledgement");
                }
                System.out.println("Got closeACK");
                synchronized(closeLock) {
                    closeLock.notifyAll();
                }
            }
        }
        
        if (!isStream) {
            // isthere data ?
            element = (MessageElement)
            message.getMessageElement(JxtaServerSocket.nameSpace, JxtaServerSocket.dataTag);
            if (element == null) {
                return;
            }
            
            try {
                queue.push(element, -1);
            } catch (InterruptedException e) {
                if (LOG.isEnabledFor(Level.DEBUG)) {
                    LOG.debug("Interrupted", e);
                }
            }
            
            return;
        }
        
        Iterator i =
                message.getMessageElements(Defs.NAMESPACE, Defs.MIME_TYPE_ACK);
        
        if (i != null && i.hasNext()) {
            if (ros != null) {
                ros.recv(message);
            }
            return;
        }
        
        i = message.getMessageElements(Defs.NAMESPACE, Defs.MIME_TYPE_BLOCK);
        if (i != null && i.hasNext()) {
            
            // It can happen that we receive messages for the input stream
            // while we have not finished creating it.
            try {
                synchronized (finalLock) {
                    while (waiting) {
                        finalLock.wait(timeout);
                    }
                }
            } catch (InterruptedException ie) {
            }
            
            if (ris != null) {
                ris.recv(message);
            }
        }
    }
    
    
    /**
     * the output pipe event
     *
     *@param  event  the event
     */
    public void outputPipeEvent(OutputPipeEvent event) {
        OutputPipe op = event.getOutputPipe();
        if (op.getAdvertisement() == null) {
            if (LOG.isEnabledFor(Level.WARN)) {
                LOG.warn("The output pipe has no internal pipe advertisement. Continueing anyway.");
            }
        }
        if (op.getAdvertisement() == null || pipeAdv.equals(op.getAdvertisement())) {
            synchronized (acceptLock) {
                // modify op within lock to prevent a race with the if.
                if (connectOutpipe == null) {
                    connectOutpipe = op;
                    op = null; // if not null, will be closed.
                }
                acceptLock.notifyAll();
            }
            // Ooops one too many, we were too fast re-trying.
            if (op != null) {
                op.close();
            }
            
        } else {
            if (LOG.isEnabledFor(Level.WARN)) {
                LOG.warn("Unexpected OutputPipe :"+op);
            }
        }
    }
    
    /**
     *  A lightweight output pipe constructor, note the return type
     *  Since all the info needed is available, there's no need for to use the pipe service
     *  to resolve the pipe we have all we need to construct a messenger.
     *
     *@param  group    group context
     *@param  pipeAdv  Remote Pipe Advertisement
     *@param  peer     Remote Peer Advertisment
     *@return          Messenger
     */
    protected static Messenger lightweightOutputPipe(PeerGroup group, PipeAdvertisement pipeAdv,
            PeerAdvertisement peer) {
        
        EndpointService endpoint = group.getEndpointService();
        ID opId = pipeAdv.getPipeID();
        String destPeer = (peer.getPeerID().getUniqueValue()).toString();
        // Get an endpoint messenger to that address
        EndpointAddress addr;
        if (pipeAdv.getType().equals(PipeService.UnicastType)) {
            addr = new EndpointAddress("jxta", destPeer, "PipeService", opId.toString());
        } else if (pipeAdv.getType().equals(PipeService.UnicastSecureType)) {
            addr = new EndpointAddress("jxtatls", destPeer, "PipeService", opId.toString());
        } else {
            // not a supported type
            throw new IllegalArgumentException(pipeAdv.getType()+" is not a supported pipe type");
        }
        return endpoint.getMessenger(addr);
    }
    
    private void sendClose() throws SocketException{
        Message msg = new Message();
        msg.addMessageElement(JxtaServerSocket.nameSpace,
                new StringMessageElement(JxtaServerSocket.closeTag,
                "close",
                null));
        try {
            if (LOG.isEnabledFor(Level.DEBUG)) {
                LOG.debug("Sending a close request");
            }
            //jxl start
            if(msgr.getState() != msgr.CLOSED)
                msgr.sendMessageB(msg, null, null);
            //jxl end
        } catch (IOException ie) {
            if (LOG.isEnabledFor(Level.WARN)) {
                LOG.warn("failed during close", ie);
            }
        }
        
    }
    
    private void sendCloseACK() {
        Message msg = new Message();
        msg.addMessageElement(JxtaServerSocket.nameSpace,
                new StringMessageElement(JxtaServerSocket.closeTag,
                "closeACK",
                null));
        try {
            if (LOG.isEnabledFor(Level.DEBUG)) {
                LOG.debug("Sending a close acknowledgement");
            }
            //System.out.println("Send close ACK");
            msgr.sendMessageB(msg, null, null);
        } catch (IOException ie) {
            if (LOG.isEnabledFor(Level.WARN)) {
                LOG.warn("failed during close acknowledgement", ie);
            }
        }
    }
    /**
     *  Gets the Timeout attribute of the JxtaServerSocket object
     *
     * @return                  The soTimeout value
     * @exception  IOException  if an I/O error occurs
     */
    public synchronized int getSoTimeout() throws SocketException {
        if (isClosed()) {
            throw new SocketException("Socket is closed");
        }
        return timeout;
    }
    
    /**
     *  Sets the Timeout attribute of the JxtaServerSocket
     *  a timeout of 0 blocks forever.
     *  In reliable mode it is possible for this call to block
     *  trying to obtain a lock on reliable input stream
     *
     * @param  timeout              The new soTimeout value
     * @exception  IOException  if an I/O error occurs
     */
    public synchronized void setSoTimeout(int timeout) throws SocketException {
        if (timeout < 0) {
            throw new IllegalArgumentException("Invalid Socket timeout :"+timeout);
        }
        this.timeout = timeout;
        if (outgoing != null) {
            outgoing.setTimeout(timeout);
        }
        if (ris != null) {
            ris.setTimeout(timeout);
        }
    }
    
    /**
     *  When in reliable mode, gets the Reliable library window size
     *
     * @return                  The windowSize value
     * @exception  IOException  if an I/O error occurs
     */
    public synchronized int getWindowSize() {
        return windowSize;
    }
    
    /**
     *  When in reliable mode, sets the Reliable library window size
     *
     * @param  windowSize              The new window size value
     * @exception  IOException  if an I/O error occurs
     */
    public synchronized void setWindowSize(int windowSize) throws SocketException {
        if (isBound()) {
            throw new SocketException("Socket bound. Can not change the window size");
        }
        this.windowSize = windowSize;
    }
    
    /**
     * Returns the closed state of the JxtaServerSocket.
     *
     * @return    true if the socket has been closed
     */
    public boolean isClosed() {
        synchronized (closeLock) {
            return closed;
        }
    }
    
    /*
     * Actual implementation of the JxtaSocketInputStream methods.
     */
    
    /**
     * Performs on behalf of JxtaSocketOutputStream.
     *
     * @see java.io.OutputStream#write
     */
    protected void write(byte[] buf, int offset, int length) throws IOException {
        checkState();
        if (isStream) {
            // reliable mode, ros != null
            ros.write(buf, offset, length);
            ros.flush();//jxl modification
            return;
        }
        
        byte[] bufCopy = new byte[length];
        System.arraycopy(buf, offset, bufCopy, 0,length);
        Message msg = new Message();
        msg.addMessageElement(JxtaServerSocket.nameSpace,
                new ByteArrayMessageElement(JxtaServerSocket.dataTag,
                MimeMediaType.AOS,
                bufCopy,
                0,
                length,
                null));
        msgr.sendMessageB(msg, null, null);
    }
    
    /**
     * Performs on behalf of JxtaSocketInputStream.
     *
     * @see java.io.InputStream#read
     */
    protected int read() throws IOException {
        if (isClosed()) {
            return -1;
        }
        checkState();
        if (isStream) {
            return ris.read();
        }
        
        int result = -1;
        InputStream in = getCurrentStream();
        if(in != null) {
            result = in.read();
            if(result == -1) {
                closeCurrentStream();
                result = read();
            }
        }
        return result;
    }
    
    /**
     * Performs on behalf of JxtaSocketInputStream.
     *
     * @see java.io.InputStream#read
     */
    protected int read(byte b[], int off, int len) throws IOException {
        if (isClosed()) {
            return -1;
        }
        checkState();
        if (isStream) {
            
            // If read is called in stream mode, then a JxtaSocketInputStream
            // exists. It is never created until ris exists. ris is never
            // zero'ed. Therefore ris is not null.
            return ris.read(b, off, len);
        }
        
        int result = -1;
        if (b == null) {
            throw new NullPointerException();
        } else if ((off < 0) || (off > b.length) || (len < 0) ||
                ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            result = 0;
        } else {
            InputStream in = getCurrentStream();
            if(in != null) {
                result = in.read(b, off, len);
                if (result == -1) {
                    closeCurrentStream();
                    result = read(b,off,len);
                }
            }
        }
        return result;
    }
    
    /**
     * Returns the number of bytes that can be read
     * (or skipped over) from this input stream.
     * It's possible for this call to block, when called for the very first time
     * before the internal stream has ever been created (i.e. no data receieved)
     * @return the number of bytes that can be read from this input stream
     *          without blocking
     * @throws IOException - if an I/O error occurs.
     */
    protected int available() throws IOException {
        checkState();
        if (isStream) {
            return ris.available();
        }
        
        int result = 0;
        // if currentMsgStream is null, then next read call will block
        // so we return 0 to indicate this.
        InputStream in = getCurrentStream();
        if(in != null) {
            result = in.available();
        }
        return result;
    }
    
    private InputStream getCurrentStream() throws IOException {
        
        // despite the warning about thread-saftey and the
        // getCurrentInQueue method, if queue is closed and the number in
        // queue goes to zero, we're done (it shouldn't be going to go back
        // up again)
        
        synchronized(instrLock) {
            if(currentMsgStream == null) {
                MessageElement me;
                try {
                    me = (MessageElement) queue.pop(timeout);
                } catch (InterruptedException e) {
                    throw new IOException(e.toString());
                }
                
                // queue.pop(0) returns null when and only when queue
                // closed and empty. The rest of the time it waits as
                // long as needed for something to return and returns
                // it. Oh, and, queue is never set to null.
                
                if (me != null) {
                    currentMsgStream = me.getStream();
                }
            }
            
            return currentMsgStream;
        }
    }
    
    private void closeCurrentStream() throws IOException {
        synchronized(instrLock) {
            if(currentMsgStream != null) {
                currentMsgStream.close();
                currentMsgStream = null;
            }
        }
    }
    /**
     * throw a SocketException if closed or not bound
     */
    private void checkState() throws SocketException {
        if (isClosed()) {
            throw new SocketException("Socket is closed");
        } else if (!isBound()) {
            throw new SocketException("Socket not bound");
        }
    }
    
    /**
     *{@inheritDoc}
     */
    public synchronized int getSendBufferSize() throws SocketException {
        if (isClosed()){
            throw new SocketException("Socket is closed");
        }
        return outputBufferSize;
    }
    /**
     *{@inheritDoc}
     */
    public synchronized void setSendBufferSize(int size) throws SocketException {
        if (isClosed()){
            throw new SocketException("Socket is closed");
        }
        if (size < 1) {
            throw new IllegalArgumentException("negative/zero buffer size");
        }
        if (osCreated) {
            throw new SocketException("Can not reset buffersize, OutputStream is already created");
        }
        outputBufferSize = size;
    }
    
    /**
     *{@inheritDoc}
     */
    public synchronized int getReceiveBufferSize() throws SocketException {
        checkState();
        // this is just rough size
        return getOutputStreamBufferSize();
    }
    
    
    /**
     *{@inheritDoc}
     */
    public boolean getKeepAlive() throws SocketException {
        if (isClosed()){
            throw new SocketException("Socket is closed");
        }
        return false;
    }
    
    /**
     *{@inheritDoc}
     */
    public int getTrafficClass() throws SocketException {
        throw new SocketException("TrafficClass not yet defined");
    }
    
    /**
     *{@inheritDoc}
     */
    public void setTrafficClass(int tc) throws SocketException {
        // a place holder when and if we decide to add hints regarding
        // flow info hints such as (IPTOS_LOWCOST (0x02), IPTOS_RELIABILITY (0x04), etc
        throw new SocketException("TrafficClass not yet defined");
    }
    
    /**
     *{@inheritDoc}
     */
    public boolean isInputShutdown() {
        
        if (isClosed()) {
            return true;
        }
        if (isStream) {
            return ris.isInputShutdown();
        } else {
            return isClosed();
        }
    }
    
    /**
     *{@inheritDoc}
     */
    public void sendUrgentData(int data) throws IOException {
        throw new SocketException("Urgent data not supported");
    }
    
    /**
     *{@inheritDoc}
     */
    public void setOOBInline(boolean state) throws SocketException {
        throw new SocketException("Enable/disable OOBINLINE supported");
    }
    
    /**
     *{@inheritDoc}
     */
    public void setKeepAlive(boolean state) throws SocketException {
        if (isClosed()){
            throw new SocketException("Socket is closed");
        }
        throw new SocketException("Operation not supported");
    }
    
    /**
     * Not a supported operation, an exception will be thrown
     */
    public static synchronized void setSocketImplFactory(SocketImplFactory factory) throws IOException {
        throw new SocketException("Operation not supported");
    }
    
    /**
     *{@inheritDoc}
     */
    public void shutdownInput() throws IOException {
        if (isStream) {
            if (ris != null) {
                ris.close();
            }
        }
        // close pipe, and queue
        in.close();
        queue.close();
    }
    
    /**
     *{@inheritDoc}
     */
    public void shutdownOutput() throws IOException {
        if (isStream) {
            //System.out.println("shutdownOutput 2");
            long quitAt = System.currentTimeMillis() + timeout;
            while (true) {
                //System.out.println("shutdownOutput 3");
                if (ros == null) {
                    //System.out.println("shutdownOutput 4");
                    // done
                    break;
                }
                //System.out.println("shutdownOutput 5");
                // stop ros from taking any new message
                
                ros.setClosing();
                //System.out.println("shutdownOutput 5.1");
                
                
                //System.out.println(ros.getMaxAck()+":"+ros.getSeqNumber());
                if (ros.getMaxAck() == ros.getSeqNumber()) {
                    //System.out.println("shutdownOutput 7");
                    break;
                }
                
                
                
                // By default wait forever.
                long left = 0;
                // compute remaining timeout
                //System.out.println("shutdownOutput 8");
                if (timeout != 0) {
                    //System.out.println("shutdownOutput 9");
                    left = quitAt - System.currentTimeMillis();
                    //System.out.println("shutdownOutput 10");
                    if (left < 0) {
                        //System.out.println("shutdownOutput 11");
                        // too late
                        sendClose();
                        //System.out.println("shutdownOutput 12");
                        msgr.close();
                        //System.out.println("shutdownOutput 13");
                        throw new IOException("shutdownOutput timeout");
                    }
                }
                //System.out.println("shutdownOutput 14");
                try {
                    //System.out.println("shutdownOutput 15");
                    ros.waitQueueEvent(left);
                    //System.out.println("shutdownOutput 16");
                } catch (InterruptedException ie) {
                    //System.out.println("shutdownOutput 17");
                    // give up, then.
                    throw new IOException("shutdownOutput interrupted");
                }
            }
        }
    }
    /**
     *{@inheritDoc}
     */
    public boolean isConnected() {
        return isBound();
    }
    
    /**
     *  {@inheritDoc}
     *
     *  <p/>Closes the JxtaSocket.
     **/
    protected synchronized void finalize() throws Throwable {
        super.finalize();
        
        if(!closed) {
            if (LOG.isEnabledFor(Level.WARN)) {
                LOG.warn("JxtaSocket is being finalized without being previously closed. This is likely a users bug.");
            }
        }
        close();
    }
    
    /**
     *{@inheritDoc}
     */
    public String toString() {
        if (isConnected()) {
            return "JxtaSocket[pipe id=" + pipeAdv.getPipeID().toString() + "]";
        }
        return "JxtaSocket[unconnected]";
    }
    
}