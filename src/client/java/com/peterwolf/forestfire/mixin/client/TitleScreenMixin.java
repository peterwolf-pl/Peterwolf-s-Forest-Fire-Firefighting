package com.peterwolf.forestfire.mixin.client;

import com.peterwolf.forestfire.ForestFireMod;
import com.peterwolf.forestfire.config.ForestFireConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Displays the firefighter dedication on the title screen.
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void forestfire$dedication(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (!ForestFireConfig.get().showDedicationOnTitle) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		int x = 8;
		int y = graphics.guiHeight() - 24;
		graphics.text(mc.font, Component.literal(ForestFireMod.DEDICATION), x, y, 0xFFFFCC66, true);
	}
}
