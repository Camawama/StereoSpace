package net.camacraft.stereospace.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.camacraft.stereospace.client.AutoStereoSplitter;
import net.camacraft.stereospace.client.StereoChannelAudioStream;
import net.camacraft.stereospace.client.StereoChannelBuffers;
import net.camacraft.stereospace.client.StereoChannelSound;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {

	@Shadow
	@Final
	private SoundManager soundManager;

	/**
	 * Automatic stereo splitting: any positional sound backed by a stereo file
	 * is replaced by a pair of mono channel halves before it reaches OpenAL.
	 * This is what makes jukebox records and other vanilla-path sounds
	 * positional without going through the StereoSounds server API.
	 */
	@Inject(method = "play", at = @At("HEAD"), cancellable = true)
	private void stereospace$autoSplitStereo(SoundInstance soundInstance, CallbackInfo ci) {
		if (AutoStereoSplitter.trySplit((SoundEngine) (Object) this, this.soundManager, soundInstance)) {
			ci.cancel();
		}
	}

	/**
	 * Callers stop auto-split sounds via the ORIGINAL instance, which the
	 * engine never actually played; forward the stop to the two halves.
	 */
	@Inject(method = "stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"))
	private void stereospace$stopSplitStereo(SoundInstance soundInstance, CallbackInfo ci) {
		AutoStereoSplitter.onStopped((SoundEngine) (Object) this, soundInstance);
	}

	/**
	 * Runtime stereo splitting, non-streamed path: a virtual stereo channel
	 * instance gets a mono SoundBuffer holding only its half of the stereo
	 * file instead of the shared complete (stereo) buffer.
	 */
	@WrapOperation(method = "play", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundBufferLibrary;getCompleteBuffer(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/concurrent/CompletableFuture;"))
	private static CompletableFuture<SoundBuffer> stereospace$splitStaticStereo(SoundBufferLibrary library, ResourceLocation path, Operation<CompletableFuture<SoundBuffer>> original, @Local(argsOnly = true) SoundInstance soundInstance) {
		if (soundInstance instanceof StereoChannelSound stereo) {
			return StereoChannelBuffers.getChannelBuffer(library, path, stereo.getStereoChannel());
		}

		return original.call(library, path);
	}

	/**
	 * Runtime stereo splitting, streamed path: the decoded stereo stream is
	 * wrapped so the engine only ever sees a mono stream of the instance's
	 * channel.
	 * <p>
	 * This call site only exists on Fabric ({@code require = 0}): NeoForge
	 * patches the streaming branch to go through the overridable
	 * {@code SoundInstance.getStream(...)} extension method instead, which the
	 * stereo instance classes implement themselves.
	 */
	@WrapOperation(method = "play", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundBufferLibrary;getStream(Lnet/minecraft/resources/ResourceLocation;Z)Ljava/util/concurrent/CompletableFuture;"), require = 0)
	private static CompletableFuture<AudioStream> stereospace$splitStreamedStereo(SoundBufferLibrary library, ResourceLocation path, boolean looping, Operation<CompletableFuture<AudioStream>> original, @Local(argsOnly = true) SoundInstance soundInstance) {
		if (soundInstance instanceof StereoChannelSound stereo) {
			return original.call(library, path, looping).thenApply(stream -> StereoChannelAudioStream.wrap(stream, stereo.getStereoChannel()));
		}

		return original.call(library, path, looping);
	}

	/**
	 * Sound files may have changed and every AL buffer died with the reloaded
	 * AL context; drop the split caches.
	 */
	@Inject(method = "reload()V", at = @At("TAIL"))
	private void stereospace$reloadSounds(CallbackInfo ci) {
		StereoChannelBuffers.dropCache();
		AutoStereoSplitter.clear();
	}
}
