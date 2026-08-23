package dev.naominet.listclient.utils;

public class TimerUtils {
    public TimerUtils() {
        this.lastMS = this.getCurrentMS();
    }
    private long time;

    private long lastMS;
    private long currentMS = System.currentTimeMillis();

    public boolean delay(float nextDelay, boolean reset) {
        if (System.currentTimeMillis() - lastMS >= nextDelay) {
            if (reset) {
                this.reset();
            }
            return true;
        }
        return false;
    }

    public long lastReset() {
        return currentMS;
    }

    private long getCurrentMS() {
        return System.nanoTime() / 1000000L;
    }

    public boolean check(float milliseconds) {
        return getTime() >= milliseconds;
    }

    public boolean hasReached(double milliseconds) {
        return (double) (this.getCurrentMS() - this.lastMS) >= milliseconds;
    }

    public boolean hasElapsed(long milliseconds) {
        return elapsed() > milliseconds;
    }

    public void setCurrentMS(long currentMS) {
        this.currentMS = currentMS;
    }

    public static long randomDelay(final int minDelay, final int maxDelay) {
        return RandomUtils.nextInt(minDelay, maxDelay);
    }

    public long time() {
        return System.nanoTime() / 1000000L - time;
    }

    public boolean sleep(final long time) {
        if (time() >= time) {
            reset();
            return true;
        }
        return false;
    }

    public void reset() {
        this.lastMS = this.getCurrentMS();
        time = System.nanoTime() / 1000000L;
    }

    /** Advances the deadline without discarding elapsed time since the deadline. */
    public void advance(double milliseconds) {
        this.lastMS += Math.max(1L, Math.round(milliseconds));
    }
    public boolean reached(final long time) {
        return time() >= time;
    }

    public long elapsed() {
        return System.currentTimeMillis() - currentMS;
    }

    public boolean delay(float milliSec) {
        return (float) (this.getTime() - this.lastMS) >= milliSec;
    }

    public long getTime() {
        return System.nanoTime() / 1000000L;
    }
}
