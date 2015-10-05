package org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.response;

import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.GameProfile;

public class RefreshResponse extends Response {

    public String accessToken;
    public String clientToken;
    public GameProfile selectedProfile;
    public GameProfile[] availableProfiles;
    public User user;
}
