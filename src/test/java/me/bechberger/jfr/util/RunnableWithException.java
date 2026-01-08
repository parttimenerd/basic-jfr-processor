package me.bechberger.jfr.util;

@FunctionalInterface
public interface RunnableWithException {
    void run() throws Exception;
}