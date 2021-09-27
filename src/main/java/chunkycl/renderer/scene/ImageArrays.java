package chunkycl.renderer.scene;

import chunkycl.renderer.RendererInstance;
import static org.jocl.CL.*;
import org.jocl.*;

public class ImageArrays {
    private static cl_image_format fmt = null;
    private static cl_image_desc desc = null;

    public static cl_mem createIntArray(int[] buffer) {
        return createTypeArray(Pointer.to(buffer), buffer.length, CL_SIGNED_INT32);
    }

    public static cl_mem createUintArray(int[] buffer) {
        return createTypeArray(Pointer.to(buffer), buffer.length, CL_UNSIGNED_INT32);
    }

    public static cl_mem createTypeArray(Pointer buffer, int bufferLength, int type) {
        RendererInstance instance = RendererInstance.get();

        if (fmt == null) {
            fmt = new cl_image_format();
            fmt.image_channel_order = CL_RGBA;
        }
        fmt.image_channel_data_type = type;

        if (desc == null) {
            desc = new cl_image_desc();
            desc.image_type = CL_MEM_OBJECT_IMAGE2D;
            desc.image_width = 8192;
        }
        desc.image_height = bufferLength / 8192 / 4 + 1;

        return clCreateImage(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                fmt, desc, buffer, null);
    }
}
