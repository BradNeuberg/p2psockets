package org.scache;

/*
 *  Smart Cache, http proxy cache server
 *  Copyright (C) 1998-2003 Radim Kolar
 *
 *    Smart Cache is Open Source Software; you may redistribute it
 *  and/or modify it under the terms of the GNU General Public
 *  License as published by the Free Software Foundation; either
 *  version 2, or (at your option) any later version.
 *
 *    This program distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *    A copy of the GNU General Public License is available as
 *  /usr/doc/copyright/GPL in the Debian GNU/Linux distribution or on
 *  the World Wide Web at http://www.gnu.org/copyleft/gpl.html. You
 *  can also obtain it by writing to the Free Software Foundation,
 *  Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;


/* bugs */
/* nepodporuje multi-line hlavicky. Pouziva to vubec nekdo? */
/* turn off cache file je neefektivni (neodstranuje lf ze seznamu) */

public final class httpreq implements Runnable
{

public static byte[] allowed[];
public static byte[] allowedmask[];
public static boolean trace_url;
public static boolean trace_inkeepalive;

public static String newline;

public static final int REQUEST_UNKNOWN=0;
public static final int REQUEST_GET=1;
public static final int REQUEST_POST=2;
public static final int REQUEST_HEAD=3;
public static final int REQUEST_OPTIONS=4;
public static final int REQUEST_PUT=5;
public static final int REQUEST_DELETE=6;
public static final int REQUEST_TRACE=7;
public static final int REQUEST_CONNECT=8;
public static final int REQUEST_PROPFIND=9;
public static final int REQUEST_REPORT=10;
public static final int REQUEST_MOVE=11;
public static final int REQUEST_COPY=12;
public static final int REQUEST_MKCOL=13;
public static final int REQUEST_PROPPATCH=14;
public static final int REQUEST_MKACTIVITY=15;
public static final int REQUEST_CHECKOUT=16;
public static final int REQUEST_MERGE=17;
public static final int REQUEST_MIN=REQUEST_GET;
public static final int REQUEST_MAX=REQUEST_MERGE;
public static mgr mgr;
public static String visible_hostname;
public static String visible_link;
public static int client_timeout;
public static boolean log_common;

public static DateFormat formatter= new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss", Locale.US);
public static String timezone;

/* access.log */
public static String logpatterns[];
public static DataOutputStream logfilez[];
public static String logfilenames[];

/* ======== private data ========== */

private Socket socket;

httpreq(Socket socket) {
        this.socket=socket;
}

final public static String methodToString(int method)
{
 switch(method)
 {
 case REQUEST_GET:
 		return "GET";
 case REQUEST_POST:
 		return "POST";
 case REQUEST_HEAD:
 		return "HEAD";
 case REQUEST_PUT:
 		return "PUT";
 case REQUEST_OPTIONS:
 		return "OPTIONS";
 case REQUEST_DELETE:
 		return "DELETE";
 case REQUEST_CONNECT:
 		return "CONNECT";
 case REQUEST_TRACE:
 		return "TRACE";
 case REQUEST_REPORT:
 		return "REPORT";
 case REQUEST_PROPFIND:
 		return "PROPFIND";
 case REQUEST_MOVE:
 		return "MOVE";
 case REQUEST_COPY:
 		return "COPY";
 case REQUEST_PROPPATCH:
 		return "PROPPATCH";
 case REQUEST_MKCOL:
 		return "MKCOL";
 case REQUEST_MKACTIVITY:
 		return "MKACTIVITY";
 case REQUEST_CHECKOUT:
		return "CHECKOUT";
 case REQUEST_MERGE:
 		return "MERGE";
 default:
 		return "UNKNOWN";
 }
}

 final public void run() {
    int httpv=9; /* 9-11 for now */
    request rq=null;
    try { 
	/* socket init */
    DataInputStream in=new DataInputStream (new BufferedInputStream(socket.getInputStream(), 2048));
    DataOutputStream ou=new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 2048));
    socket.setSoTimeout(client_timeout);
    socket.setTcpNoDelay(true); // disable  nagle

    /* precteme radku GET / HTTP/1.0 */
    keepalive:while(true)
    {
	String req;
	String req2;
	int req_method;
	try
	{
	   req=in.readLine();
	}
	catch (InterruptedIOException timeout)
	{
	    if(trace_inkeepalive)
	    {
	       System.out.println("[TRACE "+Thread.currentThread().getName()+"] = Input connection timed out.");
	    }
	    throw timeout;
	}
	req2="";
	if(req==null) { 
	    if(trace_inkeepalive)
	    {
	       System.out.println("[TRACE "+Thread.currentThread().getName()+"] = EOF when waiting for request.");
	    }
	    socket.close();
	    return;
	}
	if(req.length()==0)
	{
	    if(trace_inkeepalive)
	    {
	       System.out.println("[TRACE "+Thread.currentThread().getName()+"] = Ignoring empty request line.");
	    }
	    continue;
	}
	req_method=req.indexOf(" HTTP/1.");
	/* determine  http protocol version */
	if(req_method==-1) 
	    httpv=9;
	else
	{
	    char c;
	    c=req.charAt(req_method+8);
	    switch(c)
	    {
		case '0': 
		          httpv=10;break;
		case '1': 
		case '2':
			  httpv=11;break;
		default:
			  httpv=10;
	    }
	}
	if(trace_url==true)
	   System.out.println("[TRACE "+Thread.currentThread().getName()+"] > "+req);
	Vector headers;
	headers=new Vector();

	StringTokenizer st;
	st=new StringTokenizer(req);
	try{
	req2=st.nextToken();

	if(req2.equals("GET")) req_method=REQUEST_GET;
	else
	if(req2.equals("POST")) req_method=REQUEST_POST;
	else
	if(req2.equals("HEAD")) req_method=REQUEST_HEAD;
	else
	if(req2.equals("OPTIONS")) req_method=REQUEST_OPTIONS;
	else
	if(req2.equals("PUT")) req_method=REQUEST_PUT;
	else
	if(req2.equals("TRACE")) req_method=REQUEST_TRACE;
	else
	if(req2.equals("DELETE")) req_method=REQUEST_DELETE;
	else
	if(req2.equals("CONNECT")) req_method=REQUEST_CONNECT;
	else
	if(req2.equals("PROPFIND")) req_method=REQUEST_PROPFIND;
	else
	if(req2.equals("REPORT")) req_method=REQUEST_REPORT;
	else
	if(req2.equals("MOVE")) req_method=REQUEST_MOVE;
	else
	if(req2.equals("COPY")) req_method=REQUEST_COPY;
	else
	if(req2.equals("MKCOL")) req_method=REQUEST_MKCOL;
	else
	if(req2.equals("PROPPATCH")) req_method=REQUEST_PROPPATCH;
	else
	if(req2.equals("MKACTIVITY")) req_method=REQUEST_MKACTIVITY;
	else
	if(req2.equals("CHECKOUT")) req_method=REQUEST_CHECKOUT;
	else
	if(req2.equals("MERGE")) req_method=REQUEST_MERGE;
	else 
	server_error(httpv,501,"Method not implemented.",ou);

	req2=st.nextToken();
	}
	catch (NoSuchElementException e) { server_error(httpv,400,"Incomplete request",ou);}

	/* HTTP 1.0+ - musime precist zbytek volovin az do prazdne radky */

	if(httpv>9)  while(true)
		       { req=in.readLine();
			 if(req==null) {
				if(trace_inkeepalive)
				{
				   System.out.println("[TRACE "+Thread.currentThread().getName()+"] = EOF when reading request's headers.");
				}
			        break keepalive;
			 }
			 if(req.length()==0) break;
			 headers.addElement(req);
			}
	 /* vsechna vstupni data prectena */
	 /* zaciname zpracovavat requesty */
	 try
	 {
	     rq=new request(httpv,req_method,req2,headers,in,ou);
	 }
	 catch(MalformedURLException e2) { server_error(httpv,400,"Invalid URL syntax",ou);}

	 /* check ip access */
	 if (!mgr.checkInetAdr(socket.getInetAddress().getAddress()))
	      server_error(httpv,403,"Cache access denied.",ou);
	 
	 try
	  {
	    mgr.process_request(rq);
	  }
	  catch (EOFException DONE) {/* break keepalive*/;}
	  catch (IOException transfer_error)
	  {
	     // System.out.println("[debug] I/O error when serving "+rq.getURL());
	     socket.setSoLinger(true,0);
	     throw transfer_error;
	  }
	  log_this(rq); // write entry to log
	  if(rq.keepalive==false) { 
	      rq=null; //  no double log entries
	      break;
	  }
	  rq=null;
	  if(trace_inkeepalive)
	  {
	     System.out.println("[TRACE "+Thread.currentThread().getName()+"] = Waiting for next request on connection.");
	  }
     }/* keepalive loop */
     } /* main loop try */
     catch (IOException main_transfer_error) {}
     finally {
              /* zkusime zavrit hlavni socket */
              try {
                    socket.close();
		  }
	      catch (IOException jejda) {}
	      log_this(rq);
	     };
     return;
    }

/* posle error, vyhodi exception a skonci! */
public final static void server_error(int httpv,int err,String msg,DataOutputStream out) throws java.io.IOException
{
    request rq;
    rq=new request(httpv,REQUEST_GET,"smartcache://url-parse-error",new Vector(),null,out);
    rq.keepalive=false;
    rq.send_error(err,msg);
}

/* pokusi se otevrit protokolacni soubor */
public final static boolean
  open_logfile(String xlogfilenames[], DataOutputStream xlogfilez[], int i)
{
    if(xlogfilez[i]!=null) return true; // allready open
    if(xlogfilenames[i]==null) return false; // unknown name
           /* OPEN LOGFILE */
             try{
               DataOutputStream dos;
               dos=new DataOutputStream(new BufferedOutputStream(
               new FileOutputStream(xlogfilenames[i],true),8192));
               xlogfilez[i]=dos;
             }
             catch (IOException e1)
              {
               System.err.println("Problem creating logfile "+xlogfilenames[i]+" - turning it off.");
               xlogfilenames[i]=null;
               return false;
              }
  return true;
}

public final static void flush(DataOutputStream xlogfilez[])
{

 if(xlogfilez!=null)
   for(int z=0;z<xlogfilez.length;z++)
   {
     if(xlogfilez[z]!=null)
       try{
        xlogfilez[z].flush();
       }
       catch(IOException e)
       {}
   }
}


public final static void close(DataOutputStream xlogfilez[])
{

 if(xlogfilez!=null)
   for(int z=0;z<xlogfilez.length;z++)
   {
     if(xlogfilez[z]!=null)
       try{
        xlogfilez[z].close();
       }
       catch(IOException e)
       {}
       finally
       {
        xlogfilez[z]=null;
       }
   }
}

private final void log_this(request rq)
{
     /* zalogovat do access.logu */
     if(logpatterns!=null && rq!=null)
     {
       int j=logpatterns.length;
       String urlcache=rq.getURL();
       for(int i=0;i<j;i++)
       {
        String fragment;
        fragment=mgr.simpleWildMatch(logpatterns[i], urlcache);
        if(fragment==null) continue;
        DataOutputStream outfile;
        outfile=logfilez[i];

           if(outfile==null)
	       if(logfilenames[i]!=null)
		   synchronized (logfilenames)
		       {
			/* OPEN LOGFILE */
			if(!open_logfile(logfilenames,logfilez,i)) break;
			 else
			  outfile=logfilez[i];
		       }
	       else
		   break;

          /* ZAHAJUJEME LOGOVANI ! */
          InetAddress adr;
          adr=socket.getInetAddress();
          StringBuffer sb=new StringBuffer(90);

          sb.append(adr.getHostAddress());
          sb.append(" - - [");
          sb.append(formatter.format(new Date()));
          sb.append(timezone);
	  sb.append(methodToString(rq.method));
	  sb.append(" ");
	  /* we have full or partial URL? */
          if(logpatterns[i]!=null) 
	      sb.append("/");
	  sb.append(fragment);
	  sb.append("\" ");
	  sb.append(rq.httprc);
	  sb.append(" ");
	  sb.append(rq.log_size);
	  if(!log_common)
	  {
	      sb.append(" \"");
	      sb.append(rq.log_referer);
	      sb.append("\" \"");
	      sb.append(rq.log_uagent);
	      sb.append("\"");
	  }
	  sb.append(newline);
          synchronized (outfile)
          {
	    try
	    {
              outfile.writeBytes(sb.toString());
	    }
	    catch (IOException errr) { logfilez[i]=null; }
          }
       } /* log for */
      }/* log enabled? */
}
} /* class */
