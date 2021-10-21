package org.jackhuang.hmcl.launch;

public class CommandTooLongException extends RuntimeException {
    public CommandTooLongException() {
    }

    public CommandTooLongException(String message) {
        super(message);
    }

    public CommandTooLongException(String message, Throwable cause) {
        super(message, cause);
    }

    public CommandTooLongException(Throwable cause) {
        super(cause);
    }
}
