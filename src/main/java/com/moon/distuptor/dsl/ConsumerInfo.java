package com.moon.distuptor.dsl;

import com.moon.distuptor.Sequence;
import com.moon.distuptor.SequenceBarrier;

import java.util.concurrent.Executor;

/**
 * @author Chanmoey
 * Create at 2024/3/16
 */
public interface ConsumerInfo {

    Sequence[] getSequences();

    SequenceBarrier getBarrier();

    /**
     * 是否为消费最慢的那个消费者
     */
    boolean isEndOfChain();

    void start(Executor executor);

    void halt();

    /**
     * 如果当前消费是最慢的，但是来了一个更慢的，则用这个方法改变自己的身份
     */
    void markAsUsedInBarrier();

    /**
     * 消费者线程是否孩子运行
     */
    boolean isRunning();
}
