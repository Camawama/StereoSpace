package net.camacraft.stereospace.client;

import net.minecraft.client.sounds.AudioStream;
import net.camacraft.stereospace.api.StereoSoundChannel;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Wraps a decoded stereo {@link AudioStream} and extracts a single channel
 * from the interleaved 16-bit PCM, presenting itself to the sound engine as a
 * MONO stream. This is what lets a single stereo .ogg be split into its left
 * and right halves at runtime: OpenAL never sees the stereo data, only the
 * mono channel, so the source spatializes like any world sound.
 * <p>
 * A stereo 16-bit PCM frame is 4 bytes: [L lo, L hi, R lo, R hi]. The two
 * bytes of the wanted sample are copied verbatim, which keeps the code
 * independent of endianness. Reads from the source are not guaranteed to be
 * frame aligned, so up to one partial frame is carried over between reads.
 */
public class StereoChannelAudioStream implements AudioStream {

	private static final int STEREO_FRAME_SIZE = 4;

	private final AudioStream source;
	private final AudioFormat format;
	private final int sampleOffset;
	private final byte[] carry = new byte[STEREO_FRAME_SIZE];
	private int carryLength;

	private StereoChannelAudioStream(AudioStream source, StereoSoundChannel channel) {
		this.source = source;
		AudioFormat sourceFormat = source.getFormat();
		this.format = new AudioFormat(sourceFormat.getSampleRate(), sourceFormat.getSampleSizeInBits(), 1, true, sourceFormat.isBigEndian());
		this.sampleOffset = channel == StereoSoundChannel.LEFT ? 0 : 2;
	}

	/**
	 * Returns a mono stream carrying only the given channel of {@code source},
	 * or {@code source} unchanged when it is not 16-bit stereo (already-mono
	 * files keep working, they just carry the same audio on both halves).
	 */
	public static AudioStream wrap(AudioStream source, StereoSoundChannel channel) {
		AudioFormat sourceFormat = source.getFormat();

		if (sourceFormat.getChannels() != 2 || sourceFormat.getSampleSizeInBits() != 16) {
			return source;
		}

		return new StereoChannelAudioStream(source, channel);
	}

	@Override
	public AudioFormat getFormat() {
		return format;
	}

	@Override
	public ByteBuffer read(int size) throws IOException {
		// The engine sizes its requests for a mono stream; the source yields
		// twice as many bytes for the same duration.
		ByteBuffer stereo = source.read(Math.max(size, STEREO_FRAME_SIZE) * 2);

		int available = carryLength + stereo.remaining();
		int frames = available / STEREO_FRAME_SIZE;

		byte[] bytes = new byte[available];
		System.arraycopy(carry, 0, bytes, 0, carryLength);
		stereo.get(bytes, carryLength, stereo.remaining());

		ByteBuffer mono = BufferUtils.createByteBuffer(frames * 2);

		for (int frame = 0; frame < frames; frame++) {
			int base = frame * STEREO_FRAME_SIZE + sampleOffset;
			mono.put(bytes[base]);
			mono.put(bytes[base + 1]);
		}

		carryLength = available - frames * STEREO_FRAME_SIZE;
		System.arraycopy(bytes, frames * STEREO_FRAME_SIZE, carry, 0, carryLength);

		mono.flip();
		return mono;
	}

	@Override
	public void close() throws IOException {
		source.close();
	}
}
