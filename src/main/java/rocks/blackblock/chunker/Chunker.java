package rocks.blackblock.chunker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Chunker implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("chunker");
	public static MinecraftServer SERVER = null;

	@Override
	public void onInitialize() {
		LOGGER.info("Chunker is loaded!");

		ServerLifecycleEvents.SERVER_STARTED.register(server -> SERVER = server);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SERVER = null);
	}
}
