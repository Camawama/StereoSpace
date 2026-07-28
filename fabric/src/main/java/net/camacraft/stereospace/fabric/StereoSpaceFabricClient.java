package net.camacraft.stereospace.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.camacraft.stereospace.api.StereoSoundPackets.MoveStereoSoundPayload;
import net.camacraft.stereospace.api.StereoSoundPackets.PlayStereoSoundPayload;
import net.camacraft.stereospace.api.StereoSoundPackets.StopStereoSoundPayload;
import net.camacraft.stereospace.client.ClientStereoSoundManager;

public class StereoSpaceFabricClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		// Handlers run on the client game thread.
		ClientPlayNetworking.registerGlobalReceiver(PlayStereoSoundPayload.TYPE, (payload, context) -> ClientStereoSoundManager.handlePlay(payload));
		ClientPlayNetworking.registerGlobalReceiver(MoveStereoSoundPayload.TYPE, (payload, context) -> ClientStereoSoundManager.handleMove(payload));
		ClientPlayNetworking.registerGlobalReceiver(StopStereoSoundPayload.TYPE, (payload, context) -> ClientStereoSoundManager.handleStop(payload));
	}
}
