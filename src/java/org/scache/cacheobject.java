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
import java.net.*;

public final class cacheobject{

public static final String RESERVED="<>";
public static final String REDIRSTR="You should be redirected to UI by your browser.\n";
public static final int REDIRSTRLEN=48;
/* statistics */
public static int c_hit,c_miss,c_refresh,c_block;
public static int b_hit,b_miss;

/* static ! */
public static boolean end_dot;
public static boolean keep_deleted;
public static boolean generate_lastmod;
public static boolean hide_errors;
public static boolean escape_backslash;
public static boolean trace_refresh;
public static boolean force_ims_on_reloads;
public static String custom500;
public static String defaultname;
public static regexp[] dmachine_offmask;

public static int auto_compress;
public static int auto_decompress;
public static String nocompress[];

/* P R I V A T E    P R O P E R T Y */
private String name;      /* real name of object */
private String localname; /* stored in file name */
private String location; /* for redirects */
private String etag;
private int size;  /* size of object */
private int httprc;  /* server response */
private long expires, date, lastmod, lru;
private cachedir dir;
private String ctype;    /* Content-Type */
private String encoding; /* Content-Encoding */
private boolean good; /* good or aborted download ? */

cacheobject(String name, cachedir where)
{
 this.name=name;
 dir=where;
}

cacheobject(String name,cachedir where,String ln,long lm,String ct, String enc,int size)
{
 this(name,where);
 localname=ln;
 date=lastmod=lm;
 location=null;
 etag=null;
 lru=System.currentTimeMillis();
 ctype=ct;
 encoding=enc;
 this.size=size;
 httprc=200;
 good=true;
}

final public boolean equals(Object o)
{
 if(o==null || ! (o instanceof cacheobject)) return false;
 cacheobject o1=(cacheobject)o;

 return name.equals(o1.getName());
}

final public int hashCode()
{
 return name.hashCode();
}

final public boolean needSave()
{
 if(localname!=null) return true;
  else
 return false;
}

cacheobject(DataInputStream is, cachedir d,byte version) throws IOException
{
 this.dir=d;
 name=is.readUTF();
 ctype=is.readUTF();
 localname=is.readUTF();
 location=is.readUTF();
 if(location.length()==0) location=null;
 size=is.readInt();
 httprc=is.readInt();
 expires=is.readLong();
 date=is.readLong();
 lastmod=is.readLong();
 lru=is.readLong();
 switch(version)
 {
     case 2:
         good=true;
         break;
     case 4:
         etag=is.readUTF();
         if(etag.length()==0) etag=null;
     case 3:
         encoding=is.readUTF();
         if(encoding.length()==0) encoding=null;
         good=is.readBoolean();
 } 
}

final public void save(DataOutputStream os) throws IOException
{
 if(localname==null) return;

 os.writeUTF(name);
 os.writeUTF(ctype);
 os.writeUTF(localname);
 if(location!=null) os.writeUTF(location);
  else
    os.writeUTF("");
 os.writeInt(size);
 os.writeInt(httprc);
 os.writeLong(expires);
 os.writeLong(date);
 os.writeLong(lastmod);
 os.writeLong(lru);
 // v4
 if(etag!=null) os.writeUTF(etag); else os.writeUTF("");
 // v3
 if(encoding!=null) os.writeUTF(encoding); else os.writeUTF("");
 os.writeBoolean(good);
}

final public void make_request(request r, long reload_age,long min_age, long max_age, float percent,long expire_age,long redir_age) throws IOException
{
 lru=System.currentTimeMillis();

 /* novy objekt */
 if(localname==null) { load_object(r); return;}

 /* test na expiraci */
 if(needRefresh(r,reload_age,min_age,max_age,percent,expire_age,redir_age)) { refresh_object(r); return;}

 /* jinak poslat z cache */
 send_fromcache(r);
}


final private File genTmp()
{
 File out;
 String ld=dir.getLocalDir();
 if(ld==null) return null;
 while(true)
 {
  out=new File(ld+(int)(Math.random()*100000000f)+".tmp");
  if(!out.exists()) break;
 }
 return out;
}

final public void compress(int level)
{
 if(level<=0) return;  /* disabled by cfg */
 if(good==false) return; /* partial download */
 if(encoding!=null) return; /* compressed! */
 if(!ctype.startsWith("text/")) return; /* not text */
 if(localname==null || localname.equals(RESERVED)) return;
 if(size<=auto_compress) return; /* waste of CPU cycles */
 if(nocompress!=null)
  {
    for(int z=nocompress.length-1;z>=0;z--)
    {
      if(localname.toLowerCase().endsWith(nocompress[z])) return;
    }
  }

 File t=genTmp();
 int i;
 try
 {
  GZIPOutputStream gz=new GZIPOutputStream(new FileOutputStream(t),8192);
  DataInputStream sin=new DataInputStream(new BufferedInputStream(
  new FileInputStream(dir.getLocalDir()+localname),4096));
  byte b[]=new byte[4096];
  while(true)
  {
   /* precist */
  i=sin.read(b);
  if(i==-1) break;
  gz.write(b,0,i);
  }

  gz.close();
  sin.close();
  int sz;
  sz=(int)t.length();
  if(sz>=size-1024) { t.delete();return;}
    /* compression saved too litle space */

  String nm; /* new name */
  if(!localname.endsWith(".gz"))
      if(localname.endsWith("_r"))
          nm=genName(localname.substring(0,localname.length()-2)+".gz");
        else
          nm=genName(localname+".gz");
   else
     nm=localname;
        
  File n=new File(dir.getLocalDir()+nm);

  /* rename to new file */
  if(!rename(t,n)) {
    if(mgr.loglevel>0) System.out.println("**COMPRESS ERROR** Rename "+t+" to "+n+" failed.");
                       dir.dirty=true;
                       return;
                   }
  if(!localname.equals(nm)) new File(dir.getLocalDir()+localname).delete();
  /* update fileinfo */
  localname=nm;
  encoding="gzip";
  date=System.currentTimeMillis();
  dir.dirty=true;
  if(mgr.loglevel>3) System.out.println("Compressed: "+dir.getLocalDir()+localname+" ("+(size-sz)+" B, "+(int)(100*(1.0f-(float)sz/size))+"% saved)");
  size=sz;
 }
 catch (IOException j) {if(mgr.loglevel>0) System.out.println("**COMPRESS ERROR** "+j);}
}


final public boolean isValid()
{
  if(localname==null || localname.equals(RESERVED))
  {
    if(garbage.gcloglevel>2) System.out.print("NULL filename");
    return false;
  }
  File f=new File(  dir.getLocalDir()+localname );
  if(!f.isFile())
  {
    if(garbage.gcloglevel>2) System.out.print("No such file");
    return false;
  }
  if(!f.canRead()) {
    if(garbage.gcloglevel>2) System.out.print("No read access");
    return false;
  }
  // check obj. size
  if(f.length()!=size) {
    if(garbage.gcloglevel>2) System.out.print("File size mismatch");
    return false;
  }
  //check obj. date
  if(f.lastModified()>date+2000) { // 2000 is for second div 2
                                   // uncertainty on FAT file system
    if(garbage.gcloglevel>2) System.out.print("Modified after load");
    return false;
  }
  return true; // O.K. !
}

final public String getLocalName()
{
 return localname;
}

/* delete this object from disk and parent directory */
final public void delete()
{
 dir.remove(this);
 if(localname==null) return;
 new File(dir.getLocalDir()+localname).delete();
 localname=null;
}

/* delete file and clear local name */
final public void clearLocalName()
{
 if(localname==null) return;
 new File(dir.getLocalDir()+localname).delete();
 localname=null;
 dir.dirty=true;
}

final public synchronized void regenName()
{
 if(localname==null)
 {
   System.out.println("[INTERNAL_ERR] regenName() called on "+name+" with NULL localname!");
 }
 localname=genName(name);
 dir.dirty=true;
 if(encoding==null) return;

 if(!localname.endsWith(".gz"))
      if(localname.endsWith("_r"))
          localname=genName(localname.substring(0,localname.length()-2)+".gz");
        else
          localname=genName(localname+".gz");
}

/* Generate unique filename */
final public synchronized String genName(String tmp)
{
 File f;
 String localdir=dir.getLocalDir();

 if(localdir==null) return null;
 // System.err.println("in="+name);
   int j=tmp.length()-1;
   if(j>=0)
   {
     byte v[];
     v=new byte[j+1];
     tmp.getBytes(0,j+1,v,0);
     /* jestlize mame ? a nejaky bordel za nim, uriznout! */
     loop1:for(int zz=0;zz<=j;zz++)
       {
        switch(v[zz])
         {
          case 0x3b: // ;
          case 0x3a: // :
          case 0x3d: // =
          case 0x3f: // ?
          tmp=tmp.substring(0,zz);
          // System.err.println("new tmp="+tmp);
          break loop1;
         }
       }
   }
 // System.err.println("in2="+tmp);

 /* pokud je toto redirect, pridat k jmenu _r */
 /* aby se to nemotalo do jmena adresare */
 if(httprc==301 || httprc==302 || httprc==307) tmp+="_r";

  /* nahrazujeme nepratelske znaky */
  if(!end_dot) {
                tmp=tmp.replace('>','}');
                tmp=tmp.replace('<','{');
                // problems with OS/2
                tmp=tmp.replace('|','!');
                }
  if(escape_backslash) tmp=tmp.replace('\\','-');
  if(tmp.length()==0) tmp=defaultname;

 /* pridat .gz ke jmenu pokud je komprimovan */
 if(encoding!=null)
     if(!tmp.endsWith("gz"))
         tmp+=".gz";

  /* Kill tecku if nedovoleno ! */
  if(!end_dot && tmp.endsWith(".")) tmp=tmp.substring(0,tmp.length()-1);

  f=new File(localdir+tmp);
  if(!f.exists()) return tmp;
  /* a nyni kolotoc! */
  int i=0;
  while(true)
  {
    f=new File(localdir+i+tmp);
    if(!f.exists()) break;
    i=(int)(Math.random()*100000000f);
   // if(i==1000) return null; CHECK: need to check??
  }
  return i+tmp;
}


/* L O A D ! */

final private void load_object(request r) throws IOException
{
 WebConnection wc;
 c_miss++;
 if(localname==null) localname=RESERVED; /* prevents from async dir cleaning */
 if(ctype==null) ctype="unknown/unknown";
 String ln;
 r.add_ims(); // do not force full URL caching
 dir.dirty=true;
 try
 {
   wc=r.connectToHost();
 }
 catch (IOException ccc) 
 { 
     if(localname!=null && localname.equals(RESERVED)) localname=null;
     dir.dirty=true;
     /* POSLAT NOT MODIFIED pri load connect err ! */
     if(r.ims>0 || r.etag!=null) { 
                         r.make_headers(304,null,null,null,-1,0,0,r.etag);
                         r.send_headers();
                         return;
                 }
     /* Download machine in offline mode hooks */
     if(dmachine_offmask!=null)
     {
     dmachine:for(int i=dmachine_offmask.length-1;i>=0;i--)
             {
                 if(dmachine_offmask[i].matches(r.URL))
                 {
                     if(mgr.dmachine_ignore!=null)
                     {
                         for(int j=mgr.dmachine_ignore.length-1;j>=0;j--)
                             if(mgr.dmachine_ignore[j].matches(r.URL))
                                 break dmachine;
                     }
                     synchronized(httpreq.mgr)
                     {
                         Hashtable dmq=ui.load_dm_queue();
                         dmq.put(r.URL,r.URL);
                         ui.save_dm_queue(dmq);
                         r.send_error(202,"Request Accepted<br>\nURL <a href=\""+r.URL+"\">"+r.URL+"</a> was successfully added to download machine queue file. Matched by offline URL mask.");
                     }
                     break;
                 }
             }
     }


     /* Loader offline mode hook */
     if(ui.loader_add_missing>0)
     {
         /* redir to UI */
         r.make_headers(302,"text/html",null,"http://"+ui.ui_hostname+":"+ui.uiport+"/loader/"+(ui.loader_add_missing==ui.ASK?"confirm/":"")+"add?url="+ui.escape(dir.URLbase+name),REDIRSTRLEN,99999999,199999999,null);
         r.headers.addElement("Pragma: no-cache");
         r.send_headers();
         r.sendString(REDIRSTR);
     }   else
     /* CUSTOM 500 RC komedie */
     if(custom500==null) r.send_error(500,"Proxy load failed ("+ccc+") and object not found in cache");
        else
     if(custom500.charAt(0)=='0') 
     {
         if(name.endsWith(".js"))
         {
            r.make_headers(200,"text/html",null,null,1,new Date().getTime(),0,null);
            r.send_headers();
            r.sendString(" ");
         }
         else
         {
            r.make_headers(200,"image/gif",null,null,request.GIF.length,886828316241L,0,null);
            r.send_headers();
            r.sendBytes(request.GIF);
         }
     }  else
     if(custom500.charAt(0)=='-') 
     {
         r.send_error(204,"No Content");
     }   else 
     {
         r.make_headers(301,null,null,custom500,0,new Date().getTime(),0,null);r.send_headers();
     }
     return;
 }

 try
 {
     r.run_request(wc);
 }
 catch (IOException e)
 {
     if(localname!=null && localname.equals(RESERVED)) localname=null;
     dir.dirty=true;
     if(mgr.loglevel>1) System.out.println("LOAD FAILED: "+dir.getLocalDir()+name+" ("+e+")");
     throw e;
 }
 r.send_headers();
 if(r.method==httpreq.REQUEST_HEAD) {
     ConnectionHandler.releaseConnection(wc);
     dir.dirty=true;
     return;
 }
 httprc=r.httprc;
 if(httprc==304) {  /* server said 304 on load */
                     if(localname!=null && localname.equals(RESERVED)) localname=null;
                     dir.dirty=true;
                     ConnectionHandler.releaseConnection(wc);
                     return;
                 }
 else if(httprc==204)
 {
     delete();
     ConnectionHandler.releaseConnection(wc);
 }

 File f=null;
 switch(httprc)
 {
        /* good RC */
        case 200:               /* OK */
        case 203:               /* Non-Authoritative Information */
        case 300:               /* Multiple Choices */
        case 301:               /* Moved Permanently */
        case 302:               /* Moved Temporary */
        case 307:               /* temporary redirect */
        case 410:               /* Gone */
        /* bad RC */
        case 400:               /* Bad request */
        case 403:               /* Forbidden */
        case 404:               /* not found */
        case 405:               /* method not allowed */
        case 414:               /* URI too large */
                 f=genTmp();
                 break;
        default:
 }
 if(!r.cacheable) f=null; /* non cacheable request */
 DataOutputStream fout=null;

 try{
 if(f!=null) fout=new DataOutputStream(
                       new BufferedOutputStream(new FileOutputStream(f),4096));
             else
               {  /* not caching */
               fout=null;
               if(localname!=null && localname.equals(RESERVED)) localname=null;
               dir.dirty=true;
               }
 }
 catch (IOException e)
           { /* tmp file create error */
             fout=null;
             if(localname!=null && localname.equals(RESERVED)) localname=null;
             dir.dirty=true;
             }

 try{
      r.transfer_object(wc,fout,dir);
 }
 catch (IOException e)
 {
   if(f!=null) f.delete();
   if(localname!=null && localname.equals(RESERVED)) localname=null;
   dir.dirty=true;
   if(mgr.loglevel>1) System.out.println("LOAD FAILED: "+dir.getLocalDir()+name+" ("+e+")");
   throw e;
 }
 finally
  {
    b_miss+=r.ctsize;
  }
 if(f==null)  /* not cached, end. */
        {
         if(localname!=null && localname.equals(RESERVED)) localname=null;
         dir.dirty=true;
         return;
        }
 /* regen localname if needed */
 setInfo(r);
 if(localname==null || localname.equals(RESERVED)) ln=genName(name);
   else
    ln=localname;
 File n=new File(dir.getLocalDir()+ln);
 if(!rename(f,n)) {
                    if(mgr.loglevel>0) System.out.println("**LOAD ERROR** Rename "+f+" to "+n+" failed.");
                    if(localname!=null && localname.equals(RESERVED))
                    {
                     localname=null;
                    }
                    dir.dirty=true;
                    return;
                  }
 /* update object status */
 localname=ln;
 if(mgr.loglevel>3) System.out.println("Loaded: "+n+" (size="+size+" B)");
 good=true;
 if(auto_compress>0) compress(9);
}

/* R E F R E S H ! */

final private void refresh_object(request r) throws IOException
{
 WebConnection wc;
 dir.dirty=true;
 try
 {
     wc=r.connectToHost();
 }
 catch (IOException eee) 
 { 
     /* check for forced reload error */
     if(r.reload && ui.loader_add_reloads>0)
     {
         /* redir to UI */
         r.make_headers(302,"text/html",null,"http://"+ui.ui_hostname+":"+ui.uiport+"/loader/"+(ui.loader_add_reloads==ui.ASK?"confirm/":"")+"refresh?url="+ui.escape(dir.URLbase+name),REDIRSTRLEN,99999999,199999999,null);
         r.headers.addElement("Pragma: no-cache");
         r.send_headers();
         r.sendString(REDIRSTR);
     } else
         send_fromcache(r);
     return;
 }

 long clims=r.ims;

 /* NOTE: nonzero clims == send object on 304 reply from server */
 if(clims==0) 
 {
     clims=1; 
     /* nema nic, to je to same jako kdyby mel hodne starou verzi */
     /* pokud by se nechalo na 0 - nedostal by OBJEKT pri 304! */
 }
 else
    if (clims>=lastmod) 
    {
        /* ma pozdejsi verzi objektu nez my, posuzujeme to jako kdyby */
        /* mel tu samou jako my, protoze IMS delame na nas objekt */
        clims=0; 
    }

 if(r.reload && force_ims_on_reloads==false && clims==1)         
 {
     ;
 } else
 {
    r.etag=etag; // use our ETag when validating URL
    r.add_ims(lastmod); // use our lastmod
 }

 try
 {
     r.run_request(wc);
 }
 catch (EOFException eof)
 {
   if(hide_errors==true)
       touch();
   throw eof;
 }
 catch (IOException e)
 {   
     r.ims=0;
     r.etag=null;
     r.chunked=false;
     send_fromcache(r); 
     return;
 }
     

 /* podivame se co prislo, pokud !=304, tak to posleme a nacachujeme */
 int rc=r.httprc;
 if(rc==304) {
               date=System.currentTimeMillis(); // update our last check date
               dir.dirty=true;
               ConnectionHandler.releaseConnection(wc);
               if(r.exp>0) expires=r.exp; // refresh possible Expires for Microsoft IIS 4.0
	       if(r.etag!=null) etag=r.etag;   // update Etag info
               if(mgr.loglevel>3) System.out.println("Checked: "+dir.getLocalDir()+localname);
               if(clims>0) { /* musime poslat objekt z cache */
                             r.ims=0;
                             r.etag=null;
                             r.chunked=false;
                             send_fromcache(r);
                             return;
               }
               r.make_headers(304,null,null,null,-1,0,expires,etag);
               r.send_headers();
               c_hit++;
               return;
             }
  else if(rc==204)
  {
      delete();
      ConnectionHandler.releaseConnection(wc);
      r.send_headers();
      return;
  }
  c_refresh++;

 if(httprc<400 && rc>=400) /* we have a GOOD object in cache, but got a wrong reply */
 {
 /* handle object deletion  */
 if(rc==404 || rc==410)
    if(keep_deleted==true) 
    {
      if(mgr.loglevel>1) System.out.println("Gone: "+dir.getLocalDir()+localname+" - sending cached copy");
      touch();
      wc.close();
      r.ims=0;
      r.etag=null;
      r.chunked=false;
      send_fromcache(r);
      return;
    }

 /* handle temporary remote server error */
 if(rc==400 || rc==403 || rc==408 /* timeout */ || rc==405 || rc==414 || rc>= 500)
 {
   if(hide_errors==true)
   {
      if(rc<500) touch();
      if(mgr.loglevel>1) System.out.println("Error "+rc+": "+dir.getLocalDir()+localname+" - sending cached copy.");
      wc.close();
      r.ims=0;
      r.etag=null;
      r.chunked=false;
      send_fromcache(r);
      return;
   }
   else /* hide errors == false , do not cache this ! */
   {
      if(mgr.loglevel>1) System.out.println("Error "+rc+": "+dir.getLocalDir()+localname);
      r.nocache();
   }
 }
 } /* bad rc and good obj in cache */
 // continue with "normal way"
 r.send_headers();

 if(r.method==httpreq.REQUEST_HEAD) {
     ConnectionHandler.releaseConnection(wc);
     dir.dirty=true;
     return;
 }

 /* musime to ulozit na disk! */
 File f=null;
 switch(rc)
 {
        /* good RC */
        case 200:               /* OK */
        case 203:               /* Non-Authoritative Information */
        case 300:               /* Multiple Choices */
        case 301:               /* Moved Permanently */
        case 302:               /* Moved Temporary */
        case 307:               /* temporary redirect */
        case 410:               /* Gone */
        /* bad RC */
        case 400:               /* Bad request */
        case 403:               /* Forbidden */
        case 404:               /* not found */
        case 405:               /* method not allowed */
        case 414:               /* URI too large */
                 f=genTmp();
                 break;
 }

 if(!r.cacheable) f=null; /* non cacheable request */
 DataOutputStream fout;

 try{
 if(f!=null) fout=new DataOutputStream(
                       new BufferedOutputStream(new FileOutputStream(f),4096));
             else
               fout=null;
 }
 catch (IOException e) {fout=null;}
 try{
 r.transfer_object(wc,fout,dir);
 }
 catch (IOException e)
 {
   if(f!=null) f.delete();
   if(mgr.loglevel>1) System.out.println("REFRESH FAILED: "+dir.getLocalDir()+localname+" ("+e+")");
   throw e;
  }
 finally
  {
    b_miss+=r.ctsize;
  }
 if(f==null) return;
 /* stalled entry? - regenerate object name */
 if(localname==null || localname.equals(RESERVED)) localname=genName(name);

 File n=new File(dir.getLocalDir()+localname);

 /* rename to new */
 if(!rename(f,n)) {
  if(mgr.loglevel>0) System.out.println("**REFRESH ERROR** Rename "+f+" to "+n+" failed.");
  dir.dirty=true;return;}
 /* update hlavicek */
 clims=(lastmod>0?lastmod:date);
 setInfo(r);
 long delta;
 if(lastmod>0) delta=(lastmod-clims);
   else
               delta=(date-clims);

 if(mgr.loglevel>3) System.out.println("Refreshed: "+n+" (delta="+printDelta(delta)+", size="+size+" B)");
 if(auto_compress>0) compress(9);
}

final public synchronized boolean rename(File from, File to)
{
 to.delete();
 boolean r=from.renameTo(to);
 if(!r && !to.exists()) localname=null;
 if(!r) from.delete();
 return r;
}

/*    S E N D    F R O M    C A C H E    */

final private void send_fromcache(request r) throws IOException
{
 dir.dirty=true;
 long clims;
 String et=r.etag;
 clims=r.ims;

 if(et!=null && et.equals(etag)) 
 {
     //System.out.println("[debug] Etag hit!");
     clims=date;
 }

 if(clims>0)
     /* possible IMS? */
     if(clims>=(lastmod==0?date:lastmod))
     {
        c_hit++;
        r.make_headers(304,null,null,null,-1,0,expires,etag);
        r.send_headers();
        return;
     }

 /* otevrit soubor */
 InputStream fin=null;
 /* dekomprimovat */
 boolean decomp=false;
 if(encoding!=null && auto_decompress>0 && ctype.startsWith("text/"))
  {
    if(r.encoding==null|| // client do not supports gziped data
       auto_decompress>1 // or always decompress 
       ) decomp=true;
  }
 try{
 fin=
    new BufferedInputStream(new FileInputStream(dir.getLocalDir()+localname),4096);
    if(decomp) 
    {
        if(encoding.equals("deflate"))
            fin=new java.util.zip.InflaterInputStream(fin,new java.util.zip.Inflater(true),8192);
        else
            fin=new java.util.zip.GZIPInputStream(fin,8192);
    }
 }
 catch (IOException e) { if(mgr.loglevel>1) 
                             System.out.println("Missing: "+dir.getLocalDir()+localname);
                         localname=null;
                         r.ims=0;
                         r.etag=null;
                         load_object(r);
                         return;
 }
 c_hit++;
 b_hit+=size;
 if(generate_lastmod)
 {
    if(decomp) r.make_headers(httprc,ctype,null,location,-1,lastmod==0? date:lastmod,expires,etag);
     else
    r.make_headers(httprc,ctype,encoding,location,size,lastmod==0? date:lastmod,expires,etag);
 }
   else
 {
    if(decomp)
            r.make_headers(httprc,ctype,null,location,-1,lastmod,expires,etag);
    else
            r.make_headers(httprc,ctype,encoding,location,size,lastmod,expires,etag);
 }
 r.send_headers();
 
 if(r.method==httpreq.REQUEST_GET)
 {
    /* create a fake WebConnection for sending disk cache */
    WebConnection wc=new WebConnection(new DataInputStream(fin));
    r.ctsize=-1; // send everything
    r.transfer_object(wc,null,dir);
 } else
     fin.close();
}

final private void setInfo(request r)
{
 httprc=r.httprc;
 ctype=r.ctype;
 encoding=r.encoding;
 location=r.location;
 size=r.ctsize;
 lastmod=r.ims;
 expires=r.exp;
 etag=r.etag;

 date=System.currentTimeMillis();
 dir.dirty=true;
}

/* vraci true pokud by se mela stranka obnovit */
final private boolean needRefresh(request r,long reload_age,long min_age, long max_age, float percent,long expire_age,long redir_age)
{
 long now=System.currentTimeMillis();
 long age=now-date;

 /* - test reload status */
 if(r.reload)
     if(age>=reload_age)
     {
       if(trace_refresh==true)
          System.out.println("[TRACE "+Thread.currentThread().getName()+"] ? Refresh forced by user "+printDelta(age)+">"+printDelta(reload_age)+" "+r.getURL());
       return true;
     } else
         if(trace_refresh==true)
             System.out.println("[TRACE "+Thread.currentThread().getName()+"] ? Forced refresh ignored "+printDelta(age)+"<"+printDelta(reload_age)+" "+r.getURL());

 /* test na max age */
 if(age>max_age)
       {
         if(trace_refresh==true)
           System.out.println("[TRACE "+Thread.currentThread().getName()+"] ? Max. stale time exceed "+printDelta(age)+">"+printDelta(max_age)+" "+r.getURL());
         return true; /* puvodne to bylo az za expires ... */
       }

 /* test na expiraci */
 if(expires!=0)
 {
    if(expires<=now)
    {
       if(age>=expire_age)
       {
         if(trace_refresh==true)
           System.out.println("[TRACE "+Thread.currentThread().getName()+"] ? Expire granted "+printDelta(age)+">"+printDelta(expire_age)+" "+r.getURL());
         return true;
       }
       else
       {
         if(trace_refresh==true)
           System.out.println("[TRACE "+Thread.currentThread().getName()+"] ? Expire not yet granted "+printDelta(age)+"<"+printDelta(expire_age)+" "+r.getURL());
         return false;
       }
    }
    else
    {
       if(trace_refresh==true)
           System.out.println("[TRACE "+Thread.currentThread().getName()+"] ? Expire date "+request.printDate(new Date(expires))+" not reached: "+r.getURL());
       return false;
    }
 }

 /* redirection rest */
 if(location!=null)
   if(age>=redir_age) 
   {
      if(trace_refresh)
        System.out.println("[TRACE "+Thread.currentThread().getName()+"] ? Max. redirect time exceed "+printDelta(age)+">"+printDelta(redir_age)+" "+r.getURL());
      return true;
   }
   else 
   {
     if(trace_refresh) 
       System.out.println("[TRACE "+Thread.currentThread().getName()+"] ? Redirect refresh time not reached "+printDelta(age)+"<"+printDelta(redir_age)+" "+r.getURL());
     return false;
   }

 /* min age test */
 if(age<=min_age)
 {
    if(trace_refresh)
      System.out.println("[TRACE "+Thread.currentThread().getName()+"] ? Min. stale time not reached "+printDelta(age)+"<"+printDelta(min_age)+" "+r.getURL());
    return false;
 }
                
 if(lastmod==0) 
 {
    if(trace_refresh)
      System.out.println("[TRACE "+Thread.currentThread().getName()+"] ? Refreshing not checkable "+printDelta(age)+">"+printDelta(min_age)+" "+r.getURL());
    return true; /* usetrime si nejake to pocitani */
 }

 /* lastmod check */
 long lm_age=date - lastmod;
 float lmfactor=(float) age / (float)lm_age;

 if(lmfactor<percent && lmfactor>0)
 { 
    if(trace_refresh)
      System.out.println("[TRACE "+Thread.currentThread().getName()+"] ? Lastmod factor not reached "+lmfactor+"<"+percent+" "+r.getURL());
    return false;
 }
 else
 {
    if(trace_refresh)
      System.out.println("[TRACE "+Thread.currentThread().getName()+"] ? Lastmod factor reached "+lmfactor+">"+percent+" "+r.getURL());
    return true; /* refresh it! */
 }
}

final public String toString()
{
 return dir.getLocalDir()+name;
}

final public cachedir getDirectory()
{
 return dir;
}

final public void setDirectory(cachedir newdir)
{
 if(dir==null) throw new IllegalArgumentException("Directory must not be null");
 dir=newdir;
}

final public String getName()
{
 return name;
}

final public String getType()
{
 return ctype;
}

final public long getLRU()
{
 return lru;
}

final public int getRC()
{
 return httprc;
}

final public int getSize()
{
 return size;
}

final public long getDate()
{
 return date;
}

final public long getExp()
{
 return expires;
}


final void touch()
{
               lru=date=System.currentTimeMillis();
               dir.dirty=true;
}

final void touchLRU()
{
               lru=System.currentTimeMillis();
               dir.dirty=true;
}

final public boolean isCheckable()
{
 if(lastmod==0 && etag==null) return false; else return true;
}

final public boolean isRedirect()
{
    if(location!=null) return true; else return false;
}

final public static String printDelta(long delta)
{
 long mdelta=delta/60000L; // delta in minutes

 if(mdelta>60*24*7*4*12*2) return (mdelta/60/24/365)+"Y";
   else
 if(mdelta>60*24*7*12) return (mdelta/60/24/30)+"M";
   else
 if(mdelta>60*24*5*7) return (mdelta/60/24/7)+"w";
   else
 if(mdelta>60*72) return (mdelta/60/24)+"d";
   else
 if(mdelta>200) return (mdelta/60)+"h";
   else
 if(delta<60000) return (delta/1000)+"s";
   else
 return mdelta+"m";
}

} /* class */
