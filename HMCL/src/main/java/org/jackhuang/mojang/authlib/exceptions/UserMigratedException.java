package org.jackhuang.mojang.authlib.exceptions;

public class UserMigratedException extends InvalidCredentialsException {

    public UserMigratedException() {
    }

    public UserMigratedException(String message) {
        super(message);
    }

    public UserMigratedException(String message, Throwable cause) {
        super(message, cause);
    }

    public UserMigratedException(Throwable cause) {
        super(cause);
    }
}
