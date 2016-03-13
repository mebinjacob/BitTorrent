
public class Constants {
	public static final byte[] HANDSHAKE_HEADER = "P2PFILESHARINGPROJ".getBytes();
	public static final byte[] ZERO_BITS = {0,0,0,0,0,0,0,0,0,0};
	public enum ActualMessageTypes{
		CHOKE((byte)0), 
		UNCHOKE((byte)1), 
		INTERESTED((byte)2), 
		NOT_INTERESTED((byte)3), 
		HAVE((byte)4), 
		BITFIELD((byte)5), 
		REQUEST((byte)6), 
		PIECE((byte)7);
		byte value = -1;
		private ActualMessageTypes(byte n){
			this.value = n;
		}
	}
}
