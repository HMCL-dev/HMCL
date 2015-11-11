package org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.properties;

public class Property {

    public final String name;
    public final String value;
    public final String signature;

    public Property(String value, String name) {
	this(value, name, null);
    }

    public Property(String name, String value, String signature) {
	this.name = name;
	this.value = value;
	this.signature = signature;
    }
}
