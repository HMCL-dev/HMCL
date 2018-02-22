package org.jackhuang.hmcl.auth.yggdrasil;

class ErrorResponse {
    private final String error;
    private final String errorMessage;
    private final String cause;

    public ErrorResponse() {
        this(null, null, null);
    }

    public ErrorResponse(String error, String errorMessage, String cause) {
        this.error = error;
        this.errorMessage = errorMessage;
        this.cause = cause;
    }

    public String getError() {
        return error;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getCause() {
        return cause;
    }
}
