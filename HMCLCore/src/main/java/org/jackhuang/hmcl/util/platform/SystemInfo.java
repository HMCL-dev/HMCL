// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform;

import org.jackhuang.hmcl.util.DataSizeUnit;
import org.jackhuang.hmcl.util.platform.hardware.CentralProcessor;
import org.jackhuang.hmcl.util.platform.hardware.DeviceInfo;
import org.jackhuang.hmcl.util.platform.hardware.GraphicsCard;
import org.jackhuang.hmcl.util.platform.hardware.HardwareDetector;
import org.jackhuang.hmcl.util.platform.hardware.PageFileConfiguration;
import org.jackhuang.hmcl.util.platform.hardware.PhysicalMemoryStatus;
import org.jackhuang.hmcl.util.platform.linux.LinuxHardwareDetector;
import org.jackhuang.hmcl.util.platform.macos.MacDeviceInfo;
import org.jackhuang.hmcl.util.platform.macos.MacOSHardwareDetector;
import org.jackhuang.hmcl.util.platform.windows.Kernel32;
import org.jackhuang.hmcl.util.platform.windows.WinReg;
import org.jackhuang.hmcl.util.platform.windows.WindowsHardwareDetector;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Provides cached operating system and hardware information and current physical memory usage.
@NotNullByDefault
public final class SystemInfo {

    /// The charset used for native text, honoring the `hmcl.native.encoding` override.
    public static final Charset NATIVE_CHARSET;

    /// The current system version; always an [OSVersion.Windows] instance on Windows.
    public static final OSVersion SYSTEM_VERSION;

    /// The Windows ANSI code page, falling back to the console code page; -1 if unavailable or not on Windows.
    public static final int CODE_PAGE;

    static {
        @Nullable String nativeEncoding = System.getProperty("native.encoding");
        @Nullable String hmclNativeEncoding = System.getProperty("hmcl.native.encoding");
        Charset nativeCharset = Charset.defaultCharset();

        try {
            if (hmclNativeEncoding != null) {
                nativeCharset = Charset.forName(hmclNativeEncoding);
            } else {
                if (nativeEncoding != null && !nativeEncoding.equalsIgnoreCase(nativeCharset.name())) {
                    nativeCharset = Charset.forName(nativeEncoding);
                }

                if (nativeCharset == StandardCharsets.UTF_8 || nativeCharset == StandardCharsets.US_ASCII) {
                    nativeCharset = StandardCharsets.UTF_8;
                } else if ("GBK".equalsIgnoreCase(nativeCharset.name()) || "GB2312".equalsIgnoreCase(nativeCharset.name())) {
                    nativeCharset = Charset.forName("GB18030");
                }
            }
        } catch (UnsupportedCharsetException e) {
            e.printStackTrace(System.err);
        }
        NATIVE_CHARSET = nativeCharset;

        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            int codePage = -1;
            @Nullable OSVersion.Windows windowsVersion = null;

            @Nullable Kernel32 kernel32 = Kernel32.INSTANCE;
            @Nullable WinReg reg = WinReg.INSTANCE;

            // Get Windows version number
            if (reg != null) {
                var baseVersion = OSVersion.Windows.parse(System.getProperty("os.version", "Unknown"));
                int majorVersion = baseVersion.major();
                int minorVersion = baseVersion.minor();
                int buildNumber = baseVersion.build();
                int revision = baseVersion.revision();

                @Nullable Object currentBuild = reg.queryValue(WinReg.HKEY.HKEY_LOCAL_MACHINE,
                        "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion", "CurrentBuild");
                if (currentBuild instanceof String currentBuildStr) {
                    try {
                        buildNumber = Integer.parseInt(currentBuildStr);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid Windows build number: " + currentBuildStr);
                    }
                }

                if (majorVersion >= 10) {
                    @Nullable Object ubr = reg.queryValue(WinReg.HKEY.HKEY_LOCAL_MACHINE,
                            "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion", "UBR");

                    if (ubr instanceof Integer ubrValue)
                        revision = ubrValue;
                }

                windowsVersion = new OSVersion.Windows(majorVersion, minorVersion, buildNumber, revision);
            }

            if (windowsVersion == null) {
                try {
                    Process process = Runtime.getRuntime().exec(new String[]{"cmd", "ver"});
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), NATIVE_CHARSET))) {
                        @Nullable String line = reader.readLine();
                        Matcher matcher = Pattern.compile("(?<version>\\d+\\.\\d+\\.\\d+\\.\\d+?)]$")
                                .matcher(line == null ? "" : line.trim());
                        if (matcher.find())
                            windowsVersion = OSVersion.Windows.parse(matcher.group("version"));
                    }
                    process.destroy();
                } catch (Throwable ignored) {
                }
            }

            if (windowsVersion == null)
                windowsVersion = OSVersion.Windows.parse(System.getProperty("os.version", "Unknown"));

            // Get Code Page

            if (kernel32 != null)
                codePage = kernel32.GetACP();
            else {
                try {
                    Process process = Runtime.getRuntime().exec(new String[]{"chcp.com"});
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), NATIVE_CHARSET))) {
                        @Nullable String line = reader.readLine();
                        Matcher matcher = Pattern.compile("(?<cp>[0-9]+)$")
                                .matcher(line == null ? "" : line.trim());

                        if (matcher.find())
                            codePage = Integer.parseInt(matcher.group("cp"));
                    }
                    process.destroy();
                } catch (Throwable ignored) {
                }
            }

            SYSTEM_VERSION = windowsVersion;
            CODE_PAGE = codePage;
        } else {
            SYSTEM_VERSION = OSVersion.of(OperatingSystem.CURRENT_OS, System.getProperty("os.version", "Unknown"));
            CODE_PAGE = -1;
        }
    }

    /// The operating system and architecture of the current JVM.
    public static final Platform CURRENT_PLATFORM = Platform.getPlatform(OperatingSystem.CURRENT_OS, Architecture.CURRENT_ARCH);

    /// The operating system and native architecture of the host system.
    public static final Platform SYSTEM_PLATFORM = Platform.getPlatform(OperatingSystem.CURRENT_OS, Architecture.SYSTEM_ARCH);

    /// Whether the ARM64 host supports executing x86-64 applications through translation.
    private static final boolean SUPPORTED_TRANSLATION_X86_64;

    static {
        if (SYSTEM_PLATFORM.equals(Platform.WINDOWS_ARM64)) {
            SUPPORTED_TRANSLATION_X86_64 = SYSTEM_VERSION instanceof OSVersion.Windows windowsVersion
                    && windowsVersion.build() >= 21277;
        } else if (SYSTEM_PLATFORM.equals(Platform.MACOS_ARM64)) {
            SUPPORTED_TRANSLATION_X86_64 = Files.isRegularFile(Path.of("/usr/libexec/rosetta/runtime"));
        } else {
            SUPPORTED_TRANSLATION_X86_64 = false;
        }
    }

    /// Returns whether the ARM64 host supports x86-64 translation based on Windows build or Rosetta availability.
    public static boolean isSupportedTranslationX86_64() {
        return SUPPORTED_TRANSLATION_X86_64;
    }

    /// Returns whether the current system is Windows 7 or later.
    public static boolean isWindows7OrLater() {
        return SYSTEM_VERSION.isAtLeast(OSVersion.WINDOWS_7);
    }

    /// Returns whether a Wine environment variable is present on Windows.
    public static boolean isRunningUnderWine() {
        if (OperatingSystem.CURRENT_OS != OperatingSystem.WINDOWS) {
            return false;
        }

        String @Unmodifiable [] wineEnvVars = {
                "WINEPREFIX",
                "WINELOADER",
                "WINEDLLPATH"
        };

        for (String var : wineEnvVars) {
            if (System.getenv(var) != null) {
                return true;
            }
        }
        return false;
    }

    /// Lazily initializes the platform detector and cached system information.
    private static final class Holder {

        /// The hardware detector for the current operating system.
        public static final HardwareDetector DETECTOR;

        static {
            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS)
                DETECTOR = new WindowsHardwareDetector();
            else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX)
                DETECTOR = new LinuxHardwareDetector();
            else if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS)
                DETECTOR = new MacOSHardwareDetector();
            else
                DETECTOR = new HardwareDetector();
        }

        /// The cached operating system name and detailed description.
        public static final OperatingSystemInfo OPERATING_SYSTEM = DETECTOR.detectOperatingSystemInfo();

        /// The detected total physical memory size, in bytes.
        public static final long TOTAL_MEMORY = DETECTOR.getTotalMemorySize();

        /// A nonempty immutable processor snapshot supplied by the detector.
        public static final @Unmodifiable List<CentralProcessor> CENTRAL_PROCESSORS = DETECTOR.detectCentralProcessors();

        /// The immutable graphics card snapshot, empty if detection is unavailable or fails.
        public static final @Unmodifiable List<GraphicsCard> GRAPHICS_CARDS = DETECTOR.detectGraphicsCards();

        /// The cached device identity, or `null` if unavailable.
        public static final @Nullable DeviceInfo DEVICE_INFO = DETECTOR.detectDeviceInfo();

        /// The cached desktop environment description, or `null` if unavailable.
        public static final @Nullable String DESKTOP = DETECTOR.detectDesktopEnvironment();
    }

    /// Initializes cached system information if needed and logs a system summary on each call.
    public static void initialize() {
        StringBuilder builder = new StringBuilder("System Info:");
        builder.append("\n - Operating System: ").append(getOperatingSystemDescription());

        @Nullable DeviceInfo deviceInfo = getDeviceInfo();
        if (deviceInfo != null) {
            builder.append("\n - Device: ").append(deviceInfo.name());
            if (deviceInfo instanceof MacDeviceInfo macDeviceInfo) {
                @Nullable String modelIdentifier = macDeviceInfo.modelIdentifier();
                if (modelIdentifier != null && !modelIdentifier.isBlank() && !modelIdentifier.equals(deviceInfo.name()))
                    builder.append(" (").append(modelIdentifier).append(')');
            }
        }

        // CPU
        @Unmodifiable List<CentralProcessor> processors = getCentralProcessors();
        for (int i = 0; i < processors.size(); i++) {
            builder.append("\n - CPU");
            if (processors.size() > 1)
                builder.append(' ').append(i + 1);
            builder.append(": ").append(processors.get(i));
        }

        // Graphics Card
        @Unmodifiable List<GraphicsCard> graphicsCards = getGraphicsCards();
        if (graphicsCards.isEmpty())
            builder.append("\n - GPU: Not Found");
        else if (graphicsCards.size() == 1)
            builder.append("\n - GPU: ").append(graphicsCards.get(0));
        else {
            int index = 1;
            for (GraphicsCard graphicsCard : graphicsCards) {
                builder.append("\n - GPU ").append(index++).append(": ").append(graphicsCard);
            }
        }

        // Memory
        long totalMemorySize = getTotalMemorySize();
        long usedMemorySize = getUsedMemorySize();

        builder.append("\n - Memory: ")
                .append(DataSizeUnit.format(usedMemorySize))
                .append(" / ")
                .append(DataSizeUnit.format(totalMemorySize));

        if (totalMemorySize > 0 && usedMemorySize > 0)
            builder.append(" (").append((int) (((double) usedMemorySize / totalMemorySize) * 100)).append("%)");

        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            long pageFileSize = getTotalPageFileSize();
            builder.append("\n - Page Files (Current Capacity): ")
                    .append(pageFileSize >= 0 ? DataSizeUnit.format(pageFileSize) : "Unknown");
            PageFileConfiguration configuration = getPageFileConfiguration();
            builder.append(", Configuration: ").append(configuration.mode());
            if (configuration.mode() == PageFileConfiguration.Mode.MANUAL)
                builder.append(", Configured Limit: ").append(DataSizeUnit.format(configuration.maximumSize()));
        }

        LOG.info(builder.toString());
    }

    /// Returns the cached distribution or system name, falling back to `os.name`.
    /// Windows names include the display release when available.
    public static String getOperatingSystemName() {
        return Holder.OPERATING_SYSTEM.name();
    }

    /// Returns the cached system name and version, prefixed with the distribution's pretty name when available.
    public static String getOperatingSystemDescription() {
        return Holder.OPERATING_SYSTEM.description();
    }

    /// Returns a snapshot using the cached total and currently available physical memory.
    ///
    /// Returns [PhysicalMemoryStatus#INVALID] if the total is non-positive or available memory is negative.
    public static PhysicalMemoryStatus getPhysicalMemoryStatus() {
        long totalMemorySize = getTotalMemorySize();
        long freeMemorySize = getFreeMemorySize();

        return totalMemorySize > 0 && freeMemorySize >= 0
                ? new PhysicalMemoryStatus(totalMemorySize, freeMemorySize)
                : PhysicalMemoryStatus.INVALID;
    }

    /// Returns the cached total physical memory size in bytes, or zero if detection is unsupported.
    public static long getTotalMemorySize() {
        return Holder.TOTAL_MEMORY;
    }

    /// Queries current page file capacity in bytes without caching it.
    ///
    /// Returns zero when no page files exist, or -1 if unsupported or detection fails.
    public static long getTotalPageFileSize() {
        return Holder.DETECTOR.getTotalPageFileSize();
    }

    /// Queries configured page file sizing policy without caching; changes may require a restart.
    public static PageFileConfiguration getPageFileConfiguration() {
        return Holder.DETECTOR.getPageFileConfiguration();
    }

    /// Queries the available physical memory in bytes, including reclaimable memory where supported.
    ///
    /// Returns zero if detection is unsupported.
    public static long getFreeMemorySize() {
        return Holder.DETECTOR.getFreeMemorySize();
    }

    /// Returns the cached total minus the currently available physical memory, in bytes, clamped to zero.
    ///
    /// Returns zero without querying available memory if the cached total is non-positive.
    public static long getUsedMemorySize() {
        long totalMemorySize = getTotalMemorySize();
        if (totalMemorySize <= 0)
            return 0;

        return Long.max(0, totalMemorySize - getFreeMemorySize());
    }

    /// Returns a nonempty immutable list of processor snapshots in stable order.
    ///
    /// Sources without package topology may provide only the first processor identity.
    /// If detection returns no processors, a synthetic `Unknown` processor with no vendor is supplied.
    /// For a single entry with no core counts, logical and performance counts use [Runtime#availableProcessors()].
    /// These display estimates do not establish the hardware's physical core count or package topology.
    public static @Unmodifiable List<CentralProcessor> getCentralProcessors() {
        return Holder.CENTRAL_PROCESSORS;
    }

    /// Returns the cached immutable graphics card list, empty if no cards were reported or detection failed.
    public static @Unmodifiable List<GraphicsCard> getGraphicsCards() {
        return Holder.GRAPHICS_CARDS;
    }

    /// Returns the cached device identity, or `null` if unavailable or unsupported.
    ///
    /// This describes the hardware product, not the user-configured hostname.
    /// Detection runs once during initialization of the cached system information.
    public static @Nullable DeviceInfo getDeviceInfo() {
        return Holder.DEVICE_INFO;
    }

    /// Returns the cached desktop environment description, or `null` if detection is unsupported or no name is available.
    ///
    /// The description includes the session type when supplied by the platform detector.
    /// The result is detected once during initialization of the cached system information and is not refreshed.
    public static @Nullable String getDesktopEnvironment() {
        return Holder.DESKTOP;
    }

    /// Prevents instantiation of this utility class.
    private SystemInfo() {
    }
}
