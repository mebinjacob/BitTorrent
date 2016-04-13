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
					// sent initially
					// should be handled in acceptConnection
					break;
				case HAVE:
					byte[] readPieceIndexBytes = new byte[4];
					inputStream.read(readPieceIndexBytes);
					int pieceIndex = Util.byteArrayToInt(readPieceIndexBytes);
					byte[] myBitField = Peer.getMyBitField();
					byte myByte = myBitField[pieceIndex / 8];
					if ((myByte & (1 << pieceIndex << pieceIndex % 8)) != 1) {
						// I don't jhave this piece
						p.sendInterestedMsg();
						p.updateBitFieldMsg(pieceIndex);
					}
					break;
				case CHOKE:
					int requestedIndex = p.getRequestedIndex();
					// remove from requestedIndex
					Peer.removeSetBitFieldRequested(requestedIndex / 8,
							requestedIndex % 8);
					Peer.peersChokedMeMap.remove(p.getId());
					break;
				case INTERESTED:
					Peer.interestedNeighboursinMe.add(p);
					break;
				case NOT_INTERESTED:
					Peer.notInterestedNeighboursinMe.put(p.getId(), p);
					break;
				case PIECE:
					byte[] sizeByteArray = new byte[4];
					for (int i = 0; i < 4; i++) {
						sizeByteArray[i] = msgBytesStat[i];
					}
					int size = Util.byteArrayToInt(sizeByteArray);
					int index = p.getNextBitFieldIndexToRequest();
					p.sendRequestMsg(index);
					byte[] pieceIndexBytes = new byte[4];
					int startTime = (int) System.nanoTime();
					inputStream.read(pieceIndexBytes);
					int sizeOfPiece = size - 5;
					byte[] piece = new byte[sizeOfPiece];
					inputStream.read(piece);
					Long downTime = System.nanoTime() - Peer.requestTime.get(p.getId());
					
					Peer.downloadTime.put(p.getId(), downTime);
					
					int pieceI = Util.byteArrayToInt(pieceIndexBytes);
					int stdPieceSize = Integer.parseInt(Configuration
							.getComProp().get("PieceSize"));
					for (int i = 0; i < sizeOfPiece; i++) {
						Peer.dataShared[pieceI * stdPieceSize + i] = piece[i];
					}

					// TODO:// get the piece and make the file

					// send have message to rest of the peers
					for (PeerThread peerThread : peerProcess.peersTrees) {
						if (peerThread.getPeer() != p) {
							peerThread.getPeer().sendHaveMsg(pieceI);
						}
					}
					int i = p.getNextBitFieldIndexToRequest();
					if (i != -1 && !Peer.peersUnchokedMeMap.containsKey(p.getId())){
						p.sendRequestMsg(i);
					}
						
					break;
				case REQUEST:
					byte[] ind = new byte[4];
					inputStream.read(ind);
					int pIndex = Util.byteArrayToInt(ind);
					
					if (!p.isChocked()) {
						p.sendPieceMsg(pIndex);
					}
					// in request the id will be returned
					// send piece msg if in unchoked list
					break;
				case UNCHOKE:
					Peer.peersUnchokedMeMap.put(p.getId(), p);
					// request a piece that I do not have and have not requested
					// from other neighbors, selection
					// of piece should happen randomly
					int nextIndex = p.getNextBitFieldIndexToRequest();
					if (nextIndex != -1) {
						p.sendRequestMsg(nextIndex);
					}
					break;
				}
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
