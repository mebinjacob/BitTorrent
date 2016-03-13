import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Scanner;

/**
 * @author Mebin Jacob
 */
// This is our client entrypoint
public class peerProcess { // naming convention violated due to project
							// requirement..
	public static void main(String[] args) {
		Scanner scan = new Scanner(System.in);
		int peerId = Integer.valueOf(args[0]);
		Configuration.getComProp().put("peerId", String.valueOf(peerId));
		scan.close();
		// create a server socket
		String string = Configuration.getPeerProp().get(peerId);
		// Initialize our own custom logger
		try {
			MyLogger.setup();
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("Problems with creating the log files");
		}
		String portNo = string.split(" ")[2];
		peerProcess p = new peerProcess();
		p.clientConnect(peerId);
		p.acceptConnection(Integer.valueOf(portNo));
	}

	/**
	 * Accepts connection for every peer in a separate thread..
	 * 
	 * @param portNumber
	 */
	public void acceptConnection(int portNumber) {
		boolean listening = true;

		try (ServerSocket serverSocket = new ServerSocket(portNumber)) {
			while (listening) {
				new PeerThread(serverSocket.accept(), false, -1).start();
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}

	/**
	 * Connects to all available clients. PeerId is self peerid as to not to
	 * connect to self.
	 */
	public void clientConnect(int peerId) {
		Map<Integer, String> peerProp = Configuration.getPeerProp();
		for (Integer s : peerProp.keySet()) {
			if (s < peerId) {
				String line = peerProp.get(s);
				String[] split = line.split(" ");
				String host = split[1];
				String port = split[2];
				String peerid = split[0];
				try {
					Socket socket = new Socket(host, Integer.parseInt(port));
					new PeerThread(socket, true, Integer.parseInt(peerid)).start();
				} catch (NumberFormatException | IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}
	}

	@Override
	public void finalize() {

	}
}
