package com.moon.distuptor;

/**
 * @author Chanmoey
 * Create at 2024/3/16
 */
public interface SequenceBarrier {

    long waitFor(long sequence) throws AlertException, InterruptedException, TimeoutException;

    long getCursor();

    boolean isAlerted();

    void alert();

    void clearAlert();

    void checkAlert() throws AlertException;
}
