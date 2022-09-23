package org.jackhuang.hmcl.auth.nide8;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

public class Nide8InjectorArtifactInfo {

    public static Nide8InjectorArtifactInfo from(Path location) throws IOException {
        try (JarFile jarFile = new JarFile(location.toFile())) {
            Attributes attributes = jarFile.getManifest().getMainAttributes();

            String title = Optional.ofNullable(attributes.getValue("Implementation-Title"))
                    .orElseThrow(() -> new IOException("Missing Implementation-Title"));
            if (!"nide8auth".equals(title)) {
                throw new IOException("Bad Implementation-Title");
            }

            return new Nide8InjectorArtifactInfo(location.toAbsolutePath());
        }
    }

    private Path location;

    public Nide8InjectorArtifactInfo(Path location) {

        this.location = location;
    }

    public Path getLocation() {
        return location;
    }

    @Override
    public String toString() {
        return "nide8auth";
    }
}