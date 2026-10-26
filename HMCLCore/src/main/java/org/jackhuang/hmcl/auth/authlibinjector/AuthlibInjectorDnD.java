// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.authlibinjector;

import javafx.event.EventHandler;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

import java.util.Optional;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.io.NetworkUtils.decodeURL;

/**
 * @author yushijinhun
 * @see <a href="https://yushijinhun.github.io/authlib-injector/en/launcher-technical-specification.html#configuration-via-drag-and-drop">Launcher Technical Specification for Authlib-Injector</a>
 */
public final class AuthlibInjectorDnD {

    private static final String SCHEME = "authlib-injector";
    private static final String PATH_YGGDRASIL_SERVER = "yggdrasil-server";

    private AuthlibInjectorDnD() {}

    public static Optional<String> parseUrlFromDragboard(Dragboard dragboard) {
        String url = dragboard.getString();
        if (url == null) return Optional.empty();

        String[] urlElements = url.split(":");
        if (urlElements.length == 3 && SCHEME.equals(urlElements[0]) && PATH_YGGDRASIL_SERVER.equals(urlElements[1])) {
            return Optional.of(decodeURL(urlElements[2]));
        }
        return Optional.empty();
    }

    public static EventHandler<DragEvent> dragOverHandler() {
        return event -> parseUrlFromDragboard(event.getDragboard()).ifPresent(url -> {
            event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });
    }

    public static EventHandler<DragEvent> dragDroppedHandler(Consumer<String> onUrlTransfered) {
        return event -> parseUrlFromDragboard(event.getDragboard()).ifPresent(url -> {
            event.setDropCompleted(true);
            event.consume();
            onUrlTransfered.accept(url);
        });
    }

}
