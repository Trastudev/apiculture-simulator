package com.apiculture.simulator.data.repository;

import androidx.annotation.NonNull;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/** Extrae el tar.gz del grafo GraphHopper (nombres cortos, ustar). */
public final class TarGzipExtractor {

    private TarGzipExtractor() {
    }

    public static void extract(@NonNull File archive, @NonNull File destDir) throws IOException {
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("No se pudo crear " + destDir);
        }
        byte[] header = new byte[512];
        try (InputStream raw = new BufferedInputStream(new GZIPInputStream(new FileInputStream(archive)))) {
            while (true) {
                int n = readFully(raw, header, 0, 512);
                if (n < 512 || isZeroBlock(header)) {
                    break;
                }
                String name = cString(header, 0, 100);
                if (name.isEmpty()) {
                    break;
                }
                long size = octal(header, 124, 12);
                char type = (char) header[156];
                File out = safeFile(destDir, name);
                if (type == '5' || name.endsWith("/")) {
                    if (!out.exists() && !out.mkdirs()) {
                        throw new IOException("No se pudo crear carpeta " + out);
                    }
                    skip(raw, pad(size));
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("No se pudo crear " + parent);
                }
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    long left = size;
                    byte[] buf = new byte[8192];
                    while (left > 0) {
                        int want = (int) Math.min(buf.length, left);
                        int r = raw.read(buf, 0, want);
                        if (r < 0) {
                            throw new IOException("tar truncado: " + name);
                        }
                        fos.write(buf, 0, r);
                        left -= r;
                    }
                }
                skip(raw, pad(size) - size);
            }
        }
    }

    @NonNull
    private static File safeFile(@NonNull File destDir, @NonNull String name) throws IOException {
        File out = new File(destDir, name);
        String destPath = destDir.getCanonicalPath();
        String outPath = out.getCanonicalPath();
        if (!outPath.startsWith(destPath + File.separator) && !outPath.equals(destPath)) {
            throw new IOException("ruta tar no válida: " + name);
        }
        return out;
    }

    private static boolean isZeroBlock(byte[] header) {
        for (byte b : header) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static String cString(byte[] header, int off, int len) {
        int end = off;
        int max = off + len;
        while (end < max && header[end] != 0) {
            end++;
        }
        return new String(header, off, end - off, StandardCharsets.US_ASCII).trim();
    }

    private static long octal(byte[] header, int off, int len) {
        String s = cString(header, off, len).replaceAll("[^0-7]", "");
        if (s.isEmpty()) {
            return 0;
        }
        return Long.parseLong(s, 8);
    }

    private static long pad(long size) {
        long rem = size % 512;
        return rem == 0 ? size : size + (512 - rem);
    }

    private static void skip(InputStream in, long n) throws IOException {
        long left = n;
        while (left > 0) {
            long skipped = in.skip(left);
            if (skipped <= 0) {
                if (in.read() < 0) {
                    return;
                }
                left--;
            } else {
                left -= skipped;
            }
        }
    }

    private static int readFully(InputStream in, byte[] buf, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int r = in.read(buf, off + total, len - total);
            if (r < 0) {
                return total;
            }
            total += r;
        }
        return total;
    }
}
