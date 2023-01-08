package dev.thatredox.chunkynative.opencl.context;

import org.jocl.cl_program;
import se.llbit.log.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.function.BiFunction;

public class KernelLoader {
    private static final KernelLoader instance = new KernelLoader();

    private final BiFunction<String, String, String> rawSourceReader;
    private final boolean hotReload;

    private KernelLoader() {
        String hotReload = System.getProperty("chunkyClHotReload");
        this.hotReload = hotReload != null;
        if (this.hotReload) {
            Path reloadPath = Paths.get(hotReload);
            rawSourceReader = (base, file) -> {
                Path filePath = reloadPath.resolve(base).resolve("include").resolve(file);
                try {
                    return new String(Files.readAllBytes(filePath));
                } catch (IOException e) {
                    Log.error(String.format("Error loading ChunkyCL, error reading file \"%s\"", filePath), e);
                    throw new IllegalStateException("Failed to load file:", e);
                }
            };
        } else {
            rawSourceReader = (base, file) -> {
                String f = base + "/" + file;
                InputStream fileStream = KernelLoader.class.getClassLoader().getResourceAsStream(f);
                if (fileStream == null) {
                    Log.errorf("Error loading ChunkyCL, file \"%s\" does not exist.", f);
                    throw new IllegalStateException(String.format("File \"%s\" does not exist.", f));
                }
                Scanner s = new Scanner(fileStream).useDelimiter("\\A");
                return s.hasNext() ? s.next() : "";
            };
        }
    }

    /**
     * Load an OpenCL program.
     *
     * @param context       OpenCL context.
     * @param base          Kernel base directory name.
     * @param kernelName    Kernel entrypoint filename.
     * @return OpenCL program.
     */
    public static cl_program loadProgram(ClContext context, String base, String kernelName) {
        return context.loadProgram(file -> instance.rawSourceReader.apply(base, file), kernelName);
    }

    public static boolean canHotReload() {
        return instance.hotReload;
    }
}
