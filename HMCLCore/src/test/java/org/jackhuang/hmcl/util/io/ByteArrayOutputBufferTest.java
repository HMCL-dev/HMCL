package org.jackhuang.hmcl.util.io;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * @author Glavo
 */
public final class ByteArrayOutputBufferTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 8, 15, 16, 17, 32})
    public void testCopyFrom(int size) throws IOException {
        byte[] data = new byte[size];
        new Random(0).nextBytes(data);


        ByteArrayBuilder builder = new ByteArrayBuilder(16);
        builder.copyFrom(new ByteArrayInputStream(data));
        assertArrayEquals(data, builder.toByteArray());
        assertArrayEquals(data, builder.toByteArrayWithoutCopy());

        builder = new ByteArrayBuilder(16);
        builder.copyFrom(new ByteArrayInputStream(data) {
            @Override
            public int available() {
                return 0;
            }
        });
        assertArrayEquals(data, builder.toByteArray());
        assertArrayEquals(data, builder.toByteArrayWithoutCopy());

        builder = new ByteArrayBuilder(16);
        builder.copyFrom(new ByteArrayInputStream(data) {
            @Override
            public int available() {
                return data.length - 1;
            }
        });
        assertArrayEquals(data, builder.toByteArray());
        assertArrayEquals(data, builder.toByteArrayWithoutCopy());
    }
}
