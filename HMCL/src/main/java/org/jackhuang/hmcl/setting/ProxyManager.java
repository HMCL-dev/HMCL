/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.setting;

import javafx.beans.InvalidationListener;
import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.net.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ProxyManager {

    /// How long one system proxy lookup is reused before the operating system is consulted again.
    ///
    /// The JDK consults the selector for every connection, and under
    /// `java.net.useSystemProxies=true` that reaches `sun.net.spi.DefaultProxySelector`, whose
    /// `getSystemProxies` is `synchronized` and asks the operating system every time. It costs about
    /// 0.8 ms per call and, because the lock is global, a second thread gets no more throughput than
    /// one. Caching for a few seconds collapses the repeated lookups within a download burst, while
    /// still picking up an operating system proxy change almost immediately.
    private static final long SYSTEM_PROXY_CACHE_TTL_NANOS = TimeUnit.SECONDS.toNanos(5);

    private static final SimpleProxySelector NO_PROXY = new SimpleProxySelector(Proxy.NO_PROXY);
    private static final ProxySelector SYSTEM_DEFAULT;

    static {
        ProxySelector systemProxySelector = ProxySelector.getDefault();
        SYSTEM_DEFAULT = systemProxySelector != null
                ? new ProxySelectorWrapper(new CachingSystemProxySelector(systemProxySelector))
                : NO_PROXY;
    }

    private static volatile @NotNull ProxySelector defaultProxySelector = SYSTEM_DEFAULT;
    private static volatile @Nullable SimpleAuthenticator defaultAuthenticator = null;

    private static ProxySelector getProxySelector() {
        ProxyType proxyType = settings().proxyTypeProperty().get();
        return switch (proxyType) {
            case SYSTEM -> ProxyManager.SYSTEM_DEFAULT;
            case DIRECT -> NO_PROXY;
            case HTTP, SOCKS -> {
                String host = settings().proxyHostProperty().get();
                int port = settings().proxyPortProperty().get();

                if (StringUtils.isBlank(host)) {
                    yield NO_PROXY;
                } else if (port < 0 || port > 0xFFFF) {
                    LOG.warning("Illegal proxy port: " + port);
                    yield NO_PROXY;
                } else {
                    yield new ProxySelectorWrapper(new SimpleProxySelector(new Proxy(
                            Objects.requireNonNull(proxyType.jdkType()),
                            new InetSocketAddress(host, port))));
                }
            }
        };
    }

    private static SimpleAuthenticator getAuthenticator() {
        if (settings().proxyTypeProperty().get().usesCustomAddress() && settings().hasProxyAuthProperty().get()) {
            String username = settings().proxyUserProperty().get();
            String password = settings().proxyPasswordProperty().get();

            if (username != null || password != null)
                return new SimpleAuthenticator(
                        Objects.requireNonNullElse(username, ""),
                        Objects.requireNonNullElse(password, "").toCharArray()
                );
            else
                return null;
        } else
            return null;
    }

    /// Installs proxy and authentication handlers backed by launcher settings.
    public static void init() {
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return defaultProxySelector.select(uri);
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                defaultProxySelector.connectFailed(uri, sa, ioe);
            }
        });
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                var defaultAuthenticator = ProxyManager.defaultAuthenticator;
                return defaultAuthenticator != null ? defaultAuthenticator.getPasswordAuthentication() : null;
            }
        });

        defaultProxySelector = getProxySelector();
        InvalidationListener updateProxySelector = observable -> defaultProxySelector = getProxySelector();
        settings().proxyTypeProperty().addListener(updateProxySelector);
        settings().proxyHostProperty().addListener(updateProxySelector);
        settings().proxyPortProperty().addListener(updateProxySelector);

        defaultAuthenticator = getAuthenticator();
        InvalidationListener updateAuthenticator = observable -> defaultAuthenticator = getAuthenticator();
        settings().proxyTypeProperty().addListener(updateAuthenticator);
        settings().hasProxyAuthProperty().addListener(updateAuthenticator);
        settings().proxyUserProperty().addListener(updateAuthenticator);
        settings().proxyPasswordProperty().addListener(updateAuthenticator);

        FetchTask.notifyInitialized();
    }

    private static abstract class AbstractProxySelector extends ProxySelector {
        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            if (uri == null || sa == null || ioe == null) {
                throw new IllegalArgumentException("Arguments can't be null.");
            }
        }
    }

    private static final class SimpleProxySelector extends AbstractProxySelector {
        private final List<Proxy> proxies;

        SimpleProxySelector(Proxy proxy) {
            this.proxies = List.of(proxy);
        }

        @Override
        public List<Proxy> select(URI uri) {
            if (uri == null)
                throw new IllegalArgumentException("URI can't be null.");
            return proxies;
        }

        @Override
        public String toString() {
            return "SimpleProxySelector" + proxies;
        }
    }

    /// Wraps another ProxySelector to avoid using proxy for loopback addresses.
    private static final class ProxySelectorWrapper extends AbstractProxySelector {
        private final ProxySelector source;

        ProxySelectorWrapper(ProxySelector source) {
            this.source = source;
        }

        @Override
        public List<Proxy> select(URI uri) {
            if (uri == null)
                throw new IllegalArgumentException("URI can't be null.");

            if (NetworkUtils.isLoopbackAddress(uri))
                return NO_PROXY.proxies;

            return source.select(uri);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            // Forwarded so that a caching source can drop the entry whose proxy just failed.
            source.connectFailed(uri, sa, ioe);
        }
    }

    /// Caches the system proxy lookup so that a download burst does not ask the operating system
    /// once per connection.
    ///
    /// See [ProxyManager#SYSTEM_PROXY_CACHE_TTL_NANOS] for why the wrapped selector is expensive.
    /// Only host-specific lookups are cached: a URI without a host gives the selector nothing to
    /// apply the `http.nonProxyHosts` bypass list to, so it is passed straight through.
    private static final class CachingSystemProxySelector extends AbstractProxySelector {

        /// One cached lookup, together with the [System#nanoTime()] value at which it expires.
        ///
        /// @param proxies       the proxies the wrapped selector returned
        /// @param deadlineNanos the deadline of the cached value
        private record CacheEntry(@Unmodifiable List<Proxy> proxies, long deadlineNanos) {
        }

        /// The selector whose results are cached.
        private final ProxySelector source;

        /// Cached lookups, keyed by [#cacheKey(String, String, int)].
        private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

        /// @param source the selector to cache the results of
        CachingSystemProxySelector(ProxySelector source) {
            this.source = source;
        }

        @Override
        public List<Proxy> select(URI uri) {
            if (uri == null)
                throw new IllegalArgumentException("URI can't be null.");

            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null)
                return source.select(uri);

            String key = cacheKey(scheme, host, uri.getPort());
            long now = System.nanoTime();

            CacheEntry entry = cache.get(key);
            if (entry != null && now < entry.deadlineNanos())
                return entry.proxies();

            @Nullable List<Proxy> proxies = source.select(uri);
            if (proxies == null)
                return null;

            proxies = List.copyOf(proxies);
            cache.put(key, new CacheEntry(proxies, now + SYSTEM_PROXY_CACHE_TTL_NANOS));
            return proxies;
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            super.connectFailed(uri, sa, ioe);
            // A proxy that just failed must not be served from the cache for the rest of the window.
            cache.remove(cacheKey(uri.getScheme(), uri.getHost(), uri.getPort()));
        }

        /// Builds the key a lookup is cached under.
        ///
        /// The wrapped selector decides per scheme and applies the `http.nonProxyHosts` bypass list
        /// per host, so both take part in the key. The port is included to keep the key unambiguous.
        ///
        /// @param scheme the URI scheme, or `null` when the URI has none
        /// @param host   the URI host, or `null` when the URI has none
        /// @param port   the URI port, or `-1` when unspecified
        /// @return the cache key
        private static String cacheKey(@Nullable String scheme, @Nullable String host, int port) {
            return scheme + "://" + host + ':' + port;
        }
    }

    private static final class SimpleAuthenticator extends Authenticator {
        private final String username;
        private final char[] password;

        private SimpleAuthenticator(String username, char[] password) {
            this.username = username;
            this.password = password;
        }

        @Override
        public PasswordAuthentication getPasswordAuthentication() {
            return getRequestorType() == RequestorType.PROXY ? new PasswordAuthentication(username, password) : null;
        }
    }

    private ProxyManager() {
    }
}
