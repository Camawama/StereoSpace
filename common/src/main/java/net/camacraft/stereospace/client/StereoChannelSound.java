package net.camacraft.stereospace.client;

import net.camacraft.stereospace.api.StereoSoundChannel;

/**
 * Marks a sound instance as one half of a virtual stereo pair. The sound
 * engine mixin uses this to swap the instance's audio data for the mono
 * split of its channel, and to avoid re-splitting an already split instance.
 */
public interface StereoChannelSound {

	StereoSoundChannel getStereoChannel();
}
