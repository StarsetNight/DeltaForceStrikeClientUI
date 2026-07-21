package org.starset.deltaforcestrikeclientui.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.starset.deltaforcestrikeclientui.client.hud.ClientHudState;

/**
 * 对局 HUD 激活时隐藏原版 boss bar，改由 ClientUI 自绘下包/拆包进度。
 */
@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void dfsCui$hideBossBars(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (ClientHudState.get().active()) {
            ci.cancel();
        }
    }
}
