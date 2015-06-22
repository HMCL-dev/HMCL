package org.jackhuang.mojang.authlib.minecraft;

import org.jackhuang.mojang.authlib.GameProfile;
import org.jackhuang.mojang.authlib.exceptions.AuthenticationException;
import org.jackhuang.mojang.authlib.exceptions.AuthenticationUnavailableException;

public abstract interface MinecraftSessionService {

    public abstract void joinServer(GameProfile paramGameProfile, String paramString1, String paramString2)
	    throws AuthenticationException;

    public abstract GameProfile hasJoinedServer(GameProfile paramGameProfile, String paramString)
	    throws AuthenticationUnavailableException;

    public abstract GameProfile fillProfileProperties(GameProfile paramGameProfile, boolean paramBoolean);
}
