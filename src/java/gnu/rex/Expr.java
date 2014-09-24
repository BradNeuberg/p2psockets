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


class Expr extends Range
{
 static int numberSequence = 0;

 Atom list;
 Atom curp;
 Atom curpAlt;

 int altLevel;
 int number;

 Atom firstAtom;

 Expr() { this(true); }

 Expr(boolean newNumber)
 {

	list = curp = curpAlt = null;
	altLevel = 0;

	if(newNumber)
		number = numberSequence++;
 }

 Atom getHead() { return list; }
 boolean isEmpty() { return list == null; }

 Atom append(Atom cons)
 {
	if(altLevel > 0)
	{
		--altLevel;

		if(curpAlt == null)
			return appendAlt(cons,altLevel);

	   return curpAlt = curpAlt.rplacd(cons);
	}

	curpAlt = null;

    return curp = curp==null? (list=cons) : curp.rplacd(cons);
 }

 final Atom appendAlt(Atom atom) { return appendAlt(atom,0); }

/* final Atom appendAlt(Atom atom, int al)
	{ return appendAlt(atom, al); }

 final rxCons appendAlt(rxCons cons) { return appendAlt(cons,0); } */

 Atom appendAlt(Atom cons, int al)
 {
	if(curp == null)
		return append(cons);

	if(curpAlt == null)
		curpAlt = curp;

	altLevel = al;

    return curpAlt = curpAlt.rplacp(cons);
 }

 final void setAltLevel(int al) { altLevel = al; }

 final int getAltLevel() { return altLevel; }

 String listToString()
 {
    StringBuffer ret = new StringBuffer("");

	for(Atom rc = list; rc != null; rc = rc.cdr())
	{
		if(rc != list)
			ret.append(' ');

		ret.append(rc.rexToString());

		for(Atom rp = rc.cpr(); rp != null; rp = rp.cpr())
		{
			ret.append("\n\tOR");

			for(Atom ra = rp; ra != null; ra = ra.cdr())
				ret.append(" " + ra.rexToString());
		}
	}

    return ret.toString();
 }

 final void setFirstAtom()
	{ firstAtom = list.car(); }

 String rexToString()
	{ return "( " + listToString() +
		" ){" + min + "," + (max==MAX_VALUE? "Max" : ""+max) + "}"; }

}



