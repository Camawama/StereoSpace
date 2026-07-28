package net.camacraft.stereospace.api;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.camacraft.stereospace.StereoSpace;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Server-side entry point for playing virtual stereo sounds in a level.
 * <p>
 * Vanilla Minecraft plays stereo sound files without any positional audio.
 * Stereo Space instead plays a single <b>stereo</b> sound file as two mono
 * sound sources in the world: at play time the client splits the decoded
 * stereo PCM into its left and right channels, so OpenAL only ever sees mono
 * data it can spatialize. On each client, every tick, the two sources are
 * positioned so that they face that player: the left channel sits virtually to
 * the player's left of its anchor and the right channel virtually to the
 * player's right. Every nearby client gets its own private pair of sources
 * positioned relative to its own listener, so each player hears a correctly
 * oriented stereo image with full positional audio.
 * <p>
 * The two channels each have their own anchor position and can be moved
 * independently through the returned {@link ServerStereoSound} handle,
 * allowing the stereo field itself to be widened, rotated or torn apart for
 * effect.
 * <p>
 * Note that positional stereo sounds played through VANILLA code paths
 * (jukebox records, playSound, other mods) are also made positional
 * automatically by the client-side auto splitter; this API is what you use
 * when you want the two channels as independently movable anchors.
 * <p>
 * The lifecycle methods ({@link #tickLevel}, {@link #onPlayerQuit},
 * {@link #onServerStarting}) are called by the loader modules.
 */
public final class StereoSounds {

	private static final Map<ResourceKey<Level>, Map<Long, ServerStereoSound>> SOUNDS = new HashMap<>();
	private static long nextId = 0;

	private StereoSounds() {
	}

	/**
	 * Plays a virtual stereo sound with both channel anchors at {@code pos}.
	 *
	 * @param sound sound event backed by a stereo (or mono) .ogg; stereo files are split into their two channels at runtime on the client
	 * @param spread distance in blocks between the two virtual channels as heard by a player
	 * @param looping whether the sound loops; looping sounds are also started for players that come into range later
	 */
	public static ServerStereoSound play(ServerLevel level, SoundEvent sound, SoundSource source, Vec3 pos, float volume, float pitch, float spread, boolean looping) {
		return play(level, sound.getLocation(), source, pos, pos, volume, pitch, spread, looping, defaultRange(volume, spread));
	}

	/**
	 * Plays a virtual stereo sound with independent anchor positions for the
	 * left and right channels.
	 */
	public static ServerStereoSound play(ServerLevel level, SoundEvent sound, SoundSource source, Vec3 leftPos, Vec3 rightPos, float volume, float pitch, float spread, boolean looping) {
		return play(level, sound.getLocation(), source, leftPos, rightPos, volume, pitch, spread, looping, defaultRange(volume, spread));
	}

	/**
	 * Fully parameterized variant taking a raw sound id (useful for sounds that
	 * only exist in {@code sounds.json}) and an explicit tracking range.
	 *
	 * @param range distance in blocks from either anchor within which players receive the sound
	 */
	public static ServerStereoSound play(ServerLevel level, ResourceLocation soundId, SoundSource source, Vec3 leftPos, Vec3 rightPos, float volume, float pitch, float spread, boolean looping, double range) {
		ServerStereoSound sound = new ServerStereoSound(nextId++, level, soundId, source, leftPos, rightPos, volume, pitch, spread, looping, range);
		SOUNDS.computeIfAbsent(level.dimension(), key -> new HashMap<>()).put(sound.getId(), sound);

		for (ServerPlayer player : level.players()) {
			if (sound.isInRange(player.position())) {
				sound.listeners.add(player.getUUID());
				StereoSpace.sendToPlayer(player, sound.createPlayPayload());
			}
		}

		return sound;
	}

	private static double defaultRange(float volume, float spread) {
		return Math.max(64.0, 16.0 * volume + spread + 16.0);
	}

	/**
	 * Called by the loader modules at the start of every server level tick.
	 */
	public static void tickLevel(ServerLevel level) {
		Map<Long, ServerStereoSound> sounds = SOUNDS.get(level.dimension());

		if (sounds == null || sounds.isEmpty()) {
			return;
		}

		Iterator<ServerStereoSound> iterator = sounds.values().iterator();

		while (iterator.hasNext()) {
			ServerStereoSound sound = iterator.next();

			if (sound.isRemoved()) {
				iterator.remove();
				continue;
			}

			sound.updateListeners();
		}
	}

	/**
	 * Called by the loader modules when a player disconnects.
	 */
	public static void onPlayerQuit(ServerPlayer player) {
		SOUNDS.values().forEach(sounds -> sounds.values().forEach(sound -> sound.listeners.remove(player.getUUID())));
	}

	/**
	 * Called by the loader modules when a server is starting; clears state
	 * left over from a previous (single player) session.
	 */
	public static void onServerStarting() {
		SOUNDS.clear();
	}
}
