/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.util;

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

    /**
     * Construct a mutable map by given key-value pairs.
     * @param pairs entries in the new map
     * @param <K> the type of keys
     * @param <V> the type of values
     * @return the map which contains data in {@code pairs}.
     */
    @SafeVarargs
    public static <K, V> Map<K, V> mapOf(Pair<K, V>... pairs) {
        HashMap<K, V> map = new HashMap<>();
        for (Pair<K, V> pair : pairs)
            map.put(pair.getKey(), pair.getValue());
        return map;
    }

    @SafeVarargs
    public static <T> List<T> immutableListOf(T... elements) {
        return Collections.unmodifiableList(Arrays.asList(elements));
    }

    public static <K, V> V computeIfAbsent(Map<K, V> map, K key, Supplier<V> computingFunction) {
        V value = map.get(key);
        if (value == null) {
            V newValue = computingFunction.get();
            map.put(key, newValue);
            return newValue;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwable(Throwable exception) throws E {
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

    public static <T, R, E extends Exception> Function<T, R> liftFunction(ExceptionalFunction<T, R, E> function) throws E {
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

    public static <T, E extends Exception> Supplier<T> liftException(ExceptionalSupplier<T, E> supplier) throws E {
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

    public static <T, E extends Exception> Consumer<T> liftConsumer(ExceptionalConsumer<T, E> consumer) throws E {
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
        } catch (Exception ignore) {
            return defaultValue;
        }
    }

    public static void ignoringException(ExceptionalRunnable<?> runnable) {
        try {
            runnable.run();
        } catch (Exception ignore) {
        }
    }

    /**
     * Cast {@code obj} to V dynamically.
     * @param obj the object reference to be cast.
     * @param clazz the class reference of {@code V}.
     * @param <V> the type that {@code obj} is being cast to.
     * @return {@code obj} in the type of {@code V}.
     */
    public static <V> Optional<V> tryCast(Object obj, Class<V> clazz) {
        if (clazz.isInstance(obj)) {
            return Optional.of(clazz.cast(obj));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Get the element at the specific position {@code index} in {@code list}.
     *
     * @param index the index of element to be return
     * @param <V> the type of elements in {@code list}
     * @return the element at the specific position, null if index is out of bound.
     */
    public static <V> Optional<V> get(List<V> list, int index) {
        if (index < 0 || index >= list.size())
            return Optional.empty();
        else
            return Optional.ofNullable(list.get(index));
    }

    /**
     * Join two collections into one list.
     *
     * @param a one collection, to be joined.
     * @param b another collection to be joined.
     * @param <T> the super type of elements in {@code a} and {@code b}
     * @return the joint collection
     */
    public static <T> List<T> merge(Collection<? extends T> a, Collection<? extends T> b) {
        List<T> result = new ArrayList<>();
        if (a != null)
            result.addAll(a);
        if (b != null)
            result.addAll(b);
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

    /**
     * Start a thread invoking {@code runnable} immediately.
     * @param runnable code to run.
     * @return the reference of the started thread
     */
    public static Thread thread(Runnable runnable) {
        return thread(runnable, null);
    }

    /**
     * Start a thread invoking {@code runnable} immediately.
     * @param runnable code to run
     * @param name the name of thread
     * @return the reference of the started thread
     */
    public static Thread thread(Runnable runnable, String name) {
        return thread(runnable, name, false);
    }

    /**
     * Start a thread invoking {@code runnable} immediately.
     * @param runnable code to run
     * @param name the name of thread
     * @param isDaemon true if thread will be terminated when only daemon threads are running.
     * @return the reference of the started thread
     */
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

    /**
     * Find the first non-null reference in given list.
     * @param t nullable references list.
     * @param <T> the type of nullable references
     * @return the first non-null reference.
     */
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
