import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
	private boolean stop = false;
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

		// thread runs till not asked to stop
		try {
			InputStream inputStream = socket.getInputStream();
			OutputStream outputStream = socket.getOutputStream();
			while (!isStop()) {
				byte[] msgBytesStat = new byte[5];
				inputStream.read(msgBytesStat);
				Constants.ActualMessageTypes msgType = MessagesUtil
						.getMsgType(msgBytesStat);
				switch (msgType) {
				case BITFIELD:
					//sent initially
					// should be handled in acceptConnection 
					break;
				case HAVE:
					byte[] readPieceIndexBytes = new byte[4];
					inputStream.read(readPieceIndexBytes);
					int pieceIndex =  Util.byteArrayToInt(readPieceIndexBytes);
					byte[] myBitField = Peer.getMyBitField();
					byte myByte = myBitField[pieceIndex/8];
					if((myByte & (1<<pieceIndex<<pieceIndex%8)) != 1){
						// I don't jhave this piece
						p.sendInterestedMsg();
					}
					break;
				case CHOKE:
					int requestedIndex = p.getRequestedIndex();
					//remove from requestedIndex
					Peer.removeSetBitFieldRequested(requestedIndex/8, requestedIndex%8);
					p.setChoked(true);
					break;
				case INTERESTED:
					Peer.interestedNeighboursinMe.add(p);
					break;
				case NOT_INTERESTED:
					Peer.notInterestedNeighboursinMe.put(p.getId(), p);
					break;
				case PIECE:
					int index = p.getNextBitFieldIndexToRequest();
					p.sendRequestMsg(index);
					byte[] pieceIndexBytes = new byte[4];
					inputStream.read(pieceIndexBytes);
					int pieceIndex = Util.byteArrayToInt(pieceIndexBytes);
					//send have message to rest of the peers
					for (PeerThread peerThread : peerProcess.peersTrees) {
						if(peerThread.getPeer() != p){
							peerThread.getPeer().sendHaveMsg(pieceIndex);
						}
					}
					break;
				case REQUEST:
					// in request the id will be returned
					 //send piece msg if in unchoked list
					break;
				case UNCHOKE:
					p.setChoked(false);
					// request a piece that I do not have and have not requested
					// from other neighbors, selection
					// of piece should happen randomly
					int i = p.getNextBitFieldIndexToRequest();
					p.sendRequestMsg(i);
					break;
				}
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
