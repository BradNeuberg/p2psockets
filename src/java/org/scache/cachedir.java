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

public final class cachedir{


public static final String DIRINFO=".cacheinfo";
public static boolean readonly;
static mgr parent;

/* private data */
private String localdir;
private Hashtable objects;
public String URLbase;
public boolean dirty;

cachedir(String locdir,String baseurl)
{
 objects=new Hashtable();
 localdir=locdir;
 URLbase=baseurl;
 File f=new File(locdir);

 /* nejdrive se vytvorit adresar */
 if(!readonly) f.mkdirs();
 
 /* pokud neexistuje nebo to neni adresar, tak mame problem */
 if(!f.exists() || f.isFile())
 {
		   if(readonly) {localdir=null;return;}
		   /* likvidujeme up-dirs, mozna je to konflikt se souborem */
		   File thisdir=new File(f.getParent());
		   while(true)
		   {
		      String par;
		      if(moveOutOfWay(thisdir)) break;
		      par=thisdir.getParent();
		      if(par==null) break;
		      thisdir=new File(par);
		   }
		   // druhy a posledni pokus o vytvoreni adresare
		   f.mkdirs();
		   if(!f.exists())
		   {
		       System.out.println("Cannot create directory "+localdir+" - turning caching off for it.");
		       localdir=null;
		       return;
		   }
 }

 dirty=false;
 /* nacteme cachovane objekty */

 DataInputStream is=null;
 try{
 is=new DataInputStream(
                 new BufferedInputStream(
                 new FileInputStream( localdir+DIRINFO ),4096));

 int howmany=is.readInt();
 byte version=2;
 if(howmany==0x53433033)
  {
   version=3;
   howmany=is.readInt();
  }
 else if(howmany==0x53433034)
 {
   version=4;
   howmany=is.readInt();
 }
     
 for(int i=0;i<howmany;i++)
  {
   cacheobject o;
   o=new cacheobject(is,this,version);
   objects.put(o.getName(),o);
  }
 is.close();
 }
 catch(IOException e) {
                       try{
                         if(is!=null) { System.out.println("ERROR reading "+localdir+DIRINFO+", file corrupted?");
                                        is.close();
                                      }
                          }
                        catch (IOException z) {}
                       };
} /* konstruktor */

final public synchronized void save()
{
 if(localdir==null || readonly==true) { dirty=false;return;}
 if(!dirty) return;

 dirty=false;

 /* smazat ty, ktere neukladame */
 Enumeration en=objects.elements();
 while(en.hasMoreElements())
 {
   cacheobject o=(cacheobject)en.nextElement();
   if(!o.needSave())
                      objects.remove(o.getName());
 }
 int howmany;
 howmany=objects.size();
 if(howmany==0) {
                  /* nemusime nic ukladat */
                  return;
                 }

  try{
 DataOutputStream os=new DataOutputStream(
                     new BufferedOutputStream(
                     new FileOutputStream( localdir+DIRINFO),4096 ));

 os.writeInt(0x53433034); // VERSION 4
 os.writeInt(howmany);
 en=objects.elements();
 while(en.hasMoreElements())
  {
   cacheobject o=(cacheobject)en.nextElement();
   if(o.needSave())
                o.save(os);
  }
 os.flush();
 os.close();
 }
 catch (FileNotFoundException e)
    /* nekdo nam smazal hot dir, ze by BFU nebo GC ? */
   {
     if(new File(localdir).exists())
      { /* .cacheinfo no perm for write */
       System.out.println("No write access to "+localdir+DIRINFO);
       return;
      }
     System.out.println("Lost Directory "+localdir);
     localdir=null;
     objects=new Hashtable();
     return;
    }
 catch (IOException e)
   {
     System.out.println("**DIRECTORY SAVE ERROR** "+localdir+"  ("+e+")");
   }
}

/* returns number of deleted directories */
final int cleandir()
{
   if(localdir==null) return 0;
   if(objects.size()!=0) return 0; /* safe-check */

   int rc;
   rc=1;

   File thisdir=new File(localdir.substring(0,localdir.length()-1));
   localdir=null;
   String filez[]=thisdir.list();
   if(filez==null) return 1;

   /* delete any files found there */
   for(int i=0;i<filez.length;i++)
   {
     if(! ( (new File(thisdir,filez[i])).delete()) ) 
     {
	 rc=0;
     }
   }

   if(rc==0)
   {
     return 0;
   }

   rc=0;

   /* likvidujeme up-dirs*/
   while(thisdir.delete())
   {
     rc++;
     // System.out.println("/debug/ Deleted dir "+thisdir+" rc="+rc);
     String par;
     par=thisdir.getParent();
     if(par==null) break;
     thisdir=new File(par);
   }

   return rc;
}

final public int countObjects()
{
 return objects.size();
}

final public synchronized cacheobject getObject(String name)
{
 cacheobject obj;
 obj=(cacheobject) objects.get(name);
 if(obj!=null) return obj;
 obj=new cacheobject(name,this);
 objects.put(name,obj);
 return obj;
}

final synchronized void putObject(cacheobject co)
{
 dirty=true;
 objects.put(co.getName(),co);
}

final public boolean equals(Object o)
{
 if(o==null || ! (o instanceof cachedir)) return false;
 cachedir o1=(cachedir)o;

 return localdir.equals(o1.getLocalDir());
}

final public String getLocalDir()
{
 return localdir;
}

final public String toString()
{
 return localdir;
}

final public int hashCode()
{
 if(localdir==null) return 0;
 return localdir.hashCode();
}
final public void compressdir()
{
 // System.out.println("Compressing directory: "+localdir);
 Enumeration en=objects.elements();
 while(en.hasMoreElements())
  {
   cacheobject o;
   o=(cacheobject)en.nextElement();
   o.compress(9);
  }
}

final public String[] listLocalNames()
{
 String res[]=new String[objects.size()];
 int i=0;
 Enumeration en=objects.elements();
 while(en.hasMoreElements())
  {
   cacheobject o=(cacheobject)en.nextElement();
   res[i++]=o.getLocalName();
  }
  return res;
}

/* zjisti aktualnost objektu  */
/* smaze neexistujici objekty */
final public boolean checkDir()
{
 Enumeration en=objects.elements();
 while(en.hasMoreElements())
  {
   cacheobject o=(cacheobject)en.nextElement();
   if(!o.isValid()) { objects.remove(o.getName());
                      dirty=true;
                      if(garbage.gcloglevel>2) System.out.println(" - Bad .cacheinfo data for "+localdir+o.getLocalName());
                      }
  }
  return !dirty;
}

/* smaze referenci na objekt */
final public void remove(cacheobject o)
{
 objects.remove(o.getName());
 dirty=true;
}

final public Enumeration getObjects()
{
 return objects.elements();
}

final public void export_to(cachedir nd,int type,long difftime)
{
 if(nd==null) return;
 if(localdir==null) return;
 long now=System.currentTimeMillis();
 Enumeration e=getObjects();
 while(e.hasMoreElements())
 {
  cacheobject my,out;
  boolean exp;
  exp=false;
  my=(cacheobject)e.nextElement();
  switch(type)
  {
  case garbage.EXPORT_ALL:
		     exp=true;
		     break;
  case garbage.EXPORT_LRU:		
                     if (now-my.getLRU()<=difftime) { exp=true;}
		     break;
  case garbage.EXPORT_DATE:
                     if(now-my.getDate()<=difftime) { exp=true;}
		     break;
  case garbage.EXPORT_FILEDATE:
                     if(now-my.getDate()>difftime) break;
		     String ln;
		     ln=my.getLocalName();
		     if(ln==null || ln.equals(cacheobject.RESERVED)) break;
		     if(now-new File(localdir+ln).lastModified()<=difftime) exp=true;
		     break;


  } /* switch */
  if(exp==false) continue;
  if(!my.isValid())
  {
     System.out.println(" - "+localdir+my.getLocalName());
     continue;
  }
  /* found valid object for export */
  out=nd.getObject(my.getName());
  out.delete(); /* delete old object */
  File exp1,f2;
  exp1=new File(localdir,my.getLocalName());

  my.setDirectory(nd); // needed for localname generation
  // generovat nove jmeno
  my.regenName();
  f2=new File(nd.getLocalDir(),my.getLocalName());

  if(copyfile(exp1,f2)==false) { continue;}
  // musime udelat touch na date
  my.touch();
  nd.putObject(my);
 } /* elements */
}

/*
  merge current directory with data in new, file will be moved/copied
*/

final public void merge(cachedir nd)
{
 if(nd==null) return;
 if(localdir==null) return;
 // nd.checkDir();  //already done by garbage.import0
 Enumeration e1=nd.getObjects();
 while(e1.hasMoreElements())
 {
  cacheobject o,o2;
  /*  o - novy objekt */
  /*  o2 - stary objekt v cache */
  o=(cacheobject)e1.nextElement();
  if(o.getLocalName()==null) continue; // transient object
  o2=(cacheobject)objects.get(o.getName());
  // System.out.println("OBJ="+o.getName()+" O2="+o2);
  if(o2!=null)
   {
    if(!o2.isValid())
                {
		  System.out.println(" - "+localdir+o2.getLocalName());
		  o2.delete(); // KILL our BAD object
		  o2=null;
		}
      else
        if(o2.getDate()>o.getDate()) continue; /* our VALID object is newer */
   }
   // import object o into our directory

   // step1 - prepare f and f2
   /* f - soubor k importu, f2 - importovat do... */
   File f,f2;
   f=new File(o.getDirectory().getLocalDir(),o.getLocalName());
   o.setDirectory(this); // needed for localname generation
   if(o2!=null)
   {
      o2.delete(); // delete old object
   }
   // generovat nove jmeno
   o.regenName();
   f2=new File(localdir,o.getLocalName());

  // System.out.println("f2="+f2);
  // jmeno vygenerovano

  //step2 - presun souboru pod nove jmeno
  if(!f.renameTo(f2))
   {
            /* rename selhalo, kopiruje se */
            if(copyfile(f,f2)==false) { continue;}
	    // musime udelat touch na date
	    o.touch();
  }

  // touch imported OBJ's LRU
  o.touchLRU();

  //step3 - vymenit objekt v adresari
  objects.put(o.getName(),o);
  if(cacheobject.auto_compress>0) o.compress(9);

  dirty=true;
 }
} /* merge */

private final static boolean copyfile(File f,File f2)
{
  System.out.println("[INFO] Copying "+f+" -> "+f2);
  try
  {
    DataInputStream is=new DataInputStream(new BufferedInputStream(new FileInputStream(f),4096));
    DataOutputStream os=new DataOutputStream(new BufferedOutputStream(
    new FileOutputStream(f2),4096));

    byte b[]=new byte[4096];
    while(true)
    {
     int rb;
     rb=is.read(b);
     if(rb==-1) break; /* konec dat! */
     os.write(b,0,rb);
    }
    os.close();
    is.close();
  }
  catch (IOException ioe)
   {
     System.out.println("[ERROR] Failed copy "+f+" -> "+f2);
     f2.delete(); // try to delete new file...
     return false;
   }
   return true;
}

/* 
 * odklidi soubor pryc aby se mohl vytvorit adresar,
 * - prejmenuje ho a updatne .cacheinfo 
 */
private final static boolean moveOutOfWay(File what)
{
    if(!what.exists()) return false;
    if(what.isDirectory()) return false; // no way to rename directory
    /* 1. ziskat jeho adresar */
    String home,urlp;
    home=what.getParent()+File.separatorChar;
    urlp=ui.directoryToURL(home);
    // System.out.println("/debug/ moveout file= "+what+" in dir="+home+" uribase="+urlp);
    cachedir d=httpreq.mgr.getDir(home,urlp);
    cacheobject o;
    /* 2. projit objekty a najit ten co nam tam vadi */
    Enumeration en=d.getObjects();
    while(en.hasMoreElements())
    {
	o=(cacheobject)en.nextElement();
	if(!o.needSave()) continue;
	if(o.getLocalName().equalsIgnoreCase(what.getName()))
	{
	    o.regenName();
	    File to=new File(home+o.getLocalName());
	    if(!o.rename(what,to)) o.delete();
	}
    }
    // delete if still exists
    what.delete();
    return true;
}
} /* class */
