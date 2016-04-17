import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;


/**
 * This class represents the messsage's that are exchanged for the protocol to function.
 *
 * @author mebin
 */
public class MessagesUtil {

    public static byte[] getHandShakeMessage(int toPeerId) {
        return Util.concatenateByteArrays(Util.concatenateByteArrays(
                Constants.HANDSHAKE_HEADER, Constants.ZERO_BITS), Util.intToByteArray(toPeerId));
    }

    public static byte[] getActualMessage(String payload, Constants.ActualMessageTypes msgType) {
        int l = payload.getBytes().length;
        byte[] msgL = Util.intToByteArray(l + 1); // plus one for message type
        return Util.concatenateByteArrays(msgL,
                Util.concatenateByteArray(msgType.value, payload.getBytes()));
    }

    public static byte[] getActualMessage(Constants.ActualMessageTypes msgType) {
        byte[] msgL = Util.intToByteArray(1); // plus one for message type
        return Util.concatenateByte(msgL, msgType.value);
    }

    public static byte[] getActualMessage(byte[] payload, Constants.ActualMessageTypes msgType) {
        byte[] msgL = Util.intToByteArray(payload.length + 1); // plus one for message type
        return Util.concatenateByteArrays(Util.concatenateByte(msgL, msgType.value), payload);
    }

    public static byte[] readActualMessage(InputStream in, Constants.ActualMessageTypes bitfield) {
        byte[] lengthByte = new byte[4];
        int read = -1;
        byte[] data = null;
        try {
            read = in.read(lengthByte);
            if (read != 4) {
                System.out.println("Message length is not proper!!!");
            }
            int dataLength = Util.byteArrayToInt(lengthByte);
            //read msg type
            byte[] msgType = new byte[1];
            in.read(msgType);
            if (msgType[0] == bitfield.value) {
                int actualDataLength = dataLength - 1;
                data = new byte[actualDataLength];
                int dataL = actualDataLength;
                System.out.println("actualDataLength initially = " + actualDataLength);
                System.out.println("data length initially = " + data.length);
                System.out.println("actualDataLength of bitfield = " + actualDataLength);
                while (actualDataLength != 0) { // TODO : verify
                    int availableBytes = in.available();
                    if (availableBytes > 0) {
                        System.out.println("availableBytes = " + availableBytes);
                        byte[] bufferedData = new byte[availableBytes];
                        int dataRead = in.read(bufferedData);
                        System.out.println("actualDataLength = " + actualDataLength);
                        if (dataRead != -1) {
                            System.out.println("dataL = " + dataL);
                            System.out.println("dataRead = " + dataRead);
                            System.out.println("data.length = " + data.length);
                            System.out.println("actualDataLength = " + actualDataLength);
                            data = Util.concatenateByteArrays(data, dataL - actualDataLength, bufferedData, dataRead);
                            actualDataLength -= availableBytes;
                        } else {
                            System.out.println("This should not have happened inside MessagesUtil");
                        }
                    }

                }
                System.out.println("Actual data length " + actualDataLength);
//				if(dataRead == dataLength -1){
//					System.out.println("Gool");
//				}
//				else{
//					System.out.println("Screwed!!!");
//				}
            } else {
                System.out.println("Wrong message type sent");
            }

        } catch (IOException e) {
            System.out.println("Could not read length of actual message");
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return data;
    }

    public static Constants.ActualMessageTypes getMsgType(byte[] msgStat) {
        String s = Arrays.toString(msgStat);
        for (Constants.ActualMessageTypes actMsgType : Constants.ActualMessageTypes.values()) {
            if (actMsgType.value == msgStat[4]) {
                return actMsgType;
            }
        }
        return null;
    }
}
