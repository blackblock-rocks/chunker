package rocks.blackblock.chunker.chunk;

import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.UpgradeData;
import net.minecraft.world.tick.BasicTickScheduler;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Skeleton implementation of Chunk to represent an unloaded chunk view
 */
public class UnloadedChunkView extends Chunk {
    private final ChunkSection[] sections;
    private final World world;
    private final Heightmap worldSurfaceHeightmap;
    private final ChunkPos pos;

    UnloadedChunkView(ChunkSection[] sections, World world, ChunkPos pos) {
        super(pos, UpgradeData.NO_UPGRADE_DATA, world, world.getRegistryManager().getOrThrow(RegistryKeys.BIOME), 0, null, null);
        this.sections = sections;
        this.world = world;
        this.worldSurfaceHeightmap = new Heightmap(this, Heightmap.Type.WORLD_SURFACE);
        this.pos = pos;
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        int sectionIndex = this.getSectionIndex(y);

        if (sectionIndex >= 0 && sectionIndex < this.sections.length) {
            ChunkSection chunkSection = this.sections[sectionIndex];

            if (!chunkSection.isEmpty()) {
                return chunkSection.getBlockState(x & 15, y & 15, z & 15);
            }
        }

        return Blocks.AIR.getDefaultState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        int sectionIndex = this.getSectionIndex(y);

        if (sectionIndex >= 0 && sectionIndex < this.sections.length) {
            ChunkSection chunkSection = this.sections[sectionIndex];

            if (!chunkSection.isEmpty()) {
                return chunkSection.getFluidState(x & 15, y & 15, z & 15);
            }
        }

        return Fluids.EMPTY.getDefaultState();
    }

    @Override
    public @Nullable BlockState setBlockState(BlockPos pos, BlockState state, boolean moved) {
        return null;
    }

    @Override
    public void setBlockEntity(BlockEntity blockEntity) {}

    @Override
    public void addEntity(Entity entity) {}

    @Override
    public Set<BlockPos> getBlockEntityPositions() {
        return null;
    }

    @Override
    public ChunkSection[] getSectionArray() {
        return sections;
    }

    @Override
    public Collection<Map.Entry<Heightmap.Type, Heightmap>> getHeightmaps() {
        return Collections.singletonList(new Map.Entry<Heightmap.Type, Heightmap>() {
            @Override
            public Heightmap.Type getKey() {
                return Heightmap.Type.WORLD_SURFACE;
            }

            @Override
            public Heightmap getValue() {
                return worldSurfaceHeightmap;
            }

            @Override
            public Heightmap setValue(Heightmap value) {
                return worldSurfaceHeightmap;
            }
        });
    }

    @Override
    public void setHeightmap(Heightmap.Type type, long[] heightmap) {
        worldSurfaceHeightmap.setTo(this, type, heightmap);
    }

    @Override
    public Heightmap getHeightmap(Heightmap.Type type) {
        return worldSurfaceHeightmap;
    }

    @Override
    public int sampleHeightmap(Heightmap.Type type, int x, int z) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChunkPos getPos() {
        return this.pos;
    }

    @Override
    public boolean needsSaving() {
        return false;
    }

    @Override
    public ChunkStatus getStatus() {
        return ChunkStatus.FULL;
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {

    }

    @Override
    public ShortList[] getPostProcessingLists() {
        return new ShortList[0];
    }

    @Override
    public @Nullable NbtCompound getBlockEntityNbt(BlockPos pos) {
        return null;
    }

    @Nullable
    @Override
    public NbtCompound getPackedBlockEntityNbt(BlockPos pos, RegistryWrapper.WrapperLookup registryLookup) {
        return null;
    }

    @Override
    public BasicTickScheduler<Block> getBlockTickScheduler() {
        return null;
    }

    @Override
    public BasicTickScheduler<Fluid> getFluidTickScheduler() {
        return null;
    }

    @Override
    public TickSchedulers getTickSchedulers(long time) {
        return null;
    }

    @Override
    public UpgradeData getUpgradeData() {
        return null;
    }

    @Override
    public void setInhabitedTime(long inhabitedTime) {

    }

    @Override
    public long getInhabitedTime() {
        return 0;
    }

    @Override
    public boolean isLightOn() {
        return false;
    }

    @Override
    public void setLightOn(boolean lightOn) {

    }

    @Override
    public int getHeight() {
        return this.world.getHeight();
    }

    @Override
    public int getBottomY() {
        return this.world.getBottomY();
    }
}