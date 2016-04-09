import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;


/**
 * @author Mebin Jacob The threads may come and go but the Peer shall maintain
 *         it's state.
 */
public class PeerThread extends Thread {
	private static final Logger LOGGER = MyLogger.getMyLogger();
	private Socket socket = null;
	private boolean isClient = false;
	private boolean stop = false;
	List<Peer> interestedList = new ArrayList<Peer>();
	private Peer p = null;

	public Peer getPeer() {
		return p;
	}

	public boolean isStop() {
		return stop;
	}

	public void setStop(boolean v) {
		stop = v;
	}

	public PeerThread(Socket s, boolean client, int id) {
		this.socket = s;
		this.isClient = client;
		p = new Peer(socket);
		if (isClient) {
			p.setId(id);
			p.setClient(true);
			p.sendHandshakeMsg();
			p.receiveHandshakeMsg();
		} else {
			int peerId = p.receiveHandshakeMsg();
			p.setId(peerId);
			p.sendHandshakeMsg();

		}
		p.sendBitfieldMsg();
		p.readBitfieldMsg();
		if (p.isInterested()) {
			p.sendInterestedMsg();
		} else {
			p.sendNotInterestedMsg();
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
		// Populate list of peers who are interested in my data..
		// all real time communication to be handled here for every peer.

		// thread ruuns till not asked to stop
		try {
			InputStream inputStream = socket.getInputStream();
			OutputStream outputStream = socket.getOutputStream();
			while (!isStop()) {
				byte[] msgBytesStat = new byte[5];
				inputStream.read(msgBytesStat);
				Constants.ActualMessageTypes msgType = MessagesUtil
						.getMsgType(msgBytesStat);
				switch (msgType) {
				case BITFIELD:break;
				case HAVE:break;
				case CHOKE:break;
				case INTERESTED: 
					Peer.interestedNeighboursinMe.add(p);
					break;
				case NOT_INTERESTED: 
					Peer.notInterestedNeighboursinMe.put(p.getId(),p);
					break;
				case PIECE:break;
				case REQUEST:break;
				case UNCHOKE:break;
				}
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
