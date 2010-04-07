package malaria;

import java.io.PrintStream;


public class FlexPolicyServer extends PolicyServer {
	
	public FlexPolicyServer(String hostname, int port) {
		super(hostname, port, 843, "Flex policy server");
	}
	

	protected  void printPolicy(PrintStream clientOut) {
		printFlexPolicy(clientOut, hostname, port);
	}
	public static void printFlexPolicy(PrintStream clientOut, String hostname, int port) {
        clientOut.print("<?xml version=\"1.0\"?>\n");
        clientOut.print("<!DOCTYPE cross-domain-policy SYSTEM \"/xml/dtds/cross-domain-policy.dtd\">");
        clientOut.print("<cross-domain-policy>");
        clientOut.print("<site-control permitted-cross-domain-policies=\"master-only\"/>");
        clientOut.print("<allow-access-from domain=\"" + hostname + "\" to-ports=\"" + port + "\" />");
        clientOut.print("</cross-domain-policy>");
	}
	
}




