package rocks.blackblock.chunker.chunk;

import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import rocks.blackblock.chunker.TileGenerator;
import rocks.blackblock.chunker.world.Plane;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A wrapper class for working with chunks
 *
 * @since   0.1.0
 */
public class Lump {

    private final Chunk chunk;
    private final Plane plane;

    private int x;
    private int z;

    /**
     * Creates a new Lump with the given chunk
     *
     * @param   chunk   The chunk instance to use
     */
    public Lump(Chunk chunk) {
        this(chunk, null);
    }

    /**
     * Creates a new Lump with the given chunk & plane
     *
     * @param   chunk   The chunk instance to use
     */
    public Lump(Chunk chunk, Plane plane) {
        this.chunk = chunk;
        this.plane = plane;
    }

    /**
     * Set the chunk's X & Z coordinates
     *
     * @since   0.1.0
     */
    public void setCoordinates(int x, int z) {
        this.x = x;
        this.z = z;
    }

    /**
     * Get the underlying chunk instance.
     * This can be an unloaded chunk, though!
     *
     * @since   0.1.0
     */
    public Chunk getChunk() {
        return this.chunk;
    }

    /**
     * Get the plane this lump is on
     *
     * @since   0.1.0
     */
    public Plane getPlane() {
        return this.plane;
    }

    /**
     * Force load this chunk
     *
     * @param   enable   Whether to enable or disable
     *
     * @since   0.1.0
     */
    public void forceLoad(boolean enable) {
        ServerWorld world = this.plane.getWorld();
        world.setChunkForced(this.x, this.z, enable);
    }

    /**
     * Is this chunk force-loaded?
     *
     * @since   0.1.0
     */
    public boolean isForceLoaded() {
        ServerWorld world = this.plane.getWorld();
        long pos_long = this.getPos().toLong();
        return world.getForcedChunks().contains(pos_long);
    }

    /**
     * Get the ChunkPos of this lump
     *
     * @since   0.1.0
     */
    public ChunkPos getPos() {
        return new ChunkPos(this.x, this.z);
    }

    /**
     * Convert local (0-16) coordinates to global ones
     *
     * @param   x_or_z   The local X or Z coordinate
     *
     * @since   0.1.0
     */
    public int convertLocalCoordinateToGlobal(int x_or_z) {
        return x_or_z + (this.x * 16);
    }

    /**
     * Get a neighbouring lump
     *
     * @param  x   The X-shift of the wanted neighbour
     * @param  z   The Z-shift of the wanted neighbour
     *
     * @since   0.1.0
     */
    public Lump getNeighbour(int x, int z) {

        // Return null if no plane was set
        // Return null if both coordinates are 0
        if (this.plane == null || (x == 0 && z == 0)) {
            return null;
        }

        int wanted_x = this.x + x;
        int wanted_z = this.z + z;

        return this.plane.getLump(wanted_x, wanted_z);
    }

    /**
     * Get the BufferedImage of the chunk
     *
     * @since   0.1.0
     */
    public BufferedImage getImage() {

        int[] colors = this.getColors();
        DataBufferInt buf = new DataBufferInt(colors, colors.length);

        int[] masks = new int[]{0xff, 0xff00, 0xff0000, 0xff000000};

        BufferedImage image = new BufferedImage(new DirectColorModel(32, masks[0], masks[1], masks[2], masks[3]),
                Raster.createPackedRaster(buf, 16, 16, 16, masks, null), false, null);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            ImageIO.write(image, "png", baos);
            Files.write(Path.of("/tmp/lump.png"), baos.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
        }


        return image;
    }

    /**
     * Get the colors of this chunk
     *
     * @since   0.1.0
     */
    public int[] getColors() {

        // The resulting colors array
        int[] colors = new int[16 * 16];

        Heightmap heightmap = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE);
        boolean has_ceiling = this.plane.hasCeiling();

        // Get the chunk north of this chunk for shading
        Lump north = this.getNeighbour(0, -1);
        Heightmap north_heightmap = north != null ? north.getChunk().getHeightmap(Heightmap.Type.WORLD_SURFACE) : null;

        // The last height values
        int[] last_heights = new int[16];

        BlockSearcher searcher = new BlockSearcher(this.getPlane());

        // Iterate over all the X blocks
        for (int x = 0; x < 16; x++) {

            if (north != null) {
                if (has_ceiling) {
                    searcher.searchForBlockUnderCeiling(north, x, 15);
                } else {
                    searcher.searchForBlock(north, x, 15);
                }

                last_heights[x] = searcher.getHeight();
            }

            // And iterate over all the Z blocks
            for (int z = 0; z < 16; z++) {

                if (has_ceiling) {
                    searcher.searchForBlockUnderCeiling(this, x, z);
                } else {
                    searcher.searchForBlock(this, x, z);
                }

                int height = searcher.getHeight();

                if (height > -64 && searcher.isVisibleFluid()) {
                    searcher.calculateWaterDepth(this);
                }

                MapColor map_color = searcher.getCurrentMapColor();

                int shade;

                if (map_color == MapColor.WATER_BLUE) {
                    int water_depth = searcher.getWaterDepth();
                    double shade_test = (double) water_depth * 0.1D + (double) (x + z & 1) * 0.2D;
                    shade = 1;

                    if (shade_test < 0.5D) {
                        shade = 2;
                    }

                    if (shade_test > 0.9D) {
                        shade = 0;
                    }
                } else {
                    double shade_test = (searcher.getHeight() - last_heights[x]) * 4.0D / 5.0D + ((double) (x + z & 1) - 0.5D) * 0.4D;
                    shade = 1;

                    if (shade_test > 0.6D) {
                        shade = 2;
                    }

                    if (shade_test < -0.6D) {
                        shade = 0;
                    }
                }

                last_heights[x] = searcher.getHeight();
                colors[x + (z * 16)] = TileGenerator.getRenderColor(map_color, shade);
            }
        }

        return colors;
    }

}
