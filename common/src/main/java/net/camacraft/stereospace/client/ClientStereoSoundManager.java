package net.camacraft.stereospace.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import net.camacraft.stereospace.api.StereoSoundChannel;
import net.camacraft.stereospace.api.StereoSoundPackets.MoveStereoSoundPayload;
import net.camacraft.stereospace.api.StereoSoundPackets.PlayStereoSoundPayload;
import net.camacraft.stereospace.api.StereoSoundPackets.StopStereoSoundPayload;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side registry of active virtual stereo sounds, keyed by the id the
 * server assigned. Each entry owns the two mono {@link ClientStereoSoundInstance}s
 * that make up the stereo pair. Packet handlers run on the client game thread.
 */
public final class ClientStereoSoundManager {

	private static final Map<Long, StereoPair> SOUNDS = new HashMap<>();

	private ClientStereoSoundManager() {
	}

	public static void handlePlay(PlayStereoSoundPayload payload) {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.level == null) {
			return;
		}

		prune(minecraft.getSoundManager());

		StereoPair existing = SOUNDS.remove(payload.id());

		if (existing != null) {
			existing.stop(minecraft.getSoundManager());
		}

		SoundEvent sound = resolve(payload.sound());
		ClientStereoSoundInstance left = new ClientStereoSoundInstance(sound, payload.source(), StereoSoundChannel.LEFT, payload.leftPos(), payload.volume(), payload.pitch(), payload.spread(), payload.looping(), payload.id());
		ClientStereoSoundInstance right = new ClientStereoSoundInstance(sound, payload.source(), StereoSoundChannel.RIGHT, payload.rightPos(), payload.volume(), payload.pitch(), payload.spread(), payload.looping(), payload.id());

		SOUNDS.put(payload.id(), new StereoPair(left, right));
		minecraft.getSoundManager().play(left);
		minecraft.getSoundManager().play(right);
	}

	public static void handleMove(MoveStereoSoundPayload payload) {
		StereoPair pair = SOUNDS.get(payload.id());

		if (pair != null) {
			pair.left().setAnchor(payload.leftPos());
			pair.right().setAnchor(payload.rightPos());
		}
	}

	public static void handleStop(StopStereoSoundPayload payload) {
		StereoPair pair = SOUNDS.remove(payload.id());

		if (pair != null) {
			pair.stop(Minecraft.getInstance().getSoundManager());
		}
	}

	/**
	 * Adds a {@link StereoDebugRenderer.DebugPair} for every server-driven pair
	 * that is still playing, for the in-world debug outlines.
	 */
	public static void collectDebugPairs(SoundManager soundManager, Collection<StereoDebugRenderer.DebugPair> out) {
		for (StereoPair pair : SOUNDS.values()) {
			if (soundManager.isActive(pair.left()) || soundManager.isActive(pair.right())) {
				out.add(new StereoDebugRenderer.DebugPair(
						pair.left().getAnchor(),
						new Vec3(pair.left().getX(), pair.left().getY(), pair.left().getZ()),
						new Vec3(pair.right().getX(), pair.right().getY(), pair.right().getZ())));
			}
		}
	}

	private static SoundEvent resolve(ResourceLocation id) {
		SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(id);
		return event != null ? event : SoundEvent.createVariableRangeEvent(id);
	}

	/**
	 * Drops entries whose instances the sound engine no longer tracks, e.g.
	 * finished one-shots or sounds cleared by a disconnect. Instances start
	 * synchronously in {@link #handlePlay}, so an inactive pair is a dead pair.
	 */
	private static void prune(SoundManager soundManager) {
		SOUNDS.values().removeIf(pair -> !soundManager.isActive(pair.left()) && !soundManager.isActive(pair.right()));
	}

	private record StereoPair(ClientStereoSoundInstance left, ClientStereoSoundInstance right) {

		private void stop(SoundManager soundManager) {
			left.forceStop();
			right.forceStop();
			soundManager.stop(left);
			soundManager.stop(right);
		}
	}
}
