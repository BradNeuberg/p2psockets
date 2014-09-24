package org.scache;

/*
 *  Smart Cache, http proxy cache server
 *  Copyright (C) 1998-2002 Radim Kolar
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
import java.util.*;

public class uireq implements Runnable
{
    Socket s;
    DataInputStream in;
    DataOutputStream ou;
    boolean http10;
    
    uireq(Socket cs)
    {
	s=cs;
    }

    public void run()
    {
	http10=true;
	boolean badhost=false;
	try 
	{ 
	    in=new DataInputStream (new BufferedInputStream(s.getInputStream()));
	    ou=new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));

	/* precteme radku GET / */

	s.setSoTimeout(httpreq.client_timeout);

	String req=in.readLine();
	if(req==null) { s.close();ou.close();s=null;ou=null;return;}
        if(httpreq.trace_url==true)
        {
            System.out.println("[UI TRACE "+Thread.currentThread().getName()+"] > "+req);
        }

	StringTokenizer st=new StringTokenizer(req);

	if (req.indexOf(" HTTP/",0)==-1) http10=false;
	else
          while(true)
          { 
	      /* read rest of HTTP headers */
	      req=in.readLine();
              if(req==null) break;
              if(req.length()==0) break;
	      String s1,s2;
	      int j;
	      j=req.indexOf(':',0);
	      if(j==-1) continue;
	      s1=req.substring(0,j).toLowerCase();
	      s2=req.substring(j+1);
	      if(s1.equals("host") && s2.indexOf(ui.ui_hostname)==-1)
		  badhost=true;
          }
	String req2=null;

	/* access check */
	if(!mgr.checkInetAdr(s.getInetAddress().getAddress())) {
	      httpreq.server_error(http10?10:9,403,"Cache access denied.",ou);
	   }

	try
	{
	  req2=st.nextToken();
	  if(!req2.equals("GET")) 
	  {
	     httpreq.server_error(http10?10:9,501,"Method unimplemented in UI",ou);
	  }
	  req2=st.nextToken();
	  if(req2.charAt(0)!='/')
	  {
	    httpreq.server_error(http10?10:9,400,"Request URL not starting with /",ou);
	  }
	  /* handle redirected homepage */
	  if(req2.equals("/"))
	  {
	      req2="/homepage";
	      badhost=true;
	  }
	  
	  if(badhost==false)
	  {
	      send_reply(ui.process(req2));
	  }
	  else
	  {
            StringBuffer ans=new StringBuffer(2048);
	    ans.append("HTTP/1.0 301 Wrong_HOSTNAME\r\nContent-Type: text/html\r\n");
	    ans.append("Location: http://");
	    ans.append(ui.ui_hostname);
	    ans.append(':');
	    ans.append(ui.uiport);
	    ans.append(req2);
	    ans.append("\r\n\r\nBAD HOSTNAME/REDIRECTING TO WELCOME PAGE!\n");

	    ou.writeBytes(ans.toString());
	    ou.close();
	    ou=null;
	    s=null;
	  }
        }
        catch (NoSuchElementException e) 
	{ 
	    httpreq.server_error(http10?10:9,400,"Incomplete request",ou);
	}
	
    }
    catch (IOException err)
    {
    }
}

public final void send_reply(String reply) throws IOException
{
    StringBuffer ans=new StringBuffer(2048);
    if(http10)
    {
	ans.append("HTTP/1.0 200 UI Reply Follows\r\n");
	ans.append("Server: ");
	ans.append(scache.CACHENAME);
	ans.append(" UI ");
	ans.append(ui.VERSION);
	ans.append("\r\nPragma: no-cache\r\nExpires: Sat, 03 Jan 1970 07:33:19 GMT\r\nContent-Type: text/html\r\nConnection: close\r\n\r\n");
    }
    if(reply==null) 
	ans.append("{-No reply- from UI }\n");
    else
	ans.append(reply);
    ou.writeBytes(ans.toString());
    ou.close();
    ou=null;
    s=null;
}

}
