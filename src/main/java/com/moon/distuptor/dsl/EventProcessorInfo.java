package com.moon.distuptor.dsl;

import com.moon.distuptor.EventHandler;
import com.moon.distuptor.EventProcessor;
import com.moon.distuptor.Sequence;
import com.moon.distuptor.SequenceBarrier;

import java.util.concurrent.Executor;

/**
 * 封装消费者的信息
 *
 * @author Chanmoey
 * Create at 2024/3/16
 */
public class EventProcessorInfo<T> implements ConsumerInfo{

    /**
     * 消费者的事件处理器，封装这用户定义的消费者Handler
     */
    private final EventProcessor eventProcessor;

    private final EventHandler<? super T> handler;

    private final SequenceBarrier barrier;

    private boolean endOfChain = true;

    public EventProcessorInfo(final EventProcessor eventProcessor,
                              final EventHandler<? super T> handler,
                              final SequenceBarrier barrier) {
        this.eventProcessor = eventProcessor;
        this.handler = handler;
        this.barrier = barrier;
    }

    public EventProcessor getEventProcessor() {
        return eventProcessor;
    }

    public EventHandler<? super T> getHandler() {
        return handler;
    }

    @Override
    public Sequence[] getSequences() {
        return new Sequence[]{eventProcessor.getSequence()};
    }

    @Override
    public SequenceBarrier getBarrier() {
        return barrier;
    }

    @Override
    public boolean isEndOfChain() {
        return endOfChain;
    }

    @Override
    public void start(Executor executor) {
        executor.execute(eventProcessor);
    }

    @Override
    public void halt() {
        eventProcessor.halt();
    }

    @Override
    public void markAsUsedInBarrier() {
        endOfChain = false;
    }

    @Override
    public boolean isRunning() {
        return eventProcessor.isRunning();
    }
}
