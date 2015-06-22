package org.jackhuang.mojang.authlib.yggdrasil.response;

public class Response {

    private String error;
    private String errorMessage;
    private String cause;

    public String getError() {
        return this.error;
    }

    public String getCause() {
        return this.cause;
    }

    public String getErrorMessage() {
        return this.errorMessage;
    }
}
