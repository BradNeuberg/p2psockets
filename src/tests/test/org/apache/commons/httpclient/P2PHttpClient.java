/*
 * ====================================================================
 *
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2002-2003 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "The Jakarta Project", "HttpClient", and "Apache Software
 *    Foundation" must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache"
 *    nor may "Apache" appear in their names without prior written
 *    permission of the Apache Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 * [Additional notices, if required by prior licensing conditions]
 *
 */

package test.org.apache.commons.httpclient;

import java.io.IOException;

import java.net.*;

import org.p2psockets.*;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.P2PProtocolSocketFactory;

/**
 *
 * This is a simple text mode application that demonstrates
 * how to use the Jakarta HttpClient API.  All requests are
 * made over JXTA rather than through normal IP sockets.
 *
 * @author <a href="mailto:jsdever@apache.org">Jeff Dever</a>
 * @author Ortwin Glck
 * @author Brad GNUberg, bkn3@columbia.edu - Ported to JXTA
 * @version 0.2
 */
public class P2PHttpClient
{

    public static void main(String[] args)
    {
		// initialize JXTA
		try {
			// sign in and initialize the JXTA network
			P2PNetwork.autoSignin(args[0]);
		}
		catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		// register the P2P socket protocol factory
		Protocol jxtaHttp = new Protocol( "p2phttp", new P2PProtocolSocketFactory(), 80 );
		Protocol.registerProtocol( "p2phttp", jxtaHttp );

        //create a singular HttpClient object
        HttpClient client = new HttpClient();

        //establish a connection within 50 seconds
        client.setConnectionTimeout(50000);

        String url = args[1];
        HttpMethod method = null;

        //create a method object
            method = new GetMethod(url);
            method.setFollowRedirects(true);
            method.setStrictMode(false);
        //} catch (MalformedURLException murle) {
        //    System.out.println("<url> argument '" + url
        //            + "' is not a valid URL");
        //    System.exit(-2);
        //}

        //execute the method
        String responseBody = null;
        try{
            client.executeMethod(method);
            responseBody = method.getResponseBodyAsString();
        } catch (HttpException he) {
            System.err.println("Http error connecting to '" + url + "'");
            he.printStackTrace();
	    Throwable t = he;
	    
	    while((t = t.getCause()) != null){
		System.err.println("Caused by: ");
		t.printStackTrace();
	    }
            System.exit(-4);
        } catch (IOException ioe){
            System.err.println("Unable to connect to '" + url + "'");
	    ioe.printStackTrace();
            System.exit(-3);
        }


        //write out the request headers
        System.out.println("*** Request ***");
        System.out.println("Request Path: " + method.getPath());
        System.out.println("Request Query: " + method.getQueryString());
        Header[] requestHeaders = method.getRequestHeaders();
        for (int i=0; i<requestHeaders.length; i++){
            System.out.print(requestHeaders[i]);
        }

        //write out the response headers
        System.out.println("*** Response ***");
        System.out.println("Status Line: " + method.getStatusLine());
        Header[] responseHeaders = method.getResponseHeaders();
        for (int i=0; i<responseHeaders.length; i++){
            System.out.print(responseHeaders[i]);
        }

        //write out the response body
        System.out.println("*** Response Body ***");
        System.out.println(responseBody);

        //clean up the connection resources
        method.releaseConnection();
        method.recycle();

        System.exit(0);
    }
}
