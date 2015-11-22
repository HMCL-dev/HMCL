package org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil;

public class Response {

    public String accessToken, clientToken;
    public GameProfile selectedProfile;
    public GameProfile[] availableProfiles;
    public User user;

    public String error;
    public String errorMessage;
    public String cause;
}
