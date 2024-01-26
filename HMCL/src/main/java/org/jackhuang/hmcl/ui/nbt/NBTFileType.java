package org.jackhuang.hmcl.ui.nbt;

import com.github.steveice10.opennbt.NBTIO;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import org.apache.commons.compress.utils.BoundedInputStream;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.*;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public enum NBTFileType {
    COMPRESSED("dat", "dat_old") {
        @Override
        public Tag read(File file) throws IOException {
            try (BufferedInputStream fileInputStream = new BufferedInputStream(new FileInputStream(file))) {
                fileInputStream.mark(3);
                byte[] header = new byte[3];
                if (fileInputStream.read(header) < 3) {
                    throw new IOException("File is too small");
                }
                fileInputStream.reset();

                InputStream input;
                if (Arrays.equals(header, new byte[]{0x1f, (byte) 0x8b, 0x08})) {
                    input = new GZIPInputStream(fileInputStream);
                } else {
                    input = fileInputStream;
                }

                Tag tag = NBTIO.readTag(input);
                if (!(tag instanceof CompoundTag))
                    throw new IOException("Unexpected tag: " + tag);
                return tag;
            }
        }
    },
    ANVIL("mca") {
        @Override
        public Tag read(File file) throws IOException {
            return REGION.read(file);
        }

        @Override
        public NBTTreeView.Item readAsTree(File file) throws IOException {
            return REGION.readAsTree(file);
        }
    },
    REGION("mcr") {
        @Override
        public Tag read(File file) throws IOException {
            try (RandomAccessFile r = new RandomAccessFile(file, "r")) {
                byte[] header = new byte[4096];
                byte[] buffer = new byte[1 * 1024 * 1024]; // The maximum size of each chunk is 1MiB
                Inflater inflater = new Inflater();

                ListTag tag = new ListTag(file.getName(), CompoundTag.class);

                r.readFully(header);
                for (int i = 0; i < 4096; i += 4) {
                    int offset = ((header[i] & 0xff) << 16) + ((header[i + 1] & 0xff) << 8) + (header[i + 2] & 0xff);
                    int length = header[i + 3] & 0xff;

                    if (offset == 0 || length == 0) {
                        continue;
                    }

                    r.seek(offset * 4096L);
                    r.readFully(buffer, 0, length * 4096);

                    int chunkLength = ((buffer[0] & 0xff) << 24) + ((buffer[1] & 0xff) << 16) + ((buffer[2] & 0xff) << 8) + (buffer[3] & 0xff);

                    InputStream input = new ByteArrayInputStream(buffer);
                    input.skip(5);
                    input = new BoundedInputStream(input, chunkLength - 1);

                    switch (buffer[4]) {
                        case 0x01:
                            // GZip
                            input = new GZIPInputStream(input);
                            break;
                        case 0x02:
                            // Zlib
                            inflater.reset();
                            input = new InflaterInputStream(input, inflater);
                            break;
                        case 0x03:
                            // Uncompressed
                            break;
                        default:
                            throw new IOException("Unsupported compression method: " + Integer.toHexString(buffer[4] & 0xff));
                    }

                    try (InputStream in = input) {
                        Tag chunk = NBTIO.readTag(in);
                        if (!(chunk instanceof CompoundTag))
                            throw new IOException("Unexpected tag: " + chunk);

                        tag.add(chunk);
                    }
                }
                return tag;
            }
        }

        @Override
        public NBTTreeView.Item readAsTree(File file) throws IOException {
            NBTTreeView.Item item = new NBTTreeView.Item(read(file));

            for (Tag tag : ((ListTag) item.getValue())) {
                CompoundTag chunk = (CompoundTag) tag;

                NBTTreeView.Item tree = NBTTreeView.buildTree(chunk);

                Tag xPos = chunk.get("xPos");
                Tag zPos = chunk.get("zPos");

                if (xPos instanceof IntTag && zPos instanceof IntTag) {
                    tree.setText(String.format("Chunk: %d  %d", xPos.getValue(), zPos.getValue()));
                } else {
                    tree.setText("Chunk: Unknown");
                }

                item.getChildren().add(tree);
            }

            return item;
        }
    };

    static final NBTFileType[] types = values();

    public static NBTFileType ofFile(File file) {
        String ext = FileUtils.getExtension(file);
        for (NBTFileType type : types) {
            for (String extension : type.extensions) {
                if (extension.equals(ext))
                    return type;
            }
        }

        return null;
    }

    private final String[] extensions;

    NBTFileType(String... extensions) {
        this.extensions = extensions;
    }

    public abstract Tag read(File file) throws IOException;

    public NBTTreeView.Item readAsTree(File file) throws IOException {
        NBTTreeView.Item root = NBTTreeView.buildTree(read(file));
        root.setName(file.getName());
        return root;
    }
}
