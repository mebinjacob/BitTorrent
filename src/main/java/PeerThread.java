import java.net.Socket;
/**
 * @author Mebin Jacob
 * The threads may come and go but the Peer shall maintain it's state. 
 */
public class PeerThread extends Thread {
	private Socket socket = null;

	public PeerThread(Socket s) {
		this.socket = s;
	}

	@Override
	public void run() {
		Peer p = new Peer(socket);
		p.sendHandshakeMsg();
	}
}
