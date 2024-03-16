package com.moon.distuptor.dsl;

import com.moon.distuptor.EventProcessor;
import com.moon.distuptor.RingBuffer;
import com.moon.distuptor.Sequence;

/**
 * @author Chanmoey
 * Create at 2024/3/16
 */
public interface EventProcessorFactory<T> {
    EventProcessor createEventProcessor(RingBuffer<T> ringBuffer, Sequence[] barrierSequences);

}
