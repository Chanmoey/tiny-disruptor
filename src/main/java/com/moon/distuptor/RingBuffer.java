package com.moon.distuptor;

import com.moon.distuptor.dsl.ProducerType;
import com.moon.distuptor.util.Util;
import sun.misc.Unsafe;

/**
 * @author Chanmoey
 * Create at 2024/3/14
 */

abstract class RingBufferPad {
    // 7 * 8 = 56字节
    protected long p1, p2, p3, p4, p5, p6, p7;

}

abstract class RingBufferFields<E> extends RingBufferPad {
    /**
     * 要在数组自身中填充的空数据个数
     */
    private static final int BUFFER_PAD;

    /**
     * 数组中，第一个有效部分在数组中的内存偏移量（数组有对象头）
     */
    private static final long REF_ARRAY_BASE;

    /**
     * 数组中，应该填充的空白字节大小
     */
    private static final int REF_ELEMENT_SHIFT;

    private static final Unsafe UNSAFE = Util.getUnsafe();

    static {
        // 计算数组中，每个位置的字节大小，一般都是4字节
        final int scale = UNSAFE.arrayIndexScale(Object[].class);
        if (4 == scale) {
            REF_ELEMENT_SHIFT = 2;
        } else if (8 == scale) {
            REF_ELEMENT_SHIFT = 3;
        } else {
            throw new IllegalStateException("Unknown pointer size");
        }
        // 计算出来的是32
        BUFFER_PAD = 128 / scale;
        REF_ARRAY_BASE = UNSAFE.arrayBaseOffset(Object[].class) + (BUFFER_PAD << REF_ELEMENT_SHIFT);
    }

    private final long indexMask;

    private final Object[] entries;

    protected final int bufferSize;

    protected final Sequencer sequencer;

    RingBufferFields(EventFactory<E> eventFactory, Sequencer sequencer) {
        this.sequencer = sequencer;
        this.bufferSize = sequencer.getBufferSize();

        if (bufferSize < 1) {
            throw new IllegalArgumentException("bufferSize must not be less than 1");
        }
        if (Integer.bitCount(bufferSize) != 1) {
            throw new IllegalArgumentException("bufferSize must be a power of 2");
        }

        this.indexMask = bufferSize - 1;
        // TODO 理解这里为什么不会伪共享
        this.entries = new Object[sequencer.getBufferSize() + 2 * BUFFER_PAD];

        fill(eventFactory);
    }

    private void fill(EventFactory<E> eventFactory) {
        for (int i = 0; i < bufferSize; i++) {
            // TODO 理解这里
            entries[BUFFER_PAD + i] = eventFactory.newInstance();
        }
    }

    @SuppressWarnings("unchecked")
    protected final E elementAt(long sequence) {
        // TODO 理解这里
        return (E) UNSAFE.getObject(entries, REF_ARRAY_BASE + ((sequence & indexMask) << REF_ELEMENT_SHIFT));
    }
}

public final class RingBuffer<E> extends RingBufferFields<E> implements Cursored, EventSequencer<E>, EventSink<E> {

    public static final long INITIAL_CURSOR_VALUE = Sequence.INITIAL_VALUE;

    // TODO 理解这里
    protected long p1, p2, p3, p4, p5, p6, p7;

    RingBuffer(EventFactory<E> eventFactory, Sequencer sequencer) {
        super(eventFactory, sequencer);
    }

    public static <E> RingBuffer<E> createSingleProducer(EventFactory<E> factory, int bufferSize, WaitStrategy waitStrategy) {
        //创建单生产者序列号生成器
        SingleProducerSequencer sequencer = new SingleProducerSequencer(bufferSize, waitStrategy);
        //创建环形数组
        return new RingBuffer<E>(factory, sequencer);
    }

    public static <E> RingBuffer<E> create(ProducerType producerType, EventFactory<E> factory, int bufferSize, WaitStrategy waitStrategy) {
        switch (producerType) {
            //创建单生产者模式下的序列号生成器
            case SINGLE:
                return createSingleProducer(factory, bufferSize, waitStrategy);
            //下面创建的是多生产者序号生成器，但多生产者还未引入，所以先注释了
//            case MULTI:
//                return createMultiProducer(factory, bufferSize, waitStrategy);
            default:
                throw new IllegalStateException(producerType.toString());
        }
    }

    @Override
    public E get(long sequence) {
        return elementAt(sequence);
    }

    @Override
    public long next() {
        return sequencer.next();
    }

    @Override
    public long next(int n) {
        return sequencer.next(n);
    }

    @Override
    public long tryNext() throws InsufficientCapacityException {
        return sequencer.tryNext();
    }

    @Override
    public long tryNext(int n) throws InsufficientCapacityException {
        return sequencer.tryNext(n);
    }

    @Deprecated
    public void resetTo(long sequence) {
        sequencer.claim(sequence);
        //通知其他消费者这个序号位置可以消费了
        sequencer.publish(sequence);
    }

    public E claimAndGetPreallocated(long sequence) {
        sequencer.claim(sequence);
        return get(sequence);
    }

    @Deprecated
    public boolean isPublished(long sequence) {
        return sequencer.isAvailable(sequence);
    }

    //把新添加进来的消费者的消费序号添加到gatingSequences数组中
    public void addGatingSequences(Sequence... gatingSequences) {
        sequencer.addGatingSequences(gatingSequences);
    }

    //得到所有消费者序号和当前生产者序号中最小的那个序号
    public long getMinimumGatingSequence() {
        return sequencer.getMinimumSequence();
    }

    //从gatingSequences数组中删除不必在关注的消费者的消费序号
    public boolean removeGatingSequence(Sequence sequence) {
        return sequencer.removeGatingSequence(sequence);
    }

    //为消费者创建序号屏障
    public SequenceBarrier newBarrier(Sequence... sequencesToTrack) {
        return sequencer.newBarrier(sequencesToTrack);
    }


//    public EventPoller<E> newPoller(Sequence... gatingSequences)
//    {
//        return sequencer.newPoller(this, gatingSequences);
//    }

    //获得当前生产者的序号
    @Override
    public long getCursor() {
        return sequencer.getCursor();
    }

    //获得环形数组的有效容量
    public int getBufferSize() {
        return bufferSize;
    }

    //是否可以申请指定的序号数量
    public boolean hasAvailableCapacity(int requiredCapacity) {
        return sequencer.hasAvailableCapacity(requiredCapacity);
    }


    @Override
    public void publishEvent(EventTranslator<E> translator) {   //首先获得可用的生产者序号
        final long sequence = sequencer.next();
        translateAndPublish(translator, sequence);
    }


//    @Override
//    public boolean tryPublishEvent(EventTranslator<E> translator)
//    {
//        try
//        {
//            final long sequence = sequencer.tryNext();
//            translateAndPublish(translator, sequence);
//            return true;
//        }
//        catch (InsufficientCapacityException e)
//        {
//            return false;
//        }
//    }
//

    @Override
    public <A> void publishEvent(EventTranslatorOneArg<E, A> translator, A arg0) {
        //首先获得可用的生产者序号，生产者发布的信息就可以存放到该位置
        final long sequence = sequencer.next();
        //然后在这个序号对应的位置发布数据
        translateAndPublish(translator, sequence, arg0);
    }


//    @Override
//    public <A> boolean tryPublishEvent(EventTranslatorOneArg<E, A> translator, A arg0)
//    {
//        try
//        {
//            final long sequence = sequencer.tryNext();
//            translateAndPublish(translator, sequence, arg0);
//            return true;
//        }
//        catch (InsufficientCapacityException e)
//        {
//            return false;
//        }
//    }
//


//    @Override
//    public <A, B> void publishEvent(EventTranslatorTwoArg<E, A, B> translator, A arg0, B arg1)
//    {
//        final long sequence = sequencer.next();
//        translateAndPublish(translator, sequence, arg0, arg1);
//    }
//


//    @Override
//    public <A, B> boolean tryPublishEvent(EventTranslatorTwoArg<E, A, B> translator, A arg0, B arg1)
//    {
//        try
//        {
//            final long sequence = sequencer.tryNext();
//            translateAndPublish(translator, sequence, arg0, arg1);
//            return true;
//        }
//        catch (InsufficientCapacityException e)
//        {
//            return false;
//        }
//    }
//


//    @Override
//    public <A, B, C> void publishEvent(EventTranslatorThreeArg<E, A, B, C> translator, A arg0, B arg1, C arg2)
//    {
//        final long sequence = sequencer.next();
//        translateAndPublish(translator, sequence, arg0, arg1, arg2);
//    }
//


//    @Override
//    public <A, B, C> boolean tryPublishEvent(EventTranslatorThreeArg<E, A, B, C> translator, A arg0, B arg1, C arg2)
//    {
//        try
//        {
//            final long sequence = sequencer.tryNext();
//            translateAndPublish(translator, sequence, arg0, arg1, arg2);
//            return true;
//        }
//        catch (InsufficientCapacityException e)
//        {
//            return false;
//        }
//    }
//


//    @Override
//    public void publishEvent(EventTranslatorVararg<E> translator, Object... args)
//    {
//        final long sequence = sequencer.next();
//        translateAndPublish(translator, sequence, args);
//    }
//


//    @Override
//    public boolean tryPublishEvent(EventTranslatorVararg<E> translator, Object... args)
//    {
//        try
//        {
//            final long sequence = sequencer.tryNext();
//            translateAndPublish(translator, sequence, args);
//            return true;
//        }
//        catch (InsufficientCapacityException e)
//        {
//            return false;
//        }
//    }
//


//    @Override
//    public void publishEvents(EventTranslator<E>[] translators)
//    {
//        publishEvents(translators, 0, translators.length);
//    }
//


//    @Override
//    public void publishEvents(EventTranslator<E>[] translators, int batchStartsAt, int batchSize)
//    {
//        checkBounds(translators, batchStartsAt, batchSize);
//        final long finalSequence = sequencer.next(batchSize);
//        translateAndPublishBatch(translators, batchStartsAt, batchSize, finalSequence);
//    }
//


//    @Override
//    public boolean tryPublishEvents(EventTranslator<E>[] translators)
//    {
//        return tryPublishEvents(translators, 0, translators.length);
//    }
//


//    @Override
//    public boolean tryPublishEvents(EventTranslator<E>[] translators, int batchStartsAt, int batchSize)
//    {
//        checkBounds(translators, batchStartsAt, batchSize);
//        try
//        {
//            final long finalSequence = sequencer.tryNext(batchSize);
//            translateAndPublishBatch(translators, batchStartsAt, batchSize, finalSequence);
//            return true;
//        }
//        catch (InsufficientCapacityException e)
//        {
//            return false;
//        }
//    }
//


//    @Override
//    public <A> void publishEvents(EventTranslatorOneArg<E, A> translator, A[] arg0)
//    {
//        publishEvents(translator, 0, arg0.length, arg0);
//    }
//


//    @Override
//    public <A> void publishEvents(EventTranslatorOneArg<E, A> translator, int batchStartsAt, int batchSize, A[] arg0)
//    {
//        checkBounds(arg0, batchStartsAt, batchSize);
//        final long finalSequence = sequencer.next(batchSize);
//        translateAndPublishBatch(translator, arg0, batchStartsAt, batchSize, finalSequence);
//    }
//


//    @Override
//    public <A> boolean tryPublishEvents(EventTranslatorOneArg<E, A> translator, A[] arg0)
//    {
//        return tryPublishEvents(translator, 0, arg0.length, arg0);
//    }
//


//    @Override
//    public <A> boolean tryPublishEvents(
//            EventTranslatorOneArg<E, A> translator, int batchStartsAt, int batchSize, A[] arg0)
//    {
//        checkBounds(arg0, batchStartsAt, batchSize);
//        try
//        {
//            final long finalSequence = sequencer.tryNext(batchSize);
//            translateAndPublishBatch(translator, arg0, batchStartsAt, batchSize, finalSequence);
//            return true;
//        }
//        catch (InsufficientCapacityException e)
//        {
//            return false;
//        }
//    }
//


//    @Override
//    public <A, B> void publishEvents(EventTranslatorTwoArg<E, A, B> translator, A[] arg0, B[] arg1)
//    {
//        publishEvents(translator, 0, arg0.length, arg0, arg1);
//    }
//


//    @Override
//    public <A, B> void publishEvents(
//            EventTranslatorTwoArg<E, A, B> translator, int batchStartsAt, int batchSize, A[] arg0, B[] arg1)
//    {
//        checkBounds(arg0, arg1, batchStartsAt, batchSize);
//        final long finalSequence = sequencer.next(batchSize);
//        translateAndPublishBatch(translator, arg0, arg1, batchStartsAt, batchSize, finalSequence);
//    }
//


//    @Override
//    public <A, B> boolean tryPublishEvents(EventTranslatorTwoArg<E, A, B> translator, A[] arg0, B[] arg1)
//    {
//        return tryPublishEvents(translator, 0, arg0.length, arg0, arg1);
//    }
//


//    @Override
//    public <A, B> boolean tryPublishEvents(
//            EventTranslatorTwoArg<E, A, B> translator, int batchStartsAt, int batchSize, A[] arg0, B[] arg1)
//    {
//        checkBounds(arg0, arg1, batchStartsAt, batchSize);
//        try
//        {
//            final long finalSequence = sequencer.tryNext(batchSize);
//            translateAndPublishBatch(translator, arg0, arg1, batchStartsAt, batchSize, finalSequence);
//            return true;
//        }
//        catch (InsufficientCapacityException e)
//        {
//            return false;
//        }
//    }
//


//    @Override
//    public <A, B, C> void publishEvents(EventTranslatorThreeArg<E, A, B, C> translator, A[] arg0, B[] arg1, C[] arg2)
//    {
//        publishEvents(translator, 0, arg0.length, arg0, arg1, arg2);
//    }
//


//    @Override
//    public <A, B, C> void publishEvents(
//            EventTranslatorThreeArg<E, A, B, C> translator, int batchStartsAt, int batchSize, A[] arg0, B[] arg1, C[] arg2)
//    {
//        checkBounds(arg0, arg1, arg2, batchStartsAt, batchSize);
//        final long finalSequence = sequencer.next(batchSize);
//        translateAndPublishBatch(translator, arg0, arg1, arg2, batchStartsAt, batchSize, finalSequence);
//    }
//


//    @Override
//    public <A, B, C> boolean tryPublishEvents(
//            EventTranslatorThreeArg<E, A, B, C> translator, A[] arg0, B[] arg1, C[] arg2)
//    {
//        return tryPublishEvents(translator, 0, arg0.length, arg0, arg1, arg2);
//    }
//


//    @Override
//    public <A, B, C> boolean tryPublishEvents(
//            EventTranslatorThreeArg<E, A, B, C> translator, int batchStartsAt, int batchSize, A[] arg0, B[] arg1, C[] arg2)
//    {
//        checkBounds(arg0, arg1, arg2, batchStartsAt, batchSize);
//        try
//        {
//            final long finalSequence = sequencer.tryNext(batchSize);
//            translateAndPublishBatch(translator, arg0, arg1, arg2, batchStartsAt, batchSize, finalSequence);
//            return true;
//        }
//        catch (InsufficientCapacityException e)
//        {
//            return false;
//        }
//    }
//


//    @Override
//    public void publishEvents(EventTranslatorVararg<E> translator, Object[]... args)
//    {
//        publishEvents(translator, 0, args.length, args);
//    }
//


//    @Override
//    public void publishEvents(EventTranslatorVararg<E> translator, int batchStartsAt, int batchSize, Object[]... args)
//    {
//        checkBounds(batchStartsAt, batchSize, args);
//        final long finalSequence = sequencer.next(batchSize);
//        translateAndPublishBatch(translator, batchStartsAt, batchSize, finalSequence, args);
//    }
//


//    @Override
//    public boolean tryPublishEvents(EventTranslatorVararg<E> translator, Object[]... args)
//    {
//        return tryPublishEvents(translator, 0, args.length, args);
//    }
//


//    @Override
//    public boolean tryPublishEvents(
//            EventTranslatorVararg<E> translator, int batchStartsAt, int batchSize, Object[]... args)
//    {
//        checkBounds(args, batchStartsAt, batchSize);
//        try
//        {
//            final long finalSequence = sequencer.tryNext(batchSize);
//            translateAndPublishBatch(translator, batchStartsAt, batchSize, finalSequence, args);
//            return true;
//        }
//        catch (InsufficientCapacityException e)
//        {
//            return false;
//        }
//    }


    //通知其他消费者这个序号位置可以消费了
    @Override
    public void publish(long sequence) {
        sequencer.publish(sequence);
    }

    //通知其他消费者这个hi序号位置之前的数据都可以消费了
    @Override
    public void publish(long lo, long hi) {
        sequencer.publish(lo, hi);
    }

    //环形数组中的剩余容量
    public long remainingCapacity() {
        return sequencer.remainingCapacity();
    }

    private void checkBounds(final EventTranslator<E>[] translators, final int batchStartsAt, final int batchSize) {
        checkBatchSizing(batchStartsAt, batchSize);
        batchOverRuns(translators, batchStartsAt, batchSize);
    }

    private void checkBatchSizing(int batchStartsAt, int batchSize) {
        if (batchStartsAt < 0 || batchSize < 0) {
            throw new IllegalArgumentException("Both batchStartsAt and batchSize must be positive but got: batchStartsAt " + batchStartsAt + " and batchSize " + batchSize);
        } else if (batchSize > bufferSize) {
            throw new IllegalArgumentException("The ring buffer cannot accommodate " + batchSize + " it only has space for " + bufferSize + " entities.");
        }
    }

    private <A> void checkBounds(final A[] arg0, final int batchStartsAt, final int batchSize) {
        checkBatchSizing(batchStartsAt, batchSize);
        batchOverRuns(arg0, batchStartsAt, batchSize);
    }

    private <A, B> void checkBounds(final A[] arg0, final B[] arg1, final int batchStartsAt, final int batchSize) {
        checkBatchSizing(batchStartsAt, batchSize);
        batchOverRuns(arg0, batchStartsAt, batchSize);
        batchOverRuns(arg1, batchStartsAt, batchSize);
    }

    private <A, B, C> void checkBounds(final A[] arg0, final B[] arg1, final C[] arg2, final int batchStartsAt, final int batchSize) {
        checkBatchSizing(batchStartsAt, batchSize);
        batchOverRuns(arg0, batchStartsAt, batchSize);
        batchOverRuns(arg1, batchStartsAt, batchSize);
        batchOverRuns(arg2, batchStartsAt, batchSize);
    }

    private void checkBounds(final int batchStartsAt, final int batchSize, final Object[][] args) {
        checkBatchSizing(batchStartsAt, batchSize);
        batchOverRuns(args, batchStartsAt, batchSize);
    }

    private <A> void batchOverRuns(final A[] arg0, final int batchStartsAt, final int batchSize) {
        if (batchStartsAt + batchSize > arg0.length) {
            throw new IllegalArgumentException("A batchSize of: " + batchSize + " with batchStatsAt of: " + batchStartsAt + " will overrun the available number of arguments: " + (arg0.length - batchStartsAt));
        }
    }

    private void translateAndPublish(EventTranslator<E> translator, long sequence) {
        try {
            translator.translateTo(get(sequence), sequence);
        } finally {
            sequencer.publish(sequence);
        }
    }

    private <A> void translateAndPublish(EventTranslatorOneArg<E, A> translator, long sequence, A arg0) {
        try {
            translator.translateTo(get(sequence), sequence, arg0);
        } finally {
            sequencer.publish(sequence);
        }
    }


//    private <A, B> void translateAndPublish(EventTranslatorTwoArg<E, A, B> translator, long sequence, A arg0, B arg1)
//    {
//        try
//        {
//            translator.translateTo(get(sequence), sequence, arg0, arg1);
//        }
//        finally
//        {
//            sequencer.publish(sequence);
//        }
//    }


//    private <A, B, C> void translateAndPublish(
//            EventTranslatorThreeArg<E, A, B, C> translator, long sequence,
//            A arg0, B arg1, C arg2)
//    {
//        try
//        {
//            translator.translateTo(get(sequence), sequence, arg0, arg1, arg2);
//        }
//        finally
//        {
//            sequencer.publish(sequence);
//        }
//    }


//    private void translateAndPublish(EventTranslatorVararg<E> translator, long sequence, Object... args)
//    {
//        try
//        {
//            translator.translateTo(get(sequence), sequence, args);
//        }
//        finally
//        {
//            sequencer.publish(sequence);
//        }
//    }


//    private void translateAndPublishBatch(
//            final EventTranslator<E>[] translators, int batchStartsAt,
//            final int batchSize, final long finalSequence)
//    {
//        final long initialSequence = finalSequence - (batchSize - 1);
//        try
//        {
//            long sequence = initialSequence;
//            final int batchEndsAt = batchStartsAt + batchSize;
//            for (int i = batchStartsAt; i < batchEndsAt; i++)
//            {
//                final EventTranslator<E> translator = translators[i];
//                translator.translateTo(get(sequence), sequence++);
//            }
//        }
//        finally
//        {
//            sequencer.publish(initialSequence, finalSequence);
//        }
//    }


//    private <A> void translateAndPublishBatch(
//            final EventTranslatorOneArg<E, A> translator, final A[] arg0,
//            int batchStartsAt, final int batchSize, final long finalSequence)
//    {
//        final long initialSequence = finalSequence - (batchSize - 1);
//        try
//        {
//            long sequence = initialSequence;
//            final int batchEndsAt = batchStartsAt + batchSize;
//            for (int i = batchStartsAt; i < batchEndsAt; i++)
//            {
//                translator.translateTo(get(sequence), sequence++, arg0[i]);
//            }
//        }
//        finally
//        {
//            sequencer.publish(initialSequence, finalSequence);
//        }
//    }


//    private <A, B> void translateAndPublishBatch(
//            final EventTranslatorTwoArg<E, A, B> translator, final A[] arg0,
//            final B[] arg1, int batchStartsAt, int batchSize,
//            final long finalSequence)
//    {
//        final long initialSequence = finalSequence - (batchSize - 1);
//        try
//        {
//            long sequence = initialSequence;
//            final int batchEndsAt = batchStartsAt + batchSize;
//            for (int i = batchStartsAt; i < batchEndsAt; i++)
//            {
//                translator.translateTo(get(sequence), sequence++, arg0[i], arg1[i]);
//            }
//        }
//        finally
//        {
//            sequencer.publish(initialSequence, finalSequence);
//        }
//    }


//    private <A, B, C> void translateAndPublishBatch(
//            final EventTranslatorThreeArg<E, A, B, C> translator,
//            final A[] arg0, final B[] arg1, final C[] arg2, int batchStartsAt,
//            final int batchSize, final long finalSequence)
//    {
//        final long initialSequence = finalSequence - (batchSize - 1);
//        try
//        {
//            long sequence = initialSequence;
//            final int batchEndsAt = batchStartsAt + batchSize;
//            for (int i = batchStartsAt; i < batchEndsAt; i++)
//            {
//                translator.translateTo(get(sequence), sequence++, arg0[i], arg1[i], arg2[i]);
//            }
//        }
//        finally
//        {
//            sequencer.publish(initialSequence, finalSequence);
//        }
//    }


//    private void translateAndPublishBatch(
//            final EventTranslatorVararg<E> translator, int batchStartsAt,
//            final int batchSize, final long finalSequence, final Object[][] args)
//    {
//        final long initialSequence = finalSequence - (batchSize - 1);
//        try
//        {
//            long sequence = initialSequence;
//            final int batchEndsAt = batchStartsAt + batchSize;
//            for (int i = batchStartsAt; i < batchEndsAt; i++)
//            {
//                translator.translateTo(get(sequence), sequence++, args[i]);
//            }
//        }
//        finally
//        {
//            sequencer.publish(initialSequence, finalSequence);
//        }
//    }

    @Override
    public String toString() {
        return "RingBuffer{" + "bufferSize=" + bufferSize + ", sequencer=" + sequencer + "}";
    }
}
