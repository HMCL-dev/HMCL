package org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.request;

import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.GameProfile;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.YggdrasilAuthentication;

public class RefreshRequest {

    public String clientToken;
    public String accessToken;
    public GameProfile selectedProfile;
    public boolean requestUser = true;

    public RefreshRequest(YggdrasilAuthentication userAuth) {
        this(userAuth, null);
    }

    public RefreshRequest(YggdrasilAuthentication userAuth, GameProfile profile) {
        this.clientToken = userAuth.getClientToken();
        this.accessToken = userAuth.getAuthenticatedToken();
        this.selectedProfile = profile;
    }
}
