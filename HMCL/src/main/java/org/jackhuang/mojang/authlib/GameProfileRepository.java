package org.jackhuang.mojang.authlib;

public abstract interface GameProfileRepository {

    public abstract void findProfilesByNames(String[] paramArrayOfString, Agent paramAgent, ProfileLookupCallback paramProfileLookupCallback);
}
