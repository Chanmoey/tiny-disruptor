package com.moon.distuptor;

/**
 * @author Chanmoey
 * Create at 2024/3/15
 */
public class ProcessingSequenceBarrier implements SequenceBarrier{

    /**
     * 等待策略
     */
    private final WaitStrategy waitStrategy;

    /**
     * 有些消费者，需要依赖其他消费者消费完成之后，才能对当前事件进行消费
     */
    private final Sequence dependentSequence;

    private volatile boolean alerted = false;

    /**
     * 生产者进度
     */
    private final Sequence cursorSequence;

    /**
     * 序号生成器，为生产者分配生产序号
     */
    private final Sequencer sequencer;

    public ProcessingSequenceBarrier(final Sequencer sequencer,
                                     final WaitStrategy waitStrategy,
                                     final Sequence cursorSequence,
                                     final Sequence[] dependentSequences) {
        this.sequencer = sequencer;
        this.waitStrategy = waitStrategy;
        this.cursorSequence = cursorSequence;
        if (0 == dependentSequences.length) {
            // 不依赖其他消费者，所以默认依赖生产者
            dependentSequence = cursorSequence;
        } else {
            // TODO FIXME
            dependentSequence = cursorSequence;
        }
    }

    /**
     *
     * @param sequence 消费者期待能消费的最小序号
     * @return 可以消费的序号（包括这个序号）
     */
    public long waitFor(final long sequence) throws AlertException, InterruptedException, TimeoutException {
        checkAlert();
        long availableSequence = waitStrategy.waitFor(sequence, cursorSequence, dependentSequence, this);
        if (availableSequence < sequence) {
            return availableSequence;
        }

        // 在多生产者的情况下，要保证生产者的最大生产序号之间要连续的，否则要截断
        return sequencer.getHighestPublishedSequence(sequence, availableSequence);
    }

    @Override
    public long getCursor() {
        return dependentSequence.get();
    }

    @Override
    public boolean isAlerted() {
        return alerted;
    }

    @Override
    public void alert() {
        alerted = true;
        waitStrategy.signalAllWhenBlocking();
    }

    @Override
    public void clearAlert() {
        alerted = false;
    }

    @Override
    public void checkAlert() throws AlertException {
        if (alerted) {
            throw AlertException.INSTANCE;
        }
    }
}
