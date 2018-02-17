package org.jackhuang.hmcl.auth.yggdrasil;

public class AuthlibInjectorServerInfo {
    private final String serverIp;
    private final String serverName;

    public AuthlibInjectorServerInfo(String serverIp, String serverName) {
        this.serverIp = serverIp;
        this.serverName = serverName;
    }

    public String getServerIp() {
        return serverIp;
    }

    public String getServerName() {
        return serverName;
    }
}
