package com.moon.distuptor.dsl;


import com.moon.distuptor.BatchEventProcessor;
import com.moon.distuptor.EventFactory;
import com.moon.distuptor.EventHandler;
import com.moon.distuptor.EventProcessor;
import com.moon.distuptor.EventTranslatorOneArg;
import com.moon.distuptor.ExceptionHandler;
import com.moon.distuptor.RingBuffer;
import com.moon.distuptor.Sequence;
import com.moon.distuptor.SequenceBarrier;
import com.moon.distuptor.TimeoutException;
import com.moon.distuptor.WaitStrategy;
import com.moon.distuptor.util.Util;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 相当于Disruptor这个框架的启动类
 *
 * @author Chanmoey
 * Create at 2024/3/15
 */
public class Disruptor<T> {

    private final RingBuffer<T> ringBuffer;

    /**
     * 负责创建消费者线程
     */
    private final Executor executor;

    private final ConsumerRepository<T> consumerRepository = new ConsumerRepository<>();

    /**
     * 保证Disruptor只能启动一次
     */
    private final AtomicBoolean started = new AtomicBoolean(false);

    private ExceptionHandler<? super T> exceptionHandler = new ExceptionHandlerWrapper<>();

    public Disruptor(final EventFactory<T> eventFactory, final int ringBufferSize, final ThreadFactory threadFactory, final ProducerType producerType, final WaitStrategy waitStrategy) {
        this(
                //第一个参数为生产者类型，分单生产者模式和多生产者模式
                //第二个参数就是事件创建工厂，其实这每一个事件就是消费者要消费的东西，也就是消费者线程要执行的任务
                //第三个参数就是RingBuffer的容量
                //第四个参数就是等待策略
                //在这里创建真正存储数据的容器RingBuffer
                RingBuffer.create(producerType, eventFactory, ringBufferSize, waitStrategy),
                //创建线程工厂
                new BasicExecutor(threadFactory));
    }

    private Disruptor(final RingBuffer<T> ringBuffer, final Executor executor) {
        this.ringBuffer = ringBuffer;
        this.executor = executor;
    }

    @SuppressWarnings("varargs")
    @SafeVarargs
    public final EventHandlerGroup<T> handleEventsWith(final EventHandler<? super T>... handlers) {
        //注意，下面方法的第一个参数为数组，这里默认使用的是空数组，这个数组的作用会在后面的版本讲到，现在先不解释了
        //这个方法就会把消费事件包装在事件处理器中，一个消费者对应着一个事件处理器，一个事件处理器最终会交给一个线程来执行
        return createEventProcessors(new Sequence[0], handlers);
    }

    public EventHandlerGroup<T> handleEventsWith(final EventProcessor... processors) {
        for (final EventProcessor processor : processors) {   //把EventProcessor对象添加到消费者仓库中
            consumerRepository.add(processor);
        }
        //创建一个存放消费者的消费进度的数组
        final Sequence[] sequences = new Sequence[processors.length];
        for (int i = 0; i < processors.length; i++) {   //得到每一个消费者的消费进度
            sequences[i] = processors[i].getSequence();
        }
        //把这些新添加进来的消费者的消费进度添加到消费者序列中
        //这个方法其实会调用到ringBuffer对象中，然后在ringBuffer对象中继续调用到
        //Sequencer这个序号生成器中，因为ringBuffer必须要知道消费者序列中最慢的那个消费进度
        //这样才能保证给生产者分配可使用的序号时，保证不会覆盖尚未被消费的数据
        ringBuffer.addGatingSequences(sequences);
        //把这些新添加进来的消费者放到一个事件处理器组中
        return new EventHandlerGroup<>(this, consumerRepository, Util.getSequencesFor(processors));
    }

    public void setDefaultExceptionHandler(final ExceptionHandler<? super T> exceptionHandler) {
        checkNotStarted();
        //这里就是判断一下，是否已经设置过用户定义的异常处理器了，因为用户定义的异常处理器
        //会被包装成ExceptionHandlerWrapper使用，如果异常处理器已经是ExceptionHandlerWrapper类型了
        //说明已经设置过了
        if (!(this.exceptionHandler instanceof ExceptionHandlerWrapper)) {
            throw new IllegalStateException("setDefaultExceptionHandler can not be used after handleExceptionsWith");
        }
        //这里就是真正设置异常处理器
        ((ExceptionHandlerWrapper<T>) this.exceptionHandler).switchTo(exceptionHandler);
    }

    public <A> void publishEvent(final EventTranslatorOneArg<T, A> eventTranslator, final A arg) {
        ringBuffer.publishEvent(eventTranslator, arg);
    }

    public RingBuffer<T> start() {
        checkOnlyStartedOnce();
        for (final ConsumerInfo consumerInfo : consumerRepository) {
            consumerInfo.start(executor);
        }

        return ringBuffer;
    }

    public void halt() {
        for (final ConsumerInfo consumerInfo : consumerRepository) {
            consumerInfo.halt();
        }
    }

    public void shutdown() {
        try {   //这个是真正终止程序的方法，这里面的第一个参数是-1，可见是没有超时时间的
            // 会等到程序中所有的消费者消费完所有事件才会退出程序
            shutdown(-1, TimeUnit.MILLISECONDS);
        } catch (final TimeoutException e) {
            exceptionHandler.handleOnShutdownException(e);
        }
    }

    public void shutdown(final long timeout, final TimeUnit timeUnit) throws TimeoutException {   //这里先计算出超时时间
        final long timeOutAt = System.currentTimeMillis() + timeUnit.toMillis(timeout);
        //这里会一直判断程序中的消费者是否消费完了所有的事件
        while (hasBacklog()) {
            //如果超时了就抛出异常
            if (timeout >= 0 && System.currentTimeMillis() > timeOutAt) {
                throw TimeoutException.INSTANCE;
            }
        }
        //循环结束了就开始终止每一个运行的消费者线程
        halt();
    }

    public RingBuffer<T> getRingBuffer() {
        return ringBuffer;
    }

    //得到当前的生产者序号
    public long getCursor() {
        return ringBuffer.getCursor();
    }

    //获得环形数组存储数据的个数
    public long getBufferSize() {
        return ringBuffer.getBufferSize();
    }

    //根据序列号取出环形数组对应的生产者事件
    public T get(final long sequence) {
        return ringBuffer.get(sequence);
    }

    //根据handler获得消费者的序号屏障
    public SequenceBarrier getBarrierFor(final EventHandler<T> handler) {
        return consumerRepository.getBarrierFor(handler);
    }

    public long getSequenceValueFor(final EventHandler<T> b1) {
        return consumerRepository.getSequenceFor(b1).get();
    }

    private boolean hasBacklog() {
        //得到最新的生产者的序号
        final long cursor = ringBuffer.getCursor();
        //遍历每一个消费者
        for (final Sequence consumer : consumerRepository.getLastSequenceInChain(false)) {
            //判断每一个消费者的消费序号是否小于最新的生产者的序号
            if (cursor > consumer.get()) {
                //如果有一个小于，则说明还没有消费完，所以返回true，
                return true;
            }
        }
        return false;
    }

    public EventHandlerGroup<T> createEventProcessors(
            final Sequence[] barrierSequences,
            final EventHandler<? super T>[] eventHandlers) {
        //检查Disruptor是否启动过，因为要保证Disruptor只能启动一次
        //创建EventProcessor对象的动作肯定要在Disruptor启动之前，所以这里必须检查一下
        checkNotStarted();
        //定义一个Sequence数组，Sequence其实就是消费者的消费进度，也可以说是消费者的消费序号
        //这个数组的长度就是用户定义的eventHandlers的长度
        final Sequence[] processorSequences = new Sequence[eventHandlers.length];

        final SequenceBarrier barrier = ringBuffer.newBarrier(barrierSequences);
        //把用户定义的每一个eventHandlers包装成BatchEventProcessor对象
        for (int i = 0, eventHandlersLength = eventHandlers.length; i < eventHandlersLength; i++) {
            final EventHandler<? super T> eventHandler = eventHandlers[i];

            final BatchEventProcessor<T> batchEventProcessor =
                    new BatchEventProcessor<>(ringBuffer, barrier, eventHandler);
            //判断异常处理器是否为空
            if (exceptionHandler != null) {   //不为空则设置异常处理器
                batchEventProcessor.setExceptionHandler(exceptionHandler);
            }
            //把创建好的batchEventProcessor对象添加到消费者仓库中
            consumerRepository.add(batchEventProcessor, eventHandler, barrier);
            //这时候每一个消费者的消费序号已经初始化好了，直接可以从batchEventProcessor对象中得到
            //然后就可以放到最上面定义的数组中了
            processorSequences[i] = batchEventProcessor.getSequence();
        }

        updateGatingSequencesForNextInChain(barrierSequences, processorSequences);
        //EventHandlerGroup在第一版本还用不到，所以暂且先不讲解了，只要程序先不报错就行
        //其实也可以简单说一下，其实就是把这次添加进来的所有消费者handler当作一组了，所以自然要封装带一个group中
        return new EventHandlerGroup<>(this, consumerRepository, processorSequences);
    }

    private void updateGatingSequencesForNextInChain(final Sequence[] barrierSequences, final Sequence[] processorSequences) {
        if (processorSequences.length > 0) {

            ringBuffer.addGatingSequences(processorSequences);

            for (final Sequence barrierSequence : barrierSequences) {
                ringBuffer.removeGatingSequence(barrierSequence);
            }
            consumerRepository.unMarkEventProcessorsAsEndOfChain(barrierSequences);
        }
    }

    EventHandlerGroup<T> createEventProcessors(
            final Sequence[] barrierSequences, final EventProcessorFactory<T>[] processorFactories) {
        final EventProcessor[] eventProcessors = new EventProcessor[processorFactories.length];
        for (int i = 0; i < processorFactories.length; i++) {
            eventProcessors[i] = processorFactories[i].createEventProcessor(ringBuffer, barrierSequences);
        }
        return handleEventsWith(eventProcessors);
    }


    //检查程序是否启动了，如果已经启动就抛出异常
    private void checkNotStarted() {
        if (started.get()) {
            throw new IllegalStateException("All event handlers must be added before calling starts.");
        }
    }

    //确保程序只启动一次
    private void checkOnlyStartedOnce() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("Disruptor.start() must only be called once.");
        }
    }

    @Override
    public String toString() {
        return "Disruptor{" +
                "ringBuffer=" + ringBuffer +
                ", started=" + started +
                ", executor=" + executor +
                '}';
    }
}
