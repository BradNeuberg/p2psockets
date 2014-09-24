/* $Id: RequestInfoExample.java,v 1.1 2004/01/15 10:19:34 BradNeuberg Exp $
 *
 */

import java.io.*;
import java.text.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;

import util.HTMLFilter;

/**
 * Example servlet showing request information.
 *
 * @author James Duncan Davidson <duncan@eng.sun.com>
 */

public class RequestInfoExample extends HttpServlet {


    ResourceBundle rb = ResourceBundle.getBundle("LocalStrings");

    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
        throws IOException, ServletException
    {
        response.setContentType("text/html");

        PrintWriter out = response.getWriter();
        out.println("<html>");
        out.println("<body>");
        out.println("<head>");

        String title = rb.getString("requestinfo.title");
        out.println("<title>" + title + "</title>");
        out.println("</head>");
        out.println("<body bgcolor=\"white\">");

        // img stuff not req'd for source code html showing
	// all links relative!

        // XXX
        // making these absolute till we work out the
        // addition of a PathInfo issue
	
        out.println("<a href=\"/examples/servlets/reqinfo.html\">");
        out.println("<img src=\"/examples/images/code.gif\" height=24 " +
                    "width=24 align=right border=0 alt=\"view code\"></a>");
        out.println("<a href=\"/examples/servlets/index.html\">");
        out.println("<img src=\"/examples/images/return.gif\" height=24 " +
                    "width=24 align=right border=0 alt=\"return\"></a>");

        out.println("<h3>" + title + "</h3>");
        out.println("<table border=0><tr><td>");
        out.println(rb.getString("requestinfo.label.method"));
        out.println("</td><td>");
        out.println(request.getMethod());
        out.println("</td></tr><tr><td>");
        out.println(rb.getString("requestinfo.label.requesturi"));
        out.println("</td><td>");        
        out.println(HTMLFilter.filter(request.getRequestURI()));
        out.println("</td></tr><tr><td>");        
        out.println(rb.getString("requestinfo.label.protocol"));
        out.println("</td><td>");        
        out.println(request.getProtocol());
        out.println("</td></tr><tr><td>");
        out.println(rb.getString("requestinfo.label.pathinfo"));
        out.println("</td><td>");        
        out.println(HTMLFilter.filter(request.getPathInfo()));
        out.println("</td></tr><tr><td>");
        out.println(rb.getString("requestinfo.label.remoteaddr"));

 	String cipherSuite=
 	    (String)request.getAttribute("javax.servlet.request.cipher_suite");
        out.println("</td><td>");                
        out.println(request.getRemoteAddr());
        out.println("</table>");

 	if(cipherSuite!=null){
 	    out.println("</td></tr><tr><td>");	
 	    out.println("SSLCipherSuite:");
 	    out.println("</td>");
 	    out.println("<td>");	    
 	    out.println(request.getAttribute("javax.servlet.request.cipher_suite"));
	    out.println("</td>");	    
 	}
	
    }

    public void doPost(HttpServletRequest request,
                      HttpServletResponse response)
        throws IOException, ServletException
    {
        doGet(request, response);
    }

}

