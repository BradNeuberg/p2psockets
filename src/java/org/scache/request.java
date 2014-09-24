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
import java.util.*;
import java.util.zip.*;
import java.text.*;
import java.net.*;


/* chyby : pri POST posle HTTP1.0 hlavicku HTTP0.9 klientu */

public final class request{

public static byte GIF[];

public static final byte GIF0[]={ /* transparent gif */
(byte)0x47,(byte)0x49,(byte)0x46,(byte)0x38,(byte)0x39,(byte)0x61,(byte)0x01,(byte)0x00,
(byte)0x01,(byte)0x00,(byte)0x80,(byte)0x00,(byte)0x00,(byte)0xff,(byte)0xff,(byte)0xff,
(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x21,(byte)0xf9,(byte)0x04,(byte)0x01,(byte)0x00,
(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x2c,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,
(byte)0x01,(byte)0x00,(byte)0x01,(byte)0x00,(byte)0x00,(byte)0x02,(byte)0x02,(byte)0x44,
(byte)0x01,(byte)0x00,(byte)0x3B
};

public static final byte GIF1[]={ //JMS
    (byte)0x47,(byte)0x49,(byte)0x46,(byte)0x38,(byte)0x37,(byte)0x61,
    (byte)0x01,(byte)0x00,
    (byte)0x01,(byte)0x00,(byte)0x80,(byte)0x00,(byte)0x00,(byte)0xFF,(byte)0x00,(byte)0xFF,
    (byte)0x00,(byte)0x00,(byte)0xa8,(byte)0x2C,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,
    (byte)0x01,(byte)0x00,(byte)0x01,(byte)0x00,(byte)0x00,(byte)0x02,(byte)0x02,(byte)0x44,
    (byte)0x01,(byte)0x00,(byte)0x3B
};

/* quick abort config */
public static int qa_minlen, qa_maxlen;
public static float qa_percent;
public static long qa_maxtime;

public static String wafer;
public static boolean proxyvia;
public static String via_header;
public static char pnocache;
public static int read_timeout;
public static int request_timeout;
public static boolean cache_protected;
public static boolean cache_private;
public static boolean cache_vary;
public static char referer_hack;
public static String default_forward_for;
public static boolean remove_pragma_no_cache;
public static DateFormat formatter= new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
public static String fake_user_agent, fake_referer;
public static int maxbody;
public static int reqmaxbody;
public static int maxcacheable;

/* tracing */
public static boolean trace_request;
public static boolean trace_reply;
public static boolean trace_abort;
public static boolean trace_cookie;

public static boolean allow_all_session_cookies;

/* download machine */
public static  regexp[] dmachine_ctype_mask;
public static int dmachine_ctype_min;

/* object private data */
public  String URL;         /* requested full URL */
private String rline;       /* what to send to remote server */
public int method;
private DataInputStream in; /* klient input */
private DataOutputStream out; /* klient output */
private int httpv;
public boolean keepalive;   /* wants/gets client side keepalive? */

public boolean cacheable;

public long ims; /* if modified since */
public long exp; /* expires */

private boolean hasCookie;
public int ctsize;
public String ctype;
public String location;
public String encoding;
public String etag;
public boolean reload;
public Vector headers;
private boolean host;
public int httprc;
public boolean chunked;
/* string parse cached */
public String protocol; /* ftp, gopher, ... */
public String hostname;
public String log_uagent;
public String log_referer;
public int    log_size;

private int proxyport;
private InetAddress proxyhost;

/* konstruktor */
request(int htver,int met,String url,Vector head,DataInputStream in,DataOutputStream out) throws MalformedURLException
{
 hasCookie=false;
 ims=httprc=0;
 ctsize=-1;
 reload=false;
 cacheable=true;
 this.method=met;
 protocol=null;
 ctype=null;
 encoding=null;
 location=null;
 etag=null;
 chunked=false;
 log_uagent=log_referer="-";
 this.in=in;
 this.out=out;
 httpv=htver;
 if(httpv<11) keepalive=false; else keepalive=true;

 URL=url;
 headers=head;

 boolean hasVia=false;
 host=false; /* host hlavicka */
 /* projdeme hlavicky, zajima nas Pragma: no-cache
  a if-modified-since
 */
 int i,j;
 String s,s1,s2,hosth;
 hosth=null;
 int cachedsize=headers.size();
 headers:for(i=0;i<cachedsize;i++)
 {
    s=(String)headers.elementAt(i);
    j=s.indexOf(':',0);
    if(j==-1) continue;
    s1=s.substring(0,j).toLowerCase();
    s2=s.substring(j+1).trim();
    if(trace_request==true)
      System.out.println("[TRACE "+Thread.currentThread().getName()+"] > "+s);
    if(s1.equals("pragma"))
           {
	       reload=true;
	       if (remove_pragma_no_cache) { headers.removeElementAt(i--);cachedsize--;}
	       continue;
           }
    // TODO very primitive CC suport
    if(s1.equals("cache-control"))
    {
       if(s2.equals("max-age=0"))
           reload=true;
       continue;
    }
    if(s1.equals("host")) { host=true; hosth=s2;continue;}
    if(s1.equals("if-none-match"))
    {
	etag=s2;
        headers.removeElementAt(i--);
        cachedsize--;
	continue;
    }
    if(s1.equals("content-length"))
             try
              {
                 ctsize=Integer.valueOf(s2).intValue();
              }
              catch (Exception ignore) {}
              finally { continue;}
    if(s1.equals("accept-encoding"))
    {
      encoding=s2;
      continue;
    }
    if(s1.equals("if-modified-since")) {
                                        /* cut of file size */
                                        j=s2.indexOf(';',0);
                                        if(j>-1) s2=s2.substring(0,j);
					try
					{
                                           ims=new Date(s2).getTime();
					}
					catch (IllegalArgumentException baddate)
					 {}
                                        headers.removeElementAt(i--);
                                        cachedsize--;
                                        continue;
                                     // System.out.println("IMS="+ims);
                                      }
    /* kill possible Keep-Alive */
    if(s1.equals("connection") || s1.equals("proxy-connection")) {
	                                if(s2.length()==5) keepalive=false; else keepalive=true;
                                        headers.removeElementAt(i--);
                                        cachedsize--;
                                        continue;
                                      }

    if(hasCookie==false && s1.equals("cookie"))
                                      {
                                       hasCookie=true;
                                       continue;
                                      }
    if(s1.equals("authorization")) {
                                        cacheable=cache_protected;
                                        continue;
                                      }

    if(s1.equals("via")) {
                                        headers.setElementAt("Via: "+s2+","+via_header,i);
					hasVia=true;
                                        continue;
                                      }

    if(s1.equals("user-agent")) 
    {
	 log_uagent=s2;
	 /* Change User-Agent: */
         if(fake_user_agent!=null)
	 {
	    headers.setElementAt(fake_user_agent,i);
	 }
	 continue;
    }
    if(s1.equals("referer")) {
                                  log_referer=s2;
				  if(fake_referer!=null)
                                           s=fake_referer;
		                    else
					switch(referer_hack)
					{
					    case 1:
                                               /* kill referer header */
                                               headers.removeElementAt(i--);
					       cachedsize--;
					    case 0:
					       continue headers;
					    default:
                                               s="Referer: "+url;
				       }
                                   headers.setElementAt(s,i);
                                   continue;
                             }
 }

 /* try to request compressed data */
 if(encoding==null && cacheobject.auto_compress>0)
   headers.addElement("Accept-Encoding: gzip");
 
 /* pokud jsme http 0.9 tak pridame accept hlavicku */
 if(httpv<10) headers.addElement("Accept: */*");
 if(proxyvia && !hasVia) headers.addElement("Via:"+via_header);
 /* pro jistotu */
 // headers.addElement("Connection: close");

 /* - F O R W A R D E R - */
 if(url.charAt(0)=='/')
  {
   /* fire-up forwarder! */
   protocol="http";
   if(hosth!=null) URL="http://"+hosth+url;
           else
                   URL="http://"+default_forward_for+url;
   return;
  }

 if(met!=httpreq.REQUEST_CONNECT)
 {
   /* extract protocol */
   int pos=url.indexOf("://",0);
   if(pos==-1) throw new MalformedURLException();
   protocol=url.substring(0,pos);

   /* check for losername/password */
 try
 {
  int zav=url.indexOf('@',pos+3);
  if(zav>0)
   {
    /*   @   / - ok     */
    /*   /  @ - ignore  */
    /*   @    - ok      */
    int slash=url.indexOf('/',pos+3);
    if( slash>zav || slash==-1 )
    {
      String auth="Authorization: Basic "+HTUU.encode_string(url.substring(pos+3,zav));
      headers.addElement(auth);
      URL=protocol+"://"+url.substring(zav+1); // remove auth string from URL
    }
   }
 }
 catch (Exception e)
   { throw new MalformedURLException();} // So no need to check index out of bounds
 } 
 else 
 {
   protocol="https";
   cacheable=false;
 }
}

final public void rewriteURL(String newURL)
{
 this.URL=newURL;
 if(!host && referer_hack<2) return; // scan and kill old host header
 String s,s1;
  int cachedsize=headers.size();

  for(int i=0;i<cachedsize;i++)
 {
    int j;
    s=(String)headers.elementAt(i);
    j=s.indexOf(':',0);
    if(j==-1) continue;
    s1=s.substring(0,j).toLowerCase();
    // s2=s.substring(j+1).trim();
    if(s1.equals("host")) {
                            headers.removeElementAt(i--);
                            cachedsize--;
                            continue;
                           }
    if(referer_hack>0 && s1.equals("referer")) {
                                            s="Referer: "+newURL;
                                            headers.setElementAt(s,i);

                                            continue;
                                      }

 }
 host=false;
}

final public void removeOutgoingCookies()
{
 if(hasCookie==false) return;
 // scan and kill c00kie header
  String s,s1;
  int cachedsize=headers.size();

  for(int i=0;i<cachedsize;i++)
 {
    int j;
    s=(String)headers.elementAt(i);
    j=s.indexOf(':',0);
    if(j==-1) continue;
    s1=s.substring(0,j).toLowerCase();
    if(s1.equals("cookie")) {
                            if(wafer==null)
                              {
                                headers.removeElementAt(i--);
                                cachedsize--;
                              }
                              else
                              {headers.setElementAt(wafer,i);}
                            continue;
                           }
 }
}


final public void addHost(String hostname,String port)
{
 if(host) return; /* uz ji mame - nemenime */
 if(port==null)  headers.addElement("Host: "+hostname);
  else
   headers.addElement("Host: "+hostname+":"+port);

 host=true;
}

final public String getURL()
{
 if(method==httpreq.REQUEST_CONNECT) 
   return "https://"+URL+"/"; 
 else 
   return URL;
}

/* nastavi cil - proxy nebo cilovy server, to je jedno */
final public void setTarget(InetAddress proxyhost, int proxyport)
{
 this.proxyhost=proxyhost;
 this.proxyport=proxyport;
}

/* returns connection from possible keep-alive pool */
final public WebConnection connectToHost() throws IOException
{
  return ConnectionHandler.getConnection(proxyhost,proxyport);
}

/* do not use keep-alive connections for that */
final public void handle_connect(boolean direct) throws IOException
{
 WebConnection wc;
 try
  {
    wc=new WebConnection(proxyhost,proxyport);
  }
  catch (IOException e)
   {
     send_error(500,"Connect to server failed ("+e+"). CONNECT request canceled.");
     return;
   }
  DataInputStream sin=wc.webis;
  DataOutputStream sou=wc.webos;
  if(!direct)
  {
      /* write connect request to https_proxy */
      sou.writeBytes("CONNECT ");
      sou.writeBytes(URL);
      sou.writeBytes(" HTTP/1.0\r\n\r\n");
      sou.flush();
  }
  else
  {
      out.writeBytes("HTTP/1.0 200 Connection established\r\nProxy-agent: ");
      out.writeBytes(scache.VERSION);
      out.writeBytes("\r\n\r\n");
      out.flush();
      httprc=200;
  }

  /* buffer */
  byte b[]=new byte[512];
  int rb;

  // Thread worker=new Thread(new InOut(sin,out,Thread.currentThread()));
  Thread worker=new Thread(new InOut(in,sou,Thread.currentThread()));
  worker.start();

  mainloop:while(true)
  {
     if(Thread.interrupted()) break;
     rb=sin.read(b,0,512);
     if(rb==-1) break mainloop;
     out.write(b,0,rb);
     log_size+=rb;
     out.flush();
  }
  worker.interrupt();
  wc.close();
  close();
  keepalive=false;
  return;
}

final public void handle_options() throws IOException
{
  int maxforwards=get_and_update_maxforwards();
  if(maxforwards==0)
    {
      /* reply to OPTIONS Message */
      Vector oldheaders=(Vector)headers.clone();
      make_headers(200,null,null,null,0,0,0,null);
      headers.addElement("Allow: GET, HEAD, OPTIONS, TRACE");
      send_headers();
      close();
      keepalive=false;
    }
    else
      direct_request(false);
}
final public void handle_trace() throws IOException
{
  int maxforwards=get_and_update_maxforwards();
  if(maxforwards==0)
    {
      /* reply to trace Message */
      Vector oldheaders=(Vector)headers.clone();
      make_headers(200,"message/http",null,null,-1,0,0,null);
      send_headers();
      StringBuffer sb=new StringBuffer(1024);
      sb.append(httpreq.methodToString(method));
      sb.append(" ");
      sb.append(URL);
      if(httpv>=10)
      {
      sb.append(" HTTP/1.0\r\n");
      int szc=oldheaders.size();
      for(int i=0;i<szc;i++)
      {
        sb.append((String)oldheaders.elementAt(i));
	sb.append("\r\n");
      }
      } else sb.append("\r\n");
      out.writeBytes(sb.toString());
      close();
      keepalive=false;
    }
    else
      direct_request(false);
}

final private int get_and_update_maxforwards()
{
  int maxforwards=-1;
 String s,s1;
  int cachedsize=headers.size();
  for(int i=0;i<cachedsize;i++)
  {
    int j;
    s=(String)headers.elementAt(i);
    j=s.indexOf(':',0);
    if(j==-1) continue;
    s1=s.substring(0,j).toLowerCase();
    if(s1.equals("max-forwards")) {
                            try
			    {
                              String s2=s.substring(j+1).trim();
                              maxforwards=Integer.valueOf(s2).intValue();
			    }
			    catch (NumberFormatException nfe) {continue;}
                            if(maxforwards>0) headers.setElementAt("Max-Forwards: "+(maxforwards-1),i);
                            return maxforwards;
                           }
 }
 return -1; // NONE
}

final public void direct_request(boolean datafromclient) throws IOException
{
 WebConnection wc;
 if(datafromclient==true && ctsize>reqmaxbody)
 {
    keepalive=false;
    send_error(403,"Request body is too large.");
    return; 
 }
 try{
  if(datafromclient==false)
  {
      wc=connectToHost();
      run_request(wc);
  }
  else
  {
  // OPEN NEW CONNECTION TO BE 100% KA TIMEOUT FREE
  wc=new WebConnection(proxyhost,proxyport);
  raw_send_request(wc);  // send request headers
  }
 }
 catch (IOException e)
  {
    if(datafromclient) keepalive=false;
    send_error(500,"Connect to server failed ("+e+"). "+
    httpreq.methodToString(method)+" request canceled.");
    return; 
  }
 try{
 /* precteme data z klienta a odesleme je na server */
 /* pokud nemame ctsize, tak cteme az na konec */
 if(datafromclient) {
	 byte b[]=new byte[512];
	 int rb;
	 if(ctsize<0) keepalive=false; // disable INPUT keepalive
	 // System.out.println("[DEBUG] Need to read "+ctsize+" bytes from client");
	 while(ctsize!=0)
	 {
	  if(ctsize<0) rb=in.read(b);
		 else
		  rb=in.read(b,0,Math.min(ctsize,512));
	  if(rb==-1) break; /* konec dat! */
	  ctsize-=rb;
	  wc.webos.write(b,0,rb);
	 }
	 wc.webos.flush();
         /* otocime to, cteme data ze serveru a posilame je klientu */
         String firstline=null;
         wc.socket.setSoTimeout (request_timeout);
         firstline=wc.webis.readLine();
         // System.out.println("[DEBUG] reply from server: "+firstline);
         read_headers(firstline,wc);
 }
 
   send_headers();
   transfer_object(wc,null,null);
 }
 catch (IOException e1) { wc.close();throw e1;}
}

/* failsafe send request and read+parse reply headers */
final public void run_request(WebConnection wc) throws IOException
{
 String firstline=null;
 boolean retry=false;

 try
 {
     raw_send_request(wc);
     wc.socket.setSoTimeout (request_timeout);
     firstline=wc.webis.readLine();
 }
 catch (IOException e)
 {
     retry=true;
 }
 if(firstline==null) retry=true;
 if(retry)
 {
     //  retry
     if(ConnectionHandler.trace_keepalive && wc.releasedAt>0)
     {
       int tmout=(int)(System.currentTimeMillis()-wc.releasedAt);
       System.out.println("[TRACE "+Thread.currentThread().getName()+"] * Remote end CLOSED alive connection to "+wc+", time="+(tmout/1000)+" s.");
     } else
	 if(mgr.loglevel>1) System.out.println("No response from server "+getURL()+", will retry.");
     /* try to reconnect and resend */
     wc.reconnect();
     raw_send_request(wc);
     wc.socket.setSoTimeout (request_timeout);
     firstline=wc.webis.readLine();
 }

 read_headers(firstline,wc);
}

/* low level send request */
final public void raw_send_request(WebConnection wc) throws IOException
{
 String met=null;
 DataOutputStream sout=wc.webos;
 met=httpreq.methodToString(method);
 sout.writeBytes(met+" "+(rline==null?URL:rline)+" HTTP/1.1\r\n");

 int hscache=headers.size();
 for(int i=0;i<hscache;i++)
   { 
     sout.writeBytes((String)headers.elementAt(i));
     sout.writeBytes("\r\n");
   }
 sout.writeBytes("\r\n");
 sout.flush();
}

final public void close() throws IOException
{
 out.flush();
}

/* reads headers from server and parses it */
final public void read_headers(String firstline,WebConnection wc) throws IOException
{
 DataInputStream in=wc.webis;
 String line;

 if(firstline==null)
                  try{
                        wc.close();
                     }
                  catch(IOException e) {}
                  finally
                  { throw new IOException("Document contains no data!");}

 /* precteme si tedy kod */
 StringTokenizer st;
 st=new StringTokenizer(firstline);
 try
 {
   line=st.nextToken(); /* http/1.0 */
   if(line.equals("HTTP/1.1"))
       wc.keepalive=true;
   else
       wc.keepalive=false;
   /* tady to doufejme spadne pri remote HTTP 0.9 serveru */
   httprc=Integer.valueOf(st.nextToken()).intValue();
 }
 catch (Exception http09)
 { /* 502 - Bad gateway */
  wc.close();
  send_error(502,"Remote servers talking with old HTTP 0.9 protocol are not supported.");
  return;
 }

 headers=new Vector();
 ims=0; /* pro last modified */
 exp=0;
 ctsize=-1;
 encoding=null;
 etag=null;
 location=null;
 ctype="text/html"; // like Mozilla does
 reload=false; /* pouzivano pro pragma: no-cache */
 chunked=false;
 int pnci=-1;
 int expi=-1;
 int cci=-1;
 line=firstline;
 /* cteme hlavicky */
 while(true)
 {
      int j;
      String s1,s2;
      if(trace_reply)
        System.out.println("[TRACE "+Thread.currentThread().getName()+"] < "+line);
      line=in.readLine();
      if(line==null) break;
      if(line.length()==0) break;
      headers.addElement(line);
      j=line.indexOf(':',0);
      if(j==-1) continue;
      s1=line.substring(0,j).toLowerCase();
      s2=line.substring(j+1).trim();

      if(s1.equals("pragma")) { 
                                 reload=true;
				 /* pnc is last hdr for now, adding more hdr later */
				 pnci=headers.size()-1;
				 continue;
			      }
      if(s1.equals("cache-control"))
      {
	  if(s2.indexOf("private")>-1)
	  {
	      if(cacheable) cacheable=cache_private;
	      continue;
	  } else
	      if(s2.indexOf("no-")>-1)
	      {
		  reload=true;
		  cci=headers.size()-1;
		  continue;
	      }
      }
      if(s1.equals("vary"))
      {
	  if(cacheable) cacheable=cache_vary;
	  continue;
      }
      if(s1.equals("content-length"))
             try
             {
                 ctsize=Integer.valueOf(s2).intValue();
             }
              catch (Exception ignore) {}
              finally { continue;}

      if(s1.equals("content-type")) { ctype=s2; continue;}
      if(s1.equals("content-encoding")) { encoding=s2; continue;}
      if(s1.equals("transfer-encoding"))
      {
	  if(s2.equalsIgnoreCase("chunked"))
	  {
	      chunked=true;
	      headers.removeElementAt(headers.size()-1);
	  }
	  continue;
      }
      if(s1.equals("connection") || s1.equals("proxy-connection"))
      {
	  if(s2.length()==5) 
	      wc.keepalive=false;
	  else
	      wc.keepalive=true;
	  headers.removeElementAt(headers.size()-1);
	  continue;
      }
      if(s1.equals("etag")) { etag=s2; continue;}
      if(s1.equals("location")) { location=s2; continue;}

      if(s1.equals("last-modified")) {
                                        try{
                                        ims=new Date(s2).getTime();
                                        }
                                        catch (IllegalArgumentException e)
                                         { ims=0;}
                                        continue;
                                      }

      if(s1.equals("expires")) {          try{
                                        exp=new Date(s2).getTime();
                                        }
                                        catch (IllegalArgumentException e)
                                         { exp=886828316241L; /* allready expired */}
					finally
					{
					  expi=headers.size()-1;
					  continue;
					}
                                      }
      if(mgr.allow_cookies_to!=null &&
	 allow_all_session_cookies &&
         s1.equals("set-cookie")
	)
      {
	 s2=line.toLowerCase();
	 /* is this a pernament cookie? */
	 if(s2.indexOf("expires")!=-1 || s2.indexOf("max-age")!=-1)
	 {
           boolean allowed=false;
	   /* needs to scan */
	   for(int i=mgr.allow_cookies_to.length-1;i>=0;i--)
	     if(hostname.endsWith(mgr.allow_cookies_to[i])) 
	     {
	        allowed=true;
	        break;
	     }

	   if(!allowed)
	   {
	     /* replace cookie content */
             StringBuffer newCook=new StringBuffer(line);
	     if(trace_cookie)
	       System.out.println("[TRACE "+Thread.currentThread().getName()+"] @ Unallowed persistent cookie "+newCook+" from "+hostname);
	    
	     int ei=0;
	     /* remove expires from reply */
	     while(true)
	     {
	       ei=s2.indexOf("expires",ei);
	       if(ei>-1)
	       {
	         newCook.setCharAt(ei,'X');
		 ei++;
	       } 
	       else break;
	     }
	     
	     ei=0;

	     /* remove max-age from reply */
	     while(true)
	     {
	       ei=s2.indexOf("max-age",ei);
	       if(ei>-1)
	       {
	         newCook.setCharAt(ei,'X');
		 ei++;
	       } 
	       else break;
	     }
	     
	     headers.setElementAt(newCook.toString(),headers.size()-1);
	   }
	 }
      }

 } /* hlavicky */
 
 /* need to restart reader on 100-Continue */
 if(httprc==100)
 {
     read_headers(in.readLine(),wc);
     return;
 }

 if(ctsize>maxbody)
 {
      wc.close();
      send_error(403,"Reply body is too large.");
      return;
 }

 /* disable client tkeepalive for unknown content size */
 if(ctsize==-1 && httprc!=304 && httprc!=204) keepalive=false;

 /* Generate headers according to client's preference */
 if(keepalive==false)
 {
     headers.addElement("Proxy-Connection: close");
     headers.addElement("Connection: close");
 }else
 {
     headers.addElement("Proxy-Connection: Keep-Alive");
     headers.addElement("Connection: Keep-Alive");
 }

 if(ctsize>maxcacheable) cacheable=false;
 else
  if(method!=httpreq.REQUEST_GET) cacheable=false; /* CACHE ONLY GET REQUESTS */

 if(exp!=0) /* Check Expire: against Date: header */
 {
      /* try to get now from Date: header */
      long now=0;
      for(int i=headers.size()-1;i>=0;i--)
      {
       String h,s1,s2;
       int j;
       h=(String)headers.elementAt(i);
       j=line.indexOf(':',0);
       if(j==-1) continue;
       s1=line.substring(0,j).toLowerCase();
       if(s1.equals("date")) {
                                        try{
                                        s2=line.substring(j+1).trim();
                                        now=new Date(s2).getTime();
                                        }
                                        catch (IllegalArgumentException e)
                                         { now=0;}
                                        break;
                                      }
      } /* Date: header hunting */
      if(now==0) now=System.currentTimeMillis();
      if(exp<=now)
      {  /* Expire date NOW or in the past is the same as Pragma: no-cache */

          exp=0; // kill expire info
	  reload=true;
          headers.removeElementAt(expi); /* Drop EXPIRE: header !*/
          if(pnci>expi) pnci--;
	  if(cci>expi) cci--;
          if(pnci==-1) /* if removed Expire, generate PNC */
	                  { headers.addElement("Pragma: no-cache");
	                    pnci=headers.size()-1;
			  }
      } /* exp<=now, killed */
      else
       { /* fix expire to out local time */
         exp=System.currentTimeMillis()+(exp-now);
       }
 }
 /*  ctype dm hook */
 if(dmachine_ctype_mask!=null && httprc!=304)
     if(ctsize==-1 || (ctsize>0 && ctsize>dmachine_ctype_min)
       )
 {
dmachine:for(int i=dmachine_ctype_mask.length-1;i>=0;i--)
    {
       if(dmachine_ctype_mask[i].matches(ctype))
       {
	   if(mgr.dmachine_ctype_ignore!=null)
	   {
	      for(int j=mgr.dmachine_ctype_ignore.length-1;j>=0;j--)
		 if(mgr.dmachine_ctype_ignore[j].matches(URL))
		     break dmachine;
	   }
           synchronized(httpreq.mgr)
		 {
		     Hashtable dmq=ui.load_dm_queue();
		     dmq.put(URL,URL);
		     ui.save_dm_queue(dmq);
		     wc.close();
		     send_error(202,"Request Accepted<br>\nURL <a href=\""+URL+"\">"+URL+"</a> was successfully added to download machine queue file. Matched by Content-Type mask. Received content-type is: "+ctype);
		     return;
		 }
	 }
     }
 }
 
 if(cacheable==false) { return;}
 /* set Expire to MaxAge/2 on error codes 300 and 301
  * this prevents from too early permanent redirect refresh */
 if( (httprc==301 || httprc==300) && exp==0) exp=System.currentTimeMillis()+mgr.max_age/2;

 /* CHECK:
      if we are removing PNC, could we also kill GOOD expire header in future?

      NO: PNC and Good Expire should never happen
      */
 switch(pnocache)
 {
  case 1:
        reload=false;
        if(pnci>=0) {
	             headers.removeElementAt(pnci);
		     if(cci>pnci) cci--;
	}
	if(cci>=0) headers.removeElementAt(cci);

	break;
  case 2:
        if(ctype.startsWith("image"))
	{
		reload=false;
		if(pnci>=0) {
			     headers.removeElementAt(pnci);
			     if(cci>pnci) cci--;
		}
		if(cci>=0) headers.removeElementAt(cci);
	}
	break;
  case 3:
         if(!ctype.startsWith("text"))
	 {
		if(pnci>=0) {
			     headers.removeElementAt(pnci);
			     if(cci>pnci) cci--;
		}
		if(cci>=0) headers.removeElementAt(cci);
		reload=false;
	 }
	 break;
  case 4:
  	 if(location==null)
         {
		if(pnci>=0) {
			     headers.removeElementAt(pnci);
			     if(cci>pnci) cci--;
		}
		if(cci>=0) headers.removeElementAt(cci);
		reload=false;
	 }
         break;
 }
 /* set uncacheable */
 if(reload==true) cacheable=false;
}

final public void transfer_object(WebConnection wc, OutputStream file,cachedir dir) throws IOException
{
 int read=0;
 int i;
 long whenaborted;
 boolean clientdead;
 boolean filedead;
 DataInputStream sin=wc.webis;
 byte b[]=new byte[4096];
 clientdead=filedead=false;
 whenaborted=0;
 int todo;
 if(wc.socket!=null) wc.socket.setSoTimeout(read_timeout);
 if(chunked==true)  ctsize=-1;  // drop ctsize on chunked streams
 if(ctsize<0) {
     todo=Integer.MAX_VALUE; // read all input data
 }
  else
      todo=ctsize;

chunk:while(true)
 {
     if(chunked)
     {
	// this small part of code is from Rabbit2 HttpInputStream code
	// getChunkSize() function.
	// Copyright (c) 1998-2002 Robert Olofsson and others
	/* get a chunk size from http stream */
        String line;
        line = sin.readLine ();
        if (line == null)
             throw new IOException ("EOF when reading chunk block size");
        StringTokenizer st = new StringTokenizer (line, "\t \n\r(;");
        if (st.hasMoreTokens ()) {
           String hex = st.nextToken ();
           try {
                todo = Integer.parseInt (hex, 16);
           } 
	   catch (NumberFormatException e) {
             throw new IOException ("Chunk size is not a hex number: '" + line + "', '" + hex + "'.");
           }
        } else {
	     throw new IOException ("Chunk size is not available: '"+line+"'");
        }
     }
     if(todo==0 && chunked)
     {
	 String line;
	 // skip Trailer
	 while( (line=sin.readLine())!=null)
	 {
	     if(line.length()==0) break;
	 }
	 break chunk;

     }
     while(todo>0)
     {
      try{
      /* precist ze serveru */
      i=sin.read(b,0,Math.min(todo,4096));
      }
      catch (IOException e) { 
	  /* chyba pri cteni dat ze serveru */
	  if(file!=null) file.close();
	  ctsize=read;
	  throw e;
      }
      if(i==-1) {
	  wc.keepalive=false;
	  break; /* end of Data stream? */
      }
      
      todo-=i;
      read+=i;

      /* zapsat do diskoveho souboru */
      if(file!=null) { dir.dirty=true;
		       try {
			    file.write(b,0,i);
			   }
		       catch(IOException writerr)
		       {
			try
			 {
			  file.close();
			 }
			catch (IOException ignore) {}
			finally { file=null;filedead=true;}
		       }
		       }

      /* zapisovat do klienta opatrne! */
      try{
      if(!clientdead) { out.write(b,0,i);log_size+=i;}
       else
	if(qa_minlen<0 && System.currentTimeMillis()-whenaborted>qa_maxtime)
				{ ctsize=read;
				  wc.close();
				throw new IOException("Aborted transfer time timed out");}
      }
      catch(IOException e) { if(file==null) /* not caching */
				    { ctsize=read;wc.close();throw e; }
			     if(CheckQuickAbort(read)) { ctsize=read;file.close();wc.close();throw e;}
			     clientdead=true;
			     whenaborted=System.currentTimeMillis();
			     }
     }
     // end off server input data
     if(!chunked) break;
     sin.readLine(); // read CRLF
 }
 ConnectionHandler.releaseConnection(wc);

 /* opatrne flush klienta na konci transferu */
 try{
     out.flush();
 } catch (IOException ignore) {}

 if(file!=null) { file.flush();file.close();}

 if(ctsize>0 && read<ctsize && method!=httpreq.REQUEST_HEAD) {
                                throw new IOException("Object too small "+read+"<"+ctsize);
			     }
 if(filedead) throw new IOException("Write error (disk full?)");			
 ctsize=read;
}

/* add if-mod-since header */
final public void add_ims()
{
 add_ims(ims);
}

final public void add_ims(long when)
{
 if(etag!=null) headers.insertElementAt("If-None-Match: "+etag,1);
 if(when==0) return;
 headers.insertElementAt("If-Modified-Since: "+printDate(new Date(when)),0);
}

final public void add_header(String header)
{
 if(header==null) return;
 headers.addElement(header);
}

/* odesle obdrzene hlavicky od serveru klientu */
/* nereportuje io chyby */
final public void send_headers()
{
 if(httpv<10) return;
 try{
 out.writeBytes("HTTP/1.0 "+httprc+" OK\r\n");
 int headerscache=headers.size();
 for(int i=0;i<headerscache;i++)
   { out.writeBytes((String)headers.elementAt(i));
     out.writeBytes("\r\n");
   }
 out.writeBytes("\r\n");
 out.flush();
 }
 catch (IOException e)
   {
       //  TODO:  rmv me
    // System.out.println("[DEBUG] IO err on req.send_headers()");
    keepalive=false;
    try{
        out.close();
    }
    catch (IOException e1) {}
   }
}

final public void make_headers(int rc,String ctype,String enc,String loc,int sz,long lm,long exp,String et)
{
 httprc=rc;
 headers=new Vector();
 Date d=new Date();
 if(ctype!=null) headers.addElement("Content-Type: "+ctype);
 if(enc!=null) headers.addElement("Content-Encoding: "+enc);
 if(sz>=0) {
             headers.addElement("Content-Length: "+sz);
	   }
 else
     if(rc!=304) keepalive=false;
 if(lm!=0) headers.addElement("Last-Modified: "+printDate(new Date(lm)));

 if(et!=null) headers.addElement("ETag: "+et);
 /*
   SETUP: Uncoment following line if you want to SC don't send Expired:
     headers from the past.
   if(exp>d.getTime()) headers.addElement("Expires: "+printDate(new Date(exp)));
 */
 if(exp>0) headers.addElement("Expires: "+printDate(new Date(exp)));
 if(loc!=null) headers.addElement("Location: "+loc);
 /* prevent TCP/IP FIN2 state !! */
 if(keepalive==false)
 {
     headers.addElement("Proxy-Connection: close");
     headers.addElement("Connection: close");
 }else
 {
     headers.addElement("Proxy-Connection: Keep-Alive");
     headers.addElement("Connection: Keep-Alive");
 }
 /* less important , may be ignored in old Netscapes */
 headers.addElement("Date: "+printDate(d));
 headers.addElement("Server: "+scache.VERSION);
}

final public void setRequestTo(String rq)
{
  this.rline=rq;
}

/* vygeneruje chybove hlaseni a posle jej klientu */
final public void send_error(int errorrc,String msg) throws IOException
{
 StringBuffer bd=new StringBuffer(1024); // body

bd.append("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">\n<HTML><HEAD><TITLE>Error</TITLE></HEAD>\n<BODY><h2>");
bd.append(errorrc);
bd.append(" ");
bd.append(msg);
bd.append("</h2>\r\n");
if(URL!=null) {
    bd.append("When trying to load <a href=\"");
    bd.append(URL);
    bd.append("\">");
    bd.append(URL);
    bd.append("</a>.\n");
}
bd.append("<hr noshade size=1>\r\nGenerated by ");
bd.append(httpreq.visible_hostname);
bd.append(" (");
if(httpreq.visible_link!=null)
{
  bd.append("<a href=\"");
  bd.append(httpreq.visible_link);
  bd.append("\">");
}
bd.append(scache.VERSION);
if(httpreq.visible_link!=null)
{
 bd.append("</a>");
}
bd.append(")\r\n</BODY></HTML>\r\n");

/* setup log */
httprc=errorrc;
make_headers(errorrc,"text/html",null,null,bd.length(),new Date().getTime(),
	                                          new Date().getTime()+5*60*1000L,null);
send_headers();
sendString(bd.toString());
close();
throw new EOFException("Request processing canceled");
}

/* return true if the request should be aborted */
final private boolean
CheckQuickAbort(int readb)
{
    if (!cacheable)
          {
	     if(trace_abort==true)
	       System.out.println("[TRACE "+Thread.currentThread().getName()+"] # Load aborted (non-cacheable): "+URL);
             return true;
	  }

    if (qa_minlen < 0)
	{
	 /* disabled */
	  if(trace_abort==true)
	    System.out.println("[TRACE "+Thread.currentThread().getName()+"] # Load continued (quick abort disabled): "+URL);
	 return false;
	}

    if(ctsize!=-1) /* Content-size is known */
    {
    if (readb > ctsize)
        {
	  if(trace_abort==true)
	    System.out.println("[TRACE "+Thread.currentThread().getName()+"] # Load aborted (loaded more data than expected): "+URL);
	return true;
	}

    if ((ctsize - readb) < qa_minlen)
        {
	  /* only little bytes left */
	  if(trace_abort==true)
	    System.out.println("[TRACE "+Thread.currentThread().getName()+"] # Load continued (less than minlen bytes left): "+URL);
	return false;
	}
    if ((ctsize - readb) > qa_maxlen)
       {
	  /* too much left to go */
	  if(trace_abort==true)
	    System.out.println("[TRACE "+Thread.currentThread().getName()+"] # Load aborted (more than max bytes left): "+URL);
	return true;
       }
    if ( (float)readb / ctsize > qa_percent)
        {
	  /* past point of no return */
	  if(trace_abort==true)
	    System.out.println("[TRACE "+Thread.currentThread().getName()+"] # Load continued (percent ratio is good): "+URL);
	return false;
	} else
	{
	  if(trace_abort==true)
	    System.out.println("[TRACE "+Thread.currentThread().getName()+"] # Load aborted (percent ratio is bad): "+URL);
	return true;
	}
    }
    
    if(trace_abort==true)
      System.out.println("[TRACE "+Thread.currentThread().getName()+"] # Load aborted (unspecified size in reply): "+URL);
    return true; /* STOP DOWNLOADING! */

    /*
    return false;  no dobra povolime to, ale vypadne to na timeout nebo
    na pocet dat */
}

final public static String printDate(Date d)
{

 try
  {
   return formatter.format(d);
  }
 catch (Exception z)
  {
   z.printStackTrace();
   System.out.println("[JAVA_INTERNAL_ERROR] SimpleDateFormat.format(d) failed\nDate format=EEE, dd MMM yyyy HH:mm:ss 'GMT' BadDate(formated)="+d+" time(long)="+d.getTime()+" THIS IS A JAVA BUG.\nREPORT TO      http://java.sun.com/cgi-bin/bugreport.cgi");
  }
  return null;
}

final public void nocache()
{
 cacheable=false;
}

final public void sendString(String arg) throws IOException
{
 out.writeBytes(arg);
 log_size=arg.length();
}

final public void sendBytes(byte b[]) throws IOException
{
 out.write(b);
 log_size=b.length;
}

}
