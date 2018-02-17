package org.jackhuang.hmcl.auth.yggdrasil;

public class AuthlibInjectorServerResponse {

    private final Meta meta;

    public AuthlibInjectorServerResponse() {
        this(new Meta());
    }

    public AuthlibInjectorServerResponse(Meta meta) {
        this.meta = meta;
    }

    public Meta getMeta() {
        return meta;
    }

    public static class Meta {
        private final String serverName;

        public Meta() {
            this("");
        }

        public Meta(String serverName) {
            this.serverName = serverName;
        }

        public String getServerName() {
            return serverName;
        }
    }
}
