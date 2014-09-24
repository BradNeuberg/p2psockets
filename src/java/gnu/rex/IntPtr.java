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


/*
 * Maybe it's fine not to have pointers as far as security, etc. concerned,
 * but _awfully_ inconvenient when you expect more than 1 primitive typed
 * values to be returned from a method.
 * That was the reason why this _ugly_ class appeared.
 * It also wouldn't appear if JDK designers bothered to provide us
 * with Integer.set(int) method. Then I would use an object of type Integer
 * as if a pointer to pass/return int values.
 * What did they expect the class would be useful for except its static
 * methods applications????????
 */

class IntPtr
{
 public int value;

 public IntPtr() { this(0); }

 public IntPtr(int value) { this.value = value; }

}
