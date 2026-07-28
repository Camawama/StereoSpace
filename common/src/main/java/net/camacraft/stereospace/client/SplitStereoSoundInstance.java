package net.camacraft.stereospace.client;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.camacraft.stereospace.api.StereoSoundChannel;

/**
 * One mono half of an automatically split stereo sound. Wraps an arbitrary
 * vanilla-path {@link SoundInstance} (a jukebox record, a block sound, another
 * mod's sound) and mirrors all of its properties, but positions itself
 * billboarded around the original's position so the pair faces the local
 * player. The {@link StereoChannelSound} marker makes the sound engine mixin
 * feed it only its channel's half of the decoded stereo audio.
 * <p>
 * The resolved {@link Sound} is pinned at split time and shared by both
 * halves, so the two channels always play the same sounds.json variant.
 */
public class SplitStereoSoundInstance implements TickableSoundInstance, StereoChannelSound {

	private final SoundInstance original;
	private final WeighedSoundEvents resolved;
	private final Sound sound;
	private final StereoSoundChannel channel;
	private final float spread;

	private boolean stopped;
	private double x;
	private double y;
	private double z;

	public SplitStereoSoundInstance(SoundInstance original, WeighedSoundEvents resolved, Sound sound, StereoSoundChannel channel, float spread) {
		this.original = original;
		this.resolved = resolved;
		this.sound = sound;
		this.channel = channel;
		this.spread = spread;
		this.updatePosition();
	}

	@Override
	public StereoSoundChannel getStereoChannel() {
		return this.channel;
	}

	/**
	 * NeoForge patches an overridable {@code getStream} into SoundInstance and
	 * routes SoundEngine's streaming branch through it, so the mono split is
	 * applied here on that loader. No {@code @Override} - the method does not
	 * exist in vanilla/Fabric mappings, where the SoundSystemMixin wrap on the
	 * vanilla call site does this job instead.
	 */
	public java.util.concurrent.CompletableFuture<net.minecraft.client.sounds.AudioStream> getStream(net.minecraft.client.sounds.SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
		return soundBuffers.getStream(sound.getPath(), looping).thenApply(stream -> StereoChannelAudioStream.wrap(stream, this.channel));
	}

	public void forceStop() {
		this.stopped = true;
	}

	@Override
	public boolean isStopped() {
		if (this.original instanceof TickableSoundInstance tickable && tickable.isStopped()) {
			return true;
		}

		return this.stopped;
	}

	@Override
	public void tick() {
		// The original never reached the engine, so it does not get ticked by
		// vanilla; the LEFT half ticks it (once per pair) so tickable sounds
		// keep their movement/volume logic, then both halves re-billboard
		// around wherever it now is.
		if (this.channel == StereoSoundChannel.LEFT && this.original instanceof TickableSoundInstance tickable && !tickable.isStopped()) {
			tickable.tick();
		}

		this.updatePosition();
	}

	private void updatePosition() {
		Vec3 anchor = new Vec3(this.original.getX(), this.original.getY(), this.original.getZ());
		Vec3 pos = StereoBillboard.channelPosition(anchor, this.channel, this.spread);
		this.x = pos.x;
		this.y = pos.y;
		this.z = pos.z;
	}

	@Override
	public ResourceLocation getLocation() {
		return this.original.getLocation();
	}

	@Override
	public WeighedSoundEvents resolve(SoundManager manager) {
		return this.resolved;
	}

	@Override
	public Sound getSound() {
		return this.sound;
	}

	@Override
	public SoundSource getSource() {
		return this.original.getSource();
	}

	@Override
	public boolean isLooping() {
		return this.original.isLooping();
	}

	@Override
	public boolean isRelative() {
		return this.original.isRelative();
	}

	@Override
	public int getDelay() {
		return this.original.getDelay();
	}

	@Override
	public float getVolume() {
		return this.original.getVolume();
	}

	@Override
	public float getPitch() {
		return this.original.getPitch();
	}

	@Override
	public double getX() {
		return this.x;
	}

	@Override
	public double getY() {
		return this.y;
	}

	@Override
	public double getZ() {
		return this.z;
	}

	@Override
	public Attenuation getAttenuation() {
		return this.original.getAttenuation();
	}

	@Override
	public boolean canStartSilent() {
		return this.original.canStartSilent();
	}

	@Override
	public boolean canPlaySound() {
		return this.original.canPlaySound();
	}
}
