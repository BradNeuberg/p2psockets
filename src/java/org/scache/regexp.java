package org.scache;

/*
 *  Smart Cache, http proxy cache server
 *  Copyright (C) 1998-2003 Radim Kolar
 *
 *  Last change: Mar 28 2003
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

public final class regexp
{
 public boolean ignoreCase;

 private String elements[];

 private String prefix;
 private String suffix;

 private int prefixlen;
 private int suffixlen;

 private boolean exact;


 regexp(String exp, boolean ign)
 {
  ignoreCase=ign;
  exact=false;
  int i;

  if(exp.indexOf('*',0)==-1) { exact=true;
                               prefix=exp;
                               prefixlen=exp.length();
                               if(ign) prefix=prefix.toLowerCase();
                               return;
                             }
  i=1;
  if(!exp.startsWith("*"))
   {
    /* mame prefix */
    prefix=exp.substring(0,exp.indexOf('*',0));
    if(ign) prefix=prefix.toLowerCase();
    prefixlen=prefix.length();
    i=prefixlen+1;
   }
   int pos;
   String part;
   while(true)
   {
    pos=exp.indexOf('*',i);
    if(pos==-1)
      {
       /* mame suffix */
       if(i==exp.length()) return;
       suffix=exp.substring(i);
       if(ign) suffix=suffix.toLowerCase();
       suffixlen=suffix.length();
       return;
      }

    part=exp.substring(i,pos);
    if(ign) part=part.toLowerCase();
    if(!part.equals(""))
     {
      /* pridame part do pole */

       if(elements==null) elements=new String[1];
        else
       {
        String tmp[]=new String[elements.length+1];
        System.arraycopy(elements,0,tmp,0,elements.length);
        elements=tmp;
       }
      elements[elements.length-1]=part;
     }/* pridani */

    i=pos+1;
    if(i>=exp.length()) break;

   }/* while */

 }

 public String dump()
 {
  String result="exact="+exact+" prefix="+prefix+" suffix="+suffix+"  elements=";
  if (elements==null) return result+="null";
  for(int i=0;i<elements.length;i++)
   result+=elements[i]+",";

  return result;
 }

 final public String toString()
 {
  if(exact==true) return prefix;
  String result;
  if(prefix==null) result="*";
   else
    result=prefix+"*";
  /* pridat elementy */
  if(elements!=null)
   for(int i=0;i<elements.length;i++)
     result+=elements[i]+"*";

  /* suffix */
  if(suffix!=null) result+=suffix;

  return result;
 }

 final public boolean matches(String str)
 {
  int pos;
  pos=0;
  if(ignoreCase) str=str.toLowerCase();
  if(exact) return str.equals(prefix);

  if(prefix!=null)
   if(!str.startsWith(prefix)) return false;
       else
     pos=prefixlen;
  if(elements!=null)
   {
    int j=elements.length;
    for(int i=0;i<j;i++)
     {
     if( (pos=str.indexOf(elements[i],pos))==-1 ) return false;
     pos+=elements[i].length();
     }
   }
  if(suffix==null) return true;
  if(str.length()-pos<suffixlen) return false;

  return str.endsWith(suffix);
 }
 
 /* vraci pole stringu nahrazujicich * */
 /* nebo null pri nenalezeni */
 final public String[] search(String str)
 {
  int pos=0; // pozice v testovacim stringu
  int rsize=1;  // velikost result size
  int rpos=0;   // pozice v result bufferu

  String result[];

  if(ignoreCase) str=str.toLowerCase();
  if(exact) 
      if(str.equals(prefix))
	  return new String[0];
      else
	  return null;
  
  /* urceni velikosti resultu:
   *   mozne pripady:
   *      *      = 1
   *    pre*     = 1
   *    *sfix    = 1
   *    pre*sfix = 1
   *    pre*1*sfix = 2
   */

  if(prefix!=null)
    if(!str.startsWith(prefix)) 
	return null;
    else
        pos=prefixlen;
  /*
  else
      rsize=1;
      */
  
  if(elements!=null) rsize+=elements.length;
  
  result=new String[rsize];

  if(elements!=null)
   {
    int j=elements.length;
    for(int i=0;i<j;i++)
     {
	 int npos;
         npos=str.indexOf(elements[i],pos);
         if( npos ==-1 ) return null;
	 result[rpos++]=str.substring(pos,npos);
         pos=npos+elements[i].length();
     }
   }

  if(suffix==null) 
  {
      // add last match
      result[rpos]=str.substring(pos);
      return result;
  }
  if(str.length()-pos<suffixlen) return null;
  if(!str.endsWith(suffix))      return null;
  result[rpos]=str.substring(pos,str.length()-suffixlen);

  return result;
 }

 public static void main(String argv[])
 {
  if (argv.length<2) return;
  String res[];
  regexp r=new regexp(argv[0],false);
  System.out.println("Regexp: "+r+" compiled: "+r.dump());
  System.out.println("Matches: "+argv[1]+" : "+r.matches(argv[1]));
  res=r.search(argv[1]);
  System.out.println("Search: "+argv[1]+" : "+res);
  if(res!=null)
      for(int i=0;i<res.length;i++)
	  System.out.println("  Substring["+i+"] = "+res[i]);
 }
}
