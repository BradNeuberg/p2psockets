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
import java.net.ServerSocket;

import org.p2psockets.P2PServerSocket;
import org.p2psockets.P2PNetwork;

public class ExampleServerSocket {
    public static void main(String args[]) {
      try {
          // sign into the peer-to-peer network, using the username 'serverpeer', the password 'serverpeerpassword',
          // and create/find a scoped peer-to-peer network named 'TestNetwork'
		  // Also, profile this peer and create it
		  // if it doesn't exist
          System.out.println("Signing into the P2P network...");
          P2PNetwork.signin("serverpeer", "serverpeerpassword", "TestNetwork", true);

          // start a server socket for the domain "www.nike.laborpolicy" on port 100
          System.out.println("Creating server socket for www.nike.laborpolicy:100...");
          ServerSocket server = new P2PServerSocket("www.nike.laborpolicy", 100);

          // wait for a client
          System.out.println("Waiting for client...");
          Socket client = server.accept();
          System.out.println("Client Accepted.");

          // now communicate with this client
          DataInputStream in = new DataInputStream(client.getInputStream());
          DataOutputStream out = new DataOutputStream(client.getOutputStream());
          out.writeUTF("Hello client world!");
		  out.flush();

          String results = in.readUTF();
          System.out.println("Message from client: " + results);

          // shut everything down
          client.close();
          server.close();
		  System.exit(0);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}