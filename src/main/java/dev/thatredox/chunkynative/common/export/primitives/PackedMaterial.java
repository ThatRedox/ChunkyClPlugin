package dev.thatredox.chunkynative.common.export.primitives;

import dev.thatredox.chunkynative.common.export.texture.AbstractTextureLoader;
import dev.thatredox.chunkynative.common.export.Packer;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import se.llbit.chunky.PersistentSettings;
import se.llbit.chunky.model.Tint;
import se.llbit.chunky.resources.Texture;
import se.llbit.chunky.world.Material;
import se.llbit.log.Log;
import se.llbit.math.ColorUtil;

public class PackedMaterial implements Packer {
    /**
     * The size of a packed material in 32 bit words.
     */
    public static final int MATERIAL_DWORD_SIZE = 6;

    public final boolean hasColorTexture;
    public final boolean hasNormalEmittanceTexture;
    public final boolean hasSpecularMetalnessRoughnessTexture;

    public final int blockTint;

    public final long colorTexture;
    public final int normalEmittanceTexture;
    public final int specularMetalnessRoughnessTexture;

    public PackedMaterial(Texture texture, Tint tint, Material material, AbstractTextureLoader texturePalette) {
        this(texture, tint, material.emittance, material.specular, material.metalness, material.roughness, texturePalette);
    }

    public PackedMaterial(Material material, Tint tint, AbstractTextureLoader texMap) {
        this(material.texture, tint, material.emittance, material.specular, material.metalness, material.roughness, texMap);
    }

    public PackedMaterial(Texture texture, Tint tint, float emittance, float specular, float metalness, float roughness, AbstractTextureLoader texMap) {
        this(texture, getTint(tint), emittance, specular, metalness, roughness, texMap);
    }

    private static int getTint(Tint tint) {
        if (tint != null) {
            switch (tint.type) {
                default:
                    Log.warn("Unsupported tint type " + tint.type);
                case NONE:
                    return 0;
                case CONSTANT:
                    return ColorUtil.getRGB(tint.tint) | 0xFF000000;
                case BIOME_FOLIAGE:
                    return 1 << 24;
                case BIOME_GRASS:
                    return 2 << 24;
                case BIOME_WATER:
                    return 3 << 24;
            }
        } else {
            return 0;
        }
    }

    public PackedMaterial(Texture texture, int blockTint, float emittance, float specular, float metalness, float roughness, AbstractTextureLoader texMap) {
        this.hasColorTexture = !PersistentSettings.getSingleColorTextures();
        this.hasNormalEmittanceTexture = false;
        this.hasSpecularMetalnessRoughnessTexture = false;
        this.blockTint = blockTint;
        this.colorTexture = this.hasColorTexture ? texMap.get(texture).get() : texture.getAvgColor();
        this.normalEmittanceTexture = (int) (emittance * 255.0);
        this.specularMetalnessRoughnessTexture = (int) (specular * 255.0) |
                ((int) (metalness * 255.0) << 8) |
                ((int) (roughness * 255.0) << 16);
    }

    /**
     * Materials are packed into 6 consecutive integers:
     * 0: Flags - 0b100 = has color texture
     *            0b010 = has normal emittance texture
     *            0b001 = has specular metalness roughness texture
     * 1: Block tint - the top 8 bits control which type of tint:
     *                 0xFF = lower 24 bits should be interpreted as RGB color
     *                 0x01 = foliage color
     *                 0x02 = grass color
     *                 0x03 = water color
     * 2 & 3: Color texture reference
     * 4: Top 24 bits represent the surface normal. First 8 bits represent the emittance.
     * 5: First 8 bits represent the specularness. Next 8 bits represent the metalness. Next 8 bits represent the roughness.
     */
    @Override
    public IntArrayList pack() {
        IntArrayList packed = new IntArrayList(6);
        packed.add(((this.hasColorTexture ? 1 : 0) << 2) |
                   ((this.hasNormalEmittanceTexture ? 1 : 0) << 1) |
                   (this.hasSpecularMetalnessRoughnessTexture ? 1 : 0));
        packed.add(this.blockTint);
        packed.add((int) (this.colorTexture >>> 32));
        packed.add((int) this.colorTexture);
        packed.add(this.normalEmittanceTexture);
        packed.add(this.specularMetalnessRoughnessTexture);
        return packed;
    }
}
