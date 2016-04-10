import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Represents a single peer to which I am connected too for a single file..
 */
public class Peer {
	private static final Logger LOGGER = Logger
			.getLogger(Logger.GLOBAL_LOGGER_NAME);
	private boolean client = false;

	public static PriorityBlockingQueue<Peer> interestedNeighboursinMe = new PriorityBlockingQueue<Peer>(
			10, new PeerComparator<Peer>());

	public static Map<Integer, Peer> notInterestedNeighboursinMe = new ConcurrentHashMap<Integer, Peer>();

	public static Map<Integer, Peer> chockedMap = new HashMap<Integer, Peer>();

	public static Map<Integer, Peer> unchockedMap = new HashMap<Integer, Peer>();

	/**
	 * Downloading Rate from this peer. Initially set to 0.
	 */
	private int downloadingRate = 0;

	public int getDownloadingRate() {
		return downloadingRate;
	}

	public void setDownloadingRate(int d) {
		downloadingRate = d;
	}

	public void setClient(boolean v) {
		client = v;
	}

	public boolean isClient() {
		return client;
	}

	// All global variables are data representing the peer running in the
	// current machine that is shared with it's connecting peers.

	/**
	 * Map to store the peers with which handshake has been made successfully.
	 * Global entity.
	 */
	private static Map<Integer, Boolean> map = new HashMap<Integer, Boolean>();

	private HashSet<Peer> set = new HashSet<Peer>(); // Set of Peers that have
														// shown interest in my
														// data.

	private HashSet<Peer> preferredNeighbors = new HashSet<Peer>();

	private int id;

	private int requestedIndex;
	
	public void setRequestedIndex(int i){
		requestedIndex = i;
	}
	
	public int getRequestedIndex(){
		return requestedIndex;
	}
	
	public void setId(final int id) {
		this.id = id;
	}

	public int getId() {
		return id;
	}

	/**
	 * Bitfields corresponding to a data file that is being shared.
	 */
	private static byte[] mybitfield = null;

	public static byte[] getMyBitField() {
		return mybitfield;
	}

	/**
	 * Bit field already requested.
	 */
	private static byte[] bitFieldRequested = null;

	public static byte[] getBitFieldRequested() {
		return bitFieldRequested;
	}

	public static void setBitFieldRequested(int index, int i){
		synchronized (bitFieldRequested) {
			bitFieldRequested[index] |= (1<<i);
		}
	}
	
	public static void removeSetBitFieldRequested(int index, int i){
		synchronized (bitFieldRequested) {
			bitFieldRequested[index] ^= 1 << i;
		}
	}
	
	/**
	 * Peers bit field message.
	 */
	private byte[] bitFieldMsg = null;
	// static block
	static {
		String fileName = Configuration.getComProp().get("FileName");
		String fileSizeStr = Configuration.getComProp().get("FileSize");
		Integer fileSize = Integer.parseInt(fileSizeStr);
		long noOfPieces = 0;
		int pieceSize = Integer.parseInt(Configuration.getComProp().get(
				"PieceSize"));

		if (fileSize % pieceSize == 0) {
			noOfPieces = fileSize / pieceSize;
		} else {
			noOfPieces = fileSize / pieceSize + 1;
		}
		double bl = Math.ceil(noOfPieces / 8.0f);
		mybitfield = new byte[(int) bl];
		bitFieldRequested = new byte[(int) bl];
		File f = new File(fileName);
		if (f.exists()) // This peer is the original uploader..
		{
			if (f.length() != fileSize) {
				System.out.println("The file size mentioned in common cfg is "
						+ fileSize);
				System.out.println("Actual file size is " + f.length());
				System.exit(-1);
			} else {
				Arrays.fill(mybitfield, (byte) 1);
			}
		}

	}

	private Socket socket = null;
	private DataOutputStream out = null;
	private DataInputStream in = null;

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
			in = new DataInputStream(socket.getInputStream());
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
			// now send handshake to this peer
			byte[] concatenateByteArrays = Util.concatenateByteArrays(Util
					.concatenateByteArrays(Constants.HANDSHAKE_HEADER,
							Constants.ZERO_BITS), Configuration.getComProp()
					.get("peerId").getBytes());
			try {
				String s = new String(concatenateByteArrays);
				out.write(concatenateByteArrays);
				out.flush();
				map.put(id, false);
			} catch (IOException e) {
				LOGGER.severe("Send handshake failed !! " + e.getMessage());
				e.printStackTrace();
			}
		}

	}

	public int receiveHandshakeMsg() {
		try {
			byte[] b = new byte[32];
			in.read(b);
			byte[] copyOfRange = Arrays.copyOfRange(b, 28, 32);
			String peer = new String(copyOfRange);
			Integer peerId = Integer.parseInt(peer);
			System.out.println("The peer id is " + peerId);
			if (client) {
				// then verify the expected neighbour
				if (map.containsKey(peerId) && map.get(peerId) == false) {
					map.put(peerId, true);
					System.out.println("verified for id " + peerId);
				} else {
					System.out.println("Screwed!!!");
				}
			}
			return peerId;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return -1;
	}

	// Sends a Message of type Bitfiled
	public void sendBitfieldMsg() {
		try {
			byte[] actualMessage = MessagesUtil.getActualMessage(mybitfield,
					Constants.ActualMessageTypes.BITFIELD);
			out.write(actualMessage);
		} catch (IOException e) {
			System.out.println("Bitfield message sending failed!!!");
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void updateBitFieldMsg(int pieceIndex){
		bitFieldMsg[pieceIndex/8] |= (1<<(pieceIndex%8)); 
	}
	
	public void readBitfieldMsg() {
		bitFieldMsg = MessagesUtil.readActualMessage(in,
				Constants.ActualMessageTypes.BITFIELD);
	}

	public boolean isInterested() {
		// me xor peer
		// result xor peer
		// if not 0 then interested
		int i = 0;
		byte[] result = new byte[mybitfield.length];
		for (byte byt : mybitfield) {
			result[i] = (byte) (byt ^ bitFieldMsg[i]);
			i++;
		}
		i = 0;
		for (byte b : bitFieldMsg) {
			System.out.print(b + " ");
			result[i] = (byte) (result[i] ^ b);
			if (result[i] != 0) {
				return true;
			}
		}
		return false;
	}

	// Sends a Message of type Interesed
	public void sendInterestedMsg() {
		byte[] actualMessage = MessagesUtil
				.getActualMessage(Constants.ActualMessageTypes.INTERESTED);
		try {
			out.write(actualMessage);
			out.flush();

		} catch (IOException e) {
			System.out.println("io exception in reading " + e.getMessage());
			e.printStackTrace();
		}
	}

	// Sends a Message of type NotInterested
	public void sendNotInterestedMsg() {
		byte[] actualMessage = MessagesUtil
				.getActualMessage(Constants.ActualMessageTypes.NOT_INTERESTED);
		try {
			out.write(actualMessage);
			out.flush();

		} catch (IOException e) {
			System.out.println("io exception in reading " + e.getMessage());
			e.printStackTrace();
		}
	}

	// Sends a Message of type Have
	public void sendHaveMsg(int pieceIndex) {
		byte[] actualMessage = MessagesUtil
				.getActualMessage(Util.intToByteArray(pieceIndex), Constants.ActualMessageTypes.HAVE);
		try {
			out.write(actualMessage);
			out.flush();

		} catch (IOException e) {
			System.out.println("io exception in reading " + e.getMessage());
			e.printStackTrace();
		}
	}

	// Sends a Message of type Choke
	public void sendChokeMsg() {
		byte[] actualMessage = MessagesUtil
				.getActualMessage(Constants.ActualMessageTypes.CHOKE);
		try {
			out.write(actualMessage);
			out.flush();

		} catch (IOException e) {
			System.out.println("io exception in reading " + e.getMessage());
			e.printStackTrace();
		}
	}

	// Sends a Message of type UnChoke
	public void sendUnChokeMsg() {
		byte[] actualMessage = MessagesUtil
				.getActualMessage(Constants.ActualMessageTypes.UNCHOKE);
		try {
			out.write(actualMessage);
			out.flush();

		} catch (IOException e) {
			System.out.println("io exception in reading " + e.getMessage());
			e.printStackTrace();
		}
	}

	private boolean chocked;

	public void setChoked(boolean n) {
		chocked = n;
	}

	public boolean isChocked() {
		return chocked;
	}

	// Sends a Message of type Request
	public void sendRequestMsg(int pieceIndex) {
		byte[] pieceIndexByteArray = Util.intToByteArray(pieceIndex);

		byte[] actualMessage = MessagesUtil.getActualMessage(
				pieceIndexByteArray, Constants.ActualMessageTypes.REQUEST);
		try {
			out.write(actualMessage);
			out.flush();

		} catch (IOException e) {
			System.out.println("io exception in reading " + e.getMessage());
			e.printStackTrace();
		}
	}

	// Sends a Message of type Piece
	public void sendPieceMsg() {
		byte[] actualMessage = MessagesUtil
				.getActualMessage(Constants.ActualMessageTypes.PIECE);
		try {
			out.write(actualMessage);
			out.flush();

		} catch (IOException e) {
			System.out.println("io exception in reading " + e.getMessage());
			e.printStackTrace();
		}
	}

	public int getNextBitFieldIndexToRequest() {
		// request a piece that I do not have and have not requested from other
		// neighbors, selection
		// of piece should happen randomly
		byte[] bitFieldReq = getBitFieldRequested();
		byte[] notBytesIndex = new byte[bitFieldMsg.length]; // to store bytes
																// that I dont
																// have
		// determine bits I dont have.
		for (int i = 0; i < bitFieldReq.length; i++) {
			notBytesIndex[i] = (byte) ((bitFieldMsg[i] ^ bitFieldReq[i]) & bitFieldMsg[i]);
		}
		// select a random index
		// count the number of 1 bits set
		int count = 0;
		for (byte n : notBytesIndex) {
			while ((int)n != 0) {
				++count;
				n &= (n - 1);
			}
		}
		int getRandomIndex1SetIn = 0;
		int nextInt = ThreadLocalRandom.current().nextInt(0, count + 1);
		for (int index = 0; index < notBytesIndex.length; index++) {
			byte n = notBytesIndex[index];
			//iterate over bits
			for(int i=0; i<8; i++){
				if ( (int)(n & (1<<i)) != 0) {
					  //i-th bit is set
						nextInt--;
						if(nextInt == 0){
							setRequestedIndex((index*8) + i);
							setBitFieldRequested(index, i); //set the ith bit as 1
							return (index*8) + i;
						}
					}
			}
			
		}
		// send a random index
		return -1;
	}
}
