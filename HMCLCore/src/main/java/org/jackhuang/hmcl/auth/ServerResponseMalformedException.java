package org.jackhuang.hmcl.auth;

public class ServerResponseMalformedException extends AuthenticationException {
    public ServerResponseMalformedException() {
    }

    public ServerResponseMalformedException(String message) {
        super(message);
    }

    public ServerResponseMalformedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ServerResponseMalformedException(Throwable cause) {
        super(cause);
    }
}
