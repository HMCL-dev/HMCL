/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.setting;

import static org.jackhuang.hmcl.setting.ConfigHolder.CONFIG;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.Proxy.Type;

import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.value.ObservableObjectValue;

public final class ProxyManager {
    private ProxyManager() {
    }

    private static ObjectBinding<Proxy> proxyProperty = Bindings.createObjectBinding(
            () -> {
                String host = CONFIG.getProxyHost();
                Integer port = Lang.toIntOrNull(CONFIG.getProxyPort());
                if (!CONFIG.hasProxy() || StringUtils.isBlank(host) || port == null || CONFIG.getProxyType() == Proxy.Type.DIRECT) {
                    return Proxy.NO_PROXY;
                } else {
                    return new Proxy(CONFIG.getProxyType(), new InetSocketAddress(host, port));
                }
            },
            CONFIG.proxyTypeProperty(),
            CONFIG.proxyHostProperty(),
            CONFIG.proxyPortProperty(),
            CONFIG.hasProxyProperty());

    public static Proxy getProxy() {
        return proxyProperty.get();
    }

    public static ObservableObjectValue<Proxy> proxyProperty() {
        return proxyProperty;
    }

    static void init() {
        proxyProperty.addListener(observable -> updateSystemProxy());

        updateSystemProxy();

        Authenticator.setDefault(new Authenticator() {

            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (CONFIG.hasProxyAuth()) {
                    String username = CONFIG.getProxyUser();
                    String password = CONFIG.getProxyPass();
                    if (username != null && password != null) {
                        return new PasswordAuthentication(username, password.toCharArray());
                    }
                }
                return null;
            }
        });
    }

    private static void updateSystemProxy() {
        Proxy proxy = proxyProperty.get();
        if (proxy.type() == Proxy.Type.DIRECT) {
            System.clearProperty("http.proxyHost");
            System.clearProperty("http.proxyPort");
            System.clearProperty("https.proxyHost");
            System.clearProperty("https.proxyPort");
            System.clearProperty("socksProxyHost");
            System.clearProperty("socksProxyPort");
        } else {
            InetSocketAddress address = (InetSocketAddress) proxy.address();
            String host = address.getHostString();
            String port = String.valueOf(address.getPort());
            if (proxy.type() == Type.HTTP) {
                System.clearProperty("socksProxyHost");
                System.clearProperty("socksProxyPort");
                System.setProperty("http.proxyHost", host);
                System.setProperty("http.proxyPort", port);
                System.setProperty("https.proxyHost", host);
                System.setProperty("https.proxyPort", port);
            } else if (proxy.type() == Type.SOCKS) {
                System.clearProperty("http.proxyHost");
                System.clearProperty("http.proxyPort");
                System.clearProperty("https.proxyHost");
                System.clearProperty("https.proxyPort");
                System.setProperty("socksProxyHost", host);
                System.setProperty("socksProxyPort", port);
            }
        }
    }
}
