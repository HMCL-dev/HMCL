package org.jackhuang.hmcl.util.io;

import kala.compress.utils.SeekableInMemoryByteChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SeekableByteChannel;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class BufferedSeekableByteChannelTest {

    private SeekableByteChannel underlying;
    private BufferedSeekableByteChannel channel;

    @BeforeEach
    void setUp() {
        underlying = new SeekableInMemoryByteChannel();
        channel = new BufferedSeekableByteChannel(underlying);
    }

    // ---------- 构造测试 ----------
    @Test
    void testConstructorWithDefaultBufferSize() {
        assertNotNull(channel);
    }

    @Test
    void testConstructorWithCustomBufferSize() {
        assertDoesNotThrow(() -> new BufferedSeekableByteChannel(underlying, 4096));
    }

    @Test
    void testConstructorWithInvalidBufferSize() {
        assertThrows(IllegalArgumentException.class, () -> new BufferedSeekableByteChannel(underlying, 0));
        assertThrows(IllegalArgumentException.class, () -> new BufferedSeekableByteChannel(underlying, -1));
    }

    // ---------- 读操作测试 ----------
    @Test
    void testReadFromEmptyChannel() throws IOException {
        ByteBuffer dst = ByteBuffer.allocate(10);
        int bytesRead = channel.read(dst);
        assertEquals(-1, bytesRead);
        assertEquals(0, dst.position());
    }

    @Test
    void testReadSingleByte() throws IOException {
        underlying.write(ByteBuffer.wrap(new byte[]{42}));
        underlying.position(0);

        ByteBuffer dst = ByteBuffer.allocate(1);
        int bytesRead = channel.read(dst);
        assertEquals(1, bytesRead);
        assertEquals(42, dst.get(0));
        assertEquals(1, channel.position());
    }

    @Test
    void testReadMultipleBytesWithinBuffer() throws IOException {
        byte[] data = new byte[100];
        for (int i = 0; i < 100; i++) {
            data[i] = (byte) i;
        }
        underlying.write(ByteBuffer.wrap(data));
        underlying.position(0);

        ByteBuffer dst = ByteBuffer.allocate(50);
        int bytesRead = channel.read(dst);
        assertEquals(50, bytesRead);
        for (int i = 0; i < 50; i++) {
            assertEquals((byte) i, dst.get(i));
        }
        assertEquals(50, channel.position());
    }

    @Test
    void testReadAcrossBufferBoundary() throws IOException {
        byte[] data = new byte[10000];
        for (int i = 0; i < 10000; i++) {
            data[i] = (byte) (i % 256);
        }
        underlying.write(ByteBuffer.wrap(data));
        underlying.position(0);

        // 内部缓冲区默认8192，一次读取超过缓冲区大小
        ByteBuffer dst = ByteBuffer.allocate(9000);
        int bytesRead = channel.read(dst);
        assertEquals(9000, bytesRead);
        for (int i = 0; i < 9000; i++) {
            assertEquals((byte) (i % 256), dst.get(i));
        }
        assertEquals(9000, channel.position());
    }

    @Test
    void testReadToEOF() throws IOException {
        byte[] data = new byte[500];
        for (int i = 0; i < 500; i++) {
            data[i] = (byte) i;
        }
        underlying.write(ByteBuffer.wrap(data));
        underlying.position(0);

        ByteBuffer dst = ByteBuffer.allocate(600);
        int bytesRead = channel.read(dst);
        assertEquals(500, bytesRead);
        for (int i = 0; i < 500; i++) {
            assertEquals((byte) i, dst.get(i));
        }
        assertEquals(500, channel.position());

        // 再次读应该返回 -1
        dst.clear();
        bytesRead = channel.read(dst);
        assertEquals(-1, bytesRead);
    }

    @Test
    void testReadWithZeroRemaining() throws IOException {
        ByteBuffer dst = ByteBuffer.allocate(0);
        int bytesRead = channel.read(dst);
        assertEquals(0, bytesRead);
        assertEquals(0, channel.position());
    }

    // ---------- 定位测试 ----------
    @Test
    void testPosition() throws IOException {
        underlying.write(ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5}));
        underlying.position(0);

        assertEquals(0, channel.position());
        channel.position(3);
        assertEquals(3, channel.position());

        // 验证读位置正确
        ByteBuffer dst = ByteBuffer.allocate(2);
        int read = channel.read(dst);
        assertEquals(2, read);
        assertEquals(4, dst.get(0));
        assertEquals(5, dst.get(1));
    }

    @Test
    void testPositionWithinBuffer() throws IOException {
        byte[] data = new byte[200];
        Arrays.fill(data, (byte) 1);
        underlying.write(ByteBuffer.wrap(data));
        underlying.position(0);

        // 先读一些数据填充缓冲区
        ByteBuffer dst = ByteBuffer.allocate(100);
        channel.read(dst);
        assertEquals(100, channel.position());

        // 定位到缓冲区内部（例如 50）
        channel.position(50);
        assertEquals(50, channel.position());

        // 读数据应该从位置50继续
        dst.clear();
        int read = channel.read(dst);
        assertEquals(150, channel.position());
        // 这里无需验证数据内容，仅验证位置正确即可
    }

    @Test
    void testPositionOutsideBuffer() throws IOException {
        byte[] data = new byte[200];
        Arrays.fill(data, (byte) 1);
        underlying.write(ByteBuffer.wrap(data));
        underlying.position(0);

        // 先读一些数据填充缓冲区
        ByteBuffer dst = ByteBuffer.allocate(100);
        channel.read(dst);
        assertEquals(100, channel.position());

        // 定位到缓冲区外部（例如 150）
        channel.position(150);
        assertEquals(150, channel.position());

        // 读数据应该从位置150开始
        dst.clear();
        int read = channel.read(dst);
        assertEquals(200, channel.position());
        assertEquals(50, read);
    }

    @Test
    void testPositionNegative() {
        assertThrows(IllegalArgumentException.class, () -> channel.position(-1));
    }

    // ---------- 写操作测试 ----------
    @Test
    void testWrite() throws IOException {
        ByteBuffer src = ByteBuffer.wrap(new byte[]{10, 20, 30});
        int written = channel.write(src);
        assertEquals(3, written);
        assertEquals(3, channel.position());

        // 验证底层通道内容
        underlying.position(0);
        ByteBuffer dst = ByteBuffer.allocate(3);
        underlying.read(dst);
        dst.flip();
        assertEquals(10, dst.get());
        assertEquals(20, dst.get());
        assertEquals(30, dst.get());
    }

    @Test
    void testWriteAfterRead() throws IOException {
        // 先写入一些数据
        underlying.write(ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5}));
        underlying.position(0);

        // 读一部分
        ByteBuffer readBuf = ByteBuffer.allocate(2);
        channel.read(readBuf);
        assertEquals(2, channel.position());

        // 在当前位置写入
        ByteBuffer writeBuf = ByteBuffer.wrap(new byte[]{99, 100});
        int written = channel.write(writeBuf);
        assertEquals(2, written);
        assertEquals(4, channel.position());

        // 验证整个文件内容
        underlying.position(0);
        byte[] expected = new byte[]{1, 2, 99, 100, 5};
        ByteBuffer full = ByteBuffer.allocate(5);
        underlying.read(full);
        full.flip();
        assertArrayEquals(expected, full.array());
    }

    @Test
    void testWriteWithPositionMove() throws IOException {
        // 写入初始数据
        underlying.write(ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5}));
        // 将通道定位到末尾，然后追加写入
        channel.position(5);
        ByteBuffer writeBuf = ByteBuffer.wrap(new byte[]{6, 7});
        int written = channel.write(writeBuf);
        assertEquals(2, written);
        assertEquals(7, channel.position());

        // 验证文件内容
        underlying.position(0);
        ByteBuffer full = ByteBuffer.allocate(7);
        underlying.read(full);
        full.flip();
        byte[] expected = new byte[]{1, 2, 3, 4, 5, 6, 7};
        assertArrayEquals(expected, full.array());
    }

    @Test
    void testWriteInvalidatesBuffer() throws IOException {
        // 先读填充缓冲区
        underlying.write(ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}));
        underlying.position(0);
        ByteBuffer readBuf = ByteBuffer.allocate(5);
        channel.read(readBuf);
        assertEquals(5, channel.position());

        // 写入数据（会触发缓冲区失效）
        assertEquals(1, channel.write(ByteBuffer.wrap(new byte[]{99})));

        // 现在读取应该从位置6开始，而不是使用旧缓冲区内容
        readBuf.clear();
        int read = channel.read(readBuf);
        assertEquals(4, read); // 原始数据从6开始：6,7,8,9,10 但缓冲区被覆盖，读出的应是原始数据
        readBuf.flip();
        assertEquals(7, readBuf.get());
        assertEquals(8, readBuf.get());
        assertEquals(9, readBuf.get());
        assertEquals(10, readBuf.get());
    }

    // ---------- 截断测试 ----------
    @Test
    void testTruncate() throws IOException {
        underlying.write(ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}));
        channel.position(8);
        channel.truncate(5);
        assertEquals(5, channel.size());
        assertEquals(5, channel.position()); // 位置被调整到末尾
        // 验证底层内容
        underlying.position(0);
        ByteBuffer dst = ByteBuffer.allocate(5);
        underlying.read(dst);
        dst.flip();
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, dst.array());
    }

    @Test
    void testTruncateWhenPositionInsideNewSize() throws IOException {
        underlying.write(ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}));
        channel.position(3);
        channel.truncate(8);
        assertEquals(8, channel.size());
        assertEquals(3, channel.position()); // 位置未变
        // 验证底层内容
        underlying.position(0);
        ByteBuffer dst = ByteBuffer.allocate(8);
        underlying.read(dst);
        dst.flip();
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}, dst.array());
    }

    // ---------- 其他方法 ----------
    @Test
    void testSize() throws IOException {
        assertEquals(0, channel.size());
        underlying.write(ByteBuffer.wrap(new byte[]{1, 2, 3}));
        assertEquals(3, channel.size());
    }

    @Test
    void testIsOpen() {
        assertTrue(channel.isOpen());
    }

    @Test
    void testClose() throws IOException {
        channel.close();
        assertFalse(channel.isOpen());
        // 关闭后操作应抛出异常
        assertThrows(ClosedChannelException.class, () -> channel.read(ByteBuffer.allocate(1)));
        assertThrows(ClosedChannelException.class, () -> channel.write(ByteBuffer.allocate(1)));
        assertThrows(ClosedChannelException.class, () -> channel.position());
        assertThrows(ClosedChannelException.class, () -> channel.size());
        assertThrows(ClosedChannelException.class, () -> channel.truncate(0));
    }

    @Test
    void testDoubleCloseDoesNotThrow() throws IOException {
        channel.close();
        assertDoesNotThrow(() -> channel.close());
    }

    // ---------- 边界和缓冲区重用测试 ----------
    @Test
    void testBufferReuseOnSequentialRead() throws IOException {
        byte[] data = new byte[8192 * 2]; // 超过缓冲区两倍
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        underlying.write(ByteBuffer.wrap(data));
        underlying.position(0);

        // 第一次读，填充缓冲区
        ByteBuffer dst = ByteBuffer.allocate(5000);
        channel.read(dst);
        assertEquals(5000, channel.position());

        // 第二次读，应该复用缓冲区中的数据
        dst.clear();
        int read = channel.read(dst);
        assertEquals(5000, read); // 从5000读到10000
        // 验证数据连续性
        for (int i = 0; i < 5000; i++) {
            assertEquals((byte) (5000 + i), dst.get(i));
        }
        assertEquals(10000, channel.position());
    }

    @Test
    void testPositionAfterWriteThenRead() throws IOException {
        // 写入数据
        channel.write(ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5}));
        assertEquals(5, channel.position());
        // 定位到开头读
        channel.position(0);
        ByteBuffer dst = ByteBuffer.allocate(5);
        int read = channel.read(dst);
        assertEquals(5, read);
        dst.flip();
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, dst.array());
        assertEquals(5, channel.position());
    }
}