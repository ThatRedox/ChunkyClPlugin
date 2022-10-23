package dev.thatredox.chunkynative.rust;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class NativeLoader {
    private NativeLoader() {}

    public static void load() throws UnsatisfiedLinkError {
        String systemName = System.getProperty("os.name");
        String nativeLib;
        if (systemName.contains("Windows")) {
            nativeLib = "native.dll";
        } else if (systemName.contains("Linux") || systemName.contains("LINUX")) {
            nativeLib = "libnative.so";
        } else {
            throw new UnsatisfiedLinkError("Failed to identify operating system type.");
        }

        File tempFile;
        try {
            tempFile = File.createTempFile("chunky_native_lib_", ".bin");
            tempFile.deleteOnExit();
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("Failed to create temporary file.");
        }

        try (InputStream is = NativeLoader.class.getResourceAsStream(nativeLib)) {
            if (is == null) {
                throw new UnsatisfiedLinkError("Failed to find native library.");
            }
            Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("Failed to load native library.");
        }

        System.out.println("Native library at: " + tempFile.getAbsolutePath());
        System.load(tempFile.getAbsolutePath());
    }
}
