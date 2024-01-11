package code;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TerminationImpl implements Runnable {
    private int interval;
    private PeerMain peerAdmin;
    private Random random = new Random();
    private ScheduledFuture<?> job = null;
    private ScheduledExecutorService scheduler = null;

    public TerminationImpl(PeerMain peerAdmin) {
        this.peerAdmin = peerAdmin;
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    public void startTask(int timeInterval) {
        this.interval = timeInterval * 2;
        this.job = scheduler.scheduleAtFixedRate(this, 30, this.interval, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        try {
            if (this.peerAdmin.checkIfDone()) {
                this.peerAdmin.closeHandlers();
                this.cancelTask();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void cancelTask() {
        this.scheduler.shutdownNow();
    }
}
