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

import java.net.*;
import java.io.*;
import java.util.Date;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Vector;

public class ui implements Runnable
{
    public final static String VERSION="0.17";
    public final static String ESCAPE=": %=?&\"#\\";
    
    public final static String SEARCHCACHE="dirs.";
    public final static String SEARCHCACHEMAGIC="SC-DIRS-CACHE1";
    public static double searchcachefactor=80.0;
    
    public static final int OFF=0;
    public static final int ON=1;
    public static final int ASK=2;

    public static final int NAME_LEN=30;
    public static final int TYPE_LEN=18;
    public static final int SIZE_LEN=9;
    public static final int DIR_LEN=4;
    public static final int DEL_LEN=3;
    public static final String DEL_STR="dEl";

    private static DateFormat formatter= new SimpleDateFormat("dd-MMM-yyyy", Locale.US);
    
    public static int uiport;
    public static InetAddress uiadr;
    public static String ui_hostname;

    public static int loader_add_missing;
    public static int loader_add_reloads;
    public static String loader_queue_file;
    public static String loader_refresh_depth;
    public static String loader_add_depth;

    public static final String LOCK="-UI-LOCK-OBJECT-";
    public static final String SEARCHLOCK="-UI-SEARCH-LOCK-OBJECT-";
    
    private ServerSocket server;
    
ui()
{
 if(uiport==0) {
     server=null;
     loader_add_missing=OFF;
     loader_add_reloads=OFF;
     return;
 }

 if(uiadr!=null)

  try{
  server=new ServerSocket(uiport,10,uiadr);
  }
  catch(IOException e) {
      System.err.println("[SMARTCACHE] ERROR: Cannot bind to my UI port "+uiport+"/"+uiadr.getHostAddress()+"\n[SMARTCACHE] UI disabled, Reason: "+e);
      loader_add_missing=OFF;
      loader_add_reloads=OFF;
  }
   else
  try{
    server=new ServerSocket(uiport);
  }
  catch(IOException e) {
      System.err.println("[SMARTCACHE] ERROR: Cannot bind to my UI port "+uiport+"/*"+"\n[SMARTCACHE] UI disabled, Reason: "+e);
      loader_add_missing=OFF;
      loader_add_reloads=OFF;
  }
  /* get localhost if not set */
  if(ui_hostname==null) ui_hostname="127.0.0.1";
}

public final void run()
{
  ThreadGroup clients;
  uireq client;
  Socket clientSocket;

  if(server==null) return;

  clients=new ThreadGroup("UI-clients");
  System.out.println(new Date()+" "+scache.CACHENAME+" UI "+VERSION+" ready on "+uiport+"/"+(uiadr==null?"*":uiadr.getHostAddress()));
   while (true) {
	    clientSocket = null;
            client=null;
            try {
                clientSocket = server.accept();
                } catch (IOException e) {
                System.err.println("[SMARTCACHE] Warning: UI Accept failed: "+e);
                continue;
            }
            if(clients.activeCount()>scache.MAXHTTPCLIENTS)
              try {
              System.err.println(new Date()+" [SMARTCACHE] Warning: UI Active connections limit ("+scache.MAXHTTPCLIENTS+") reached, request rejected.");
	      clientSocket.setSoLinger(true,0);
              clientSocket.close();}
              catch(IOException e) {}
              finally { continue;}
            client=new uireq(clientSocket);
            new Thread(clients,client).start();
   }  /* listen */
}

public final static String process(String URL)
{
  if(URL.startsWith("/loader/setdepth?"))
  {
      String t=getparam(URL,"type");
      URL=getparam(URL,"depth");
      if(t!=null && URL!=null)
      {
	  if(t.equals("add"))
	      loader_add_depth=URL;
	  else
	      loader_refresh_depth=URL;

	  return "Default depth was changed.";
      }
      else
	  return "type and depth must be both set!";
  }
  else
  if(URL.startsWith("/loader/confirm/add?"))
  {
      URL=getparam(URL,"url");
      return "<title>Smart Cache: Confirm adding URL</title>\n<body bgcolor=\"#ffffe5\" text=\"#000000\">You have requested a URL: <em><a href=\""+URL+"\">"+URL+"</a></em> which is not in cache and can not be downloaded from remote server now. Do you want to add this URL to Smart Cache Loader queue for later batch retrieve?<p>\n"+
             "<form method=get action=\"/loader/add\">\n"+
	     "URL: <input type=text name=url size=40 value=\""+URL+"\"><br>\n"+
	     "Fetch depth: <input type=text name=depth maxlength=3 size=3 value=\""+loader_add_depth+"\"><br>\n"+
	     "Loader options: <input type=text size=20 name=options><br>\n"+
	     "<input type=submit value=\"Yes, Add it\">\n"+
	     "</form>\n"+
	     "<p><a href=\"/ui/add?off\">[X] Do not show this dialog again.</a>\n<p><a href=\"/ui/add?on\">[X] Next time add without asking.</a>\n"+
	     "<p><form method=get action=\"/loader/setdepth\">\n"+
	     "Set default depth for load to: <input type=text name=depth maxlength=3 size=3 value=\""+loader_add_depth+"\"><input type=submit value=OK>\n<input type=hidden name=type value=add></form>";
  } else
  if(URL.startsWith("/loader/confirm/refresh?"))
  {
      URL=getparam(URL,"url");
      return "<title>Smart Cache: Confirm refresh URL</title>\n<body bgcolor=\"#ffe5e5\" text=\"#000000\">You have requested a forced refresh of URL: <em><a href=\""+URL+"\">"+URL+"</a></em> which can not be done from remote server now. If this reforced refresh was auto requested by your browser (using Bookmarks), click on URL to continue.\n<p>Do you want to add this URL to Smart Cache Loader queue for later batch retrieve?<p>\n"+
             "<form method=get action=\"/loader/add\">\n"+
	     "URL: <input type=text name=url size=40 value=\""+URL+"\"><br>\n"+
	     "Refresh depth: <input type=text name=depth maxlength=3 size=3 value=\""+loader_refresh_depth+"\"><br>\n"+
	     "Loader options: <input type=text size=20 name=options><br>\n"+
	     "<input type=submit value=\"Yes, Add it\">\n"+
	     "</form>\n"+
	     "<p><a href=\"/ui/refresh?off\">[X] Do not show this dialog again.</a>\n<p><a href=\"/ui/refresh?on\">[X] Next time add to queue without asking.</a>\n"+
	     "<p><form method=get action=\"/loader/setdepth\">\n"+
	     "Set default depth for refresh to: <input type=text name=depth maxlength=3 size=3 value=\""+loader_refresh_depth+"\"><input type=submit value=OK>\n<input type=hidden name=type value=refresh></form>";
  } else
  if(URL.startsWith("/loader/add?") || URL.startsWith("/loader/refresh?"))
  {
	  String url=getparam(URL,"url");
	  String depth=getparam(URL,"depth");
	  String options=getparam(URL,"options");
	  
	  if(depth==null) 
	      if(URL.startsWith("/loader/add"))
		  depth=loader_add_depth;
	      else
		  depth=loader_refresh_depth;
	  else
	      if(depth.length()==0) depth=null;
	  
	  if(options==null) options="";
	  if(url==null)
	      return "Error: Can not add null URL!";
	  if(loader_queue_file==null)
	      return "Can not add URL! Error: loader_queue_file is not defined in scache.cnf.";
	  synchronized(LOCK)
	  {
	      Hashtable q=load_ldr_queue();
	      q.put(url,(depth!=null? "depth="+depth : "")+' '+options);
	      try
	      {
	         save_ldr_queue(q);
		 return "<title>Smart Cache: URL added!</title>\nRequested URL <a href=\""+url+"\">"+url+"</a> was sucessfully added to Smart Cache loader queue file.<p>Run: \"java loader @loader_queue_file\" for batch fetch. Delete loader_queue_file after that.<p><a href=\"/loader/queue/show\">Show current queue</a>.";
	      }
	      catch (IOException dd)
	      {
		  return "Error writing to loader queue file.";
	      }
	  }
  } else
  if(URL.startsWith("/loader/queue/show"))
  {
     StringBuffer result=new StringBuffer(512);
     Hashtable q;

     if(loader_queue_file==null)
	 return "Smart Cache loader support is not configured.";
     synchronized(LOCK)
     {
       q=load_ldr_queue();
     }
     result.append("<title>Smart Cache: Load Queue</title>\n<h2>Loader Queue</h2>\n");
     for(Enumeration en=q.keys();en.hasMoreElements();)
     {
	 URL=(String)en.nextElement();
	 result.append("<a href=\"/loader/queue/delete?url=");
	 result.append(escape(URL));
	 result.append("\">Del</a> <a href=\"");
	 result.append(URL);
	 result.append("\">");
	 result.append(URL);
	 result.append("</a> (");
	 result.append(q.get(URL));
	 result.append(")<br>\n");
     }
     result.append("<p>End of queue.\n\n<p><a href=\"/\">Back to home</a>.\n");
     return result.toString();
  } else
  if(URL.startsWith("/loader/queue/delete?"))
  {
     String url=getparam(URL,"url");
     if(url==null)
	 return "Can not delete null URL from queue!";
     synchronized(LOCK)
     {
        Hashtable q=load_ldr_queue();
        q.remove(url);
	try
	{
	    save_ldr_queue(q);
	    return "Requested URL "+url+" has been removed from queue (if exists there).<p><a href=\"/loader/queue/show\">Show queue</a>.\n<p><a href=\"/\">Back to home</a>.\n";
	}
	catch (IOException e)
	{
	    return "Error updating loader queue file.";
	}
     }
  } else
  if(URL.startsWith("/dmachine/queue/show"))
  {
     StringBuffer result=new StringBuffer(512);
     Hashtable q;

     if(mgr.dmachine_queue==null)
	 return "Download Machine support is not configured.";
     synchronized(LOCK)
     {
       q=load_dm_queue();
     }
     result.append("<title>Download Machine Queue</title>\n<h2>Download machine Queue</h2>\n");
     for(Enumeration en=q.keys();en.hasMoreElements();)
     {
	 URL=(String)en.nextElement();
	 result.append("<a href=\"/dmachine/queue/delete?url=");
	 result.append(escape(URL));
	 result.append("\">Del</a> <a href=\"");
	 result.append(URL);
	 result.append("\">");
	 result.append(URL);
	 result.append("</a><br>\n");
     }
     result.append("<p>End of queue.\n\n<p><a href=\"/\">Back to home</a>.\n");
     return result.toString();
  } else
  if(URL.startsWith("/dmachine/queue/delete?"))
  {
     String url=getparam(URL,"url");
     if(url==null)
	 return "Can not delete null URL from queue!";
     synchronized(LOCK)
     {
        Hashtable q=load_dm_queue();
        q.remove(url);
	try
	{
	    save_dm_queue(q);
	    return "<title>Delete from DM queue</title>Requested URL "+url+" has been removed from Download machine queue (if exists there).<p><a href=\"/dmachine/queue/show\">Show DM queue</a>.\n<p><a href=\"/\">Back to home</a>.\n";
	}
	catch (IOException e)
	{
	    return "Error updating dmachine queue file.";
	}
     }
  } else
  if(URL.startsWith("/store/browse?"))
  {
      boolean del=false;
      String bURL;
      bURL=getparam(URL,"showdel");
      if(bURL!=null)
	  if(bURL.length()>0)
	      del=true;
      URL=getparam(URL,"dir");

      if(URL==null)
      {
	  return "dir argument is required!";
      }
      if(URL.indexOf("..")>-1) 
	  return "Unsecure directory name.";
      StringBuffer ans=new StringBuffer(2048);
      ans.append("<title>Smart Cache directory browsing</title>\n<h2>Browsing cache directory ");
      ans.append(URL);
      ans.append("</h2>\n");

      File f=new File(mgr.cache_dir+URL);
      if(URL.length()==0 || !f.isDirectory())
	  ans.append("This is not a valid directory!");
      else
      {
	  ans.append("\n<pre>");
	  ans.append(text_align(" ",DEL_LEN+DIR_LEN));
	  ans.append(text_align("Name",NAME_LEN));
	  ans.append(text_align("Date",TYPE_LEN));
	  ans.append(text_align("Size",SIZE_LEN));
	  ans.append("\n<hr width=\"80%\" align=left>\n");
	  File z=new File(URL.substring(0,URL.length()-1));
	  /* dir up link */
	  bURL=z.getParent();
	  if(bURL!=null)
	  {
	      ans.append(text_align(" ",DEL_LEN));
	      ans.append(text_align("DIR",DIR_LEN));
	      ans.append("<a href=\"/store/browse?dir=");
	      ans.append(escape(bURL));
	      if(bURL.length()>1)
		  ans.append(escape(File.separator));
	      ans.append("\">.. Parent directory</a>\n");
	  }
	  /* subdirs links */
	  String filez[]=f.list();
	  if(filez!=null)
	  {
	      for(int i=0;i<filez.length;i++)
	      {
		  z=new File(f,filez[i]);
		  if(z.isDirectory())
		  {
		      // handle delete sign
		      if(del==true)
		      {
			  ans.append("<a href=\"/store/rmdir?dir=");
			  ans.append(escape(URL));
			  ans.append(escape(filez[i]));
			  ans.append(escape(File.separator));
			  ans.append("\">");
			  ans.append(DEL_STR);
			  ans.append("</a>");
		      } else
	                  ans.append(text_align(" ",DEL_LEN));
		      
	              ans.append(text_align("DIR",DIR_LEN));
		      ans.append("<a href=\"/store/browse?dir=");
		      ans.append(escape(URL));
		      ans.append(escape(filez[i]));
		      ans.append(escape(File.separator));
		      ans.append("\">");
		      ans.append(filez[i]);
		      ans.append("</a>\n");
		  }
	      }
	  }

	  /* objects */
	  bURL=directoryToURL(URL);
	  cachedir d=httpreq.mgr.getDir(mgr.cache_dir+URL,bURL);
	  
	  for(Enumeration en=d.getObjects();en.hasMoreElements();)
	  {
	      cacheobject o;
	      o=(cacheobject)en.nextElement();
	      if(o.needSave()==false) continue;
	      if(del==true)
	      {
		  ans.append("<a href=\"/store/delete?url=");
		  ans.append(escape(bURL));
		  ans.append(escape(o.getName()));
		  ans.append("\">");
		  ans.append(text_align(DEL_STR,DEL_LEN+DIR_LEN));
		  ans.append("</a>");	  
	      } else
	          ans.append(text_align(" ",DIR_LEN+DEL_LEN));
	      ans.append("<a href=\"");
	      ans.append(bURL);
	      ans.append(o.getName());
	      ans.append("\">");
	      if(o.getName().length()==0)
	      {
		  ans.append(text_align("**Welcome**",NAME_LEN-1));
	      } else
	        ans.append(text_align(o.getName(),NAME_LEN-1));
	      ans.append(" ");
	      // ans.append(text_align(o.getType(),TYPE_LEN-1));
	      ans.append(text_align(formatter.format(new Date(o.getDate())),TYPE_LEN-1));
	      ans.append(" ");
	      ans.append(text_align(""+o.getSize(),SIZE_LEN));
	      ans.append("</a>\n");
	  }
      }

      ans.append("<hr width=\"80%\" align=left></pre><br>\n");
      if(del==false)
      {
	  ans.append("<a href=\"/store/browse?dir=");
	  ans.append(escape(URL));
	  ans.append("&showdel=true\">[X] Enable Delete</a> in this directory.\n");
      }
      ans.append("<p><form action=\"/store/search\" method=get>\nSearch for: <input type=text name=dir>\nDepth: <input type=text name=depth size=3 maxlength=3 value=2><input type=submit value=\"Search\"></form>\n");
      return ans.toString();
  } else
  if(URL.startsWith("/store/search?"))
  {
      String depth=getparam(URL,"depth");
      if(depth==null) depth="2";
      if(depth.length()==0) depth="2";
      URL=getparam(URL,"dir");
      StringBuffer ans=new StringBuffer(2048);
      if(URL==null)
	  return "Can not search for null string";
      if(URL.length()==0)
	  return "Can not search for empty string";
      ans.append("<title>Smart Cache search results: "+URL+"</title>\n<h1>Directory search for ");
      ans.append(URL);
      ans.append("</h1>\n");
      int dpth=0;
      try
      {
         dpth=Integer.valueOf(depth).intValue();
      }
      catch (NumberFormatException z)
      {
	  return "ERROR: Search depth is not a number";
      }
      if(dpth<2) dpth=2;

      Vector v=null;
      long now;
      synchronized(SEARCHLOCK)
      {
	  now=System.currentTimeMillis();
	  v=search_loadcache(dpth);
	  if(v==null)
	  {
	      /* rebuild cache */
	      v=search_buildcache(mgr.cache_dir,dpth,null);
	      search_savecache(v,dpth,System.currentTimeMillis()-now);
	      ans.append("<font size=\"-1\">Search cache was rebuilded in ");
	      ans.append((System.currentTimeMillis()-now)/1000.0+" seconds.</font>\n<p>");
	  } else
	  {
	      ans.append("<font size=\"-1\">Search cache loaded in ");
	      ans.append((System.currentTimeMillis()-now)/1000.0+" seconds.</font>\n<p>");
	  }
      }
      now=System.currentTimeMillis();
      search_searchcache(v,URL,ans);
      ans.append("<p><font size=\"-1\">Search took ");
      ans.append((System.currentTimeMillis()-now)/1000.0+" seconds.</font>\n");
      ans.append("<p><form action=\"/store/search\" method=get>\nSearch for: <input type=text name=dir value=\"");
      ans.append(URL);
      ans.append("\">Depth: <input type=text name=depth size=3 maxlength=3 value="+dpth+"><input type=submit value=\"Search\"></form>\n");
      return ans.toString();
  }
  else
  if(URL.startsWith("/store/delete?"))
  {
      URL=getparam(URL,"url");
      if(URL==null) return "Missing URL argument!";
      String parsed[];
      parsed=mgr.parseURL(URL,null);
      /* konverze na local dir */
      String locdir=mgr.getLocalDir(parsed[0],parsed[1],parsed[2],parsed[4]);
      cachedir dir;
      dir=httpreq.mgr.getDir(locdir,parsed[4],parsed[0],parsed[1],parsed[2]);
      cacheobject o=dir.getObject(parsed[3]);
      o.delete();
      return "<title>Smart Cache: URL deletion</title>\nObject was deleted.";
  } 
  else
  if(URL.startsWith("/store/rmdir?"))
  {
      String bURL;
      URL=getparam(URL,"dir");
      if(URL==null) return "Missing dir argument!";
      if(URL.indexOf("..")>-1) 
	  return "Unsecure directory name.";
      File f=new File(mgr.cache_dir+URL);
      if(URL.length()==0 || !f.isDirectory())
	  return "This is not a valid directory!";
      else
      {
	  bURL=directoryToURL(URL);
	  cachedir d=httpreq.mgr.getDir(mgr.cache_dir+URL,bURL);
	  cacheobject o;
	  for(Enumeration en=d.getObjects();en.hasMoreElements();)
	  {
	      o=(cacheobject)en.nextElement();
	      o.delete();
	  }
	  return "<title>Smart Cache: Directory purged</title>\nAll objects in directory deleted. (Except subdirs).\n";
      }
  } 
  else
  if(URL.startsWith("/store/purgesearchcache"))
  {
      File d=new File(mgr.cache_dir);
      String filez[];
      filez=d.list();
      
      if(filez!=null)
	  for(int i=filez.length-1;i>=0;i--)
	      if(filez[i].startsWith(SEARCHCACHE))
		  new File(d,filez[i]).delete();
      return "<title>Search caches purged</title><h2>Search caches purged</h2>\n<p><a href=\"/\">Back to home</a>.\n";
  }
  else
  if(URL.startsWith("/ui/refresh?off"))
  {
	      ui.loader_add_reloads=OFF;
	      return "<title>Change refresh watch</title>\nReload watching has been turned off.<p><a href=\"/\">Back to home</a>.\n";
  }
  else
  if(URL.startsWith("/ui/refresh?on"))
  {
	ui.loader_add_reloads=ON;
        return "<title>Change refresh watch</title>\nConfirmation dialog for Reload watching has been turned off.<p><a href=\"/\">Back to home</a>.\n";
  }
  else
  if(URL.startsWith("/ui/refresh?confirm"))
  {
	ui.loader_add_reloads=ASK;
        return "<title>Change refresh watch</title>\nConfirmation dialog for Reload watching has been turned on.<p><a href=\"/\">Back to home</a>.\n";
  } 
  else
  if(URL.startsWith("/ui/add?off"))
  {
	ui.loader_add_missing=OFF;
	return "<title>Change add watch</title>\nMissing pages support has been turned off.<p><a href=\"/\">Back to home</a>.\n";
  }
  else
  if(URL.startsWith("/ui/add?on"))
  {
	ui.loader_add_missing=ON;
	return "<title>Change add watch</title>\nAuto adding of missing pages has been turned on.<p><a href=\"/\">Back to home</a>.\n";
  }
  else
  if(URL.startsWith("/ui/add?confirm"))
  {
       ui.loader_add_missing=ASK;
       return "<title>Change add watch</title>\nConfirmation of adding missing pages has been turned on.<p><a href=\"/\">Back to home</a>.\n";
  } 
  else
  if(URL.startsWith("/trace/config"))
  {
              StringBuffer ans=new StringBuffer(2048);
	      ans.append("<title>Trace configuration</title>\n<h2>Change Trace configuration</h2><p>\n<table>\n");
              display_trace_switch("Trace request URL",ans,httpreq.trace_url,1);
              display_trace_switch("Trace request headers",ans,request.trace_request,2);
              display_trace_switch("Trace server reply",ans,request.trace_reply,3);
              display_trace_switch("Trace client abort",ans,request.trace_abort,4);
              display_trace_switch("Trace cookie",ans,request.trace_cookie,5);
              display_trace_switch("Trace refresh",ans,cacheobject.trace_refresh,7);
              display_trace_switch("Trace fail",ans,mgr.trace_fail,6);
              display_trace_switch("Trace output pool",ans,ConnectionHandler.trace_keepalive,8);
              display_trace_switch("Trace input keep alive",ans,httpreq.trace_inkeepalive,9);
              display_trace_switch("Trace redirect",ans,mgr.trace_redirect,10);
              display_trace_switch("Trace rewrite",ans,mgr.trace_remap,11);
	      ans.append("</table>\n<p><a href=\"/\">Back to home</a>.");
	      return ans.toString();
  }
  else
  if(URL.startsWith("/trace/set"))
  {
      String what;
      what=getparam(URL,"what");
      if(what==null) return "Missing what argument!";
      int i=0;
      try
      {
         i=Integer.valueOf(what).intValue();
      }
      catch (NumberFormatException ex)
      {
	  return "what argument is not a number!";
      }
      what=getparam(URL,"value");
      if(what==null) return "Missing value argument!";
      boolean v=false;
      if(what.equals("1")) v=true;
      switch(i)
      {
	  case 1:
	      httpreq.trace_url=v;
	      break;
	  case 2:
	      request.trace_request=v;
	      break;
	  case 3:
	      request.trace_reply=v;
	      break;
	  case 4:
	      request.trace_abort=v;
	      break;
	  case 5:
	      request.trace_cookie=v;
	      break;
	  case 6:
	      mgr.trace_fail=v;
	      break;
	  case 7:
	      cacheobject.trace_refresh=v;
	      break;
	  case 8:
	      ConnectionHandler.trace_keepalive=v;
	      break;
	  case 9:
	      httpreq.trace_inkeepalive=v;
	      break;
	  case 10:
	      mgr.trace_redirect=v;
	      break;
	  case 11:
	      mgr.trace_remap=v;
	      break;
	  default:
	      return "Unknown value (no such switch) in what argument.";
      }
      return "<title>Trace configuration changed</title>\nTrace configuration changed. Continue <a href=\"/trace/config\">here</a>.";
  }
  else
  if(URL.startsWith("/homepage"))
  {
      String quote=getparam(URL,"quote");
      if(quote==null) quote="-2"; // random
      int q=-2;
      try
      {
        q=Integer.valueOf(quote).intValue();
      }
      catch (NumberFormatException nfe)
      {
	  return "Quote is not a number";
      }
      if(q<-1) q=(int)(Math.random()/(1f/(float)scache.quotes.length));
      if(q<0) q=0;
      else
	  if (q>=scache.quotes.length) q=scache.quotes.length-1;
      
      return "<title>Smart Cache HTML UI</title>\n<h1>Smart Cache UI Home</h1>\n<hr width=\"50%\"><pre>"+scache.quotes[q]+
      "</pre>\n<center>Quotes: [ <a href=\"/homepage?quote="+(q-1)+"\">&lt;&lt; Prev.</a> | <a href=\"/homepage?quote=-2\">Random</a> | <a href=\"/homepage?quote="+(q+1)+"\"> Next &gt;&gt;</a> ]\n</center><hr width=\"50%\">\n<p>Welcome to "+
     (httpreq.visible_link!=null?"<a href=\""+httpreq.visible_link+"\">":"")+
     "Smart Cache"+
     (httpreq.visible_link!=null?"</a>":"")+
      " HTML User Interface "+VERSION+" running for "+scache.VERSION+" on port "+mgr.ourport+".\n"+
      "<p>You can <a href=\"/store/browse?dir="+escape(File.separator)+"\">browse cache</a>"+
      ", <a href=\"/trace/config\">configure tracing</a>"+
      ", <a href=\"/store/purgesearchcache\">purge search caches</a>"+
      ", <a href=\"/logs?flush\">flush logs</a>"+
      (loader_queue_file!=null?", <a href=\"/loader/queue/show\">show SC Loader queue</a>":"")+
      (mgr.dmachine_queue!=null?", <a href=\"/dmachine/queue/show\">show Download machine queue</a>":"")+
      ".\n"+
      "<h2>Directory search</h2>\n<form action=\"/store/search\" method=get>\nSearch for: <input type=text name=dir>Depth: <input type=text name=depth size=3 maxlength=3 value=2><input type=submit value=\"Search\"></form><p>\n"+
      "<h2>Set console loglevel</h2>\n<form action=\"/logs/console\" method=get>\nSet max. console loglevel to: <select name=\"loglevel\">\n"+
      "<option value=0"+(mgr.loglevel==0?" selected":"")+">Off</option>"+
      "<option value=1"+(mgr.loglevel==1?" selected":"")+">System errors</option>"+
      "<option value=2"+(mgr.loglevel==2?" selected":"")+">Network errors</option>"+
      "<option value=3"+(mgr.loglevel==3?" selected":"")+">Stats</option>"+
      "<option value=4"+(mgr.loglevel==4?" selected":"")+">Info messages</option></select>\n<input type=submit value=\"OK\"></form><p>"+
      "<h2>Setup offline support</h2>"+(loader_queue_file!=null?
       "<p>Reload checks: [ "+
       (loader_add_reloads==OFF?"<b>OFF</b>":"<a href=\"/ui/refresh?off\">off</a>")+" | "+
       (loader_add_reloads==ON?"<b>ON</b>":"<a href=\"/ui/refresh?on\">on</a>")+" | "+
       (loader_add_reloads==ASK?"<b>CONFIRM</b>":"<a href=\"/ui/refresh?confirm\">confirm</a>")+" ]<br>\nMissing pages: [ "+
       (loader_add_missing==OFF?"<b>OFF</b>":"<a href=\"/ui/add?off\">off</a>")+" | "+
       (loader_add_missing==ON?"<b>ON</b>":"<a href=\"/ui/add?on\">on</a>")+" | "+
       (loader_add_missing==ASK?"<b>CONFIRM</b>":"<a href=\"/ui/add?confirm\">confirm</a>")+" ]\n"+
	     "<p><form method=get action=\"/loader/setdepth\">\n"+
	     "Set default depth for load to: <input type=text name=depth maxlength=3 size=3 value=\""+loader_add_depth+"\"><input type=submit value=OK>\n<input type=hidden name=type value=add></form><br>"+
	     "<form method=get action=\"/loader/setdepth\">\n"+
	     "Set default depth for refresh to: <input type=text name=depth maxlength=3 size=3 value=\""+loader_refresh_depth+"\"><input type=submit value=OK>\n<input type=hidden name=type value=refresh></form>\n":"Smart Cache Loader support is not configured.");
  }
  else
      if(URL.startsWith("/logs/console?"))
      {
	  URL=getparam(URL,"loglevel");
	  if(URL==null) return "Error! Required parameter 'loglevel' was not found.";
	  int lvl=3;
	  try
	  {
	     lvl=Integer.valueOf(URL).intValue();
	  }
	  catch (NumberFormatException z)
	  {
	      return "ERROR: Search loglevel is not a number";
	  }
	  if(lvl<0 && lvl>4) lvl=3;
	  mgr.loglevel=lvl; 
	  return "<title>Log level changed</title>\n<h2>Console loglevel has been changed</h2>\n<p><a href=\"/\">Back to home</a>.\n";
      }
      else
      if(URL.equals("/logs?flush"))
      {
	  httpreq.flush(httpreq.logfilez);
	  return "<title>Logs flushed</title>\n<h2>Logs flushed</h2>\n<p><a href=\"/\">Back to home</a>.\n";
      }
		  
  
  return "<title>Bad URL</title>\nERROR: Your requested URL "+URL+" was not recogized as UI valid command!<p><a href=\"/\">Go to the UI homepage</a>.";
}

public final static String getparam(String URL,String arg)
{
    int i1=URL.lastIndexOf(arg+'=');
    if(i1==-1) return null;
    int i2=URL.indexOf('&',i1);
    if(i2==-1) i2=URL.length();
    String part=URL.substring(i1+1+arg.length(),i2);
    return unescape(part);
}

public final static String escape(String URL)
{
    StringBuffer result=new StringBuffer(80);
    char c;
    int l=URL.length();
    for(int i=0;i<l;i++)
    {
	c=URL.charAt(i);
	if(ESCAPE.indexOf(c)>-1)
	{
	    result.append('%');
	    result.append(Integer.toHexString((int)c));
	}
	else
	{
	    result.append(c);
	}
    }
    return result.toString();
}

public final static String unescape(String URL)
{
    StringBuffer result=new StringBuffer(80);
    char c;
    int l=URL.length();
    for(int i=0;i<l;i++)
    {
	c=URL.charAt(i);
	if(c=='%' && i<l-2)
	    try
	    {
		c=(char)Integer.valueOf(URL.substring(i+1,i+3),16).intValue();
		result.append(c);
		i+=2;
	    }
	    catch (NumberFormatException nfe)
	    {
		result.append('%');
		continue;
	    }
	else
	    result.append(c);
    }

    return result.toString();
}

/* konverze localdir na URL */
/* /2/3/www.lakret.cz_2345/ -> http://www.lakret.cz:2345/ */
final static public String directoryToURL(String dir)
{
   String urlpart;
   if(dir.startsWith(mgr.cache_dir)) 
   {
       dir=dir.substring(mgr.cache_dir.length());
   }
   if(dir.length()==0) return null;
   dir=dir.substring(1);
   /* preskocit 2x file-separator */
   int i;
   i=dir.indexOf(File.separatorChar,0);
   if(i!=-1) {
      dir=dir.substring(i+1);
      i=dir.indexOf(File.separatorChar,0);
      if(i!=-1) dir=dir.substring(i+1);
                else
      return null;
   } else return null;
   i=dir.indexOf(File.separatorChar);
   if(i==-1) return null;
   if(File.separatorChar!='/')
       dir=dir.replace(File.separatorChar,'/');
   urlpart=dir.substring(i);
   String hn=dir.substring(0,i);
   String proto=null;
   String port=null;

  i=hn.indexOf('^');
  if(i>-1)
  {
    proto=hn.substring(i+1);
    hn=hn.substring(0,i);
  }

  i=hn.indexOf('_');
  if(i>-1)
  {
    port=hn.substring(i+1);
    hn=hn.substring(0,i);
  }

  return (proto==null?"http":proto)+"://"+hn+(port==null?"":":"+port)+urlpart;
}

final static Hashtable load_ldr_queue()
{
    Hashtable result=new Hashtable(20);
    String line;
    int i;
    StringTokenizer st;
    try
    {
	DataInputStream dis=new DataInputStream(
	                     new BufferedInputStream(
			      new FileInputStream(loader_queue_file),4096
			     )
			    );
	while( (line=dis.readLine())!=null)
	{
	    st=new StringTokenizer(line);
	    i=st.countTokens();
	    if(i==0) continue;
	    line="";
	    for(int j=0;j<i-1;j++)
	    {
		line+=st.nextToken();
	        line+=" ";
	    }
	    result.put(st.nextToken(),line);
	}
	dis.close();
    }
    catch (IOException grr)
    {}

    return result;
}			    

final static void save_ldr_queue(Hashtable data) throws IOException
{
	DataOutputStream dos=new DataOutputStream(
	                     new BufferedOutputStream(
			     new FileOutputStream(loader_queue_file),4096
			     )
			    );
	String URL;
	for(Enumeration keys=data.keys(); keys.hasMoreElements();)
	{
	    URL=(String)keys.nextElement();
	    
	    dos.write(((String)data.get(URL)).getBytes());
            dos.write(' ');
	    dos.write(URL.getBytes());
            dos.write('\n');
	}
	dos.close();
}

final static Hashtable load_dm_queue()
{
    Hashtable result=new Hashtable(20);
    String line;
    int i;
    StringTokenizer st;
    try
    {
	DataInputStream dis=new DataInputStream(
	                     new BufferedInputStream(
			      new FileInputStream(mgr.dmachine_queue)
			     )
			    );
	while( (line=dis.readLine())!=null)
	{
	    st=new StringTokenizer(line);
	    i=st.countTokens();
	    if(i!=1) continue;
	    line=st.nextToken();
	    result.put(line,line);
	}
	dis.close();
    }
    catch (IOException grr)
    {}
    catch (NullPointerException uch)
    {}

    return result;
}

final static void save_dm_queue(Hashtable data) throws IOException
{
        if(mgr.dmachine_queue==null) return;

	DataOutputStream dos=new DataOutputStream(
	                     new BufferedOutputStream(
			     new FileOutputStream(mgr.dmachine_queue)
			     )
			    );
	String URL;
	for(Enumeration keys=data.keys(); keys.hasMoreElements();)
	{
	    URL=(String)keys.nextElement();
	    
	    dos.write(URL.getBytes());
            dos.write('\n');
	}
	dos.close();
}


final static public String text_align(String text,int data)
{
    if(text.length()>=data) return text.substring(0,data);
    StringBuffer sb=new StringBuffer(data);
    sb.append(text);
    for(int i=data-text.length();i>0;i--)
	sb.append(' ');
    return sb.toString();
}

final static private void display_trace_switch(String title,StringBuffer ans,boolean val,int conf)
{
	      ans.append("<tr><td>");
	      ans.append(title);
	      ans.append("</td><td>");

	      if(val==true)
	      {
		  ans.append("<b>ON</b></td><td><a href=\"/trace/set?what=");
		  ans.append(conf);
		  ans.append("&value=0\">off</a></td></tr>\n");
	      } else
	      {
		  ans.append("<a href=\"/trace/set?what=");
		  ans.append(conf);
		  ans.append("&value=1\">on</a></td><td><b>OFF</b></td></tr>\n");
	      }
}

/* Search machine functions */

/* vygeneruje Vector() skladajici se z adresaru o dane hloubce */
public final static Vector search_buildcache(String dir,int depth,Vector v)
{
    Thread.yield();
    if(v==null) v=new Vector(100);
    if(depth<0) return v;
    File f,d;
    f=new File(dir);
    String filez[]=f.list();
    if(filez==null) return v;
    if(filez.length==0) return v;
    int rl=mgr.cache_dir.length();
    String bURL;
    for(int z=filez.length-1;z>=0;z--)
    {
	d=new File(f,filez[z]);
	if(!d.isDirectory()) continue;
	bURL=dir+File.separatorChar+filez[z];
	v.addElement(bURL.substring(rl));
	search_buildcache(bURL,depth-1,v);
    }
    return v;
}

/* prohleda cache */
public final static void search_searchcache(Vector cache,String str,StringBuffer ans)
{
    Enumeration e;
    String s;
    String bURL;
    
    str=str.toLowerCase();
    e=cache.elements();
    
    while(e.hasMoreElements())
    {
	s=(String)e.nextElement();
	if(s.substring(s.lastIndexOf(File.separatorChar)).toLowerCase().indexOf(str)>-1)
	{
	    bURL=directoryToURL(s+File.separatorChar);
	    if(bURL!=null)
	    {
		ans.append("<a href=\"/store/browse?dir=");
	        ans.append(escape(s));
	        ans.append(escape(File.separator));
	        ans.append("\">");
		ans.append(bURL);
	        ans.append("</a><br>\n");
	    }
	}
    }
}

/* zasejvuje cache na disk */
public static final void search_savecache(Vector cache,int depth,long scan)
{
    try
    {
	DataOutputStream dos=new DataOutputStream(
	                     new BufferedOutputStream(
			     new FileOutputStream(mgr.cache_dir+File.separatorChar+SEARCHCACHE+depth),4096
			     )
			    );
	dos.writeUTF(SEARCHCACHEMAGIC);
	dos.writeChar(File.separatorChar);
	dos.writeLong(scan);
	
        Enumeration e=cache.elements();
        while(e.hasMoreElements())
        {
	    dos.writeUTF((String)e.nextElement());
	}
	dos.writeUTF("");
	dos.close();
    }
    catch (IOException ignore)
    {}
}

public static final Vector search_loadcache(int depth)
{
    Vector v;
    File f;
    long scantime;
    String s;

    v=null;
    f=new File(mgr.cache_dir+File.separatorChar+SEARCHCACHE+depth);
    if(!f.exists()) return null;

    try
    {
	DataInputStream din=new DataInputStream(
	                     new BufferedInputStream(
			     new FileInputStream(f),4096
			     )
			    );
	if(!SEARCHCACHEMAGIC.equals(din.readUTF()))
	{
	    // System.out.println("/debug/ searchcache has bad magic");
	    return null; // no magic
	}
	if(File.separatorChar!=din.readChar())
	{
	    // cache from non-compatible OS
	    // System.out.println("/debug/ searchcache from other OS");
	    return null;
	}
	scantime=din.readLong();
	if(f.lastModified()+scantime*searchcachefactor < System.currentTimeMillis())
	{
	    // System.out.println("/debug/ searchcache has expired");
	    return null;  // expired
	}

	v=new Vector(100);
        while(true)
        {
	    s=din.readUTF();
	    if(s.length()==0) break;
	    v.addElement(s);
	}
	din.close();
    }
    catch (IOException ignore)
    {
	// System.out.println("/debug/ error reading searchcache");
    }
    
    return v;
}

} /* class ui */
