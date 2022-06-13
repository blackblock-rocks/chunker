package rocks.blackblock.chunker.chunk;

import net.minecraft.block.MapColor;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rocks.blackblock.chunker.Chunker;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A wrapper class for working with chunks
 *
 * @since   0.1.0
 */
public class Lump {

    @NotNull
    private final Chunk chunk;

    @Nullable
    private final Plane plane;

    private Integer x;
    private Integer z;

    /**
     * Creates a new Lump with the given chunk
     *
     * @param   chunk   The chunk instance to use
     */
    public Lump(@NotNull Chunk chunk) {
        this(chunk, null);
    }

    /**
     * Creates a new Lump with the given chunk & plane
     *
     * @param   chunk   The chunk instance to use
     */
    public Lump(@NotNull Chunk chunk, @Nullable Plane plane) {
        this.chunk = chunk;
        this.plane = plane;

        ChunkPos pos = chunk.getPos();
        this.x = pos.x;
        this.z = pos.z;
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
    public @NotNull Chunk getChunk() {
        return this.chunk;
    }

    /**
     * Get the plane this lump is on
     *
     * @since   0.1.0
     */
    public @Nullable Plane getPlane() {
        return this.plane;
    }

    /**
     * Force load this chunk
     *
     * @param   enable   Whether to enable or disable
     *
     * @since   0.1.0
     */
    public boolean forceLoad(boolean enable) {

        if (this.plane == null) {
            return false;
        }

        ServerWorld world = this.plane.getWorld();
        world.setChunkForced(this.x, this.z, enable);

        return true;
    }

    /**
     * Is this chunk force-loaded?
     *
     * @since   0.1.0
     */
    public boolean isForceLoaded() {

        if (this.plane == null) {
            return false;
        }

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
     * Preload a neighbour
     *
     * @param x The X-shift of the wanted neighbour
     * @param z The Z-shift of the wanted neighbour
     * @author Jelle De Loecker   <jelle@elevenways.be>
     * @since 0.2.0
     */
    public @NotNull CompletableFuture<Optional<Lump>> preloadNeighbour(int x, int z) {

        // Return null if no plane was set
        // Return null if both coordinates are 0
        if (this.plane == null || (x == 0 && z == 0)) {
            return null;
        }

        int wanted_x = this.x + x;
        int wanted_z = this.z + z;

        System.out.println("Preloading neighbour " + wanted_x + " " + wanted_z);

        return this.plane.preloadLump(wanted_x, wanted_z);
    }

    /**
     * Get a neighbouring lump.
     * This would have to be loaded or pre-fetched.
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
     * Get the colors of this asynchronously
     *
     * @author   Jelle De Loecker   <jelle@elevenways.be>
     * @since    0.2.0
     */
    public CompletableFuture<int[]> getColorsAsync() {
        return this.preloadNeighbour(0, -1).thenApplyAsync(lump -> this.getColors());
    }

    /**
     * Get the colors of this chunk in ABGR format
     *
     * @author   Jelle De Loecker   <jelle@elevenways.be>
     * @since    0.1.0
     */
    public int[] getColors() {

        if (this.plane == null) {
            return null;
        }

        // The resulting colors array
        int[] colors = new int[16 * 16];

        boolean has_ceiling = this.plane.hasCeiling();

        // Get the chunk north of this chunk for shading
        Lump north = this.getNeighbour(0, -1);

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

    /**
     * Convert pixels from java default ABGR int format to byte array in ARGB
     * format.
     *
     * @param pixels the pixels to convert
     */
    public static void convertABGRtoARGB(int[] pixels) {
        int p, r, g, b, a;
        for (int i = 0; i < pixels.length; i++) {
            p = pixels[i];
            a = (p >> 24) & 0xFF; // get pixel bytes in ARGB order
            b = (p >> 16) & 0xFF;
            g = (p >> 8) & 0xFF;
            r = (p >> 0) & 0xFF;
            pixels[i] = (a << 24) | (r << 16) | (g << 8) | (b << 0);
        }
    }

}
