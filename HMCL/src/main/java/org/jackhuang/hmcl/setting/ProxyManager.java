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

import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ProxyManager extends ProxySelector {

    private static final List<Proxy> NO_PROXY = Collections.singletonList(Proxy.NO_PROXY);

    private volatile Function<URI, List<Proxy>> proxySelector;

    static void init() {
        Function<URI, List<Proxy>> systemDefault = ProxySelector.getDefault()::select;
        ProxyManager proxyManager = new ProxyManager();
        ProxySelector.setDefault(proxyManager);

        InvalidationListener listener = observable -> {
            if (config().hasProxy()) {
                Proxy.Type proxyType = config().getProxyType();
                String host = config().getProxyHost();
                int port = config().getProxyPort();

                if (proxyType == Proxy.Type.DIRECT || StringUtils.isBlank(host)) {
                    proxyManager.proxySelector = null;
                } else if (port < 0 || port > 0xFFFF) {
                    LOG.warning("Illegal proxy port: " + port);
                    proxyManager.proxySelector = systemDefault;
                } else {
                    List<Proxy> proxies = Collections.singletonList(new Proxy(proxyType, new InetSocketAddress(host, port)));
                    proxyManager.proxySelector = uri -> proxies;
                }
            } else {
                proxyManager.proxySelector = systemDefault;
            }
        };
        config().proxyTypeProperty().addListener(listener);
        config().proxyHostProperty().addListener(listener);
        config().proxyPortProperty().addListener(listener);
        config().hasProxyProperty().addListener(listener);
        listener.invalidated(null);

        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (config().hasProxyAuth()) {
                    String username = config().getProxyUser();
                    String password = config().getProxyPass();
                    if (username != null && password != null) {
                        return new PasswordAuthentication(username, password.toCharArray());
                    }
                }
                return null;
            }
        });
    }

    @Override
    public List<Proxy> select(URI uri) {
        return proxySelector != null ? proxySelector.apply(uri) : NO_PROXY;
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        if (uri == null || sa == null || ioe == null) {
            throw new IllegalArgumentException("Arguments can't be null.");
        }
    }
}
