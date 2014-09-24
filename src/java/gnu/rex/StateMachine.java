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

/*
 * Changes Protocol:
 *
 *	8/23/99, sts:
 *		the `augmentIt' flag has been introduced to
 *		overcome the one character length deficit for
 *		reg.expressions with a StateRepeater at the end
 *		and a match found at the end of input.
 *
 */

package gnu.rex;

class StateMachine
{
 static final int DEF_POSSBL	= 32;
 static final int DEF_CLOSUR	= 48;
 static final int MAX_ALTNVS	= 32;

 static final int MAXSUBLEN	= 512;

 static final char EOL_CHAR	= '\n';

 static StateAnchor	anchorStart;
 static StateAnchor	anchorEnd;
 static State		auxAccept;

 static {
	anchorStart = new StateAnchor('^');
	anchorEnd = new StateAnchor('$');
	auxAccept = new StateCntrl();
	auxAccept.setAccept();
 }

 short possible[];
 int pCount;

 short closure[];
 int cCount;

 short realPossible[];
 short shadowPossible[];
 int savedPCount;

 short realClosure[];
 short shadowClosure[];
 int savedCCount;

 private int poffset[];
 private int coffset[];

 State acceptingState;

 State[] tabState;
 int tabCount;

 short startState;
 int farthestState;

 char[] input;
 int start;

 RexResult result = new RexResult();

 int subEnd[];
 int subStart[];
 int subRepeat[];

 int resCount;

 int backRefState[];

 int backRefCount;

 boolean ignoreSubs = false;

 private StateMachine(int maxStates, boolean hasBRs)
 {
   int nRes = result.maxResults();

	realPossible = new short[DEF_POSSBL];
	realClosure = new short[DEF_CLOSUR];

	possible = realPossible;
	closure = realClosure;

	if(hasBRs)
	{
		shadowPossible = new short[DEF_POSSBL];
		shadowClosure = new short[DEF_CLOSUR];

		poffset = new int[DEF_POSSBL];
		coffset = new int[DEF_CLOSUR];

		backRefState = new int[nRes];
	}

	tabState = new State[maxStates];

	startState = State.IMPASSE;

	subRepeat = new int[nRes];
	subStart = new int[nRes];
	subEnd = new int[nRes];

	for( int i = 0; i< nRes; subStart[i] = subEnd[i] = -1, i++)
	{
		if(backRefState != null)
			backRefState[i] = -1;
	}

	backRefCount = resCount = 0;
 }

 static StateMachine buildMachine(Expr expr)
 {
   StateMachine sm = new StateMachine(expectStatesTotal(expr), true);

	sm.addState(new StateCntrl());
	sm.startState = 0;
	sm.adoptExpr(expr,0);

	(sm.acceptingState = sm.tabState[sm.tabCount-1]).setAccept();

	sm.farthestState = sm.tabCount - 1;
	sm.result.subRes = sm.resCount - 1;

	// System.out.println("st.real=" + sm.tabCount);

   return sm;
 }

 private int tmp;

 private void saveTables()
 {
	savedCCount = cCount;
	savedPCount = pCount;
	possible = shadowPossible;
	closure = shadowClosure;
 }

 private void restoreTables()
 {
	cCount = savedCCount;
	pCount = savedPCount;
	possible = realPossible;
	closure = realClosure;
 }

 private static int expectStatesTotal(Expr expr)
	{ return expectStates(expr); }

 private static int expectStates(Atom atom)
 {
	if(atom instanceof Expr)
		return expectStates((Expr)atom) + 1;
	else if(atom instanceof AtomBackRef)
		return 2;
	else if(atom instanceof Range)
		return 4 + (atom instanceof AtomString?
			((AtomString)atom).string.length : 0);
	else
		return 1;
 }

 private static int expectStates(Expr expr)
 {
   int nst = 0;

	for( Atom p = expr.getHead(); p != null; p = p.cdr())
	{
		if(p.cpr() == null)
			nst += expectStates(p);
		else {
			for( Atom pp = p; pp != null; pp = pp.cpr())
					nst += 3 + expectStates(pp);
		}
	}

	if(expr.max >= Atom.MAX_VALUE && allCanonical(expr))
	{
		return (nst<<1) + expr.min*nst;
	}

	nst *= expr.max;

	// System.out.println("st.expect: " + (nst+1));

   return nst + 1;
 }

 private static boolean allOptional(Expr expr)
 {
   Atom p = expr.getHead();

	if(p.cdr() == null)
		return false;

	for( ; p != null; p = p.cdr())
	{
		if(p instanceof Range)
			if(((Range)p).min > 0)
				break;

		if(p instanceof Expr)
		{
			if(!allOptional((Expr)p))
				break;
		}
	}

   return p == null;
 }

 /*
  * Returns true only if each and every term is either
  * <term>+, <term>*, or <term>.
  * To put it another way: if the expr doesn't produce any StateRepeater.
  */

 private static boolean allCanonical(Expr expr)
 {
   Atom p = expr.getHead();

	for( ; p != null /*&& p.cpr() == null*/; p = p.cdr())
	{
		if(p instanceof Range) {

			if(((Range)p).min > 1
				|| ((Range)p).max < Atom.MAX_VALUE
				&& ((Range)p).max != 1)
				break;
		}

		if(p instanceof Expr)
		{
			if(!allCanonical((Expr)p))
				break;
		}
	}

   return p == null;
 }

 private int adoptAtom$(Atom p, int state0)
 {
	if(p instanceof Expr)
		return adoptExprSimple((Expr)p, state0);
	else {
		adoptAtom(p, state0);

	   return tabCount - 1;
	}

 }

 private int adoptAlternative(Atom fork, int state0, int[] alts, int aCnt)
 {
   int branch1;

	if(fork.cpr() == null)
		branch1 = adoptAtom$(fork, state0);
	else {

	   int branch2;

		branch1 = addState(new StateCntrl());

		((StateCntrl)tabState[state0]).setNext0(branch1);

		alts[aCnt] = branch1 = adoptAtom$(fork, branch1);

		((StateCntrl)tabState[state0]).
			setNext1(branch2 = addState(new StateCntrl()));

		return adoptAlternative(fork.cpr(), branch2, alts, aCnt+1);
	}

	alts[aCnt] = branch1;

    return aCnt;
 }

 private int adoptAlternatives(Atom fork, int state0)
 {
   int terminators[] = new int[MAX_ALTNVS];
   int cnt = adoptAlternative(fork, state0, terminators, 0);

	while(--cnt >= 0)
		((StateCntrl)tabState[terminators[cnt]]).setNext0(tabCount-1);

   return tabCount - 1;
 }
 private int adoptExpr(Expr expr, int state0)
 {

   return expr.cpr() == null?
	adoptExprSimple(expr, state0) :
	adoptAlternatives(expr, state0);
 }

 private int adoptExprSimple(Expr expr, int state0)
 {
   int n;
   int resN = resCount;

	if(resCount < subStart.length)
	{
		subRepeat[resCount] = expr.max;
		subStart[resCount++] = state0;
	}

	if(expr.min == 1 && expr.max == expr.min)
	{
		adoptSubExpr(expr, state0);

		if(resN < subEnd.length && subEnd[resN] < 0)
			subEnd[resN] = tabCount - 1;

	  return tabCount - 1;
	}

	if((n=expr.min) > 0)
	{
		while(n-- >0)
		{
			adoptSubExpr(expr, state0);
			state0 = tabCount - 1;

                        if(resN < subEnd.length && subEnd[resN] < 0)
                                subEnd[resN] = state0;
		}
	}
	else {	/* expr.min == 0 */

		if(expr.max == 1) {

			/*
			 * (RE){0,1} case, a.k.a. (RE)?
			 */

			((StateCntrl)tabState[state0]).setNext0(tabCount);

			addState(new StateCntrl());
			adoptSubExpr(expr, tabCount - 1);
			((StateCntrl)tabState[state0]).setNext1(tabCount - 1);

			if(resN < subEnd.length && subEnd[resN] < 0)
				subEnd[resN] = tabCount - 1;

		   return tabCount - 1;
		}

	}

	if(expr.max >= Atom.MAX_VALUE && allCanonical(expr))
	{
		((StateCntrl)tabState[state0]).setNext0(tabCount);

		addState(new StateCntrl());
		adoptSubExpr(expr, n=tabCount-1, allOptional(expr));

		if(tabState[n].getNext1() == tabCount-1)
		{
			/*
			 * An important passage:
			 * if sub.expr[0] has the sub.expr[$] edge
			 * we should eliminate it or we'll be trapped
			 * in a stack overflow infinite loop.
			 */

			((StateCntrl)tabState[n]).
				setNext1(State.IMPASSE);
		}

		((StateCntrl)tabState[tabCount-1]).setNext1(n);
		((StateCntrl)tabState[tabCount-1]).setNext0(tabCount);

		if(resN < subEnd.length && subEnd[resN] < 0)
			subEnd[resN] = tabCount - 1;

		addState(new StateCntrl());
		((StateCntrl)tabState[state0]).setNext1(tabCount - 1);

	    return state0 = tabCount - 1;
	}

	if((n = expr.max - expr.min) > 0)
	{
	   int state$ = addState(new StateCntrl());

		/*
		 * Essentially it is the (RE){0,n} case.
		 */

		while(n-- >0)
		{
			adoptOptionalSubExpr(expr, state0, state$);
			state0 = tabCount - 1;
		}

		// state0 = state$;
		((StateCntrl)tabState[state$]).setNext0(tabCount - 1);
	}

	if(resN < subEnd.length && subEnd[resN] < 0)
		subEnd[resN] = state0;

   return state0;
 }

 private int adoptOptionalSubExpr(Expr expr, int state0, int state$)
 {
	((StateCntrl)tabState[state0]).setNext0(tabCount);

	addState(new StateCntrl());
	adoptSubExpr(expr, tabCount - 1, false);
	((StateCntrl)tabState[state0]).setNext1(state$);

			
   return tabCount - 1;
 }

 private int adoptSubExpr(Expr e, int state0)
	{ return adoptSubExpr(e, state0, false); }

 private int adoptSubExpr(Expr e, int state0, boolean ramus)
 {
   int state$ = state0;
   int start = State.IMPASSE;
   IntPtr stAlt = new IntPtr(State.IMPASSE);

	for(Atom p = e.getHead(); p != null; p = p.cdr())
	{
		if(p.cpr() != null)
			state$ = adoptAlternatives(p, state$);
		else if(p instanceof Expr)
			state$ = adoptExpr((Expr)p, state$);
		else if(p instanceof AtomBackRef)
			state$ = adoptBackRef((AtomBackRef)p, state$);
		else {
			if(p instanceof Anchor)
				state0 = adoptAnchor((Anchor)p, state$);
			else {
				state0 = ramus?
					adoptAtomOpt(p, state$, stAlt) :
					adoptAtom(p, state$);
			}

			state$ = tabCount - 1;
		}

		if(start == State.IMPASSE)
			start = state0;
	}

   return start;
 }

 private int addState(State state)
	{ tabState[tabCount++] = state; return tabCount-1; }

 private int adoptBackRef(AtomBackRef a, int state0)
 {
	tabState[state0] = new StateBackRef(a.refNo);

	addState(new StateCntrl());
        // ((StateBackRef)tabState[state0]).setNext0(tabCount - 1);

	backRefState[backRefCount++] = state0;

   return tabCount - 1;
 }

 private int adoptAnchor(Anchor a, int state0)
 {
   StateAnchor sa;

	switch(a.anchorType)
	{
		case Anchor.END : sa = anchorEnd; break;
		case Anchor.START : sa = anchorStart; break;
		case Anchor.SUFFIX :
		case Anchor.PREFIX :
		default : return state0;
	}

	tabState[state0] = sa;

	addState(new StateCntrl());

   return tabCount - 1;
 }

 private int adoptSet(AtomSet a, int st0)
 {
	tabState[st0] = a.neg?
		(State)new StateNSet(a.set) :
		(a.set.length() >1?
			(State)new StateSet(a.set) :
			(State)new State1(a.set.charAt(0)));

	addState(new StateCntrl());

   return tabCount - 1;
 }

 private int adoptString(AtomString a, int st0)
 {
	tabState[st0] = new State1(a.string[0]);

	for( int i = 1; i < a.string.length; i++)
		addState(new State1(a.string[i]));

	addState(new StateCntrl());

   return tabCount - (a.string.length + 1);
 }

 final int adoptAtomOnce(Atom a, int st0)
 {

	 return a instanceof AtomSet? adoptSet((AtomSet)a,st0) :
		(a instanceof AtomString? adoptString((AtomString)a,st0): -1);
 }

 private void adoptClosure(Range a, int state0, StateCntrl start)
 {
	start.setNext0(tabCount);

	adoptAtomOnce((Atom)a,tabCount++);

	((StateCntrl)tabState[tabCount-1]).setNext0(tabCount - 2);
	((StateCntrl)tabState[tabCount-1]).setNext1(tabCount);

	if(a.min == 0)
		start.setNext1(tabCount);

	addState(new StateCntrl());
 }

 private int adoptAtom(Atom a, int state0)
 {
   int start = state0;
   StateCntrl state = (StateCntrl)tabState[state0];
   Range r = (Range)a;

	if(r.min == 1 && r.max == r.min)
		return adoptAtomOnce(a, state0);

	if(r.max >= Atom.MAX_VALUE)
	{
		if(r.min == 1 || r.min == 0)
		{
			adoptClosure(r, start, state);

		  return start;
		}
	}

	if(r.min <= r.max)
	{
		state.setNext0(tabCount);
		adoptAtomOnce(a,tabCount++);

		if(r.min == 0)
		{
			if(r.max > 1)
			{
				tabState[tabCount-1] = new StateRepeater(
					start+1, tabCount, 0, r.max);

				((StateCntrl)tabState[start]).
					setNext1(tabCount);

				addState(new StateCntrl());
			}
			else {
				/*
				 * a? case, a.k.a. a{0,1}
				 */

				((StateCntrl)tabState[start]).
					setNext1(tabCount-1);
			}
		}
		else {
			tabState[tabCount-1] = new StateRepeater(
					start+1, tabCount, r.min, r.max);

			addState(new StateCntrl());
		}

	}

    return start;
 }


 private int adoptAtomOpt(Atom a, int state0, IntPtr stateAlt)
 {
   int start = state0;
   int state$;

	if(stateAlt.value == State.IMPASSE)
	{
		start = adoptAtom(a, state0);

		((StateCntrl)tabState[tabCount -
			(tabState[tabCount-2] instanceof StateCntrl? 2:1)]).
			setNext1(tabCount);

		stateAlt.value = tabCount - 1;
		addState(new StateCntrl());
		((StateCntrl)tabState[start]).setNext1(stateAlt.value);

	   return start;
	}

	if(((Range)a).max == 1)
	{
		/*
		 * a? is a special case.
		 */

		((StateCntrl)tabState[stateAlt.value]).setNext1(tabCount);
		addState(new StateCntrl());
		((StateCntrl)tabState[stateAlt.value]).setNext0(tabCount);
		stateAlt.value = tabCount - 1;

		start = adoptAtom(a, state0);
	}
	else {
		adoptAtom(a, stateAlt.value);
		((StateCntrl)tabState[stateAlt.value]).setNext1(tabCount - 1);
		stateAlt.value = tabCount - 1;
		state$ = tabCount - 2;

		start = adoptAtom(a, state0);

		((StateCntrl)tabState[state$]).setNext1(tabCount - 1);
	}

   return start;
 }

 /*
  * MATCH Methods Section
  */

 private void reset()
 {
        // reset passage counters

        for( int i = farthestState; i >=0; i--)
	{
		if(tabState[i] instanceof StateRepeater)
			tabState[i].reset();
		/*
		// So what is faster this:
		if(tabState[i] != null)
			tabState[i].reset();
		// or this:
		// try { tabState[i].reset(); }
		// catch(NullPointerException e) {}
		// ?????
		*/
	}

	cCount = 0;
	farthestState = 0;

   return ;
 }

 private boolean appendSet(short[] set, int capacity, short elem)
 {
   int i = 0;

        for( ; i< capacity && set[i] != elem; i++);

        if(i >= capacity)
        {
                set[capacity++] = elem;

          return true;
        }

   return false;
 }

 private int moveOn(int state, int offset, int lim)
 {
        if(tabState[state] instanceof StateCntrl)
        {
           State st = tabState[state];

                if(st.getNext0() != State.IMPASSE)
                        moveOn(st.getNext0(), offset, lim);

                if(st.getNext1() != State.IMPASSE)
                        moveOn(st.getNext1(), offset, lim);
	}
        else {
		if(tabState[state] instanceof StateAnchor)
		{
			if(tabState[state] == anchorStart)
			{
				if(!isBol(offset))
					return offset;

				++state;

				if(tabState[state] instanceof StateCntrl)
				{
					moveOn(state,
						input[offset], offset, lim);

				   return offset;
				}
			}
			else if(tabState[state] == anchorEnd) {
				if(isEol(offset, lim))
				{
		                        if(appendSet(possible, pCount,
							(short)(state + 1)))
        	        	                poffset[pCount++] = offset;
				}

			   return offset;
			}
		}

		if(tabState[state] instanceof StateBackRef)
		{
			if(tabState[state].canAccept())
				return offset;

			if(appendSet(possible, pCount, (short)(state+1)))
			{
			   int off = matchBackRef(
				(StateBackRef)tabState[state], offset, lim);

				if(off >= 0)
	                                poffset[pCount++] = (offset=off)+1;
			}
		}
		else if(tabState[state].hasTransitionOn(input[offset]))
			if(appendSet(possible, pCount, (short)(state+1)))
				poffset[pCount++] = offset+1;
        }

   return offset;
 }

 private int moveOn(int state, char c, int offset, int lim)
 {
        if(tabState[state] instanceof StateCntrl)
        {
           State st = tabState[state];

                if(st.getNext0() != State.IMPASSE)
                        moveOn(st.getNext0(), c, offset, lim);

                if(st.getNext1() != State.IMPASSE)
                        moveOn(st.getNext1(), c, offset, lim);
	}
        else {
		if(tabState[state] instanceof StateAnchor)
		{
			if(tabState[state] == anchorStart)
			{
				if(!isBol(offset))
					return 0;

				++state;

				if(tabState[state] instanceof StateCntrl)
				{
					if(tabState[state].canAccept())
					{
						specBol = true;

						appendSet(possible,
							pCount++,
							(short)state);
					}
					else
						moveOn(state, c, offset, lim);

				   return 0;
				}
			}
			else if(tabState[state] == anchorEnd) {
				if(isEol(offset, lim))
				{
		                        if(appendSet(possible, pCount,
							(short)(state + 1)))
        	        	                pCount++;
				}

			   return 0;
			}
		}

		if(tabState[state].hasTransitionOn(c))
			if(appendSet(possible, pCount, (short)(state+1)))
                                pCount++;
        }

   return 0;
 }

 private void moveOnEmpty(int state)
 {

        if(tabState[state] instanceof StateCntrl)
        {
           State st = tabState[state];

                if(st.getNext0() != State.IMPASSE)
                {
                        if(appendSet(closure, cCount, st.getNext0()))
                                moveOnEmpty(closure[cCount++]);
                }

                if(st.getNext1() != State.IMPASSE)
		{
                        if(appendSet(closure, cCount, st.getNext1()))
                                moveOnEmpty(closure[cCount++]);
                }
        }
 }

 private void moveOnEmpty(int state, int offset)
 {

        if(tabState[state] instanceof StateCntrl)
        {
           State st = tabState[state];

                if(st.getNext0() != State.IMPASSE)
                {
                        if(appendSet(closure, cCount, st.getNext0()))
			{
				coffset[cCount] = offset;
                                moveOnEmpty(closure[cCount++], offset);
			}
                }

                if(st.getNext1() != State.IMPASSE)
		{
                        if(appendSet(closure, cCount, st.getNext1()))
			{
				coffset[cCount] = offset;
                                moveOnEmpty(closure[cCount++], offset);
			}
                }
        }
 }

 private int acceptingState()
 {
        for( int i = 0; i < cCount; i++)
        {
                if(tabState[closure[i]].canAccept())
                        return closure[i];
        }

   return -1;
 }

 boolean augmentIt = false;

 final synchronized RexResult search(char[] inp, int at, int lim)
 {
	input = inp;
	start = at;

	result.leng[0] = backRefCount > 0?
		matchBR(at, lim, this.startState) :
		match(at, lim, this.startState);

	if(result.leng[0] >= 0)
	{
		result.start[0] = at;

		if(!ignoreSubs && resCount >0)
		{
			matchSubs(at, result.leng[0], result,
				backRefCount>0? backRefCount : 1);

			if(augmentIt)
				++result.leng[resCount-1];
		}

		result.leng[0] -= at;

		if(augmentIt && resCount > 1)
			++result.leng[0];

	   return result;
	}

   return null;
 }

 private int match(int offset, int lim, short startSt)
 {
   int i = -1;
   int accpt = -1;
   char c = (char)0;

	reset();
	pastEol = specBol = false;

	if(appendSet(closure, cCount, startSt))
		cCount++;

	moveOnEmpty(startSt);

	for( ; offset< lim; offset++)
	// for( ; offset <= lim; offset++)
	{
		c = input[offset];

                for( pCount = i = 0; i< cCount; i++)
                {
			if(farthestState < closure[i])
				farthestState = closure[i];

			if(tabState[closure[i]].canAccept())
				accpt = offset;
			else
				moveOn(closure[i], c, offset, lim);
                }

                if(pCount == 0)
                        // there's no transition to any state on c
			break;

                for( cCount = i = 0; i< pCount; i++)
                {
			tabState[possible[i]].incPass();

			if(farthestState < possible[i])
				farthestState = possible[i];

                        if(appendSet(closure, cCount, possible[i]))
                                cCount++;

			moveOnEmpty(possible[i]);
		}
	}

	if(pastEol || specBol)
	{
		--accpt;
		--offset;
	}

	if(accpt >= 0)
	{
		augmentIt = offset >= lim;

	// System.out.println("offset: " + offset + ", lim: " + lim);

	  return accpt;
	}

   return acceptingState() < 0? -1 : offset;
 }

 private int matchBR(int offset, int lim, short startSt)
 {
   int i = -1;
   int accpt = -1;
   char c = (char)0;

	reset();

	if(appendSet(closure, cCount, startSt))
		cCount++;

	moveOnEmpty(startSt, offset);

	for(;;) // ; offset< lim; offset++)
	{
		// c = input[offset];

                for( pCount = i = 0; i< cCount; i++)
                {
			if(farthestState < closure[i])
				farthestState = closure[i];

			if(tabState[closure[i]].canAccept())
				accpt = coffset[i];
			else {
				if(coffset[i] >= lim)
					break;

				moveOn(closure[i], coffset[i], lim);
			}
                }

                if(pCount == 0)
			break;

                for( cCount = i = 0; i< pCount; i++)
                {
			tabState[possible[i]].incPass();

			if(farthestState < possible[i])
				farthestState = possible[i];

			if(poffset[i] >= lim)
				break;

                        if(appendSet(closure, cCount, possible[i]))
                                coffset[cCount++] = poffset[i];

			moveOnEmpty(possible[i], poffset[i]);
		}
	}

   return accpt < 0? (acceptingState() < 0? -1 : offset) : accpt;
 }

 private int matchSubExpr(int subNo, int offset, int lim, RexResult result)
 {
   int state0 = subStart[subNo];
   int state$ = subEnd[subNo];
   State savedState = tabState[state$];
   int startPos = offset, endPos = startPos;
   int re = subRepeat[subNo];
   int auxOffset;

	for( ; re-- > 0; startPos = offset, offset = endPos = auxOffset)
	{
		if(tabState[state$].canAccept())
			auxOffset = match(offset, lim + 1, (short)state0);
		else {
			acceptingState.resetAccept();
			tabState[state$] = auxAccept;

			auxOffset = match(offset, lim, (short)state0);

			tabState[state$] = savedState;
			acceptingState.setAccept();
		}

		if(auxOffset < 0)
			break;

		if(!tabState[state$].canAccept())
			if(match(auxOffset, lim, (short)state$) < 0)
				break;

		if(auxOffset == offset)
		{
			// subRE is optional and 0 length string matched

		   break;
		}

		// System.out.println("auxOffset="+auxOffset+"; lim="+lim);
	}

	result.start[subNo] = startPos;
	result.leng[subNo] = endPos;

	// System.out.println("startPos=" + startPos + "; endPos=" + endPos);

   return endPos;
 }


 private int skipMatch(int startSt, int subNo, int offset, int lim)
 {
   int restOff = 0;
   int savedOff = 0;

	if(subStart[subNo] != startSt)
	{
	   State savedState = tabState[subStart[subNo]];

		for( int n = 0; n < lim; n++)
		{
			tabState[subStart[subNo]] = auxAccept;
			restOff = match(offset, lim - n, (short)startSt);
			tabState[subStart[subNo]] = savedState;

			if(restOff < 0)
				break;

			if(match(restOff, lim, (short)subStart[subNo]) >= 0)
				savedOff = restOff;
		}

	   return savedOff;
	}
	else
		return offset;
 }

 private int matchSubs(int at, int lim, RexResult result, int fromSub)
 {
   int offset = at;
   int restOff = 0;
   int state = this.startState;

	for( int i = fromSub; i< resCount; state = subEnd[i++])
	{
		offset = skipMatch(state, i, offset, lim);

		if((restOff=matchSubExpr(i, offset, lim, result)) >= 0)
			offset = restOff;

		if(result.start[i] >= 0)
		{
			result.leng[i] -= result.start[i];
			result.start[i] -= at;
		}
		else
			result.start[i] = -1;
	}

   return offset;
 }

 private int matchBackRef(StateBackRef st, int offset, int lim)
 {
   int n = st.backRef;
   int stop = offset;
   int retv = -1;

	st.setAccept();
	acceptingState.resetAccept();
	saveTables();

	stop = match(start, offset, this.startState);
	// System.out.println("Ciao! from/to=" + start + "/" +
	//	offset + "; stop=" + stop);

	for( ; stop >= 0 && offset >= start; )
	{
		if(matchSubs(start, stop, result, 1) < 0)
			break;

		/* System.out.println("\\" + n + ": " +
			(result.start[n]+start) + "," +
			(result.start[n]+result.leng[n]+start)); */

		if(matchBackRef(stop, lim, result.start[n]+start,
				result.start[n]+result.leng[n]+start) >= 0)
		{
			retv = stop + result.leng[n] - 1;

			/* System.out.println("qui: " + stop +
				"+" + result.leng[n]); */

		   break;
		}
		else
			stop = match(start, --offset, this.startState);
	}

	restoreTables();
	acceptingState.setAccept();
	st.resetAccept();

   return retv;
 }

 private int matchBackRef(int offset, int lim, int subOff, int subLim)
 {
	/* System.out.println("; offset=" + offset + ", lim=" + lim +
		", subOff=" + subOff + ", subLim=" + subLim); */

	for( ; offset < lim &&
		subOff < subLim &&
		input[offset] == input[subOff]; offset++, subOff++);

  return subOff >= subLim? offset : -1;
 }

 void printStates()
 {
	System.out.println("State Transition Table\nstart=" + startState);

        for( int i = 0; i < tabState.length && tabState[i] != null; i++)
		System.out.println(i + " " + tabState[i].stateToString());
 }

 /*
  * Anchors Handling
  */

 private boolean specBol = false;

 private boolean isBol(int off)
	{ return off==0 || input[off-1] == EOL_CHAR; }

 private boolean pastEol = false;

 private boolean isEol(int off, int lim)
	{ return pastEol = off+1 >= lim || input[off] == EOL_CHAR; }

}

