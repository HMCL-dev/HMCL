package org.jackhuang.hmcl.launch;

public final class ExecutionPolicyLimitException extends RuntimeException {
    public ExecutionPolicyLimitException() {
    }

    public ExecutionPolicyLimitException(String message) {
        super(message);
    }

    public ExecutionPolicyLimitException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExecutionPolicyLimitException(Throwable cause) {
        super(cause);
    }
}
