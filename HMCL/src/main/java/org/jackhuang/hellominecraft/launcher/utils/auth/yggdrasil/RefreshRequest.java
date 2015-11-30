package org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil;

public class RefreshRequest {

    public String clientToken;
    public String accessToken;
    public GameProfile selectedProfile;
    public boolean requestUser = true;

    public RefreshRequest(String accessToken, String clientToken) {
        this.clientToken = clientToken;
        this.accessToken = accessToken;
    }
}
