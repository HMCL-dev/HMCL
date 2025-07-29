/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.platform.linux;

import org.glavo.pci.ids.PCIIDsDatabase;
import org.glavo.pci.ids.model.Device;
import org.glavo.pci.ids.model.Vendor;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.platform.hardware.GraphicsCard;
import org.jackhuang.hmcl.util.platform.hardware.HardwareVendor;

import java.io.*;
import java.lang.ref.SoftReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 * @see <a href="https://github.com/fastfetch-cli/fastfetch/blob/33205326683fd89ec63204f83549839e2374c286/src/detection/gpu/gpu_linux.c">gpu_linux.c</a>
 */
final class LinuxGPUDetector {
    private static volatile SoftReference<PCIIDsDatabase> databaseCache;

    private static final Pattern PCI_MODALIAS_PATTERN =
            Pattern.compile("pci:v(?<vendorId>\\p{XDigit}{8})d(?<deviceId>\\p{XDigit}{8})sv(?<subVendorId>\\p{XDigit}{8})sd(?<subDeviceId>\\p{XDigit}{8})bc(?<classId>\\p{XDigit}{2})sc(?<subclassId>\\p{XDigit}{2})i\\p{XDigit}{2}");
    private static final Pattern PCI_DEVICE_PATTERN =
            Pattern.compile("(?<pciDomain>\\p{XDigit}+):(?<pciBus>\\p{XDigit}+):(?<pciDevice>\\p{XDigit}+)\\.(?<pciFunc>\\p{XDigit}+)");
    private static final Pattern OF_DEVICE_PATTERN =
            Pattern.compile("of:N(img)?gpuT[^C]*C(?<compatible>.*)");

    private static PCIIDsDatabase getPCIIDsDatabase() {
        SoftReference<PCIIDsDatabase> databaseWeakReference = LinuxGPUDetector.databaseCache;
        PCIIDsDatabase pciIDsDatabase;

        if (databaseCache != null) {
            pciIDsDatabase = databaseWeakReference.get();
            if (pciIDsDatabase != null) {
                return pciIDsDatabase;
            }
        }

        for (String path : new String[]{
                "/usr/share/misc/pci.ids",
                "/usr/share/hwdata/pci.ids",
                "/usr/local/share/hwdata/pci.ids"
        }) {
            Path p = Paths.get(path);
            if (Files.isRegularFile(p)) {
                try {
                    pciIDsDatabase = PCIIDsDatabase.load(p);
                    databaseCache = new SoftReference<>(pciIDsDatabase);
                    return pciIDsDatabase;
                } catch (IOException e) {
                    return null;
                }
            }
        }

        return null;
    }

    private static void detectDriver(GraphicsCard.Builder builder, Path deviceDir) {
        try {
            Path driverDir = Files.readSymbolicLink(deviceDir.resolve("driver"));
            if (driverDir.getNameCount() > 0) {

                String name = driverDir.getName(driverDir.getNameCount() - 1).toString();
                builder.setDriver(name);

                Path versionFile = deviceDir.resolve("driver/module/version");
                if (Files.isRegularFile(versionFile)) {
                    builder.setDriverVersion(Files.readString(versionFile).trim());
                } else if ("zx".equals(name)) {
                    versionFile = deviceDir.resolve("zx_info/driver_version");
                    if (Files.isRegularFile(versionFile)) {
                        builder.setDriverVersion(Files.readString(versionFile).trim());
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static GraphicsCard detectPCI(Path deviceDir, String modalias) throws IOException {
        Matcher matcher = PCI_MODALIAS_PATTERN.matcher(modalias);
        if (!matcher.matches())
            return null;

        GraphicsCard.Builder builder = GraphicsCard.builder();

        int vendorId = Integer.parseInt(matcher.group("vendorId"), 16);
        int deviceId = Integer.parseInt(matcher.group("deviceId"), 16);
        int classId = Integer.parseInt(matcher.group("classId"), 16);
        int subclassId = Integer.parseInt(matcher.group("subclassId"), 16);

        if (classId != 0x03) // PCI_BASE_CLASS_DISPLAY
            return null; // Not a GPU device

        Path pciPath = Files.readSymbolicLink(deviceDir);
        int nameCount = pciPath.getNameCount();
        if (nameCount == 0)
            return null;

        matcher = PCI_DEVICE_PATTERN.matcher(pciPath.getName(nameCount - 1).toString());
        if (!matcher.matches())
            return null;

        int pciDomain = Integer.parseInt(matcher.group("pciDomain"), 16);
        int pciBus = Integer.parseInt(matcher.group("pciBus"), 16);
        int pciDevice = Integer.parseInt(matcher.group("pciDevice"), 16);
        int pciFunc = Integer.parseInt(matcher.group("pciFunc"), 16);

        builder.setVendor(HardwareVendor.ofPciVendorId(vendorId));
        detectDriver(builder, deviceDir);

        try {
            if (builder.getVendor() == HardwareVendor.AMD) {
                Path hwmon = deviceDir.resolve("hwmon");
                try (Stream<Path> subDirs = Files.list(hwmon)) {
                    for (Path subDir : Lang.toIterable(subDirs)) {
                        if (Files.isDirectory(subDir) && !subDir.getFileName().toString().startsWith(".")) {
                            builder.setType(Files.exists(subDir.resolve("in1_input"))
                                    ? GraphicsCard.Type.Integrated
                                    : GraphicsCard.Type.Discrete);
                            break;
                        }
                    }
                } catch (IOException ignored) {
                }

                Path revisionFile = deviceDir.resolve("revision");
                if (Files.isRegularFile(revisionFile)) {
                    String revisionString = Files.readString(revisionFile).trim();
                    int revision = Integer.decode(revisionString);
                    String prefix = String.format("%X,\t%X,\t", deviceId, revision);
                    //noinspection DataFlowIssue
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                            LinuxHardwareDetector.class.getResourceAsStream("/assets/platform/amdgpu.ids"), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith(prefix)) {
                                builder.setName(line.substring(prefix.length()));
                                break;
                            }
                        }
                    }
                }

            } else if (builder.getVendor() == HardwareVendor.INTEL) {
                builder.setType(pciDevice == 20 ? GraphicsCard.Type.Integrated : GraphicsCard.Type.Discrete);
            }
        } catch (Throwable ignored) {
        }

        if (builder.getName() == null || builder.getVendor() == null) {
            PCIIDsDatabase database = getPCIIDsDatabase();
            if (database != null) {
                Vendor vendor = database.findVendor(vendorId);
                if (vendor != null) {
                    if (builder.getVendor() == null)
                        builder.setVendor(HardwareVendor.of(vendor.getName()));

                    if (builder.getName() == null) {
                        Device device = vendor.getDevices().get(deviceId);
                        if (device != null) {
                            matcher = Pattern.compile(".*\\[(?<name>.*)]").matcher(device.getName());
                            if (matcher.matches())
                                builder.setName(GraphicsCard.cleanName(builder.getVendor() + " " + matcher.group("name")));
                            else
                                builder.setName(builder.getVendor() + " " + device.getName());
                        }
                    }
                }
            }
        }

        if (builder.getName() == null) {
            String subclassStr;
            switch (subclassId) {
                case 0: // PCI_CLASS_DISPLAY_VGA
                    subclassStr = " (VGA compatible)";
                    break;
                case 1: // PCI_CLASS_DISPLAY_XGA
                    subclassStr = " (XGA compatible)";
                    break;
                case 2: // PCI_CLASS_DISPLAY_3D
                    subclassStr = " (3D)";
                    break;
                default:
                    subclassStr = "";
            }

            builder.setName(String.format("%s Device %04X%s",
                    builder.getVendor() != null ? builder.getVendor().toString() : "Unknown",
                    deviceId, subclassStr));
        }

        if (builder.getType() == null) {
            if (builder.getVendor() == HardwareVendor.NVIDIA) {
                if (builder.getName().startsWith("GeForce")
                        || builder.getName().startsWith("Quadro")
                        || builder.getName().startsWith("Tesla"))
                    builder.setType(GraphicsCard.Type.Discrete);

            } else if (builder.getVendor() == HardwareVendor.MOORE_THREADS) {
                if (builder.getName().startsWith("MTT "))
                    builder.setType(GraphicsCard.Type.Discrete);
            }
        }

        return builder.build();
    }

    private static GraphicsCard detectOF(Path deviceDir, String modalias) throws IOException {
        Matcher matcher = OF_DEVICE_PATTERN.matcher(modalias);
        if (!matcher.matches())
            return null;

        GraphicsCard.Builder builder = new GraphicsCard.Builder();

        String compatible = matcher.group("compatible");
        int idx = compatible.indexOf(',');
        if (idx < 0) {
            String name = compatible.trim().toUpperCase(Locale.ROOT);
            if (name.equals("IMG-GPU"))  // Fucking Imagination
                builder.setVendor(HardwareVendor.IMG);
            else
                builder.setName(name);
        } else {
            String vendorName = compatible.substring(0, idx).trim();
            HardwareVendor vendor = HardwareVendor.getKnown(vendorName);
            if (vendor == null)
                vendor = new HardwareVendor(StringUtils.capitalizeFirst(vendorName));

            builder.setVendor(vendor);

            String name = compatible.substring(idx + 1).trim().toUpperCase(Locale.ROOT);
            if (vendor == HardwareVendor.IMG) {
                if (!name.equals("GPU"))
                    builder.setName(vendor + " " + name);
            } else
                builder.setName(vendor + " " + name);
        }

        builder.setType(GraphicsCard.Type.Integrated);

        detectDriver(builder, deviceDir);
        return builder.build();
    }

    static List<GraphicsCard> detect() {
        Path drm = Paths.get("/sys/class/drm");
        if (!Files.isDirectory(drm))
            return Collections.emptyList();

        ArrayList<GraphicsCard> cards = new ArrayList<>();

        try (Stream<Path> stream = Files.list(drm)) {
            for (Path deviceRoot : Lang.toIterable(stream)) {
                Path dirName = deviceRoot.getFileName();
                if (dirName == null)
                    continue;

                String name = dirName.toString();
                if (!name.startsWith("card") || name.indexOf('-') >= 0)
                    continue;

                Path deviceDir = deviceRoot.resolve("device");
                Path modaliasFile = deviceDir.resolve("modalias");
                if (!Files.isRegularFile(modaliasFile))
                    continue;

                try {
                    String modalias = Files.readString(modaliasFile).trim();
                    GraphicsCard graphicsCard = null;
                    if (modalias.startsWith("pci:"))
                        graphicsCard = detectPCI(deviceDir, modalias);
                    else if (modalias.startsWith("of:"))
                        graphicsCard = detectOF(deviceDir, modalias);

                    if (graphicsCard != null)
                        cards.add(graphicsCard);
                } catch (IOException ignored) {
                }
            }
        } catch (Throwable e) {
            LOG.warning("Failed to get graphics card info", e);
        } finally {
            databaseCache = null;
        }

        return Collections.unmodifiableList(cards);
    }

    private LinuxGPUDetector() {
    }
}
