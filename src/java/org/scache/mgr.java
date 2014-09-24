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
 *
 * @modified Brad GNUberg, bkn3@columbia.edu - Updated all references
 * to InetAddress.getByName to be P2PInetAddress.getByAddress to
 * work with JXTA and to avoid any network calls that attempt to
 * turn the Name into an IP address (getByAddress avoids making any
 * network calls).
 */

import java.io.*;
import java.util.*;
import java.text.*;
import java.util.zip.*;
import java.net.*;

import org.p2psockets.*;

public final class mgr implements Runnable{

public static final String DEFAULTNAME=".welcome";
/* HTTP PROXY */
public static InetAddress http_proxy; // address
public static String http_proxy_auth; /* auth header */
public static int http_proxy_port;    // parentproxyport

/* security proxy */
public static InetAddress https_proxy; 
public static int https_proxy_port;

/* FTP PROXY */
public static InetAddress ftp_proxy; // address
public static String ftp_proxy_auth; /* auth header */
public static int ftp_proxy_port;    // parentproxyport

/* our setup */
public static int ourport;
private static InetAddress ouraddress;

public static final long SAVETIMER=1000L*60*3; /* 3 min. */
public  static String cache_dir;
private static String custom403;
public static int swap_level1_dirs= 4;
public static int swap_level2_dirs= 4;
public  static String[] no_proxy;
public  static String[] allow_cookies_to;
public static boolean case_sensitive;

/* access rulez */
public   static  boolean cacheonly;
public   static  regexp[] fail;
public   static  gnu.rex.Rex regex_fail;
public   static  regexp[] nocache;
public   static  regexp[] pass;
private  static  int allowconnect[];
public   static  boolean trace_fail,trace_remap,trace_redirect;
public   static  int loglevel;

/* time stamps */
private  static  long pass_timestamp;
private  static  long fail_timestamp;
private  static  long regex_fail_timestamp;
private  static  long cookie_timestamp;
private  static  long redir_timestamp;
private  static  long remap_timestamp;

public   static  String cookie_filename;
public   static  String pass_filename;
public   static  String fail_filename;
public   static  String regex_fail_filename;
public   static  String redir_filename;
public   static  String remap_filename;

/* refresh strategy - multiple */
public   static regexp[] refresh;
private  static long minage[];
private  static long maxage[];
private  static long reloadage[];
private  static float lastmodf[];
private  static long expireage[];
private  static long redirage[];

/* simple refresh p. */
public   static long min_age;
public   static long max_age;
public   static long reload_age;
public   static long expire_age;
public   static long redir_age;
public   static float lmfactor;

/* redirects */
private  static regexp[] redirfrom;
private  static String[] redirto;
private  static int[]    redirpart;

/* remaps */
private  static regexp[] remapfrom;
private  static String[] remapto;
private  static int[]    remappart;

/* cached hot directories */
private  static Hashtable dircache;

/* flags */
private  static String shutdownflag,immediate_shutdownflag;
public static boolean clear_flags_on_start=true;
private static int flag_check_interval;
private static String aliveflag;

/* log for statistic */
private static String stat_log;

/* download machine */
public   static  String   dmachine_queue;
private  static  regexp[] dmachine_mask;
public   static  regexp[] dmachine_ignore;
public   static  regexp[] dmachine_ctype_ignore;

mgr()
{
 dircache=new Hashtable(40);

 /* static intit */
 request.formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
 httpreq.formatter.setTimeZone(TimeZone.getDefault());
 /* compute timezone in apache log style format */
 int ofs=TimeZone.getDefault().getRawOffset();
 if(TimeZone.getDefault().inDaylightTime(new Date())) ofs+=60*60*1000;
 httpreq.timezone=(ofs<0?" -":" +");
 ofs=Math.abs(ofs);
 httpreq.timezone=httpreq.timezone+
            (ofs/(60*60*1000)<10 ? "0"+ofs/(60*60*1000): ""+ofs/(60*60*1000));
ofs-=(ofs/(60*60*1000))*60*60*1000;
ofs/=60*1000;
httpreq.timezone=httpreq.timezone+(ofs<10? "0"+ofs : ""+ofs)+"] \"";
httpreq.newline=(String)System.getProperties().get("line.separator"); 

 /* init to good starting defaults */
 scache.listenbacklog=511;
 cacheonly=false;
 cache_dir="store";
 http_proxy_port=3128; /* default squid parent proxy port */
 ui.uiport=0;
 ui.uiadr=null;
 ui.loader_add_missing=0;
 ui.loader_add_reloads=0;
 ui.loader_queue_file=null;
 ui.loader_refresh_depth=ui.loader_add_depth="0";
 ui.searchcachefactor=80.0;
 ourport=8080; /* well known webcache port     */
 request.default_forward_for="forwarder.is.not.set";
 swap_level1_dirs= swap_level2_dirs=4;
 request.maxbody=Integer.MAX_VALUE;
 request.reqmaxbody=Integer.MAX_VALUE;
 request.maxcacheable=Integer.MAX_VALUE;
 lmfactor=0.2f;
 min_age=30*60*1000L;         /* 30 minut */
 max_age=7*24*60*60*1000L;    /* 7 dnu */
 expire_age=1000L*60*5;       /* 5 minutes */
 redir_age=15*1000L;          /* 15 seconds */
 request.qa_minlen=-1;        /* quick abort disable */
 request.qa_maxtime=1*60*60*1000L; /* max luxovaci cas 1 min */
 httpreq.client_timeout=30*1000; /* 30 sec. read from cl. timeout */
 request.read_timeout=4*60*1000; /* 4 min. read from net timeout  */
 request.request_timeout=45*1000; /* 45 seconds, like Opera */
 cacheobject.keep_deleted=false;
 cacheobject.generate_lastmod=true;
 cacheobject.hide_errors=false;
 cacheobject.defaultname=DEFAULTNAME;
 cacheobject.force_ims_on_reloads=true;
 request.remove_pragma_no_cache=false;
 request.referer_hack=0;
 request.fake_referer=null;
 request.fake_user_agent=null;
 request.proxyvia=true;
 request.cache_protected=false;
 request.cache_private=false;
 request.cache_vary=true;
 case_sensitive=true;
 httpreq.visible_hostname="smart.cache";
 httpreq.visible_link=null;
 cachedir.readonly=false;
 clear_flags_on_start=true;
 trace_fail=false;
 trace_remap=false;
 trace_redirect=false;
 httpreq.trace_url=false;
 request.trace_request=false;
 request.trace_reply=false;
 request.trace_abort=false;
 request.trace_cookie=false;
 cacheobject.trace_refresh=false;
 ConnectionHandler.trace_keepalive=false;
 httpreq.trace_inkeepalive=false;
 stat_log=null;
 httpreq.log_common=true;
 flag_check_interval=35*1000;
 cacheobject.auto_compress=0;
 cacheobject.auto_decompress=0;
 dmachine_queue=null;
 dmachine_mask=null;
 dmachine_ignore=null;
 dmachine_ctype_ignore=null;
 request.dmachine_ctype_mask=null;
 request.dmachine_ctype_min=30*1024;
 cacheobject.dmachine_offmask=null;
 loglevel=4;

 /* for safety */
 cacheobject.end_dot=false;
 cacheobject.escape_backslash=true;

 /* gif image */
 request.GIF=request.GIF0;
}

public final void check_filesystem()
{
 if(
      (cache_dir.length()==2 && cache_dir.charAt(1)==':') || /* c: */
      (cache_dir.length()==1 && (cache_dir.charAt(0)=='/' || cache_dir.charAt(0)=='\\') ) || /* / */
      (cache_dir.length()==3 && cache_dir.charAt(1)==':' && ( cache_dir.charAt(2)=='/' || cache_dir.charAt(2)=='\\') )
   ) throw new IllegalArgumentException("Refusing to use ROOT directory '"+cache_dir+"' as data storage. Create some subdirectory instead; this should prevent you from doing something stupid. You can use ROOT only if you have dedicated partition for scache date. In this case remove this test from source code and recompile. See exception stack dump for file and line of this test.");

 if(cache_dir.endsWith(File.separator))
     cache_dir=cache_dir.substring(0,cache_dir.length()-File.separator.length());

 System.err.println("Checking filesystem ("+cache_dir+")");

 if(!cachedir.readonly)
 {
   writeableFilesystem();
 }
 if(!new File(cache_dir).isDirectory())
   {
     System.out.println("[SMARTCACHE_ERROR] Directory "+cache_dir+" can not be created.");
     System.out.println("[SMARTCACHE_ERROR] Turning caching off, no files will be cached.");
     cachedir.readonly=true;
   }

 if(cachedir.readonly)
    System.err.println("   Using cache directory in READ-ONLY mode.");
  else
 {
   cacheobject.end_dot=endDotFilesystem();
   System.err.println("   Filesystem allows ending dot in filename: "+cacheobject.end_dot);
   cacheobject.escape_backslash=!(filesystemCanUseBackslash());
   System.err.println("   Filesystem allows backslash in filename : "+!(cacheobject.escape_backslash));
   garbage.realdirsize=filesystemHasRealDirsize();
   System.err.println("   Filesystem reports real directory size  : "+garbage.realdirsize);
 }
 System.err.println("");
}

public void go()
{

 scache daemon=new scache(ourport,ouraddress);
 Thread saver = new Thread(this);
 saver.start(); /* start background directory saver... */

 Java13_routines.activate_ShutdownHook(saver);

 scache.faststart=true; // always enable Faststart

 if(scache.faststart==false)
 {
   System.err.println("Checking for latest Smart Cache version");
   String lv=getLatestVersion();
 if (lv==null) lv="(Error connecting to server)";
 else if (!lv.equals(scache.CACHEVER)) lv+="\007 ... its time for update!";
   else
    lv+=" ... up to date";
 System.err.println("   Latest version is "+lv);
 }
 
 /* refresh pattern hack */
 if(refresh!=null && -1==isInRegexpArray("*",refresh))
 {
  refresh=addRegexpToArray("*",refresh,false);

  /* natahnout ! */
  reloadage=incLongArraySize(reloadage);
  minage=incLongArraySize(minage);
  maxage=incLongArraySize(maxage);
  lastmodf=incFloatArraySize(lastmodf);
  expireage=incLongArraySize(expireage);
  redirage=incLongArraySize(redirage);

  /* nastavit hodnoty */
  reloadage[reloadage.length-1]=reload_age;
  minage[minage.length-1]=min_age;
  maxage[maxage.length-1]=max_age;
  lastmodf[lastmodf.length-1]=lmfactor;
  expireage[expireage.length-1]=expire_age;
  redirage[redirage.length-1]=redir_age;
 }

 if(clear_flags_on_start)
 {
   checkFlag(shutdownflag); // delete pending shutdown flag on start
   checkFlag(immediate_shutdownflag); // delete pending shutdown flag on start
 }
 new Thread(new ui()).start(); // run UI
 ConnectionHandler.init();     // run KeepAlive cleaner
 touch_flag(aliveflag);
 System.out.println(new Date()+" "+scache.VERSION+" ready on "+ourport+"/"+(ouraddress==null ? "*" : ouraddress.getHostAddress()));
 request.via_header=" 1.0 "+httpreq.visible_hostname+" ("+scache.VERSION+")";
 ouraddress=null; // GC IT!
 /* fallback FTP proxy to http_proxy */
 if(ftp_proxy==null)
 {
     ftp_proxy=http_proxy;
     ftp_proxy_port=http_proxy_port;
     ftp_proxy_auth=http_proxy_auth;
 }
 daemon.httpdloop(); // enter main loop
}

private final static boolean filesystemCanUseBackslash()
{
 if(cachedir.readonly) return false;
 File test=new File(cache_dir,"test\\slash");
 try{
 new FileOutputStream(test).close();
 }
 catch (IOException e1) {
                          return false;
			}
 test.delete();
 return true;
}
private final static boolean filesystemHasRealDirsize()
{
  try
   {
    if(new File(".").length()>0) return true;
    else return false;
  }
  catch (Exception grrrrrrrrrrrrrrrr)
    {
      return false;
    }
   // return false; /* Uncomment to keep some JAVAC compilers happy */
}

private final static String getLatestVersion()
{
 URL u=null;
 try
 {
      u=new URL("http://home.worldonline.cz/~cz210552/cachever.txt");
 }
 catch (MalformedURLException e)
  { return null;}
 String ver=null;
 try
 {
   ver=new DataInputStream(u.openStream()).readLine();
   return ver;
 }
 catch (IOException e)
  { return null;}

}

private final void writeableFilesystem()
{
   (new File(cache_dir)).mkdirs();
   new File(cache_dir,"canwritelongname.txt").delete();
   try{
       new FileOutputStream(cache_dir+File.separator+"canwritelongname.txt").close();
       new File(cache_dir,"canwritelongname.txt").delete();
      }
   catch (IOException e1) {
			      cachedir.readonly=true;
		          }
}

private final boolean endDotFilesystem()
{
 try{
 new FileOutputStream(cache_dir+File.separator+"dot.").close();
 }
 catch (IOException e1) {
                          return false;
			}

 File d=new File(cache_dir);
 String names[];
 names=d.list();
 new File(cache_dir,"dot.").delete();
 if(names==null) return false;

 for(int i=0;i<names.length;i++)
  {
   if(names[i].toLowerCase().equals("dot.")) return true;
  }

 return false;
}

public final void read_config(String cfgfile)
{
 try{
 String line,token;
 StringTokenizer st;
 int lineno=0;
 
 cfgfile=scache.cfgdir+File.separator+cfgfile;

 DataInputStream dis=new DataInputStream(new BufferedInputStream(new FileInputStream(cfgfile)));
 while ( (line = dis.readLine()) != null)
 {
      lineno++;
      if(line.startsWith("#")) continue;
      st=new StringTokenizer(line);
      if(st.hasMoreTokens()==false) continue;
      token=st.nextToken();
      try
      {

          if(token.equalsIgnoreCase("visible_link")) {httpreq.visible_link=st.nextToken();
          					    continue;
						    }
          if(token.equalsIgnoreCase("stat_log"))   {stat_log=st.nextToken();
          					    continue;
						    }
          if(token.equalsIgnoreCase("visible_hostname")) {httpreq.visible_hostname=st.nextToken();
          					    continue;
                                                    }
          if(token.equalsIgnoreCase("ui_hostname")) {ui.ui_hostname=st.nextToken();
          					    continue;
                                                    }
          if(token.equalsIgnoreCase("default_forward_for")) {request.default_forward_for=st.nextToken();
          					    continue;
                                                    }

         if(token.equalsIgnoreCase("defaultname")) {cacheobject.defaultname=st.nextToken();
          					    continue;
                                                    }


          if(token.equalsIgnoreCase("bindaddress")) {
                                                    String ip=st.nextToken();
                                                    if(ip.equals("*")) { ouraddress=null;continue;}
                                                    try{
                                                    ouraddress=InetAddress.getByName(ip);
                                                    }
                                                    catch (IOException e) { System.err.println("[SMARTCACHE-ERROR] Can not resolve my BindAddress: "+ip);}
          					    continue;
                                                    }

          if(token.equalsIgnoreCase("ui_bindaddress")) {
                                                    String ip=st.nextToken();
                                                    if(ip.equals("*")) { ouraddress=null;continue;}
                                                    try{
                                                    ui.uiadr=InetAddress.getByName(ip);
                                                    }
                                                    catch (IOException e) { System.err.println("[SMARTCACHE-ERROR] Can not resolve my UI BindAddress: "+ip);}
          					    continue;
                                                    }


          if(token.equalsIgnoreCase("shutdown_flag")) {shutdownflag=st.nextToken();
          					    continue;
                                                    }
          if(token.equalsIgnoreCase("alive_flag")) {aliveflag=st.nextToken();
          					    continue;
                                                    }
          if(token.equalsIgnoreCase("mime_types")) {repair.loadMimeTypes(st.nextToken());
          					    continue;
                                                    }


          if(token.equalsIgnoreCase("immediate_shutdown_flag")) {immediate_shutdownflag=st.nextToken();
          					    continue;
                                                    }

          if(token.equals("case_sensitive_matching")) {
                                                           case_sensitive=getYesNo(st.nextToken());
                                                           continue;
                                                      }
          if(token.equals("log_common")) {
                                                           httpreq.log_common=getYesNo(st.nextToken());
                                                           continue;
                                                      }
						
          if(token.equals("auto_compress")) {
          				                   int c=Integer.valueOf(st.nextToken()).intValue();
                                                           if(c>0 && c<512) c=512; else if(c<0) c=0;
							   cacheobject.auto_compress=c;
                                                           continue;
                                                      }
          if(token.equals("auto_decompress")) {
          				                   char c=(char)Integer.valueOf(st.nextToken()).intValue();
                                                           if(c!=0) cacheobject.auto_decompress=c; else cacheobject.auto_decompress=0;
                                                           continue;
                                                      }
          if(token.equals("allowconnect")) {
                                                    while(st.hasMoreTokens())
						    {
          				                   int c;
							   c=Integer.valueOf(st.nextToken()).intValue();
							   allowconnect=incIntegerArraySize(allowconnect);
							   allowconnect[allowconnect.length-1]=c;
						    }
                                                           continue;
                                                      }

          if(token.equalsIgnoreCase("cache_dir")) {
	                                cache_dir=st.nextToken("\r\n").trim();
          			        continue;
                                                  }

          if(token.equalsIgnoreCase("http_port")) {ourport=Integer.valueOf(st.nextToken()).intValue();
          					    continue;
                                                    }

          if(token.equalsIgnoreCase("ui_port")) {ui.uiport=Integer.valueOf(st.nextToken()).intValue();
          					    continue;
                                                    }
          if(token.equalsIgnoreCase("loader_add_missing")) {ui.loader_add_missing=Integer.valueOf(st.nextToken()).intValue();
          					    continue;
                                                    }
          if(token.equalsIgnoreCase("loader_add_refresh")) {ui.loader_add_reloads=Integer.valueOf(st.nextToken()).intValue();
          					    continue;
                                                    }
          if(token.equalsIgnoreCase("loader_queue_file")) {ui.loader_queue_file=st.nextToken();
          					    continue;
                                                    }
          if(token.equalsIgnoreCase("loader_refresh_depth")) {ui.loader_refresh_depth=st.nextToken();
          					    continue;
                                                    }
          if(token.equalsIgnoreCase("loader_add_depth")) {ui.loader_add_depth=st.nextToken();
          					    continue;
                                                    }
          if(token.equalsIgnoreCase("referer_hack")) {
                                                     String rh=st.nextToken();
                                                    try
                                                      {
				                       request.referer_hack=(char)Integer.valueOf(rh).intValue();
                                                      }
                                                    catch (NumberFormatException e)
                                                     {
						      System.out.println("[ERROR] Referer_hack do not longer accept non-numeric agument `"+rh+"`.\nUse fake_referer instead.");
                                                     }
          					    continue;
                                                    }

          if(token.equalsIgnoreCase("loglevel")) {
				                    loglevel=Integer.valueOf(st.nextToken()).intValue();
          					    continue;
                                                    }
          if(token.equalsIgnoreCase("listenbacklog")) {
				                    scache.listenbacklog=Integer.valueOf(st.nextToken()).intValue();
          					    continue;
                                                    }
          if(token.equalsIgnoreCase("fake_user_agent")) {request.fake_user_agent="User-Agent: "+st.nextToken("\r\n").trim();
          					         continue;
                                                        }

          if(token.equalsIgnoreCase("fake_referer")) {request.fake_referer="Referer:"+st.nextToken("\r\n").trim();
	  request.referer_hack=98;
          					      continue;
                                                    }

          if(token.equalsIgnoreCase("swap_level1_dirs")) {swap_level1_dirs=Integer.valueOf(st.nextToken()).intValue();
          					    continue;
                                                    }

          if(token.equalsIgnoreCase("swap_level2_dirs")) {swap_level2_dirs=Integer.valueOf(st.nextToken()).intValue();
          					    continue;
                                                    }
          if(token.equalsIgnoreCase("http_proxy")) {
                                                    if (st.countTokens()>2)
                                                        setproxy(st.nextToken(),Integer.valueOf(st.nextToken()).intValue(),st.nextToken(),false);
                                                     else
                                                        setproxy(st.nextToken(),Integer.valueOf(st.nextToken()).intValue(),null,false);
							
           					    continue;
                                                    }
          if(token.equalsIgnoreCase("ftp_proxy")) {
                                                    if (st.countTokens()>2)
                                                        setproxy(st.nextToken(),Integer.valueOf(st.nextToken()).intValue(),st.nextToken(),true);
                                                     else
                                                        setproxy(st.nextToken(),Integer.valueOf(st.nextToken()).intValue(),null,true);
							
           					    continue;
                                                    }
          if(token.equalsIgnoreCase("https_proxy")) {
                                                        https_proxy=InetAddress.getByName(st.nextToken());
                                                        https_proxy_port=Integer.valueOf(st.nextToken()).intValue();
							
           					    continue;
                                                    }
          if(token.equalsIgnoreCase("access_log")) {
                                                    String mask=st.nextToken();
                                                    if(mask.equals("*")) mask=null;

                                                    httpreq.logpatterns=addStringToArray(mask,httpreq.logpatterns);
                                                    httpreq.logfilenames=addStringToArray(st.nextToken(),httpreq.logfilenames);

          					    continue;
                                                    }

          if(token.equalsIgnoreCase("reply_body_max_size"))   {
                                                    request.maxbody=(int)garbage.sizestring(st.nextToken("\r\n"));
          					    continue;
                                                    }
	  
          if(token.equalsIgnoreCase("request_body_max_size"))   {
                                                    request.reqmaxbody=(int)garbage.sizestring(st.nextToken("\r\n"));
          					    continue;
                                                    }
	  
          if(token.equalsIgnoreCase("max_cacheable_size"))   {
                                                    request.maxcacheable=(int)garbage.sizestring(st.nextToken("\r\n"));
          					    continue;
                                                    }

          if(token.equalsIgnoreCase("download_machine_queue_file"))   {
                                                    dmachine_queue=st.nextToken("\r\n").trim();
          					    continue;
                                                    }
	  
          if(token.equalsIgnoreCase("download_machine_url_mask"))   {
                                                    while(st.hasMoreTokens())
                                                      dmachine_mask=addRegexpToArray(st.nextToken(),dmachine_mask,true);
          					    continue;
                                                    }

          if(token.equalsIgnoreCase("download_machine_ignore"))   {
                                                    while(st.hasMoreTokens())
                                                      dmachine_ignore=addRegexpToArray(st.nextToken(),dmachine_ignore,true);
          					    continue;
                                                    }
	  
          if(token.equalsIgnoreCase("download_machine_offline_url_mask"))   {
                                                    while(st.hasMoreTokens())
                                                      cacheobject.dmachine_offmask=addRegexpToArray(st.nextToken(),cacheobject.dmachine_offmask,true);
          					    continue;
                                                    }
	  
          if(token.equalsIgnoreCase("download_machine_ctype_minimum_size"))   {
                                                    request.dmachine_ctype_min=(int)garbage.sizestring(st.nextToken("\r\n"));
          					    continue;
                                                    }
          if(token.equalsIgnoreCase("download_machine_ctype_mask"))   {
                                                    while(st.hasMoreTokens())
                                                      request.dmachine_ctype_mask=addRegexpToArray(st.nextToken(),request.dmachine_ctype_mask,true);
          					    continue;
                                                    }
          if(token.equalsIgnoreCase("download_machine_ctype_ignore"))   {
                                                    while(st.hasMoreTokens())
                                                      dmachine_ctype_ignore=addRegexpToArray(st.nextToken(),dmachine_ctype_ignore,true);
          					    continue;
                                                    }

	  
          if(token.equalsIgnoreCase("no_proxy"))   {
                                                    while(st.hasMoreTokens())
                                                      no_proxy=addStringToArray(st.nextToken().toLowerCase(),no_proxy);
          					    continue;
                                                    }

          if(token.equalsIgnoreCase("nocompress")) {     while(st.hasMoreTokens())
                                                      cacheobject.nocompress=addStringToArray(st.nextToken().toLowerCase(),cacheobject.nocompress);
          					    continue;
                                                    }

          if(token.equalsIgnoreCase("regex_fail_file")) {
                                                    String fn=st.nextToken();
                                                    if(! new File(fn).isAbsolute()) fn=scache.cfgdir+File.separator+fn;
                                                    regex_fail=parseRealRegexpFile(fn);
                                                    regex_fail_timestamp=new File(fn).lastModified();
                                                    regex_fail_filename=fn;
          					    continue;
                                                    }
          if(token.equalsIgnoreCase("fail_file")) {
                                                    String fn=st.nextToken();
                                                    if(! new File(fn).isAbsolute()) fn=scache.cfgdir+File.separator+fn;
                                                    fail=parseRegexpFile(fn,fail);
                                                    fail_timestamp=new File(fn).lastModified();
                                                    fail_filename=fn;
          					    continue;
                                                    }


          if(token.equalsIgnoreCase("allow_cookies_to_file")) {
                                                    String fn=st.nextToken();
                                                    if(! new File(fn).isAbsolute()) fn=scache.cfgdir+File.separator+fn;
                                                    allow_cookies_to=parseCookieFile(fn);
                                                    cookie_timestamp=new File(fn).lastModified();
                                                    cookie_filename=fn;
          					    continue;
                                                    }
          if(token.equalsIgnoreCase("allow_all_session_cookies")) {
                                                    request.allow_all_session_cookies=getYesNo(st.nextToken());
          					    continue;
                                                    }

          if(token.equalsIgnoreCase("redirect_file")) {
                                                    String fn=st.nextToken();
                                                    if(! new File(fn).isAbsolute()) fn=scache.cfgdir+File.separator+fn;
                                                    parseRedirFile(fn,true);
                                                    redir_timestamp=new File(fn).lastModified();
                                                    redir_filename=fn;
          					    continue;
                                                    }

          if(token.equalsIgnoreCase("rewrite_file")) {
                                                    String fn=st.nextToken();
                                                    if(! new File(fn).isAbsolute()) fn=scache.cfgdir+File.separator+fn;
                                                    parseRedirFile(fn,false);
                                                    remap_timestamp=new File(fn).lastModified();
                                                    remap_filename=fn;
          					    continue;
                                                    }

          if(token.equalsIgnoreCase("pass_file")) {
                                                    String fn=st.nextToken();
                                                    if(! new File(fn).isAbsolute()) fn=scache.cfgdir+File.separator+fn;
                                                    pass=parseRegexpFile(fn,pass);
                                                    pass_timestamp=new File(fn).lastModified();
                                                    pass_filename=fn;
          					    continue;
                                                    }

          if(token.equalsIgnoreCase("no_cache")) { while(st.hasMoreTokens())
                                                       nocache=addRegexpToArray(st.nextToken(),nocache,true);
                                                    cacheonly=false;
          					    continue;
                                                    }

          if(token.equalsIgnoreCase("fake_cookie")
	  ) {request.wafer="Cookie:"+st.nextToken("\r\n").trim();
          					         continue;
                                              }

          if(token.equalsIgnoreCase("cacheonly")) {while(st.hasMoreTokens())
                                                     nocache=addRegexpToArray(st.nextToken(),nocache,true);
                                                   cacheonly=true;
          					   continue;
                                                    }

          if(token.equalsIgnoreCase("ui_searchcachefactor")) { 
	                                           ui.searchcachefactor=Float.valueOf(st.nextToken()).floatValue();
						   continue;
	  }


          if(token.equalsIgnoreCase("quick_abort_min")) {
                                                   request.qa_minlen=(int)garbage.sizestring(st.nextToken("\r\n"));
						   continue;
	  }

          if(token.equalsIgnoreCase("quick_abort_max")) {
                                                   request.qa_maxlen=(int)garbage.sizestring(st.nextToken("\r\n"));
          					   continue;
                                                   }

          if(token.equalsIgnoreCase("quick_abort_pct")) {
                                                   request.qa_percent=Float.valueOf(st.nextToken()).floatValue();
						   if(request.qa_percent>1) request.qa_percent/=100f;
          					   continue;
                                                   }

          if(token.equalsIgnoreCase("max_aborted_transfer_time")) {
					           request.qa_maxtime=garbage.timestring(st.nextToken("\r\n"));
          					   continue;
                                                   }

          if(token.equalsIgnoreCase("errordocument")) {
                                                   int code=Integer.valueOf(st.nextToken()).intValue();
                                                   if(code==403) custom403=st.nextToken();
                                                    else if(code==400 || code==500) cacheobject.custom500=st.nextToken();
                                                          else
                                                            System.err.println("[CONFIG_ERROR] "+cfgfile+":"+lineno+" Error code "+code+" is not customizable.");
          					   continue;
                                                   }

          if(token.equalsIgnoreCase("max_connections")) {
                                                   scache.MAXHTTPCLIENTS=Integer.valueOf(st.nextToken()).intValue();
          					   continue;
                                                   }

          if(token.equalsIgnoreCase("client_timeout")) {
                                                   httpreq.client_timeout=(int)garbage.timestring(st.nextToken());
          					   continue;
                                                   }
          if(token.equalsIgnoreCase("read_timeout")) {
                                                   request.read_timeout=(int)garbage.timestring(st.nextToken());
          					   continue;
                                                   }
          if(token.equalsIgnoreCase("request_timeout")) {
                                                   request.request_timeout=(int)garbage.timestring(st.nextToken());
          					   continue;
                                                   }

          if(token.equalsIgnoreCase("pconn_timeout")) { 
	                                            ConnectionHandler.setKeepaliveTime(garbage.timestring(st.nextToken()));
          					    continue;
                                                    }

          if(token.equalsIgnoreCase("pragma_no_cache")) {
                                                   request.pnocache=(char)Integer.valueOf(st.nextToken()).intValue();
          					   continue;
                                                   }
          if(token.equalsIgnoreCase("proxyvia")) {
                                                   request.proxyvia=getYesNo(st.nextToken());
          					   continue;
                                                   }
          if(token.equalsIgnoreCase("keep_deleted")) {
                                                   cacheobject.keep_deleted=getYesNo(st.nextToken());
          					   continue;
                                                   }
          if(token.equalsIgnoreCase("reload_into_ims")) {
                                                   cacheobject.force_ims_on_reloads=getYesNo(st.nextToken());
          					   continue;
                                                   }

          if(token.equalsIgnoreCase("cache_password_protected")) {
                                                   request.cache_protected=getYesNo(st.nextToken());
          					   continue;
                                                   }
	  
          if(token.equalsIgnoreCase("cache_private")) {
                                                   request.cache_private=getYesNo(st.nextToken());
          					   continue;
                                                   }
	  
          if(token.equalsIgnoreCase("cache_vary")) {
                                                   request.cache_vary=getYesNo(st.nextToken());
          					   continue;
                                                   }


          if(token.equalsIgnoreCase("remove_pragma_no_cache")) {
                                                   request.remove_pragma_no_cache=getYesNo(st.nextToken());
          					   continue;
                                                   }

          if(token.equalsIgnoreCase("trace_fail")) {
                                                   trace_fail=getYesNo(st.nextToken());
          					   continue;
                                                   }
          if(token.equalsIgnoreCase("trace_rewrite")) {
                                                   trace_remap=getYesNo(st.nextToken());
          					   continue;
                                                   }
          if(token.equalsIgnoreCase("trace_redirect")) {
                                                   trace_redirect=getYesNo(st.nextToken());
          					   continue;
                                                   }
          if(token.equalsIgnoreCase("trace_keepalive")) {
                                                   ConnectionHandler.trace_keepalive=getYesNo(st.nextToken());
          					   continue;
                                                   }
          if(token.equalsIgnoreCase("trace_input_keepalive")) {
                                                   httpreq.trace_inkeepalive=getYesNo(st.nextToken());
          					   continue;
                                                   }
          if(token.equalsIgnoreCase("trace_url")) {
                                                   httpreq.trace_url=getYesNo(st.nextToken());
          					   continue;
                                                   }
          if(token.equalsIgnoreCase("trace_request")) {
                                                   request.trace_request=getYesNo(st.nextToken());
          					   continue;
                                                   }
          if(token.equalsIgnoreCase("trace_reply")) {
                                                   request.trace_reply=getYesNo(st.nextToken());
          					   continue;
                                                   }
          if(token.equalsIgnoreCase("trace_abort")) {
                                                   request.trace_abort=getYesNo(st.nextToken());
          					   continue;
                                                   }
          if(token.equalsIgnoreCase("trace_cookie")) {
                                                   request.trace_cookie=getYesNo(st.nextToken());
          					   continue;
                                                   }
          if(token.equalsIgnoreCase("trace_refresh")) {
                                                   cacheobject.trace_refresh=getYesNo(st.nextToken());
          					   continue;
                                                   }
          if(token.equalsIgnoreCase("clear_flags_on_start")) {
                                                   clear_flags_on_start=getYesNo(st.nextToken());
          					   continue;
                                                   }
          if(token.equalsIgnoreCase("flag_check_interval")) {
                                                   flag_check_interval=(int)garbage.timestring(st.nextToken());
          					   continue;
                                                   }

          if(token.equalsIgnoreCase("generate_lastmod")) {
                                                   cacheobject.generate_lastmod=getYesNo(st.nextToken());
          					   continue;
                                                   }

          if(token.equalsIgnoreCase("hide_errors")) {
                                                   cacheobject.hide_errors=getYesNo(st.nextToken());
          					   continue;
                                                   }

          if(token.equalsIgnoreCase("allow")) {
					       while(st.hasMoreElements())
					       {
						   String adr;
						   adr=st.nextToken();
						   if(adr.equals("*")) 
						       httpreq.allowed=null;
						   else
						       addInetAdr(adr);
					       }
					       continue;
					      }
          if(token.equalsIgnoreCase("builtin_gif_color"))
                                                    {
							int r,g,b;
							r=(int)(Float.valueOf(st.nextToken()).floatValue());
							g=(int)(Float.valueOf(st.nextToken()).floatValue());
							b=(int)(Float.valueOf(st.nextToken()).floatValue());
							request.GIF=request.GIF1;
							request.GIF[13]=(byte)r;
							request.GIF[14]=(byte)g;
							request.GIF[15]=(byte)b;
							continue;
						    }

                                                              if(token.equalsIgnoreCase("default_refresh_pattern"))
                                                    {
                                                    reload_age=garbage.timestring(st.nextToken());
                                                    min_age=garbage.timestring(st.nextToken());
                                                    lmfactor=Float.valueOf(st.nextToken()).floatValue();
                                                    max_age=garbage.timestring(st.nextToken());
                                                    if(reload_age>min_age) reload_age=min_age/2;
                                                    if(max_age<=min_age) max_age=min_age+1000L*60L*60L*48L;
                                                    expire_age=garbage.timestring(st.nextToken());
                                                    redir_age=garbage.timestring(st.nextToken());
						    if(expire_age>max_age) expire_age=max_age;
						    if(redir_age>min_age) redir_age=min_age;
          					    continue;
                                                    }
          if(token.equalsIgnoreCase("refresh_pattern"))
                                                    {
                                                    String url=st.nextToken();
                                                    if(isInRegexpArray(url,refresh)>-1) 
						    {
							System.out.println("[INFO] Ignoring refresh pattern for URL: "+url);
							continue;
						    }
                                                    long xreload_age=garbage.timestring(st.nextToken());
                                                    long xmin_age=garbage.timestring(st.nextToken());
                                                    float xlmfactor=Float.valueOf(st.nextToken()).floatValue();
                                                    long xmax_age=garbage.timestring(st.nextToken());
                                                    if(xreload_age>xmin_age) xreload_age=xmin_age/4;
                                                    if(xmax_age<=xmin_age) xmax_age=xmin_age+1000L*60L*60L*48L;
						    long xexpire_age,xredir_age;
                                                    xexpire_age=garbage.timestring(st.nextToken());
                                                    xredir_age=garbage.timestring(st.nextToken());
						    if(xexpire_age>xmax_age) xexpire_age=xmax_age;
						    if(xredir_age>xmin_age) xredir_age=xmin_age;
                                                    /* pridat do pole */
                                                    refresh=addRegexpToArray(url,refresh,false);
                                                    /* natahnout ! */
                                                    reloadage=incLongArraySize(reloadage);
                                                    minage=incLongArraySize(minage);
                                                    maxage=incLongArraySize(maxage);
                                                    expireage=incLongArraySize(expireage);
                                                    redirage=incLongArraySize(redirage);
                                                    lastmodf=incFloatArraySize(lastmodf);

                                                    /* nastavit hodnoty */
                                                    reloadage[reloadage.length-1]=xreload_age;
                                                    minage[minage.length-1]=xmin_age;
                                                    maxage[maxage.length-1]=xmax_age;
                                                    lastmodf[lastmodf.length-1]=xlmfactor;
                                                    expireage[expireage.length-1]=xexpire_age;
                                                    redirage[redirage.length-1]=xredir_age;
          					    continue;
                                                    }


          System.out.println("[CONFIG_ERROR] Unknown keyword "+token+" in "+cfgfile+":"+lineno);
     }
     catch (NoSuchElementException nse)
           { System.out.println("[CONFIG_ERROR] "+cfgfile+":"+lineno+" Missing arguent(s).");
             continue;}
     catch (NumberFormatException nse)
           { System.out.println("[CONFIG_ERROR] "+cfgfile+":"+lineno+" Invalid Number Format.");
             continue;}


 }/* while */
 dis.close();

 }
 catch(FileNotFoundException kiss)
  { System.out.println("[WARNING] No config file ("+cfgfile+") found, using defaults.");}
 catch(IOException e)
  { System.out.println("I/O Error reading config file "+cfgfile);}

 // init access log filez
 if(httpreq.logpatterns!=null) httpreq.logfilez=new DataOutputStream[httpreq.logpatterns.length];

 if(swap_level1_dirs<1) swap_level1_dirs=1;
 if(swap_level2_dirs<1) swap_level2_dirs=1;

 if(dmachine_queue==null || (dmachine_mask==null && request.dmachine_ctype_mask==null && cacheobject.dmachine_offmask==null))
 {
     dmachine_queue=null;
     dmachine_mask=null;
     request.dmachine_ctype_mask=null;
     cacheobject.dmachine_offmask=null;
 }
}


public final void setproxy(String hostname,int port,String auth,boolean ftp)
{
 if (auth!=null) 
     if(ftp)
	 ftp_proxy_auth="Proxy-authorization: Basic "+HTUU.encode_string(auth);
     else
	 http_proxy_auth="Proxy-authorization: Basic "+HTUU.encode_string(auth);
 else 
     if(ftp)
         ftp_proxy_auth=null;
     else
	 http_proxy_auth=null;
 try{
     if(ftp)
     {
	   ftp_proxy=InetAddress.getByName(hostname);
           ftp_proxy_port=port;
     } else
     {
	   http_proxy=InetAddress.getByName(hostname);
	   http_proxy_port=port;
     }
 }
 catch (UnknownHostException e)
  {
   System.err.println("[WARNING] Can not resolve parent proxy hostname: "+hostname+" - running without it.");
   if(ftp)
   {
       ftp_proxy=null;
       ftp_proxy_auth=null;
       ftp_proxy_port=0;
   }
   else
   {
       http_proxy=null;
       no_proxy=null;
       http_proxy_auth=null;
       http_proxy_port=0;
   }
  }
}

final public void process_request(request req) throws MalformedURLException, IOException
{
 boolean allowed=false; /* is URL allowed? */
 boolean direct=false; /* use direct connection? no proxy */
 boolean uireq=false; /* requesting data from UI? */

 String requested=req.getURL();

 /* HANDLE REMAP RULES ! */
 try{
 if(remapfrom!=null)
 {
   String fragment[];
   int j=remapfrom.length;
   for(int i=0;i<j;i++)
   {
    fragment=remapfrom[i].search(requested);
    if(fragment==null) continue;
    j=remappart[i];
    if(j>0)
       req.rewriteURL(remapto[i]+fragment[j-1]);
     else
       req.rewriteURL(remapto[i]);

    requested=req.getURL(); // reread URL
    if(trace_remap)
    {
       System.out.println("[TRACE "+Thread.currentThread().getName()+"] $ Remaped to "+requested+"\n\tby rule: \""+remapfrom[i]+"\"");
    }
    break;
   }
  } /* remap handler end */
 }catch (IndexOutOfBoundsException ignore) {}

 /* HANDLE REDIR RULES ! */
 try{
 if(redirfrom!=null)
 {
   String fragment[];
   String target;
   int j=redirfrom.length;
   for(int i=0;i<j;i++)
   {
    fragment=redirfrom[i].search(requested);
    if(fragment==null) continue;
    j=redirpart[i];
    if(j>0)
	target=redirto[i]+fragment[j-1];
     else
	target=redirto[i];
     
    if(trace_redirect)
    {
       System.out.println("[TRACE "+Thread.currentThread().getName()+"] ~ Redirected "+requested+" to "+target+"\n\tby rule: \""+redirfrom[i]+"\"");
    }
    req.make_headers(301,null,null,target,0,new Date().getTime(),0,null);
    req.send_headers();
    req.close();
    return;
   }
  } /* redirect handler end */
 }catch (IndexOutOfBoundsException ignore) {}

 String[] parsed;
 parsed=parseURL(requested,req.protocol);
 String host=parsed[0];
 req.hostname=host;

 /* CHECK OUT UI code, set mode to direct, avoid caching */
 if(host.equals(ui.ui_hostname))
     if(parsed[1]!=null)
	 if(parsed[1].equals(Integer.toString(ui.uiport)))
	 {
	     req.nocache();
	     direct=true;
	     uireq=true;
	     allowed=true;
	 }
 
 /* check if URL is allowed */
 try
 {
     if(pass!=null)
     { 
       for(int i=pass.length-1;i>=0;i--)
	 if(pass[i].matches(requested))
	 { 
	     allowed=true;
	     break;
	 }
     }
 }
 catch (IndexOutOfBoundsException ignore) {}

 /* check if URL blocked */
 try
 {
    if(allowed==false)
    {
       if(regex_fail!=null)
       {
	  int ln;
	  ln=requested.length();
	  char str[]=new char[ln];
	  requested.getChars(0,ln,str,0);
	  if(regex_fail.match(str,0,ln)!=null)
		{
		   if(trace_fail)
		   {
		     System.out.println("[TRACE "+Thread.currentThread().getName()+"] ! Blocked "+requested+"\n\tby regexp rules.");
		   }
		   handleBlock(req);
		   return;
		}
       }
       if(fail!=null)
       {
	 for(int i=fail.length-1;i>=0;i--)
	 {
	   if(fail[i].matches(requested))
	   {
	     if(trace_fail)
	     {
	       System.out.println("[TRACE "+Thread.currentThread().getName()+"] ! Blocked "+requested+"\n\tby rule: \""+fail[i]+"\"");
	     }
	     
	     handleBlock(req);
	     return;
	   }
	 }
       }
    }
 }
 catch (IndexOutOfBoundsException ignore) {}


  /* NO-CACHE HANDLER */
  if(nocache!=null)
  { 
     int j=nocache.length;
     int i;
     toploop:while(true)
     {
	 for(i=0;i<j;i++)
	 {
	     if(nocache[i].matches(requested))
	     {
		if(!cacheonly) req.nocache();
		break toploop;
	     }
	 } /* for */
	 if(cacheonly) req.nocache();
	 break;
     } /* toploop */
  }


 /* download machine hook */
 /* Mascot of Commodore C64 is Captain Hook, C64 created piracy */
 if(dmachine_mask!=null)
 {
     if(!uireq)
     {
dmachine:for(int i=dmachine_mask.length-1;i>=0;i--)
	 {
	     if(dmachine_mask[i].matches(requested))
	     {
		 if(dmachine_ignore!=null)
		 {
		     for(int j=dmachine_ignore.length-1;j>=0;j--)
			 if(dmachine_ignore[j].matches(requested))
			     break dmachine;
		 }
		 synchronized(this)
		 {
		     Hashtable dmq=ui.load_dm_queue();
		     dmq.put(requested,requested);
		     ui.save_dm_queue(dmq);
		     req.send_error(202,"Request Accepted<br>\nURL <a href=\""+requested+"\">"+requested+"</a> was successfully added to download machine queue file. Matched by URL mask.");
		 }
		 break;
	     }
	 }
     }
 }

 /* OUTGOING C00KIES FILTER */
 try
 {
  if(allow_cookies_to!=null && request.allow_all_session_cookies==false)
  {
   cookieloop:while(true)
   {
    for(int i=allow_cookies_to.length-1;i>=0;i--)
     if(host.endsWith(allow_cookies_to[i])) { break cookieloop;}

   req.removeOutgoingCookies();
   break;
   }
  }
 }
 catch (IndexOutOfBoundsException ignore) {}

 boolean ftp=false;
 if(parsed[4]!=null)
     if(parsed[4].equals("ftp"))
	     ftp=true;
	     
 if(!ftp)
 {
	 /* USE HTTP/HTTPS PARENT PROXY SERVER ?? */
	 if(http_proxy==null && https_proxy==null) direct=true;
	 else
	 {
	     if(no_proxy!=null) 
	     {
		int j=no_proxy.length;
		for(int i=0;i<j;i++) 
		{
		   if(host.endsWith(no_proxy[i])) 
		   {
		      direct=true;
		      break;
		   }
		}
	     } /* if No_proxy */
	 }

	 if(direct)
	 {
	    /* DIREKT REZIM */
	    try {
		  // bkn3@columbia.edu - changed from getByName to getByAddress to work on JXTA and
		  // avoid name to IP network resolutions
	      req.setTarget(P2PInetAddress.getByAddress(host, null),parsed[1]==null? 80: Integer.valueOf(parsed[1]).intValue());
	    }
	    catch (UnknownHostException e) { req.setTarget(null,80); }
	    catch (NumberFormatException e) { req.send_error(400,"Port is not a numeric constant: '"+parsed[1]+"'"); }

	    if(parsed[4]!=null) 
	      if(!parsed[4].equals("https"))
		 req.send_error(501,"Protocol "+parsed[4]+" is not directly supported, configure http_proxy or ftp_proxy.");
		 
	    /* premenime request na normal */
	    req.setRequestTo(parsed[2]+parsed[3]);
	 }
	 else
	 {
	   /* go through proxy */
	   req.setTarget(http_proxy,http_proxy_port);
	   if (http_proxy_auth!=null) req.add_header(http_proxy_auth);
	 }
 } else
 {
       /* sent request to FTP proxy */
       req.setTarget(ftp_proxy,ftp_proxy_port);
       if (ftp_proxy_auth!=null) req.add_header(ftp_proxy_auth);
 }

 /* testneme na non HEAD/GET methods */
  switch(req.method)
  {
   case httpreq.REQUEST_POST:
   case httpreq.REQUEST_PUT:
                               req.addHost(host,parsed[1]);
                               req.direct_request(true);
                               return;
   case httpreq.REQUEST_TRACE:
                               req.handle_trace();
			       return;
   case httpreq.REQUEST_OPTIONS:
                               req.addHost(host,parsed[1]);
                               req.handle_options();
			       return;
   case httpreq.REQUEST_DELETE:
                               req.addHost(host,parsed[1]);
                               req.direct_request(false);
                               return;
  case httpreq.REQUEST_CONNECT:
                                { 
				 /* HTTPS */
				 int port=80;
                                 if(parsed[1]!=null)
				   try
				    { 
					port=Integer.valueOf(parsed[1]).intValue();
				    }
                                    catch (NumberFormatException e) 
				    { 
					req.send_error(400,"Target port is not a numeric constant: '"+parsed[1]+"'"); 
				    }

                                 if(allowconnect==null) req.send_error(501,"CONNECCT method is disabled by configuration.");
				 if(https_proxy==null) direct=true;
				   try
				   {
                                     if(direct) {
						// bkn3@columbia.edu - changed from getByName to getByAddress to work on JXTA and
						// avoid name to IP network resolutions.
						req.setTarget(P2PInetAddress.getByAddress(host, null),port);
					 }
				     else
					 req.setTarget(https_proxy,https_proxy_port);
				   }
                                   catch (UnknownHostException e)
                                     { req.send_error(500,"CONNECT failed: "+host+" Host unknown.");}
				 for(int i=allowconnect.length-1;i>=0;i--)
				 {
				   if(allowconnect[i]==port)
				    {
				     req.handle_connect(direct);
				     return;
				    }
				 }
                                 req.send_error(403,"Direct connecting to port "+parsed[1]+" is forbidden by rule.");
				}
  }

 req.addHost(host,parsed[1]);

 /* konverze na local dir */
 String locdir=getLocalDir(host,parsed[1],parsed[2],parsed[4]);
 cachedir dir=null;
 try{
      dir=getDir(locdir,parsed[4],host,parsed[1],parsed[2]);
    }
 catch(Exception e) { 
                      /* Internal parse error */
                      e.printStackTrace();
                      System.out.println("Hostname:"+host);
                      System.out.println("Port:"+parsed[1]);
                      System.out.println("Dir:"+parsed[2]);
                      System.out.println("File:"+parsed[3]);
                      System.out.println("Protocol:"+parsed[4]);
                      req.close();
                      return;
                    }
 cacheobject cobj;
 cobj=dir.getObject(parsed[3]);

 /* HANDLE REFRESH PATTERNS MIN_AGE, PERCENT, MAX_AGE, ... */
 if(refresh!=null)
  {
   int refreshcache=refresh.length;
   for(int i=0;i<refreshcache;i++)
    if(refresh[i].matches(requested))
     {
     cobj.make_request(req,reloadage[i],minage[i],maxage[i],lastmodf[i],expireage[i],redirage[i]);
     break;
     }

  }
  else
   cobj.make_request(req,reload_age,min_age,max_age, lmfactor,expire_age,redir_age);

 req.close();
}

final public synchronized cachedir getDir(String locdir,String proto,String host,String port,String direct)
{
 cachedir dir=(cachedir)dircache.get(locdir);
 if(dir!=null) return dir;

 dir=new cachedir(locdir,(proto==null?"http":proto)
	 +"://"+host+(port==null?"":":"+port)
	 +direct);
 dircache.put(locdir,dir);
 return dir;
}

final public synchronized cachedir getDir(String locdir,String urlpart)
{
 cachedir dir=(cachedir)dircache.get(locdir);
 if(dir!=null) return dir;

 dir=new cachedir(locdir,urlpart);
 dircache.put(locdir,dir);
 return dir;
}

final public void run()
{
 int ticks;
 if(immediate_shutdownflag==null || flag_check_interval<=0)
       flag_check_interval=(int)(SAVETIMER);
 /* normalize fci */
 ticks=(int)(SAVETIMER/flag_check_interval);
 if(ticks==0) ticks=1;
 flag_check_interval=(int)(SAVETIMER/ticks);
 int i=0;
 while(true)
 {
   try
   {
     Thread.sleep(flag_check_interval);
   }
   catch (InterruptedException e)
   {
     System.out.println("[SMARTCACHE] Background saver interrupted, exiting.");
     save();save();
     httpreq.flush(httpreq.logfilez);
     break;
   }
 i++;
 if(i>=ticks) {
                save();
		i=0;
		touch_flag(aliveflag);
	       }
 /* check 4 IMM shutdownflag */
 if(checkFlag(immediate_shutdownflag))
 {
    checkFlag(aliveflag);
    Enumeration e2=dircache.elements();
    while(e2.hasMoreElements())
    {
     cachedir d;
     d=(cachedir) e2.nextElement();
     d.save();
     d.cleandir(); /* if needed */
    }
    httpreq.flush(httpreq.logfilez);
    System.out.println(new Date()+" Server stopped by Immediate shutdown flag.");System.exit(0);
 }

  // reloading block files
    if(pass_filename!=null)
    {
      if(new File(pass_filename).lastModified()!=pass_timestamp)
      {
       if(loglevel>2) System.out.println(new Date()+" Reloading Pass_file "+pass_filename);
       pass=parseRegexpFile(pass_filename,null);
       pass_timestamp=new File(pass_filename).lastModified();
      }
     }

    if(fail_filename!=null)
     {
      if(new File(fail_filename).lastModified()!=fail_timestamp)
      {
       if(loglevel>2) System.out.println(new Date()+" Reloading fail_file "+fail_filename);
       fail=parseRegexpFile(fail_filename,null);
       fail_timestamp=new File(fail_filename).lastModified();
      }
     }
    
    if(regex_fail_filename!=null)
     {
      if(new File(regex_fail_filename).lastModified()!=regex_fail_timestamp)
      {
       if(loglevel>2) System.out.println(new Date()+" Reloading regex_fail_file "+regex_fail_filename);
       regex_fail=parseRealRegexpFile(regex_fail_filename);
       regex_fail_timestamp=new File(regex_fail_filename).lastModified();
      }
     }

    if(cookie_filename!=null)
    {
      if(new File(cookie_filename).lastModified()!=cookie_timestamp)
      {
       if(loglevel>2) System.out.println(new Date()+" Reloading allow_cookies_to_file "+cookie_filename);
       allow_cookies_to=parseCookieFile(cookie_filename);
       cookie_timestamp=new File(cookie_filename).lastModified();
      }
    }
    if(redir_filename!=null)
    {
      if(new File(redir_filename).lastModified()!=redir_timestamp)
      {
       if(loglevel>2) System.out.println(new Date()+" Reloading redirect_file "+redir_filename);
       parseRedirFile(redir_filename,true);
       redir_timestamp=new File(redir_filename).lastModified();
      }
    }
    if(remap_filename!=null)
    {
      if(new File(remap_filename).lastModified()!=remap_timestamp)
      {
       if(loglevel>2) System.out.println(new Date()+" Reloading remap_file "+remap_filename);
       parseRedirFile(remap_filename,false);
       remap_timestamp=new File(remap_filename).lastModified();
      }
    }
 }

}

/* Ulozi adresare cachovane v pameti na disk a udela gc, flush logy */
final private synchronized void save()
{
 int saved=0;
 int gc=0;
 int cleaned=0;
 Enumeration e1=dircache.keys();
 Enumeration e2=dircache.elements();
 while(e1.hasMoreElements())
 {
  cachedir d;
  d=(cachedir) e2.nextElement();
  String key;
  key=(String) e1.nextElement();

  if(d.dirty==true) { d.save(); saved++;}
    else
                  { 
		    dircache.remove(key);
                    cleaned+=d.cleandir();
                    gc++;
		  }

 }
 httpreq.flush(httpreq.logfilez);

 /* STATs */
 DataOutputStream dos=null;
 if(stat_log!=null)
     try
     {
       dos=new DataOutputStream(new BufferedOutputStream(
       new FileOutputStream(stat_log,true),4096));
     }
       catch (IOException ee1)
       {
	      System.err.println("Problem creating stat_log file '"+stat_log+"' - turning it off.");
	      stat_log=null;
       }

 if(saved>0) // write hit stats
 {
   StringBuffer sb=new StringBuffer(100);
   sb.append(new Date().toString());
   sb.append(" - ");
   sb.append(
   (int)(
   100.0f*(cacheobject.c_hit+cacheobject.c_block)  /
   ((float)cacheobject.c_hit+cacheobject.c_block
                +cacheobject.c_miss+cacheobject.c_refresh)
        )   );
   sb.append("% REQS: ");
   sb.append(cacheobject.c_hit);
   sb.append(" Hit, ");
   sb.append(cacheobject.c_refresh);
   sb.append(" Refresh, ");
   sb.append(cacheobject.c_miss);
   sb.append(" Miss, ");
   sb.append(cacheobject.c_block);
   sb.append(" Block.");
   if(loglevel>2) System.out.println(sb);
   if(dos!=null)
     try
      {
        dos.writeBytes(sb.toString());
        dos.writeBytes(httpreq.newline);
      }
      catch (IOException ignore) {}

   // clear the stats
   cacheobject.c_hit=cacheobject.c_miss=cacheobject.c_refresh=cacheobject.c_block
   =0;
 }
 if(cacheobject.b_miss+cacheobject.b_hit>0)
 {
   StringBuffer sb=new StringBuffer(100);
   sb.append(new Date());
   sb.append(" - ");
   sb.append(
   (int)(
   100.0f*cacheobject.b_hit/((float)cacheobject.b_miss+cacheobject.b_hit)
   )         );
   sb.append("% BYTES: ");
   sb.append(cacheobject.b_miss);
   sb.append(" B in, ");
   sb.append(cacheobject.b_miss+cacheobject.b_hit);
   sb.append(" B out.");
   if(loglevel>2) System.out.println(sb);
   if(dos!=null)
     try
      {
        dos.writeBytes(sb.toString());
        dos.writeBytes(httpreq.newline);
      }
      catch (IOException ignore) {}

   // clear the stats
   cacheobject.b_hit=cacheobject.b_miss=0;
  }

 if(saved>0 || gc> 0)
 {
    StringBuffer sb=new StringBuffer(100);
    sb.append(new Date());
    sb.append(" - DIRS: ");
    sb.append(saved);
    sb.append(" saved, ");
    sb.append(gc);
    sb.append(" freed, ");
    sb.append(cleaned);
    sb.append(" purged.");
    if(loglevel>2) System.out.println(sb);
    if(dos!=null)
    try
    	  {
	    dos.writeBytes(sb.toString());
	    dos.writeBytes(httpreq.newline);
	  }
    catch (IOException ignore) {}
 }
  /* close stat_logfile */
  if(dos!=null)
   try
    { dos.close();}
   catch (IOException err)
    {
     stat_log=null;
    }

 if(saved==0)
 {
   httpreq.close(httpreq.logfilez);
   dircache=new Hashtable(40); // reinit hashtable

   if(checkFlag(shutdownflag))
         { 
	   System.out.println(new Date()+" Server stopped by shutdown flag.");
	   checkFlag(aliveflag);
	   System.exit(0);
	 }
   System.gc();
 }

} /* save end */

/*  prechrousta URL a vrati
0 - hostname
1 - port (if any) jinak null
2 - directory
3 - file including query string ("" if empty)
4 - protocol (null if http)
*/

final public static String[] parseURL(String url,String proto)
{
  String[] res=new String[5];
  res[3]=""; /* HashTable do not likes NULL */
  int i,j,seven;

  if(proto!=null)
  {
  /* we allready have protocol parsed */
  res[4]=proto;
  seven=proto.length()+3; /* '://' */

  }
  else 
  {
      seven=url.indexOf("://");
      res[4]=url.substring(0,seven);
      seven+=3;
  }
  /* http -> null */
  if(seven==7)
   {
    char c;
    c=res[4].charAt(0);
    if(c=='h' || c=='H') res[4]=null;
   }

  i=url.indexOf('/',seven);
  if(i==-1) { url+='/'; i=url.length()-1;}

  j=url.indexOf(':',seven);
  /* http://zero.dsd:434/ */
  if(j!=-1 && j<i)  /* mame tu portname */
                    {
                      res[0]=url.substring(seven,j).toLowerCase();
		      if(j+1<i)
		      {
                        res[1]=url.substring(j+1,i);
                        if(res[1].equals("80")) res[1]=null;
		      }
                    }
           else
             {
              res[0]=url.substring(seven,i).toLowerCase();
             }

  /* parse adr */

  /* najdeme nejvice vlevo z moznych problemovych znaku */

   j=url.length()-1;
   byte v[];
   v=new byte[j+1];
   url.getBytes(i,j+1,v,i);
   // getChars(i,j,v,0);
   loop1:for(int zz=i;zz<=j;zz++)
    {
    switch(v[zz])
    {
    case 0x3b: // ;
    case 0x3a: // :
    case 0x3d: // =
    case 0x3f: // ?
              j=zz;break loop1;
    case 0x23: // #
     url=url.substring(0,zz);break loop1;
    }
    }


   j=url.lastIndexOf('/',j);

  String adr=url.substring(i,j+1); // adresar
  /* Normalize URL - remove /../ */
  /* NOTE: /./ should be also removed, but it do not hurt anything? */
  while( (i=adr.indexOf("/../")) >= 0 )
  {
   int l;
   if( (l=adr.lastIndexOf('/',i-1)) >=0 )
      adr= adr.substring(0,l)+ adr.substring(i+3);
   else
     adr=adr.substring(i+3);
  }
  res[2]=adr;
  if(j+1!=url.length()) res[3]=url.substring(j+1);
  return res;
}

final public static String getLocalDir(String host,String port,String urldir,String proto)
{
/*
Host - zero.vole.cz:3333
urldir = /ddfgfds/rerew/ - musi koncit s /
*/
  StringBuffer result=new StringBuffer(80);
  result.append(cache_dir);
  int i;

  /* 1. spocitat hash z host stringu */
  CRC32 crc=new CRC32();

  // host=host.toLowerCase();
  crc.update(host.getBytes());

  /* mame hash - rozdelime ho na adresar */
  /* zacneme budovat cestu */
      i=(int)(crc.getValue()/(0x100000000L/(swap_level1_dirs*swap_level2_dirs)));
      result.append(File.separator+(i/swap_level2_dirs)+File.separator+(i % swap_level2_dirs)+File.separator+host);

  /* pridame port */
  if(port!=null)
   {result.append('_');
    result.append(port);
   }

  /* add protocol */
  if(proto!=null)
             {
              result.append('^');
              result.append(proto);
             }

  /* pridame adresar */
  if(File.separatorChar!='/') urldir=urldir.replace('/',File.separatorChar);

  /* *************************************************** */
  /* ALERT: Synchronize by hand with garbage.encodechars */
  /* *************************************************** */
  /* nahrazujeme nepratelske znaky */
  // urldir=urldir.replace(':','_');
  // zde to neni nutne, neb muze byt jen v portu

  //  urldir=urldir.replace('*','@');
  //  urldir=urldir.replace('?','#');
  //  urldir=urldir.replace('~','-');

  // these two are not needed. Browsers sends &lt; &gt;
  // urldir=urldir.replace('>','}');
  // urldir=urldir.replace('<','{');
  urldir=urldir.replace('|','!');

  result.append(urldir);
  return result.toString();
}

/* U T I L I T Y */

final static String[] addStringToArray(String what,String[] array)
{
 //if(what==null) return array;
 if(array==null) { array=new String[1];array[0]=what;return array;}
 String[] tmp;
 int ar=array.length;
 tmp=new String[ar+1];
 System.arraycopy(array,0,tmp,0,ar);
 tmp[ar]=what;
 return tmp;
}

final static String[] reverseStringArray(String[] what)
{
 String res[];
 if(what==null) return null;
 res=new String[what.length];
 for(int i=0;i<what.length;i++)
  res[what.length-1-i]=what[i];

 return res;
}

final static regexp[] reverseRegexpArray(regexp[] what)
{
 regexp res[];
 if(what==null) return null;
 res=new regexp[what.length];
 for(int i=0;i<what.length;i++)
  res[what.length-1-i]=what[i];

 return res;
}

final static regexp[] addRegexpToArray(String what,regexp[] array, boolean  clean)
{
 int i;
 if(what==null) return array;
 if(array==null) 
 { 
     array=new regexp[1];
     array[0]=new regexp(what,!case_sensitive);
     return array;
 }

 /* test zda neni rule uz zbytecne */
 if( (i=isInRegexpArray(what,array)) > -1)
 {
      System.err.println("[INFO] Rule \""+what+"\" ignored - already matched by \""+array[i]+"\"");
      return array;
 }

 if(clean) array=cleanUpRegexpArray(array,what);
 
 regexp[] tmp;
 regexp whatrule=new regexp(what,!case_sensitive);
	 
 tmp=new regexp[array.length+1];
 System.arraycopy(array,0,tmp,0,array.length);
 tmp[array.length]=whatrule;
 return tmp;
}

public final static regexp[] cleanUpRegexpArray(regexp[] array,String what)
{
 if(array==null) return null;
 /* test zda nova rule neco nevyhodi */
 regexp whatrule=new regexp(what,!case_sensitive);
 regexp[] tmp;
 for(int i=0;i<array.length;i++)
 {
   if(whatrule.matches(array[i].toString()))
   { 
      System.err.println("[INFO] New rule \""+what+"\" made old rule \""+array[i]+"\" obsolete.");
      tmp=new regexp[array.length-1];
      /* nakopirovat zacatek */
      System.arraycopy(array,0,tmp,0,i);
      System.arraycopy(array,i+1,tmp,i,array.length-i-1);
      i--;
      array=tmp;
      continue;
  }
 }
 return array;
}

final static int isInRegexpArray(String what,regexp[] array)
{
 if(array==null) return -1;
 for(int i=0;i<array.length;i++)
  if(array[i].matches(what)) { return i;}
 return -1;
}


final public static int [] incIntegerArraySize(int[] array)
{
 if(array==null) { array=new int[1];return array;}
 int[] tmp;
 tmp=new int[array.length+1];
 System.arraycopy(array,0,tmp,0,array.length);
 return tmp;
}

final public static long[] incLongArraySize(long[] array)
{
 if(array==null) { array=new long[1];return array;}
 long[] tmp;
 tmp=new long[array.length+1];
 System.arraycopy(array,0,tmp,0,array.length);
 return tmp;
}

final public static float[] incFloatArraySize(float[] array)
{
 if(array==null) { array=new float[1];return array;}
 float[] tmp;
 tmp=new float[array.length+1];
 System.arraycopy(array,0,tmp,0,array.length);
 return tmp;
}


/* SIMPLE WILD CARD REGEXP */

/* hvezdicka muze byt jen na konci retezce */

final public static String simpleWildMatch(String mask, String test)
{
 if(mask==null) return test;
 int i=mask.indexOf('*',0);
 if(i==-1) {
            if(test.equals(mask)) return test;
             else
               return null;
           }
 int tl=test.length();
 int ml=mask.length();
 if(i!=ml-1) throw new IllegalArgumentException("* is not at end of string "+mask);

 if(test.regionMatches(0,mask,0,ml-1))

  if (tl>=ml) return test.substring(ml-1);
          else
              return "";

 return null;
}

final public void garbage_collection(long gcinterval)
{
     garbage g;
     while(true)
     {
	 System.out.println(new Date()+" running garbage collection.");
         g=new garbage(cache_dir);
         g.garbage_collection();
         System.out.println(new Date()+" garbage collection ended.");
	 if(gcinterval<=0) break;
	 try{
           System.out.println(new Date()+" will run next garbage collection in "+cacheobject.printDelta(gcinterval)+".");
	   System.gc();
	   Thread.sleep(gcinterval);
	 }
	 catch (InterruptedException e)
	 {
	     break;
	 }
     }
}

final public void rebalance()
{
 System.out.println(new Date()+" rebalancing directories to "+swap_level1_dirs+"/"+swap_level2_dirs+" levels.");
 garbage g=new garbage(cache_dir);
 g.rebalance();
 System.out.println(new Date()+" operation ended.");
}

final public void cacheimport(String d)
{
 repair.quiet=true;
 repair.nosave=true;
 repair.force=false;
 System.out.println(new Date()+" importing from cache on "+d);
 garbage g=new garbage(cache_dir);
 g.cacheimport(d);
 System.out.println(new Date()+" operation ended.");
}

final public void cacheexport(String d,int type,long diff)
{
 repair.quiet=true;
 repair.nosave=true;
 repair.force=false;
 System.out.println(new Date()+" exporting cache to "+d);
 garbage g=new garbage(cache_dir);
 g.export(d,type,diff);
 save();save(); // flush changed .cacheinfos
 System.out.println(new Date()+" operation ended.");
}

final public void dirimport(String d)
{
 repair.quiet=true;
 repair.nosave=true;
 repair.force=false;
 System.out.println(new Date()+" importing "+d);
 garbage g=new garbage(cache_dir);
 g.dirimport(d);
 System.out.println(new Date()+" operation ended.");
}

final public void fake_garbage_collection()
{
 System.out.println(new Date()+" [FAKE] simulating garbage collection.");
 System.out.println(new Date()+" [FAKE] only bad files will be deleted from cache.");
 garbage g=new garbage(cache_dir);
 g.fake_garbage_collection();
 System.out.println(new Date()+" [FAKE] garbage collection ended.");
}

final public void kill_unref()
{
 System.out.println(new Date()+" killing unreferenced files.");
 garbage g=new garbage(cache_dir);
 g.scandel=0;
 g.cleanup();
 System.out.println(new Date()+" "+g.scandel+" unreferenced files removed.");
}

final static private boolean is_wild(String r)
{
 if(r.indexOf('*',0)==-1) return false;
  else
   return true;
}

final static private void touch_flag(String s)
{
 if(s==null) return;
 try
 {
  java.io.FileOutputStream f=new java.io.FileOutputStream(s);
  f.close();
 }
 catch (IOException ignore)
 {}
}

final static private boolean checkFlag(String f)
{
 if(f==null) return false;
 File fl=new File(f);
 if(fl.canRead())
    {fl.delete();return true;}
 return false;
}

/* parse real regexp file */
final static gnu.rex.Rex parseRealRegexpFile(String fname)
{
 gnu.rex.Rex.config_Alternative("|");
 gnu.rex.Rex.config_GroupBraces("(",")");
 try
 {
 String tmprx="";
 String line,token;
 StringTokenizer st;
 boolean first=true;

 DataInputStream dis=new DataInputStream(new BufferedInputStream(new FileInputStream(fname)));

 while ( (line = dis.readLine()) != null)
 {
      if(line.startsWith("#")) continue;
      st=new StringTokenizer(line);
      if(st.hasMoreTokens()==false) continue;
      token=st.nextToken("\r\n").trim();
      if(token.length()==0) continue;
      if(token.charAt(0)!='^' && token.charAt(0)!='.')
	  token=".*"+token;
      if(token.charAt(token.length()-1)!='$'
       &&token.charAt(token.length()-1)!='*')
	  token+=".*";
      token="("+token+")";
      if(!first) {
	           tmprx+="|";
		 }
      else
	 first=false;
      tmprx+=token;
 }
 dis.close();
 // System.out.println("RX build from cfgfile: "+tmprx);
 return gnu.rex.Rex.build(tmprx);
 }
 catch (IOException ioe)
  {
   System.out.println(new Date()+" I/O Error reading file "+fname);
  }
 catch (gnu.rex.RegExprSyntaxException zatracene)
 {
   System.out.println(new Date()+" regexp compilation failed: "+zatracene);
 }
 return null;
}

/* parse old wildcard file */
final static regexp[] parseRegexpFile(String fname,regexp fail[])
{
 try
 {
 String line,token;
 StringTokenizer st;

 DataInputStream dis=new DataInputStream(new BufferedInputStream(new FileInputStream(fname)));
 while ( (line = dis.readLine()) != null)
 {
      if(line.startsWith("#")) continue;
      st=new StringTokenizer(line);
      if(st.hasMoreTokens()==false) continue;
      token=st.nextToken();
      if(token.indexOf('*')==-1 &&
	 token.indexOf('/')==-1 
	) 
	  /* domain name only support */
	  token="*"+token+"/*";
      fail=addRegexpToArray(token,fail,true);
 }
 dis.close();

 }
 catch (IOException ioe)
  {
   System.out.println(new Date()+" I/O Error reading file "+fname);
  }
 return fail;
}

/* load block c00kie */
final static String[] parseCookieFile(String fname)
{
 String fail[]=null;
 try
 {
 String line,token;
 StringTokenizer st;

 DataInputStream dis=new DataInputStream(new BufferedInputStream(new FileInputStream(fname)));
 while ( (line = dis.readLine()) != null)
 {
      if(line.startsWith("#")) continue;
      st=new StringTokenizer(line);
      while(st.hasMoreTokens())
      {
        token=st.nextToken().toLowerCase();

        if(token.indexOf('*')>-1)
         {
          System.out.println(new Date()+" [ERROR] Wildcards records are not supported in cookie_file");
          continue;
         }
        else
         if(token.equalsIgnoreCase("all")) { return null;}
        fail=addStringToArray(token,fail);

      }
 }
 dis.close();

 }
 catch (IOException ioe)
  {
   System.out.println(new Date()+" I/O Error reading cookie file "+fname);
  }
 return fail;
}

/* load redir.cnf file */
final static void parseRedirFile(String fname,boolean red)
{
 // clear old redirects
 if(red)
 {
     redirfrom=null;
     redirto=null;
     redirpart=null;
 } else
 {
     remapfrom=null;
     remapto=null;
     remappart=null;
 }
 
 try
 {
 String line,token1,token2;
 int part;
 StringTokenizer st;

 DataInputStream dis=new DataInputStream(new BufferedInputStream(new FileInputStream(fname)));
 while ( (line = dis.readLine()) != null)
 {
      if(line.startsWith("#")) continue;
      st=new StringTokenizer(line);
      while(st.countTokens()>1)
      {
        token1=st.nextToken();

        if(token1.indexOf('*')==-1)
        {
	  token1+="*";
        }
	token2=st.nextToken();
	if(token2.endsWith("*"))
	{
	    // remote trailing *
	    token2=token2.substring(0,token2.length()-1);
	    part=1;
	} else
	    part=0;
	if(st.hasMoreTokens())
	    try
	    {
		part=Integer.valueOf(st.nextToken()).intValue();
	    }
	    catch (NumberFormatException nfe) {}
        if(part<=0) part=0;
	else
	{
	    //check if part is not too high
	    regexp r;
	    int n;
	    r=new regexp(token1,false);
	    n=r.search(token1).length;
	    if(part>n) part=n;
	}
	
	if(red)
	{
	    if(-1==isInRegexpArray(token1,redirfrom))
	    {
		redirfrom=addRegexpToArray(token1,redirfrom,false);
		redirto=addStringToArray(token2,redirto);
		redirpart=incIntegerArraySize(redirpart);
		redirpart[redirpart.length-1]=part;
	    }
	}
	else
	{
	    if(-1==isInRegexpArray(token1,remapfrom))
	    {
		remapfrom=addRegexpToArray(token1,remapfrom,false);
		remapto=addStringToArray(token2,remapto);
		remappart=incIntegerArraySize(remappart);
		remappart[remappart.length-1]=part;
	    }
	}
      }
 }
 dis.close();

 }
 catch (IOException ioe)
  {
   System.out.println(new Date()+" I/O Error reading redir or remap file "+fname);
  }
 return ;
}

/* formats supported:
 * 1.   hostname or ip address
 * 2.   hostname or ip address / netmask
 * 3.   hostname or ip address / number of bits
 */ 
final public static void addInetAdr(String iadr)
{
   byte[] adr;
   byte[] mask;

   InetAddress a;
   StringTokenizer st=new StringTokenizer(iadr,"/");
   try
   {
	  // bkn3@columbia.edu - changed from getByName to getByAddress to work on JXTA and
	  // avoid name to IP network resolutions.
      a=P2PInetAddress.getByAddress(st.nextToken(), null);
   }
   catch (UnknownHostException uf) 
   { 
       System.out.println(new Date()+" [ERROR] Can not resolve hostname "+iadr);
       return;
   }
   adr=a.getAddress();
   /* init mask with FF */
   mask=new byte[adr.length];
   for(int i=0;i<adr.length;i++)
       mask[i]=(byte)0xFF;

   if(st.hasMoreTokens())
   {
       iadr=st.nextToken();
       if(iadr.indexOf('.')>0)
       {
	   try
	   {
	      // bkn3@columbia.edu - changed from getByName to getByAddress to work on JXTA and
		  // avoid name to IP network resolutions.
	      a=P2PInetAddress.getByAddress(iadr, null);
	   }
	   catch (UnknownHostException uf) { 
	       System.out.println(new Date()+" [ERROR] Can not resolve hostmask "+iadr);
	       return;
	   }
	   mask=a.getAddress();
       } else
       {
	   /* convert number of bits to netmask */
	   int bits=Integer.valueOf(iadr).intValue();
	   /* zero netmask */
	   for(int i=0;i<mask.length;mask[i++]=0);
	   int pos=0;
	   while(bits>0)
	   {
	       int t;
	       t=(bits>8?8:bits);
	       int v;
	       v=0;
	       for(int i=1;i<=t;i++)
		   v=(v >> 1)|0x80;
	       bits-=t;
	       mask[pos++]=(byte)v;
	   }
       }
   }
   /* apply mask to adr */
   for(int i=0;i<adr.length;i++)
   {
       //System.out.println(adr[i]+"/"+mask[i]);
       adr[i]&=mask[i];
   }
   
   /* add to httpreq */
   if(httpreq.allowed==null)
   {
       httpreq.allowed=new byte[0][];
       httpreq.allowedmask=new byte[0][];
   }
   
   byte[] tmp[];
   tmp=new byte[httpreq.allowed.length+1][];
   System.arraycopy(httpreq.allowed,0,tmp,0,httpreq.allowed.length);
   tmp[httpreq.allowed.length]=adr;
   httpreq.allowed=tmp;
   tmp=new byte[httpreq.allowedmask.length+1][];
   System.arraycopy(httpreq.allowedmask,0,tmp,0,httpreq.allowedmask.length);
   tmp[httpreq.allowedmask.length]=mask;
   httpreq.allowedmask=tmp;
}

/* checks inet address agains allowed list, returns true if good */
final public static boolean checkInetAdr(byte[] adr)
{
    if(httpreq.allowed==null) return true;
    
nextadres:for(int i=httpreq.allowed.length-1;i>=0;i--)
    {
	for(int j=0;j<adr.length;j++)
	{
	    if( (adr[j] & httpreq.allowedmask[i][j]) != httpreq.allowed[i][j])
		continue nextadres;
	}
	return true;
    }
    
    return false;
}


final public static boolean getYesNo(String s)
{
  if(s==null) return false;
  if(s.length()==0) return false;
  
  s=s.toLowerCase();

  if(s.equals("off") || s.equals("no") || s.equals("0") || s.equals("false"))
    return false;
    
  if(s.equals("on") || s.equals("yes") || s.equals("1") || s.equals("true"))
    return true;

  System.err.println("[SMARTCACHE] Can not determine if `"+s+"` means yes or no.");
  return false;
}

static final public void handleBlock(request req) throws IOException
{
       cacheobject.c_block++;
       if(req.method==httpreq.REQUEST_POST)
	   req.keepalive=false; // disable keepalive on POST reply
       if(custom403==null) req.send_error(403,"Forbidden by rule");
         else
            if(custom403.charAt(0)=='0') {
		 if(req.getURL().endsWith(".js"))
		 {
		    req.make_headers(200,"text/html",null,null,1,new Date().getTime(),0,null);
		    req.send_headers();
		    req.sendString(" ");
		 }
		 else
		 {
		    req.make_headers(200,"image/gif",null,null,request.GIF.length,886828316241L,0,"\"Builtin-gif\"");
		    req.send_headers();
		    req.sendBytes(request.GIF);
		 }
	      }
	    else
            if(custom403.charAt(0)=='-') {
                                       req.send_error(204,"No Content");
                                       }
	    else
	    {  
		req.make_headers(301,null,null,custom403,0,new Date().getTime(),0,null);
	        req.send_headers();
	    }

       req.close();
}

} /* class */
