// ========================================================================
// Copyright (c) 1999-2002 Mort Bay Consulting (Australia) Pty. Ltd.
// $Id: P2PSocketListener.java,v 1.3 2004/11/12 10:38:28 bradneuberg Exp $
// ========================================================================

package org.mortbay.p2psockets.http;

import java.io.*;
import java.net.*;

import org.p2psockets.*;

import org.mortbay.util.*;
import org.mortbay.http.*;

import org.mortbay.p2psockets.util.P2PThreadedServer;

/* ------------------------------------------------------------ */
/** Socket HTTP Listener modified to use Jxta P2P Sockets.
 * The behaviour of the listener can be controlled with the
 * attributues of the ThreadedServer and ThreadPool from which it is
 * derived. Specifically: <PRE>
 * MinThreads    - Minumum threads waiting to service requests.
 * MaxThread     - Maximum thread that will service requests.
 * MaxIdleTimeMs - Time for an idle thread to wait for a request or read.
 * LowResourcePersistTimeMs - time in ms that connections will persist if listener is
 *                            low on resources. 
 * </PRE>
 *
 * We aggregate P2PThreadedServers inside
 * of P2PSocketListeners instead of subclassing from them so
 * that we can retain P2PSocketListener as our superclass, so that the
 * rest of Jetty doesn't have to have know about P2PSocketListener.
 * -- bkn3@columbia.edu
 *
 * @version $Id: P2PSocketListener.java,v 1.3 2004/11/12 10:38:28 bradneuberg Exp $
 * @author Greg Wilkins (gregw)
 */
public class P2PSocketListener extends SocketListener
{
    /* ------------------------------------------------------------------- */
    private int _lowResourcePersistTimeMs=2000;
    private String _scheme=HttpMessage.__SCHEME;
    private String _integralScheme=HttpMessage.__SSL_SCHEME;
    private String _confidentialScheme=HttpMessage.__SSL_SCHEME;
    private int _integralPort=0;
    private int _confidentialPort=0;
    private boolean _identifyListener=false;
    private int _bufferSize=4096;
    private int _bufferReserve=512;

    private transient HttpServer _server;
    private transient int _throttled=0;
    private transient boolean _lastLow=false;
    private transient boolean _lastOut=false;

	/** We aggregate this class and make all calls to it that would
	  * have instead gone to P2PSocketListener's superclass, which is
	  * ThreadedServer. -- bkn3@columbia.edu */
	private P2PThreadedServer jxtaThreadedServer;

    /* ------------------------------------------------------------------- */
    public P2PSocketListener()
    {
		jxtaThreadedServer = new P2PThreadedServer(this);
		// 0 indicates that we wait forever
		jxtaThreadedServer.setMaxIdleTimeMs(0);
	}

    /* ------------------------------------------------------------------- */
    public P2PSocketListener(InetAddrPort address)
        throws IOException
    {
        jxtaThreadedServer = new P2PThreadedServer(address, this);
		// 0 indicates that we wait forever
		jxtaThreadedServer.setMaxIdleTimeMs(0);
    }
    
    /* ------------------------------------------------------------ */
    public void setHttpServer(HttpServer server)
    {
        Code.assertTrue(_server==null || _server==server,
                        "Cannot share listeners");
        _server=server;
    }

    /* ------------------------------------------------------------ */
    public HttpServer getHttpServer()
    {
        return _server;
    }

    /* ------------------------------------------------------------ */
    public int getBufferSize()
    {
        return _bufferSize;
    }
    
    /* ------------------------------------------------------------ */
    public void setBufferSize(int size)
    {
        _bufferSize=size;
    }

    /* ------------------------------------------------------------ */
    public int getBufferReserve()
    {
        return _bufferReserve;
    }
    
    /* ------------------------------------------------------------ */
    public void setBufferReserve(int size)
    {
        _bufferReserve=size;
    }
        
    /* ------------------------------------------------------------ */
    public boolean getIdentifyListener()
    {
        return _identifyListener;
    }
    
    /* ------------------------------------------------------------ */
    /** 
     * @param identifyListener If true, the listener name is added to all
     * requests as the org.mortbay.http.HttListener attribute
     */
    public void setIdentifyListener(boolean identifyListener)
    {
        _identifyListener = identifyListener;
    }
    
    /* --------------------------------------------------------------- */
    public void setDefaultScheme(String scheme)
    {
        _scheme=scheme;
    }
    
    /* --------------------------------------------------------------- */
    public String getDefaultScheme()
    {
        return _scheme;
    }

    /* ------------------------------------------------------------ */
    /** 
     * @return time in ms that connections will persist if listener is
     * low on resources.
     */
    public int getLowResourcePersistTimeMs()
    {
        return _lowResourcePersistTimeMs;
    }

    /* ------------------------------------------------------------ */
    /** Set the low resource persistace time.
     * When the listener is low on resources, this timeout is used for idle
     * persistent connections.  It is desirable to have this set to a short
     * period of time so that idle persistent connections do not consume
     * resources on a busy server.
     * @param ms time in ms that connections will persist if listener is
     * low on resources. 
     */
    public void setLowResourcePersistTimeMs(int ms)
    {
        _lowResourcePersistTimeMs=ms;
    }
    
    
    /* --------------------------------------------------------------- */
    public void start()
        throws Exception
    {
        jxtaThreadedServer.start();
        Log.event("Started P2PSocketListener on "+getInetAddrPort());
    }

    /* --------------------------------------------------------------- */
    public void stop()
        throws InterruptedException
    {
        jxtaThreadedServer.stop();
        Log.event("Stopped P2PSocketListener on "+getInetAddrPort());
    }

    /* ------------------------------------------------------------ */
    /** Handle Job.
     * Implementation of ThreadPool.handle(), calls handleConnection.
     * @param job A Connection.
     */
    public void handleConnection(Socket socket)
        throws IOException
    {
        socket.setTcpNoDelay(true);
        
        HttpConnection connection =
            new HttpConnection(this,
                               socket.getInetAddress(),
                               socket.getInputStream(),
                               socket.getOutputStream(),
                               socket);

        try
        {
            if (_lowResourcePersistTimeMs>0 && isLowOnResources())
            {
                socket.setSoTimeout(_lowResourcePersistTimeMs);
                connection.setThrottled(true);
            }
            else
            {
                socket.setSoTimeout(getMaxIdleTimeMs());
                connection.setThrottled(false);
            }
            
        }
        catch(Exception e)
        {
            Code.warning(e);
        }
        connection.handle();
    }

    /* ------------------------------------------------------------ */
    /** Customize the request from connection.
     * This method extracts the socket from the connection and calls
     * the customizeRequest(Socket,HttpRequest) method.
     * @param request
     */
    public void customizeRequest(HttpConnection connection,
                                 HttpRequest request)
    {
        if (_identifyListener)
            request.setAttribute(HttpListener.ATTRIBUTE,getName());
        
        Socket socket=(Socket)(connection.getConnection());
        customizeRequest(socket,request);
    }

    /* ------------------------------------------------------------ */
    /** Customize request from socket.
     * Derived versions of P2PSocketListener may specialize this method
     * to customize the request with attributes of the socket used (eg
     * SSL session ids).
     * This version resets the SoTimeout if it has been reduced due to
     * low resources.  Derived implementations should call
     * super.customizeRequest(socket,request) unless persistConnection
     * has also been overridden and not called.
     * @param request
     */
    protected void customizeRequest(Socket socket,
                                    HttpRequest request)
    {
        try
        {
            if (request.getHttpConnection().isThrottled())
            {
                socket.setSoTimeout(getMaxIdleTimeMs());
                request.getHttpConnection().setThrottled(false);
            }
        }
        catch(Exception e)
        {
            Code.ignore(e);
        }
    }

    /* ------------------------------------------------------------ */
    /** Persist the connection.
     * This method is called by the HttpConnection in order to prepare a
     * connection to be persisted. For this implementation,
     * if the listener is low on resources, the connection read
     * timeout is set to lowResourcePersistTimeMs.  The
     * customizeRequest method is used to reset this to the normal
     * value after a request has been read.
     * @param connection.
     */
    public void persistConnection(HttpConnection connection)
    {
        try
        {
            Socket socket=(Socket)(connection.getConnection());

            if (_lowResourcePersistTimeMs>0 && isLowOnResources())
            {
                socket.setSoTimeout(_lowResourcePersistTimeMs);
                connection.setThrottled(true);
            }
            else
                connection.setThrottled(false);
        }
        catch(Exception e)
        {
            Code.ignore(e);
        }
    }

    /* ------------------------------------------------------------ */
    /** Get the lowOnResource state of the listener.
     * A P2PSocketListener is considered low on resources if the total number of
     * threads is maxThreads and the number of idle threads is less than minThreads.
     * @return True if low on idle threads. 
     */
    public boolean isLowOnResources()
    {
        boolean low =
            getThreads()==getMaxThreads() &&
            getIdleThreads()<getMinThreads();
        if (low && !_lastLow)
            Log.event("LOW ON THREADS: "+this);
        else if (!low && _lastLow)
        {
            Log.event("OK on threads: "+this);
            _lastOut=false;
        }
        _lastLow=low;
        return low;
    }

    /* ------------------------------------------------------------ */
    /**  Get the outOfResource state of the listener.
     * A P2PSocketListener is considered out of resources if the total number of
     * threads is maxThreads and the number of idle threads is zero.
     * @return True if out of resources. 
     */
    public boolean isOutOfResources()
    {
        boolean out =
            getThreads()==getMaxThreads() &&
            getIdleThreads()==0;
        if (out && !_lastOut)
            Code.warning("OUT OF THREADS: "+this);
            
        _lastOut=out;
        return out;
    }
    
    /* ------------------------------------------------------------ */
    public boolean isIntegral(HttpConnection connection)
    {
        return false;
    }
    
    /* ------------------------------------------------------------ */
    public boolean isConfidential(HttpConnection connection)
    {
        return false;
    }

    /* ------------------------------------------------------------ */
    public String getIntegralScheme()
    {
        return _integralScheme;
    }
    
    /* ------------------------------------------------------------ */
    public void setIntegralScheme(String integralScheme)
    {
        _integralScheme = integralScheme;
    }
    
    /* ------------------------------------------------------------ */
    public int getIntegralPort()
    {
        return _integralPort;
    }

    /* ------------------------------------------------------------ */
    public void setIntegralPort(int integralPort)
    {
        _integralPort = integralPort;
    }
    
    /* ------------------------------------------------------------ */
    public String getConfidentialScheme()
    {
        return _confidentialScheme;
    }

    /* ------------------------------------------------------------ */
    public void setConfidentialScheme(String confidentialScheme)
    {
        _confidentialScheme = confidentialScheme;
    }

    /* ------------------------------------------------------------ */
    public int getConfidentialPort()
    {
        return _confidentialPort;
    }

    /* ------------------------------------------------------------ */
    public void setConfidentialPort(int confidentialPort)
    {
        _confidentialPort = confidentialPort;
    }

	/* ------------------------------------------------------------ */
	/** The following are methods that we delegate to our 
	  * P2PThreadedServer instance instead of our superclass,
	  * as the normal SocketListener does. 
	  */
    /** 
     * @return The ServerSocket
     */
    public ServerSocket getServerSocket()
    {
        return jxtaThreadedServer.getServerSocket();
    }   
    
    /* ------------------------------------------------------------ */
    /** Set the server InetAddress and port.
     * @param address The Address to listen on, or 0.0.0.0:port for
     * all interfaces.
     */
    public synchronized void setInetAddrPort(InetAddrPort address) 
    {
        jxtaThreadedServer.setInetAddrPort(address);
    }

    /* ------------------------------------------------------------ */
    /** 
     * @return IP Address and port in a new Instance of InetAddrPort.
     */
    public InetAddrPort getInetAddrPort()
    {
        return jxtaThreadedServer.getInetAddrPort();
    }
    
    /* ------------------------------------------------------------ */
    /** 
     * @param host 
     */
    public synchronized void setHost(String host)
        throws UnknownHostException
    {
        jxtaThreadedServer.setHost(host);
    }
    
    /* ------------------------------------------------------------ */
    /** 
     * @return Host name
     */
    public String getHost()
    {
        return jxtaThreadedServer.getHost();
    }

    
    /* ------------------------------------------------------------ */
    /** 
     * @param addr 
     */
    public synchronized void setInetAddress(InetAddress addr)
    {
        jxtaThreadedServer.setInetAddress(addr);
    }
    
    /* ------------------------------------------------------------ */
    /** 
     * @return IP Address
     */
    public InetAddress getInetAddress()
    {
        return jxtaThreadedServer.getInetAddress();
    }
    
    /* ------------------------------------------------------------ */
    /** 
     * @param port 
     */
    public synchronized void setPort(int port)
    {
        jxtaThreadedServer.setPort(port);
    }

    /* ------------------------------------------------------------ */
    /** 
     * @return port number
     */
    public int getPort()
    {
        return jxtaThreadedServer.getPort();
    }
	    
	/* ------------------------------------------------------------ */
    /** 
     * @return milliseconds
     */
    public int getMaxReadTimeMs()
    {
        return jxtaThreadedServer.getMaxReadTimeMs();
    }
    
    /* ------------------------------------------------------------ */
    /** 
     * @param ls seconds to linger or -1 to disable linger.
     */
    public void setLingerTimeSecs(int ls)
    {
       jxtaThreadedServer.setLingerTimeSecs(ls);
    }
    
    /* ------------------------------------------------------------ */
    /** 
     * @return seconds.
     */
    public int getLingerTimeSecs()
    {
        return jxtaThreadedServer.getLingerTimeSecs();
    }
    
    /* ------------------------------------------------------------ */
    public String toString()
    {
        return jxtaThreadedServer.toString();
    }

	/** Handle Job - we modify this to call the handleConnection
	 * that is inside of P2PSocketListener.
     * Implementation of ThreadPool.handle(), calls handleConnection.
     * @param job A Connection.
     */
    public void handle(Object job)
    {
        jxtaThreadedServer.handle(job);
    }


	/** The following methods are from ThreadPool; we delegate
	  * them all to the instance of P2PThreadedServer that we
	  * aggregate.
	  */
	/* ------------------------------------------------------------ */
    /** 
     * @return The name of the ThreadPool.
     */
    public String getName()
    {
        return jxtaThreadedServer.getName();
    }

    /* ------------------------------------------------------------ */
    /** 
     * @param name Name of the ThreadPool to use when naming Threads.
     */
    public void setName(String name)
    {
        jxtaThreadedServer.setName(name);
    }
    
    /* ------------------------------------------------------------ */
    /** Is the pool running jobs.
     * @return True if start() has been called.
     */
    public boolean isStarted()
    {
        return jxtaThreadedServer.isStarted();
    }
    
    /* ------------------------------------------------------------ */
    /** Get the number of threads in the pool.
     * Delegated to the named or anonymous Pool.
     * @see #getIdleThreads
     * @return Number of threads
     */
    public int getThreads()
    {
        return jxtaThreadedServer.getThreads();
    }
    
    /* ------------------------------------------------------------ */
    /** Get the number of idle threads in the pool.
     * Delegated to the named or anonymous Pool.
     * @see #getThreads
     * @return Number of threads
     */
    public int getIdleThreads()
    {
        return jxtaThreadedServer.getIdleThreads();
    }
    
    /* ------------------------------------------------------------ */
    /** Get the minimum number of threads.
     * Delegated to the named or anonymous Pool.
     * @see #setMinThreads
     * @return minimum number of threads.
     */
    public int getMinThreads()
    {
        return jxtaThreadedServer.getMinThreads();
    }
    
    /* ------------------------------------------------------------ */
    /** Set the minimum number of threads.
     * Delegated to the named or anonymous Pool.
     * @see #getMinThreads
     * @param minThreads minimum number of threads
     */
    public void setMinThreads(int minThreads)
    {
        jxtaThreadedServer.setMinThreads(minThreads);
    }
    
    /* ------------------------------------------------------------ */
    /** Set the maximum number of threads.
     * Delegated to the named or anonymous Pool.
     * @see #setMaxThreads
     * @return maximum number of threads.
     */
    public int getMaxThreads()
    {
        return jxtaThreadedServer.getMaxThreads();
    }
    
    /* ------------------------------------------------------------ */
    /** Set the maximum number of threads.
     * Delegated to the named or anonymous Pool.
     * @see #getMaxThreads
     * @param maxThreads maximum number of threads.
     */
    public void setMaxThreads(int maxThreads)
    {
        jxtaThreadedServer.setMaxThreads(maxThreads);
    }
    
    /* ------------------------------------------------------------ */
    /** Get the maximum thread idle time.
     * Delegated to the named or anonymous Pool.
     * @see #setMaxIdleTimeMs
     * @return Max idle time in ms.
     */
    public int getMaxIdleTimeMs()
    {
        return jxtaThreadedServer.getMaxIdleTimeMs();
    }
    
    /* ------------------------------------------------------------ */
    /** Set the maximum thread idle time.
     * Threads that are idle for longer than this period may be
     * stopped.
     * Delegated to the named or anonymous Pool.
     * @see #getMaxIdleTimeMs
     * @param maxIdleTimeMs Max idle time in ms.
     */
    public void setMaxIdleTimeMs(int maxIdleTimeMs)
    {
        jxtaThreadedServer.setMaxIdleTimeMs(maxIdleTimeMs);
    }
    
    
    /* ------------------------------------------------------------ */
    /** Set Max Read Time.
     * @deprecated maxIdleTime is used instead.
     */
    public void setMaxStopTimeMs(int ms)
    {
        Code.warning("setMaxStopTimeMs is deprecated. No longer required.");
    }
    
    /* ------------------------------------------------------------ */
    public void join()
    {
        jxtaThreadedServer.join();
    }
    
    /* ------------------------------------------------------------ */
    /** Run job.
     * Give a job to the pool. 
     * @param job  If the job is derived from Runnable, the run method
     * is called, otherwise it is passed as the argument to the handle
     * method.
     */
    public void run(Object job)
        throws InterruptedException
    {
        jxtaThreadedServer.run(job);
    }
}
