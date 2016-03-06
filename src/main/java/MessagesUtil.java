
/**
 * This class represents the messsage's that are exchanged for the protocol to function.
 * @author mebin
 *
 */
public class MessagesUtil {
	
	public static byte[] getHandShakeMessage(int toPeerId) {
		return Util.concatenateByteArrays(Util.concatenateByteArrays(
				Constants.HANDSHAKE_HEADER, Constants.ZERO_BITS), Util.intToByteArray(toPeerId));
	}
	
	public static byte[] getActualMessage(String payload, Constants.ActualMessageTypes msgType){
		int l = payload.getBytes().length;
		byte[] msgL = Util.intToByteArray(l + 1); // plus one for message type
		return Util.concatenateByteArrays(msgL, 
				Util.concatenateByteArray(msgType.n, payload.getBytes()));
	}

	public static byte[] getActualMessage(Constants.ActualMessageTypes msgType){
		byte[] msgL = Util.intToByteArray(1); // plus one for message type
		return Util.concatenateByte(msgL, msgType.n);
	}
	
	public static byte[] getActualMessage(byte[] payload, Constants.ActualMessageTypes msgType){
		byte[] msgL = Util.intToByteArray(payload.length + 1); // plus one for message type
		return Util.concatenateByte(msgL, msgType.n);
	}

}
