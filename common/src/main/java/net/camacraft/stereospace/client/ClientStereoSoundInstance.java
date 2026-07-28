package net.camacraft.stereospace.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.camacraft.stereospace.api.StereoSoundChannel;

/**
 * One mono half of a server-driven virtual stereo sound. Every tick the
 * instance re-derives its world position from its anchor and this client's
 * listener position: the pair of instances always "faces" the local player,
 * with the left channel offset to the player's virtual left of its anchor and
 * the right channel to the virtual right. Since this runs locally on every
 * client, each player hears their own correctly oriented stereo pair.
 * <p>
 * The instance points at the stereo sound file itself; the sound engine mixin
 * recognizes {@link StereoChannelSound} and feeds it only its channel's half
 * of the decoded audio.
 */
public class ClientStereoSoundInstance extends AbstractTickableSoundInstance implements StereoChannelSound {

	private final StereoSoundChannel channel;
	private final float spread;
	private Vec3 anchor;

	/**
	 * @param seed both halves of one stereo pair must be built with the same
	 *             seed so they resolve the same sounds.json variant and sample
	 *             identical volume/pitch randomness, keeping the two channels
	 *             sample-locked
	 */
	public ClientStereoSoundInstance(SoundEvent sound, SoundSource source, StereoSoundChannel channel, Vec3 anchor, float volume, float pitch, float spread, boolean looping, long seed) {
		super(sound, source, RandomSource.create(seed));
		this.channel = channel;
		this.anchor = anchor;
		this.spread = spread;
		this.volume = volume;
		this.pitch = pitch;
		this.looping = looping;
		this.delay = 0;
		this.attenuation = Attenuation.LINEAR;
		this.updatePosition();
	}

	@Override
	public boolean canStartSilent() {
		// Looping sounds must start even when out of earshot so they tick and
		// become audible once the player approaches.
		return this.looping;
	}

	@Override
	public void tick() {
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
	public java.util.concurrent.CompletableFuture<net.minecraft.client.sounds.AudioStream> getStream(net.minecraft.client.sounds.SoundBufferLibrary soundBuffers, net.minecraft.client.resources.sounds.Sound sound, boolean looping) {
		return soundBuffers.getStream(sound.getPath(), looping).thenApply(stream -> StereoChannelAudioStream.wrap(stream, this.channel));
	}

	public void setAnchor(Vec3 anchor) {
		this.anchor = anchor;
	}

	public void forceStop() {
		this.stop();
	}

	private void updatePosition() {
		Vec3 pos = StereoBillboard.channelPosition(this.anchor, this.channel, this.spread);
		this.x = pos.x;
		this.y = pos.y;
		this.z = pos.z;
	}
}
