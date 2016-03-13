import java.io.DataInputStream;
import java.io.IOException;


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
				Util.concatenateByteArray(msgType.value, payload.getBytes()));
	}

	public static byte[] getActualMessage(Constants.ActualMessageTypes msgType){
		byte[] msgL = Util.intToByteArray(1); // plus one for message type
		return Util.concatenateByte(msgL, msgType.value);
	}
	
	public static byte[] getActualMessage(byte[] payload, Constants.ActualMessageTypes msgType){
		byte[] msgL = Util.intToByteArray(payload.length + 1); // plus one for message type
		return Util.concatenateByteArrays(Util.concatenateByte(msgL, msgType.value), payload);
	}
	
	public static byte[] readActualMessage(DataInputStream in, Constants.ActualMessageTypes bitfield){
		byte[] lengthByte = new byte[4];
		int read = -1;
		byte[] data = null;
		try {
			read = in.read(lengthByte);
			if(read != 4){
				System.out.println("Message length is not proper!!!");
			}
			int dataLength = Util.byteArrayToInt(lengthByte);
			//read msg type
			byte[] msgType = new byte[1];
			in.read(msgType);
			if(msgType[0] == bitfield.value){
				 data = new byte[dataLength-1];
				in.read(data);
			}
			else{
				System.out.println("Wrong message type sent");
			}
			
		} catch (IOException e) {
			System.out.println("Could not read length of actual message");
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return data;
	}
}
