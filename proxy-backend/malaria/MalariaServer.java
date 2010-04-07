package malaria;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MalariaServer {
	public static void main(String[] args) {
		if (args.length != 2) {
			System.out.println("Usage: java malariaserver.MalariaServer <hostname> <port>");
			System.out.println(" - hostname - hostname from which the RIA app is served");
			System.out.println(" - port     - port number the RIA app connects back to");
			System.exit(0);
		}
		int port = Integer.parseInt(args[1]);
		System.out.println("Starting listener on port " + port + " from hostname " + args[0]);
		new MalariaServer(args[0], port);
	}

	private MalariaServer(String hostname, int port) {
		System.out.println(">> Starting MalariaServer");
		try {
			SilverlightPolicyServer slServer = new SilverlightPolicyServer(hostname, port);
			FlexPolicyServer flexServer = new FlexPolicyServer(hostname, port);
			Thread slThread = new Thread(slServer);
			Thread flexThread = new Thread(flexServer);
			slThread.start();
			flexThread.start();
			
			ServerSocket clientSocket = new ServerSocket(port);
			ServerSocket proxySocket = new ServerSocket(8080);
			while(true) {
				serveSocket(clientSocket.accept(), proxySocket, hostname, port);
			}
		} catch (Exception e) {
			System.out.println(e);
		}
	}

	public void serveSocket(Socket client, ServerSocket proxySocket, String hostname, int port) {
		try {
			InputStreamReader clientIn = new InputStreamReader(client.getInputStream(), "UTF8");
			PrintStream clientOut = new PrintStream(client.getOutputStream());
			System.out.println("Client connected");
			String message = readMessage(clientIn);
			System.out.println("<- " + message);
			if (message.indexOf(PolicyServer.policyRequest) > -1) { //Flex will do this if it cannot connect to port 843
				FlexPolicyServer.printFlexPolicy(clientOut, hostname, port);
				clientOut.flush();
				client.close();
				System.out.println("Served flex policy on client socket...");
				return;
			}
			
			while (true) {
				Socket proxyClient = proxySocket.accept();
				InputStreamReader proxyIn = new InputStreamReader(proxyClient.getInputStream());
				PrintStream proxyOut = new PrintStream(proxyClient.getOutputStream());
				
				
				String proxyMessage = readMessage(proxyIn);
				Pattern hostAndAccept = Pattern.compile("(GET|POST) ([^ ]+)(.|[\\s])+Accept: ([\\S]+)(.|[\\s])+");
				Matcher m = hostAndAccept.matcher(proxyMessage);
				if (m.matches()) {
					if (isBinaryData(m)) {
						proxyOut.print("HTTP/1.1 500 OK\n");
						proxyOut.flush();
						proxyClient.close();
						System.out.println("Binary data currently not supported");
						continue;
					}
					clientOut.print(m.group(1) + " " + m.group(2) + " " + m.group(4) + "\n");
					clientOut.flush();
					System.out.println("-> " + m.group(1) + " " + m.group(2) + " " + m.group(4));
					boolean done = false;
					StringBuffer sbuffer = new StringBuffer();
					int dl = -1;
					int read = 0;
					System.out.println("Ready to read...");
					while (!done) {
						char[] buffer = new char[4096];
						int length = clientIn.read(buffer, 0, buffer.length);
						sbuffer.append(buffer, 0, length);
						if (sbuffer.toString().equals("HTTP/1.1 502 Not accessible")) {
							proxyOut.print(sbuffer.toString());
							proxyOut.flush();
							proxyClient.close();
							System.out.println("Not accessible");
							continue;
						}
						if (dl == -1) {
							String fl = sbuffer.toString().split(":", 2)[0];
							dl = Integer.parseInt(fl);
							System.out.println("DL: " + dl);
							read -= fl.length() + 1;
						}
						read += length;
						System.out.println("<- Read " + length + ":" + read + "/" + dl);
						if (read >= dl)
							done = true;
					}
					String res = sbuffer.toString();
					res = res.split(":", 2)[1];
					proxyOut.print("HTTP/1.1 200 OK\n");
					proxyOut.print("\n");
					proxyOut.print(res);
					proxyOut.flush();
				} else {
					System.out.println("No match");
					proxyOut.print("HTTP/1.1 500 OK\n");
					proxyOut.flush();
				}
				proxyClient.close();
			}
		} catch (Exception ex) {
			System.out.println("Error in communication");
			ex.printStackTrace();
		}
	}

	
	private boolean isBinaryData(Matcher m) {
		return m.group(2).matches(".*\\.(png|jpg|jpeg|gif|ico)");
	}

	private String readMessage(InputStreamReader clientReader) throws IOException {
		StringBuffer messageBuffer = new StringBuffer();
		char[] buffer = new char[4096];
		boolean done = false;
		while (!done) {
			int length = clientReader.read(buffer, 0, buffer.length);
			messageBuffer.append(buffer, 0, length);
			System.out.println("Read " + length);
			if (length < buffer.length)
				done = true;
		}
		return messageBuffer.toString();
	}

}
