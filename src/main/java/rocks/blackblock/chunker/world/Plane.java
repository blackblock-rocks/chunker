package rocks.blackblock.chunker.world;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.dimension.DimensionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rocks.blackblock.bib.collection.LRUCache;
import rocks.blackblock.chunker.Chunker;
import rocks.blackblock.chunker.chunk.ChunkFetcher;
import rocks.blackblock.chunker.chunk.Lump;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A wrapper class for working with worlds
 *
 * @since   0.1.0
 */
public class Plane {

    private ServerWorld world;
    private DimensionType dimension;
    private ChunkFetcher.Session fetcher = null;
    private LRUCache<ChunkPos, Lump> preload_cache = new LRUCache<>(128);

    /**
     * Creates a new Plane with the given World
     *
     * @param   world   The world to work with
     *
     * @since   0.1.0
     */
    public Plane(World world) {

        if (world instanceof ServerWorld) {
            this.world = (ServerWorld) world;
        } else if (Chunker.SERVER != null) {
            for (ServerWorld server_world : Chunker.SERVER.getWorlds()) {
                if (server_world.getRegistryKey().getValue().equals(world.getRegistryKey().getValue())) {
                    this.world = server_world;
                    break;
                }
            }
        }

        if (this.world == null) {
            return;
        }

        this.dimension = this.world.getDimension();
    }

    /**
     * Make sure the chunk fetcher has been made
     *
     * @author   Jelle De Loecker   <jelle@elevenways.be>
     * @since    0.2.0
     */
    private ChunkFetcher.Session getFetcherSession() {

        if (this.fetcher == null) {
            this.fetcher = new ChunkFetcher(Chunker.SERVER, world).new Session();
        }

        return this.fetcher;
    }

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
        return Plane.from(Identifier.of(name));
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
    public static Plane from(World world) {
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
     * Preload a lump
     *
     * @author   Jelle De Loecker   <jelle@elevenways.be>
     * @since    0.2.0
     *
     * @param   chunk_pos   The position of the chunk to preload
     */
    public CompletableFuture<Optional<Lump>> preloadLump(ChunkPos chunk_pos) {

        if (this.preload_cache.containsKey(chunk_pos)) {
            return CompletableFuture.completedFuture(Optional.of(this.preload_cache.get(chunk_pos)));
        }

        ChunkFetcher.Session session = this.getFetcherSession();
        CompletableFuture<Optional<Chunk>> future = session.getChunkViewAsync(chunk_pos.x, chunk_pos.z);

        return future.thenApplyAsync(optional_chunk -> optional_chunk.map(chunk -> {

            Lump result = new Lump(chunk, this);

            this.preload_cache.put(chunk_pos, result);

            return result;
        }));
    }

    /**
     * Preload a lump
     *
     * @author   Jelle De Loecker   <jelle@elevenways.be>
     * @since    0.2.0
     *
     * @param   x   The chunk's X position
     * @param   z   The chunk's Z position
     */
    @NotNull
    public CompletableFuture<Optional<Lump>> preloadLump(int x, int z) {
        return this.preloadLump(new ChunkPos(x, z));
    }

    /**
     * Get a Lump chunk from this plane
     *
     * @param   chunk_pos
     *
     * @since   0.1.0
     */
    @Nullable
    public Lump getLump(ChunkPos chunk_pos) {

        if (this.preload_cache.containsKey(chunk_pos)) {
            return this.preload_cache.get(chunk_pos);
        }

        Chunk chunk = this.getFetcherSession().getChunkView(chunk_pos.x, chunk_pos.z);

        if (chunk == null) {
            return null;
        }

        Lump result = new Lump(chunk, this);
        result.setCoordinates(chunk_pos.x, chunk_pos.z);

        return result;
    }

    /**
     * Get a Lump chunk from this plane.
     * Will only return a Lump that's actively loaded or pre-loaded.
     *
     * @param   x   The chunk's X position
     * @param   z   The chunk's Z position
     *
     * @since   0.1.0
     */
    @Nullable
    public Lump getLump(int x, int z) {
        return getLump(new ChunkPos(x, z));
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

    /**
     * Get the floor at the given X and Z coordinates.
     * Break through the ceiling if needed.
     *
     * @since   0.3.0
     */
    public BlockPos getFloorAtBlock(BlockPos pos) {
        return getFloorAtBlock(pos.getX(), pos.getZ());
    }

    /**
     * Get the floor at the given X and Z coordinates.
     * Break through the ceiling if needed.
     *
     * @param   block_x   The block's X position
     * @param   block_z   The block's Z position
     *
     * @since   0.3.0
     */
    public BlockPos getFloorAtBlock(int block_x, int block_z) {

        Lump lump = getLumpAtBlock(block_x, block_z);

        if (lump == null) {
            return null;
        }

        if (this.hasCeiling()) {
            return lump.getFloorUnderCeiling(block_x & 15, block_z & 15);
        } else {
            return lump.getFloor(block_x & 15, block_z & 15);
        }
    }

}
