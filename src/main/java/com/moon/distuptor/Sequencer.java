package com.moon.distuptor;

/**
 * 序号生成器
 *
 * @author Chanmoey
 * Create at 2024/3/14
 */
public interface Sequencer extends Cursored, Sequenced{

    long INITIAL_CURSOR_VALUE = -1L;

    /**
     * 设置生产者的序号
     */
    void claim(long sequence);

    /**
     * 判断序号是否可用
     */
    boolean isAvailable(long sequence);

    /**
     * 把新添加进来的消费者消费序号添加到gatingSequences数组中
     */
    void addGatingSequences(Sequence... gatingSequences);

    /**
     * 从gatingSequence数组中，删除消费者的消费序号
     */
    boolean removeGatingSequence(Sequence sequence);

    /**
     * 为消费者创建序号屏障
     */
    SequenceBarrier newBarrier(Sequence... sequencesToTrack);

    /**
     * 得到所有消费者序号和当生产者序号中，最小的那个序号
     */
    long getMinimumSequence();

    /**
     * 得到已经发布的最大生产者序号，且保证最大生产者序号之前的序号都是连续的
     */
    long getHighestPublishedSequence(long nextSequence, long availableSequence);
}
