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

class AtomString extends Range
{
 char[] string;

 AtomString(int min, int max)
	{ super(min, max); string = null; }

 AtomString(String string) { this(string, 1, 1); }

 AtomString(String string, int min, int max)
 {
	super(min, max);
	this.string = new char[string.length()];

	string.getChars(0, string.length(), this.string, 0);
 }

 AtomString(char[] string, int min, int max)
 {
	super(min, max);
	this.string = new char[string.length];

	System.arraycopy(string, 0, this.string, 0, string.length);
 }

 String rexToString()
	{ return "<string>\"" + string + "\"" + super.rexToString(); }
}
