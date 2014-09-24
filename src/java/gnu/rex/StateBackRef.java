/*
Copyright (C) 1998 Stepan Solokov (sts@crocodile.org)

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Library General Public
License as published by the Free Software Foundation; either
version 2 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Library General Public License for more details.

You should have received a copy of the GNU Library General Public
License along with this library; if not, write to the
Free Software Foundation, Inc., 59 Temple Place - Suite 330,
Boston, MA  02111-1307, USA.
*/

package gnu.rex;

class StateBackRef extends State1
{
 byte backRef;
 boolean accept;

 StateBackRef(int backRef)
	{ super((char)0); this.backRef = (byte)backRef; }

 final void setAccept() { accept = true; }

 final void resetAccept() { accept = false; }

 final boolean canAccept() { return accept; }

 String stateToString()
	{ return "br#" + backRef + (accept? "<ACCPT>->" : "->"); }


}
