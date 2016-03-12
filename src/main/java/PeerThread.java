import java.net.Socket;
import java.util.logging.Logger;

/**
 * @author Mebin Jacob The threads may come and go but the Peer shall maintain
 *         it's state.
 */
public class PeerThread extends Thread {
	private static final Logger LOGGER = Logger.getLogger(PeerThread.class
			.getName());
	private Socket socket = null;
	private boolean isClient = false;
	private Peer p = null;
	public PeerThread(Socket s, boolean client) {
		this.socket = s;
		this.isClient = client;
		p = new Peer(socket);
		p.sendHandshakeMsg();
		p.receiveHandshakeMsg();
		//should have all peerid's of receiver and sender here, hence do logging 
		String peerId = "1";
		if(isClient == true){
			LOGGER.info(Configuration.getComProp().get("peerId") + " makes a connection to Peer " + peerId);
		}
		else{
			LOGGER.info(Configuration.getComProp().get("peerId") + " is connected from " + peerId);
		}
	}

	@Override
	public void run() {
		
	}
}
