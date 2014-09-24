/** 
 * Copyright (c) 2003 by Bradley Keith Neuberg, bkn3@columbia.edu.
 *
 * Redistributions in source code form must reproduce the above copyright and this condition.
 *
 * The contents of this file are subject to the Sun Project JXTA License Version 1.1 (the "License"); 
 * you may not use this file except in compliance with the License. A copy of the License is available 
 * at http://www.jxta.org/jxta_license.html.
 */

package org.p2psockets;

import java.io.*;
import java.net.*;

/** This class wraps the many exceptions that can be thrown from P2PInetAddress
  * so that clients only have to deal with one type of exception.  We subclass
  * java.net.UnknownHostException so that client's who used the previous InetAddress
  * methods can use our P2PInetAddress methods without having to catch a new kind
  * of exception.
  */
public class P2PInetAddressException extends UnknownHostException {	
	private Throwable t;

	public P2PInetAddressException(Throwable t) {
		this.t = t;
	}

	public Throwable getCause() {
		return t.getCause();
	}
	public String getLocalizedMessage() {
		return t.getLocalizedMessage();
	}

	public String getMessage() {
		return t.getMessage();
	}

	public StackTraceElement[] getStackTrace() {
		return t.getStackTrace();
	}

	public Throwable initCause(Throwable cause) {
		return t.initCause(cause);
	}

	public void printStackTrace() {
		t.printStackTrace();
	}

	public void printStackTrace(PrintStream s) {
		t.printStackTrace(s);
	}

	public void printStackTrace(PrintWriter s) {
		t.printStackTrace(s);
	}

	public void setStackTrace(StackTraceElement[] stackTrace) {
		t.setStackTrace(stackTrace);
	}

	public String toString() {
		return t.toString();
	}
}