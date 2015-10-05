package org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.logging.logger.Logger;
import org.jackhuang.hellominecraft.utils.ArrayUtils;
import org.jackhuang.hellominecraft.utils.NetUtils;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.properties.Property;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.properties.PropertyMap;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.request.AuthenticationRequest;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.request.RefreshRequest;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.response.AuthenticationResponse;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.response.RefreshResponse;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.response.User;

public class YggdrasilUserAuthentication {

    protected static final String STORAGE_KEY_PROFILE_NAME = "displayName";
    protected static final String STORAGE_KEY_PROFILE_ID = "uuid";
    protected static final String STORAGE_KEY_PROFILE_PROPERTIES = "profileProperties";
    protected static final String STORAGE_KEY_USER_NAME = "username";
    protected static final String STORAGE_KEY_USER_ID = "userid";
    protected static final String STORAGE_KEY_USER_PROPERTIES = "userProperties";
    private final YggdrasilAuthenticationService authenticationService;
    private final PropertyMap userProperties = new PropertyMap();
    private String userid;
    private String username;
    private String password;
    private GameProfile selectedProfile;

    public void setUsername(String username) {
        if ((isLoggedIn()) && (canPlayOnline()))
            throw new IllegalStateException("Cannot change username whilst logged in & online");

        this.username = username;
    }

    public void setPassword(String password) {
        if ((isLoggedIn()) && (canPlayOnline()) && (StrUtils.isNotBlank(password)))
            throw new IllegalStateException("Cannot set password whilst logged in & online");

        this.password = password;
    }

    protected String getUsername() {
        return this.username;
    }

    protected String getPassword() {
        return this.password;
    }

    protected void setSelectedProfile(GameProfile selectedProfile) {
        this.selectedProfile = selectedProfile;
    }

    public GameProfile getSelectedProfile() {
        return this.selectedProfile;
    }

    public YggdrasilAuthenticationService getAuthenticationService() {
        return this.authenticationService;
    }

    public String getUserID() {
        return this.userid;
    }

    public PropertyMap getUserProperties() {
        if (isLoggedIn()) {
            PropertyMap result = new PropertyMap();
            result.putAll(getModifiableUserProperties());
            return result;
        }
        return new PropertyMap();
    }

    protected PropertyMap getModifiableUserProperties() {
        return this.userProperties;
    }

    protected void setUserid(String userid) {
        this.userid = userid;
    }

    private static final Logger LOGGER = new Logger("YggdrasilUserAuthentication");
    private static final String BASE_URL = "https://authserver.mojang.com/";
    private static final URL ROUTE_AUTHENTICATE = NetUtils.constantURL(BASE_URL + "authenticate");
    private static final URL ROUTE_REFRESH = NetUtils.constantURL(BASE_URL + "refresh");
    private static final String STORAGE_KEY_ACCESS_TOKEN = "accessToken";
    private GameProfile[] profiles;
    private String accessToken;
    private boolean isOnline;

    public YggdrasilUserAuthentication(YggdrasilAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    public boolean canLogIn() {
        return (!canPlayOnline()) && (StrUtils.isNotBlank(getUsername())) && ((StrUtils.isNotBlank(getPassword())) || (StrUtils.isNotBlank(getAuthenticatedToken())));
    }

    public void logIn() throws AuthenticationException {
        if (StrUtils.isBlank(getUsername()))
            throw new AuthenticationException(C.i18n("login.invalid_username"));

        if (StrUtils.isNotBlank(getAuthenticatedToken()))
            logInWithToken();
        else if (StrUtils.isNotBlank(getPassword()))
            logInWithPassword();
        else
            throw new AuthenticationException(C.i18n("login.invalid_password"));
    }

    protected void logInWithPassword() throws AuthenticationException {
        if (StrUtils.isBlank(getUsername()))
            throw new AuthenticationException(C.i18n("login.invalid_username"));
        if (StrUtils.isBlank(getPassword()))
            throw new AuthenticationException(C.i18n("login.invalid_password"));

        LOGGER.info("Logging in with username & password");

        AuthenticationRequest request = new AuthenticationRequest(this, getUsername(), getPassword());
        AuthenticationResponse response = (AuthenticationResponse) getAuthenticationService().makeRequest(ROUTE_AUTHENTICATE, request, AuthenticationResponse.class);

        if (!response.clientToken.equals(getAuthenticationService().getClientToken()))
            throw new AuthenticationException(C.i18n("login.changed_client_token"));

        User user = response.user;

        setUserid(user != null && user.id != null ? user.id : getUsername());

        this.isOnline = true;
        this.accessToken = response.accessToken;

        this.profiles = response.availableProfiles;
        setSelectedProfile(response.selectedProfile);
        getModifiableUserProperties().clear();

        updateUserProperties(user);
    }

    protected void updateUserProperties(User user) {
        if (user == null)
            return;

        if (user.properties != null)
            getModifiableUserProperties().putAll(user.properties);
    }

    protected void logInWithToken() throws AuthenticationException {
        if (StrUtils.isBlank(getUserID()))
            if (StrUtils.isBlank(getUsername()))
                setUserid(getUsername());
            else
                throw new AuthenticationException(C.i18n("login.invalid_uuid_and_username"));
        if (StrUtils.isBlank(getAuthenticatedToken()))
            throw new AuthenticationException(C.i18n("login.invalid_access_token"));

        LOGGER.info("Logging in with access token");

        RefreshRequest request = new RefreshRequest(this);
        RefreshResponse response = (RefreshResponse) getAuthenticationService().makeRequest(ROUTE_REFRESH, request, RefreshResponse.class);

        if (!response.clientToken.equals(getAuthenticationService().getClientToken()))
            throw new AuthenticationException(C.i18n("login.changed_client_token"));

        setUserid(response.user != null && response.user.id != null ? response.user.id : getUsername());

        this.isOnline = true;
        this.accessToken = response.accessToken;
        this.profiles = response.availableProfiles;
        setSelectedProfile(response.selectedProfile);
        getModifiableUserProperties().clear();

        updateUserProperties(response.user);
    }

    public void logOut() {
        this.password = null;
        this.userid = null;
        setSelectedProfile(null);
        getModifiableUserProperties().clear();

        this.accessToken = null;
        this.profiles = null;
        this.isOnline = false;
    }

    public GameProfile[] getAvailableProfiles() {
        return this.profiles;
    }

    public boolean isLoggedIn() {
        return StrUtils.isNotBlank(this.accessToken);
    }

    public boolean canPlayOnline() {
        return isLoggedIn() && getSelectedProfile() != null && this.isOnline;
    }

    public void selectGameProfile(GameProfile profile) throws AuthenticationException {
        if (!isLoggedIn())
            throw new AuthenticationException(C.i18n("login.profile.not_logged_in"));
        if (getSelectedProfile() != null)
            throw new AuthenticationException(C.i18n("login.profile.selected"));
        if (profile == null || !ArrayUtils.contains(this.profiles, profile))
            throw new IllegalArgumentException("Invalid profile '" + profile + "'");

        RefreshRequest request = new RefreshRequest(this, profile);
        RefreshResponse response = (RefreshResponse) getAuthenticationService().makeRequest(ROUTE_REFRESH, request, RefreshResponse.class);

        if (!response.clientToken.equals(getAuthenticationService().getClientToken()))
            throw new AuthenticationException(C.i18n("login.changed_client_token"));

        this.isOnline = true;
        this.accessToken = response.accessToken;
        setSelectedProfile(response.selectedProfile);
    }

    public void loadFromStorage(Map<String, Object> credentials) {
        logOut();

        setUsername((String) credentials.get("username"));

        if (credentials.containsKey("userid"))
            this.userid = (String) credentials.get("userid");
        else
            this.userid = this.username;

        if (credentials.containsKey("userProperties"))
            try {
                List<Map> list = (List<Map>) credentials.get("userProperties");

                for (Map propertyMap : list) {
                    String name = (String) propertyMap.get("name");
                    String value = (String) propertyMap.get("value");
                    String signature = (String) propertyMap.get("signature");

                    getModifiableUserProperties().put(name, new Property(name, value, signature));
                }
            } catch (Throwable t) {
                LOGGER.warn("Couldn't deserialize user properties", t);
            }

        if ((credentials.containsKey("displayName")) && (credentials.containsKey("uuid"))) {
            GameProfile profile = new GameProfile(UUIDTypeAdapter.fromString((String) credentials.get("uuid")), (String) credentials.get("displayName"));
            if (credentials.containsKey("profileProperties"))
                try {
                    List<Map<String, String>> list = (List<Map<String, String>>) credentials.get("profileProperties");
                    for (Map<String, String> propertyMap : list) {
                        String name = propertyMap.get("name");
                        String value = propertyMap.get("value");
                        String signature = propertyMap.get("signature");

                        profile.properties.put(name, new Property(name, value, signature));
                    }
                } catch (Throwable t) {
                    LOGGER.warn("Couldn't deserialize profile properties", t);
                }
            setSelectedProfile(profile);
        }

        this.accessToken = (String) credentials.get(STORAGE_KEY_ACCESS_TOKEN);
    }

    public Map<String, Object> saveForStorage() {
        Map result = new HashMap();

        if (getUsername() != null)
            result.put("username", getUsername());
        if (getUserID() != null)
            result.put("userid", getUserID());
        else if (getUsername() != null)
            result.put("username", getUsername());

        if (!getUserProperties().isEmpty()) {
            List properties = new ArrayList();
            for (Property userProperty : getUserProperties().values()) {
                Map<String, String> property = new HashMap<>();
                property.put("name", userProperty.name);
                property.put("value", userProperty.value);
                property.put("signature", userProperty.signature);
                properties.add(property);
            }
            result.put("userProperties", properties);
        }

        GameProfile sel = getSelectedProfile();
        if (sel != null) {
            result.put("displayName", sel.name);
            result.put("uuid", sel.id);

            List properties = new ArrayList();
            for (Property profileProperty : sel.properties.values()) {
                Map<String, String> property = new HashMap<>();
                property.put("name", profileProperty.name);
                property.put("value", profileProperty.value);
                property.put("signature", profileProperty.signature);
                properties.add(property);
            }

            if (!properties.isEmpty())
                result.put("profileProperties", properties);
        }

        if (StrUtils.isNotBlank(getAuthenticatedToken()))
            result.put("accessToken", getAuthenticatedToken());

        return result;
    }

    @Deprecated
    public String getSessionToken() {
        if (isLoggedIn() && getSelectedProfile() != null && canPlayOnline())
            return String.format("token:%s:%s", new Object[]{getAuthenticatedToken(), getSelectedProfile().id});
        return null;
    }

    public String getAuthenticatedToken() {
        return this.accessToken;
    }

    @Override
    public String toString() {
        return "YggdrasilAuthenticationService{profiles=" + Arrays.toString(this.profiles) + ", selectedProfile=" + getSelectedProfile() + ", username='" + getUsername() + '\'' + ", isLoggedIn=" + isLoggedIn() + ", canPlayOnline=" + canPlayOnline() + ", accessToken='" + this.accessToken + '\'' + ", clientToken='" + getAuthenticationService().getClientToken() + '\'' + '}';
    }
}
