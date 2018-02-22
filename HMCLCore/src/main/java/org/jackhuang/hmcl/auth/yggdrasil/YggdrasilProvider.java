package org.jackhuang.hmcl.auth.yggdrasil;

import java.net.URL;
import java.util.UUID;

/**
 * @see <a href="http://wiki.vg">http://wiki.vg</a>
 */
public interface YggdrasilProvider {

    URL getAuthenticationURL();

    URL getRefreshmentURL();

    URL getValidationURL();

    URL getInvalidationURL();

    URL getProfilePropertiesURL(UUID uuid);

}
