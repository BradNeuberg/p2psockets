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

class AtomSet extends Range
{
 static final boolean ANY = true;
 private static final String NEGMINIMUM = "\n";

 String set;
 boolean neg = false;

 AtomSet(String set) { this(set, false); }

 AtomSet(String set, boolean neg) { this(set, 1, 1); this.neg = neg; }

 AtomSet(String set, int min, int max)
	{ super(min, max); this.set = set; }

 AtomSet(boolean any, int min, int max)
 {
	super(min, max);

	if(any)
	{
		this.set = NEGMINIMUM;
		this.neg = true;
	}
 }

 final void setNegate() { neg = true; }

 String rexToString()
	{ return "<" + (neg? "~" : "") +
		"Set>[" + set + "]" + super.rexToString(); }
}
