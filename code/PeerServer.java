package code;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import code.CommonConfig;
import code.PeerMain;
import code.PeerProcessHandler;
import code.PeerInfoConfig;
import code.RemotePeerDetails;

public class PeerServer implements Runnable {
    private String peerID;
    private ServerSocket listener;
    private PeerMain peerAdmin;
    private boolean isTerminated;

    public PeerServer(String peerID, ServerSocket listener, PeerMain admin) {
        this.peerID = peerID;
        this.listener = listener;
        this.peerAdmin = admin;
        this.isTerminated = false;
    }

    public void run() {
        while (!this.isTerminated) {
            try {
                Socket neighborSocket = this.listener.accept();
                PeerProcessHandler neighborHandler = new PeerProcessHandler(neighborSocket, this.peerAdmin);
                new Thread(neighborHandler).start();
                String neighborAddress = neighborSocket.getInetAddress().toString();
                int neighborPort = neighborSocket.getPort();
            } catch (SocketException e) {
                break;
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
        }
    }
}
