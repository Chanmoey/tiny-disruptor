package com.moon.distuptor;

/**
 * @author Chanmoey
 * Create at 2024/3/16
 */
public interface ExceptionHandler<T> {

    void handleEventException(Throwable ex, long sequence, T event);


    void handleOnStartException(Throwable ex);


    void handleOnShutdownException(Throwable ex);

}
