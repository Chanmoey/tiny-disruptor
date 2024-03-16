package com.moon.distuptor;

/**
 * 把Event中的多个数据替换掉
 */
public interface EventTranslatorVararg<T> {
    void translateTo(T event, long sequence, Object... args);
}
