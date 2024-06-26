package rocks.blackblock.chunker.chunk;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import rocks.blackblock.chunker.Chunker;
import rocks.blackblock.chunker.world.Plane;

public class BlockSearcher {

    private Plane plane;
    private Lump lump = null;
    private Heightmap heightmap = null;
    private int height;
    private int water_depth;

    public final BlockPos.Mutable pos = new BlockPos.Mutable();
    private final BlockPos.Mutable depth_test_pos = new BlockPos.Mutable();
    private BlockState block_state;

    /**
     * Create a new BlockSearcher for the given plane
     *
     * @param   plane
     */
    public BlockSearcher(Plane plane) {
        this.plane = plane;
    }

    /**
     * Get the heightmap for the current chunk
     *
     * @since   0.1.0
     */
    private Heightmap getHeightmap() {

        if (this.heightmap == null && this.lump != null) {
            this.heightmap = this.lump.getChunk().getHeightmap(Heightmap.Type.WORLD_SURFACE);
        }

        return this.heightmap;
    }

    /**
     * Set the current active lump
     *
     * @since   0.1.0
     */
    private void setLump(Lump lump) {

        if (this.lump != null && this.lump == lump) {
            return;
        }

        this.lump = lump;
        this.heightmap = null;
        this.block_state = null;
    }

    /**
     * Return the height
     *
     * @since   0.1.0
     */
    public int getHeight() {
        return this.height;
    }

    /**
     * Return the current BlockState
     *
     * @since   0.1.0
     */
    public BlockState getBlockState() {
        return this.block_state;
    }

    /**
     * Is the current block a non-empty fluid?
     *
     * @since   0.1.0
     */
    public boolean isVisibleFluid() {
        return !this.block_state.getFluidState().isEmpty();
    }

    /**
     * Get the current water depth
     *
     * @since   0.1.0
     */
    public int getWaterDepth() {
        return this.water_depth;
    }

    /**
     * Get the current found BlockState's map color
     *
     * @since   0.1.0
     */
    public MapColor getCurrentMapColor() {

        if (this.block_state == null) {
            return MapColor.CLEAR;
        }

        return this.block_state.getMapColor(this.lump.getPlane().getWorld(), this.pos);
    }

    /**
     * Search for the first non-clear (map color) block
     *
     * @param   lump   The lump to search in
     * @param   x      The X-coordinate of the block
     * @param   z      The Z-coordinate of the block
     */
    public boolean searchForBlock(Lump lump, int x, int z) {

        this.setLump(lump);
        Heightmap heightmap = this.getHeightmap();

        if (heightmap == null) {
            return false;
        }

        // Get the top, non-air block Y level
        this.height = heightmap.get(x & 15, z & 15);

        this.pos.set(lump.convertLocalCoordinateToGlobal(x), this.height, lump.convertLocalCoordinateToGlobal(z));

        if (this.height <= -63) {
            this.block_state = Blocks.BEDROCK.getDefaultState();
        } else {
            do {
                pos.setY(--this.height);
                this.block_state = this.lump.getChunk().getBlockState(this.pos);
            } while (this.getCurrentMapColor() == MapColor.CLEAR && this.height > -64);
        }

        return true;
    }

    /**
     * Search for the first non-clear (map color) block under the ceiling
     *
     * @param   lump   The lump to search in
     * @param   x      The X-coordinate of the block (Chunk-local)
     * @param   z      The Z-coordinate of the block (Chunk-local)
     */
    public boolean searchForBlockUnderCeiling(Lump lump, int x, int z) {

        this.setLump(lump);
        Heightmap heightmap = this.getHeightmap();

        if (heightmap == null) {
            return false;
        }

        // Get the top, non-air block Y level
        this.height = heightmap.get(x & 15, z & 15) - 1;

        // Remember the initial height
        int initial_height = this.height;

        // Keep track of when we broke through the ceiling
        boolean broke_though_ceiling = false;

        this.pos.set(lump.convertLocalCoordinateToGlobal(x), initial_height, lump.convertLocalCoordinateToGlobal(z));

        BlockState first_block_state = this.lump.getChunk().getBlockState(this.pos);
        this.block_state = first_block_state;

        if (first_block_state.isAir()) {
            broke_though_ceiling = true;
        }

        while ((!broke_though_ceiling || this.getCurrentMapColor() == MapColor.CLEAR) && this.height > -64) {
            pos.setY(--this.height);
            this.block_state = this.lump.getChunk().getBlockState(this.pos);

            if (this.block_state.isAir()) {
                broke_though_ceiling = true;
            }
        }

        if (!broke_though_ceiling) {
            this.block_state = first_block_state;
            this.height = initial_height;
            pos.setY(initial_height);
        }

        return true;
    }

    /**
     * Calculate the depth of the water
     *
     * @since   0.1.0
     */
    public void calculateWaterDepth(Lump lump) {

        int temp_height = this.height - 1;
        this.water_depth = 0;

        this.depth_test_pos.set(this.pos);

        BlockState depth_test_block;

        do {
            depth_test_pos.setY(temp_height--);
            depth_test_block = lump.getChunk().getBlockState(depth_test_pos);
            ++this.water_depth;
        } while (temp_height > -64 && !depth_test_block.getFluidState().isEmpty());

        FluidState fluid_state = this.block_state.getFluidState();
        this.block_state = !fluid_state.isEmpty() && !this.block_state.isSideSolidFullSquare(this.lump.getPlane().getWorld(), depth_test_pos, Direction.UP) ? fluid_state.getBlockState() : this.block_state;

    }

}
