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

	private SplitStereoSoundInstance sibling;
	private boolean stopped;
	private double x;
	private double y;
	private double z;

	public SplitStereoSoundInstance(SoundInstance original, WeighedSoundEvents resolved, Sound sound, StereoSoundChannel channel) {
		this.original = original;
		this.resolved = resolved;
		this.sound = sound;
		this.channel = channel;
		this.updatePosition();
	}

	/** The other half of the pair, set right after both halves are built. */
	void setSibling(SplitStereoSoundInstance sibling) {
		this.sibling = sibling;
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

	/** The position of the wrapped original, i.e. where the sound "is". */
	public Vec3 getAnchor() {
		return new Vec3(this.original.getX(), this.original.getY(), this.original.getZ());
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
		// vanilla; exactly one half ticks it (LEFT normally, RIGHT if the left
		// half died early) so tickable sounds keep their movement/volume
		// logic - this is what makes entity-bound sounds follow their entity
		// after the split - then both halves re-billboard around wherever the
		// original now is.
		boolean ticksOriginal = this.channel == StereoSoundChannel.LEFT
				? !this.isStopped()
				: this.sibling != null && this.sibling.isStopped() && !this.isStopped();

		if (ticksOriginal && this.original instanceof TickableSoundInstance tickable && !tickable.isStopped()) {
			tickable.tick();
		}

		this.updatePosition();
	}

	private void updatePosition() {
		// The spread is read live rather than pinned at split time, so
		// /stereospace spread changes are heard on already-playing sounds.
		Vec3 anchor = new Vec3(this.original.getX(), this.original.getY(), this.original.getZ());
		Vec3 pos = StereoBillboard.channelPosition(anchor, this.channel, AutoStereoSplitter.getSpread());
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
