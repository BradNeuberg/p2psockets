/** 
 * Copyright (c) 2003 by Bradley Keith Neuberg, bkn3@columbia.edu.
 *
 * Redistributions in source code form must reproduce the above copyright and this condition.
 *
 * The contents of this file are subject to the Sun Project JXTA License Version 1.1 (the "License"); 
 * you may not use this file except in compliance with the License. A copy of the License is available 
 * at http://www.jxta.org/jxta_license.html.
 */

import java.io.InputStream;
import java.io.OutputStream;
import java.io.DataOutputStream;
import java.io.DataInputStream;

import java.net.Socket;

import org.p2psockets.P2PSocket;
import org.p2psockets.P2PNetwork;

public class ExampleClientSocket {
    public static void main(String args[]) {
      try {
          // sign into the peer-to-peer network, using the username 'clientpeer', the password 'clientpeerpassword',
          // and find a network named 'TestNetwork'
		  // Also, profile this peer and create it
		  // if it doesn't exist
          System.out.println("Signing into the P2P network..");
          P2PNetwork.signin("clientpeer", "clientpeerpassword", "TestNetwork", true);

          // create a socket to connect to the domain "www.nike.laborpolicy" on port 100
          System.out.println("Connecting to server socket at www.nike.laborpolicy:100...");
          Socket socket = new P2PSocket("www.nike.laborpolicy", 100);
          System.out.println("Connected.");

          // now communicate with this server
          DataInputStream in = new DataInputStream(socket.getInputStream());
          DataOutputStream out = new DataOutputStream(socket.getOutputStream());
          String results = in.readUTF();
          System.out.println("Message from server: " + results);
          out.writeUTF("Hello server world!");
		  out.flush();

          // shut everything down
          socket.close();
		  System.exit(0);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}