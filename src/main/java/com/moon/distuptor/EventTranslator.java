package com.moon.distuptor;

/**
 * @author Chanmoey
 * Create at 2024/3/16
 */
public interface EventTranslator<T>
{

    void translateTo(T event, long sequence);
}
