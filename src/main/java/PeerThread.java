import java.net.Socket;
import java.util.logging.Logger;

/**
 * @author Mebin Jacob The threads may come and go but the Peer shall maintain
 *         it's state.
 */
public class PeerThread extends Thread {
	private static final Logger LOGGER = MyLogger.getMyLogger();
	private Socket socket = null;
	private boolean isClient = false;
	private Peer p = null;

	public PeerThread(Socket s, boolean client, int id) {
		this.socket = s;
		this.isClient = client;
		p = new Peer(socket);
		if (isClient){
			p.setId(id);
			p.setClient(true);
			p.sendHandshakeMsg();
			p.receiveHandshakeMsg();
		}
		else{
			int peerId = p.receiveHandshakeMsg();
			p.setId(peerId);
			p.sendHandshakeMsg();
			
		}
		p.sendBitfieldMsg();
		p.readBitfieldMsg();
		if(p.isInterested()){
			p.sendInterestedMsg();
		}
		System.out.println("It comes here 3 ");
		// should have all peerid's of receiver and sender here, hence do
		// logging
		
		if (isClient == true) {
			LOGGER.info(Configuration.getComProp().get("peerId")
					+ " makes a connection to Peer " + p.getId());
		} else {
			LOGGER.info(Configuration.getComProp().get("peerId")
					+ " is connected from " + p.getId());
		}
	}

	@Override
	public void run() {

	}
}
