package org.jackhuang.hmcl.util.gson;

public @interface JsonSubtype {
    Class<?> clazz();

    String name();
}
