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

public class RexResult
{

 /*
  * Since there are only 9 back-refs \1 - \9
  * and one overall result it requires 10 place-holders.
  */

 static final int DFL_MAX_RESULTS = 10;

 int start[];
 int leng[];
 int subRes;
 private StringBuffer strBuf = new StringBuffer(64);

 RexResult() { this(DFL_MAX_RESULTS); }

 RexResult(int maxResults)
	{ start = new int[maxResults]; leng = new int[maxResults]; }

 /**
  * returns the offset into the input stream of characters
  * at which the matched string begins.
  */

 public final int offset() { return start[0]; }

 /**
  * returns length of the matched string
  */

 public final int length() { return leng[0]; }

 /**
  * returns the offset into the input stream of characters
  * at which the matched substring begins. The offset is relative
  * to the origin of the entire string that has matched a given regular
  * expression. In other words, it's relative to the offset returned by
  * offset() or offset(0).
  *
  * @param n sub-expression number
  */

 public final int offset(int n) { return n >= start.length? -1 : start[n]; }

 /**
  * returns length of a matched substring
  *
  * @param n sub-expression number
  */

 public final int length(int n) { return n >= leng.length? -1 : leng[n]; }

 final int maxResults() { return leng.length; }

 /**
  * returns the number of sub-results 
  *
  */

 public final int subResults() { return subRes; }

 public final String toString()
 {
	strBuf.setLength(0);
 	strBuf.append("@" + start[0] + ";" + leng[0]);

	if(subRes >0)
	{
		strBuf.append("{ ");

		for( int i=1; i< subRes+1; i++)
			strBuf.append(start[i] + "," + leng[i] + " ");

		strBuf.append("}");
	}

    return strBuf.toString();
 }

}


