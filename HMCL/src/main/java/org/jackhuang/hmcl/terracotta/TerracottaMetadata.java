package org.jackhuang.hmcl.terracotta;

import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.terracotta.provider.GeneralProvider;
import org.jackhuang.hmcl.terracotta.provider.ITerracottaProvider;
import org.jackhuang.hmcl.terracotta.provider.MacOSProvider;
import org.jackhuang.hmcl.util.StringUtils;

import java.net.URI;
import java.util.List;

public final class TerracottaMetadata {
    private TerracottaMetadata() {
    }

    public static final TerracottaDaemon WINDOWS_X86_64 = create(
            "windows-x86_64.exe", "sha256:ac2ea92629b6ff524df94a610d9c2835f289d28bd55fdd78def461261ddf71e1"
    );
    public static final TerracottaDaemon WINDOWS_ARM64 = create(
            "windows-arm64.exe", "sha256:e96d450e15523b8f248f2935630f39ef88afcc731449cd2ce5a4114cb6476120"
    );

    public static final TerracottaDaemon LINUX_X86_64 = create(
            "linux-x86_64", "sha256:2bdad0df5e16e3cccdb7c6d43ac342b7175d51c157b6c7637e2092c1e93f4ba9"
    );
    public static final TerracottaDaemon LINUX_ARM64 = create(
            "linux-arm64", "sha256:1c74ac50392431fd1cc56407d86aa16f29e842ce0c7ecaf599c7b5059c3ccee2"
    );

    public static final TerracottaDaemon MACOS_INSTALLER_X86_64 = create(
            "macos-x86_64.pkg", "sha256:814b054fcea6604cb119a25f7704905d40e522874724cf77abf266de64472f5d"
    );
    public static final TerracottaDaemon MACOS_INSTALLER_ARM64 = create(
            "macos-arm64.pkg", "sha256:07a80444aeace22c3b1f4c914907bd34b758a3b861d8252f503cc8d49135e71f"
    );
    public static final TerracottaDaemon MACOS_BIN_X86_64 = create(
            "macos-x86_64", "sha256:4ca91c55ecb2e3090fd23ec7e3920dd150c4916b1d1792b9504ff868a81d0fb5");
    public static final TerracottaDaemon MACOS_BIN_ARM64 = create(
            "macos-arm64", "sha256:36257716f6c7c2f86097fe59f2ac009fa68ad6dcfdd65a56fda62025836a12c1"
    );

    public static final String VERSION = "0.3.8-rc.2";

    private static TerracottaDaemon create(String classifier, String hash) {
        hash = StringUtils.removePrefix(hash, "sha256:");
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
