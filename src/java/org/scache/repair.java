package org.scache;

/*
 *  Smart Cache REPAIR TOOL
 *  Copyright (C) 1999-2002 Radim Kolar
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

import java.util.*;
import java.io.*;

public class repair
{

 public static final String VERSION="0.10";
 /* global vars */
 public static boolean quiet=false;
 public static boolean force=false;
 public static boolean nosave=false;
 public static boolean forcesave=false;
 public static boolean compress=false;

 static regexp ignoretable[]=
            {
              new regexp("*.tmp",false)
            };


 static String guesstable[]=
           {
             // java stuff
             // ".java","application/java",
             ".java","text/plain",
             ".class","application/java-vm",
             ".jar","application/java-archive",
	
	     // images
             ".gif","image/gif",
	     ".ief","image/ief",
	     ".tiff","image/tiff",
	     ".tif","image/tiff",
             ".jpeg","image/jpeg",
             ".jpe","image/jpeg",
             ".jpg","image/jpeg",
	     ".png","image/png",
	     ".ras","image/x-cmu-raster",
	     ".bmp","image/x-ms-bmp",
	     ".pnm","image/x-portable-anymap",
	     ".pbm","image/x-portable-bitmap",
	     ".pgm","image/x-portable-graymap",
	     ".ppm","image/x-portable-pixmap",
	     ".rgb","image/x-rgb",
	     ".xbm","image/x-xbitmap",
	     ".xpm","image/x-xpixmap",
	     ".xwd","image/x-xwindowdump",
	
	     // plain text
             ".txt","text/plain",
             ".text","text/plain",
             ".doc","text/plain",
             ".log","text/plain",
	     ".csv","text/comma-separated-values",
	     ".tsv","text/tab-separated-values",

	     // hypertext
             "welcome",  "text/html", // cache-generated index
	     ".css","text/css",
	     ".js","text/javascript",
	     ".shtml", "text/html",
	     ".pl",  "text/html",
	     ".cgi",  "text/html",
	     ".asp",  "text/html",
	     ".jsp",  "text/html",
             ".htm",  "text/html",
             ".html",  "text/html",
             ".htmli",  "text/html",
             ".dchtml",  "text/html",
	     ".pht",   "text/html",
	     ".phtml", "text/html",
	     ".php",   "text/html",
	     ".php3",  "text/html",
	     ".php4",  "text/html",
	     ".php3p", "text/html",
	
	     ".texi","application/x-texinfo",
	     ".texinfo","application/x-texinfo",

	     // VRML
	     ".vrm","x-world/x-vrml",
	     ".vrml","x-world/x-vrml",
	     ".wrl" ,"x-world/x-vrml",
	
	     // formated text
	     ".rtx","text/richtext",
             ".pdf","application/pdf",
	     ".rtf","application/rtf",
	     ".ai","application/postscript",
	     ".ps","application/postscript",
	     ".eps","application/postscript",
	     ".wp5","application/wordperfect5.1",
	     ".wk","application/x-123",
	     ".dvi","application/x-dvi",
	     ".frm","application/x-maker",
	     ".maker","application/x-maker",
	     ".frame","application/x-maker",
	     ".fm"   ,"application/x-maker",
	     ".fb"   ,"application/x-maker",
	     ".book", "application/x-maker",
	     ".fbdoc","application/x-maker",

	     // fonts
	     ".pfa","application/x-font",
	     ".pfb","application/x-font",
	     ".gsf","application/x-font",
	     ".pcf","application/x-font",
	     ".pcf.z","application/x-font",
	     ".gf","application/x-tex-gf",
	     ".pk","application/x-tex-pk",

	     // archives
             ".zip","application/zip",
             ".tar","application/x-tar",
	     ".hqx","application/mac-binhex40",
	     ".bcpio","application/x-bcpio",
	     ".cpio","application/x-cpio",
	     ".deb","application/x-debian-package",
             ".gtar","application/x-gtar",
	     ".tgz" ,"application/x-gtar",
	     ".tar.gz","application/x-gtar",
	     ".shar","application/x-shar",
	     ".sit","application/x-stuffit",
	     ".sv4cpio","application/x-sv4cpio",
	     ".sv4crc","application/x-sv4crc",
	
	     //audio
	     ".au","audio/basic",
	     ".snd","audio/basic",
             ".mid","audio/midi",
             ".midi","audio/midi",
             ".mp2","audio/mpeg",
             ".mpega","audio/mpeg",
             ".mpga","audio/mpeg",
             ".mp3","audio/mpeg",
	     ".m3u","audio/mpegurl",
             ".aif","audio/x-aiff",
             ".aiff","audio/x-aiff",
             ".aifc","audio/x-aiff",
	     ".wav","audio/x-wav",
	     ".ra" ,"audio/x-pn-realaudio",
	     ".rm" ,"audio/x-pn-realaudio",
	     ".ram","audio/x-pn-realaudio",

	     //video
	     ".mpeg","video/mpeg",
	     ".mpg","video/mpeg",
             ".mpe","video/mpeg",
             ".qt","video/quicktime",
             ".mov","video/quicktime",
             ".avi","video/x-msvideo",
             ".movie","video/x-sgi-movie",
	     ".dl","video/dl",
	     ".fli","video/fli",
	     ".gl","video/gl",
	     ".asf","video/x-ms-asf",
	     ".asx","video/x-ms-asf",

	     //sources
	     ".tex","text/x-tex",
	     ".c","text/plain",
	     ".h","text/plain",
	     ".ltx","text/x-tex",
	     ".sty","text/x-tex",
	     ".cls","text/x-tex",
	     ".latex","application/x-latex",
	     ".oda","application/oda",
	     ".t"  ,"application/x-troff",
	     ".tr" ,"application/x-troff",
	     ".roff","application/x-troff",
	     ".man", "application/x-troff-man",
	     ".me",  "application/x-troff-me",
	     ".ms",  "application/x-troff-ms",
	     ".vcs", "text/x-vCalendar",
	     ".vcf", "text/x-vCard",

	     // misc apps
	     ".csm","application/cu-seeme",
	     ".cu", "application/cu-seeme",
	     ".tsp","application/dsptype",
	     ".spl","application/futuresplash",
	     ".pgp","application/pgp-signature",
             ".wz", "application/x-Wingz",
	     ".dcr","application/x-director",
	     ".dir","application/x-director",
	     ".dxr","application/x-director",
             ".hdf","application/x-hdf",
             ".mif","application/x-mif",
	     ".nc" ,"application/x-netcdf",
	     ".cdf","application/x-netcdf",
	     ".pac","application/x-ns-proxy-autoconfig",
	     ".swf","application/x-shockwave-flash",
	     ".swfl","application/x-shockwave-flash",	
	     ".ustar","application/x-ustar",
	     ".src","application/x-wais-source",

	     // msdos
	     ".com","application/x-msdos-program",
	     ".exe","application/x-msdos-program",
	     ".bat","application/x-msdos-program",

	     //microsoft apps
	     ".xls","application/excel",
	     ".dot","application/msword",
             ".ppt","application/powerpoint",
	
	     //binary files
	     ".bin","application/octet-stream",

           };

 public final static void main(String argv[])
 {
  System.out.println("Repair "+VERSION+" - Smart Cache integrity checker and repairer.");
  System.out.println("Utility for recreating .cacheinfo files without data loss. ");
  System.out.println("Copyright (c) Radim Kolar 1999-2002. There are NO warranty and no miracles!\n");

  if(argv.length==0)
   {
     usage();
   }
  boolean rec;
  rec=false;
  if(cacheobject.defaultname==null) cacheobject.defaultname=mgr.DEFAULTNAME;
  boolean anydir=false;
  for(int i=0;i<argv.length;i++)
  {
   if(argv[i].equals("-q")) { quiet=true;continue;}
   if(argv[i].equals("-f")) { force=true;continue;}
   if(argv[i].equals("-n")) { nosave=true;continue;}
   if(argv[i].equals("-c")) { compress=true;continue;}
   if(argv[i].equals("-r")) { rec=true;continue;}
   if(argv[i].equals("-w")) { forcesave=true;continue;}
   if(argv[i].equals("-m"))
	   if (i<argv.length-1)
	   {
		   loadMimeTypes(argv[i+1]);i++;continue;
	   } else continue;
   if(argv[i].equals("-x"))
	   if (i<argv.length-1)
	   {
	   	   StringTokenizer st=new StringTokenizer(argv[i+1],File.pathSeparator,false);
		   while(st.hasMoreTokens())
		   {
		    String token;
		    token=st.nextToken();
		    if(token.equals("!")) { ignoretable=null;continue;}
		    ignoretable=mgr.addRegexpToArray(token,ignoretable,true);i++;continue;
		   }
	   } else continue;
   if(argv[i].equals("-i"))
	   if (i<argv.length-1)
	   {
		   cacheobject.defaultname=argv[i+1];continue;
	   } else continue;
   if(argv[i].startsWith("-"))
     {
       System.out.println("[ERROR] Unrecognized option: "+argv[i]);
       return;
     }
   repairDir(argv[i],rec);
   anydir=true;
  }
  if(anydir) System.out.println("[OK] Rebuild done.\n");
   else
    usage();
 }

 public final static cachedir repairDir(String dirname,boolean recurse)
 {
   if(dirname==null) return null;
   File f=new File(dirname);
   if(!f.exists())
   {
     System.out.println("[ERROR] Directory "+dirname+" doesn't exists.");
     return null;
   }

   if(!f.isDirectory())
    {
      int sep=dirname.lastIndexOf(File.separatorChar);
      if(sep==-1) dirname=".";
        else
      dirname=dirname.substring(0,sep);
      f=new File(dirname);
    }
  if(!dirname.endsWith(File.separator)) dirname+=File.separatorChar;

  cachedir cd;
  if(force)
  {
    /* try to delete old .cacheinfo */
    new File(dirname,cachedir.DIRINFO).delete();
  }
  cd=new cachedir(dirname,null);
  boolean msg=false;
  if(!cd.checkDir())
    if(!quiet)
       {
         System.out.println("[START] Rebuilding directory "+dirname);
	 msg=true;
       }

  String dirfilez[];
  String local[];
  Vector filez=new Vector();

  dirfilez=f.list();
  local=cd.listLocalNames();
  if(dirfilez==null) return null;
  /* konverze dirfilez na vector */
  for(int i=dirfilez.length-1;i>=0;i--)
   filez.addElement(dirfilez[i]);
  dirfilez=null;

  /* smazat localnames z filez, abychom je nesmazali */
  for(int i=local.length-1;i>=0;i--)
   filez.removeElement(local[i]);

  filez.removeElement(cachedir.DIRINFO);
  // cd=null;

  local=null;
  /* projedeme filez a pridame nalezene soubory */
  addorphan:for(int i=filez.size()-1;i>=0;i--)
   {
    File df=null;
    String fn=null;
    try{
      fn=(String)filez.elementAt(i);
    }
    catch (ArrayIndexOutOfBoundsException z) {continue;}
    df=new File(dirname+fn);
    /* scan ignoretable */
    for(int j=ignoretable.length-1;j>=0;j--)
       if(ignoretable[j].matches(fn)) continue addorphan;
    if(!df.isDirectory())
      {
       if(msg==false && !quiet)
       {
         System.out.println("[START] Rebuilding directory "+dirname);
	 msg=true;
       }
       String ctype;
       String enc;
       enc=null;
       String nm;
       nm=fn;
       if(nm.endsWith(".gz"))
       {
	  nm=fn.substring(0,fn.length()-3); 
	  ctype=guessContentType(nm);
	  enc="gzip";
	} else ctype=guessContentType(nm);
       if(!quiet) System.out.println("[INFO] Adding orphan file: "+df+" ct="+ctype);
       cacheobject co;
       if(nm.equals(cacheobject.defaultname)) nm="";
       co=new cacheobject(nm,cd,fn,
       df.lastModified(),ctype,enc,(int)df.length());
       cd.putObject(co);
       continue;
      } else
         if(recurse==true)
	    repairDir(df.getPath(),true);
   }
  if(!nosave)
         {
	    if(cd.countObjects()==0) { cd.cleandir();return null;}
	      else
	        {
		  if(forcesave==true) cd.dirty=true;
		  if(compress) cd.compressdir();
                  cd.save();
		}
         }
  return cd;
 }

 public final static void loadMimeTypes(String fname)
 {
  if(fname==null) return;
  File f=new File(fname);
  if(!f.isFile()) return;
  try
  {
    BufferedReader in=new BufferedReader(new LineNumberReader(new FileReader(fname)));
    String line;
    StringTokenizer st;
    String mimetype,ext;
    // init GT
    guesstable=new String[0];
    while(true)
    {
	    ext=mimetype=null;
	    line=in.readLine();
	    if(line==null) break;
	    st=new StringTokenizer(line);
	    if(!st.hasMoreTokens()) continue;
	    mimetype=st.nextToken();
	    if(mimetype.startsWith("#")) continue;
	    while(true)
	    {
		
	    	if(!st.hasMoreTokens()) break;
	    	ext=st.nextToken();
		updateGuessTable(mimetype,ext);
	    }
    }
    in.close();
  }
  catch (IOException grrrrrrrrrrrrr)
   {
    System.err.println("[ERROR] Reading mime.types from "+fname);
   }

 }

 public final static String guessContentType(String fname)
 {
  fname=fname.toLowerCase();
  for(int i=0;i<guesstable.length;i+=2)
   {
    if(fname.endsWith(guesstable[i])) return guesstable[i+1];
   }
  System.out.println("[WARNING] Can not determine MIME type for "+fname+", defaulting to text/html");
  return "text/html";
 }

 /* prozdejsi prepise predchozi hodnotu */
 private final static void updateGuessTable(String mimetype,String ext)
 {
 	 if(mimetype==null || ext==null) return;
	 if(mimetype.length()==0 || ext.length()==0) return;
	 ext=("."+ext).toLowerCase();
	 for(int i=0;i<guesstable.length;i+=2)
	 {
		 if(ext.equals(guesstable[i]))
		   {
		     guesstable[i+1]=mimetype;
		     return;
		   }
	 }
	 String tmp[];
	 tmp=new String[guesstable.length+2];
	 System.arraycopy(guesstable,0,tmp,0,guesstable.length);
	 tmp[guesstable.length]=ext;
	 tmp[guesstable.length+1]=mimetype;
	 guesstable=tmp;
}
private static final void usage()
{
     System.out.println("Syntax: repair [-q] [-r] [-f] [-n] [-m mime.types] < Directory ... >");

 System.out.println("  -q            quiet mode");
 System.out.println("  -r            recurse into directories");
 System.out.println("  -w            write new .cacheinfo files even if not changed");
 System.out.println("  -f            ignore existing .cacheinfo files");
 System.out.println("  -c            gzip compress directory contents");
 System.out.println("  -n            do not actually make changes");
 System.out.println("  -i <filename> directory index filename (default .welcome)");
 System.out.println("  -x <mask>["+File.pathSeparator+"mask] ... Ignore more files (default *.tmp, ! to reset)");
 System.out.println("  -m <file>     use alternate table for determining mime type");
     System.exit(1);
     return;
}
}
