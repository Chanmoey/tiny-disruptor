package com.moon.distuptor;

/**
 * @author Chanmoey
 * Create at 2024/3/16
 */
public final class TimeoutException extends Exception
{
    /**
     * The efficiency saving singleton instance
     */
    public static final TimeoutException INSTANCE = new TimeoutException();

    private TimeoutException()
    {
        // Singleton
    }

    @Override
    public Throwable fillInStackTrace()
    {
        return this;
    }
}
