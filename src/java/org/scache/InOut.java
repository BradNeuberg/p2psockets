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

public class InOut implements Runnable
{
 InputStream in;
 OutputStream out;
 Thread notify;
 InOut(InputStream is,OutputStream os,Thread intr)
 {
  in=is;
  out=os;
  notify=intr;
 }
 public void run()
 {
  byte b[];
  int rb;

  b=new byte[512];

  mainloop:while(true)
  {
     try
     {
      if(Thread.interrupted()) return;
      rb=in.read(b);
      if(rb==-1) break mainloop;
      out.write(b,0,rb);
      out.flush();
     }
     catch (InterruptedIOException timeout)
     {
      notify.interrupt();
      return;
     }
     catch (IOException data_error)
     {
      notify.interrupt();
      return;
     }
 }
 notify.interrupt();
}

}/*class*/
