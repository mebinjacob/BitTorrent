import java.math.BigInteger;


public class Util {
	/**
	 * Utility function to append two byte arrays.
	 * @param a - byte array input 1 
	 * @param b - byte array input 2
	 * @return byte array concatenated
	 */
	public static byte[] concatenateByteArrays(byte[] a, byte[] b){
		byte[] result = new byte[a.length + b.length];
		System.arraycopy(a, 0, result, 0, a.length);
		System.arraycopy(b, 0, result, a.length, b.length); 
		return result;
	}
	
	public static byte[] concatenateByteArray(byte b, byte[] a){
		byte[] result = new byte[a.length + 1];
		System.arraycopy(a, 0, result, 0, a.length);
		result[a.length] = b;
		return result;
	}
	
	public static byte[] concatenateByte(byte[] a, byte b){
		byte[] result = new byte[a.length + 1];
		System.arraycopy(a, 0, result, 0, a.length);
		result[a.length] = b;
		return result;
	}
	
	public static byte[] intToByteArray(int n){
		return BigInteger.valueOf(n).toByteArray();
	}
}
