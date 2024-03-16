package com.moon.distuptor;

/**
 * @author Chanmoey
 * Create at 2024/3/16
 */
public final class AlertException extends Exception {

    public static final AlertException INSTANCE = new AlertException();


    private AlertException() {
    }


    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
