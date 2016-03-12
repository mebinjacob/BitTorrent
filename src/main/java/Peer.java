import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Represents a single peer to which I am connected too for a single file..
 */
public class Peer {
	private static final Logger LOGGER = Logger.getLogger(Peer.class.getName());
	// All global variables are data representing the peer running in the
	// current machine that is shared with it's connecting peers.

	/**
	 * Map to store the peers with which handshake has been made successfully.
	 * Global entity.
	 */
	private static Map<Peer, Boolean> map = new HashMap<Peer, Boolean>();

	private HashSet<Peer> set = new HashSet<Peer>(); // Set of Peers that have
														// shown interest in my
														// data.

	private HashSet<Peer> preferredNeighbors = new HashSet<Peer>();

	/**
	 * Bitfields corresponding to a data file that is being shared.
	 */
	private static byte[] bitfields = null;

	// static block
	static {
		String peerId = Configuration.getComProp().get("peerId");
		File f = new File(peerId);
		long noOfPieces = 0;
		if (f.exists()) // This peer is the original uploader..
		{
			int pieceSize = Integer.parseInt(Configuration.getPeerProp().get(
					"PieceSize"));
			if (f.length() % pieceSize == 0) {
				noOfPieces = f.length() / pieceSize;
			} else {
				noOfPieces = f.length() / pieceSize + 1;
			}
		}
		double bl = Math.ceil(noOfPieces / 8.0f);
		bitfields = new byte[(int) bl];
	}

	private Socket socket = null;
	private DataOutputStream out = null;
	private BufferedReader buffReader =  null;
	/*
	 * BufferedReader buffReader = new BufferedReader(new
	 * InputStreamReader(socket.getInputStream())); ){ String input, output;
	 * while((input = buffReader.readLine()) != null){
	 * System.out.println(input); if(input.equals("Bye")) break; }
	 */

	public Peer(Socket s) {
		this.socket = s;
		try {
			out = new DataOutputStream(socket.getOutputStream());
			buffReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		} catch (IOException e) {
			System.out.println("socket exception!!!");
			e.printStackTrace();
		}
	}

	public void close() {
		try {
			socket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void finalize() {
		this.close();
	}

	public void sendHandshakeMsg() {
		synchronized (map) {
			map.put(this, false);
			// now send handshake to this peer
			byte[] concatenateByteArrays = Util.concatenateByteArrays(
					Util.concatenateByteArrays(Constants.HANDSHAKE_HEADER,
							Constants.ZERO_BITS), Configuration.getComProp()
							.get("peerId").getBytes());
			try {
				out.write(concatenateByteArrays);
			} catch (IOException e) {
				LOGGER.severe("Send handshake failed !! " + e.getMessage());
				e.printStackTrace();
			}
		}

	}

	public void receiveHandshakeMsg() {
		// TODO: check whether Peer B is the right neighbor. -- check if the
		// peerid in the receive message is same as that sent
		String input = null;
		byte[] concatenateByteArrays = Util.concatenateByteArrays(
				Util.concatenateByteArrays(Constants.HANDSHAKE_HEADER,
						Constants.ZERO_BITS), Configuration.getComProp()
						.get("peerId").getBytes());
		try {
			while((input = buffReader.readLine()) != null){
				if(input.getBytes().equals(concatenateByteArrays)){
					byte[] bytes = input.getBytes();
					byte[] peerIdBytes = new byte[4];
					System.arraycopy(bytes, 28, peerIdBytes, 0, 4);
					if(Configuration.getComProp()
						.get("peerId").getBytes().equals(peerIdBytes)){
						System.out.println("We are doing good!!!");
					}
					System.out.println("Receive handshake matched !! ");
					synchronized (map) {
						if (map.containsKey(this) && map.get(this) == false) {
							map.put(this, true);
						}
					}
				}
				else{
					System.out.println("Receive handshake screwed up !!!");
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	public void sendBitfieldMsg() {
		try {
			byte[] actualMessage = MessagesUtil.getActualMessage(bitfields,
					Constants.ActualMessageTypes.BITFIELD);
			out.write(actualMessage);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		;
	}

}
