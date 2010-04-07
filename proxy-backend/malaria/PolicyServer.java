package malaria;

import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;

public abstract class PolicyServer extends Thread {
	public static final String policyRequest = "<policy-file-request/>";
	protected String hostname;
	protected int port;
	private int listenerPort;
	private String name;
	
	public PolicyServer(String hostname, int port, int listenerPort, String name) {
		this.hostname = hostname;
		this.port = port;
		this.listenerPort = listenerPort;
		this.name = name;
	}

	
	public void run() {
		try {
			System.out.println(name + " starting in port " + listenerPort + " for serving policy for " + hostname + " and port " + port);
			ServerSocket socket = new ServerSocket(listenerPort);
			while(true) {
				serveConnection(socket.accept());
			}
		} catch (Exception ex) {
			System.out.println("Error starting listener socket for " + name);
			ex.printStackTrace();
		}
	}
	public void serveConnection(Socket client) {
		try {
			System.out.println(name + ">> Client connected");
	        InputStreamReader clientIn = new InputStreamReader(client.getInputStream(), "UTF8");
	        PrintStream clientOut = new PrintStream(client.getOutputStream());
	        char[] buffer = new char[4096];
	        int clength = clientIn.read(buffer, 0, buffer.length);
	        StringBuffer sbuffer = new StringBuffer();
	        sbuffer.append(buffer, 0, clength);
	        System.out.println(sbuffer.toString());
	        if (sbuffer.toString().indexOf("<policy-file-request/>") > -1) {
                printPolicy(clientOut);
	            clientOut.flush();
	            System.out.println(name + ">> Policy established");
	        } else {
	        	System.out.println("Don't understand: " + sbuffer);
	        }
	        client.close();	
		} catch(Exception ex) {
			System.out.println("Error serving client");
			ex.printStackTrace();
		}
	}

	protected abstract void printPolicy(PrintStream clientOut);
	
}
