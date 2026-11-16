// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.shader;

import kala.encdet.EncodingDetector;
import org.jackhuang.hmcl.addon.LocalAddonManager;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.util.PropertiesOrdered;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Unmodifiable;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class ShaderPackManager extends LocalAddonManager<ShaderPackFile> {

    private static final String IRIS_COMMENT =
            "This file stores configuration options for Iris, such as the currently active shaderpack";

    public ShaderPackManager(DefaultGameInstance instance) {
        super(instance);
    }

    private boolean loaded = false;

    @Override
    public Path getDirectory() {
        return instance.getShadersDirectory();
    }

    @Override
    public @Unmodifiable List<ShaderPackFile> getLocalFiles() throws IOException {
        lock.lock();
        try {
            if (!loaded)
                refresh();
            return super.getLocalFiles();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Comparator<ShaderPackFile> getComparator() {
        return ShaderPackFile::compareTo;
    }

    private void addShaderPackInfo(Path file) throws IOException {
        ShaderPackFile shaderPackFile = ShaderPackFile.fromFile(this, file);
        if (shaderPackFile != null) localFiles.add(shaderPackFile);
    }

    @Override
    public void refresh() throws IOException {
        lock.lock();
        try {
            localFiles.clear();

            if (Files.isDirectory(getDirectory())) {
                try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(getDirectory())) {
                    for (Path item : directoryStream) {
                        try {
                            addShaderPackInfo(item);
                        } catch (IOException e) {
                            LOG.warning("Failed to load resource pack " + item, e);
                        }
                    }
                }
            }

            loaded = true;
        } finally {
            lock.unlock();
        }
    }

    public void importShaderPack(Path file) throws IOException, IllegalArgumentException {
        if (ShaderPackFile.isFileShaderPack(file)) {
            Files.createDirectories(getDirectory());
            FileUtils.copyTo(file, getDirectory());

            loaded = false;
        } else {
            throw new IllegalArgumentException("File is not shader pack");
        }
    }

    public void removeShaderPacks(List<ShaderPackFile> files) throws IOException {
        for (var file : files) {
            file.delete();
        }

        loaded = false;
    }

    public void updateConfig(ShaderPackFile pack, String newFileNameWithExt) {
        String oldFileNameWithExt = pack.getFileNameWithExtension();
        var shaderConfigPath = pack.getFile().resolveSibling(oldFileNameWithExt + ".txt");
        if (Files.isRegularFile(shaderConfigPath)) {
            var newConfigPath = pack.getFile().resolveSibling(newFileNameWithExt + ".txt");
            try {
                Files.move(shaderConfigPath, newConfigPath);
            } catch (IOException e) {
                LOG.warning("Failed to rename shader config file " + shaderConfigPath, e);
            }
        }

        iris:
        {
            Path irisConf = instance.getRunDirectory().resolve("config").resolve("iris.properties");
            if (!Files.isRegularFile(irisConf)) break iris;

            var prop = new Properties();
            try {
                prop.load(Files.newInputStream(irisConf));
            } catch (IOException e) {
                LOG.warning("Failed to load Iris config from " + irisConf);
                break iris;
            }

            if (oldFileNameWithExt.equals(prop.getProperty("shaderPack"))) {
                prop.setProperty("shaderPack", newFileNameWithExt);
                try {
                    prop.store(Files.newOutputStream(irisConf), IRIS_COMMENT);
                } catch (IOException e) {
                    LOG.warning("Failed to save Iris config to " + irisConf, e);
                }
            }
        }

        optifine:
        {
            Path optifineConf = instance.getRunDirectory().resolve("optionsshaders.txt");
            if (!Files.isRegularFile(optifineConf)) break optifine;

            byte[] bytes;
            try {
                bytes = Files.readAllBytes(optifineConf);
            } catch (IOException | UncheckedIOException e) {
                LOG.warning("Failed to read OptiFine shaders options from " + optifineConf, e);
                break optifine;
            }

            EncodingDetector.Encoding bestEncoding = EncodingDetector.MODERN_WEB.detect(bytes).bestEncoding();
            if (bestEncoding == EncodingDetector.Encoding.ASCII)
                bestEncoding = EncodingDetector.Encoding.UTF_8;
            Charset encoding = bestEncoding != null && bestEncoding.approximateCharset() != null
                    ? Objects.requireNonNull(bestEncoding.approximateCharset()) : StandardCharsets.UTF_8;

            var prop = new PropertiesOrdered();
            try {
                prop.load(new InputStreamReader(new ByteArrayInputStream(bytes), encoding));
            } catch (IOException e) {
                LOG.warning("Failed to read OptiFine shaders options from " + optifineConf, e);
                break optifine;
            }

            if (oldFileNameWithExt.equals(prop.getProperty("shaderPack"))) {
                prop.setProperty("shaderPack", newFileNameWithExt);
                try {
                    prop.store(Files.newBufferedWriter(optifineConf, encoding), null);
                } catch (IOException e) {
                    LOG.warning("Failed to save OptiFine config to " + optifineConf, e);
                }
            }
        }
    }
}
