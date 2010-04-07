package malaria;

import java.io.PrintStream;

public class SilverlightPolicyServer extends PolicyServer {
	
	public SilverlightPolicyServer(String hostname, int port) {
		super(hostname, port, 943, "Silverlight policy server");
	}
	
	protected int getListenerPort() {
		return 943;
	}

	protected void printPolicy(PrintStream clientOut) {
        clientOut.print("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        clientOut.print("<access-policy>");
        clientOut.print("  <cross-domain-access>");
        clientOut.print("    <policy>");
        clientOut.print("      <allow-from>");
        clientOut.print("        <domain uri=\"" + hostname + "\" />");
        clientOut.print("        <domain uri=\"http://" + hostname + "\" />");
        clientOut.print("      </allow-from>");
        clientOut.print("      <grant-to>");
        clientOut.print("        <socket-resource port=\"" + port + "\" protocol=\"tcp\" />");
        clientOut.print("      </grant-to>"); 
        clientOut.print("    </policy>");
        clientOut.print("  </cross-domain-access>");
        clientOut.print("</access-policy>");
	}
}
