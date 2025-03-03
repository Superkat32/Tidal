package net.superkat.tidal;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.superkat.tidal.duck.TidalWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tidal implements ModInitializer {
	public static final String MOD_ID = "tidal";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ClientTickEvents.END_WORLD_TICK.register(clientWorld -> {
			TidalWorld tidalWorld = (TidalWorld) clientWorld;
			tidalWorld.tidal$tidalWaveHandler().tick();
		});

		TidalParticles.registerParticles();
	}
}