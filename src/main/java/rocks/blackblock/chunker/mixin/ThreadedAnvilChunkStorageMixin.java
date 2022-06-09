package rocks.blackblock.chunker.mixin;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Mixin(ThreadedAnvilChunkStorage.class)
public interface ThreadedAnvilChunkStorageMixin {
    @Invoker
    CompletableFuture<Optional<NbtCompound>> callGetUpdatedChunkNbt (ChunkPos pos) throws IOException;
}
