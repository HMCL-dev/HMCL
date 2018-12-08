/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
public class SafeStringConverter<S extends T, T> extends StringConverter<T> {

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

    private ExceptionalFunction<String, S, ?> converter;
    private Class<?> malformedExceptionClass;
    private S fallbackValue = null;
    private List<Predicate<S>> restrictions = new ArrayList<>();

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

    protected boolean filter(S value) {
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
