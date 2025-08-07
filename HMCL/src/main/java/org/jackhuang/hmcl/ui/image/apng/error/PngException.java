// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.error;

/**
 * All exceptions in the library are a PngException or subclass of it.
 */
public class PngException extends Exception {
    int code;

    public PngException(int code, String message) {
        super(message);
        this.code = code;
    }

    public PngException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
