/*
 * Launcher.java
 *
 * Created on August 28, 2005, 3:23 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package org.p2psockets.j2p;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.apache.log4j.helpers.Loader;


/**
 *
 * @author Alex Lynch (jxlynch@users.sf.net)
 */
public class Launcher {
    
    /** Creates a new instance of Launcher */
    private Launcher() {
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception{
        Thread.currentThread().setUncaughtExceptionHandler(
                new Thread.UncaughtExceptionHandler(){
            public void uncaughtException(Thread t, Throwable e){
                e.printStackTrace();
                System.exit(1);
            }
        });
	
	if(Class.forName("org.p2psockets.JxtaSocketOutputStream")!=null){
	    System.out.println("P2PSockets pressent");
	    //For some reason, I have to forcibly load these classes
	    Class.forName("org.p2psockets.JxtaSocketInputStream");
	}
        
        String className = System.getProperty("jxl.j2p.main.class");
        String jarName = System.getProperty("jxl.j2p.jar");
        String antTaskName = System.getProperty("jxl.j2p.ant.task");
        
        String antScriptPath = System.getProperty("jxl.j2p.ant.script");
        String networkName = System.getProperty("jxl.j2p.networkName");
        String hostName = System.getProperty("jxl.j2p.hostName");
        
        int count = 0;
        if(className != null)count++;
        if(jarName != null)count++;
        if(antTaskName != null)count++;
        
        if(count != 1)
            exitWithUsage();
        
        if(networkName == null){
            System.out.println("Using networkName: j2proxy");
            networkName = "j2Proxy";
        }
        
        if(hostName == null){
            System.out.println("Using randomly generated hostName");
            
        }
        
        J2PSocketImplFactory.install(hostName, networkName);
        
        if(antTaskName != null){
            List antArgs = new ArrayList();
            if(antScriptPath != null)
                antArgs.add(antScriptPath);
            antArgs.add(antTaskName);
            antArgs.addAll(Arrays.asList(args));

            String[] stringArr = new String[antArgs.size()];
            antArgs.toArray(stringArr);
            org.apache.tools.ant.Main.main(stringArr);
        }else{
            Class mainClass = null;
            if(jarName != null){
                File file = new File(jarName);
                if(!file.exists()){
                    System.out.println(jarName+" does not exist.");
                    exitWithUsage();
                }//else
                JarFile jarFile = new JarFile(file);
                Manifest m = jarFile.getManifest();
                Attributes  atts = m.getMainAttributes();
                String mainclassName = atts.getValue("Main-Class");
                
                URLClassLoader ucl = new URLClassLoader(new URL[]{file.toURI().toURL()},ClassLoader.getSystemClassLoader());//Loader.class.getClassLoader());
                mainClass = ucl.loadClass(mainclassName);
            }else{
                mainClass = Class.forName(className);
            }
            
            Method meth = mainClass.getDeclaredMethod("main", new Class[]{String[].class});
            meth.invoke(null, new Object[]{args});
        }
        
        // TODO code application logic here
    }
    
    private static void exitWithUsage(){
        System.out.println(
                "This is the j2p app launcher. Args are given as system properties: ie -Djxl...\n" +
                "Exactly one of the following must be specified:\n"+
                "jxl.j2p.main.class     =>      The main class to run\n" +
                "jxl.j2p.jar            =>      A jar with main-class in manifest\n"+
                "jxl.j2p.ant.task       =>      The name of an ant task to execute\n\n"+
                
                "The following are optional:\n"+
                "jxl.j2p.ant.script     =>      Path to ant build script, default is ./build.xml\n"+
                "jxl.j2p.networkName    =>      The virtual networkName space to use\n"+
                "jxl.j2p.hostName       =>      The virtual hostName for the peer\n\n"+
                
                "Actuall arguments to this class are passed to the main-class that is run.");
        System.exit(1);
    }
}
