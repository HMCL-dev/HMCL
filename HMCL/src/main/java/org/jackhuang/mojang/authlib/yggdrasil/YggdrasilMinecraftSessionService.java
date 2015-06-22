package org.jackhuang.mojang.authlib.yggdrasil;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.jackhuang.hellominecraft.logging.logger.Logger;
import org.jackhuang.hellominecraft.utils.NetUtils;
import org.jackhuang.mojang.authlib.GameProfile;
import org.jackhuang.mojang.authlib.HttpAuthenticationService;
import org.jackhuang.mojang.authlib.exceptions.AuthenticationException;
import org.jackhuang.mojang.authlib.exceptions.AuthenticationUnavailableException;
import org.jackhuang.mojang.authlib.minecraft.HttpMinecraftSessionService;
import org.jackhuang.mojang.authlib.yggdrasil.request.JoinMinecraftServerRequest;
import org.jackhuang.mojang.authlib.yggdrasil.response.HasJoinedMinecraftServerResponse;
import org.jackhuang.mojang.authlib.yggdrasil.response.MinecraftProfilePropertiesResponse;
import org.jackhuang.mojang.authlib.yggdrasil.response.Response;
import org.jackhuang.mojang.util.UUIDTypeAdapter;

public class YggdrasilMinecraftSessionService extends HttpMinecraftSessionService {

    private static final Logger LOGGER = new Logger("YggdrasilMinecraftSessionService");
    private static final String BASE_URL = "https://sessionserver.mojang.com/session/minecraft/";
    private static final URL JOIN_URL = NetUtils.constantURL(BASE_URL + "join");
    private static final URL CHECK_URL = NetUtils.constantURL(BASE_URL + "hasJoined");

    protected YggdrasilMinecraftSessionService(YggdrasilAuthenticationService authenticationService) {
	super(authenticationService);
    }

    @Override
    public void joinServer(GameProfile profile, String authenticationToken, String serverId) throws AuthenticationException {
	JoinMinecraftServerRequest request = new JoinMinecraftServerRequest();
	request.accessToken = authenticationToken;
	request.selectedProfile = profile.getId();
	request.serverId = serverId;

	getAuthenticationService().makeRequest(JOIN_URL, request, Response.class);
    }

    @Override
    public GameProfile hasJoinedServer(GameProfile user, String serverId) throws AuthenticationUnavailableException {
	Map arguments = new HashMap();

	arguments.put("username", user.getName());
	arguments.put("serverId", serverId);

	URL url = NetUtils.concatenateURL(CHECK_URL, HttpAuthenticationService.buildQuery(arguments));
	try {
	    HasJoinedMinecraftServerResponse response = (HasJoinedMinecraftServerResponse) getAuthenticationService().makeRequest(url, null, HasJoinedMinecraftServerResponse.class);

	    if ((response != null) && (response.getId() != null)) {
		GameProfile result = new GameProfile(response.getId(), user.getName());

		if (response.getProperties() != null) {
		    result.getProperties().putAll(response.getProperties());
		}

		return result;
	    }
	    return null;
	} catch (AuthenticationUnavailableException e) {
	    throw e;
	} catch (AuthenticationException e) {
	}
	return null;
    }
    
    @Override
    public GameProfile fillProfileProperties(GameProfile profile, boolean requireSecure) {
	if (profile.getId() == null) {
	    return profile;
	}
	try {
	    URL url = NetUtils.constantURL(new StringBuilder().append("https://sessionserver.mojang.com/session/minecraft/profile/").append(UUIDTypeAdapter.fromUUID(profile.getId())).toString());
	    url = NetUtils.concatenateURL(url, new StringBuilder().append("unsigned=").append(!requireSecure).toString());
	    MinecraftProfilePropertiesResponse response = (MinecraftProfilePropertiesResponse) getAuthenticationService().makeRequest(url, null, MinecraftProfilePropertiesResponse.class);

	    if (response == null) {
		LOGGER.debug(new StringBuilder().append("Couldn't fetch profile properties for ").append(profile).append(" as the profile does not exist").toString());
		return profile;
	    }
	    GameProfile result = new GameProfile(response.getId(), response.getName());
	    result.getProperties().putAll(response.getProperties());
	    profile.getProperties().putAll(response.getProperties());
	    LOGGER.debug(new StringBuilder().append("Successfully fetched profile properties for ").append(profile).toString());
	    return result;
	} catch (Exception e) {
	    LOGGER.warn(new StringBuilder().append("Couldn't look up profile properties for ").append(profile).toString(), e);
	}
	return profile;
    }

    @Override
    public YggdrasilAuthenticationService getAuthenticationService() {
	return (YggdrasilAuthenticationService) super.getAuthenticationService();
    }
}