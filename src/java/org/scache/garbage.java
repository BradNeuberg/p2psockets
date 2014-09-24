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

public final class garbage
{
public static final String GCVERSION="0.23";
public static final float REFAGE_MAX=999999; /* nic se nemaze pokud je >= */

public static final int EXPORT_LRU=1;
public static final int EXPORT_DATE=2;
public static final int EXPORT_FILEDATE=3;
public static final int EXPORT_ALL=4;

public static boolean realdirsize;
public static int gcloglevel=3;
/* 0 - Off */
/* 1 - basic progress */
/* 2 - stats */
/* 3 - cache consistency errors */
/* 4 - deleted files list */
/* 5 - debug info */

private long cachesize; /* maximum size of cache */
private int dirsize;    /* average dirsize */
private long oursize;   /* current cache size */
private int ourdirs;    /* directory count in cache */
private int ourfiles;   /* files count in cache */
public  int scandel;    /* files deleted during killunref or GC scan */

private int lowmark,highmark;
private int maxsize,minsize;
private float refage; /* marks for fast file deletion */
private int blocksize;

/* GC array */
private cacheobject gcarray[];
private float gcvalues[];

private int gcused;
private float gclow;

private long now;

private int lopsize,lopsizeinc;
private String lopact1,lopact2;

private int sopsize,sopsizedec;
private String sopact1,sopact2;

private String exppenal, notchpenal, ebc, redirpenal;
private String neg; /* negative cached */

private regexp urlmask[];
private String urlact[];

String root;

garbage(String root)
{
 this.root=root;
 /* init to defaults */
 cachesize=100*1024*1024; /* 100MB Cache */
 maxsize=5*1024*1024; // 5MB
 minsize=0;
 lowmark=90;
 highmark=95;
 refage=365;
 blocksize=4096;
 dirsize=4096;
 lopsize=1024*1024*20;
 lopsizeinc=1024*1024*20;
 sopsize=0;
 gcused=0;
 gclow=0;
 gcarray=new cacheobject[3000];
 gcvalues=new float[3000];
 exppenal="=1";
 ebc="*2";
 neg="*10";
 mgr.case_sensitive=true;
 gcloglevel=4;
 /* */
 read_config(scache.GCFGFILE);
 urlmask=fixup_urls(urlmask);
 /* reverze it! */
 urlmask=mgr.reverseRegexpArray(urlmask);
 urlact=mgr.reverseStringArray(urlact);
 now=System.currentTimeMillis();
 if(realdirsize) dirsize=0;
 optimise();
 if(gcloglevel>0) System.out.println(new Date()+" Garbage collector version "+GCVERSION+" initialized.");
}

/*  vycisti prazdne modifikatory */
private final void optimise()
{
  if(dummyMod(lopact1)) lopact1=null;
  if(dummyMod(lopact2)) lopact2=null;
  if(lopact1==null) lopsize=Integer.MAX_VALUE;
  if(lopact2==null) lopsizeinc=Integer.MAX_VALUE;
  
  if(dummyMod(sopact1)) sopact1=null;
  if(dummyMod(sopact2)) sopact2=null;
  if(sopact1==null) sopsize=0;
  if(sopact2==null) sopsizedec=0;
  
  if(dummyMod(exppenal)) exppenal=null;
  if(dummyMod(notchpenal)) notchpenal=null;
  if(dummyMod(ebc)) ebc=null;
  if(dummyMod(neg)) neg=null;
  if(dummyMod(redirpenal)) redirpenal=null;
}

private final void read_config(String cfgfile)
{
 try{
 String line,token;
 StringTokenizer st;
 int lineno=0;
 if(! new File(cfgfile).isAbsolute()) cfgfile=scache.cfgdir+File.separator+cfgfile;

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

          if(token.equalsIgnoreCase("cache_swap_size")) {cachesize=sizestring(st.nextToken("\r\n"));
          					    continue;
                                                    }

          if(token.equalsIgnoreCase("average_directory_size")) {dirsize=(int)sizestring(st.nextToken("\r\n"));
          					    continue;
                                                    }

          if(token.equalsIgnoreCase("cache_swap_low")) {lowmark=Integer.valueOf(st.nextToken()).intValue();
          					    continue;
                                                    }

          if(token.equalsIgnoreCase("cache_swap_high")) {highmark=Integer.valueOf(st.nextToken()).intValue();
          					    continue;
                                                    }
          if(token.equalsIgnoreCase("block_size")) {blocksize=(int)sizestring(st.nextToken("\r\n"));
          					    continue;
                                                    }

          if(token.equalsIgnoreCase("gcarraysize")) {gcarray=new cacheobject[Integer.valueOf(st.nextToken()).intValue()];
                                                     gcvalues=new float[gcarray.length];
          					    continue;
                                                    }

          if(token.equalsIgnoreCase("maximum_object_size")) {
                                                    maxsize=(int)sizestring(st.nextToken("\n\r"));
                                                    if(maxsize<=0) maxsize=Integer.MAX_VALUE;
          					    continue;
                                                    }
          if(token.equalsIgnoreCase("minimum_object_size")) {
                                                    minsize=(int)sizestring(st.nextToken("\r\n"));
                                                    if(minsize<0) minsize=0;
          					    continue;
                                                    }
          if(token.equalsIgnoreCase("reference_age")) {
	                                            refage=timestring(st.nextToken("\r\n"))/86400000.0f;
                                                    if(refage<=0 || refage>=REFAGE_MAX) refage=REFAGE_MAX-1;
          					    continue;
                                                    }
          if(token.equalsIgnoreCase("gcloglevel")) {
                                                    gcloglevel=Integer.valueOf(st.nextToken()).intValue();

          					    continue;
                                                    }

          if(token.equalsIgnoreCase("large_object_penalty")) {
                                                    lopsize=(int)sizestring(st.nextToken());
                                                    lopact1=st.nextToken();
                                                    lopsizeinc=(int)sizestring(st.nextToken());
                                                    lopact2=st.nextToken();
          					    continue;
                                                    }
          if(token.equalsIgnoreCase("small_object_penalty")) {
                                                    sopsize=(int)sizestring(st.nextToken());
                                                    sopact1=st.nextToken();
                                                    sopsizedec=(int)sizestring(st.nextToken());
                                                    sopact2=st.nextToken();
						    
          					    continue;
                                                    }

          if(token.equalsIgnoreCase("expired_penalty")) {
                                                    exppenal=st.nextToken();
          					    continue;
                                                    }

          if(token.equalsIgnoreCase("expired_but_checkable_penalty")) {
                                                    ebc=st.nextToken();
          					    continue;
                                                    }

          if(token.equalsIgnoreCase("not_checkable_penalty")) {
                                                    notchpenal=st.nextToken();
          					    continue;
                                                    }
	  
          if(token.equalsIgnoreCase("redirect_penalty")) {
                                                    redirpenal=st.nextToken();
          					    continue;
                                                    }

          if(token.equalsIgnoreCase("negative_cached_penalty")) {
                                                    neg=st.nextToken();
          					    continue;
                                                    }
          if(token.equals("case_sensitive_matching")) 
	  {
              mgr.case_sensitive=mgr.getYesNo(st.nextToken());
              continue;
          }

          if(token.equalsIgnoreCase("urlmask")) {
          					 token=st.nextToken();
                                                 if(mgr.isInRegexpArray(token,urlmask)>-1) continue;
                                                 urlmask=mgr.addRegexpToArray(token,urlmask,false);
                                                 urlact=mgr.addStringToArray(st.nextToken(),urlact);
          					 continue;
                                                 }
          }
          catch (NoSuchElementException z)
           {
          System.out.println("[GC_CONFIG_ERROR] Missing argument(s) in "+cfgfile+":"+lineno);
          continue;
           }

          catch (NumberFormatException z)
           {
          System.out.println("[GC_CONFIG_ERROR] Invalid number in "+cfgfile+":"+lineno);
          continue;
           }

          System.out.println("[GC_CONFIG_ERROR] Unknown keyword "+token+" in "+cfgfile+":"+lineno);
 }/* while */
 dis.close();
 }
 catch(IOException e) {
     System.out.println(new Date()+" [ERROR] reading GC configuration.");
 }
 /* make sure that values are somewhat sane */
 if(dirsize<1024) dirsize=1024;
 if(dirsize<blocksize) dirsize=blocksize;

 if(highmark<=0) highmark=95;
 if(highmark>100)  highmark=100;
 if(lowmark>highmark  || lowmark<=0) lowmark=highmark-5;

}

private final regexp[] fixup_urls(regexp what[])
{
 if(what==null) return null;
 for(int i=0;i<what.length;i++)
  {
      regexp r;
      r=new regexp( encodechars(what[i].toString()),false);
      what[i]=r;
  }
  return what;
}

public final void rebalance()
{
 if(cachedir.readonly==true)
 {
	 System.out.println(new Date()+" Cache directory is in READ-ONLY mode, operation Aborted.");
	 return;
 }
 rebalance0(root);
}

public final void dirimport(String improot)
{
 import0(improot,improot);
}

public final void export(String to,int type,long difftime)
{
 if(to.endsWith(File.separator)) to=to.substring(0,to.length()-1);
 export_one_dir(root,to,type,difftime);
}

public final void cacheimport(String improot)
{
 importcache0(improot);
}

public final void cleanup()
{
 if(cachedir.readonly==true)
 {
	 System.out.println(new Date()+" Cache directory is in READ-ONLY mode, operation Aborted.");
	 return;
 }
 kill_unref_dir(root,true);
}


private final void dump_gcarray()
{
 System.out.println("\n"+new Date()+" Dumping garbage collection memory array:\nComputed LRU value in days\t\tURL\n");
 for(int i=gcused;i>=0;i--)
  {
   if(gcarray[i]==null) continue;
   System.out.println(gcvalues[i]+"  "+gcarray[i].getDirectory().getLocalDir()+gcarray[i].getName());
  }
 System.out.println("\n"+new Date()+"   ... End of dump.");
}

private final void import0(String improot,String dirname)
{
 File myroot=new File(dirname);
 if(!myroot.isDirectory()) return;

 if(dirname.length()>improot.length())
 {
   String urlpart;
   String hp;
   urlpart=dirname.substring(improot.length()+1);
   // roseknuti na host cast adresare a URL cast
   if(urlpart.indexOf(File.separator)==-1)
   {
     hp=urlpart;
     urlpart="";
   }
   else
   {
     hp=urlpart.substring(0,urlpart.indexOf(File.separator));
     urlpart=urlpart.substring(hp.length());
   }
   // extract hostname from filename
   String hn;
   hn=hp.toLowerCase();
   if(hn.indexOf('_')>-1)
      hn=hn.substring(0,hn.indexOf('_'));
   if(hn.indexOf('^')>-1)
      hn=hn.substring(0,hn.indexOf('^'));

   if(hn.indexOf('.')==-1)
    {
      System.out.println(new Date()+" [IMPORT] ignoring host without dot in name: "+hn);
      return;
     }
      // spocitat crc32 hash
      StringBuffer result;
      result=new StringBuffer(80);
      result.append(root);
      int ii;

      /* 1. spocitat hash z host stringu */
      java.util.zip.CRC32 crc=new CRC32();
      crc.update(hn.getBytes());
      /* mame hash - rozdelime ho na adresar */
      long unit=((long)1<<32)/(mgr.swap_level1_dirs*mgr.swap_level2_dirs);
      ii=(int)(crc.getValue()/unit);
      /* zacneme budovat cestu */
      result.append(File.separator+(ii/mgr.swap_level2_dirs)+File.separator+(ii-mgr.swap_level1_dirs*(ii/mgr.swap_level2_dirs))+File.separator+hp+urlpart+File.separator);

   cachedir imp,my;
   /* create .cacheinfo in to-be-imported directory */
   imp=repair.repairDir(dirname,false);
   my=new cachedir(result.toString(),null);
   // System.out.println("hp="+hp+"("+hn+") urlpart="+urlpart+" mydir="+result);
   my.merge(imp);
   my.save();
 }

 // into subdirz
 String filez[];
 filez=myroot.list();
 if(filez==null) return;
 for(int i=filez.length-1;i>=0;i--)
  if(new File(dirname+File.separator+filez[i]).isDirectory()) import0(improot,dirname+File.separator+filez[i]);

}

private final void rebalance0(String dirname)
{
 File myroot=new File(dirname);
 boolean goodlevel=false;

 if(!myroot.isDirectory()) return;

 /* zjistit, zda jsme jiz na spravne urovni t.j. */
 /* root=d:\store
    my           \x\y */

 if(dirname.length()-this.root.length()>3)
 {
  // je uz to mozne.... ?
  String nd=dirname.substring(this.root.length()+1);
  int i=nd.indexOf(File.separatorChar,0);
  if(i!=-1) {
     // System.out.println("[debug] Good level="+dirname);
     goodlevel=true;
            }
 }
  /* rekurzivne do adresaru */
  String dirfilez[]=myroot.list();
  
  for(int i=0;i<dirfilez.length;i++)
   {
     if(!new File(myroot,dirfilez[i]).isDirectory()) continue;
     if(goodlevel==true)
     {
      // extract hostname from filename
      String hn;
      hn=dirfilez[i];
      if(hn.indexOf('_')>-1)
        hn=hn.substring(0,hn.indexOf('_'));
	
      if(hn.indexOf('^')>-1)
        hn=hn.substring(0,hn.indexOf('^'));
      hn=hn.toLowerCase();
      // System.out.println("fn="+dirname+File.separator+dirfilez[i]+" hn="+hn);

      // spocitat crc32 hash
      StringBuffer result=new StringBuffer(90);
      result.append(root);

      /* 1. spocitat hash z host stringu */
      java.util.zip.CRC32 crc=new CRC32();
      crc.update(hn.getBytes());
      /* mame hash - rozdelime ho na adresar */
      int ii;
      ii=(int)(crc.getValue()/(0x100000000L/(mgr.swap_level1_dirs*mgr.swap_level2_dirs)));
      result.append(File.separator+(ii/mgr.swap_level2_dirs)+File.separator+(ii % mgr.swap_level2_dirs)+File.separator+dirfilez[i]);
      // System.out.println("crc="+crc.getValue()+" units="+ii+" np="+result);
      if(!result.toString().equals(dirname+File.separator+dirfilez[i]))
       {
          File f1,f2;
	  f1=new File(myroot,dirfilez[i]); // old
	  f2=new File(result.toString());
          new File(f2.getParent()).mkdirs();
	  if(!f1.renameTo(f2))
	    System.out.println("Rename "+f1+" to "+f2+" failed.");

       }

     } else /* goodleve==false */
         rebalance0(dirname+File.separator+(String)dirfilez[i]);
   }
   myroot.delete();
}

/* ************************************* */
/* ALERT: Synchronize by hand with mgr!! */
/* ************************************* */

private final String encodechars(String urldir)
{
  // System.out.println("Encode in="+urldir);
  /* get protocol if any */
  int i=urldir.indexOf("://",0);
  if(i!=-1)
   {
    String proto=urldir.substring(0,i);
    urldir=urldir.substring(i+3); //cut of protocol
    /* odstranit http:// if any */
    if(!proto.equalsIgnoreCase("http"))
     {
      /* ftp://ftp.debian.org/* -> ftp.debian.org^ftp/* */
      /* ftp://ftp.lamer* -> ftp.lamer*^ftp/*           */
      /* *://www.debian.org/* -> www.debian.org(*)/     */

      i=urldir.indexOf('/',0);
      if(proto.equals("*"))
      {
	  urldir=urldir.substring(0,i)+"*"+urldir.substring(i);
      } else
      {
	  if(i==-1)  urldir+='^'+proto+"/*";
	    else
	  urldir=urldir.substring(0,i)+'^'+proto+urldir.substring(i);
      }
     }
   }
  // if(urldir.startsWith("http://")) urldir=urldir.substring(7);

  /* nahrazujeme nepratelske znaky */
  urldir=urldir.replace(':','_');
  // urldir=urldir.replace('*','@');
  // urldir=urldir.replace('?','#');
  // urldir=urldir.replace('~','-');
  if(cacheobject.end_dot==false)
  {
    urldir=urldir.replace('>','}');
    urldir=urldir.replace('<','{');
    urldir=urldir.replace('|','!');
  }
  
  if(File.separatorChar!='/') urldir=urldir.replace('/',File.separatorChar);
  // System.out.println("encode out debug="+urldir);
  return urldir;
}

public final void garbage_collection()
{
 long bot=(long)( (float)cachesize*(float)lowmark/100f);
 long high=(long)( (float)cachesize*(float)highmark/100f);
 if(cachedir.readonly==true)
 {
	 System.out.println(new Date()+" Cache directory is in READ-ONLY mode, GC Aborted.\n"+new Date()+"\tYou can use -fakegc instead");
	 return;
 }
 
 if(gcloglevel>1) print_gc_intro();
 oursize=high+1;

 while(oursize>high)
 {
    oursize=0;
    ourfiles=0;
    ourdirs=0;
    gcarray=new cacheobject[gcarray.length];
    gcvalues=new float[gcvalues.length];
    gclow=0;
    gcused=0;
    scandel=0;
    if(gcloglevel>0) System.out.println(new Date()+" Scanning cache directory. This takes some time...");
    scan_cache(root);
    if(scandel>0 && gcloglevel>0) System.out.println(new Date()+" "+scandel+" files deleted during scan.");
    if(gcloglevel>1) print_gc_outro();
    delete_files();
 }
}

private final void print_gc_outro()
{
    long high=(long)( (float)cachesize*(float)highmark/100f);
    System.out.println(new Date()+" CACHE SCAN SUMMARY:");
    System.out.println(new Date()+"      High watermark: "+high+" B ("+highmark+"%)");
    System.out.println(new Date()+"      Disk storage: "+oursize+
    " Bytes used.");
    if(cachesize>0)
       System.out.println(new Date()+"      Cache is "+(oursize*1000L/cachesize)/10.0+" percent full.");
    System.out.println(new Date()+"         ("+ourfiles+" files and "+ourdirs+" dirs.)");
    if(ourfiles>0) 
	System.out.println(new Date()+"          avg. file size: "+oursize/ourfiles+" Bytes.");
    if(ourdirs>0) 
	System.out.println(new Date()+"          avg. directory: "+oursize/ourdirs+" B in "+ourfiles/ourdirs+" files.");
    System.out.println(new Date()+"      "+(gcused+1)+" of "+gcvalues.length+" gc slots used.");
    System.out.println(new Date()+"      Time marks: low="+gclow+" middle="+new Float(gcvalues[gcused/2])+" high="+
    new Float(gcvalues[gcused]));
}

private final void print_gc_intro()
{
  long bot=(long)( (float)cachesize*(float)lowmark/100f);
  long high=(long)( (float)cachesize*(float)highmark/100f);
  System.out.println(new Date()+" Cache size watermarks:");
  System.out.println(new Date()+"       High: "+high+" B ("+highmark+"%)");
  System.out.println(new Date()+"        Low: "+bot+" B ("+lowmark+"%)");
  System.out.println(new Date()+" Block size: "+blocksize+" B, avg. directory size: "+(dirsize>0?dirsize+" B.":"not used."));
  System.out.println(new Date()+" File sizes: Min="+minsize+" B, Max="+maxsize+" B.");

  System.out.println(new Date()+" GC Array size: "+gcvalues.length+", Max age: "+refage+" d.");

}

public final void fake_garbage_collection()
{
 refage=REFAGE_MAX;
 maxsize=Integer.MAX_VALUE;
 minsize=0;
 print_gc_intro();
 oursize=0;
 ourfiles=0;
 ourdirs=0;
 scandel=0;
 gcarray=new cacheobject[gcarray.length];
 gcvalues=new float[gcvalues.length];
 System.out.println(new Date()+" Scanning cache directory. This takes some time...");
 scan_cache(root);
 if(scandel>0) System.out.println(new Date()+" "+scandel+" bad files deleted during scan.");
 print_gc_outro();
 dump_gcarray();
 fake_delete_files();
}

private final void delete_files()
{
 long bot=(long)( (float)cachesize*(float)lowmark/100f);
 long high=(long)( (float)cachesize*(float)highmark/100f);
 int files=0;
 
 if(oursize<bot)
 {
   if(gcloglevel>0) System.out.println(new Date()+" Cache size is bellow lowmark - No cleaning needed.");
   return;
 }

 if(gcloglevel>0) System.out.println(new Date()+" Cleaning files....");
 
 for(int i=gcused;i>=0;i--)
 {
  if(gcarray[i]==null) continue;
  if(gcloglevel>3) System.out.println("Deleted: "+gcarray[i].getDirectory().getLocalDir()+gcarray[i].getLocalName()+" age="+gcvalues[i]);
  gcarray[i].delete();
  int size;
  size=gcarray[i].getSize();
  oursize-= (size/blocksize)*blocksize;
  if(size % blocksize !=0 ) oursize-=blocksize;

  if(gcarray[i].getDirectory().countObjects()==0)
   {
    oursize-=dirsize*gcarray[i].getDirectory().cleandir();
   }
  files++;  
  if(oursize<bot) {
       if(gcloglevel>1) System.out.println(new Date()+" Low watermark reached.");
       break;
  }
 }
 if(gcloglevel>0) System.out.println(new Date()+" Deleted "+files+" files from cache.");
 
 /* SAVE Directories */
 if(gcloglevel>0) System.out.println(new Date()+" Updating directory information...");
 for(int i=gcused;i>=0;i--)
 {
  if(gcarray[i]==null) continue;
  gcarray[i].getDirectory().save();
 }
 
 if(gcloglevel>0) System.out.println(new Date()+" Done. Cachesize is now "+oursize+" B, "+(oursize*1000L/cachesize)/10.0+"% full.");
 
 if(oursize>high) 
     if(mgr.loglevel>1) System.out.println(new Date()+" WARNING: Another rescan cache needed to finish GC.\nIf this happens frequently increase gcarraysize in gc.cnf.\ngcarraysize is now set to "+gcarray.length+", good practice is add 20% to it.\nLowering reference_age value somewhat also helps.");
}

private final void fake_delete_files()
{
 long bot=(long)( (float)cachesize*(float)lowmark/100f);
 long high=(long)( (float)cachesize*(float)highmark/100f);

 if(oursize<bot)
  {
   System.out.println(new Date()+" [FAKE] Cache size is OK - No further cleaning needed.");
   return;
  }

 System.out.println(new Date()+" [FAKE] NOT Cleaning files....");
 for(int i=gcused;i>=0;i--)
 {
  if(gcarray[i]==null) continue;
  /* smazat soubor */
  System.out.println("[FAKE] Will delete "+gcarray[i].getDirectory()+gcarray[i].getName()+" age="+gcvalues[i]);
  int size;
  size=gcarray[i].getSize();
  oursize-= (size/blocksize)*blocksize;
  if(size % blocksize !=0 ) oursize-=blocksize;

  if(oursize<bot) break;
 }

 System.out.println(new Date()+" [FAKE] Done. Cachesize is now "+oursize+" bytes.");
 if(oursize>high) System.out.println(new Date()+" [FAKE] WARNING: Another rescan cache will be needed to finishing real GC.");
}

/* proscanuje rekurzivne cache a naplni gcarray */
private final void scan_cache(String dirname)
{
 oursize+=dirsize;
 cachedir cd;
 /* nacist adresar */
 cd=kill_unref_dir(dirname,false);
 /* nacpat do gc pole */
 if(cd!=null)
 {
   /* prevest localname na URL */
   /* ze d:\store\XX\YY\name */
   /* udelat name */
   String urlpart;
   urlpart=cd.getLocalDir().substring(root.length()+1);
   /* preskocit 2x file-separator */
   int i;
   i=urlpart.indexOf(File.separatorChar,0);
   if(i!=-1) {
      urlpart=urlpart.substring(i+1);
      i=urlpart.indexOf(File.separatorChar,0);
      if(i!=-1) urlpart=urlpart.substring(i+1);
                else
             { if(gcloglevel>3) System.out.println(new Date()+" Scanning "+dirname); urlpart="";}
   } else { urlpart="";}

   // System.out.println(cd.getLocalDir()+"="+urlpart);

   Enumeration en;
   en=cd.getObjects();
   while(en.hasMoreElements())
   {
       cacheobject obj;
       obj=(cacheobject)en.nextElement();
       /* spocitat vahu objektu */
       float w;
       w=(now-obj.getLRU())/86400000.0000f;
       /* large obj penalty */
       int size;
       size=obj.getSize();
       if(size>maxsize)
       {
	   if(gcloglevel>3) System.out.println("Deleted too large object: "+dirname+File.separator+obj.getLocalName()+" size="+size+" B, maxsize="+maxsize+" B.");
	   obj.delete();
	   scandel++;
	   continue; 
       } else if(size<minsize)
       {
	   if(gcloglevel>3) System.out.println("Deleted too small object: "+dirname+File.separator+obj.getLocalName()+" size="+size+" B, minsize="+minsize+" B.");
	   obj.delete();
	   scandel++;
	   continue; 
       }

       /* TOO LARGE ?? */
       long xsize;
       if(size>lopsize) { 
	                  xsize=size;
			  xsize-=lopsize;
			  w=applyMod(w,lopact1);
			  
			  if(lopsizeinc>0)
			      while(xsize>lopsizeinc)
			       {
				xsize-=lopsizeinc;
				w=applyMod(w,lopact2);
			       }
			}
      else if(size<=sopsize) 
           { 
	                   xsize=size;
			   xsize-=sopsize;
			   w=applyMod(w,sopact1);

			   if(sopsizedec>0)
			       while(xsize<0)
			       {
				   xsize+=sopsizedec;
				   w=applyMod(w,sopact2);
			       }
           }
      
      /* negative cached?  */
      if(obj.getRC()>=400) w=applyMod(w,neg);
      else
      {
	  /* test expire info */
	  xsize=obj.getExp();
	  boolean chk;
	  chk=obj.isCheckable();
	  if(xsize>0 && xsize<now) {  // object is expired
	   if(chk) 
	       w=applyMod(w,ebc);
	   else
	       w=applyMod(w,exppenal);
	  }
	  else
	  /* redirect ? */
	  if(obj.isRedirect()) w=applyMod(w,redirpenal);
	  else
	  if (chk==false) w=applyMod(w,notchpenal);
      }
     
      /* URL masky */
      if(urlmask!=null)
      {
	  for(i=urlmask.length-1;i>=0;i--)
	  {
	       if(urlmask[i].matches(urlpart+obj.getName())) 
	       { 
		   w=applyMod(w,urlact[i]);
		   break;
	       }
	  }
      }
      
      /* refage delete */
      if(w>=refage) 
      {
		    if(gcloglevel>3) System.out.println("Deleted too old object: "+dirname+File.separator+obj.getLocalName()+" age="+w+", max="+refage+".");
		    obj.delete();
		    scandel++;
		    continue; 
      }
      
      /* add file size to cachesize and files */
      oursize+= (size/blocksize)*blocksize;
      if(size % blocksize !=0 ) oursize+=blocksize;
      ourfiles++;

      /* pridat do gc pole */
      if(w<gclow) continue;
      
      /* najit misto pro vlozeni pomoci puleni intervalu */
      int l,h;
      l=0;
      h=gcused;
      while (l!=h)
      {
	int z;
	z=(l+h)/2;
	if(w<gcvalues[z]) {h=z;continue;}
	if(w>gcvalues[z]) {l=z+1;continue;}
	l=z;break;
      }
      /* pridat za pozici l */
      if(w<gcvalues[l]) l--;

      /* pridat doprava */
	 if(gcused<gcvalues.length-1)
	  {
	   /* jeste mame misto - posunuje se doprava*/
	   // System.out.println("right l="+l+" gcused="+gcused);
	   System.arraycopy(gcvalues,l+1,gcvalues,l+2,gcvalues.length-l-2);
	   System.arraycopy(gcarray,l+1,gcarray,l+2,gcarray.length-l-2);
	   gcvalues[l+1]=w;
	   gcarray[l+1]=obj;
	   gcused++;
	  }
	 else
	  {
	   /* misto neni - jedeme do leva */
	   // System.out.println("left l="+l+" gcused="+gcused);
	   if(l>0)
	   {
	    System.arraycopy(gcvalues,1,gcvalues,0,l);
	    System.arraycopy(gcarray,1,gcarray,0,l);
	   }
	   gcvalues[l]=w;
	   gcarray[l]=obj;
	   gclow=gcvalues[0];
	  }

  } /* directory elements loop */
   
  if(cd.countObjects()==0) { 
      i=cd.cleandir();
      if(i>1) oursize-=dirsize*(i-1);
  } else
  {
      cd.save();
      // add .cacheinfo size
      int size=(int)new File(dirname,cachedir.DIRINFO).length();
      oursize+= (size/blocksize)*blocksize;
      if(size % blocksize !=0 ) oursize+=blocksize;
      // System.out.println("adding "+dirname+"/.cacheinfo, oursize="+oursize);
      ourfiles++;
  }
  } /* cd not null */

  File root=new File(dirname);

  String filez[];
  filez=root.list();
  if(filez==null) 
  { 
      /* directory no longer exists */
      oursize-=dirsize;
      return;
  } else
  {
      ourdirs++;
      if(realdirsize)
      {
	  // add real directory size
	  int size=(int)new File(dirname).length();
	  oursize+= (size/blocksize)*blocksize;
	  if(size % blocksize !=0 ) oursize+=blocksize;
          //  System.out.println("adding real directory"+dirname+", oursize="+oursize);
      }
  }
  /* rekurzivne dive into subdirz */
  Vector dirs=new Vector();
  String t2;
  /* extract subdirs */
  for(int i=filez.length-1;i>=0;i--)
  {
     t2=dirname+File.separator+filez[i];
     if(new File(t2).isDirectory()) dirs.addElement(t2);
  }

  /* clean some memory */
  dirs.trimToSize();
  filez=null;
 
  for(int i=dirs.size()-1;i>=0;i--)
     scan_cache((String)dirs.elementAt(i));
}

/* vraci true pokud modifikator nic nedela */
private final boolean dummyMod(String mod)
{
    float f;
    
    if(mod!=null)
	if(mod.length()>0)
	    if(mod.charAt(0)=='=')
		return false;

    f=applyMod(1.23456f,mod);
    if(f==1.23456f) 
    {
        // System.out.println("modificator: "+mod+" ignored.");	
	return true;
    }
    else
	return false;
}

private final float applyMod(float f,String mod)
{
 if(mod==null) return f;
 float m;
 try
 {
   m=Float.valueOf(mod.substring(1)).floatValue();
 }
 catch ( NumberFormatException z)
  {
   System.out.println("[GC_CONFIG_ERROR] Invalid number in LRU modifier '"+mod+"'");
   throw z;
  }
 switch(mod.charAt(0))
 {
  case '+':f+=m;break;
  case '-':f-=m;break;
  case '*':f*=m;break;
  case '/':f/=m;break;
  case '=':if(f>=m) f=REFAGE_MAX-1;
           break;
  default:
   System.out.println("[GC_CONFIG_ERROR] Invalid action in LRU modifier '"+mod+"'");
   throw new IllegalArgumentException("Unknown action "+mod.charAt(0));
 }
 return f;
}

private final cachedir kill_unref_dir(String dirname, boolean recurse)
{
  File root=new File(dirname);

  String dirfilez[];
  String local[];
  Vector filez=new Vector();

  if(!root.isDirectory()) return null;
  dirfilez=root.list();
  if(dirfilez==null) return null; // WAS: dirfilez=new String[0]; WHY?
  cachedir cd=new cachedir(dirname+File.separator,null);
  cd.checkDir();
  local=cd.listLocalNames();
  cd.save();
  /* konverze dirfilez na vector */
  for(int i=dirfilez.length-1;i>=0;i--)
   filez.addElement(dirfilez[i]);
  dirfilez=null;

  /* smazat localnames z filez, abychom je nesmazali */
  for(int i=local.length-1;i>=0;i--)
   filez.removeElement(local[i]);

  /* abychom si nesmazali cacheinfo !! */
  /* pokud jsme v empty dir, tak ho smazeme */
  if(cd.countObjects()>0 ) filez.removeElement(cachedir.DIRINFO);
    else
      cd=null;
  
  local=null;
  
  now=System.currentTimeMillis();

  /* projedeme filez a smazeme nalezene soubory */
  for(int i=filez.size()-1;i>=0;i--)
  {
    File df=null;
    try{
      df=new File(dirname+File.separator+(String)filez.elementAt(i));
    }
    catch (ArrayIndexOutOfBoundsException z) {continue;}
    if(!df.isDirectory())
      {
       // .tmp age check
       if(now-df.lastModified()>2*mgr.SAVETIMER)
       {
           if(gcloglevel>2) System.out.println("Deleting orphan file: "+df);
	   scandel++;
           df.delete();
       }
       filez.removeElementAt(i);
       i++;
       continue;
      }
   }

  if(recurse) 
  { 
      /* rekurzivne do adresaru */
      cd=null;
      filez.trimToSize();
      for(int i=filez.size()-1;i>=0;i--)
       {
	 kill_unref_dir(dirname+File.separator+(String)filez.elementAt(i),true);
       }
  }
 
  /* try to delete empty dir */
  if(cd==null)
     root.delete();

  return cd;
}

private final void importcache0(String dirname)
{
 File myroot=new File(dirname);
 if(!myroot.isDirectory()) return;

  /* rekurzivne do adresaru */
  String dirfilez[]=myroot.list();
  for(int i=0;i<dirfilez.length;i++)
   {
     if(!new File(myroot,dirfilez[i]).isDirectory()) continue;
     try {
          Integer.valueOf(dirfilez[i]);
	 }
     catch (Exception exx) { continue;}
	 String subdirz[];
	 subdirz=new File(dirname,dirfilez[i]).list();
	 for(int j=0;j<subdirz.length;j++)
	 {
          if(!new File(myroot+File.separator+dirfilez[i],subdirz[j]).isDirectory()) continue;
          try {
              Integer.valueOf(subdirz[j]);
	      }
          catch (Exception exx) { continue;}
          import0(dirname+File.separator+dirfilez[i]+File.separator+subdirz[j],
	          dirname+File.separator+dirfilez[i]+File.separator+subdirz[j]);
	  }
   }
} /* import cache 0 */

/* export */
private final void  export_one_dir(String dirname, String to, int type, long difftime)
{
  File root=new File(dirname);

  if(!root.isDirectory()) return;
  cachedir cd=new cachedir(dirname+File.separator,null);
  boolean export=false;
  if(type!=EXPORT_ALL)
  {
    now=System.currentTimeMillis();
    Enumeration en=cd.getObjects();
    while(en.hasMoreElements())
    {
     cacheobject obj;
     obj=(cacheobject)en.nextElement();
     if(type==EXPORT_LRU)
        if (now-obj.getLRU()<=difftime) { export=true;break;}
	else continue;
     if(now-obj.getDate()<=difftime) { export=true;break;}
    }
 } else export=true;

 if(export)
  {
   String en;
   en=dirname.substring(this.root.length());
   if(en.length()>5)
   {
       en=en.substring(1); // remove first file separator
       // System.out.println("Exporting: "+dirname+" >> "+en);
       en=en.substring(en.indexOf(File.separator)+1);
       // System.out.println(">>: "+en);
       en=en.substring(en.indexOf(File.separator)+1);
       // System.out.println(">>:: "+en);
       cachedir ncd;
       ncd=new cachedir(to+File.separator+en+File.separator,null);
       cd.export_to(ncd,type,difftime);
       ncd.save();
       ncd.cleandir();
   }
  }

 // into subdirz
 String filez[];
 filez=root.list();
 if(filez==null) return;
 for(int i=filez.length-1;i>=0;i--)
  if(new File(dirname+File.separator+filez[i]).isDirectory()) export_one_dir(dirname+File.separator+filez[i],to,type,difftime);
}

/* parses verbose timestring 1d, 2M,  ... */
public final static long timestring(String s)
{
 int multi;
 double value=0;
 int i;
 if(s==null) return 0;
 s=s.trim(); 
 for(i=0;i<s.length();i++)
 {
     char c;
     c=s.charAt(i);
     if(c=='.') continue;
     if(c>'9' || c<'0' )
	 break;
 }
 value=Float.valueOf(s.substring(0,i)).floatValue();
 multi=parseTimeUnits(s.substring(i).trim());
 return (long)(1000.0*multi*value);
}

/* parses verbose sizestring 1KB, 2bytes,  ... */
public final static long sizestring(String s)
{
 int multi;
 double value=0;
 int i;
 if(s==null) return 0;
 s=s.trim(); 
 for(i=0;i<s.length();i++)
 {
     char c;
     c=s.charAt(i);
     if(c=='.' || c=='-') continue;
     if(c>'9' || c<'0' )
	 break;
 }
 value=Float.valueOf(s.substring(0,i)).floatValue();
 multi=parseSizeUnits(s.substring(i).trim());
 return (long)(multi*value);
}

/* prevede time unitu na sekundy */
private static final int parseTimeUnits(String unit)
{
    if(unit==null) return 60;
    if(unit.length()==0) return 60;
    if(unit.length()==1)
    {
       switch(unit.charAt(0))
       {
	case 's':
	case 'S':
		  return 1; // seconds
	case 'm':
		  return 60; // minutes
	case 'h':
	case 'H':
		  return 3600; // hours
	case 'd':
	case 'D':
		  return 86400; // day
	case 'W':
	case 'w':
		  return 86400*7; // week
	case 'f':
	case 'F':
		  return 86400*14; // fortnight
	case 'M':
		  return 30*86400; //Months
	case 'y':
	case 'Y':
		  return (int)(86400*365.2522); // Year
       }
    }
    else
    {
	unit=unit.toLowerCase();
	if(unit.startsWith("second"))
	    return 1;
	if(unit.startsWith("minute"))
	    return 60;
	if(unit.startsWith("hour"))
	    return 3600;
	if(unit.startsWith("day"))
	    return 86400;
	if(unit.startsWith("week"))
	    return 86400*7;
	if(unit.startsWith("fortight"))
	    return 86400*14;
	if(unit.startsWith("month"))
	    return 86400*30;
	if(unit.startsWith("year"))
	    return (int)(86400*365.2522);
    }

    System.out.println("Unknown time unit: '"+unit+"'");
    return 0;
}

/* prevede size unitu na bajty */
private static final int parseSizeUnits(String unit)
{
    if(unit==null) return 1;
    if(unit.length()==0) return 1;
    char c;
    c=unit.toLowerCase().charAt(0);
    switch(c)
    {
       case 'b':
	      return 1;
       case 'k':
	      return 1024; // kilobytes
       case 'm':
	      return 1024*1024; // megabytes
       case 'g':
	      return 1024*1024*1024; // gigabytes
    }
    System.out.println("Unknown size unit: '"+unit+"'");
    return 0;
}

} /* class */
