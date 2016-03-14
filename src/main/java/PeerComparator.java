import java.util.Comparator;


public class PeerComparator implements Comparator<PeerThread> {

	@Override
	public int compare(PeerThread o1, PeerThread o2) {

		return (o1.getPeer().getDownloadingRate() - o2.getPeer().getDownloadingRate()); // since it's a min heap
	}

}
