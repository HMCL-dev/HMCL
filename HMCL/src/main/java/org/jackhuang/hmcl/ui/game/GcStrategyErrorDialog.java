// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.game;

import org.jackhuang.hmcl.launch.GcStrategyException;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Locale;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Shows a translated, actionable GC failure without modifying or automatically retrying the strategy.
@NotNullByDefault
public final class GcStrategyErrorDialog {
    /// Utility class.
    private GcStrategyErrorDialog() {
    }

    /// Creates a failure dialog for game launch or script export, with the actual runtime version and path.
    public static MessageDialogPane create(GcStrategyException failure, String title, Runnable openSettings) {
        String strategy = i18n("settings.advanced.gc_strategy." + failure.getGcStrategy().name().toLowerCase(Locale.ROOT));
        String message = i18n("launch.failed.gc." + failure.getReason().name().toLowerCase(Locale.ROOT), strategy)
                + "\n\n" + i18n("launch.failed.gc.java", failure.getJavaVersion(), failure.getExecutable());
        return new MessageDialogPane.Builder(message, title, MessageDialogPane.MessageType.ERROR)
                .addAction(i18n("launch.failed.gc.open_settings"), openSettings)
                .addCancel(i18n("launch.failed.gc.cancel"), null)
                .build();
    }
}
