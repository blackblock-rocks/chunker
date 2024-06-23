package rocks.blackblock.chunker.chunk;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rocks.blackblock.chunker.TileGenerator;
import rocks.blackblock.chunker.mixin.MinecraftServerAccessor;
import rocks.blackblock.chunker.mixin.ThreadedAnvilChunkStorageMixin;

import java.io.File;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * This class is a fetcher of chunks. These can be loaded or unloaded.
 *
 * @since     0.1.0
 * @version   0.2.0
 */
public class ChunkFetcher {

    private final LongSet validRegions = new LongOpenHashSet();

    // The path to the region folder
    private final File regionFolder;

    // The world to get chunks for
    private final ServerWorld world;

    // The TACS in use by this world
    private final ServerChunkLoadingManager tacs;

    // Method should (also) be called `createCodec` isntead of method_44343
    private static final Codec<PalettedContainer<BlockState>> CODEC = PalettedContainer.createPalettedContainerCodec(Block.STATE_IDS, BlockState.CODEC, PalettedContainer.PaletteProvider.BLOCK_STATE, Blocks.AIR.getDefaultState());
    private static final Logger LOGGER = LogManager.getLogger();

    private static Codec<PalettedContainer<Biome>> createCodec(Registry<Biome> biomeRegistry) {
        return PalettedContainer.createPalettedContainerCodec(biomeRegistry, biomeRegistry.getCodec(), PalettedContainer.PaletteProvider.BIOME, biomeRegistry.getOrThrow(BiomeKeys.PLAINS));
    }

    private static void logRecoverableError(ChunkPos chunkPos, int y, String message) {
        LOGGER.error("Recoverable errors when loading section [" + chunkPos.x + ", " + y + ", " + chunkPos.z + "]: " + message);
    }

    /**
     * Initialize the new ChunkFetcher
     *
     * @author   Jelle De Loecker   <jelle@elevenways.be>
     * @since    0.1.0
     *
     * @param    server   The server instance
     * @param    world    The world to get chunks from
     */
    public ChunkFetcher(MinecraftServer server, ServerWorld world) {
        this.regionFolder = new File(((MinecraftServerAccessor) server).getSession().getWorldDirectory(world.getRegistryKey()).toFile(), "region");
        this.world = world;
        this.tacs = world.getChunkManager().chunkLoadingManager;
    }

    /**
     * Thread-local session of a ChunkFetcher
     *
     * @author   Jelle De Loecker   <jelle@elevenways.be>
     * @since    0.1.0
     */
    public class Session {
        // Saved in testTileExists - as this data will be read again when rendering the chunk, might as well only read it once
        private final Long2ObjectMap<NbtCompound> unloadedChunkCachedData = new Long2ObjectOpenHashMap<>();

        /**
         * See if the tile exists
         *
         * @since   0.1.0
         */
        public boolean testTileExists(int tileX, int tileZ, int zoomShift) {
            int regionSize = TileGenerator.rightShiftButReversible(1, TileGenerator.TILE_TO_REGION_SHIFT - zoomShift);
            if (regionSize < 1) {
                regionSize = 1;
            }

            int regionOriginX = TileGenerator.rightShiftButReversible(tileX, TileGenerator.TILE_TO_REGION_SHIFT - zoomShift);
            int regionOriginZ = TileGenerator.rightShiftButReversible(tileZ, TileGenerator.TILE_TO_REGION_SHIFT - zoomShift);
            boolean regionFound = false;
            synchronized (validRegions) {
                outer:
                for (int regionOffX = 0; regionOffX < regionSize; regionOffX++) {
                    for (int regionOffZ = 0; regionOffZ < regionSize; regionOffZ++) {
                        // Not really a chunk pos, actually a region pos... :)
                        long pos = ChunkPos.toLong(regionOriginX + regionOffX, regionOriginZ + regionOffZ);
                        if (validRegions.contains(pos)) {
                            regionFound = true;
                            break outer;
                        }
                        if (new File(regionFolder, "r." + (regionOriginX + regionOffX) + "." + (regionOriginZ + regionOffZ) + ".mca").exists()) {
                            regionFound = true;
                            validRegions.add(pos);
                            break outer;
                        }
                    }
                }
            }
            if (!regionFound) {
                return false;
            }

            // If there is exactly one or more regions in this tile, checking for the regions' existence is enough
            // If there are more than one tiles in this region, we need to check the chunks
            if (TileGenerator.rightShiftButReversible(1, TileGenerator.TILE_TO_REGION_SHIFT - zoomShift) >= 1) {
                return true;
            }

            int chunkSize = TileGenerator.rightShiftButReversible(1, TileGenerator.TILE_TO_CHUNK_SHIFT - zoomShift);
            int chunkOriginX = TileGenerator.rightShiftButReversible(tileX, TileGenerator.TILE_TO_CHUNK_SHIFT - zoomShift);
            int chunkOriginZ = TileGenerator.rightShiftButReversible(tileZ, TileGenerator.TILE_TO_CHUNK_SHIFT - zoomShift);

            for (int chunkOffX = 0; chunkOffX < chunkSize; chunkOffX++) {
                for (int chunkOffZ = 0; chunkOffZ < chunkSize; chunkOffZ++) {
                    if (world.isChunkLoaded(chunkOriginX + chunkOffX, chunkOriginZ + chunkOffZ)) {
                        return true;
                    }

                    // First check if the region is valid
                    synchronized (validRegions) {
                        if (validRegions.contains(ChunkPos.toLong((chunkOriginX + chunkOffX) >> 5, (chunkOriginZ + chunkOffZ) >> 5))) {
                            return true;
                        }
                    }

                    // Attempt to get it's NBT
                    try {
                        ChunkPos pos = new ChunkPos(chunkOriginX + chunkOffX, chunkOriginZ + chunkOffZ);
                        CompletableFuture<Optional<NbtCompound>> chunkTag = tacs.getNbt(pos);
                        if (chunkTag != null) {
                            // @TODO: Again: this nbtcompound is in a promise. We need to wait for it to be resolved
                            //unloadedChunkCachedData.put(pos.toLong(), chunkTag);
                            //return true;
                            return false;
                        }
                    } catch (Exception e) {
                        // TODO: better logging
                        e.printStackTrace();
                        return false;
                    }
                }
            }

            return false;
        }

        /**
         * Get a Chunk from this world.
         * This can be a loaded chunk, or an unloaded chunk that we got earlier.
         * If the chunk is not loaded, it will not be fetched because that has to happen asynchronously.
         *
         * @author   Jelle De Loecker   <jelle@elevenways.be>
         * @since    0.1.0
         *
         * @param    chunk_x   The chunk x coordinate
         * @param    chunk_z   The chunk z coordinate
         */
        @Nullable
        public Chunk getChunkView(int chunk_x, int chunk_z) {

            // If the chunk is already loaded, it's an easy return!
            if (world.isChunkLoaded(chunk_x, chunk_z)) {
                return world.getChunk(chunk_x, chunk_z);
            }

            return null;
        }

        /**
         * Get a Future for a Chunk from this world.
         * This can be a loaded chunk, or an unloaded chunk.
         *
         * @author   Jelle De Loecker   <jelle@elevenways.be>
         * @since    0.1.0
         *
         * @param    chunk_x   The chunk x coordinate
         * @param    chunk_z   The chunk z coordinate
         */
        @NotNull
        public CompletableFuture<Optional<Chunk>> getChunkViewAsync(int chunk_x, int chunk_z) {

            CompletableFuture<Optional<Chunk>> result = new CompletableFuture<>();

            // If the chunk is already loaded, it's an easy return!
            if (world.isChunkLoaded(chunk_x, chunk_z)) {

                // `isChunkLoaded` LIES! It can return true, but the chunk is still not loaded
                // Causing a simple `world.getChunk()` call to hang the server.
                // So get the best effort chunk instead
                Chunk chunk = world.getChunk(chunk_x, chunk_z, ChunkStatus.EMPTY, false);

                result.complete(Optional.of(chunk));
                return result;
            }

            // Create the position to the chunk
            ChunkPos pos = new ChunkPos(chunk_x, chunk_z);

            // See if the Chunk's NBT data is already in memory
            NbtCompound chunk_nbt = unloadedChunkCachedData.remove(pos.toLong());

            // Create another future for this
            CompletableFuture<Optional<NbtCompound>> chunk_nbt_future = null;

            if (chunk_nbt == null) {
                try {
                    chunk_nbt_future = ((ThreadedAnvilChunkStorageMixin) tacs).callGetUpdatedChunkNbt(pos);
                } catch (Exception e) {
                    // TODO: better logging
                    e.printStackTrace();
                }

                if (chunk_nbt_future == null) {
                    result.complete(Optional.empty());
                    return result;
                }
            } else {
                // Create a dummy future for the nbt data we already found
                chunk_nbt_future = CompletableFuture.completedFuture(Optional.of(chunk_nbt));
            }

            // Wait for the actual chunk NBT data
            result = chunk_nbt_future.thenApply(optional_nbt -> {

                // It's not there, so no chunk data found!
                if (optional_nbt.isEmpty()) {
                    return Optional.empty();
                }

                // Parse the chunk nbt and make it return an optional chunk
                Optional<UnloadedChunkView> chunk_from_nbt_option = this.getChunkFromNbt(optional_nbt.get(), pos);

                // If the chunk is not there, return an empty optional
                return chunk_from_nbt_option.map(unloadedChunkView -> (Chunk) unloadedChunkView);

            });

            return result;
        }

        /**
         * Try to get a Chunk instance from the given chunk data
         *
         * @author   Jelle De Loecker   <jelle@elevenways.be>
         * @since    0.2.0
         *
         * @param    chunk_nbt   The chunk NBT data
         * @param    pos         The chunk position
         */
        @NotNull
        private Optional<UnloadedChunkView> getChunkFromNbt(NbtCompound chunk_nbt, ChunkPos pos) {

            NbtCompound level = chunk_nbt.getCompound("Level");
            ChunkStatus status = ChunkStatus.byId(chunk_nbt.getString("Status"));

            // We only want fully generated chunks
            if (!status.isAtLeast(ChunkStatus.FULL)) {

                // Chunks that have been updated via a DFU however are marked as "EMPTY",
                // but actually contain all the data needed to render the map
                if (!status.equals(ChunkStatus.EMPTY)) {
                    return Optional.empty();
                }
            }

            // Get all the chunk sections from the NBT data
            NbtList chunk_sections = chunk_nbt.getList("sections", 10);

            // Get the amount of vertical sections in this world
            int vertical_section_count = world.countVerticalSections();

            ChunkSection[] sections = new ChunkSection[vertical_section_count];

            ReadableContainer palettedContainer2;
            PalettedContainer palettedContainer;
            Registry<Biome> registry = world.getRegistryManager().get(RegistryKeys.BIOME);
            Codec<PalettedContainer<Biome>> codec = createCodec(registry);

            for (int i = 0; i < chunk_sections.size(); ++i) {
                NbtCompound sectionTag = chunk_sections.getCompound(i);
                int y = sectionTag.getByte("Y");
                int l = world.sectionCoordToIndex(y);

                if (l >= 0 && l < sections.length) {

                    if (sectionTag.contains("block_states", 10)) {
                        palettedContainer = (PalettedContainer)CODEC.parse(NbtOps.INSTANCE, sectionTag.getCompound("block_states")).promotePartial((errorMessage) -> {
                            logRecoverableError(pos, y, errorMessage);
                        }).getOrThrow(ChunkSerializer.ChunkLoadingException::new);
                    } else {
                        palettedContainer = new PalettedContainer(Block.STATE_IDS, Blocks.AIR.getDefaultState(), PalettedContainer.PaletteProvider.BLOCK_STATE);
                    }

                    if (sectionTag.contains("biomes", 10)) {
                        palettedContainer2 = (ReadableContainer)codec.parse(NbtOps.INSTANCE, sectionTag.getCompound("biomes")).promotePartial((errorMessage) -> {
                            logRecoverableError(pos, y, errorMessage);
                        }).getOrThrow(ChunkSerializer.ChunkLoadingException::new);
                    } else {
                        palettedContainer2 = new PalettedContainer(registry.getIndexedEntries(), registry.entryOf(BiomeKeys.PLAINS), PalettedContainer.PaletteProvider.BIOME);
                    }

                    ChunkSection chunkSection = new ChunkSection((PalettedContainer<BlockState>)palettedContainer, palettedContainer2);
                    chunkSection.calculateCounts();
                    sections[l] = chunkSection;
                }
            }

            UnloadedChunkView unloadedChunkView = new UnloadedChunkView(sections, world, pos);

            NbtCompound heightmaps = level.getCompound("Heightmaps");
            String heightmapName = Heightmap.Type.WORLD_SURFACE.getName();
            if (heightmaps.contains(heightmapName, 12)) {
                unloadedChunkView.setHeightmap(Heightmap.Type.WORLD_SURFACE, heightmaps.getLongArray(heightmapName));
            } else {
                Heightmap.populateHeightmaps(unloadedChunkView, Collections.singleton(Heightmap.Type.WORLD_SURFACE));
            }

            return Optional.of(unloadedChunkView);
        }
    }
}