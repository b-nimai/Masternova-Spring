package com.masternova.patterns.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * <b>Dynamic proxy</b> — instead of hand-writing a proxy class per interface, the JDK GENERATES
 * one at runtime and sends every call to one handler. One handler = timing/logging for ANY
 * interface. This is exactly what Spring does for @Transactional, @Cacheable, @Async
 * (with CGLIB subclasses when there is no interface).
 */
public final class TimingProxy {

  private TimingProxy() {}

  /** Wraps {@code target} so every call through {@code type} is recorded in {@code log}. */
  public static <T> T timed(Class<T> type, T target, List<String> log) {
    InvocationHandler handler =
        (proxy, method, args) -> {
          long start = System.nanoTime();
          try {
            return method.invoke(target, args);
          } catch (InvocationTargetException e) {
            throw e.getCause(); // ⭐ unwrap: callers must see the REAL exception, not reflection's wrapper
          } finally {
            log.add(method.getName() + " " + ((System.nanoTime() - start) >= 0 ? "timed" : "?"));
          }
        };
    Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    return type.cast(proxy);
  }
}
