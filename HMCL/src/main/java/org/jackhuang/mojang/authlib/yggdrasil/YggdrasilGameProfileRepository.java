package org.jackhuang.mojang.authlib.yggdrasil;

import java.util.HashSet;
import java.util.Set;
import org.jackhuang.hellominecraft.logging.logger.Logger;
import org.jackhuang.hellominecraft.utils.NetUtils;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.mojang.authlib.Agent;
import org.jackhuang.mojang.authlib.GameProfile;
import org.jackhuang.mojang.authlib.GameProfileRepository;
import org.jackhuang.mojang.authlib.ProfileLookupCallback;
import org.jackhuang.mojang.authlib.exceptions.AuthenticationException;
import org.jackhuang.mojang.authlib.yggdrasil.response.ProfileSearchResultsResponse;

public class YggdrasilGameProfileRepository
        implements GameProfileRepository {

    private static final Logger LOGGER = new Logger("YggdrasilGameProfileRepository");
    private static final String BASE_URL = "https://api.mojang.com/";
    private static final String SEARCH_PAGE_URL = BASE_URL + "profiles/page/";
    private static final int MAX_FAIL_COUNT = 3;
    private static final int DELAY_BETWEEN_PAGES = 100;
    private static final int DELAY_BETWEEN_FAILURES = 750;
    private final YggdrasilAuthenticationService authenticationService;

    public YggdrasilGameProfileRepository(YggdrasilAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Override
    public void findProfilesByNames(String[] names, Agent agent, ProfileLookupCallback callback) {
        Set<ProfileCriteria> criteria = new HashSet<ProfileCriteria>();

        for (String name : names) {
            if (StrUtils.isNotBlank(name)) {
                criteria.add(new ProfileCriteria(name, agent));
            }
        }

        Exception exception = null;
        Set request = new HashSet<ProfileCriteria>(criteria);
        int page = 1;
        int failCount = 0;
        while (!criteria.isEmpty()) {
            try {
                ProfileSearchResultsResponse response = (ProfileSearchResultsResponse) this.authenticationService.makeRequest(NetUtils.constantURL("https://api.mojang.com/profiles/page/" + page), request, ProfileSearchResultsResponse.class);
                failCount = 0;
                exception = null;

                if ((response.getSize() == 0) || (response.getProfiles().length == 0)) {
                    LOGGER.debug("Page {} returned empty, aborting search", new Object[]{page});
                } else {
                    LOGGER.debug("Page {} returned {} results of {}, parsing", new Object[]{page, response.getProfiles().length, response.getSize()});

                    for (GameProfile profile : response.getProfiles()) {
                        LOGGER.debug("Successfully looked up profile {}", new Object[]{profile});
                        criteria.remove(new ProfileCriteria(profile.getName(), agent));
                        callback.onProfileLookupSucceeded(profile);
                    }

                    LOGGER.debug("Page {} successfully parsed", new Object[]{page});
                    page++;
                    try {
                        Thread.sleep(DELAY_BETWEEN_PAGES);
                    } catch (InterruptedException ignored) {
                    }
                }
            } catch (AuthenticationException e) {
                exception = e;
                failCount++;

                if (failCount != 3) {
                    try {
                        Thread.sleep(DELAY_BETWEEN_FAILURES);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }
        if (criteria.isEmpty()) {
            LOGGER.debug("Successfully found every profile requested");
        } else {
            LOGGER.debug("{} profiles were missing from search results", new Object[]{criteria.size()});
            if (exception == null) {
                exception = new ProfileNotFoundException("Server did not find the requested profile");
            }
            for (ProfileCriteria profileCriteria : criteria) {
                callback.onProfileLookupFailed(new GameProfile(null, profileCriteria.getName()), exception);
            }
        }
    }

    private class ProfileCriteria {

        private final String name;
        private final String agent;

        private ProfileCriteria(String name, Agent agent) {
            this.name = name;
            this.agent = agent.getName();
        }

        public String getName() {
            return this.name;
        }

        public String getAgent() {
            return this.agent;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if ((o == null) || (getClass() != o.getClass())) {
                return false;
            }
            ProfileCriteria that = (ProfileCriteria) o;
            return (this.agent.equals(that.agent)) && (this.name.toLowerCase().equals(that.name.toLowerCase()));
        }

        @Override
        public int hashCode() {
            return 31 * this.name.toLowerCase().hashCode() + this.agent.hashCode();
        }

        @Override
        public String toString() {
            return "ProfileCriteria{" + "name=" + name + ", agent=" + agent + '}';
        }

    }
}
