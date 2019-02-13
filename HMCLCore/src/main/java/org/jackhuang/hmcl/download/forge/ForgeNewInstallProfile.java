/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download.forge;

import org.jackhuang.hmcl.game.Artifact;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.function.ExceptionalFunction;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Immutable
public class ForgeNewInstallProfile {

    private final int spec;
    private final String minecraft;
    private final String json;
    private final List<Library> libraries;
    private final List<Processor> processors;
    private final Map<String, Datum> data;

    public ForgeNewInstallProfile(int spec, String minecraft, String json, List<Library> libraries, List<Processor> processors, Map<String, Datum> data) {
        this.spec = spec;
        this.minecraft = minecraft;
        this.json = json;
        this.libraries = libraries;
        this.processors = processors;
        this.data = data;
    }

    /**
     * Specification for install_profile.json.
     */
    public int getSpec() {
        return spec;
    }

    /**
     * Vanilla game version that this installer supports.
     */
    public String getMinecraft() {
        return minecraft;
    }

    /**
     * Version json to be installed.
     * @return path of the version json relative to the installer JAR file.
     */
    public String getJson() {
        return json;
    }

    /**
     * Libraries that processors depend on.
     * @return the required dependencies.
     */
    public List<Library> getLibraries() {
        return libraries == null ? Collections.emptyList() : libraries;
    }

    /**
     * Tasks to be executed to setup modded environment.
     */
    public List<Processor> getProcessors() {
        if (processors == null) return Collections.emptyList();
        return processors.stream().filter(p -> p.isSide("client")).collect(Collectors.toList());
    }

    /**
     * Data for processors.
     */
    public Map<String, String> getData() {
        if (data == null)
            return new HashMap<>();

        return data.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getClient()));
    }

    public static class Processor {
        private final List<String> sides;
        private final Artifact jar;
        private final List<Artifact> classpath;
        private final List<String> args;
        private final Map<String, String> outputs;

        public Processor(List<String> sides, Artifact jar, List<Artifact> classpath, List<String> args, Map<String, String> outputs) {
            this.sides = sides;
            this.jar = jar;
            this.classpath = classpath;
            this.args = args;
            this.outputs = outputs;
        }

        /**
         * Check which side this processor should be run on. We only support client install currently.
         * @param side can be one of "client", "server", "extract".
         * @return true if the processor can run on the side.
         */
        public boolean isSide(String side) {
            return sides == null || sides.contains(side);
        }

        /**
         * The executable jar of this processor task. Will be executed in installation process.
         * @return the artifact path of executable jar.
         */
        public Artifact getJar() {
            return jar;
        }

        /**
         * The dependencies of this processor task.
         * @return the artifact path of dependencies.
         */
        public List<Artifact> getClasspath() {
            return classpath == null ? Collections.emptyList() : classpath;
        }

        /**
         * Arguments to pass to the processor jar.
         * Each item can be in one of the following formats:
         * [artifact]: An artifact path, used for locating files.
         * {entry}: Get corresponding value of the entry in {@link ForgeNewInstallProfile#getData()}
         * {MINECRAFT_JAR}: path of the Minecraft jar.
         * {SIDE}: values other than "client" will be ignored.
         * @return arguments to pass to the processor jar.
         * @see ForgeNewInstallTask#parseLiteral(String, Map, ExceptionalFunction)
         */
        public List<String> getArgs() {
            return args == null ? Collections.emptyList() : args;
        }

        /**
         * File-checksum pairs, used for verifying the output file is correct.
         * Arguments to pass to the processor jar.
         * Keys can be in one of [artifact] or {entry}. Should be file path.
         * Values can be in one of {entry} or 'literal'. Should be SHA-1 checksum.
         * @return files output from this processor.
         * @see ForgeNewInstallTask#parseLiteral(String, Map, ExceptionalFunction)
         */
        public Map<String, String> getOutputs() {
            return outputs == null ? Collections.emptyMap() : outputs;
        }
    }

    public static class Datum {
        private final String client;

        public Datum(String client) {
            this.client = client;
        }

        /**
         * Can be in the following formats:
         * [value]: An artifact path.
         * 'value': A string literal.
         * value: A file in the installer package, to be extracted to a temp folder, and then have the absolute path in replacements.
         * @return Value to use for the client install
         */
        public String getClient() {
            return client;
        }
    }
}
