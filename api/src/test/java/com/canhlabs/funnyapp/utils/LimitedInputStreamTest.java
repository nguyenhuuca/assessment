package com.canhlabs.funnyapp.utils;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import static org.junit.jupiter.api.Assertions.*;

class LimitedInputStreamTest {
    @Test
    void testReadWithinLimit() throws IOException {
        byte[] data = {1, 2, 3, 4, 5};
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        LimitedInputStream lis = new LimitedInputStream(bais, 3, null);
        assertEquals(1, lis.read());
        assertEquals(2, lis.read());
        assertEquals(3, lis.read());
        assertEquals(-1, lis.read()); // limit reached
    }

    @Test
    void testReadArrayWithinLimit() throws IOException {
        byte[] data = {10, 20, 30, 40, 50};
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        LimitedInputStream lis = new LimitedInputStream(bais, 4, null);
        byte[] buffer = new byte[10];
        int read = lis.read(buffer, 0, buffer.length);
        assertEquals(4, read);
        assertArrayEquals(new byte[]{10, 20, 30, 40, 0, 0, 0, 0, 0, 0}, buffer);
        assertEquals(-1, lis.read(buffer, 0, buffer.length)); // limit reached
    }

    @Test
    void testSkipWithinLimit() throws IOException {
        byte[] data = {1, 2, 3, 4, 5};
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        LimitedInputStream lis = new LimitedInputStream(bais, 3, null);
        assertEquals(2, lis.skip(2));
        assertEquals(3, lis.read());
        assertEquals(-1, lis.read());
    }

    @Test
    void testAvailable() throws IOException {
        byte[] data = {1, 2, 3, 4, 5};
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        LimitedInputStream lis = new LimitedInputStream(bais, 2, null);
        assertTrue(lis.available() <= 2);
        lis.read();
        assertTrue(lis.available() <= 1);
    }
}

