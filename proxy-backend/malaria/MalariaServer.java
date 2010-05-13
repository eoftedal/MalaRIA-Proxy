package malaria;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
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
			new Thread(new SilverlightPolicyServer(hostname, port)).start();
			new Thread(new FlexPolicyServer(hostname, port)).start();
			
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
			System.out.println(client.getInetAddress().getHostAddress());
			PrintStream clientOut = new PrintStream(client.getOutputStream());
			InputStream clientIn = client.getInputStream();
			System.out.println("Client connected");
			String message = readMessage(new InputStreamReader(clientIn, "UTF8"));
			System.out.println("<- " + message);
			if (message.indexOf(PolicyServer.policyRequest) > -1) { //Flex will do this if it cannot connect to port 843
				FlexPolicyServer.printFlexPolicy(clientOut, hostname, port);
				clientOut.flush();
				client.close();
				System.out.println("Served flex policy on client socket...");
				return;
			}
			
			handleProxyRequests:
			while (true) {
				Socket proxyClient = proxySocket.accept();
				InputStreamReader proxyIn = new InputStreamReader(proxyClient.getInputStream());
				OutputStream proxyOut = proxyClient.getOutputStream();
				String proxyMessage = readMessage(proxyIn);
				String[] parts = parseRequest(proxyMessage);
				String clientRequest = buildClientRequest(parts);
				if (clientRequest != null) {
					clientOut.print(clientRequest + "\n");
					clientOut.flush();
					System.out.println("-> " + clientRequest);
					boolean done = false;
					int dl = -1;
					int read = 0;
					System.out.println("Waiting for response from client...");
					ArrayList<byte[]> fullBuffer = new ArrayList<byte[]>();
					while (!done) {
						byte[] buffer = new byte[4096];
						int length = clientIn.read(buffer, 0, buffer.length);
						if (new String(buffer, 0, length, "UTF8").equals("HTTP/1.1 502 Not accessible")) {
							proxyOut.write(buffer, 0, length);
							proxyOut.flush();
							proxyClient.close();
							System.out.println("Not accessible");
							continue handleProxyRequests;
						}
						if (dl == -1) {
							String fl = new String(buffer, "UTF8").toString().split(":", 2)[0];
							dl = Integer.parseInt(fl);
							System.out.println("DL: " + dl);
							read -= fl.length() + 1;
							byte[] bytes = new byte[buffer.length - fl.length() - 1];
							for(int i = fl.length() + 1; i < buffer.length; i++) {
								bytes[i - fl.length() - 1] = buffer[i];
							}
							fullBuffer.add(bytes);
						} else {
							fullBuffer.add(buffer);
						}
						read += length;
						System.out.println("<- Read " + length + ":" + read + "/" + dl);
						if (read >= dl)
							done = true;
					}
					proxyOut.write("HTTP/1.1 200 OK\r\n\r\n".getBytes("UTF8"));
					for (int i = 0; i < fullBuffer.size(); i++) {
						proxyOut.write(fullBuffer.get(i));
					}
					proxyOut.flush();
				} else {
					System.out.println("No match");
					proxyOut.write("HTTP/1.1 500 OK\n".getBytes("UTF8"));
					proxyOut.flush();
				}
				proxyClient.close();
			}
		} catch (Exception ex) {
			System.out.println("Error in communication");
			ex.printStackTrace();
		}
	}

	private String[] parseRequest(String proxyMessage) {
		ArrayList<String> parts = new ArrayList<String>();
		Pattern hostAndAccept = Pattern.compile("(GET|POST) ([^ ]+)(.|[\\s])+Accept: ([\\S]+)(.|[\\s])+");
		Matcher m = hostAndAccept.matcher(proxyMessage);
		if (!m.matches()) {
			return null;
		}
		parts.add(m.group(1));
		parts.add(m.group(2));
		parts.add(m.group(4));
		String[] headersAndData = proxyMessage.split("\r\n\r\n", 2);
		if (headersAndData.length > 1) {
			parts.add(headersAndData[1]);
		}
		return parts.toArray(new String[parts.size()]);
	}
	private String buildClientRequest(String[] parts) {
		if (parts == null) return null;
		String message = parts[0];
		for(int i = 1; i < parts.length; i++) {
			message += " " + parts[i];
		}
		return message;
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
