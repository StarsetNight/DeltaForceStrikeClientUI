package org.starset.deltaforcestrikeclientui.mixin.client;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.starset.deltaforcestrikeclientui.client.hud.ClientHudState;

/**
 * 对局 HUD 激活时忽略服务端回合胜/负音效（ui.toast.challenge_complete / entity.villager.no）。
 */
@Mixin(SoundManager.class)
public class SoundManagerMixin {

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void dfsCui$suppressRoundResultSounds(
            SoundInstance sound,
            CallbackInfoReturnable<SoundEngine.PlayResult> cir
    ) {
        if (sound == null || !ClientHudState.get().active()) {
            return;
        }
        Identifier id = sound.getIdentifier();
        if (id == null) {
            return;
        }
        String path = id.getPath();
        if ("ui.toast.challenge_complete".equals(path) || "entity.villager.no".equals(path)) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        }
    }
}
