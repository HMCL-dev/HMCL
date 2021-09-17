package org.jackhuang.hmcl.setting;

public final class SambaException extends RuntimeException {
    public SambaException() {
    }

    public SambaException(String message) {
        super(message);
    }

    public SambaException(String message, Throwable cause) {
        super(message, cause);
    }

    public SambaException(Throwable cause) {
        super(cause);
    }
}
