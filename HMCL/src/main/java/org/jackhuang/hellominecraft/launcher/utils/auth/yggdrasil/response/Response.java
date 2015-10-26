package org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.response;

import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.GameProfile;

public class Response {

    public String accessToken;
    public String clientToken;
    public GameProfile selectedProfile;
    public GameProfile[] availableProfiles;
    public User user;
    
    public String error;
    public String errorMessage;
    public String cause;
}
