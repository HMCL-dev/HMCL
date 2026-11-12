// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.gradle.utils

import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.zip.ZipFile

class ArtifactUtils {

    companion object {
        val algorithms = mapOf(
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

        fun attachSignature(jar: File, keyLocation: String) {
            val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(File(keyLocation).readBytes()))
            val signer = Signature.getInstance("SHA512withRSA")
            signer.initSign(privateKey)
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

        fun resolveArtifactFile(jarFile: File, ext: String) = jarFile.resolveSibling(jarFile.nameWithoutExtension + '.' + ext)
    }
}
