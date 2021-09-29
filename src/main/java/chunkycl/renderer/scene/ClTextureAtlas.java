package chunkycl.renderer.scene;

import static org.jocl.CL.*;

import chunkycl.renderer.RendererInstance;
import org.jocl.*;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import se.llbit.chunky.resources.Texture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class ClTextureAtlas {
    public final cl_mem texture;
    public final HashMap<AtlasTexture, AtlasTexture> textureMap = new HashMap<>();

    public ClTextureAtlas(AtlasTexture[] textures) {
        for (AtlasTexture tex : textures) {
            textureMap.put(tex, tex);
        }

        Arrays.sort(textures);
        ArrayList<boolean[][]> layers = new ArrayList<>();
        layers.add(new boolean[256][256]);
        for (AtlasTexture tex : textures) {
            if (!insertTex(layers, tex)) {
                layers.add(new boolean[256][256]);
                insertTex(layers, tex);
            }
        }

        cl_image_format fmt = new cl_image_format();
        fmt.image_channel_order = CL_RGBA;
        fmt.image_channel_data_type = CL_UNORM_INT8;

        cl_image_desc desc = new cl_image_desc();
        desc.image_width = 8192;
        desc.image_height = 8192;
        desc.image_array_size = layers.size();
        desc.image_type = CL_MEM_OBJECT_IMAGE2D_ARRAY;

        RendererInstance instance = RendererInstance.get();
        texture = clCreateImage(instance.context, CL_MEM_READ_ONLY, fmt, desc, null, null);

        for (AtlasTexture tex : textures) {
            clEnqueueWriteImage(instance.commandQueue, texture, CL_TRUE,
                    new long[] {tex.getX()* 16L, tex.getY()* 16L, tex.getD()},
                    new long[] {tex.getWidth(), tex.getHeight(), 1},
                    0, 0, Pointer.to(tex.getTexture()),
                    0, null, null
            );
        }
    }

    public AtlasTexture get(Texture tex) {
        AtlasTexture texture = new AtlasTexture(tex);
        return textureMap.getOrDefault(texture, null);
    }

    public AtlasTexture get(AtlasTexture tex) {
        return textureMap.getOrDefault(tex, null);
    }

    private static boolean insertTex(ArrayList<boolean[][]> layers, AtlasTexture tex) {
        int l = 0;
        for (boolean[][] layer : layers) {
            for (int x = 0; x < 256; x++) {
                for (int y = 0; y < 256; y++) {
                    if (insertAt(x, y, tex.getWidth()/16, tex.getHeight()/16, layer)) {
                        tex.setLocation(x, y, l);
                        return true;
                    }
                }
            }
            l++;
        }
        return false;
    }

    private static boolean insertAt(int x, int y, int width, int height, boolean[][] layer) {
        if (y + height > layer.length || x + width > layer[0].length) {
            return false;
        }

        if (y < 0 || x < 0) {
            return false;
        }

        for (int line = y; line < y + height; line++) {
            for (int pixel = x; pixel < x + width; pixel++) {
                if (layer[line][pixel]) {
                    return false;
                }
            }
        }

        for (int line = y; line < y + height; line++) {
            for (int pixel = x; pixel < x + width; pixel++) {
                layer[line][pixel] = true;
            }
        }

        return true;
    }

    public static class AtlasBuilder {
        HashMap<AtlasTexture, AtlasTexture> textureMap = new HashMap<>();

        public AtlasTexture addTexture(Texture tex) {
            AtlasTexture texture = new AtlasTexture(tex);
            return textureMap.computeIfAbsent(texture, t -> t);
        }

        public ClTextureAtlas build() {
            return new ClTextureAtlas(textureMap.values().toArray(new AtlasTexture[0]));
        }

        public void release() {
            textureMap.clear();
            textureMap = null;
        }
    }

    public static class AtlasTexture implements Comparable<AtlasTexture> {
        public final Texture texture;
        public final int size;
        public int location = 0xFFFFFFFF;

        protected AtlasTexture(Texture tex) {
            this.texture = tex;
            this.size = (tex.getWidth() << 16) | tex.getHeight();
        }

        protected void setLocation(int x, int y, int d) {
            this.location = (x << 22) | (y << 13) | d;
        }

        protected void setLocation(int index) {
            this.location = index;
        }

        public int getWidth() {
            return (size >>> 16) & 0xFFFF;
        }

        public int getHeight() {
            return size & 0xFFFF;
        }

        public int getX() {
            return (location >>> 22) & 0x1FF;
        }

        public int getY() {
            return (location >>> 13) & 0x1FF;
        }

        public int getD() {
            return location & 0x1FFF;
        }

        public byte[] getTexture() {
            byte[] out = new byte[getHeight() * getWidth() * 4];
            int index = 0;
            for (int y = 0; y < getHeight(); y++) {
                for (int x = 0; x < getWidth(); x++) {
                    float[] rgba = texture.getColor(x, y);
                    out[index] = (byte) (rgba[0] * 255.0);
                    out[index+1] = (byte) (rgba[1] * 255.0);
                    out[index+2] = (byte) (rgba[2] * 255.0);
                    out[index+3] = (byte) (rgba[3] * 255.0);
                    index += 4;
                }
            }
            return out;
        }

        @Override
        public int compareTo(AtlasTexture o) {
            return o.size - this.size;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(texture.getData());
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) return false;
            if (!(o instanceof AtlasTexture)) return false;
            AtlasTexture other = (AtlasTexture) o;
            return this.size == other.size &&
                    Arrays.equals(this.texture.getData(), other.texture.getData());
        }
    }

    public void release() {
        clReleaseMemObject(texture);
    }
}
