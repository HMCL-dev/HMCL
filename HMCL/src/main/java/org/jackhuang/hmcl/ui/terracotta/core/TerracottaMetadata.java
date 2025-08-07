package org.jackhuang.hmcl.ui.terracotta.core;

import org.jackhuang.hmcl.ui.terracotta.core.provider.ExecutableProvider;
import org.jackhuang.hmcl.ui.terracotta.core.provider.ITerracottaProvider;

import java.net.URI;

public final class TerracottaMetadata {
    private TerracottaMetadata() {
    }

    public static final String VERSION = "0.3.8-rc.1";

    public static final URI WINDOWS_X86_64 = URI.create(String.format("https://github.com/burningtnt/Terracotta/releases/download/V%1$s/terracotta-%1$s-windows-x86_64.exe", VERSION));
    public static final URI WINDOWS_ARM64 = URI.create(String.format("https://github.com/burningtnt/Terracotta/releases/download/V%1$s/terracotta-%1$s-windows-arm64.exe", VERSION));
    public static final URI LINUX_X86_64 = URI.create(String.format("https://github.com/burningtnt/Terracotta/releases/download/V%1$s/terracotta-%1$s-linux-x86_64", VERSION));
    public static final URI LINUX_ARM64 = URI.create(String.format("https://github.com/burningtnt/Terracotta/releases/download/V%1$s/terracotta-%1$s-linux-arm64", VERSION));

    public static final ITerracottaProvider PROVIDER = locateProvider();
    private static ITerracottaProvider locateProvider() {
        if (ExecutableProvider.TARGET != null) {
            return new ExecutableProvider();
        } else {
            return null;
        }
    }
}
