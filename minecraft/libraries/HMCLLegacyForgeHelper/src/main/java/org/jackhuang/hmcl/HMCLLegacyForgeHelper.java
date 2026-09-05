/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.charset.Charset;
import java.security.ProtectionDomain;

@SuppressWarnings("JavaPrintToLogpoint")
public final class HMCLLegacyForgeHelper {
    private HMCLLegacyForgeHelper() {
        throw new AssertionError();
    }

    private static final String TARGET_URL = "http://files.minecraftforge.net/fmllibs/%s";

    private static String newRootUrl = "https://hmcl.glavo.site/metadata/fmllibs/%s";

    public static void premain(String agentArgs, Instrumentation inst) {
        if (agentArgs != null && !agentArgs.trim().isEmpty()) {
            newRootUrl = agentArgs.trim();
        }

        inst.addTransformer(new CoreFMLLibrariesTransformer());
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        premain(agentArgs, inst);
    }

    private final static class CoreFMLLibrariesTransformer implements ClassFileTransformer {

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            if (className == null) {
                return null;
            }

            if ("cpw/mods/fml/relauncher/CoreFMLLibraries".equals(className) || "cpw.mods.fml.relauncher.CoreFMLLibraries".equals(className)) {
                try {
                    System.out.println("[LegacyForgeHelper] Transforming " + className + " ...");
                    byte[] modified = patchConstantPoolUtf8(classfileBuffer, TARGET_URL, newRootUrl);
                    System.out.println("[LegacyForgeHelper] Successfully patched RootURL in " + className);
                    return modified;
                } catch (Throwable t) {
                    System.err.println("[LegacyForgeHelper] Failed to patch class");
                    t.printStackTrace();
                }
            }
            return null;
        }
    }

    private static byte[] patchConstantPoolUtf8(byte[] classBytes, String targetStr, String replacementStr) {
        if (classBytes == null || classBytes.length < 10) {
            return classBytes;
        }

        int pos = 0;

        int magic = ((classBytes[pos++] & 0xFF) << 24) | ((classBytes[pos++] & 0xFF) << 16) | ((classBytes[pos++] & 0xFF) << 8) | (classBytes[pos++] & 0xFF);
        if (magic != 0xCAFEBABE) {
            throw new IllegalArgumentException("Invalid class file (magic mismatch)");
        }

        // minor_version & major_version
        pos += 4;

        int cpCount = ((classBytes[pos++] & 0xFF) << 8) | (classBytes[pos++] & 0xFF);

        for (int i = 1; i < cpCount; i++) {
            int tag = classBytes[pos++] & 0xFF;
            switch (tag) {
                case 1: // CONSTANT_Utf8
                    int utf8LengthPos = pos;
                    int len = ((classBytes[pos++] & 0xFF) << 8) | (classBytes[pos++] & 0xFF);
                    String str = new String(classBytes, pos, len, Charset.forName("UTF-8"));
                    pos += len;

                    if (targetStr.equals(str)) {
                        byte[] replacementBytes = replacementStr.getBytes(Charset.forName("UTF-8"));
                        if (replacementBytes.length > 65535) {
                            throw new IllegalArgumentException("Replacement URL is too long (max 65535 bytes)");
                        }

                        int beforeLen = utf8LengthPos;
                        int afterOffset = utf8LengthPos + 2 + len;
                        int afterLen = classBytes.length - afterOffset;

                        byte[] newClass = new byte[beforeLen + 2 + replacementBytes.length + afterLen];

                        System.arraycopy(classBytes, 0, newClass, 0, beforeLen);
                        newClass[beforeLen] = (byte) ((replacementBytes.length >>> 8) & 0xFF);
                        newClass[beforeLen + 1] = (byte) (replacementBytes.length & 0xFF);
                        System.arraycopy(replacementBytes, 0, newClass, beforeLen + 2, replacementBytes.length);
                        System.arraycopy(classBytes, afterOffset, newClass, beforeLen + 2 + replacementBytes.length, afterLen);

                        return newClass;
                    }
                    break;

                case 3: // CONSTANT_Integer
                case 4: // CONSTANT_Float
                case 9: // CONSTANT_Fieldref
                case 10: // CONSTANT_Methodref
                case 11: // CONSTANT_InterfaceMethodref
                case 12: // CONSTANT_NameAndType
                case 18: // CONSTANT_InvokeDynamic
                    pos += 4;
                    break;

                case 5: // CONSTANT_Long
                case 6: // CONSTANT_Double
                    pos += 8;
                    i++;
                    break;

                case 7: // CONSTANT_Class
                case 8: // CONSTANT_String
                case 16: // CONSTANT_MethodType
                    pos += 2;
                    break;

                case 15: // CONSTANT_MethodHandle
                    pos += 3;
                    break;

                default:
                    throw new IllegalArgumentException("Unsupported constant pool tag: " + tag + " at pos " + (pos - 1));
            }
        }

        System.out.println("[LegacyForgeHelper] Warning: Target URL string not found in constant pool.");
        return classBytes;
    }
}
