package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.Metadata;

import javax.imageio.ImageIO;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public abstract class ResourceTransformer {
    private static final File transformedResourceRoot = Metadata.HMCL_DIRECTORY.resolve("transformedResources").toFile();

    static {
        if (!transformedResourceRoot.exists()) {
            if (!transformedResourceRoot.mkdir()) {
                throw new RuntimeException(String.format("Cannot make dir \"%s\".", transformedResourceRoot.getAbsolutePath()));
            }
        }
    }

    public static final ResourceTransformer webpResourceTransformer = new ResourceTransformer("webpResource") {
        @Override
        protected void transformResource(String resourceURL, InputStream inputStream, OutputStream outputStream) throws IOException {
            ImageIO.write(ImageIO.read(inputStream), "png", outputStream);
        }
    };

    private final File currentTransformerRoot;

    private final Map<String, File> processedResources = new HashMap<>();

    protected ResourceTransformer(String currentStoragePrefix) {
        this.currentTransformerRoot = new File(transformedResourceRoot, currentStoragePrefix);

        if (!this.currentTransformerRoot.exists()) {
            if (!this.currentTransformerRoot.mkdir()) {
                throw new RuntimeException(String.format("Cannot make dir \"%s\".", this.currentTransformerRoot.getAbsolutePath()));
            }
        }
    }

    public final InputStream getTransformedResource(String url /* e.g. "/assets/img/background.webp" */) {
        if (!url.startsWith("/")) {
            throw new ResourceNotFoundError("Resource url has to be started with '/'.");
        }

        if (!processedResources.containsKey(url)) {
            this.generateTransformedResource(url);
        }

        try {
            return new FileInputStream(processedResources.get(url));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void generateTransformedResource(String url /* e.g. "/assets/img/background.webp" */) {
        URL inputURL = this.getClass().getResource(url);
        if (inputURL == null) {
            throw new ResourceNotFoundError(String.format("Cannot find resource \"%s\".", url));
        }

        File outputFile = new File(this.currentTransformerRoot, url.substring(1));
        if (!outputFile.getParentFile().exists()) {
            if (!outputFile.getParentFile().mkdirs()) {
                throw new RuntimeException(String.format("Cannot make dir \"%s\".", outputFile.getParentFile().getAbsolutePath()));
            }
        }

        try (
                InputStream inputStream = inputURL.openStream();
                OutputStream outputStream = Files.newOutputStream(outputFile.toPath())
        ) {
            this.transformResource(url, inputStream, outputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.processedResources.put(url, outputFile);
    }

    protected abstract void transformResource(String resourceURL, InputStream inputStream, OutputStream outputStream) throws IOException;
}
