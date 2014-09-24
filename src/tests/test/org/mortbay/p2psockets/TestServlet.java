// ===========================================================================
// Copyright (c) 1996 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id: TestServlet.java,v 1.1 2004/01/15 09:35:29 BradNeuberg Exp $
// ---------------------------------------------------------------------------

package test.org.mortbay.p2psockets;

import java.net.*;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.p2psockets.*;

import org.mortbay.util.Loader;

/* ------------------------------------------------------------ */
/** Dump Servlet Request.
 * 
 */
public class TestServlet extends HttpServlet
{
    /* ------------------------------------------------------------ */
    public void doGet(HttpServletRequest sreq, HttpServletResponse sres) 
        throws ServletException, IOException
    {
		System.out.println("doGet");
		System.out.println("sreq="+sreq);
		System.out.println("sreq="+sreq);
        String info=sreq.getPathInfo();
        try
        {
            throw (Throwable)(Loader.loadClass(this.getClass(),
                                               info.substring(1)).newInstance());
        }
        catch(Throwable th)
        {
            throw new ServletException(th);
        }
        
    }

    /* ------------------------------------------------------------ */
    public String getServletInfo()
    {
        return "Test Servlet";
    }    
}
