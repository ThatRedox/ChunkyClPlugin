package chunkycl.renderer.scene;

import se.llbit.chunky.PersistentSettings;
import se.llbit.chunky.block.Block;
import se.llbit.chunky.renderer.scene.Scene;

public class ClBlock {
    public boolean invisible;
    public boolean hasColorTexture;
    public boolean hasNormalEmittanceTexture;
    public boolean hasSpecularMetalnessRoughnessTexture;

    public int blockTint;

    public int textureSize;
    public int colorTexture;
    public int normalEmittanceTexture;
    public int specularMetalnessRoughnessTexture;

    public ClBlock(Block block, ClTextureAtlas texMap) {
        this.invisible = block.invisible;
        this.hasColorTexture = !PersistentSettings.getSingleColorTextures();
        this.hasNormalEmittanceTexture = false;
        this.hasSpecularMetalnessRoughnessTexture = false;

        this.blockTint = 0;

        this.textureSize = (block.texture.getWidth() << 16) | block.texture.getHeight();
        this.colorTexture = this.hasColorTexture ? texMap.get(block.texture).location : block.texture.getAvgColor();
        this.normalEmittanceTexture = (int) (block.emittance * 255.0);
        this.specularMetalnessRoughnessTexture = (int) (block.specular * 255.0) |
                ((int) (block.metalness * 255.0) << 8) |
                ((int) (block.roughness * 255.0) << 16);
    }

    public int[] pack() {
        int[] packed = new int[6];
        packed[0] = ((this.invisible ? 0 : 1) << 31) |
                ((this.hasColorTexture ? 1 : 0) << 2) |
                ((this.hasNormalEmittanceTexture ? 1 : 0) << 1) |
                (this.hasSpecularMetalnessRoughnessTexture ? 1 : 0);
        packed[1] = this.textureSize;
        packed[2] = this.blockTint;
        packed[3] = this.colorTexture;
        packed[4] = this.normalEmittanceTexture;
        packed[5] = this.specularMetalnessRoughnessTexture;
        return packed;
    }

    public static void preLoad(Block block, ClTextureAtlas.AtlasBuilder builder) {
        if (!PersistentSettings.getSingleColorTextures()) {
            builder.addTexture(block.texture);
        }
    }
}
