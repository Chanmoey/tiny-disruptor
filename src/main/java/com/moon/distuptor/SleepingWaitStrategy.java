package com.moon.distuptor;

import java.util.concurrent.locks.LockSupport;

/**
 * @author Chanmoey
 * Create at 2024/3/14
 */
public class SleepingWaitStrategy implements WaitStrategy {

    private static final int SPIN_THRESHOLD = 100;
    private static final int DEFAULT_RETRIES = 200;
    private static final long DEFAULT_SLEEP = 100;

    /**
     * 重试次数
     */
    private final int retries;

    /**
     * 休眠的纳秒数
     */
    private final long sleepTimeNs;

    public SleepingWaitStrategy() {
        this(DEFAULT_RETRIES, DEFAULT_SLEEP);
    }

    public SleepingWaitStrategy(final int retries, final long sleepTimeNs) {
        this.retries = retries;
        this.sleepTimeNs = sleepTimeNs;
    }


    @Override
    public long waitFor(long sequence, Sequence cursor, Sequence dependentSequence, SequenceBarrier barrier) throws AlertException, InterruptedException, TimeoutException {
        long availableSequence;

        int counter = retries;
        while ((availableSequence = dependentSequence.get()) < sequence) {
            counter = applyWaitMethod(barrier, counter);
        }

        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking() {

    }

    private int applyWaitMethod(final SequenceBarrier barrier, int counter) throws AlertException{

        barrier.checkAlert();

        if (counter > SPIN_THRESHOLD) {
            // 空转
            return counter - 1;
        }
        if (counter > 0) {
            // 放弃CPU
            Thread.yield();
            return counter - 1;
        }
        // 休眠
        LockSupport.parkNanos(this.sleepTimeNs);
        return counter;
    }
}
