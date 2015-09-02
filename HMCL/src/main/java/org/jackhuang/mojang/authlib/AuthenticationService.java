package org.jackhuang.mojang.authlib;

public abstract interface AuthenticationService {

    public abstract UserAuthentication createUserAuthentication(Agent paramAgent);
}
