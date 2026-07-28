package net.camacraft.stereospace.api;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.camacraft.stereospace.StereoSpace;

/**
 * The three S2C payloads that drive virtual stereo sounds. The loader modules
 * register these (Fabric: PayloadTypeRegistry + ClientPlayNetworking,
 * NeoForge: RegisterPayloadHandlersEvent) and route them into
 * {@code ClientStereoSoundManager} on the client game thread.
 */
public final class StereoSoundPackets {

	private StereoSoundPackets() {
	}

	private static void writeVec3(FriendlyByteBuf buf, Vec3 vec) {
		buf.writeDouble(vec.x);
		buf.writeDouble(vec.y);
		buf.writeDouble(vec.z);
	}

	private static Vec3 readVec3(FriendlyByteBuf buf) {
		return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	/**
	 * Starts a virtual stereo sound on the client: the stereo sound file is
	 * split at runtime into two mono sound instances, one per channel,
	 * anchored at {@code leftPos} and {@code rightPos}.
	 */
	public record PlayStereoSoundPayload(long id, ResourceLocation sound, SoundSource source, Vec3 leftPos, Vec3 rightPos, float volume, float pitch, float spread, boolean looping) implements CustomPacketPayload {

		public static final Type<PlayStereoSoundPayload> TYPE = new Type<>(StereoSpace.id("play_stereo_sound"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PlayStereoSoundPayload> STREAM_CODEC = CustomPacketPayload.codec(PlayStereoSoundPayload::write, PlayStereoSoundPayload::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarLong(id);
			buf.writeResourceLocation(sound);
			buf.writeEnum(source);
			writeVec3(buf, leftPos);
			writeVec3(buf, rightPos);
			buf.writeFloat(volume);
			buf.writeFloat(pitch);
			buf.writeFloat(spread);
			buf.writeBoolean(looping);
		}

		private static PlayStereoSoundPayload read(RegistryFriendlyByteBuf buf) {
			return new PlayStereoSoundPayload(buf.readVarLong(), buf.readResourceLocation(), buf.readEnum(SoundSource.class), readVec3(buf), readVec3(buf), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Moves the anchors of an already-playing virtual stereo sound. The two
	 * channels move independently.
	 */
	public record MoveStereoSoundPayload(long id, Vec3 leftPos, Vec3 rightPos) implements CustomPacketPayload {

		public static final Type<MoveStereoSoundPayload> TYPE = new Type<>(StereoSpace.id("move_stereo_sound"));
		public static final StreamCodec<RegistryFriendlyByteBuf, MoveStereoSoundPayload> STREAM_CODEC = CustomPacketPayload.codec(MoveStereoSoundPayload::write, MoveStereoSoundPayload::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarLong(id);
			writeVec3(buf, leftPos);
			writeVec3(buf, rightPos);
		}

		private static MoveStereoSoundPayload read(RegistryFriendlyByteBuf buf) {
			return new MoveStereoSoundPayload(buf.readVarLong(), readVec3(buf), readVec3(buf));
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Stops both channels of a virtual stereo sound.
	 */
	public record StopStereoSoundPayload(long id) implements CustomPacketPayload {

		public static final Type<StopStereoSoundPayload> TYPE = new Type<>(StereoSpace.id("stop_stereo_sound"));
		public static final StreamCodec<RegistryFriendlyByteBuf, StopStereoSoundPayload> STREAM_CODEC = CustomPacketPayload.codec(StopStereoSoundPayload::write, StopStereoSoundPayload::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarLong(id);
		}

		private static StopStereoSoundPayload read(RegistryFriendlyByteBuf buf) {
			return new StopStereoSoundPayload(buf.readVarLong());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
