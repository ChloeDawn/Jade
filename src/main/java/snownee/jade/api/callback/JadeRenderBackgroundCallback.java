package snownee.jade.api.callback;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.Rect2i;
import snownee.jade.api.Accessor;
import snownee.jade.api.callback.JadeBeforeRenderCallback.ColorSetting;
import snownee.jade.api.ui.ITooltipRenderer;

@FunctionalInterface
public interface JadeRenderBackgroundCallback {

	boolean onRender(ITooltipRenderer tooltip, Rect2i rect, PoseStack matrixStack, Accessor<?> accessor, ColorSetting color);

}
