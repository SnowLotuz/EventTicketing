package com.capstone.eventticketing.util;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

/**
 * Generates QR bitmaps from ticket payloads ({@code bookingId|ticketId}).
 * Uses high error correction so the code still scans on a slightly dimmed or
 * smudged screen at the gate.
 */
public final class QrGenerator {

    private QrGenerator() { /* no instances */ }

    /**
     * Encodes {@code content} as a square QR bitmap.
     *
     * @param content the payload to encode (e.g. "bookingId|ticketId").
     * @param sizePx  desired width/height in pixels.
     * @return the QR bitmap, or null if encoding failed.
     */
    @Nullable
    public static Bitmap generate(@NonNull String content, int sizePx) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.MARGIN, 1);

        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
            int width = matrix.getWidth();
            int height = matrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            int dark = 0xFF0F172A;  // slate_900 — on-brand, scanner reads it as "black"
            int light = 0xFFFFFFFF; // white
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? dark : light);
                }
            }
            return bitmap;
        } catch (WriterException e) {
            return null;
        }
    }
}