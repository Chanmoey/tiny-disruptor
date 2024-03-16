package com.moon.distuptor.dsl;

import com.moon.distuptor.ExceptionHandler;
import com.moon.distuptor.FatalExceptionHandler;

/**
 * @author Chanmoey
 * Create at 2024/3/16
 */
public class ExceptionHandlerWrapper<T> implements ExceptionHandler<T> {

    private ExceptionHandler<? super T> delegate = new FatalExceptionHandler();

    /**
     * 替换默认的异常处理器
     */
    public void switchTo(final ExceptionHandler<? super T> exceptionHandler) {
        this.delegate = exceptionHandler;
    }

    @Override
    public void handleEventException(Throwable ex, long sequence, T event) {
        delegate.handleEventException(ex, sequence, event);
    }

    @Override
    public void handleOnStartException(Throwable ex) {
        delegate.handleOnStartException(ex);
    }

    @Override
    public void handleOnShutdownException(Throwable ex) {
        delegate.handleOnShutdownException(ex);
    }
}
