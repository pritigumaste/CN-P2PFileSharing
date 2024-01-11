package code;
public class RemotePeerDetails {
	public String pId;
	public String pAddress;
	public int pPort;
	public int cFile;

	public RemotePeerDetails(String peerId, String peerAddress, String peerPort, String containsFile) {
		this.pId = peerId;
		this.pAddress = peerAddress;
		this.pPort = Integer.parseInt(peerPort);
		this.cFile = Integer.parseInt(containsFile);
	}
	
}
