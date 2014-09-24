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

class StateCntrl extends State
{
 short next0;
 short next1;
 boolean accept;

 StateCntrl() { this(IMPASSE, IMPASSE); }

 StateCntrl(short next0, short next1)
 	{ this.next0 = next0; this.next1 = next1; }

 StateCntrl(int next0, int next1)
 	{ this((short)next0, (short)next1); }

 void setAccept() { accept = true; }

 void resetAccept() { accept = false; }

 boolean canAccept() { return accept; }

 short getNext0() { return next0; }

 short getNext1() { return next1; }

 final void setNext0(int next0) { this.next0 = (short)next0; }

 void setNext1(int next1) { this.next1 = (short)next1; }

 String stateToString()
        { return accept? "<ACCPT>" :  (next0 + "," + next1); }

}
