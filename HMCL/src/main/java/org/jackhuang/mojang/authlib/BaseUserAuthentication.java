package org.jackhuang.mojang.authlib;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jackhuang.hellominecraft.logging.logger.Logger;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.mojang.authlib.properties.Property;
import org.jackhuang.mojang.authlib.properties.PropertyMap;
import org.jackhuang.mojang.util.UUIDTypeAdapter;

public abstract class BaseUserAuthentication
	implements UserAuthentication {

    private static final Logger LOGGER = new Logger("BaseUserAuthentication");
    protected static final String STORAGE_KEY_PROFILE_NAME = "displayName";
    protected static final String STORAGE_KEY_PROFILE_ID = "uuid";
    protected static final String STORAGE_KEY_PROFILE_PROPERTIES = "profileProperties";
    protected static final String STORAGE_KEY_USER_NAME = "username";
    protected static final String STORAGE_KEY_USER_ID = "userid";
    protected static final String STORAGE_KEY_USER_PROPERTIES = "userProperties";
    private final AuthenticationService authenticationService;
    private final PropertyMap userProperties = new PropertyMap();
    private String userid;
    private String username;
    private String password;
    private GameProfile selectedProfile;
    private UserType userType;

    protected BaseUserAuthentication(AuthenticationService authenticationService) {
	Objects.requireNonNull(authenticationService);
	this.authenticationService = authenticationService;
    }

    @Override
    public boolean canLogIn() {
	return (!canPlayOnline()) && (StrUtils.isNotBlank(getUsername())) && (StrUtils.isNotBlank(getPassword()));
    }

    @Override
    public void logOut() {
	this.password = null;
	this.userid = null;
	setSelectedProfile(null);
	getModifiableUserProperties().clear();
	setUserType(null);
    }

    @Override
    public boolean isLoggedIn() {
	return getSelectedProfile() != null;
    }

    @Override
    public void setUsername(String username) {
	if ((isLoggedIn()) && (canPlayOnline())) {
	    throw new IllegalStateException("Cannot change username whilst logged in & online");
	}

	this.username = username;
    }

    @Override
    public void setPassword(String password) {
	if ((isLoggedIn()) && (canPlayOnline()) && (StrUtils.isNotBlank(password))) {
	    throw new IllegalStateException("Cannot set password whilst logged in & online");
	}

	this.password = password;
    }

    protected String getUsername() {
	return this.username;
    }

    protected String getPassword() {
	return this.password;
    }

    @Override
    public void loadFromStorage(Map<String, Object> credentials) {
	logOut();

	setUsername((String)credentials.get("username"));

	if (credentials.containsKey("userid")) {
	    this.userid = (String)credentials.get("userid");
	} else {
	    this.userid = this.username;
	}

	if (credentials.containsKey("userProperties")) {
	    try {
		List<Map> list = (List<Map>) credentials.get("userProperties");

		for (Map propertyMap : list) {
		    String name = (String) propertyMap.get("name");
		    String value = (String) propertyMap.get("value");
		    String signature = (String) propertyMap.get("signature");

		    if (signature == null) {
			getModifiableUserProperties().put(name, new Property(name, value));
		    } else {
			getModifiableUserProperties().put(name, new Property(name, value, signature));
		    }
		}
	    } catch (Throwable t) {
		LOGGER.warn("Couldn't deserialize user properties", t);
	    }
	}

	if ((credentials.containsKey("displayName")) && (credentials.containsKey("uuid"))) {
	    GameProfile profile = new GameProfile(UUIDTypeAdapter.fromString((String)credentials.get("uuid")), (String)credentials.get("displayName"));
	    if (credentials.containsKey("profileProperties")) {
		try {
		    List<Map> list = (List<Map>) credentials.get("profileProperties");
		    for (Map propertyMap : list) {
			String name = (String) propertyMap.get("name");
			String value = (String) propertyMap.get("value");
			String signature = (String) propertyMap.get("signature");

			if (signature == null) {
			    profile.getProperties().put(name, new Property(name, value));
			} else {
			    profile.getProperties().put(name, new Property(name, value, signature));
			}
		    }
		} catch (Throwable t) {
		    LOGGER.warn("Couldn't deserialize profile properties", t);
		}
	    }
	    setSelectedProfile(profile);
	}
    }

    @Override
    public Map<String, Object> saveForStorage() {
	Map result = new HashMap();

	if (getUsername() != null) {
	    result.put("username", getUsername());
	}
	if (getUserID() != null) {
	    result.put("userid", getUserID());
	} else if (getUsername() != null) {
	    result.put("username", getUsername());
	}

	if (!getUserProperties().isEmpty()) {
	    List properties = new ArrayList();
	    for (Property userProperty : getUserProperties().values()) {
		Map property = new HashMap();
		property.put("name", userProperty.getName());
		property.put("value", userProperty.getValue());
		property.put("signature", userProperty.getSignature());
		properties.add(property);
	    }
	    result.put("userProperties", properties);
	}

	GameProfile sel = getSelectedProfile();
	if (sel != null) {
	    result.put("displayName", sel.getName());
	    result.put("uuid", sel.getId());

	    List properties = new ArrayList();
	    for (Property profileProperty : sel.getProperties().values()) {
		Map property = new HashMap();
		property.put("name", profileProperty.getName());
		property.put("value", profileProperty.getValue());
		property.put("signature", profileProperty.getSignature());
		properties.add(property);
	    }

	    if (!properties.isEmpty()) {
		result.put("profileProperties", properties);
	    }
	}

	return result;
    }

    protected void setSelectedProfile(GameProfile selectedProfile) {
	this.selectedProfile = selectedProfile;
    }

    @Override
    public GameProfile getSelectedProfile() {
	return this.selectedProfile;
    }

    @Override
    public String toString() {
	StringBuilder result = new StringBuilder();

	result.append(getClass().getSimpleName());
	result.append("{");

	if (isLoggedIn()) {
	    result.append("Logged in as ");
	    result.append(getUsername());

	    if (getSelectedProfile() != null) {
		result.append(" / ");
		result.append(getSelectedProfile());
		result.append(" - ");

		if (canPlayOnline()) {
		    result.append("Online");
		} else {
		    result.append("Offline");
		}
	    }
	} else {
	    result.append("Not logged in");
	}

	result.append("}");

	return result.toString();
    }

    public AuthenticationService getAuthenticationService() {
	return this.authenticationService;
    }

    @Override
    public String getUserID() {
	return this.userid;
    }

    @Override
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

    @Override
    public UserType getUserType() {
	if (isLoggedIn()) {
	    return this.userType == null ? UserType.LEGACY : this.userType;
	}
	return null;
    }

    protected void setUserType(UserType userType) {
	this.userType = userType;
    }

    protected void setUserid(String userid) {
	this.userid = userid;
    }
}