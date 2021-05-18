package chunkycl;

import se.llbit.util.TaskTracker;

/**
 * An asynchronous render canvas finalizer.
 */
public class Finalizer extends Thread {
    protected RenderManagerCl renderer;

    protected final Object startMonitor = new Object();
    protected final Object completeMonitor = new Object();
    protected volatile boolean finalizing = false;

    public Finalizer(RenderManagerCl renderer) {
        this.renderer = renderer;
        this.setDaemon(true);
        this.start();
    }

    /** Wait for all threads to finish. */
    public void finalizeJoin() throws InterruptedException {
        synchronized (completeMonitor) {
            while (finalizing) {
                completeMonitor.wait(50);
            }
        }
    }

    /** Finalize and wait for all threads to finish */
    public void finalizeOnce() throws InterruptedException {
        finalizeSoon();
        finalizeJoin();
    }

    /** Start finalization process. */
    public void finalizeSoon() {
        synchronized (startMonitor) {
            finalizing = true;
            startMonitor.notifyAll();
        }
    }

    /** Check if threads are currently finalizing (false = done). */
    public boolean isFinalizing() {
        return finalizing;
    }

    @Override
    public void run() {
        try {
            while (!isInterrupted()) {
                synchronized (startMonitor) {
                    while (!finalizing) {
                        startMonitor.wait();
                    }
                }

                renderer.getBufferedScene().postProcessFrame(TaskTracker.NONE);

                synchronized (completeMonitor) {
                    finalizing = false;
                    completeMonitor.notifyAll();
                }
            }
        } catch (InterruptedException e) {
            // Interrupted
        }
    }
}
