package com.moon.distuptor;

/**
 * @author Chanmoey
 * Create at 2024/3/16
 */
public final class InsufficientCapacityException extends Exception {
    public static final InsufficientCapacityException INSTANCE = new InsufficientCapacityException();

    private InsufficientCapacityException() {

    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
