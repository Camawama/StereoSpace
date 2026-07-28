package net.camacraft.stereospace.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.camacraft.stereospace.api.StereoSoundPackets.MoveStereoSoundPayload;
import net.camacraft.stereospace.api.StereoSoundPackets.PlayStereoSoundPayload;
import net.camacraft.stereospace.api.StereoSoundPackets.StopStereoSoundPayload;
import net.camacraft.stereospace.client.ClientStereoSoundManager;
import net.camacraft.stereospace.client.StereoDebugRenderer;
import net.camacraft.stereospace.client.StereoSpaceCommands;

public class StereoSpaceFabricClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		// Handlers run on the client game thread.
		ClientPlayNetworking.registerGlobalReceiver(PlayStereoSoundPayload.TYPE, (payload, context) -> ClientStereoSoundManager.handlePlay(payload));
		ClientPlayNetworking.registerGlobalReceiver(MoveStereoSoundPayload.TYPE, (payload, context) -> ClientStereoSoundManager.handleMove(payload));
		ClientPlayNetworking.registerGlobalReceiver(StopStereoSoundPayload.TYPE, (payload, context) -> ClientStereoSoundManager.handleStop(payload));

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(StereoSpaceCommands.<FabricClientCommandSource>build((source, message) -> source.sendFeedback(message))));

		WorldRenderEvents.AFTER_ENTITIES.register(context ->
				StereoDebugRenderer.render(context.matrixStack(), context.camera().getPosition()));
	}
}
