package chunkycl;

import static org.jocl.CL.*;

import org.jocl.*;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

import se.llbit.chunky.block.Air;
import se.llbit.chunky.chunk.BlockPalette;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.math.Octree;
import se.llbit.math.Ray;

public class OctreeIntersectCl {
    private cl_mem voxelArray = null;
    private cl_mem voxelNum = null;
    private cl_mem voxelBounds = null;

    private cl_program program;
    private cl_kernel kernel;

    private cl_context context;
    private cl_command_queue commandQueue;

    public final long workgroupSize;

    private static String programSource;

    OctreeIntersectCl() {
        // The platform, device type and device number
        // that will be used
        final int platformIndex = 0;
        final long deviceType = CL_DEVICE_TYPE_ALL;
        final int deviceIndex = 0;

        // Load program source
        InputStream i = OctreeIntersectCl.class.getClassLoader().getResourceAsStream("octreeIntersect.cl");
        assert i != null;
        Scanner s = new Scanner(i).useDelimiter("\\A");
        programSource = s.hasNext() ? s.next() : "";

        // Enable exceptions and subsequently omit error checks in this sample
        CL.setExceptionsEnabled(true);

        // Obtain the number of platforms
        int[] numPlatformsArray = new int[1];
        clGetPlatformIDs(0, null, numPlatformsArray);
        int numPlatforms = numPlatformsArray[0];

        // Obtain a platform ID
        cl_platform_id[] platforms = new cl_platform_id[numPlatforms];
        clGetPlatformIDs(platforms.length, platforms, null);
        cl_platform_id platform = platforms[platformIndex];

        // Initialize the context properties
        cl_context_properties contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);

        // Obtain the number of devices for the platform
        int[] numDevicesArray = new int[1];
        clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
        int numDevices = numDevicesArray[0];

        // Obtain a device ID
        cl_device_id[] devices = new cl_device_id[numDevices];
        clGetDeviceIDs(platform, deviceType, numDevices, devices, null);
        cl_device_id device = devices[deviceIndex];

        System.out.println("OpenCL Device: " + getString(device, CL_DEVICE_NAME));

        workgroupSize = getSizes(device, CL_DEVICE_MAX_WORK_GROUP_SIZE, 1)[0];

        // Create a context for the selected device
        context = clCreateContext(
                contextProperties, 1, new cl_device_id[]{device},
                null, null, null);

        // Create a command-queue for the selected device
        cl_queue_properties properties = new cl_queue_properties();

        // Check if opencl is 2.0 or greater
        String versionString = getString(device, CL_DRIVER_VERSION);
        if (Integer.parseInt(versionString.substring(0, 1)) >= 2) {
            commandQueue = clCreateCommandQueueWithProperties(
                    context, device, properties, null);
        } else {
            commandQueue = clCreateCommandQueue(
                    context, device, 0, null);
        }

        // Create the program
        program = clCreateProgramWithSource(context, 1, new String[] {programSource},
                null, null);

        // Build the program
        try {
            clBuildProgram(program, 0, null, null, null, null);
        } catch (CLException e) {
            if (e.getStatus() == CL_BUILD_PROGRAM_FAILURE) {
                // Obtain the length of the string that will be queried
                long[] size = new long[1];
                clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, 0, null, size);

                // Create a buffer of the appropriate size and fill it with the info
                byte[] buffer = new byte[(int)size[0]];
                clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, buffer.length, Pointer.to(buffer), null);

                // Create a string from the buffer (excluding the trailing \0 byte)
                System.err.println(new String(buffer, 0, buffer.length-1));
            }

            throw e;
        }

        // Create the kernel
        kernel = clCreateKernel(program, "octreeIntersect", null);
    }

    public void load(Scene scene) {
        Octree octree;

        try {
            Field worldOctree = scene.getClass().getDeclaredField("worldOctree");
            worldOctree.setAccessible(true);
            octree = (Octree) worldOctree.get(scene);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return;
        }

        if (this.voxelArray != null) {
            clReleaseMemObject(this.voxelArray);
            clReleaseMemObject(this.voxelBounds);
        }

        int size = (int) Math.pow(2, octree.getDepth());
        List<Integer> voxelList = new LinkedList<>();

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                for (int k = 0; k < size; k++) {
                    if (isAir(scene, octree, i, j, k) == 0) {
                        voxelList.add(i);
                        voxelList.add(j);
                        voxelList.add(k);
                    }
                }
            }
        }
        int[] voxelArray = new int[voxelList.size()];
        int i = 0;
        while (voxelList.size() > 0) {
            voxelArray[i++] = voxelList.remove(0);
        }

        Pointer srcVoxel = Pointer.to(voxelArray);
        this.voxelArray = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int * voxelArray.length, srcVoxel, null);

        this.voxelNum = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_int, Pointer.to(new int[] {voxelArray.length}), null);

        this.voxelBounds = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int, Pointer.to(new int[]{size/2}), null);
    }

    public void intersect(List<RayCl> rays) {
        float[] rayPos = new float[rays.size()*3];
        float[] rayDir = new float[rays.size()*3];
        float[] rayRes = new float[rays.size()];

        for (int i = 0; i < rays.size(); i++) {
            RayCl wrapper = rays.get(i);

            rayPos[i*3 + 0] = (float) wrapper.getRay().o.x;
            rayPos[i*3 + 1] = (float) wrapper.getRay().o.y;
            rayPos[i*3 + 2] = (float) wrapper.getRay().o.z;

            rayDir[i*3 + 0] = (float) wrapper.getRay().d.x;
            rayDir[i*3 + 1] = (float) wrapper.getRay().d.y;
            rayDir[i*3 + 2] = (float) wrapper.getRay().d.z;
        }

        Pointer srcRayPos = Pointer.to(rayPos);
        Pointer srcRayDir = Pointer.to(rayDir);
        Pointer srcRayRes = Pointer.to(rayRes);

        cl_mem clRayPos = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_float * rayPos.length, srcRayPos, null);
        cl_mem clRayDir = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_float * rayPos.length, srcRayDir, null);
        cl_mem clRayRes = clCreateBuffer(context,
                CL_MEM_WRITE_ONLY,
                (long) Sizeof.cl_float * rayRes.length, null, null);

        // Set the arguments
        clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(clRayPos));
        clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(clRayDir));
        clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(voxelArray));
        clSetKernelArg(kernel, 3, Sizeof.cl_mem, Pointer.to(voxelNum));
        clSetKernelArg(kernel, 4, Sizeof.cl_mem, Pointer.to(voxelBounds));
        clSetKernelArg(kernel, 5, Sizeof.cl_mem, Pointer.to(clRayRes));

        long[] global_work_size = new long[]{rayRes.length};

        // Execute the program
        clEnqueueNDRangeKernel(commandQueue, kernel, 1, null, global_work_size,
                null, 0, null, null);

        // Get the results
        try {
            clEnqueueReadBuffer(commandQueue, clRayRes, CL_TRUE, 0, Sizeof.cl_float * rayRes.length,
                    srcRayRes, 0, null, null);
        } catch (CLException e) {
            throw e;
        }

        // Clean up
        clReleaseMemObject(clRayPos);
        clReleaseMemObject(clRayDir);
        clReleaseMemObject(clRayRes);

        // Set all the intersect results
        for (int i = 0; i < rayRes.length; i++) {
            RayCl ray = rays.get(i);
            ray.getRay().o.scaleAdd(rayRes[i]*0.99, ray.getRay().d);
            ray.getRay().distance = rayRes[i]*0.99;
        }
    }

    public void cleanup() {
        clReleaseMemObject(voxelArray);
        clReleaseKernel(kernel);
        clReleaseProgram(program);
        clReleaseCommandQueue(commandQueue);
        clReleaseContext(context);
    }

    private int isAir(Scene scene, Octree octree, int x, int y, int z) {
        BlockPalette palette = scene.getPalette();
        Octree.Node node = octree.get(x, y, z);

        return palette.get(node.type).invisible ? 1 : 0;
    }

    private static String getString(cl_device_id device, int paramName)
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

    static long[] getSizes(cl_device_id device, int paramName, int numValues)
    {
        // The size of the returned data has to depend on
        // the size of a size_t, which is handled here
        ByteBuffer buffer = ByteBuffer.allocate(
                numValues * Sizeof.size_t).order(ByteOrder.nativeOrder());
        clGetDeviceInfo(device, paramName, Sizeof.size_t * numValues,
                Pointer.to(buffer), null);
        long values[] = new long[numValues];
        if (Sizeof.size_t == 4)
        {
            for (int i=0; i<numValues; i++)
            {
                values[i] = buffer.getInt(i * Sizeof.size_t);
            }
        }
        else
        {
            for (int i=0; i<numValues; i++)
            {
                values[i] = buffer.getLong(i * Sizeof.size_t);
            }
        }
        return values;
    }
}
