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

 /**
  * This is a key class of the package. It allows you to invoke the tow
  * main actions: expression parsing and matching it against a string.
  * The regular expressions supported are a subset of lex plus some
  * extensions: <P>
  * <Pre>
  *  x			the character x.
  * "x"			an x, even if x is an operator.
  * \x			an x, even if x is an operator.
  * [xy]		the character x or y.
  * [x-z]		the character x, y or z.
  * [^x]		any character but x.
  * .			any character but newline.
  * ^x			an x at the beginning of a line.
  * x$			an x at the end of a line.
  * x?			an optional x.
  * x*			0 or more instances of x.
  * x+			1 or more instances of x.
  * x\|y		an x or a y.
  * \(x\)		an x matched as a subexpression.
  * x{m,n}		m through n occurences of x.
  * 
  * \n		a back reference, where n is a digit 0 through 9</Pre>
  * <Br>
  *
  * @author Stepan Sokolov
  *
  */

public class Rex
{
 private Expr expression;
 private StateMachine machine;

 private Rex(Expr expression)
	{ machine = StateMachine.buildMachine(this.expression = expression); }

 /**
  * builds an internal representation for a given regular expression and<Br>
  * returns the corresponding reg.expr. matcher object.
  * @param regExpr a string determining a valid regular expression
  * @return a regular expression object to use for reg.expr. matching
  * @exception RegExprSyntaxException
  *	if regExpr has unacceptable syntax
  */

 public static Rex build(String regExpr) throws RegExprSyntaxException
	{ return new Rex(Parser.parse(regExpr)); }

 /**
  * determines the string recognized as the alternative symbol
  * @param alt the default value is "\|"
  */

 public static final void config_Alternative(String alt)
	{ Parser.config_Alternative(alt); }

 /**
  * determines what sequences stand for the open/close subexpression symbols.
  * Note that the changes made by a call of this method affect all subsequent
  * calls of build().
  * @param openGroup the default value is "\("
  * @param closeGroup the default value is "\)"
  */

 public static void config_GroupBraces(String openGroup, String closeGroup)
	{ Parser.config_GroupBraces(openGroup, closeGroup); }

 /**
  * defines a new class of characters.<Br>
  * There are at least two default classes \W and \S. <Br>
  * \W stands for the word constituent character class and \S, <Br>
  * which is basically complimentary to \W, defines spaces and delimiters.
  *
  * @param set a sequence of characters to comprise the class.<Br>
  *	If it begins with a ^ it is interpreted as "all characters but" <Br>
  *	(see [^x-z] above ).
  * @param name a one character name for the class. Only upper case names
  * are supported in this version.
  *
  */

 public static boolean config_CharClass(String set, char name)
	{ return Parser.config_CharClass(set, name); }

 /**
  * tries to match the regular expression in a given array of characters
  * @param input	input array of characters
  * @param offset	the starting offset
  * @param limit	the ending offset
  * @return the set of pairs each representing offset and length of
  *	the corresponding subexpression. Only 9 (1 through 9) subexpressions
  *	are supported. The pair at 0 element represents the entire matched
  *	region.
  */

 public RexResult match(char[] input, int offset, int limit)
	{ return machine.search(input, offset, limit); }

 public void printStates()
	{ machine.printStates(); }

 public String toString()
	{ return expression.rexToString(); }

}
