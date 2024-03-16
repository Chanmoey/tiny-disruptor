package com.moon.distuptor;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Chanmoey
 * Create at 2024/3/14
 */
public final class BatchEventProcessor<T>
        implements EventProcessor {
    //空闲状态
    private static final int IDLE = 0;
    //停止状态
    private static final int HALTED = IDLE + 1;
    //运行状态
    private static final int RUNNING = HALTED + 1;

    //该处理器的运行状态，初始默认为空闲状态
    private final AtomicInteger running = new AtomicInteger(IDLE);
    //异常处理器
    private ExceptionHandler<? super T> exceptionHandler = new FatalExceptionHandler();
    //这个成员变量会被环形数组赋值
    private final DataProvider<T> dataProvider;
    //序号屏障，每一个消费者都有一个序号屏障
    private final SequenceBarrier sequenceBarrier;
    //用户定义的消费handler，在该类的run方法中，其实执行的也是该handler中实现的方法
    private final EventHandler<? super T> eventHandler;
    //消费者的消费序号，其实就是消费的进度
    private final Sequence sequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
    //超时处理器，这个是可以让用户自己定义的，相当于一个扩展点
    private final TimeoutHandler timeoutHandler;
    //消费者开始真正工作时的一个回调接口，其实也是一个扩展点
    //用户可以定义这个接口的实现类，在消费者开始工作的时候，执行该接口实现类中定义的方法
    private final BatchStartAware batchStartAware;

    public BatchEventProcessor(
            final DataProvider<T> dataProvider,
            final SequenceBarrier sequenceBarrier,
            final EventHandler<? super T> eventHandler) {
        this.dataProvider = dataProvider;
        this.sequenceBarrier = sequenceBarrier;
        this.eventHandler = eventHandler;
        batchStartAware =
                (eventHandler instanceof BatchStartAware) ? (BatchStartAware) eventHandler : null;
        timeoutHandler =
                (eventHandler instanceof TimeoutHandler) ? (TimeoutHandler) eventHandler : null;
    }

    //得到当前消费者的消费进度，序号就是进度
    @Override
    public Sequence getSequence() {
        return sequence;
    }

    //终止该消费处理器的运行
    @Override
    public void halt() {
        //因为要终止了，所以先把状态改为停止状态
        running.set(HALTED);
        //这里会把阻塞的消费处理器唤醒，然后去响应这个终止的状态，就像是线程中断
        //注意，在第一个版本，大家顺着这个方法点进去，会发现最后在等待策略的实现类中，signalAllWhenBlocking
        //方法是一个空实现，实际上在程序中有很多等待策略，我还没有为大家引入，后面会依次引入的
        sequenceBarrier.alert();
    }

    //判断该处理器是否正在运行
    @Override
    public boolean isRunning() {
        return running.get() != IDLE;
    }

    //为该消费处理器设置异常处理器
    public void setExceptionHandler(final ExceptionHandler<? super T> exceptionHandler) {
        if (null == exceptionHandler) {
            throw new NullPointerException();
        }

        this.exceptionHandler = exceptionHandler;
    }


    @Override
    public void run() {
        //既然消费者线程已经启动了，就要把消费处理器的状态设置成运行状态了
        if (running.compareAndSet(IDLE, RUNNING)) {
            //先把消费者的中断状态设置为false
            sequenceBarrier.clearAlert();
            //这里会检查消费handler是否同时也实现了LifecycleAware接口
            //如果实现了这个接口，也会进行这个接口方法的回调，也是一个扩展点
            notifyStart();
            try {
                //再次判断状态是否正确
                if (running.get() == RUNNING) {   //如果状态正确，就执行真正的处理
                    processEvents();
                }
            } finally {
                //这里会再次执行LifecycleAware接口中的方法，LifecycleAware接口中一共有两个方法
                //一个是onStart，一个是onShutdown方法，一个在执行真正的消费者事件之前执行
                //一个在执行事件完之后执行，从这里也能看出来，这个接口有点生命周期的意思
                notifyShutdown();
                //消费事件都执行完了，所以也可以把消费处理器的状态设置成空闲状态了
                running.set(IDLE);
            }
        } else {
            if (running.get() == RUNNING) {
                throw new IllegalStateException("Thread is already running");
            } else {
                //走到这里显然没有执行消费事件，但是生命周期接口定义的方法还是要执行一下，让用户知道
                //因为生命周期接口的方法都执行了，但消费事件没有执行，这就是失败了呀
                earlyExit();
            }
        }
    }


    private void processEvents() {
        //先定义要消费的事件
        T event = null;
        //sequence是当前消费者自身的消费进度，这个get方法得到的就是当前的消费进度
        long nextSequence = sequence.get() + 1L;
        //在一个循环中开始执行真正的消费任务了
        while (true) {
            try {

                final long availableSequence = sequenceBarrier.waitFor(nextSequence);
                if (batchStartAware != null) {
                    //这个接口中的方法参数就是本次批处理的事件的个数
                    batchStartAware.onBatchStart(availableSequence - nextSequence + 1);
                }
                //下面就是批处理的逻辑了，在一个循环中判断，直到消费者要消费的进度等于刚才返回的最大的进度，就意味着
                //不能再消费了，因为生产者可能还没有继续发布事件
                while (nextSequence <= availableSequence) {
                    // 其实dataProvider就是环形数组
                    //这里就是根据序号从环形数组中把生产者发布的事件取出来消费
                    event = dataProvider.get(nextSequence);
                    //真正消费事件的方法，就是我在测试类中定义的SimpleEventHandler
                    eventHandler.onEvent(event, nextSequence, nextSequence == availableSequence);
                    //下一个要消费的进度加1
                    nextSequence++;
                }
                //消费完了之后，再把当前消费者的消费进度赋值成最新的
                sequence.set(availableSequence);
            }
            //下面之所以会出现异常，是因为上看调用的sequenceBarrier.waitFor(nextSequence)这行代码
            //因为序号屏障的waitFor方法会抛出异常，就是下面这个方法
            //long waitFor(long sequence) throws AlertException, InterruptedException, TimeoutException;
            catch (final TimeoutException e) {
                //超时异常，就用TimeoutHandler来处理，当然，前提是用户创建的handler同时也实现了这个接口
                notifyTimeout(sequence.get());
            } catch (final AlertException ex) {
                //如果序号屏障获得最大的可消费进度的过程中发现要中断消费者工作，那么就退出当前的循环
                if (running.get() != RUNNING) {
                    break;
                }
            } catch (final Throwable ex) {
                //这个就是普通的异常了，用户设置的异常处理器，就是用来处理这个异常的
                //如果是用户自己定义的异常处理器，这个方法可能实现的比较普通，并不会抛出异常，程序也就不会受到影响仍然会进入下一轮循环
                //如果是用程序默认的FatalExceptionHandler异常处理器，在handleEventException方法中，处理异常的过程中
                //会再次抛出异常  throw new RuntimeException(ex) 就像这样
                //可是现在并没有catch再抓住这个异常了，所以当前消费者就会直接被强制终止了，这就是为什么要暴露给用户一个接口
                //让用户自己去定义一个异常类
                exceptionHandler.handleEventException(ex, nextSequence, event);
                //处理异常之后，会发现把当前要消费进度更新到sequence中了，这也就意味着，一旦出现异常
                //虽然将要消费的事件还未消费，但是程序内部会默认为已经消费了，总之，会跳过这个事件了
                sequence.set(nextSequence);
                nextSequence++;
            }
        }
    }

    //该方法就是用来回调LifecycleAware接口中的方法的
    private void earlyExit() {
        notifyStart();
        notifyShutdown();
    }

    //处理超时异常的方法
    private void notifyTimeout(final long availableSequence) {
        try {
            if (timeoutHandler != null) {
                timeoutHandler.onTimeout(availableSequence);
            }
        } catch (Throwable e) {
            exceptionHandler.handleEventException(e, availableSequence, null);
        }
    }

    //回调LifecycleAware接口的onStart方法
    private void notifyStart() {
        if (eventHandler instanceof LifecycleAware) {
            try {
                ((LifecycleAware) eventHandler).onStart();
            } catch (final Throwable ex) {
                exceptionHandler.handleOnStartException(ex);
            }
        }
    }

    //回调LifecycleAware接口的onShutdown方法
    private void notifyShutdown() {
        if (eventHandler instanceof LifecycleAware) {
            try {
                ((LifecycleAware) eventHandler).onShutdown();
            } catch (final Throwable ex) {
                exceptionHandler.handleOnShutdownException(ex);
            }
        }
    }
}

