package org.jackhuang.hmcl.ui.terracotta.core;

import org.jackhuang.hmcl.ui.terracotta.core.provider.GeneralProvider;
import org.jackhuang.hmcl.ui.terracotta.core.provider.ITerracottaProvider;
import org.jackhuang.hmcl.ui.terracotta.core.provider.MacOSProvider;

import java.net.URI;
import java.util.List;

public final class TerracottaMetadata {
    private TerracottaMetadata() {
    }

    public static final String VERSION = "0.3.8-rc.1";

    public static final List<URI> WINDOWS_X86_64 = create(String.format("https://github.com/burningtnt/Terracotta/releases/download/V%1$s/terracotta-%1$s-windows-x86_64.exe", VERSION));
    public static final List<URI> WINDOWS_ARM64 = create(String.format("https://github.com/burningtnt/Terracotta/releases/download/V%1$s/terracotta-%1$s-windows-arm64.exe", VERSION));

    public static final List<URI> LINUX_X86_64 = create(String.format("https://github.com/burningtnt/Terracotta/releases/download/V%1$s/terracotta-%1$s-linux-x86_64", VERSION));
    public static final List<URI> LINUX_ARM64 = create(String.format("https://github.com/burningtnt/Terracotta/releases/download/V%1$s/terracotta-%1$s-linux-arm64", VERSION));

    public static final List<URI> MACOS_INSTALLER_X86_64 = create(String.format("https://github.com/burningtnt/Terracotta/releases/download/V%1$s/terracotta-%1$s-macos-x86_64.pkg", VERSION));
    public static final List<URI> MACOS_INSTALLER_ARM64 = create(String.format("https://github.com/burningtnt/Terracotta/releases/download/V%1$s/terracotta-%1$s-macos-arm64.pkg", VERSION));
    public static final List<URI> MACOS_BIN_X86_64 = create(String.format("https://github.com/burningtnt/Terracotta/releases/download/V%1$s/terracotta-%1$s-macos-x86_64", VERSION));
    public static final List<URI> MACOS_BIN_ARM64 = create(String.format("https://github.com/burningtnt/Terracotta/releases/download/V%1$s/terracotta-%1$s-macos-arm64", VERSION));

    private static List<URI> create(String s) {
        return List.of(URI.create("https://ghfast.top/" + s), URI.create("https://cdn.crashmc.com/" + s), URI.create(s));
    }

    public static final ITerracottaProvider PROVIDER = locateProvider();

    private static ITerracottaProvider locateProvider() {
        if (GeneralProvider.TARGET != null) {
            return new GeneralProvider();
        } else if (MacOSProvider.INSTALLER != null) {
            return new MacOSProvider();
        } else {
            return null;
        }
    }
}
