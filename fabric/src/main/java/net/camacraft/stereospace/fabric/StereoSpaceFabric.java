package net.camacraft.stereospace.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.camacraft.stereospace.StereoSpace;
import net.camacraft.stereospace.api.StereoSoundPackets.MoveStereoSoundPayload;
import net.camacraft.stereospace.api.StereoSoundPackets.PlayStereoSoundPayload;
import net.camacraft.stereospace.api.StereoSoundPackets.StopStereoSoundPayload;
import net.camacraft.stereospace.api.StereoSounds;

public class StereoSpaceFabric implements ModInitializer {

	@Override
	public void onInitialize() {
		StereoSpace.setPacketSender(ServerPlayNetworking::send);

		PayloadTypeRegistry.playS2C().register(PlayStereoSoundPayload.TYPE, PlayStereoSoundPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(MoveStereoSoundPayload.TYPE, MoveStereoSoundPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(StopStereoSoundPayload.TYPE, StopStereoSoundPayload.STREAM_CODEC);

		ServerLifecycleEvents.SERVER_STARTING.register(server -> StereoSounds.onServerStarting());
		ServerTickEvents.START_WORLD_TICK.register(StereoSounds::tickLevel);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> StereoSounds.onPlayerQuit(handler.player));
	}
}
