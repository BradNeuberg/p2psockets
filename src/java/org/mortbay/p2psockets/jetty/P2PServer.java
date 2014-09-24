// ========================================================================
// Copyright (c) 2002 Mort Bay Consulting (Australia) Pty. Ltd.
// $Id: P2PServer.java,v 1.1 2004/03/02 02:04:50 BradNeuberg Exp $
// ========================================================================

package org.mortbay.p2psockets.jetty;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

import org.p2psockets.*;

import org.mortbay.http.*;
import org.mortbay.jetty.*;
import org.mortbay.jetty.servlet.*;
import org.mortbay.util.*;
import org.mortbay.xml.*;


/* ------------------------------------------------------------ */
/** The Jetty HttpServer modified to work on the Jxta P2P network.
 *
 * This specialization of org.mortbay.http.HttpServer adds knowledge
 * about servlets and their specialized contexts.   It also included
 * support for initialization from xml configuration files
 * that follow the XmlConfiguration dtd.
 *
 * HttpContexts created by Server are of the type
 * org.mortbay.jetty.servlet.ServletHttpContext unless otherwise
 * specified.
 *
 * This class also provides a main() method which starts a server for
 * each config file passed on the command line.  If the system
 * property JETTY_NO_SHUTDOWN_HOOK is not set to true, then a shutdown
 * hook is thread is registered to stop these servers.   
 *
 * @see org.mortbay.xml.XmlConfiguration
 * @see org.mortbay.jetty.servlet.ServletHttpContext
 * @version $Revision: 1.1 $
 * @author Greg Wilkins (gregw)
 * @author Brad GNUberg, bkn3@columbia.edu - Added Jxta support.
 */
public class P2PServer extends Server 
{
	/** The system property name for the host name to use when starting up this Jety server.
	  * Added by bkn3@columbia.edu.
	  */
	public static String JETTY_HOST = "jetty.host";
	/** The system property name for the port to use when starting up this Jety server.
	  * Added by bkn3@columbia.edu.
	  */
	public static String JETTY_PORT = "jetty.port";

    private String _configuration;

    /* ------------------------------------------------------------ */
    /** Constructor. 
     */
    public P2PServer()
    {}
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @param configuration The filename or URL of the XML
     * configuration file.
     */
    public P2PServer(String configuration)
        throws IOException
    {
        this(Resource.newResource(configuration).getURL());
    }
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @param configuration The filename or URL of the XML
     * configuration file.
     */
    public P2PServer(Resource configuration)
        throws IOException
    {
        this(configuration.getURL());
    }
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @param configuration The filename or URL of the XML
     * configuration file.
     */
    public P2PServer(URL configuration)
        throws IOException
    {
        _configuration=configuration.toString();
        try
        {
            XmlConfiguration config=new XmlConfiguration(configuration);
            config.configure(this);
        }
        catch(IOException e)
        {
            throw e;
        }
        catch(Exception e)
        {
            Code.warning(e);
            throw new IOException("Jetty configuration problem: "+e);
        }
    }
    
    /* ------------------------------------------------------------ */
    /**  Configure the server from an XML file.
     * @param configuration The filename or URL of the XML
     * configuration file.
     */
    public void configure(String configuration)
        throws IOException
    {

        URL url=Resource.newResource(configuration).getURL();
        if (_configuration!=null && _configuration.equals(url.toString()))
            return;
        if (_configuration!=null)
            throw new IllegalStateException("Already configured with "+_configuration);
        try
        {
            XmlConfiguration config=new XmlConfiguration(url);
            _configuration=url.toString();
            config.configure(this);
        }
        catch(IOException e)
        {
            throw e;
        }
        catch(Exception e)
        {
            Code.warning(e);
            throw new IOException("Jetty configuration problem: "+e);
        }
    }
    
    /* ------------------------------------------------------------ */
    public String getConfiguration()
    {
        return _configuration;
    }
    
    /* ------------------------------------------------------------ */
    /** Create a new ServletHttpContext.
     * Ths method is called by HttpServer to creat new contexts.  Thus
     * calls to addContext or getContext that result in a new Context
     * being created will return an
     * org.mortbay.jetty.servlet.ServletHttpContext instance.
     * @param contextPathSpec 
     * @return ServletHttpContext
     */
    protected HttpContext newHttpContext()
    {
        return new ServletHttpContext();
    }


    
    /* ------------------------------------------------------------ */
    /** Add Web Application.
     * @param contextPathSpec The context path spec. Which must be of
     * the form / or /path/*
     * @param webApp The Web application directory or WAR file.
     * @return The WebApplicationContext
     * @exception IOException 
     */
    public WebApplicationContext addWebApplication(String contextPathSpec,
                                                   String webApp)
        throws IOException
    {
        return addWebApplication(null,contextPathSpec,webApp);
    }
    
    /* ------------------------------------------------------------ */
    /** Add Web Application.
     * @param virtualHost Virtual host name or null
     * @param contextPathSpec The context path spec. Which must be of
     * the form / or /path/*
     * @param webApp The Web application directory or WAR file.
     * @return The WebApplicationContext
     * @exception IOException 
     */
    public WebApplicationContext addWebApplication(String virtualHost,
                                                   String contextPathSpec,
                                                   String webApp)
        throws IOException
    {
        WebApplicationContext appContext =
            new WebApplicationContext(webApp);
        appContext.setContextPath(contextPathSpec);
        addContext(virtualHost,appContext);
        Code.debug("Web Application ",appContext," added");
        return appContext;
    }

    
    /* ------------------------------------------------------------ */
    /**  Add Web Applications.
     * Add auto webapplications to the server.  The name of the
     * webapp directory or war is used as the context name. If a
     * webapp is called "root" it is added at "/".
     * @param webapps Directory file name or URL to look for auto webapplication.
     * @exception IOException 
     */
    public WebApplicationContext[] addWebApplications(String webapps)
        throws IOException
    {
        return addWebApplications(null,webapps,null,false);
    }
    
    /* ------------------------------------------------------------ */
    /**  Add Web Applications.
     * Add auto webapplications to the server.  The name of the
     * webapp directory or war is used as the context name. If a
     * webapp is called "root" it is added at "/".
     * @param host Virtual host name or null
     * @param webapps Directory file name or URL to look for auto webapplication.
     * @exception IOException 
     */
    public WebApplicationContext[] addWebApplications(String host,
                                                      String webapps)
        throws IOException
    {
        return addWebApplications(host,webapps,null,false);
    }
        
    /* ------------------------------------------------------------ */
    /**  Add Web Applications.
     * Add auto webapplications to the server.  The name of the
     * webapp directory or war is used as the context name. If a
     * webapp is called "root" it is added at "/".
     * @param host Virtual host name or null
     * @param webapps Directory file name or URL to look for auto
     * webapplication.
     * @param extract If true, extract war files
     * @exception IOException 
     */
    public WebApplicationContext[] addWebApplications(String host,
                                                      String webapps,
                                                      boolean extract)
        throws IOException
    {
        return addWebApplications(host,webapps,null,extract);
    }
    
    /* ------------------------------------------------------------ */
    /**  Add Web Applications.
     * Add auto webapplications to the server.  The name of the
     * webapp directory or war is used as the context name. If a
     * webapp is called "root" it is added at "/".
     * @param host Virtual host name or null
     * @param webapps Directory file name or URL to look for auto
     * webapplication.
     * @param defaults The defaults xml filename or URL which is
     * loaded before any in the web app. Must respect the web.dtd.
     * If null the default defaults file is used. If the empty string, then
     * no defaults file is used.
     * @param extract If true, extract war files
     * @exception IOException 
     */
    public WebApplicationContext[] addWebApplications(String host,
                                                      String webapps,
                                                      String defaults,
                                                      boolean extract)
        throws IOException
    {
        ArrayList wacs = new ArrayList();
        Resource r=Resource.newResource(webapps);
        if (!r.exists())
            throw new IllegalArgumentException("No such webapps resource "+r);
        
        if (!r.isDirectory())
            throw new IllegalArgumentException("Not directory webapps resource "+r);
        
        String[] files=r.list();
        
        for (int f=0;files!=null && f<files.length;f++)
        {
            String context=files[f];
            
            if (context.equalsIgnoreCase("CVS/") ||
                context.equalsIgnoreCase("CVS") ||
                context.startsWith("."))
                continue;

            
            String app = r.addPath(r.encode(files[f])).toString();
            if (context.toLowerCase().endsWith(".war") ||
                context.toLowerCase().endsWith(".jar"))
                context=context.substring(0,context.length()-4);
            if (context.equalsIgnoreCase("root")||
                context.equalsIgnoreCase("root/"))
            {
                context="/";
            }
            else
                context="/"+context;

            WebApplicationContext wac= addWebApplication(host,
                                                         context,
                                                         app);
            wac.setExtractWAR(extract);
            if (defaults!=null)
            {
                if (defaults.length()==0)
                    wac.setDefaultsDescriptor(null);
                else
                    wac.setDefaultsDescriptor(defaults);
            }
            wacs.add(wac);
        }

        return (WebApplicationContext[])wacs.toArray(new WebApplicationContext[wacs.size()]);
    }

    
    /* ------------------------------------------------------------ */
    /** Add Web Application.
     * @param contextPathSpec The context path spec. Which must be of
     * the form / or /path/*
     * @param webApp The Web application directory or WAR as file or URL.
     * @param defaults The defaults xml filename or URL which is
     * loaded before any in the web app. Must respect the web.dtd.
     * Normally this is passed the file $JETTY_HOME/etc/webdefault.xml
     * @param extractWar If true, WAR files are extracted to the
     * webapp subdirectory of the contexts temporary directory.
     * @return The WebApplicationContext
     * @exception IOException 
     * @deprecated use addWebApplicaton(host,path,webapp)
     */
    public WebApplicationContext addWebApplication(String contextPathSpec,
                                                   String webApp,
                                                   String defaults,
                                                   boolean extractWar)
        throws IOException
    {
        return addWebApplication(null,
                                 contextPathSpec,
                                 webApp,
                                 defaults,
                                 extractWar);
    }
    
    
    /* ------------------------------------------------------------ */
    /**  Add Web Application.
     * @param virtualHost Virtual host name or null
     * @param contextPathSpec The context path spec. Which must be of
     * the form / or /path/*
     * @param webApp The Web application directory or WAR file.
     * @param defaults The defaults xml filename or URL which is
     * loaded before any in the web app. Must respect the web.dtd.
     * Normally this is passed the file $JETTY_HOME/etc/webdefault.xml
     * @param extractWar If true, WAR files are extracted to the
     * webapp subdirectory of the contexts temporary directory.
     * @return The WebApplicationContext
     * @exception IOException
     * @deprecated use addWebApplicaton(host,path,webapp)
     */
    public WebApplicationContext addWebApplication(String virtualHost,
                                                   String contextPathSpec,
                                                   String webApp,
                                                   String defaults,
                                                   boolean extractWar)
        throws IOException
    {
        Log.warning("DEPRECATED: use addWebApplicaton(host,path,webapp)");
        WebApplicationContext appContext =
            addWebApplication(virtualHost,contextPathSpec,webApp);
        appContext.setDefaultsDescriptor(defaults);
        appContext.setExtractWAR(extractWar);        
        return appContext;
    }

    
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    public static void main(String[] arg)
    {
		// parse command line arguments, bkn3@columbia.edu
		handleArguments(arg);
		
		// sign into the peer-to-peer network
        System.out.println("Signing into the P2P network...");
		try {
			P2PNetwork.signin();
		}
		catch (Exception e) {
			System.out.println("The following error occured while trying to sign in and initialize the Jxta network");
			e.printStackTrace();
			System.exit(1);
		}

		String[] configFilenames = getConfigurationFilenames(arg);
        String[] dftConfig={"etc/jetty.xml"};
        
        if (configFilenames.length==0)
        {
            Log.event("Using default configuration: etc/jetty.xml");
            configFilenames=dftConfig;
        }

        final Server[] servers=new P2PServer[configFilenames.length];

        // create and start the servers.
        for (int i=0;i<configFilenames.length;i++)
        {
            try
            {
                servers[i] = new P2PServer(configFilenames[i]);
                servers[i].start();

            }
            catch(Exception e)
            {
                Code.warning(e);
            }
        }

        // Create and add a shutdown hook
		// No shutdown hook for YOU! Brad Neuberg, bkn3@columbia.edu
        /*if (!Boolean.getBoolean("JETTY_NO_SHUTDOWN_HOOK"))
        {
            try
            {
                Method shutdownHook=
                    java.lang.Runtime.class
                    .getMethod("addShutdownHook",new Class[] {java.lang.Thread.class});
                Thread hook = 
                    new Thread() {
                            public void run()
                            {
                                setName("Shutdown");
                                Log.event("Shutdown hook executing");
                                for (int i=0;i<servers.length;i++)
                                {
                                    try{servers[i].stop();}
                                    catch(Exception e){Code.warning(e);}
                                }
                                
                                // Try to avoid JVM crash
                                try{Thread.sleep(1000);}
                                catch(Exception e){Code.warning(e);}
                            }
                        };
                shutdownHook.invoke(Runtime.getRuntime(),
                                    new Object[]{hook});
            }
            catch(Exception e)
            {
                Code.debug("No shutdown hook in JVM ",e);
            }
        }*/
    }

	/** Returns out if the user want's the given 'thing',
	  * such as "--help", "--serverpeer", or "--clientpeer"
	  * @argument The argument to check for, without dashes. Example "help".
	  * @author Brad GNUberg, bkn3@columbia.edu
	  */
	private static boolean userWants(String argumentName, String args[]) {
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("--" + argumentName)) {
				return true;
			}
		}

		return false;
	}

	/** Prints out the available command-line options.
	  * @author Brad GNUberg, bkn3@columbia.edu
	  */
	private static void printHelp() {
		System.out.println("This class starts the Jetty server listening on the Jxta peer-to-peer network");
		System.out.println();
		System.out.println("Options:");
		System.out.println("\t--help - print out help on using the Jetty P2P server");
		System.out.println();
		System.out.println("\t--host - use the given host name for this P2P server");
		System.out.println("\t\tExample: --host www.nike.laborpolicy");
		System.out.println();
		System.out.println("\t--port - use the given port for this P2P server");
		System.out.println("\t\tExample: --port 8080");
		System.out.println();
		System.out.println("\t--network - the peer network to sign into");
		System.out.println("\t\tExample: --network \"Test Network\"");
		System.out.println();
		System.out.println("\t--username - the JXTA username of this peer");
		System.out.println("\t\tExample: --username BradGNUberg");
		System.out.println();
		System.out.println("\t--password - the JXTA password of this peer");
		System.out.println("\t\tExample: --username mypassword");
		System.out.println();
		System.out.println("\t--config - the Jetty configuration files to use");
		System.out.println("\t\tExample: --config etc/demo.xml");
		System.out.println();
		System.out.println("\t--clientpeer - indicates that this peer is a client peer.");
		System.out.println("\t               This will use the pre-configured client in p2psockets/test/clientpeer.");
		System.out.println("\t               You do not need to give the username or password if this is used.");
		System.out.println();
		System.out.println("\t--serverpeer - indicates that this peer is a server peer.");
		System.out.println("\t               This will use the pre-configured server in p2psockets/test/serverpeer.");
		System.out.println("\t               You do not need to give the username or password if this is used.");
		System.out.println();

		System.exit(0);
	}

	/** Parses out a string argument from the command-line and uses this value
	  * to set a system property.
	  *	@argument argumentName The argument name to look for, such as "--hostname" or
	  *	"--network".
	  *	@argument systemPropertyName The system property that will be set for this argument
	  *	instead of giving it on the command-line, such as "jetty.host" or "p2psockets.network".
	  *	@argument arg The array of String args[] that were passed in on the command-line.
	  * @author Brad GNUberg, bkn3@columbia.edu
	  */
	private static void handleArgument(String argumentName, String systemPropertyName, String[] arg) {
		String argumentValue = null;

		for (int i = 0; i < arg.length; i++) {

			if (arg[i].equals(argumentName)) {

				// make sure the user actually gave an argument value
				if ( (i + 1) == arg.length ||			// no value and we are the last argument!
					 arg[i + 1].startsWith("--")) {		// no value was given, just another argument name!
					System.out.println("Please provide a value for " + argumentName);
					printHelp();
				}
				else {
					argumentValue = arg[i + 1];
				}
			}
		}

		// at this point, we either have an argument value, or the argumentName wasn't given on the
		// command-line (i.e. they want to use what the system property already is)
		if (argumentValue != null) {
			System.setProperty(systemPropertyName, argumentValue);
		}
	}

	/** Handles each of the arguments given on the command-line.
	  * @author Brad GNUberg, bkn3@columbia.edu
	  */
	private static void handleArguments(String arg[]) {
		if (userWants("help", arg)) {
			printHelp();
		}

		// these are friendly flags that do alot of the hard work of
		// specifying where to find Jxta configuration files, usernames, passwords,
		// etc.
		String peerType = null;
		if (userWants("clientpeer", arg)) {
			peerType = "clientpeer";
		}
		else if (userWants("serverpeer", arg)) {
			peerType = "serverpeer";
		}

		if (peerType != null) {
			String newArguments[] = new String[arg.length + 4];
			
			newArguments[arg.length] = "--username";
			newArguments[arg.length + 1] = peerType;

			newArguments[arg.length + 2] = "--password";
			newArguments[arg.length + 3] = peerType + "password";

			System.arraycopy(arg, 0, newArguments, 0, arg.length);
			
			arg = newArguments;

			System.setProperty(P2PNetwork.JXTA_HOME, "./../../test/" + peerType + "/.jxta");
		}

		handleArgument("--host", JETTY_HOST, arg);
		handleArgument("--port", JETTY_PORT, arg);

		handleArgument("--network", P2PNetwork.P2PSOCKETS_NETWORK, arg);
			
		handleArgument("--username", P2PNetwork.JXTA_USERNAME, arg);
		handleArgument("--password", P2PNetwork.JXTA_PASSWORD, arg);
	}

	/** Parses out the XML Jetty configuration filenames to use.
	  * @returns Array of configuration filenames, or null if none were provided.
	  * @author Brad GNUberg, bkn3@columbia.edu
	  */
	private static String[] getConfigurationFilenames(String args[]) {
		List results = new ArrayList();

		int startParsingAt = -1; // the index position we should start grabbing config filenames from
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("--config")) {
				startParsingAt = i + 1;
				break;
			}
		}

		if (startParsingAt == -1 ||							// --config not provided
			startParsingAt == args.length ||				// no value given
			args[startParsingAt].startsWith("--")) {		// no value given, just another argument name
			return null;
		}

		while (startParsingAt < args.length &&
			   args[startParsingAt].startsWith("--") == false) {
			results.add(args[startParsingAt]);
			startParsingAt++;
		}		

		if (results.size() == 0) {
			return null;
		}
		else {
			return (String[])results.toArray(new String[0]);
		}
	}
}




