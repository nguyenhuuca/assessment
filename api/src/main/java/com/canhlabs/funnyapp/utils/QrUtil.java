package com.canhlabs.funnyapp.utils;

import com.canhlabs.funnyapp.exception.CustomException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

public class QrUtil {

    public static String generateQRCodeBase64(String content, int width, int height) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height);

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", stream);
            byte[] qrBytes = stream.toByteArray();

            return Base64.getEncoder().encodeToString(qrBytes);
        } catch (Exception e) {
            throw  CustomException.builder()
                    .message("Cannot generate QA")
                    .build();
        }

    }
}
