package net.camacraft.stereospace.client;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;

/**
 * The loader-neutral {@code /stereospace} client command tree, controlling the
 * {@link AutoStereoSplitter}'s runtime settings:
 * <ul>
 * <li>{@code /stereospace} - show current settings</li>
 * <li>{@code /stereospace spread} - show the current channel separation</li>
 * <li>{@code /stereospace spread <blocks>} - set the distance in blocks between
 * the left and right mono sources; takes effect immediately, including on
 * sounds that are already playing</li>
 * <li>{@code /stereospace on | off} - enable/disable auto-splitting for sounds
 * started from then on</li>
 * <li>{@code /stereospace reset} - restore the default spread</li>
 * <li>{@code /stereospace debug [on|off]} - toggle the in-world source
 * outlines</li>
 * </ul>
 * The tree is generic over the command source because each loader has its own
 * client command source type (Fabric's {@code FabricClientCommandSource},
 * NeoForge's {@code CommandSourceStack}); the loader entrypoints supply the
 * matching feedback callback when registering.
 */
public final class StereoSpaceCommands {

	private StereoSpaceCommands() {
	}

	public static <S> LiteralArgumentBuilder<S> build(BiConsumer<S, Component> feedback) {
		return LiteralArgumentBuilder.<S>literal("stereospace")
				.executes(context -> {
					feedback.accept(context.getSource(), status());
					return 1;
				})
				.then(LiteralArgumentBuilder.<S>literal("spread")
						.executes(context -> {
							feedback.accept(context.getSource(), Component.literal("Stereo spread is " + format(AutoStereoSplitter.getSpread()) + " blocks"));
							return 1;
						})
						.then(RequiredArgumentBuilder.<S, Float>argument("blocks", FloatArgumentType.floatArg(AutoStereoSplitter.MIN_SPREAD, AutoStereoSplitter.MAX_SPREAD))
								.executes(context -> {
									AutoStereoSplitter.setSpread(FloatArgumentType.getFloat(context, "blocks"));
									feedback.accept(context.getSource(), Component.literal("Stereo spread set to " + format(AutoStereoSplitter.getSpread()) + " blocks"));
									return 1;
								})))
				.then(LiteralArgumentBuilder.<S>literal("on")
						.executes(context -> {
							AutoStereoSplitter.setEnabled(true);
							feedback.accept(context.getSource(), Component.literal("Stereo splitting enabled"));
							return 1;
						}))
				.then(LiteralArgumentBuilder.<S>literal("off")
						.executes(context -> {
							AutoStereoSplitter.setEnabled(false);
							feedback.accept(context.getSource(), Component.literal("Stereo splitting disabled (already-playing sounds keep going)"));
							return 1;
						}))
				.then(LiteralArgumentBuilder.<S>literal("reset")
						.executes(context -> {
							AutoStereoSplitter.setSpread(AutoStereoSplitter.DEFAULT_SPREAD);
							feedback.accept(context.getSource(), Component.literal("Stereo spread reset to " + format(AutoStereoSplitter.getSpread()) + " blocks"));
							return 1;
						}))
				.then(LiteralArgumentBuilder.<S>literal("debug")
						.executes(context -> {
							StereoDebugRenderer.setEnabled(!StereoDebugRenderer.isEnabled());
							feedback.accept(context.getSource(), debugStatus());
							return 1;
						})
						.then(LiteralArgumentBuilder.<S>literal("on")
								.executes(context -> {
									StereoDebugRenderer.setEnabled(true);
									feedback.accept(context.getSource(), debugStatus());
									return 1;
								}))
						.then(LiteralArgumentBuilder.<S>literal("off")
								.executes(context -> {
									StereoDebugRenderer.setEnabled(false);
									feedback.accept(context.getSource(), debugStatus());
									return 1;
								})));
	}

	private static Component status() {
		return Component.literal("Stereo Space: splitting " + (AutoStereoSplitter.isEnabled() ? "enabled" : "disabled") + ", spread " + format(AutoStereoSplitter.getSpread()) + " blocks, debug outlines " + (StereoDebugRenderer.isEnabled() ? "shown" : "hidden"));
	}

	private static Component debugStatus() {
		return Component.literal(StereoDebugRenderer.isEnabled()
				? "Stereo debug outlines shown (red = left source, green = right source, yellow = anchor)"
				: "Stereo debug outlines hidden");
	}

	private static String format(float value) {
		return value == (long) value ? Long.toString((long) value) : Float.toString(value);
	}
}
