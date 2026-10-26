// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.javafx;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jackhuang.hmcl.util.function.ExceptionalFunction;

import javafx.util.StringConverter;

/**
 * @author yushijinhun
 */
public final class SafeStringConverter<S extends T, T> extends StringConverter<T> {

    public static SafeStringConverter<Integer, Number> fromInteger() {
        return new SafeStringConverter<Integer, Number>(Integer::parseInt, NumberFormatException.class)
                .fallbackTo(0);
    }

    public static SafeStringConverter<Double, Number> fromDouble() {
        return new SafeStringConverter<Double, Number>(Double::parseDouble, NumberFormatException.class)
                .fallbackTo(0.0);
    }

    public static SafeStringConverter<Double, Number> fromFiniteDouble() {
        return new SafeStringConverter<Double, Number>(Double::parseDouble, NumberFormatException.class)
                .restrict(Double::isFinite)
                .fallbackTo(0.0);
    }

    private final ExceptionalFunction<String, S, ?> converter;
    private final Class<?> malformedExceptionClass;
    private S fallbackValue = null;
    private final List<Predicate<S>> restrictions = new ArrayList<>();

    public <E extends Exception> SafeStringConverter(ExceptionalFunction<String, S, E> converter, Class<E> malformedExceptionClass) {
        this.converter = converter;
        this.malformedExceptionClass = malformedExceptionClass;
    }

    @Override
    public String toString(T object) {
        return object == null ? "" : object.toString();
    }

    @Override
    public S fromString(String string) {
        return tryParse(string).orElse(fallbackValue);
    }

    private Optional<S> tryParse(String string) {
        if (string == null) {
            return Optional.empty();
        }

        S converted;
        try {
            converted = converter.apply(string);
        } catch (Exception e) {
            if (malformedExceptionClass.isInstance(e)) {
                return Optional.empty();
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        }

        if (!filter(converted)) {
            return Optional.empty();
        }

        return Optional.of(converted);
    }

    private boolean filter(S value) {
        for (Predicate<S> restriction : restrictions) {
            if (!restriction.test(value)) {
                return false;
            }
        }
        return true;
    }

    public SafeStringConverter<S, T> fallbackTo(S fallbackValue) {
        this.fallbackValue = fallbackValue;
        return this;
    }

    public SafeStringConverter<S, T> restrict(Predicate<S> condition) {
        this.restrictions.add(condition);
        return this;
    }

    public Predicate<String> asPredicate() {
        return string -> tryParse(string).isPresent();
    }

    public SafeStringConverter<S, T> asPredicate(Consumer<Predicate<String>> consumer) {
        consumer.accept(asPredicate());
        return this;
    }
}
