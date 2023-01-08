package dev.thatredox.chunkynative.opencl.renderer;

import static org.jocl.CL.*;

import dev.thatredox.chunkynative.opencl.context.ClContext;
import dev.thatredox.chunkynative.opencl.context.Device;
import dev.thatredox.chunkynative.opencl.context.KernelLoader;
import org.jocl.*;

public class RendererInstance {
    public final int[] version;
    public final cl_device_id device;

    public final cl_program program;
    public final cl_context context;
    public final cl_command_queue commandQueue;

    private static RendererInstance instance = null;

    public static RendererInstance get() {
        if (instance == null) {
            instance = new RendererInstance();
        }
        return instance;
    }

    private RendererInstance() {
        Device device = Device.getPreferredDevice();
        this.version = device.version();
        this.device = device.device;

        ClContext context = new ClContext(device);
        this.context = context.context;
        this.commandQueue = context.queue;

        // Build the program
        program = KernelLoader.loadProgram(context, "kernel", "rayTracer.cl");
    }

    /** Get a string from OpenCL
     * Based on code from https://github.com/gpu/JOCLSamples/
     * List of available parameter names: https://www.khronos.org/registry/OpenCL/sdk/1.2/docs/man/xhtml/clGetDeviceInfo.html
     *
     * @param device Device to query
     * @param paramName Parameter to query
     */
    public static String getString(cl_device_id device, int paramName)
    {
        // Obtain the length of the string that will be queried
        long[] size = new long[1];
        clGetDeviceInfo(device, paramName, 0, null, size);

        // Create a buffer of the appropriate size and fill it with the info
        byte[] buffer = new byte[(int)size[0]];
        clGetDeviceInfo(device, paramName, buffer.length, Pointer.to(buffer), null);

        // Create a string from the buffer (excluding the trailing \0 byte)
        return new String(buffer, 0, buffer.length-1);
    }

    /** Get an integer(array) from OpenCL
     * Based on code from https://github.com/gpu/JOCLSamples/
     * List of available parameter names: https://www.khronos.org/registry/OpenCL/sdk/1.2/docs/man/xhtml/clGetDeviceInfo.html
     *
     * @param device Device to query
     * @param paramName Parameter to query
     * @param numValues Number of values to query
     */
    public static int[] getInts(cl_device_id device, int paramName, int numValues) {
        int[] values = new int[numValues];
        clGetDeviceInfo(device, paramName, (long) Sizeof.cl_int * numValues, Pointer.to(values), null);
        return values;
    }

    /** Get a long(array) from OpenCL
     * Based on code from https://github.com/gpu/JOCLSamples/
     * List of available parameter names: https://www.khronos.org/registry/OpenCL/sdk/1.2/docs/man/xhtml/clGetDeviceInfo.html
     *
     * @param device Device to query
     * @param paramName Parameter to query
     * @param numValues Number of values to query
     */
    public static long[] getLongs(cl_device_id device, int paramName, int numValues) {
        long[] values = new long[numValues];
        clGetDeviceInfo(device, paramName, (long) Sizeof.cl_long * numValues, Pointer.to(values), null);
        return values;
    }
}
