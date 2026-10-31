// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download;

import org.glavo.url.WebURL;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Utility class for rewriting OpenBMCLAPI download redirects to a custom cluster node.
@NotNullByDefault
public final class OpenBMCLAPICustomNode {

    private OpenBMCLAPICustomNode() {
    }

    private static volatile boolean enabled = false;
    private static volatile String nodeUrl = "";
    private static volatile String clusterSecret = "";

    private static final Pattern OPENBMCLAPI_DOWNLOAD_PATTERN =
            Pattern.compile("^/download/([0-9a-fA-F]{40})(?:\\?.*)?$");

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static String getNodeUrl() {
        return nodeUrl;
    }

    public static void setNodeUrl(@Nullable String url) {
        nodeUrl = url == null ? "" : url.trim();
    }

    public static String getClusterSecret() {
        return clusterSecret;
    }

    public static void setClusterSecret(@Nullable String secret) {
        clusterSecret = secret == null ? "" : secret.trim();
    }

    /// Rewrites a 302 redirection target URL if it matches an OpenBMCLAPI file download endpoint.
    ///
    /// @param originalTarget the redirected target URL received from server
    /// @return the rewritten URL pointing to the custom cluster node, or originalTarget if not applicable
    public static WebURL rewriteUrl(WebURL originalTarget) {
        if (!enabled) {
            return originalTarget;
        }

        String currentNodeUrl = nodeUrl;
        String currentSecret = clusterSecret;
        if (currentNodeUrl.isEmpty() || currentSecret.isEmpty()) {
            return originalTarget;
        }

        String host = originalTarget.getHost();
        if (host == null) {
            return originalTarget;
        }
        String hostLower = host.toLowerCase(Locale.ROOT);
        if (!hostLower.contains("openbmclapi") && !hostLower.contains("bangbang93.com")) {
            return originalTarget;
        }

        String href = originalTarget.href();
        int queryIdx = href.indexOf('?');
        String path = (queryIdx >= 0) ? href.substring(0, queryIdx) : href;

        int slashIdx = path.indexOf("://");
        if (slashIdx >= 0) {
            int pathStart = path.indexOf('/', slashIdx + 3);
            if (pathStart >= 0) {
                path = path.substring(pathStart);
            } else {
                path = "/";
            }
        }

        Matcher matcher = OPENBMCLAPI_DOWNLOAD_PATTERN.matcher(path);
        if (!matcher.matches()) {
            return originalTarget;
        }

        String hash = matcher.group(1).toLowerCase(Locale.ROOT);
        long exp = System.currentTimeMillis() + 300_000L;

        String sign = sign(hash, currentSecret, exp);
        if (sign == null) {
            return originalTarget;
        }

        String base = currentNodeUrl.endsWith("/")
                ? currentNodeUrl.substring(0, currentNodeUrl.length() - 1)
                : currentNodeUrl;
        String newUrlStr = base + "/download/" + hash + "?s=" + sign + "&e=" + exp;

        try {
            return WebURL.parse(newUrlStr);
        } catch (Exception e) {
            LOG.warning("Failed to parse custom OpenBMCLAPI node URL: " + newUrlStr, e);
            return originalTarget;
        }
    }

    @Nullable
    private static String sign(String hash, String secret, long exp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            String data = secret + hash + exp;
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
