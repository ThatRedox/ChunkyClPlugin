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

import se.llbit.chunky.block.Block;
import se.llbit.chunky.chunk.BlockPalette;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.math.Octree;
import se.llbit.math.PackedOctree;
import se.llbit.math.Vector3;

public class OctreeIntersectCl {
    private cl_mem octreeDepth = null;
    private cl_mem octreeData = null;
    private cl_mem voxelLength = null;
    private cl_mem transparentArray = null;
    private cl_mem transparentLength = null;

    private cl_program program;
    private cl_kernel kernel;

    private cl_context context;
    private cl_command_queue commandQueue;

    private int[] version;

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

        // Get OpenCL version
        this.version = new int[2];
        String versionString = getString(device, CL_DRIVER_VERSION);
        this.version[0] = Integer.parseInt(versionString.substring(0, 1));
        this.version[1] = Integer.parseInt(versionString.substring(2, 3));

        if (this.version[0] >= 2) {
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
        int[] treeData;

        try {
            Field worldOctree = scene.getClass().getDeclaredField("worldOctree");
            worldOctree.setAccessible(true);
            octree = (Octree) worldOctree.get(scene);

            Field worldOctreeImplementation = octree.getClass().getDeclaredField("implementation");
            worldOctreeImplementation.setAccessible(true);
            PackedOctree packedWorldOctree = (PackedOctree) worldOctreeImplementation.get(octree);

            Field worldOctreeTreeData = packedWorldOctree.getClass().getDeclaredField("treeData");
            worldOctreeTreeData.setAccessible(true);
            treeData = (int[]) worldOctreeTreeData.get(packedWorldOctree);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return;
        }

        if (this.octreeData != null) {
            clReleaseMemObject(this.octreeData);
            clReleaseMemObject(this.octreeDepth);
            clReleaseMemObject(this.transparentArray);
            clReleaseMemObject(this.transparentLength);
        }

        // Load bounds into memory
        this.octreeDepth = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int, Pointer.to(new int[] {octree.getDepth()}), null);

        // Load octree into texture memory
        cl_image_format format = new cl_image_format();
        format.image_channel_data_type = CL_SIGNED_INT32;
        format.image_channel_order = CL_INTENSITY;

        if (this.version[0] >= 1 && this.version[1] >= 2) {
            cl_image_desc desc = new cl_image_desc();
            desc.image_type = CL_MEM_OBJECT_IMAGE1D;
            desc.image_width = treeData.length;

            this.octreeData = clCreateImage(context,
                    CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                    format, desc, Pointer.to(treeData), null);
        } else {
            this.octreeData = null;
            this.octreeData.notify();
        }

        this.voxelLength = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int, Pointer.to(new int[] {treeData.length}), null);

        // Create transparent block table
        List<Integer> transparentList = new LinkedList<>();
        List<Block> blockPalette;
        BlockPalette palette = scene.getPalette();

        try {
            Field blockPaletteList = palette.getClass().getDeclaredField("palette");
            blockPaletteList.setAccessible(true);
            blockPalette = (List<Block>) blockPaletteList.get(palette);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return;
        }

        for (int i = 0; i < blockPalette.size(); i++) {
            if (palette.get(i).invisible)
                transparentList.add(i);
        }

        int[] transparent = new int[transparentList.size()];
        for (int i = 0; i < transparent.length; i++) {
            transparent[i] = transparentList.remove(0);
        }

        this.transparentArray = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_int * transparent.length,
                Pointer.to(transparent), null);

        this.transparentLength = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, Sizeof.cl_int,
                Pointer.to(new int[] {transparent.length}), null);
    }

    public float[] intersect(float[] rayDirs, Vector3 origin) {
        float[] rayRes = new float[rayDirs.length/3];

        float[] rayPos = new float[3];
        rayPos[0] = (float) origin.x;
        rayPos[1] = (float) origin.y;
        rayPos[2] = (float) origin.z;

        Pointer srcRayRes = Pointer.to(rayRes);

        cl_mem clRayPos = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_float * rayPos.length, Pointer.to(rayPos), null);
        cl_mem clNormCoords = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_float * rayDirs.length, Pointer.to(rayDirs), null);
        cl_mem clRayRes = clCreateBuffer(context, CL_MEM_WRITE_ONLY,
                (long) Sizeof.cl_float * rayRes.length, null, null);

        // Set the arguments
        clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(clRayPos));
        clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(clNormCoords));
        clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(octreeDepth));
        clSetKernelArg(kernel, 3, Sizeof.cl_mem, Pointer.to(octreeData));
        clSetKernelArg(kernel, 4, Sizeof.cl_mem, Pointer.to(voxelLength));
        clSetKernelArg(kernel, 5, Sizeof.cl_mem, Pointer.to(transparentArray));
        clSetKernelArg(kernel, 6, Sizeof.cl_mem, Pointer.to(transparentLength));
        clSetKernelArg(kernel, 7, Sizeof.cl_mem, Pointer.to(clRayRes));

        long[] global_work_size = new long[]{rayRes.length};

        // Execute the program
        clEnqueueNDRangeKernel(commandQueue, kernel, 1, null, global_work_size,
                null, 0, null, null);

        // Get the results
        try {
            clEnqueueReadBuffer(commandQueue, clRayRes, CL_TRUE, 0, (long) Sizeof.cl_float * rayRes.length,
                    srcRayRes, 0, null, null);
        } catch (CLException e) {
            throw e;
        }

        // Clean up
        clReleaseMemObject(clRayPos);
        clReleaseMemObject(clNormCoords);
        clReleaseMemObject(clRayRes);

        return rayRes;
    }

    public void cleanup() {
        clReleaseMemObject(octreeData);
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

    public int octreeGet(int x, int y, int z, int bounds, int[] treeData) {
        int nodeIndex = 0;
        int level = bounds * 2;

        int data = treeData[nodeIndex];
        while (data > 0) {
            level -= 1;

            int lx = 1 & (x >> level);
            int ly = 1 & (y >> level);
            int lz = 1 & (z >> level);

            nodeIndex = data + ((lx << 2) | (ly << 1) | lz);
            data = treeData[nodeIndex];
        }

        return -data;
    }
}
