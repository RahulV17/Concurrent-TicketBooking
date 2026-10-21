import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class StatusMonitor implements Runnable {

    // volatile so the main thread can stop the monitor and the monitor sees it
    private volatile boolean running = true;
    private final long intervalMillis;

    // shared with the console, the monitor waits for it so a status line
    // never lands in the middle of what the user is typing
    private final Object outputLock;

    // counted down after the first status line so the app can wait for it
    private final CountDownLatch firstStatus;

    public StatusMonitor(long intervalMillis) {
        this(intervalMillis, null, null);
    }

    public StatusMonitor(long intervalMillis, Object outputLock, CountDownLatch firstStatus) {
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException(
                    "Monitor interval must be a positive number of milliseconds. Received: "
                            + intervalMillis);
        }
        this.intervalMillis = intervalMillis;
        this.outputLock = outputLock;
        this.firstStatus = firstStatus;
    }

    @Override
    public void run() {
        boolean announced = false;
        while (running) {
            printStatus(String.format(
                    "[%s] [STATUS-MONITOR] Status: ACTIVE. Booking service is running and ready.",
                    Thread.currentThread().getName()), true);
            if (!announced) {
                announced = true;
                CountDownLatch latch = firstStatus;
                if (latch != null) {
                    latch.countDown();
                }
            }
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                // restore the flag, the thread was interrupted on purpose
                Thread.currentThread().interrupt();
                break;
            }
        }
        printStatus(String.format("[%s] [MONITOR STOP] Status monitor stopped cleanly.",
                Thread.currentThread().getName()), false);
    }

    // re-check running after taking the lock, the monitor can be stopped
    // while it waits and should not print a stale ACTIVE line
    private void printStatus(String message, boolean requireRunning) {
        if (outputLock == null) {
            if (!requireRunning || running) {
                System.out.println(message);
            }
            return;
        }
        synchronized (outputLock) {
            if (!requireRunning || running) {
                System.out.println(message);
            }
        }
    }

    public void stopMonitoring() {
        this.running = false;
    }
}
