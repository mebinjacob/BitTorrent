import java.io.*;
import java.net.Socket;
import java.util.*;
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

    public static Map<Integer, Peer> peersChokedMeMap = new HashMap<Integer, Peer>();

    public static Map<Integer, Peer> peersUnchokedMeMap = new HashMap<Integer, Peer>();

    public static int myId = 0;
    /**
     * Map object between peer id and piece requested time.
     */
    public static Map<Integer, Long> requestTime = new HashMap<Integer, Long>();

    /**
     * Map object between peer id and download time.
     */
    public static Map<Integer, Long> downloadTime = new HashMap<Integer, Long>();

    /**
     * Downloading Rate from this peer. Initially set to 0.
     */
    private long downloadingRate = 0;

    public long getDownloadingRate() {
        return downloadingRate;
    }

    public void setDownloadingRate(long d) {
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


    private int id;

    private int requestedIndex;

    public void setRequestedIndex(int i) {
        requestedIndex = i;
    }

    public int getRequestedIndex() {
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

    public static synchronized byte[] getBitFieldRequested() {
        return bitFieldRequested;
    }

    public static void setMyBitFieldRequested(int index, int i) {
        synchronized (mybitfield) {
            mybitfield[index] |= (1 << (7-i));
        }
    }

    public static void setBitFieldRequested(int index, int i) {
        synchronized (bitFieldRequested) {
            bitFieldRequested[index] |= (1 << (7-i));
        }
    }

    public static void removeSetBitFieldRequested(int index, int i) {
        synchronized (bitFieldRequested) {
            bitFieldRequested[index] ^= 1 << i;
        }
    }

    /**
     * Peers bit field message.
     */
    private byte[] bitFieldMsg = null;

    public static byte[] dataShared = null;

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
        dataShared = new byte[Integer.parseInt(Configuration.getComProp().get("FileSize"))];
        File f = new File(fileName);
        if (f.exists()) // This peer is the original uploader..
        {
            if (f.length() != fileSize) {
                System.out.println("The file size mentioned in common cfg is "
                        + fileSize);
                System.out.println("Actual file size is " + f.length());
                System.exit(-1);
            } else {
                Arrays.fill(mybitfield, (byte) 255);
                FileInputStream fileInputStream = null;
                try {
                    fileInputStream = new FileInputStream(f);
                    fileInputStream.read(dataShared);
                    fileInputStream.close();
                } catch (FileNotFoundException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }
        }

    }

    private Socket socket = null;
    private OutputStream out = null;
    private InputStream in = null;

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

    public void updateBitFieldMsg(int pieceIndex) {
        bitFieldMsg[pieceIndex / 8] |= (1 << (pieceIndex % 8));
    }

    public void readBitfieldMsg() {
        bitFieldMsg = MessagesUtil.readActualMessage(in,
                Constants.ActualMessageTypes.BITFIELD);
    }

    public void print(String s) {
        System.out.println(s);
    }

    // TODO: Test this function
    // logger in thread
    public boolean isInterested() {
        // me xor peer
        // result & ~me
        // if not 0 then interested
        int i = 0;
        print("My bit field is " + Arrays.toString(mybitfield));
        print("Peers bit field is " + Arrays.toString(bitFieldMsg));
        byte[] result = new byte[mybitfield.length];
        for (byte byt : mybitfield) {
            result[i] = (byte) (byt ^ bitFieldMsg[i]);
            i++;
        }
        i = 0;

        for (byte b : mybitfield) {

            result[i] = (byte) (result[i] & ~b);
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
        byte[] actualMessage = MessagesUtil.getActualMessage(
                Util.intToByteArray(pieceIndex),
                Constants.ActualMessageTypes.HAVE);
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

    private boolean choked = true;

    public void setChoked(boolean n) {
        choked = n;
    }

    public boolean isChoked() {
        return choked;
    }

    // Sends a Message of type Request
    public void sendRequestMsg(int pieceIndex) {
        if (pieceIndex >= 0) {
            byte[] pieceIndexByteArray = Util.intToByteArray(pieceIndex);

            byte[] actualMessage = MessagesUtil.getActualMessage(
                    pieceIndexByteArray, Constants.ActualMessageTypes.REQUEST);
            try {
                out.write(actualMessage);
                out.flush();
                // Noting the time when the request was made
                Peer.requestTime.put(id, System.nanoTime());
            } catch (IOException e) {
                System.out.println("io exception in reading " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // Sends a Message of type Piece
    public void sendPieceMsg(int pieceIndex) {
        int pI = pieceIndex;
        int stdPieceSize = Integer.parseInt(Configuration.getComProp().get(
                "PieceSize"));
        int startIndex = stdPieceSize * pieceIndex;
        int endIndex = startIndex + stdPieceSize - 1;
        if(endIndex >= dataShared.length){
            endIndex = dataShared.length - 1;
        }
        //special case
        //if pieceSize is greater than the entire file left

        byte[] data = new byte[endIndex - startIndex + 1 + 4]; // 4 is for pieceIndex

        //populate the piece index
        byte[] pieceIndexByteArray = Util.intToByteArray(pieceIndex);
        for (int i = 0; i < 4; i++) {
            data[i] = pieceIndexByteArray[i];
        }
        //populates the actual data
        int i = startIndex ; // plus 4 again for piece index
        for (; i < endIndex /*&& i < dataShared.length*/; i++) {
            data[i - startIndex + 4] = dataShared[i];
        }

        byte[] actualMessage = MessagesUtil.getActualMessage(data,
                Constants.ActualMessageTypes.PIECE);
        try {
            System.out.println("The actual message size is " + actualMessage.length);
            out.write(actualMessage);
            out.flush();
        } catch (IOException e) {
            System.out.println("io exception in reading " + e.getMessage());
            e.printStackTrace();
        }
    }

    // TODO: Test this function
    public int getNextBitFieldIndexToRequest() {
        // request a piece that I do not have and have not requested from other
        // neighbors, selection
        // of piece should happen randomly
        byte[] bitFieldReq = getBitFieldRequested();
        byte[] notBytesIndex = new byte[bitFieldMsg.length]; // to store bytes that I don't have
        byte[] bitFieldReqAndHave = new byte[bitFieldMsg.length];

        for (int i = 0; i < bitFieldReq.length; i++) {
            bitFieldReqAndHave[i] = (byte) (bitFieldReq[i] | mybitfield[i]);
        }
        // determine bits I dont have.
        for (int i = 0; i < bitFieldReqAndHave.length; i++) {
            notBytesIndex[i] = (byte) ((bitFieldReqAndHave[i] ^ bitFieldMsg[i]) & ~bitFieldReqAndHave[i]);
        }
        // select a random index
        // count the number of 1 bits set
        int count = 0;
//        for (byte n : notBytesIndex) {
//            byte temp = n;
//            while (temp != 0) {
//                ++count;
//                temp &= (temp - 1);
//            }
//
//        }
        int pos = 0;
        for(int i= 0; i < notBytesIndex.length; i++){
            count = 8*i;
            byte temp = notBytesIndex[i];
            Byte b = new Byte(temp);

            System.out.println("Util.byteArraytoInt = " + b.intValue());
            pos =0;
            while (temp != 0 && pos < 8) {
                if((temp & (1<<pos)) != 0)
                {
                    setBitFieldRequested(i, pos);
                    pos = 7 - pos;
                    int index = count + pos;
                    setRequestedIndex(index);
                     // set the ith bit as 1
                    return index;
                }
                ++pos;
            }
        }

////        Random randomGen = new Random();
//        if (count != 0) {
//            int nextInt = ThreadLocalRandom.current().nextInt(0, count);
////         =
//            for (int index = 0; index < notBytesIndex.length; index++) {
//                byte n = notBytesIndex[index];
//                // iterate over bits
//                for (int i = 0; i < 8; i++) {
//                    if ((n & (1 << i)) != 0) {
//                        // i-th bit is set
//
//                        if (nextInt == 0) {
//                            setRequestedIndex((index * 8) + i);
//                            setBitFieldRequested(index, i); // set the ith bit as 1
//                            return (index * 8) + i;
//                        }
//                        nextInt--;
//                    }
//                }
//
//            }
//            // send a random index
//        }

        return -1;
    }
}
