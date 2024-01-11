package code;

import code.RemotePeerDetails;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class PeerInfoConfig {

    private HashMap<String, RemotePeerDetails> peerInfoMap;
    private ArrayList<String> peerList;

    public PeerInfoConfig() {
        this.peerInfoMap = new HashMap<>();
        this.peerList = new ArrayList<>();
    }

    public void loadCfgFile() {
        try (Scanner scanner = new Scanner(new File("PeerInfo.cfg"))) {
            while (scanner.hasNextLine()) {
                String[] tokens = scanner.nextLine().split("\\s+");
                RemotePeerDetails peerInfo = new RemotePeerDetails(tokens[0], tokens[1], tokens[2], tokens[3]);
                this.peerInfoMap.put(tokens[0], peerInfo);
                this.peerList.add(tokens[0]);
            }
        } catch (IOException ex) {
            System.out.println(ex.toString());
        }
    }

    public RemotePeerDetails getPeerConfig(String peerID) {
        return this.peerInfoMap.get(peerID);
    }

    public HashMap<String, RemotePeerDetails> getPeerMap() {
        return this.peerInfoMap;
    }

    public ArrayList<String> getPeerList() {
        return this.peerList;
    }
}
