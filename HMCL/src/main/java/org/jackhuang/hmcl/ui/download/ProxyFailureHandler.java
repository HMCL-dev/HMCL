// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.download;

import org.jackhuang.hmcl.setting.ProxyManager;
import org.jackhuang.hmcl.setting.ProxyType;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Detects download failures caused by an unreachable configured proxy and offers to disable it.
///
/// Only manually configured HTTP and SOCKS proxies are reported, because switching the launcher
/// to [ProxyType#DIRECT] is the only way to bypass them.
@NotNullByDefault
public final class ProxyFailureHandler {

    /// The maximum time to wait while testing whether the configured proxy accepts connections.
    private static final Duration PROXY_TEST_TIMEOUT = Duration.ofSeconds(3);

    /// Checks the failure and shows the proxy-unavailable prompt if the configured proxy is unreachable.
    ///
    /// The connection test runs asynchronously and the prompt is always shown on the JavaFX
    /// application thread. [onOtherFailure] runs on the calling thread when the failure is not
    /// caused by an unreachable proxy, so it can present its own dialog immediately.
    ///
    /// @param failure        the download failure to inspect, or `null` if no failure occurred
    /// @param onDisableProxy the action invoked after the user disables the proxy
    /// @param onOtherFailure the action invoked when the proxy is not the cause of the failure
    public static void handle(@Nullable Throwable failure,
                              Runnable onDisableProxy,
                              Runnable onOtherFailure) {
        InetSocketAddress proxyAddress = ProxyManager.getCustomProxyAddress();
        if (proxyAddress == null || !isConnectFailure(failure)) {
            onOtherFailure.run();
            return;
        }

        Schedulers.io().execute(() -> {
            boolean reachable = ProxyManager.isProxyReachable(proxyAddress, PROXY_TEST_TIMEOUT);
            runInFX(() -> {
                @Nullable InetSocketAddress currentAddress = ProxyManager.getCustomProxyAddress();
                if (!reachable && currentAddress != null && currentAddress.equals(proxyAddress)) {
                    showDialog(proxyAddress, onDisableProxy);
                } else {
                    onOtherFailure.run();
                }
            });
        });
    }

    /// Shows the prompt that lets the user keep or disable the unreachable proxy.
    ///
    /// @param proxyAddress   the unreachable proxy address
    /// @param onDisableProxy the action invoked after the user disables the proxy
    private static void showDialog(InetSocketAddress proxyAddress, Runnable onDisableProxy) {
        String address = proxyAddress.getHostString() + ":" + proxyAddress.getPort();
        Controllers.dialog(new MessageDialogPane.Builder(
                i18n("download.proxy_unavailable.message", address),
                i18n("download.proxy_unavailable.title"),
                MessageDialogPane.MessageType.WARNING)
                .addAction(i18n("download.proxy_unavailable.disable_proxy"), () -> {
                    settings().proxyTypeProperty().set(ProxyType.DIRECT);
                    onDisableProxy.run();
                })
                .addCancel(i18n("download.proxy_unavailable.cancel"), null)
                .build());
    }

    /// Returns whether any exception in the cause and suppressed chains is a connection failure.
    ///
    /// Only connection-level failures can indicate an unreachable proxy. A positive answer is
    /// still confirmed by testing the proxy connection before prompting the user.
    ///
    /// @param failure the failure to inspect, or `null`
    /// @return whether a connection-level exception is present in the failure chain
    private static boolean isConnectFailure(@Nullable Throwable failure) {
        Set<Throwable> visited = new HashSet<>();
        Deque<Throwable> pending = new ArrayDeque<>();
        if (failure != null) {
            pending.add(failure);
        }

        while (!pending.isEmpty()) {
            Throwable current = pending.removeLast();
            if (!visited.add(current)) {
                continue;
            }

            if (current instanceof ConnectException
                    || current instanceof SocketTimeoutException
                    || current instanceof NoRouteToHostException
                    || current instanceof UnknownHostException) {
                return true;
            }

            @Nullable Throwable cause = current.getCause();
            if (cause != null) {
                pending.add(cause);
            }
            for (Throwable suppressed : current.getSuppressed()) {
                pending.add(suppressed);
            }
        }

        return false;
    }

    /// Prevents instantiation.
    private ProxyFailureHandler() {
    }
}
