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

package org.jackhuang.hmcl.gradle.utils

import org.gradle.api.logging.Logger
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.zip.ZipFile

class PackUtils {

    companion object {
        val algorithms = linkedMapOf(
            "SHA-1" to "sha1",
            "SHA-256" to "sha256",
            "SHA-512" to "sha512"
        )

        fun digest(algorithm: String, bytes: ByteArray): ByteArray = MessageDigest.getInstance(algorithm).digest(bytes)

        fun createChecksum(file: File) {
            algorithms.forEach { (algorithm, ext) ->
                File(file.parentFile, "${file.name}.$ext").writeText(
                    digest(algorithm, file.readBytes()).joinToString(separator = "", postfix = "\n") { "%02x".format(it) }
                )
            }
        }

        fun attachSignature(jar: File, logger: Logger) {
            val keyLocation = System.getenv("HMCL_SIGNATURE_KEY")
            if (keyLocation == null) {
                logger.warn("Missing signature key")
                return
            }

            val privatekey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(File(keyLocation).readBytes()))
            val signer = Signature.getInstance("SHA512withRSA")
            signer.initSign(privatekey)
            ZipFile(jar).use { zip ->
                zip.stream()
                    .sorted(Comparator.comparing { it.name })
                    .filter { it.name != "META-INF/hmcl_signature" }
                    .forEach {
                        signer.update(digest("SHA-512", it.name.toByteArray()))
                        signer.update(digest("SHA-512", zip.getInputStream(it).readBytes()))
                    }
            }
            val signature = signer.sign()
            FileSystems.newFileSystem(URI.create("jar:" + jar.toURI()), emptyMap<String, Any>()).use { zipfs ->
                Files.newOutputStream(zipfs.getPath("META-INF/hmcl_signature")).use { it.write(signature) }
            }
        }

        fun artifactFile(jarFile: File, ext: String) = jarFile.resolveSibling(jarFile.nameWithoutExtension + '.' + ext)
    }
}
