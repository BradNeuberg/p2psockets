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

class StateRepeater extends StateCntrl
{
 short min;
 short max;
 short pass;

 StateRepeater(short next0, short next1, short min, short max)
	{ super(next0, next1); this.min = min; this.max = max; }

 StateRepeater(int next0, int next1, int min, int max)
	{ this((short)next0, (short)next1, (short)min, (short)max); }

 final short getNext0() { return pass < max? next0 : IMPASSE; }

 final short getNext1() { return pass >= min? next1 : IMPASSE; }

 final short getMin() { return min; }

 final short getMax() { return max; }

 final short getPass() { return pass; }

 final short incPass() { return ++pass; }

 final void reset() { super.reset(); pass = 0; }

 String stateToString()
        { return super.stateToString() + "{"+min+";"+max+"}." + pass; }

}
