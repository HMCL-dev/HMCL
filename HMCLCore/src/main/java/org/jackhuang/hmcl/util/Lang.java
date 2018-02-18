/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hmcl.util;

import com.google.gson.JsonParseException;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 *
 * @author huangyuhui
 */
public final class Lang {

    private Lang() {
    }

    public static <T> Consumer<T> emptyConsumer() {
        return x -> {};
    }

    public static <T> T requireJsonNonNull(T obj) throws JsonParseException {
        return requireJsonNonNull(obj, "Json object cannot be null.");
    }

    public static <T> T requireJsonNonNull(T obj, String message) throws JsonParseException {
        if (obj == null)
            throw new JsonParseException(message);
        return obj;
    }

    @SafeVarargs
    public static <K, V> Map<K, V> mapOf(Pair<K, V>... pairs) {
        HashMap<K, V> map = new HashMap<>();
        for (Pair<K, V> pair : pairs)
            map.put(pair.getKey(), pair.getValue());
        return map;
    }

    public static <K, V> V getOrPut(Map<K, V> map, K key, Supplier<V> defaultValue) {
        V value = map.get(key);
        if (value == null) {
            V answer = defaultValue.get();
            map.put(key, answer);
            return answer;
        } else
            return value;
    }

    public static <E extends Throwable> void throwable(Throwable exception) throws E {
        throw (E) exception;
    }

    /**
     * This method will call a method without checked exceptions
     * by treating the compiler.
     *
     * If this method throws a checked exception,
     * it will still abort the application because of the exception.
     *
     * @param <T> type of argument.
     * @param <R> type of result.
     * @param function your method.
     * @return the result of the method to invoke.
     */
    public static <T, R, E extends Exception> R invoke(ExceptionalFunction<T, R, E> function, T t) {
        try {
            return function.apply(t);
        } catch (Exception e) {
            throwable(e);
            throw new Error(); // won't get to here.
        }
    }

    public static <T, R, E extends Exception> Function<T, R> hideFunction(ExceptionalFunction<T, R, E> function) {
        return r -> invoke(function, r);
    }

    public static <T, R, E extends Exception> Function<T, R> liftFunction(ExceptionalFunction<T, R, E> function) {
        return hideFunction(function);
    }

    /**
     * This method will call a method without checked exceptions
     * by treating the compiler.
     *
     * If this method throws a checked exception,
     * it will still abort the application because of the exception.
     *
     * @param <T> type of result.
     * @param supplier your method.
     * @return the result of the method to invoke.
     */
    public static <T, E extends Exception> T invoke(ExceptionalSupplier<T, E> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            throwable(e);
            throw new Error(); // won't get to here.
        }
    }

    public static <T, E extends Exception> Supplier<T> hideException(ExceptionalSupplier<T, E> supplier) {
        return () -> invoke(supplier);
    }

    public static <T, E extends Exception> Supplier<T> liftException(ExceptionalSupplier<T, E> supplier) {
        return hideException(supplier);
    }

    /**
     * This method will call a method without checked exceptions
     * by treating the compiler.
     *
     * If this method throws a checked exception,
     * it will still abort the application because of the exception.
     *
     * @param <T> type of result.
     * @param consumer your method.
     * @return the result of the method to invoke.
     */
    public static <T, E extends Exception> void invokeConsumer(ExceptionalConsumer<T, E> consumer, T t) {
        try {
            consumer.accept(t);
        } catch (Exception e) {
            throwable(e);
        }
    }

    public static <T, E extends Exception> Consumer<T> hideConsumer(ExceptionalConsumer<T, E> consumer) {
        return it -> invokeConsumer(consumer, it);
    }

    public static <T, E extends Exception> Consumer<T> liftConsumer(ExceptionalConsumer<T, E> consumer) {
        return hideConsumer(consumer);
    }

    public static <E extends Exception> boolean test(ExceptionalSupplier<Boolean, E> r) {
        try {
            return r.get();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * This method will call a method without checked exceptions
     * by treating the compiler.
     *
     * If this method throws a checked exception,
     * it will still abort the application because of the exception.
     *
     * @param r your method.
     */
    public static <E extends Exception> void invoke(ExceptionalRunnable<E> r) {
        try {
            r.run();
        } catch (Exception e) {
            throwable(e);
        }
    }

    public static <E extends Exception> Runnable hideException(ExceptionalRunnable<E> r) {
        return () -> invoke(r);
    }

    public static <E extends Exception> Runnable liftException(ExceptionalRunnable<E> r) {
        return hideException(r);
    }

    public static <E extends Exception> boolean test(ExceptionalRunnable<E> r) {
        try {
            r.run();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static <T> T ignoringException(ExceptionalSupplier<T, ?> supplier) {
        return ignoringException(supplier, null);
    }

    public static <T> T ignoringException(ExceptionalSupplier<T, ?> supplier, T defaultValue) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static void ignoringException(ExceptionalRunnable<?> runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
        }
    }

    public static <V> Optional<V> convert(Object o, Class<V> clazz) {
        if (o == null || !ReflectionHelper.isInstance(clazz, o))
            return Optional.empty();
        else
            return Optional.of((V) o);
    }

    public static <V> V convert(Object o, Class<V> clazz, V defaultValue) {
        if (o == null || !ReflectionHelper.isInstance(clazz, o))
            return defaultValue;
        else
            return (V) o;
    }

    public static <V> Optional<V> get(List<V> list, int index) {
        if (index < 0 || index >= list.size())
            return Optional.empty();
        else
            return Optional.ofNullable(list.get(index));
    }

    public static <V> Optional<V> get(Map<?, ?> map, Object key, Class<V> clazz) {
        return convert(map.get(key), clazz);
    }

    public static <V> V get(Map<?, ?> map, Object key, Class<V> clazz, V defaultValue) {
        return convert(map.get(key), clazz, defaultValue);
    }

    public static <T> List<T> merge(Collection<T> a, Collection<T> b) {
        LinkedList<T> result = new LinkedList<>();
        if (a != null)
            result.addAll(a);
        if (b != null)
            result.addAll(b);
        return result;
    }

    public static <T> List<T> merge(Collection<T> a, Collection<T> b, Collection<T> c) {
        LinkedList<T> result = new LinkedList<>();
        if (a != null)
            result.addAll(a);
        if (b != null)
            result.addAll(b);
        if (c != null)
            result.addAll(c);
        return result;
    }

    public static void executeDelayed(Runnable runnable, TimeUnit timeUnit, long timeout, boolean isDaemon) {
        thread(() -> {
            try {
                timeUnit.sleep(timeout);
                runnable.run();
            } catch (InterruptedException ignore) {
            }

        }, null, isDaemon);
    }

    public static Thread thread(Runnable runnable) {
        return thread(runnable, null);
    }

    public static Thread thread(Runnable runnable, String name) {
        return thread(runnable, name, false);
    }

    public static Thread thread(Runnable runnable, String name, boolean isDaemon) {
        Thread thread = new Thread(runnable);
        if (isDaemon)
            thread.setDaemon(true);
        if (name != null)
            thread.setName(name);
        thread.start();
        return thread;
    }

    public static <T> Iterator<T> asIterator(Enumeration<T> enumeration) {
        return new Iterator<T>() {
            @Override
            public boolean hasNext() {
                return enumeration.hasMoreElements();
            }

            @Override
            public T next() {
                return enumeration.nextElement();
            }
        };
    }

    public static <T> Iterable<T> asIterable(Enumeration<T> enumeration) {
        return () -> asIterator(enumeration);
    }

    public static int parseInt(Object string, int defaultValue) {
        try {
            return Integer.parseInt(string.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static Integer toIntOrNull(Object string) {
        try {
            if (string == null) return null;
            return Integer.parseInt(string.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Double toDoubleOrNull(Object string) {
        try {
            return Double.parseDouble(string.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SafeVarargs
    public static <T> T nonNull(T... t) {
        for (T a : t) if (a != null) return a;
        return null;
    }

    public static <T> T apply(T t, Consumer<T> consumer) {
        consumer.accept(t);
        return t;
    }
}
