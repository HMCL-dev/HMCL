package org.jackhuang.hmcl.auth.yggdrasil;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.JsonUtils;
import org.jackhuang.hmcl.util.NetworkUtils;

import java.io.IOException;

@Immutable
public final class AuthlibInjectorBuildInfo {

    private final int buildNumber;
    private final String url;

    public AuthlibInjectorBuildInfo() {
        this(0, "");
    }

    public AuthlibInjectorBuildInfo(int buildNumber, String url) {
        this.buildNumber = buildNumber;
        this.url = url;
    }

    public int getBuildNumber() {
        return buildNumber;
    }

    public String getUrl() {
        return url;
    }

    public static AuthlibInjectorBuildInfo requestBuildInfo() throws IOException, JsonParseException {
        return requestBuildInfo(UPDATE_URL);
    }

    public static AuthlibInjectorBuildInfo requestBuildInfo(String updateUrl) throws IOException, JsonParseException {
        return JsonUtils.fromNonNullJson(NetworkUtils.doGet(NetworkUtils.toURL(updateUrl)), AuthlibInjectorBuildInfo.class);
    }

    public static final String UPDATE_URL = "https://authlib-injector.to2mbn.org/api/buildInfo";
}
