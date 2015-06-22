package org.jackhuang.mojang.authlib.yggdrasil;

public class ProfileIncompleteException extends RuntimeException {

    public ProfileIncompleteException() {
    }

    public ProfileIncompleteException(String message) {
        super(message);
    }

    public ProfileIncompleteException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProfileIncompleteException(Throwable cause) {
        super(cause);
    }
}
