package net.camacraft.stereospace.neoforge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.camacraft.stereospace.StereoSpace;
import net.camacraft.stereospace.api.StereoSoundPackets.MoveStereoSoundPayload;
import net.camacraft.stereospace.api.StereoSoundPackets.PlayStereoSoundPayload;
import net.camacraft.stereospace.api.StereoSoundPackets.StopStereoSoundPayload;
import net.camacraft.stereospace.api.StereoSounds;
import net.camacraft.stereospace.client.ClientStereoSoundManager;

@Mod(StereoSpace.MOD_ID)
public class StereoSpaceNeoForge {

	public StereoSpaceNeoForge(IEventBus modBus) {
		StereoSpace.setPacketSender((player, payload) -> PacketDistributor.sendToPlayer(player, payload));

		modBus.addListener(this::registerPayloads);

		NeoForge.EVENT_BUS.<ServerStartingEvent>addListener(event -> StereoSounds.onServerStarting());
		NeoForge.EVENT_BUS.<LevelTickEvent.Pre>addListener(event -> {
			if (event.getLevel() instanceof ServerLevel level) {
				StereoSounds.tickLevel(level);
			}
		});
		NeoForge.EVENT_BUS.<PlayerEvent.PlayerLoggedOutEvent>addListener(event -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				StereoSounds.onPlayerQuit(player);
			}
		});
	}

	private void registerPayloads(RegisterPayloadHandlersEvent event) {
		// Handlers run on the client game thread; they never fire on a
		// dedicated server, so the client class reference is safe here.
		var registrar = event.registrar("1");
		registrar.playToClient(PlayStereoSoundPayload.TYPE, PlayStereoSoundPayload.STREAM_CODEC, (payload, context) -> ClientStereoSoundManager.handlePlay(payload));
		registrar.playToClient(MoveStereoSoundPayload.TYPE, MoveStereoSoundPayload.STREAM_CODEC, (payload, context) -> ClientStereoSoundManager.handleMove(payload));
		registrar.playToClient(StopStereoSoundPayload.TYPE, StopStereoSoundPayload.STREAM_CODEC, (payload, context) -> ClientStereoSoundManager.handleStop(payload));
	}
}
