package net.camacraft.stereospace.api;

/**
 * The two halves of a virtual stereo sound. Each channel is played as its own
 * mono sound source in the world, offset perpendicular to the line between the
 * listener and the channel's anchor position, so that the left channel is
 * always virtually to the listener's left and the right channel to their right.
 */
public enum StereoSoundChannel {
	LEFT(-1),
	RIGHT(1);

	private final int direction;

	StereoSoundChannel(int direction) {
		this.direction = direction;
	}

	/**
	 * -1 for the left channel, +1 for the right channel. Multiplied against the
	 * listener-relative "right" vector to place the channel in the world.
	 */
	public int direction() {
		return direction;
	}
}
