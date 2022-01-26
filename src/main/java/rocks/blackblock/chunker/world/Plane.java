package rocks.blackblock.chunker.world;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.dimension.DimensionType;
import rocks.blackblock.chunker.Chunker;
import rocks.blackblock.chunker.chunk.ChunkFetcher;
import rocks.blackblock.chunker.chunk.Lump;

import java.awt.*;

/**
 * A wrapper class for working with worlds
 *
 * @since   0.1.0
 */
public class Plane {

    private ServerWorld world;
    private DimensionType dimension;
    private ChunkFetcher.Session fetcher = null;

    /**
     * Creates a new Plane with the given ServerWorld
     *
     * @param   world   The world instance to use
     *
     * @since   0.1.0
     */
    public Plane(ServerWorld world) {
        this.world = world;
        this.dimension = world.getDimension();
    }

    /**
     * Lookup the world with the given name
     *
     * @param   name   The world name to use
     *
     * @since   0.1.0
     */
    public static Plane from(String name) {
        return Plane.from(new Identifier(name));
    }

    /**
     * Lookup the world with the given name
     *
     * @param   identifier   The world identifier to use
     *
     * @since   0.1.0
     */
    public static Plane from(Identifier identifier) {

        if (Chunker.SERVER == null) {
            return null;
        }

        for (ServerWorld world : Chunker.SERVER.getWorlds()) {
            if (world.getRegistryKey().getValue().equals(identifier)) {
                return Plane.from(world);
            }
        }

        return null;
    }

    /**
     * Create a new plane with the given ServerWorld instance
     *
     * @param   world   The world to use
     *
     * @since   0.1.0
     */
    public static Plane from(ServerWorld world) {
        return new Plane(world);
    }

    /**
     * Get this Plane's underlying world instance
     *
     * @since   0.1.0
     */
    public ServerWorld getWorld() {
        return this.world;
    }

    /**
     * Get this plane's dimension
     *
     * @since   0.1.0
     */
    public DimensionType getDimension() {
        return this.dimension;
    }

    /**
     * Does this plane have a ceiling?
     *
     * @since   0.1.0
     */
    public boolean hasCeiling() {
        return this.dimension.hasCeiling();
    }

    /**
     * Get a Lump chunk from this plane
     *
     * @param   x   The chunk's X position
     * @param   z   The chunk's Z position
     *
     * @since   0.1.0
     */
    public Lump getLump(int x, int z) {

        if (fetcher == null) {
            fetcher = new ChunkFetcher(Chunker.SERVER, world).new Session();
        }

        Chunk chunk = fetcher.getChunkView(x, z);

        if (chunk == null) {
            return null;
        }

        Lump result = new Lump(chunk, this);
        result.setCoordinates(x, z);

        return result;
    }

    /**
     * Get a Lump chunk from this plane at the given block coordinates
     *
     * @param   block_x   The block's X position
     * @param   block_z   The block's Z position
     *
     * @since   0.1.0
     */
    public Lump getLumpAtBlock(int block_x, int block_z) {

        int chunk_x = block_x >> 4;
        int chunk_z = block_z >> 4;

        return getLump(chunk_x, chunk_z);
    }

    /**
     * Get a Lump chunk from this plane at the given BlockPos
     *
     * @param   pos   The block's position
     *
     * @since   0.1.0
     */
    public Lump getLumpAtBlock(BlockPos pos) {
        return getLumpAtBlock(pos.getX(), pos.getZ());
    }

}
