
package net.sf.antcontrib.p2psockets;

import org.apache.tools.ant.*;

import java.io.*;
import java.util.*;
import java.text.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** This class implements a new custom tag for Ant, the &lt;release&gt; tag.  
  * This tag makes it easy for an open-source project to follow
  * the Linux release style, which is to have even version numbers on the
  * second digit indicate stable releases (1.2, 1.4, etc.) and odd version
  * numbers on the second digit indicate unstable releases (1.1, 1.3,
  * including 1.5.2 since the second digit is odd).
  *
  * To use, in your ant file add the release tag:
  *
  * &lt;release destdir="c:\\paperairplane\\www" name="paperairplane"
  *             origfile="distributable.zip" ending=".zip"/&gt;
  *				home="${p2psockets_home}"
  * 
  * When calling your Ant file, you must define the system property 'release', such as:
  * 
  * ant sometask -Drelease=1.1
  *
  * The release tag will then take the original file defined in the property <i>origfile</i>,
  * rename it to <i>name</i> plus the release value given in <i>-Drelease</i> (where all decimals
  * are changed into underscores), and add the ending <i>ending</i>.  In the example above
  * the file <i>distributable.zip</i> will be renamed to <i>paperairplane1_1.zip</i>.
  * If the shell property<i>-Ddatestamp=true</i> is true, then a date stamp will be added 
  * after the release number, such as <i>paperairplane1_1-2003-11-06.zip</i>.
  *
  * After generating this file name, the original distributable.zip will be moved over to a particular
  * directory given by <i>destdir</i>.  This task will create the following subdirectories under the directory
  * given by <i>destdir</i> if they aren't there: a <i>releases</i> directory and <i>releases/stable</i> and
  * <i>releases/unstable</i>.  This task will copy the generated file to the correct location based
  * on whether it is odd or even on the second digit.  In the example above, since the release is 1.1
  * or an unstable release, the <i>paperairplane1_1-2003-11-06.zip</i> file will be copied to
  * <i>c:\\paperairplane\\www\\releases\\unstable</i>.
  *
  * The file given in <i>origfile</i> will be removed.
  *
  * As a side effect, this task will also create a property named "distributable-cvs-filename", which can
  * be used in other ant tasks, such as checking the file into CVS.  This task returns the full file
  * location, rooted from where CVS begins.  This is calculated by removing the filename given
  * by the 'home' attribute above from the distributable's filename.  For example, if 'home' is
  * set to "c:/p2psockets" and the distributable filename generated by this Release class is 
  * 'c:/p2psockets/www/releases/unstable/p2psockets-1_1-2004-01-13.zip', then 'distributable-cvs-filename'
  * would be set to 'www/releases/unstable/p2psockets-1_1-2004-01-13.zip'.
  *
  * @author Brad Neuberg, bkn3@columbia.edu
  * @version 0,1
  */
public class Release extends Task {
	private static String RELEASE_DIR = "releases";
	private static String STABLE_DIR = RELEASE_DIR + File.separator + "stable";
	private static String UNSTABLE_DIR = RELEASE_DIR + File.separator + "unstable";
	private static String DISTRIBUTABLE_CVS_FILENAME = "distributable-cvs-filename";
	private String destdir, name, origfile, home, ending = "zip";
	private String release;
	private boolean datestamp = false;

	public void setDestdir(String destdir) {
		// convert all forward or backward slashes in our destdir to whatever
		// our platform needs
            //jxl start (the old implementation does not acount for a Unix/Linux leading slash'/'
            //now it does) 
                this.destdir = destdir.replaceAll("[\\\\/]", File.separator);

	}

	public void setName(String name) {
		this.name = name;
	}

	public void setOrigfile(String origfile) {
		this.origfile = origfile;
	}

	public void setEnding(String ending) {
		if (ending.startsWith(".")) {
			ending = ending.substring(1, ending.length());
		}
		this.ending = ending;
	}

	public void setHome(String home) {
		this.home = home;
	}

	public void execute() throws BuildException {
		try {
			// make sure we have the info we need
			checkRequiredAttributes();

			// get the properties we need
			getProperties();

			// convert all dots in release version to underscores
			formatRelease();

			// make sure the destdir and origfile both exist
			checkFileProperties();
			
			// generate our releases, releases/stable, and release/unstable directories
			// if they don't exist
                        char s = File.separatorChar;
			File releaseDir = new File(destdir +s+ RELEASE_DIR);
			File stableDir = new File(destdir +s+ STABLE_DIR);
			File unstableDir = new File(destdir +s+ UNSTABLE_DIR);
			generateReleaseDirectories(releaseDir, stableDir, unstableDir);

			String releaseFilename = getReleaseFilename(stableDir, unstableDir);

			// move the origfile to the new path and filename we have generated;
			// if an older file with the new generated name exists, remove it
			File finalSrc = new File(origfile);
			File finalDest = new File(releaseFilename);
			if (finalDest.exists()) {
				System.out.println("Removed older version of " + finalDest);
				finalDest.delete();
			}
			finalSrc.renameTo(finalDest);
			System.out.println("Moved and renamed " + finalSrc + " to " + finalDest);

			// set our 'distributable-name' property
			setCustomProperties(finalDest.toString());
		}
		catch (Exception e) {
			throw new BuildException(e);
		}
	}

	private void getProperties() throws NumberFormatException {
		Project thisProject = getProject();
		release = thisProject.getProperty("release");
		if (release == null) {
			String message = "You must provide the property 'release', such as 'release=1.1'.  " + 
							 "Either provide this on the command-line as -Drelease=1.1 or in " +
							 "a build.properties file in the same directory as build.xml";
			throw new BuildException(message);
		}

		String datestampStr = thisProject.getProperty("datestamp");
		datestamp = false;
		if (datestampStr != null) {
			datestamp = new Boolean(datestampStr).booleanValue();
		}
		String releaseType;
		if (isStableRelease()) {
			releaseType = "stable";
		}
		else {
			releaseType = "unstable";
		}
		System.out.println("Dealing with " + releaseType + " release " + release);
		System.out.println("Adding a datestamp: " + datestamp);
	}

	private void checkRequiredAttributes() throws BuildException {
		if (home == null) {
			throw new BuildException("You must provide a 'home' attribute that gives the " +
									 "home location of your project, such as c:/p2psockets");
		}
		
		if (destdir == null) {
			throw new BuildException("You must provide a 'destdir' attribute that provides " +
									 "the root of where your release, release/stable, and " +
									 "release/unstable directories are, such as " +
									 "'c:/p2psockets/www'");
		}

		if (name == null) {
			throw new BuildException("You must provide a 'name' attribute that provides " +
									 "the base filename of the release that is generated, " +
									 "such as 'paperairplane' turning into " +
									 "paperairplane-1_1.zip");
		}

		if (origfile == null) {
			throw new BuildException("You must provide an 'origfile' attribute that provides " +
									 "the original distributable file that will be moved and " +
									 "have its filename changed appropriately, such as " +
									 "'distributable.zip'");
		}
	}

	private void formatRelease() {
		StringTokenizer tk = new StringTokenizer(release, ".", true);
		StringBuffer results = new StringBuffer();
		while (tk.hasMoreTokens()) {
			String versionPiece = tk.nextToken();
			if (versionPiece.equals(".")) {
				versionPiece = "_";
			}
			results.append(versionPiece);
		}
		release = results.toString();
	}

	private void checkFileProperties() throws FileNotFoundException {
		File destdirFile = new File(destdir);
		File origfileFile = new File(origfile);
		if (destdirFile.exists() == false) {
			String message = "The file " + destdir + " given by the attribute 'destdir' " +
							 "does not exist";
			throw new BuildException(message);
		}
		if (origfileFile.exists() == false) {
			String message = "The archive file " + origfile + " given by the attribute 'origfile' " +
							 "does not exist; you must create this file before calling the <release> tag";
			throw new BuildException(message);
		}	
	}

	private void generateReleaseDirectories(File releaseDir, File stableDir, File unstableDir) 
					throws IOException, FileNotFoundException {
		if (releaseDir.exists() == false) {
			System.out.println("Creating " + releaseDir + "...");
			releaseDir.mkdir();
		}
		if (stableDir.exists() == false) {
			System.out.println("Creating " + stableDir + "...");
			stableDir.mkdir();
		}
		if (unstableDir.exists() == false) {
			System.out.println("Creating " + unstableDir + "...");
			unstableDir.mkdir();
		}
	}

	private boolean isStableRelease() throws NumberFormatException {
		// generate the release file's directory path
		// find the minor version number - first number after first dot
                Pattern p = Pattern.compile("\\d+[._](\\d+).*");
                Matcher m = p.matcher(release);
                
		
		String minorVersionStr = m.group(1);
		int minorVersion = Integer.parseInt(minorVersionStr);
		// if it is even, append stable release dir
		return (minorVersion % 2 == 0);
	}

	private String getReleaseFilename(File stableDir, File unstableDir) {
		StringBuffer releaseFilename = new StringBuffer();
		// generate the release file's filename
		// generate the first part (without the ending)
		releaseFilename.append(name + "-" + release);
		// generate a datestamp if that is desired
		if (datestamp) {
			Calendar cal = new GregorianCalendar();
			Date now = cal.getTime();
			DateFormat dateFormat = new SimpleDateFormat("-yyyy-MM-dd");
			releaseFilename.append(dateFormat.format(now));
		}
		// add the ending
		releaseFilename.append("." + ending);

		// generate the release file's directory path
		// find the minor version number - first number after first dot
		char minorVersionChar = release.charAt(release.indexOf("_") + 1);
		int minorVersion = new Integer(minorVersionChar).intValue();
		// if it is even, append stable release dir
		if (isStableRelease()) {
			releaseFilename.insert(0, stableDir + File.separator);
		}
		// if it is odd, append unstable release dir
		else {
			releaseFilename.insert(0, unstableDir + File.separator);
		}

		return releaseFilename.toString();
	}

	private void setCustomProperties(String filename) {
		if (filename.indexOf(home) != -1) {
			int amountToRemove = home.length();
			filename = filename.substring(amountToRemove);
			if (filename.startsWith(File.separator)) {
				filename = filename.substring(1);
			}
		}

		// change all back-slashes to forward slashes (needed by CVS)
		StringBuffer results = new StringBuffer();
		StringTokenizer tk = new StringTokenizer(filename, "\\", true);
		while (tk.hasMoreTokens()) {
			String token = tk.nextToken();
			if (token.equals("\\")) {
				results.append("/");
			}
			else {
				results.append(token);
			}
		}
		filename = results.toString();

		Project thisProject = getProject();
		thisProject.setProperty(DISTRIBUTABLE_CVS_FILENAME, filename);
	}
}