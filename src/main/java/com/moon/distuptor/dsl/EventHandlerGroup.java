package com.moon.distuptor.dsl;

import com.moon.distuptor.EventHandler;
import com.moon.distuptor.EventProcessor;
import com.moon.distuptor.Sequence;
import com.moon.distuptor.SequenceBarrier;

import java.util.Arrays;

/**
 * @author Chanmoey
 * Create at 2024/3/16
 */
public class EventHandlerGroup<T> {

    private final Disruptor<T> disruptor;

    private final ConsumerRepository<T> consumerRepository;

    private final Sequence[] sequences;

    EventHandlerGroup(final Disruptor<T> disruptor, final ConsumerRepository<T> consumerRepository, final Sequence[] sequences) {
        this.disruptor = disruptor;
        this.consumerRepository = consumerRepository;
        this.sequences = Arrays.copyOf(sequences, sequences.length);
    }


    public EventHandlerGroup<T> and(final EventHandlerGroup<T> otherHandlerGroup) {
        final Sequence[] combinedSequences = new Sequence[this.sequences.length + otherHandlerGroup.sequences.length];
        System.arraycopy(this.sequences, 0, combinedSequences, 0, this.sequences.length);
        System.arraycopy(otherHandlerGroup.sequences, 0, combinedSequences, this.sequences.length, otherHandlerGroup.sequences.length);
        return new EventHandlerGroup<>(disruptor, consumerRepository, combinedSequences);
    }


    public EventHandlerGroup<T> and(final EventProcessor... processors) {
        Sequence[] combinedSequences = new Sequence[sequences.length + processors.length];

        for (int i = 0; i < processors.length; i++) {
            consumerRepository.add(processors[i]);
            combinedSequences[i] = processors[i].getSequence();
        }
        System.arraycopy(sequences, 0, combinedSequences, processors.length, sequences.length);

        return new EventHandlerGroup<>(disruptor, consumerRepository, combinedSequences);
    }


    @SafeVarargs
    public final EventHandlerGroup<T> then(final EventHandler<? super T>... handlers) {
        return handleEventsWith(handlers);
    }


    @SafeVarargs
    public final EventHandlerGroup<T> then(final EventProcessorFactory<T>... eventProcessorFactories) {
        return handleEventsWith(eventProcessorFactories);
    }


//    @SafeVarargs
//    public final EventHandlerGroup<T> thenHandleEventsWithWorkerPool(final WorkHandler<? super T>... handlers)
//    {
//        return handleEventsWithWorkerPool(handlers);
//    }


    @SafeVarargs
    public final EventHandlerGroup<T> handleEventsWith(final EventHandler<? super T>... handlers) {
        return disruptor.createEventProcessors(sequences, handlers);
    }

    @SafeVarargs
    public final EventHandlerGroup<T> handleEventsWith(final EventProcessorFactory<T>... eventProcessorFactories) {
        return disruptor.createEventProcessors(sequences, eventProcessorFactories);
    }


//    @SafeVarargs
//    public final EventHandlerGroup<T> handleEventsWithWorkerPool(final WorkHandler<? super T>... handlers)
//    {
//        return disruptor.createWorkerPool(sequences, handlers);
//    }


    public SequenceBarrier asSequenceBarrier() {
        return disruptor.getRingBuffer().newBarrier(sequences);
    }
}