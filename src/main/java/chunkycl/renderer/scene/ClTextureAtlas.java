package chunkycl.renderer.scene;

import chunkycl.renderer.RendererInstance;
import static org.jocl.CL.*;
import org.jocl.*;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import se.llbit.chunky.resources.Texture;

import java.util.Arrays;

public class ClTextureAtlas {
    public final cl_mem texture;

    public ClTextureAtlas(AtlasTexture[] textures) {
        IntArrayList texture = new IntArrayList();

        Arrays.sort(textures);
        for (AtlasTexture tex : textures) {
            int position = texture.size();
            tex.setLocation(position);
            texture.addAll(IntList.of(tex.texure.getData()));
        }

        this.texture = ImageArrays.createUintArray(texture.toIntArray());
    }

    public static class AtlasBuilder {
        ObjectArraySet<AtlasTexture> textures = new ObjectArraySet<>();

        public AtlasTexture addTexture(Texture tex) {
            AtlasTexture texture = new AtlasTexture(tex);
            textures.add(texture);
            return texture;
        }

        public ClTextureAtlas build() {
            return new ClTextureAtlas(textures.toArray(new AtlasTexture[0]));
        }
    }

    public static class AtlasTexture implements Comparable<AtlasTexture> {
        public final Texture texure;
        public final int size;
        public int location = 0xFFFFFFFF;

        protected AtlasTexture(Texture tex) {
            this.texure = tex;
            this.size = (tex.getWidth() << 16) | tex.getHeight();
        }

        protected void setLocation(int x, int y, int d) {
            this.location = (x << 22) | (y << 13) | d;
        }

        protected void setLocation(int index) {
            this.location = index;
        }

        @Override
        public int compareTo(AtlasTexture o) {
            return o.size - this.size;
        }
    }

    public void release() {
        clReleaseMemObject(texture);
    }
}
