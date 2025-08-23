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
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ProxyManager {

    private static final ProxySelector NO_PROXY = new SimpleProxySelector(Proxy.NO_PROXY);
    private static final ProxySelector SYSTEM_DEFAULT = Objects.requireNonNullElse(ProxySelector.getDefault(), NO_PROXY);

    private static volatile @NotNull ProxySelector defaultProxySelector = SYSTEM_DEFAULT;
    private static volatile @Nullable SimpleAuthenticator defaultAuthenticator = null;

    private static ProxySelector getProxySelector() {
        if (config().hasProxy()) {
            Proxy.Type proxyType = config().getProxyType();
            String host = config().getProxyHost();
            int port = config().getProxyPort();

            if (proxyType == Proxy.Type.DIRECT || StringUtils.isBlank(host)) {
                return NO_PROXY;
            } else if (port < 0 || port > 0xFFFF) {
                LOG.warning("Illegal proxy port: " + port);
                return NO_PROXY;
            } else {
                return new SimpleProxySelector(new Proxy(proxyType, new InetSocketAddress(host, port)));
            }
        } else {
            return ProxyManager.SYSTEM_DEFAULT;
        }
    }

    private static SimpleAuthenticator getAuthenticator() {
        if (config().hasProxy() && config().hasProxyAuth()) {
            String username = config().getProxyUser();
            String password = config().getProxyPass();

            if (username != null || password != null)
                return new SimpleAuthenticator(username, password.toCharArray());
            else
                return null;
        } else
            return null;
    }

    static void init() {
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
        config().proxyTypeProperty().addListener(updateProxySelector);
        config().proxyHostProperty().addListener(updateProxySelector);
        config().proxyPortProperty().addListener(updateProxySelector);
        config().hasProxyProperty().addListener(updateProxySelector);

        defaultAuthenticator = getAuthenticator();
        InvalidationListener updateAuthenticator = observable -> defaultAuthenticator = getAuthenticator();
        config().hasProxyProperty().addListener(updateAuthenticator);
        config().hasProxyAuthProperty().addListener(updateAuthenticator);
        config().proxyUserProperty().addListener(updateAuthenticator);
        config().proxyPassProperty().addListener(updateAuthenticator);
    }

    private static final class SimpleProxySelector extends ProxySelector {
        private final List<Proxy> proxies;

        SimpleProxySelector(Proxy proxy) {
            this(Collections.singletonList(proxy));
        }

        SimpleProxySelector(List<Proxy> proxies) {
            this.proxies = proxies;
        }

        @Override
        public List<Proxy> select(URI uri) {
            if (uri == null)
                throw new IllegalArgumentException("URI can't be null.");

            return proxies;
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            if (uri == null || sa == null || ioe == null) {
                throw new IllegalArgumentException("Arguments can't be null.");
            }
        }

        @Override
        public String toString() {
            return "SimpleProxySelector" + proxies;
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
