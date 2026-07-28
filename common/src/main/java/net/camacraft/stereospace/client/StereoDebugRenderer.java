package net.camacraft.stereospace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * In-world debug outlines for the virtual stereo sources, toggled with
 * {@code /stereospace debug}. For every playing stereo pair - auto-split and
 * server-driven alike - a red box marks the LEFT mono source, a green box the
 * RIGHT one, and a yellow box the anchor the pair is billboarded around, with
 * lines connecting the anchor to its two halves. Since the halves re-billboard
 * every tick, the boxes visibly swing around the anchor as the player moves.
 * <p>
 * The loader entrypoints call {@link #render} from their world-render event
 * (after entities), so the outlines are drawn with the level's own pose stack.
 */
public final class StereoDebugRenderer {

	private static final float MARKER_SIZE = 0.5F;
	private static final float ANCHOR_SIZE = 1.04F;

	private static boolean enabled = false;

	private StereoDebugRenderer() {
	}

	public static void setEnabled(boolean enabled) {
		StereoDebugRenderer.enabled = enabled;
	}

	public static boolean isEnabled() {
		return enabled;
	}

	/** One playing stereo pair: the anchor and its two billboarded halves. */
	public record DebugPair(Vec3 anchor, Vec3 left, Vec3 right) {
	}

	public static void render(PoseStack poseStack, Vec3 cameraPos) {
		if (!enabled) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.level == null) {
			return;
		}

		List<DebugPair> pairs = new ArrayList<>();
		AutoStereoSplitter.collectDebugPairs(minecraft.getSoundManager(), pairs);
		ClientStereoSoundManager.collectDebugPairs(minecraft.getSoundManager(), pairs);

		if (pairs.isEmpty()) {
			return;
		}

		MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
		VertexConsumer lines = buffers.getBuffer(RenderType.lines());

		poseStack.pushPose();
		poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

		for (DebugPair pair : pairs) {
			renderMarker(poseStack, lines, pair.anchor(), ANCHOR_SIZE, 1.0F, 1.0F, 0.2F);
			renderMarker(poseStack, lines, pair.left(), MARKER_SIZE, 1.0F, 0.2F, 0.2F);
			renderMarker(poseStack, lines, pair.right(), MARKER_SIZE, 0.2F, 1.0F, 0.2F);
			renderLine(poseStack, lines, pair.anchor(), pair.left(), 1.0F, 0.2F, 0.2F);
			renderLine(poseStack, lines, pair.anchor(), pair.right(), 0.2F, 1.0F, 0.2F);
		}

		poseStack.popPose();
		buffers.endBatch(RenderType.lines());
	}

	private static void renderMarker(PoseStack poseStack, VertexConsumer lines, Vec3 pos, float size, float r, float g, float b) {
		LevelRenderer.renderLineBox(poseStack, lines, AABB.ofSize(pos, size, size, size), r, g, b, 1.0F);
	}

	private static void renderLine(PoseStack poseStack, VertexConsumer lines, Vec3 from, Vec3 to, float r, float g, float b) {
		Vec3 direction = to.subtract(from);
		double length = direction.length();

		if (length < 1.0E-4) {
			return;
		}

		// The lines shader expects the line direction as the vertex normal.
		float nx = (float) (direction.x / length);
		float ny = (float) (direction.y / length);
		float nz = (float) (direction.z / length);
		PoseStack.Pose pose = poseStack.last();

		lines.addVertex(pose, (float) from.x, (float) from.y, (float) from.z).setColor(r, g, b, 1.0F).setNormal(pose, nx, ny, nz);
		lines.addVertex(pose, (float) to.x, (float) to.y, (float) to.z).setColor(r, g, b, 1.0F).setNormal(pose, nx, ny, nz);
	}
}
