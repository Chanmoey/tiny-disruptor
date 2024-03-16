package com.moon.distuptor;

/**
 * @author Chanmoey
 * Create at 2024/3/13
 */
public interface WaitStrategy {
    long waitFor(long sequence, Sequence cursor, Sequence dependentSequence, SequenceBarrier barrier)
            throws AlertException, InterruptedException, TimeoutException;

    void signalAllWhenBlocking();
}
