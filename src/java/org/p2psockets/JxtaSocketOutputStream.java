/*
 *  $Id: JxtaSocketOutputStream.java,v 1.3 2005/08/25 16:45:53 jxlynch Exp $
 *
 *  Copyright (c) 2001 Sun Microsystems, Inc.  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1. Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the
 *  distribution.
 *
 *  3. The end-user documentation included with the redistribution,
 *  if any, must include the following acknowledgment:
 *  "This product includes software developed by the
 *  Sun Microsystems, Inc. for Project JXTA."
 *  Alternately, this acknowledgment may appear in the software itself,
 *  if and wherever such third-party acknowledgments normally appear.
 *
 *  4. The names "Sun", "Sun Microsystems, Inc.", "JXTA" and "Project JXTA" must
 *  not be used to endorse or promote products derived from this
 *  software without prior written permission. For written
 *  permission, please contact Project JXTA at http://www.jxta.org.
 *
 *  5. Products derived from this software may not be called "JXTA",
 *  nor may "JXTA" appear in their name, without prior written
 *  permission of Sun.
 *
 *  THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 *  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 *  ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 *  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 *  USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 *  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 *  OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 *  SUCH DAMAGE.
 *  ====================================================================
 *
 *  This software consists of voluntary contributions made by many
 *  individuals on behalf of Project JXTA.  For more
 *  information on Project JXTA, please see
 *  <http://www.jxta.org/>.
 *
 *  This license is based on the BSD license adopted by the Apache Foundation.
 *
 */
package org.p2psockets;

import net.jxta.socket.*;

import java.io.IOException;
import java.io.OutputStream;

/**
 *  This class implements a buffered output stream. By setting up such an output
 *  stream, an application can write bytes to the underlying output stream
 *  without necessarily causing a call to the underlying system for each byte
 *  written. Data buffer is flushed to the underlaying stream, when it is full,
 *  or an explicit call to flush is made.
 *
 */
public class JxtaSocketOutputStream extends OutputStream {

    /**
     *  Data buffer
     */
    protected byte buf[];

    /**
     *  byte count in buffer
     */
    protected int count;

    /**
     *  JxtaSocket associated with this stream
     */
    protected JxtaSocket socket;

    /**
     *  Constructor for the JxtaSocketOutputStream object
     *
     *@param  socket  JxtaSocket associated with this stream
     */
    public JxtaSocketOutputStream(JxtaSocket socket) {
        this(socket, 16384);
    }

    /**
     *  Constructor for the JxtaSocketOutputStream object
     *
     *@param  socket  JxtaSocket associated with this stream
     *@param  size    buffer size in bytes
     */
    public JxtaSocketOutputStream(JxtaSocket socket, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size <= 0");
        }
        buf = new byte[size];
        this.socket = socket;
    }

    /**
     *  Flush the internal buffer
     *
     *@exception  IOException  if an i/o error occurs
     */
    private void flushBuffer() throws IOException {
        if (count > 0) {
            // send the message
            socket.write(buf, 0, count);
            count = 0;
        }
    }

    /**
     *  {@inheritDoc}
     */
    public synchronized void write(int b) throws IOException {
        if (count >= buf.length) {
            flushBuffer();
        }
        buf[count++] = (byte) b;
    }

    /**
     *  {@inheritDoc}
     */
    public synchronized void write(byte b[], int off, int len)  throws IOException {
        int left = buf.length - count;
        if (len > left) {
            System.arraycopy(b, off, buf, count, left);
            len -= left;
            off += left;
            count += left;
            flushBuffer();
        }
        // chunk data if larger than buf.length
        while (len >= buf.length) {
            socket.write(b, off, buf.length);
            len -= buf.length;
            off += buf.length;
        }
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    /**
     *  {@inheritDoc}
     */
    public synchronized void flush() throws IOException {
        flushBuffer();
    }
    
    //jxl modification start
    public void close() throws IOException{
        super.close();
        socket.shutdownOutput(); 
        
    }
    //jxl modification end
}


