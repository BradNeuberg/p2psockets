package org.scache;

/*									 HTUU.c
**	UUENCODE AND UUDECODE
**
**  COPYRIGHT 1995 BY: MASSACHUSETTS INSTITUTE OF TECHNOLOGY (MIT), INRIA
**
** This W3C software is being provided by the copyright holders under the
** following license. By obtaining, using and/or copying this software, you
** agree that you have read, understood, and will comply with the following
** terms and conditions:
**
** Permission to use, copy, modify, and distribute this software and its
** documentation for any purpose and without fee or royalty is hereby granted,
** provided that the full text of this _NOTICE_ appears on ALLcopies of the
** software and documentation or portions thereof, including modifications,
** that you make.
**
** _THIS SOFTWARE IS PROVIDED "AS IS," AND COPYRIGHT HOLDERS MAKE NO
** REPRESENTATIONS OR WARRANTIES, EXPRESS OR IMPLIED. BY WAY OF EXAMPLE, BUT
** NOT LIMITATION, COPYRIGHT HOLDERS MAKE NO REPRESENTATIONS OR WARRANTIES OF
** MERCHANTABILITY OR FITNESS FOR ANY PARTICULAR PURPOSE OR THAT THE USE OF THE
** SOFTWARE OR DOCUMENTATION WILL NOT INFRINGE ANY THIRD PARTY PATENTS,
** COPYRIGHTS, TRADEMARKS OR OTHER RIGHTS. COPYRIGHT HOLDERS WILL BEAR NO
** LIABILITY FOR ANY USE OF THIS SOFTWARE OR DOCUMENTATION._
**
** The name and trademarks of copyright holders may NOTbe used in advertising
** or publicity pertaining to the software without specific, written prior
** permission. Title to copyright in this software and any associated
** documentation will at all times remain with copyright holders.
**
**	@(#) $Id: HTUU.java,v 1.1 2004/01/15 10:27:02 BradNeuberg Exp $
**
** ACKNOWLEDGEMENT:
**	This code is taken from rpem distribution, and was originally
**	written by Mark Riordan.
**
** AUTHORS:
**	MR	Mark Riordan	riordanmr@clvax1.cl.msu.edu
**	AL	Ari Luotonen	luotonen@dxcern.cern.ch
**     HSN      Radim Kolar     hsn@cybermail.net
**
** HISTORY:
**      Rewritten to Java                              HSN  1 Dec 1998
**	Added as part of the WWW library and edited to conform
**	with the WWW project coding standards by:	AL  5 Aug 1993
**	Originally written by:				MR 12 Aug 1990
**	Original header text:
** -------------------------------------------------------------
**  File containing routines to convert a buffer
**  of bytes to/from RFC 1113 printable encoding format.
**
**  This technique is similar to the familiar Unix uuencode
**  format in that it maps 6 binary bits to one ASCII
**  character (or more aptly, 3 binary bytes to 4 ASCII
**  characters).  However, RFC 1113 does not use the same
**  mapping to printable characters as uuencode.
**
**  Mark Riordan   12 August 1990 and 17 Feb 1991.
**  This code is hereby placed in the public domain.
** -------------------------------------------------------------
**
** BUGS:
**
**
*/

/* Library include files */
public final class HTUU
{


 private final static byte six2pr[] = {
    // 'A','B','C','D','E','F','G','H','I','J','K','L','M',
        65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77,
    // 'N','O','P','Q','R','S','T','U','V','W','X','Y','Z',
        78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90,
    // 'a','b','c','d','e','f','g','h','i','j','k','l','m',
      97,  98, 99, 100,101,102,103,104,105,106,107,108,109,
    // 'n','o','p','q','r','s','t','u','v','w','x','y','z',
      110, 111,112,113,114,115,116,117,118,119,120,121,122,
    // '0','1','2','3','4','5','6','7','8','9','+','/'
        48, 49, 50, 51, 52, 53, 54, 55, 56, 57,43,47
};

private static byte pr2six[]=new byte[256];
// unsigned

/*--- function HTUU_encode -----------------------------------------------
 *
 *   Encode a single line of binary data to a standard format that
 *   uses only printing ASCII characters (but takes up 33% more bytes).
 *
 *    Entry    bufin    points to a buffer of bytes.  If nbytes is not
 *                      a multiple of three, then the byte just beyond
 *                      the last byte in the buffer must be 0.
 *             nbytes   is the number of bytes in that buffer.
 *                      This cannot be more than 48.
 *             bufcoded points to an output buffer.  Be sure that this
 *                      can hold at least 1 + (4*nbytes)/3 characters.
 *
 *    Exit     bufcoded contains the coded line.  The first 4*nbytes/3 bytes
 *                      contain printing ASCII characters representing
 *                      those binary bytes. This may include one or
 *                      two '=' characters used as padding at the end.
 *                      The last byte is a zero byte.
 *             Returns the number of ASCII characters in "bufcoded".
 */

public final static byte[] encode (String s)
{
 return encode(s.getBytes());
}

public final static String encode_string (String s)
{
 byte ascii[]=encode(s.getBytes());
 return new String(ascii, 0, 0, ascii.length);
}

public final static byte[] encode (byte bufin[])
{
/* ENC is the basic 1 character encoding function to make a char printing */
// #define ENC(c) six2pr[c]

   int nbytes=bufin.length;
   byte bufcoded[];

   if(nbytes % 3 == 0)
    {
     bufcoded=new byte[4*nbytes/3];
    }
   else
    {
     bufcoded=new byte[ (nbytes/3+1)*4];
     byte tmp[];
     tmp=new byte[(nbytes/3+1)*3];
     System.arraycopy(bufin,0,tmp,0,nbytes);
     bufin=tmp;
    }

   // register char *outptr = bufcoded;
   int outptr=0;
   int i;

   for (i=0; i<nbytes; i += 3) {
      //*(outptr++) = ENC(*bufin >> 2);
      bufcoded[outptr++] = six2pr[ bufin[i] >>2 ];

      //*(outptr++) = ENC(((*bufin << 4) & 060) | ((bufin[1] >> 4) & 017)); /*c2*/
      bufcoded[outptr++]= six2pr [ (( bufin[i] <<4) & 060 ) | ((bufin[i+1] >>4) & 017) ];

      // *(outptr++) = ENC(((bufin[1] << 2) & 074) | ((bufin[2] >> 6) & 03));/*c3*/
      bufcoded[outptr++]= six2pr[ ((bufin[i+1]<<2) & 074) | ((bufin[i+2] >> 6) &03)   ];
      // *(outptr++) = ENC(bufin[2] & 077);         /* c4 */
      bufcoded[outptr++]= six2pr[bufin[i+2] & 077];
      //bufin += 3;
   }

   /* If nbytes was not a multiple of 3, then we have encoded too
    * many characters.  Adjust appropriately.
    */
   if(i == nbytes+1) {
      /* There were only 2 bytes in that last group */
      bufcoded[outptr-1] = 61;
   } else if(i == nbytes+2) {
      /* There was only 1 byte in that last group */
      bufcoded[outptr-1] = 61;
      bufcoded[outptr-2] = 61;
   }
   // *outptr = '\0';
  // return(outptr - bufcoded);
  //for(i=0;i<bufcoded.length;i++)
  // System.out.print((char)bufcoded[i]);
  return bufcoded;
}


}
