import java.util.Comparator;


public class PeerComparator<T extends Peer> implements Comparator<Peer> {

	@Override
	public int compare(Peer o1, Peer o2) {

		return (o1.getDownloadingRate() - o2.getDownloadingRate()); // since it's a min heap
	}


}
