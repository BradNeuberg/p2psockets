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


Contributions by:

Peter Kutschera <peter@pinguin1.arcs.ac.at>

*/

package gnu.rex;

import java.util.*;


class Parser
{
 private final static char LT_01 = '?'; 
 private final static char LT_ANY = '.'; 
 private final static char LT_NSET = '^'; 
 private final static char LT_ESC = '\\';
 private final static char LT_0MAX = '*'; 
 private final static char LT_1MAX = '+'; 
 private final static char LT_DBLQ = '\"'; 
 private final static char LT_SGLQ = '\''; 
 private final static char LT_OPEN_SET = '['; 
 private final static char LT_CLOSE_SET = ']'; 
 private final static char LT_OPEN_RANGE = '{'; 
 private final static char LT_CLOSE_RANGE = '}'; 
 private final static char LT_DELIM_RANGE = ','; 
 private final static char LT_SPAN = '-';

 private final static char LT_HEX = 'x';
 private final static char LT_OCT = 'o';

 final static String LT_SUFFIX = "\\>";
 final static String LT_PREFIX = "\\<";

 static String LT_END_ONLY = "$";
 static String LT_START_ONLY = "^";
 static String LT_OPEN_SUB = "\\(";
 static String LT_CLOSE_SUB = "\\)";
 static String LT_ALTERNATIVE = "\\|";

 static String LT_WCONST = "\\W";	// [^] \t\n\r.?[{}]
 static String LT_NWCONST = "\\S";	// [] \t\n\r.?[{}]
 static String LT_LEAD_SPC = "\\B";	// ^\|[] \t\n\r.?[{}]
 static String LT_TRAIL_SPC = "\\E";	// [] \t\n\r.?[{}]\|$

 private final static int TOK_ERROR = -2;
 private final static int TOK_EOS = 1;
 private final static int TOK_N = 2;
 private final static int TOK_E = 3;
 private final static int TOK_R = 4;
 private final static int TOK_S = 5;
 private final static int TOK_O = 6;
 private final static int TOK_A = 7;
 private final static int TOK_C = 8;
 private final static int TOK_F = 9;
 private final static int TOK_P = 10;

 private final static int ST_0 = 0;
 private final static int ST_A = 1;
 private final static int ST_N = 2;
 private final static int ST_R = 3;
 private final static int ST_S = 4;
 private final static int ST_E = 5;
 private final static int ST_C = 6;
 private final static int ST_F = 7;
 private final static int ST_DONE = 8;
 private final static int ST_ERROR = -1;

 static String SPEC_CHARS_RANGE = "*+?{";
 static String SPEC_CHARS = ".*+?[{\"\'$";		// ... ()"; \( \) case
 static String SPEC_ESCS = "<>()|";			// "|";

 static boolean disabledSpecChar[] = new boolean[32];

/*
 static String SPEC_CHARS = ".*+?[{\"\'$()";		// ( ) case
 static String SPEC_ESCS = "<>|";			// ... ()";
*/

 static Atom CharClassVec[];
 static Parser staticP;

 int lastToken;
 int lastMin, lastMax;
 int lastOffset;

 Atom lastAtom;
 Expr exprList;
 String lastValue;
 char lastChar;
 StringBuffer sBuf;
 StringBuffer nBuf = new StringBuffer(12);
 String errMessage = null;

 static final String octDigs = "01234567";
 static final String decDigs = octDigs + "89";
 static final String hexDigs = decDigs + "ABCDEFabsdef";
 static final boolean NOT = true;

 static {


	staticP = new Parser();

	CharClassVec = new Atom[26];

	config_CharClass(new AtomSet(" \t\n\r.\"\'{}[]()"), 'S');
	config_CharClass(new AtomSet(" \t\n\r.\"\'{}[]()", NOT), 'W');
 }

 /*
  *	Static methods
  *	for special tokens configuration
  */

 static void addSpecEscs(char c)
	{ if(SPEC_ESCS.indexOf(c) < 0) SPEC_ESCS += c; }

 static void addSpecChrs(char c)
	{ if(SPEC_CHARS.indexOf(c) < 0) SPEC_CHARS += c; }

 static void addSpecRange(char c)
	{ if(SPEC_CHARS_RANGE.indexOf(c) < 0) SPEC_CHARS_RANGE += c; }

 static void substSpecEscs(char c, char c2)
	{ SPEC_ESCS.replace(c, c2); }

 static private String delSpec(char c, String specStr)
 {
   int off = specStr.indexOf(c);

	return specStr.substring(0,off) + specStr.substring(off+1);
 }

 static final void delSpecEscs(char c)
	{ SPEC_ESCS = delSpec(c, SPEC_ESCS); }

 static final void delSpecChrs(char c)
	{ SPEC_CHARS = delSpec(c, SPEC_CHARS); }


 static void config_GroupBraces(String o, String c)
 {
	if(o.charAt(0) == LT_ESC) {
		if(LT_OPEN_SUB.charAt(1) != o.charAt(1))
			substSpecEscs(LT_OPEN_SUB.charAt(1), o.charAt(1));
	}
	else  {
		if(LT_OPEN_SUB.charAt(0) == LT_ESC)
			delSpecEscs(LT_OPEN_SUB.charAt(1));

		addSpecChrs(o.charAt(0));
	}

	if(c.charAt(0) == LT_ESC) {
		if(LT_CLOSE_SUB.charAt(1) != c.charAt(1))
			substSpecEscs(LT_CLOSE_SUB.charAt(1), c.charAt(1));
	}
	else {
		if(LT_CLOSE_SUB.charAt(0) == LT_ESC)
			delSpecEscs(LT_CLOSE_SUB.charAt(1));

		addSpecChrs(c.charAt(0));
	}

	LT_OPEN_SUB = o;
	LT_CLOSE_SUB = c;
 }

 static void config_Alternative(String s)
 {
	if(s.charAt(0) == LT_ESC)
	{
		if(LT_ALTERNATIVE.charAt(1) != s.charAt(1))
			substSpecEscs(LT_ALTERNATIVE.charAt(1), s.charAt(1));
	}
	else {
		if(LT_ALTERNATIVE.charAt(0) == LT_ESC)
			delSpecEscs(LT_ALTERNATIVE.charAt(1));

		addSpecChrs(s.charAt(0));
	}

	LT_ALTERNATIVE = s;
 }


 static boolean config_CharClass(String set, char name)
 {
	if(!Character.isUpperCase(name)) 
		return false;

	config_CharClass(
		set.charAt(0) == '^' && set.length() > 1?
		new AtomSet(set.substring(1), NOT) :
		new AtomSet(set), name);

   return true;
 } 

 static boolean config_CharClass(Atom atom, char name)
 {
	if(!Character.isUpperCase(name)) 
		return false;

	CharClassVec[(int)name - (int)'A'] = atom;

   return true;
 }

 /*
  *	END Of Config Stuff
  */

 private Stack exprStack;
 private Vector subExpr = new Vector(10);

 private void push(Expr exp)
	{ exprStack.push((Object)exp); subExpr.addElement(exp); }

 private Expr pop()
	{ return (Expr)exprStack.pop(); }

 private Expr curExpr()
	{ return (Expr)exprStack.peek(); }

 private boolean empty()
	{ return exprStack.empty(); }

 private int specCharIx;

 private boolean specChar(char c)
 {

	if((specCharIx=SPEC_CHARS.indexOf(c)) >= 0)
	{
		if(disabledSpecChar[specCharIx])
		{
			disabledSpecChar[specCharIx] = false;
			specCharIx = -1;
		}
	}

   return specCharIx >= 0;
 }

 private boolean specCharRange(char c)
	{ return SPEC_CHARS_RANGE.indexOf(""+c) >= 0; }

 private int escCharE(char c)
 {
	if(SPEC_ESCS.indexOf(""+c) >= 0
		|| Character.isUpperCase(c)
		&& CharClassVec[(int)c - (int)'A'] != null)
		return -1;

   return (int)escChar(c);
 }

 private char isEscChar(char c)
 {

	switch(c)
	{
		case 'f' : c = '\f'; break;
		case 'n' : c = '\n'; break;
		case 'r' : c = '\r'; break;
		case 't' : c = '\t'; break;
		case 's' : c = ' '; break;
		default  : c = '0'; break;
	}

    return c;
 }

 

 private char escChar(char c)
 {

	switch(c)
	{
		case 'f' : c = '\f'; break;
		case 'n' : c = '\n'; break;
		case 'r' : c = '\r'; break;
		case 't' : c = '\t'; break;
		case 's' : c = ' '; break;
	}

    return c;
 }

 private int getCharByCode(String inp, int off) throws NumberFormatException
 {
   int radix = 10;
   String digStr = null;

	if(inp.charAt(off) == LT_HEX)
	{
		try {
			if(hexDigs.indexOf(inp.charAt(off+1)) < 0)
				return -1;
		}
		catch(StringIndexOutOfBoundsException e) {
			return -1;
		}

		radix = 16;
		digStr = hexDigs;
		++off;
	}
	else if(inp.charAt(off) == LT_OCT) {

		try {
			if(octDigs.indexOf(inp.charAt(off+1)) < 0)
				return -1;
		}
		catch(StringIndexOutOfBoundsException e) {
			return -1;
		}

		digStr = octDigs;
		radix = 8;
		++off;
	}
	else if(Character.isDigit(inp.charAt(off)))
		digStr = decDigs;
	else
		return -1;

	nBuf.setLength(0);

	try {
		for( ; digStr.indexOf(inp.charAt(off)) >= 0;
				nBuf.append(inp.charAt(off++)));
	}
	catch(StringIndexOutOfBoundsException e) {

	}
	finally {
		lastOffset = off - 1;
	}

   return Integer.parseInt(nBuf.toString(), radix);
 }


 private boolean testRepeat(String inp, int off)
	{ return (Character.isDigit(inp.charAt(off)) && (off+1>=inp.length()
		|| !Character.isDigit(inp.charAt(off+1))))? true : false; }

 private Atom backRef(int bref)
 {
	if((bref -= '0') < subExpr.size() && bref > 0)
		return new AtomBackRef((Expr)subExpr.elementAt(bref), bref);

	errMessage = "Invalid back reference: \\" + bref;

   return null;
 }

 private boolean validEndOnly(String sub)
 {
	if(sub.startsWith(LT_END_ONLY))
	{
		if(sub.length() == LT_END_ONLY.length() ||
			sub.charAt(LT_END_ONLY.length()) == LT_0MAX ||
			sub.substring(LT_END_ONLY.length()).
				startsWith(LT_CLOSE_SUB) /* ||
			sub.substring(LT_END_ONLY.length()).
				startsWith(LT_ALTERNATIVE)*/) {

				return true;
		}

		disabledSpecChar[SPEC_CHARS.indexOf(LT_END_ONLY)] = true;
	}


   return false;
 }


 private int getToken(String inp, int off)
 {
   int e_char = LT_SGLQ;
   int leng = inp.length();
   int wasToken = lastToken;
   int aux_char = -1;
   int aux_c = -1;
   boolean sBufDontTouch = false;
   boolean onceMore = false;
   boolean fallThrough = false;
   char current;

	if(off >= leng)
		return lastToken = TOK_EOS;

	if(sBuf == null)
		sBuf = new StringBuffer();

	lastValue = "";
	lastToken = TOK_N;

	current = inp.charAt(off);

	do
	{
	    onceMore = false;

	    if(fallThrough)
		current = 'A';

	    switch(current)
	    {
		case LT_ANY  :
			lastToken = TOK_A; ++off;
			lastAtom = new AtomSet(AtomSet.ANY, 1, 1);
			break;

		case LT_01   :
			++off; lastMin = 0; lastMax = 1; break;

		case LT_0MAX :
			if(lastAtom != null
				&& wasToken != TOK_S
				&& wasToken != TOK_R)
			{
				++off;
				lastMin = 0; lastMax = Atom.MAX_VALUE;
			}
			else if(!fallThrough) {
				disabledSpecChar[
					SPEC_CHARS.indexOf(LT_0MAX)] = true;

				fallThrough = onceMore = true;
			}
		break;

		case LT_1MAX :
			if(lastAtom != null
				&& wasToken != TOK_S
				&& wasToken != TOK_R)
			{
				++off;
				lastMin = 1; lastMax = Atom.MAX_VALUE;
			}
			else if(!fallThrough) {
				disabledSpecChar[
					SPEC_CHARS.indexOf(LT_1MAX)] = true;

				fallThrough = onceMore = true;
			}
		break;

		case LT_OPEN_SET   :
		try {
		   boolean positive_flag = true;

			sBuf.setLength(0);

			if(inp.charAt(++off) == LT_NSET)
			{
				++off;
				positive_flag = false;
			}

			if(inp.charAt(off) == LT_CLOSE_SET)
				sBuf.append(inp.charAt(off++));

			for( int off0 = off; inp.charAt(off) != LT_CLOSE_SET; )
			{
			    if(inp.charAt(off) == LT_ESC)
			    {
				if((aux_char=
					isEscChar(inp.charAt(++off)))=='0')
				try {
					aux_c =	getCharByCode(inp,off);

					if(aux_c >=0)
					{
						aux_char = aux_c;
						off = lastOffset;
					}
				}
				catch(NumberFormatException e) {
					return TOK_ERROR;
				}

				sBuf.append((char)aux_char);
				++off;
			    }
			    else {
				if(inp.charAt(off) == LT_SPAN)
				if(inp.charAt(off+1) != LT_CLOSE_SET
					&& off != off0) {
					int next = (int)inp.charAt(off+1);

					for(int i=(int)inp.charAt(off-1);
					      i< next; sBuf.append((char)i++));

					++off;
				}

				sBuf.append(inp.charAt(off++));
			    }
			}

			++off;

			lastAtom = new AtomSet(
				sBuf.toString(), !positive_flag);

			lastToken = TOK_A;

		}
		catch(StringIndexOutOfBoundsException e) {
			errMessage = "Unmatched [ at pos #" + off;
			lastToken = TOK_ERROR;

		   return off;
		}
		finally {
			break;
		}

		case LT_DBLQ : e_char = LT_DBLQ;
		case LT_SGLQ :

			sBuf.setLength(0);

			for( off++; off<leng && inp.charAt(off)!=e_char; off++)
			{
				if(inp.charAt(off) == LT_ESC)
				   sBuf.append(escChar(inp.charAt(++off)));
				else
					sBuf.append(inp.charAt(off));
			}

			++off;

			lastAtom = sBuf.length() == 1?
				(Atom)new AtomSet(sBuf.toString()) :
				(Atom)new AtomString(sBuf.toString());

			lastToken = TOK_A;
			break;

		case LT_OPEN_RANGE :
		{
		  int saved_off = off;

		  try {
			sBuf.setLength(0);

			for( ++off; inp.charAt(off) != LT_DELIM_RANGE &&
					inp.charAt(off) != LT_CLOSE_RANGE;
					sBuf.append(inp.charAt(off++)));

			lastMin = sBuf.length() == 0? 0 :
				Integer.parseInt(sBuf.toString());

			if(inp.charAt(off) == LT_CLOSE_RANGE)
				lastMax = lastMin;
			else {
				sBuf.setLength(0);
				for( ++off; inp.charAt(off) != LT_CLOSE_RANGE;
					sBuf.append(inp.charAt(off++)));

				lastMax = sBuf.length() == 0?
					Atom.MAX_VALUE :
					Integer.parseInt(sBuf.toString());
			}

			++off;

		     break;
		  }
		  catch(NumberFormatException e) {
			off = saved_off + 1;
			sBuf.setLength(0);
			sBuf.append(LT_OPEN_RANGE);
			sBufDontTouch = true;

			/*
			 * If we've caught the exception
			 * then fall through into 'default'.
			 */
		  }

		}

		default      :
		{
		   String sub = inp.substring(off);

			fallThrough = false;

			/*
			 * The literals those might be longer than
			 * one char are parsed here
			 */

			if(validEndOnly(sub))
			{
				lastToken = TOK_E;
				off += LT_END_ONLY.length();

				if(off < inp.length() &&
						inp.charAt(off) == LT_0MAX)
					++off;
			}
			else if(sub.startsWith(LT_PREFIX)) {
				lastToken = TOK_F;
				off += LT_PREFIX.length();
				lastAtom = new Anchor(Anchor.PREFIX);
			}
			else if(sub.startsWith(LT_SUFFIX)) {
				lastToken = TOK_F;
				off += LT_SUFFIX.length();
				lastAtom = new Anchor(Anchor.SUFFIX);
			}
			else if(sub.startsWith(LT_OPEN_SUB))
				{ lastToken = TOK_O;
					off += LT_OPEN_SUB.length(); }
			else if(sub.startsWith(LT_CLOSE_SUB))
				{ lastToken = TOK_C;
					off += LT_CLOSE_SUB.length(); }
			else if(sub.startsWith(LT_ALTERNATIVE))
				{ lastToken = TOK_R;
					off += LT_ALTERNATIVE.length(); }
			else if(sub.startsWith(LT_START_ONLY))
				{ lastToken = TOK_S;
					off += LT_START_ONLY.length(); }
			else try {

			   if(inp.charAt(off) == LT_ESC)
			   {
				if(Character.isUpperCase(inp.charAt(off+1)))
				{
				   Atom atom = CharClassVec
					[(int)inp.charAt(off+1) - (int)'A'];

				     if(atom != null)
				     {
					/* lastToken = (lastAtom = atom)
						instanceof ExprPrefix?
						TOK_P : TOK_A; */

						/*
						 * courtesy Peter Kutschera
						 * <peter@pinguin1.arcs.ac.at>
						 */
						lastAtom = (Atom)atom.clone();

						// instead of lastAtom = atom;

						lastToken = TOK_A;

					return off+2;
				     }
				}
				else if(testRepeat(inp, off+1)) {

					lastAtom = backRef(inp.charAt(++off));

					lastToken = lastAtom == null?
						TOK_ERROR :
						TOK_A;

				   return ++off;
				}
			   }

			   if(!sBufDontTouch)
				   sBuf.setLength(0);

			   for( ; off<leng
				&& !specChar(inp.charAt(off)); off++)
			   {
				if(inp.charAt(off) == LT_ESC)
				{
					if((aux_char=
					   escCharE(inp.charAt(++off)))<0)
						{ --off; break; }
					else if(testRepeat(inp,off)) {
						--off;
						break;
					}
					else try {
						aux_c =
						getCharByCode(inp,off);

						if(aux_c >=0)
						{
							aux_char = aux_c;
							off = lastOffset;
						}
					}
					catch(NumberFormatException e) {
						return TOK_ERROR;
					}

					sBuf.append((char)aux_char);
				}
				else
					sBuf.append(inp.charAt(off));
			   }


			   try {
				   if(sBuf.length() > 1
					&& specCharRange(inp.charAt(off)))
				   {
					/*
					 * Let the following modifier
					 * belong to the single character
					 * before it, not to the string.
					 */

					sBuf.setLength(sBuf.length()-1);
					--off;
				   }
			   }
			   catch(StringIndexOutOfBoundsException e) {}

			   lastAtom = sBuf.length() == 1?
				(Atom)new AtomSet(sBuf.toString()) :
				(Atom)new AtomString(sBuf.toString());

			   lastToken = TOK_A;

			}
			catch(StringIndexOutOfBoundsException e) {
				errMessage = "Trailing \\ at pos #" + off;
				lastToken = TOK_ERROR;

			   return off;
			}

		} break;
	    }
	}
	while(onceMore);

   return off;
 }

 static Expr parse(String inp) throws RegExprSyntaxException
 {
	staticP.errMessage = null;

	if(staticP.parse(inp,0) < 0)
		throw new RegExprSyntaxException(
			staticP.errMessage != null? staticP.errMessage :
			("Syntax error at pos#" + staticP.lastOffset));

    return staticP.exprList;
 }

 /*
  * this method implements an automaton
  * matching an LR(1) grammar. I hope to find the piece of paper
  * bearing the grammar itself, as well as the automaton transition table. :(
  */

 private int parse(String inp, int off)
 {
   int leng = inp.length();
   int state = ST_0;
   int subExprCount = 0;

	exprList = null;
	lastAtom = null;

	for( exprStack = new Stack(); state != ST_DONE; )
	{
		if((off=getToken(inp,off)) < 0)
			return TOK_ERROR;

		lastOffset = off;

		switch(lastToken)
		{
			case TOK_O : subExprCount++; break;
			case TOK_C : subExprCount--; break;
		}

		switch(state)
		{
			case ST_0 :	/* This is the start state */
			{
			  boolean add_atom = true;
			  Expr rExpr = new Expr();

				if(empty())
					exprList = rExpr;

				push(rExpr);

				switch(lastToken)
				{
					case TOK_S :
						lastAtom = new Anchor(
							Anchor.START);

					case TOK_P :
						curExpr().append(lastAtom);
						curExpr().append(
						new Anchor(Anchor.PREFIX));

						add_atom = false;
						state = ST_F; break;

					case TOK_A : state = ST_A; break;

					case TOK_E :
						lastAtom =
						new Anchor(Anchor.END);
						state = ST_E; break;

					case TOK_O :
						add_atom = false;
						state = ST_0; break;

					default : return ST_ERROR;
				}

				if(add_atom)
					curExpr().append(lastAtom);

			} break;

			case ST_A : /* this state corresponds to an atom */
			switch(lastToken)
			{
				case TOK_N :
					lastAtom.setRange(lastMin, lastMax);
					state = ST_N; break;
				case TOK_E :
					curExpr().append(
						new Anchor(Anchor.END));
					state = ST_E;
					break;

				case TOK_R : state = ST_R; break;
				case TOK_O : state = ST_0; break;

				case TOK_A :
					curExpr().append(lastAtom);
					state = ST_A;
					break;

				case TOK_P :
					curExpr().append(lastAtom);
					curExpr().append(
						new Anchor(Anchor.PREFIX));
					state = ST_F;
					break;

				case TOK_F :
					curExpr().append(lastAtom);
					state = ST_F; break;
				case TOK_EOS : state = ST_DONE; break;

				case TOK_C : state = ST_C; break;

				default : return ST_ERROR;
			} break;

			case ST_N :	/* this state denotes the range */
			switch(lastToken)
			{
				case TOK_E :
					curExpr().append(
						new Anchor(Anchor.END));
					state = ST_E; break;	
				case TOK_R : state = ST_R; break;	
				case TOK_O : state = ST_0; break;	

				case TOK_A :
					curExpr().append(lastAtom);
					state = ST_A; break;

				case TOK_P :
					curExpr().append(lastAtom);
					curExpr().append(
						new Anchor(Anchor.PREFIX));
					state = ST_F;
					break;

				case TOK_F :
					curExpr().append(lastAtom);
					state = ST_F; break;

				case TOK_C : state = ST_C; break;
				case TOK_EOS : state = ST_DONE; break;
				default : return ST_ERROR;
			} break;

			case ST_R :
			switch(lastToken)
			{
				case TOK_S :
					curExpr().appendAlt(
						new Anchor(Anchor.START),1);
					state = ST_S; break;	
				case TOK_A :
					curExpr().appendAlt(lastAtom);
					state = ST_A; break;
				case TOK_O :
					curExpr().setAltLevel(1);
					state = ST_0; break;	
				case TOK_E :
					curExpr().appendAlt(
						new Anchor(Anchor.END));
					state = ST_E;
					break;

				default : return ST_ERROR;

			} break;


			case ST_S :
			switch(lastToken)
			{
				case TOK_A :
					curExpr().append(lastAtom);
					state = ST_A; break;
				case TOK_R :
					curExpr().setAltLevel(0);
					state = ST_R; break;
				case TOK_E :
					curExpr().append(
						new Anchor(Anchor.END));

					state = ST_E; break;
				case TOK_O : state = ST_0; break;	
				default : return ST_ERROR;
			} break;

			case ST_E :
			switch(lastToken)
			{
				case TOK_R : state = ST_R; break;
				case TOK_C : state = ST_C; break;
				case TOK_EOS : state = ST_DONE; break;

				default : return ST_ERROR;
				
			} break;

			case ST_C :
			{
			   Expr lastExpr = curExpr();

				pop();

				if(!empty())
					curExpr().append(lastExpr);

				switch(lastToken)
				{
					case TOK_N :
						lastExpr.setRange(
							lastMin, lastMax);
						state = ST_N; break;

					case TOK_E :
						curExpr().append(
						     new Anchor(Anchor.END));

						state = ST_E;
						break;

					case TOK_R : state = ST_R; break;
					case TOK_O : state = ST_0; break;

					case TOK_A :
						curExpr().append(lastAtom);
						state = ST_A; break;

					case TOK_P :
						curExpr().append(lastAtom);
						curExpr().append(
						new Anchor(Anchor.PREFIX));

						state = ST_F; break;

					case TOK_F :
						curExpr().append(lastAtom);
						state = ST_F; break;

					case TOK_EOS :
						state = ST_DONE;
						break;

					case TOK_C :
						state = ST_C;
						break;

					default : return ST_ERROR;

				}
			} break;

			case ST_F :
			switch(lastToken)
			{
				case TOK_O :
					state = ST_0; break;
	
				case TOK_A :
					curExpr().append(lastAtom);
					state = ST_A; break;	

				case TOK_EOS :
					state = ST_DONE; break;

				default : return ST_ERROR;

			} break;
		}

	}

	if(subExprCount != 0)
	{
		errMessage = "Mismatched parenthesis " +
			(subExprCount >0? LT_OPEN_SUB : LT_CLOSE_SUB);

	   return -1;
	}

	exprList.setFirstAtom();

   return off;
 }

}


