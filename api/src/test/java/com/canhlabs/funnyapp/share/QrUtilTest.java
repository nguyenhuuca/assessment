package com.canhlabs.funnyapp.share;

import com.canhlabs.funnyapp.share.exception.CustomException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QrUtilTest {

    @Test
    void generateQRCodeBase64_shouldReturnBase64_whenValidInput() {
        String content = "Hello, QR!";
        String base64 = QrUtil.generateQRCodeBase64(content, 200, 200);
        assertNotNull(base64);
        assertFalse(base64.isEmpty());
    }

    @Test
    void generateQRCodeBase64_shouldThrow_whenInvalidSize() {
        assertThrows(CustomException.class, () ->
                QrUtil.generateQRCodeBase64("test", -1, -1));
    }

    @Test
    void generateQRCodeBase64_shouldThrow_whenNullContent() {
        assertThrows(CustomException.class, () ->
                QrUtil.generateQRCodeBase64(null, 100, 100));
    }
}