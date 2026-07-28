package net.camacraft.stereospace.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.camacraft.stereospace.api.StereoSoundChannel;
import org.joml.Vector3f;

/**
 * The math that keeps a virtual stereo pair facing the local player. Given a
 * channel's anchor, returns the world position the channel should emit from
 * this tick: offset perpendicular to the listener-to-anchor line so the left
 * channel is always virtually to the player's left and the right channel to
 * their right.
 */
public final class StereoBillboard {

	private StereoBillboard() {
	}

	public static Vec3 channelPosition(Vec3 anchor, StereoSoundChannel channel, float spread) {
		Minecraft minecraft = Minecraft.getInstance();
		Camera camera = minecraft.gameRenderer == null ? null : minecraft.gameRenderer.getMainCamera();
		boolean cameraReady = camera != null && camera.isInitialized();

		Vec3 listener;

		if (cameraReady) {
			listener = camera.getPosition();
		} else if (minecraft.player != null) {
			listener = minecraft.player.position();
		} else {
			return anchor;
		}

		double offset = spread / 2.0 * channel.direction();
		Vec3 toAnchor = new Vec3(anchor.x - listener.x, 0.0, anchor.z - listener.z);

		if (toAnchor.lengthSqr() < 1.0E-4) {
			// The listener is (horizontally) on top of the anchor, so there is
			// no facing direction; fall back to the camera's own orientation.
			if (cameraReady) {
				Vector3f left = camera.getLeftVector();
				return anchor.add(-left.x() * offset, 0.0, -left.z() * offset);
			}

			return anchor;
		}

		Vec3 direction = toAnchor.normalize();
		Vec3 right = new Vec3(-direction.z, 0.0, direction.x);
		return anchor.add(right.scale(offset));
	}
}
