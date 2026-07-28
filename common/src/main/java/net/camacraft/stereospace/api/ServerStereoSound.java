package net.camacraft.stereospace.api;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.camacraft.stereospace.StereoSpace;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A server-side handle to a virtual stereo sound playing in a level. The sound
 * is made of two mono channels (left and right) which each have their own
 * anchor position and can be moved independently while the sound is playing.
 * <p>
 * Obtain instances through {@link StereoSounds#play}. Looping sounds keep
 * playing (and are sent to players that come into range) until {@link #stop()}
 * is called. One-shot sounds finish on their own on the client, but the handle
 * stays alive server-side so the channels can still be moved; call
 * {@link #stop()} or {@link #discard()} once you are done with it.
 */
public final class ServerStereoSound {

	private final long id;
	private final ServerLevel level;
	private final ResourceLocation sound;
	private final SoundSource source;
	private final float volume;
	private final float pitch;
	private final float spread;
	private final boolean looping;
	private final double range;

	private Vec3 leftPos;
	private Vec3 rightPos;
	private Entity attachedTo;
	private boolean removed;

	final Set<UUID> listeners = new HashSet<>();

	ServerStereoSound(long id, ServerLevel level, ResourceLocation sound, SoundSource source, Vec3 leftPos, Vec3 rightPos, float volume, float pitch, float spread, boolean looping, double range) {
		this.id = id;
		this.level = level;
		this.sound = sound;
		this.source = source;
		this.leftPos = leftPos;
		this.rightPos = rightPos;
		this.volume = volume;
		this.pitch = pitch;
		this.spread = spread;
		this.looping = looping;
		this.range = range;
	}

	public long getId() {
		return id;
	}

	public ServerLevel getLevel() {
		return level;
	}

	public Vec3 getLeftPosition() {
		return leftPos;
	}

	public Vec3 getRightPosition() {
		return rightPos;
	}

	public boolean isLooping() {
		return looping;
	}

	public boolean isRemoved() {
		return removed;
	}

	/**
	 * Moves both channel anchors to the same position, collapsing the sound
	 * back into a single billboarded stereo emitter.
	 */
	public void setPosition(Vec3 pos) {
		setPositions(pos, pos);
	}

	/**
	 * Moves both channel anchors at once, sending a single move packet.
	 */
	public void setPositions(Vec3 leftPos, Vec3 rightPos) {
		if (removed) {
			return;
		}

		this.leftPos = leftPos;
		this.rightPos = rightPos;
		broadcast(new StereoSoundPackets.MoveStereoSoundPayload(id, leftPos, rightPos));
	}

	/**
	 * Attaches the sound to an entity: both channel anchors follow the
	 * entity's position every level tick (the stereo center rides the entity)
	 * until {@link #detach()} is called. When the entity is removed or changes
	 * dimension, looping sounds are stopped and one-shots detach and finish
	 * playing where the entity last was. Manual {@link #setPositions} calls
	 * while attached are overwritten on the next tick.
	 */
	public void attachTo(Entity entity) {
		if (!removed) {
			this.attachedTo = entity;
		}
	}

	/**
	 * Stops following the attached entity; the anchors stay wherever the
	 * entity last was.
	 */
	public void detach() {
		this.attachedTo = null;
	}

	public Entity getAttachedEntity() {
		return attachedTo;
	}

	/**
	 * Moves only the left channel's anchor.
	 */
	public void setLeftPosition(Vec3 leftPos) {
		setPositions(leftPos, this.rightPos);
	}

	/**
	 * Moves only the right channel's anchor.
	 */
	public void setRightPosition(Vec3 rightPos) {
		setPositions(this.leftPos, rightPos);
	}

	/**
	 * Stops both channels on every listening client and removes the sound from
	 * the server-side manager.
	 */
	public void stop() {
		if (removed) {
			return;
		}

		broadcast(new StereoSoundPackets.StopStereoSoundPayload(id));
		listeners.clear();
		removed = true;
	}

	/**
	 * Removes the sound from the server-side manager without telling clients to
	 * stop, letting one-shot sounds finish playing naturally. After this the
	 * channels can no longer be moved.
	 */
	public void discard() {
		listeners.clear();
		removed = true;
	}

	/**
	 * Called by {@link StereoSounds#tickLevel} before listener updates; drags
	 * the anchors along with the attached entity, if any.
	 */
	void tickAttachment() {
		if (attachedTo == null || removed) {
			return;
		}

		if (attachedTo.isRemoved() || attachedTo.level() != level) {
			attachedTo = null;

			if (looping) {
				stop();
			}

			return;
		}

		Vec3 pos = attachedTo.position();

		if (!pos.equals(leftPos) || !pos.equals(rightPos)) {
			setPositions(pos, pos);
		}
	}

	StereoSoundPackets.PlayStereoSoundPayload createPlayPayload() {
		return new StereoSoundPackets.PlayStereoSoundPayload(id, sound, source, leftPos, rightPos, volume, pitch, spread, looping);
	}

	boolean isInRange(Vec3 pos) {
		return pos.distanceToSqr(leftPos) <= range * range || pos.distanceToSqr(rightPos) <= range * range;
	}

	void updateListeners() {
		listeners.removeIf(uuid -> {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);

			if (player == null || player.serverLevel() != level || !isInRange(player.position())) {
				if (player != null) {
					StereoSpace.sendToPlayer(player, new StereoSoundPackets.StopStereoSoundPayload(id));
				}

				return true;
			}

			return false;
		});

		if (looping) {
			for (ServerPlayer player : level.players()) {
				if (!listeners.contains(player.getUUID()) && isInRange(player.position())) {
					listeners.add(player.getUUID());
					StereoSpace.sendToPlayer(player, createPlayPayload());
				}
			}
		}
	}

	private void broadcast(CustomPacketPayload payload) {
		for (UUID uuid : listeners) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);

			if (player != null) {
				StereoSpace.sendToPlayer(player, payload);
			}
		}
	}
}
