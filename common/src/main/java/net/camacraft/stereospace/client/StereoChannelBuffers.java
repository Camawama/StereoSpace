package net.camacraft.stereospace.client;

import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.camacraft.stereospace.api.StereoSoundChannel;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime channel splitting for NON-streamed ("stream": false) sounds, which
 * the engine plays from a complete in-memory {@link SoundBuffer}. A stereo
 * file is decoded once, de-interleaved into two mono PCM buffers, and both
 * halves are cached per file, mirroring vanilla's own complete-buffer cache.
 * <p>
 * The cache is dropped on sound-engine reload; the AL context (and with it
 * every AL buffer this cache produced) is destroyed by the reload itself, so
 * no AL cleanup is needed here.
 */
public final class StereoChannelBuffers {

	private static final int STEREO_FRAME_SIZE = 4;
	private static final int READ_CHUNK_SIZE = 32768;

	private static final Map<ResourceLocation, CompletableFuture<SoundBuffer[]>> CACHE = new ConcurrentHashMap<>();

	private StereoChannelBuffers() {
	}

	/**
	 * Returns the mono {@link SoundBuffer} holding one channel of the given
	 * sound file, decoding and splitting the file if it is not cached yet.
	 */
	public static CompletableFuture<SoundBuffer> getChannelBuffer(SoundBufferLibrary library, ResourceLocation path, StereoSoundChannel channel) {
		return CACHE.computeIfAbsent(path, key -> library.getStream(key, false).thenApply(StereoChannelBuffers::decodeAndSplit))
				.thenApply(buffers -> buffers[channel == StereoSoundChannel.LEFT ? 0 : 1]);
	}

	/**
	 * Called when the sound engine reloads. The reload destroys the AL context
	 * and every buffer with it, so the stale handles are simply forgotten.
	 */
	public static void dropCache() {
		CACHE.clear();
	}

	private static SoundBuffer[] decodeAndSplit(AudioStream stream) {
		try (stream) {
			ByteBuffer data = readAll(stream);
			AudioFormat format = stream.getFormat();

			if (format.getChannels() != 2 || format.getSampleSizeInBits() != 16) {
				// Not splittable; play the same buffer on both halves.
				SoundBuffer buffer = new SoundBuffer(data, format);
				return new SoundBuffer[] { buffer, buffer };
			}

			int frames = data.remaining() / STEREO_FRAME_SIZE;
			ByteBuffer left = BufferUtils.createByteBuffer(frames * 2);
			ByteBuffer right = BufferUtils.createByteBuffer(frames * 2);
			int start = data.position();

			for (int frame = 0; frame < frames; frame++) {
				int base = start + frame * STEREO_FRAME_SIZE;
				left.put(data.get(base));
				left.put(data.get(base + 1));
				right.put(data.get(base + 2));
				right.put(data.get(base + 3));
			}

			left.flip();
			right.flip();

			AudioFormat mono = new AudioFormat(format.getSampleRate(), format.getSampleSizeInBits(), 1, true, format.isBigEndian());
			return new SoundBuffer[] { new SoundBuffer(left, mono), new SoundBuffer(right, mono) };
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static ByteBuffer readAll(AudioStream stream) throws IOException {
		ByteBuffer total = BufferUtils.createByteBuffer(READ_CHUNK_SIZE);

		while (true) {
			ByteBuffer chunk = stream.read(READ_CHUNK_SIZE);

			if (!chunk.hasRemaining()) {
				break;
			}

			if (total.remaining() < chunk.remaining()) {
				ByteBuffer grown = BufferUtils.createByteBuffer(Math.max(total.capacity() * 2, total.position() + chunk.remaining()));
				total.flip();
				grown.put(total);
				total = grown;
			}

			total.put(chunk);
		}

		total.flip();
		return total;
	}
}
