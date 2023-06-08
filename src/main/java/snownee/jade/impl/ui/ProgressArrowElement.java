package snownee.jade.impl.ui;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import snownee.jade.Jade;
import snownee.jade.api.ui.Element;
import snownee.jade.overlay.DisplayHelper;

public class ProgressArrowElement extends Element {

	private static final ResourceLocation SHEET = new ResourceLocation(Jade.MODID, "textures/sprites.png");

	private final float progress;

	public ProgressArrowElement(float progress) {
		this.progress = progress;
	}

	@Override
	public Vec2 getSize() {
		return new Vec2(26, 16);
	}

	@Override
	public void render(GuiGraphics guiGraphics, float x, float y, float maxX, float maxY) {
		RenderSystem.setShaderTexture(0, SHEET);
		RenderSystem.enableBlend();

		// Draws the "empty" background arrow
		DisplayHelper.drawTexturedModalRect(guiGraphics, x + 2, y, 0, 16, 22, 16, 22, 16);

		if (progress > 0) {
			int progress = (int) (this.progress * 22);
			// Draws the "full" foreground arrow based on the progress
			DisplayHelper.drawTexturedModalRect(guiGraphics, x + 2, y, 0, 0, progress + 1, 16, progress + 1, 16);
		}
	}
}
