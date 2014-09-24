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

abstract class Atom implements Cloneable
{
 Atom Cdr;
 Atom Cpr;

 final static int MAX_VALUE = 256;
 final static int DEBUG_LEVEL = 0;

 /*
  * This method added to allow for atom duplication when same
  * copy cannot be used more than once.
  *
  * Contributed by:
  * Peter Kutschera <peter@pinguin1.arcs.ac.at>
  */

 public Object clone()
 {
	try {
		return super.clone();
	} catch (CloneNotSupportedException e) {
		// Ignore for now
	}

    return this;
  }

 final Atom cdr() { return Cdr; }
 final Atom cpr() { return Cpr; }

 final Atom car() { return this; }
 final Atom rplacd(Atom d) { return Cdr = d; }
 final Atom rplacp(Atom p) { return Cpr = p; }

 void setRange(int n, int x) {}

 String rexToString() { return ""; }

}
