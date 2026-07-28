package net.camacraft.stereospace.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.camacraft.stereospace.api.StereoSoundChannel;

import java.io.IOException;
import java.io.InputStream;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Automatic client-side stereo splitting for sounds that never go through the
 * {@code StereoSounds} server API - jukebox records, vanilla and modded world
 * sounds alike. Whenever ANY positional sound resolves to a stereo .ogg, the
 * original instance is intercepted before it reaches OpenAL and replaced by a
 * pair of {@link SplitStereoSoundInstance} mono halves billboarded around the
 * original's position. Non-positional sounds (music, UI clicks, anything
 * relative or unattenuated) are left alone, since vanilla's everywhere-at-once
 * playback is correct for those.
 * <p>
 * Whether a file is stereo is sniffed from the Vorbis identification header
 * (the channel count lives at a fixed offset after the {@code \x01vorbis}
 * marker) and cached per file until the next resource reload.
 */
public final class AutoStereoSplitter {

	/**
	 * Distance in blocks between the two virtual channels of an auto-split
	 * sound, as heard by the player.
	 */
	private static float spread = 4.0F;
	private static boolean enabled = true;

	private static final Map<ResourceLocation, Boolean> STEREO_FILES = new ConcurrentHashMap<>();
	private static final Map<SoundInstance, SplitStereoSoundInstance[]> ACTIVE = new IdentityHashMap<>();

	private AutoStereoSplitter() {
	}

	public static void setEnabled(boolean enabled) {
		AutoStereoSplitter.enabled = enabled;
	}

	public static void setSpread(float spread) {
		AutoStereoSplitter.spread = spread;
	}

	/**
	 * Called at the head of {@code SoundEngine.play}. Returns true if the
	 * instance was split into a mono pair (and the original play call must be
	 * cancelled).
	 */
	public static boolean trySplit(SoundEngine engine, SoundManager soundManager, SoundInstance instance) {
		if (!enabled || instance instanceof StereoChannelSound) {
			return false;
		}

		if (instance.isRelative() || instance.getAttenuation() == SoundInstance.Attenuation.NONE || !instance.canPlaySound()) {
			return false;
		}

		WeighedSoundEvents resolved = instance.resolve(soundManager);

		if (resolved == null) {
			return false;
		}

		Sound sound = instance.getSound();

		if (sound == null || sound == SoundManager.EMPTY_SOUND || sound == SoundManager.INTENTIONALLY_EMPTY_SOUND) {
			return false;
		}

		if (!isStereoFile(sound.getPath())) {
			return false;
		}

		prune(soundManager);

		SplitStereoSoundInstance left = new SplitStereoSoundInstance(instance, resolved, sound, StereoSoundChannel.LEFT, spread);
		SplitStereoSoundInstance right = new SplitStereoSoundInstance(instance, resolved, sound, StereoSoundChannel.RIGHT, spread);
		ACTIVE.put(instance, new SplitStereoSoundInstance[] { left, right });

		engine.play(left);
		engine.play(right);
		return true;
	}

	/**
	 * Called at the head of {@code SoundEngine.stop}. Code that stops a sound
	 * holds the ORIGINAL instance, which the engine never actually played, so
	 * the stop is forwarded to the two halves that replaced it. This is what
	 * stops a jukebox record when the disc is removed.
	 */
	public static void onStopped(SoundEngine engine, SoundInstance instance) {
		SplitStereoSoundInstance[] halves = ACTIVE.remove(instance);

		if (halves != null) {
			for (SplitStereoSoundInstance half : halves) {
				half.forceStop();
				engine.stop(half);
			}
		}
	}

	/**
	 * Called on sound-engine reload; sound files may have changed and every
	 * playing sound was stopped.
	 */
	public static void clear() {
		STEREO_FILES.clear();
		ACTIVE.clear();
	}

	private static void prune(SoundManager soundManager) {
		ACTIVE.values().removeIf(halves -> !soundManager.isActive(halves[0]) && !soundManager.isActive(halves[1]));
	}

	private static boolean isStereoFile(ResourceLocation path) {
		return STEREO_FILES.computeIfAbsent(path, AutoStereoSplitter::sniffStereo);
	}

	/**
	 * Reads the first bytes of the .ogg and finds the Vorbis identification
	 * header: {@code \x01 v o r b i s}, a 4 byte version, then one byte of
	 * channel count.
	 */
	private static boolean sniffStereo(ResourceLocation path) {
		try (InputStream in = Minecraft.getInstance().getResourceManager().open(path)) {
			byte[] head = in.readNBytes(256);

			for (int i = 0; i + 11 < head.length; i++) {
				if (head[i] == 1 && head[i + 1] == 'v' && head[i + 2] == 'o' && head[i + 3] == 'r' && head[i + 4] == 'b' && head[i + 5] == 'i' && head[i + 6] == 's') {
					return (head[i + 11] & 0xFF) >= 2;
				}
			}
		} catch (IOException ignored) {
		}

		return false;
	}
}
