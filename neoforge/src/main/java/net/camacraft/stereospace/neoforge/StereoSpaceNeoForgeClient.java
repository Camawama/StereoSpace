package net.camacraft.stereospace.neoforge;

import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.camacraft.stereospace.client.StereoDebugRenderer;
import net.camacraft.stereospace.client.StereoSpaceCommands;

/**
 * Client-only NeoForge hooks, kept in a separate class so the client event
 * classes are never loaded on a dedicated server.
 */
public final class StereoSpaceNeoForgeClient {

	private StereoSpaceNeoForgeClient() {
	}

	public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
		event.getDispatcher().register(StereoSpaceCommands.<CommandSourceStack>build((source, message) -> source.sendSuccess(() -> message, false)));
	}

	public static void onRenderLevelStage(RenderLevelStageEvent event) {
		if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
			StereoDebugRenderer.render(event.getPoseStack(), event.getCamera().getPosition());
		}
	}
}
