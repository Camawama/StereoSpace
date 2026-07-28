package net.camacraft.stereospace;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;

/**
 * Core of Stereo Space. The mod is almost entirely common code; the loader
 * modules only register the packets, forward a handful of server lifecycle
 * events into {@link net.camacraft.stereospace.api.StereoSounds}, and install a
 * packet sender here.
 */
public final class StereoSpace {

	public static final String MOD_ID = "stereospace";
	public static final Logger LOGGER = LoggerFactory.getLogger("StereoSpace");

	private static BiConsumer<ServerPlayer, CustomPacketPayload> packetSender = (player, payload) -> {
		throw new IllegalStateException("Stereo Space packet sender was not installed by the loader module");
	};

	private StereoSpace() {
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	/**
	 * Installed once by the loader entrypoint (Fabric/NeoForge networking).
	 */
	public static void setPacketSender(BiConsumer<ServerPlayer, CustomPacketPayload> sender) {
		packetSender = sender;
	}

	public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
		packetSender.accept(player, payload);
	}
}
