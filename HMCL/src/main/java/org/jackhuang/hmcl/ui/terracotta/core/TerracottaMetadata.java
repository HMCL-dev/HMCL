package org.jackhuang.hmcl.ui.terracotta.core;

import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.ui.terracotta.core.provider.GeneralProvider;
import org.jackhuang.hmcl.ui.terracotta.core.provider.ITerracottaProvider;
import org.jackhuang.hmcl.ui.terracotta.core.provider.MacOSProvider;

import java.net.URI;
import java.util.List;

public final class TerracottaMetadata {
    private TerracottaMetadata() {
    }

    public static final TerracottaDaemon WINDOWS_X86_64 = create(
            "windows-x86_64.exe", "b1badefb1e503d4e9b886edab1bf3fb6b1ff75763b29a06fe7cc2f2343610d02"
    );
    public static final TerracottaDaemon WINDOWS_ARM64 = create(
            "windows-arm64.exe", "05f376bcf3a8317a36fd51b6335ad8e6821af03af78a90cc1b0ff91771e095f3"
    );

    public static final TerracottaDaemon LINUX_X86_64 = create(
            "linux-x86_64", "ca197ab3780834a58e51d17fa57157f82486bc6b22bf57242eca169c6e408ede"
    );
    public static final TerracottaDaemon LINUX_ARM64 = create(
            "linux-arm64", "85949ef696668f0a6c08944c998342bc1bbad62f112d6c2663acc2a0cc3e1b3c"
    );

    public static final TerracottaDaemon MACOS_INSTALLER_X86_64 = create(
            "macos-x86_64.pkg", "e46c71f0c446f9ba0bd67f7216b64bad811417a00e54d4841eb1c71e7f70f189"
    );
    public static final TerracottaDaemon MACOS_INSTALLER_ARM64 = create(
            "macos-arm64.pkg", "223ab9964c05867bd76fd66b0bc9dde18f3c2958356c9c15be8205dcb7bdee00"
    );
    public static final TerracottaDaemon MACOS_BIN_X86_64 = create(
            "macos-x86_64", "49b4813538e1c6c495d69760a289bd8d4bd3a7ef51cc4a7db7a6a33f45846440");
    public static final TerracottaDaemon MACOS_BIN_ARM64 = create(
            "macos-arm64", "9e4da85595301fec392a4efa7aff44f05c3c81666d99a0c9df5d1c368617dfff"
    );

    public static final String VERSION = "0.3.8-rc.1";

    private static TerracottaDaemon create(String classifier, String hash) {
        String link = String.format("https://github.com/burningtnt/Terracotta/releases/download/V%1$s/terracotta-%1$s-%2$s", VERSION, classifier);

        return new TerracottaDaemon(
                List.of(URI.create("https://ghfast.top/" + link), URI.create("https://cdn.crashmc.com/" + link), URI.create(link)),
                classifier, new FileDownloadTask.IntegrityCheck("SHA-256", hash)
        );
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
