package net.camacraft.stereospace.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.camacraft.stereospace.api.StereoSoundChannel;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
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

	public static final float DEFAULT_SPREAD = 4.0F;
	public static final float MIN_SPREAD = 0.0F;
	public static final float MAX_SPREAD = 64.0F;

	/**
	 * Distance in blocks between the two virtual channels of an auto-split
	 * sound, as heard by the player. Read live by every playing
	 * {@link SplitStereoSoundInstance}, so changes apply to sounds that are
	 * already playing.
	 */
	private static float spread = DEFAULT_SPREAD;
	private static boolean enabled = true;

	private static final Map<ResourceLocation, Boolean> STEREO_FILES = new ConcurrentHashMap<>();
	private static final Map<SoundInstance, SplitStereoSoundInstance[]> ACTIVE = new IdentityHashMap<>();

	private AutoStereoSplitter() {
	}

	public static void setEnabled(boolean enabled) {
		AutoStereoSplitter.enabled = enabled;
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void setSpread(float spread) {
		AutoStereoSplitter.spread = Mth.clamp(spread, MIN_SPREAD, MAX_SPREAD);
	}

	public static float getSpread() {
		return spread;
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

		SplitStereoSoundInstance left = new SplitStereoSoundInstance(instance, resolved, sound, StereoSoundChannel.LEFT);
		SplitStereoSoundInstance right = new SplitStereoSoundInstance(instance, resolved, sound, StereoSoundChannel.RIGHT);
		left.setSibling(right);
		right.setSibling(left);
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

		if (halves == null) {
			halves = removeSameSoundSameSpot(instance);
		}

		if (halves != null) {
			for (SplitStereoSoundInstance half : halves) {
				half.forceStop();
				engine.stop(half);
			}
		}
	}

	/**
	 * Called at the head of {@code SoundEngine.isActive}. The original of a
	 * split never reached OpenAL, so the engine would report it dead while its
	 * two halves are still audibly playing; code polling the original (to
	 * avoid restarting an ambient loop, for instance) must see it as active.
	 */
	public static boolean isSplitActive(SoundManager soundManager, SoundInstance instance) {
		SplitStereoSoundInstance[] halves = ACTIVE.get(instance);

		return halves != null && (soundManager.isActive(halves[0]) || soundManager.isActive(halves[1]));
	}

	/**
	 * Compat fallback for the stop forwarding: some sound mods (Sound Physics
	 * forks, for one) cancel the original play call and re-play a wrapper
	 * around the instance a few ticks later, so the split gets registered
	 * under the wrapper while stopping code still holds the original. When
	 * the identity lookup misses, treat a split playing the same sound from
	 * the exact same spot as the sound being stopped.
	 */
	private static SplitStereoSoundInstance[] removeSameSoundSameSpot(SoundInstance stopped) {
		for (Iterator<Map.Entry<SoundInstance, SplitStereoSoundInstance[]>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<SoundInstance, SplitStereoSoundInstance[]> entry = it.next();
			SoundInstance original = entry.getKey();

			if (original.getLocation().equals(stopped.getLocation())
					&& original.getSource() == stopped.getSource()
					&& original.getX() == stopped.getX()
					&& original.getY() == stopped.getY()
					&& original.getZ() == stopped.getZ()) {
				it.remove();
				return entry.getValue();
			}
		}

		return null;
	}

	/**
	 * Adds a {@link StereoDebugRenderer.DebugPair} for every auto-split pair
	 * that is still playing, for the in-world debug outlines.
	 */
	public static void collectDebugPairs(SoundManager soundManager, Collection<StereoDebugRenderer.DebugPair> out) {
		for (SplitStereoSoundInstance[] halves : ACTIVE.values()) {
			if (soundManager.isActive(halves[0]) || soundManager.isActive(halves[1])) {
				out.add(new StereoDebugRenderer.DebugPair(
						halves[0].getAnchor(),
						new Vec3(halves[0].getX(), halves[0].getY(), halves[0].getZ()),
						new Vec3(halves[1].getX(), halves[1].getY(), halves[1].getZ())));
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
