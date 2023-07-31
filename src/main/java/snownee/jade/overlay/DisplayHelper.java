package snownee.jade.overlay;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import snownee.jade.Jade;
import snownee.jade.api.config.IWailaConfig;
import snownee.jade.api.config.IWailaConfig.IConfigOverlay;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.ui.IDisplayHelper;
import snownee.jade.util.ClientProxy;
import snownee.jade.util.Color;

public class DisplayHelper implements IDisplayHelper {

	public static final DisplayHelper INSTANCE = new DisplayHelper();
	private static final Minecraft CLIENT = Minecraft.getInstance();
	private static final ResourceLocation GUI_ICONS_LOCATION = new ResourceLocation("textures/gui/icons.png");
	//https://github.com/mezz/JustEnoughItems/blob/1.16/src/main/java/mezz/jei/plugins/vanilla/ingredients/fluid/FluidStackRenderer.java
	private static final int TEX_WIDTH = 16;
	private static final int TEX_HEIGHT = 16;
	private static final int MIN_FLUID_HEIGHT = 1; // ensure tiny amounts of fluid are still visible
	private static final Pattern STRIP_COLOR = Pattern.compile("(?i)\u00a7[0-9A-F]");
	public static DecimalFormat dfCommas = new DecimalFormat("##.##");

	static {
		dfCommas.setRoundingMode(RoundingMode.DOWN);
	}

	private static void renderGuiItemDecorations(GuiGraphics guiGraphics, Font font, ItemStack stack, int i, int j, @Nullable String text) {
		if (stack.isEmpty()) {
			return;
		}
		guiGraphics.pose().pushPose();
		if (stack.getCount() != 1 || text != null) {
			String s = text == null ? INSTANCE.humanReadableNumber(stack.getCount(), "", false) : text;
			guiGraphics.pose().pushPose();
			guiGraphics.pose().translate(0.0f, 0.0f, 200.0f);
			guiGraphics.pose().scale(.75f, .75f, .75f);
			INSTANCE.drawText(guiGraphics, s, i + 22 - font.width(s), j + 13, 16777215);
			guiGraphics.pose().popPose();
		}

		if (stack.isBarVisible()) {
			RenderSystem.disableDepthTest();
			int k = stack.getBarWidth();
			int l = stack.getBarColor();
			int m = i + 2;
			int n = j + 13;
			guiGraphics.fill(RenderType.guiOverlay(), m, n, m + 13, n + 2, -16777216);
			guiGraphics.fill(RenderType.guiOverlay(), m, n, m + k, n + 1, l | 0xFF000000);
		}
		guiGraphics.pose().popPose();
		ClientProxy.renderItemDecorationsExtra(guiGraphics, font, stack, i, j, text);
	}

	public static void drawTexturedModalRect(GuiGraphics guiGraphics, float x, float y, int textureX, int textureY, int width, int height, int tw, int th) {
		Matrix4f matrix = guiGraphics.pose().last().pose();
		float f = 0.00390625F;
		float f1 = 0.00390625F;
		float zLevel = 0.0F;
		Tesselator tessellator = Tesselator.getInstance();
		BufferBuilder buffer = tessellator.getBuilder();
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
		buffer.vertex(matrix, x, y + height, zLevel).uv(((textureX) * f), ((textureY + th) * f1)).endVertex();
		buffer.vertex(matrix, x + width, y + height, zLevel).uv(((textureX + tw) * f), ((textureY + th) * f1)).endVertex();
		buffer.vertex(matrix, x + width, y, zLevel).uv(((textureX + tw) * f), ((textureY) * f1)).endVertex();
		buffer.vertex(matrix, x, y, zLevel).uv(((textureX) * f), ((textureY) * f1)).endVertex();
		BufferUploader.drawWithShader(buffer.end());
	}

	public static void renderIcon(GuiGraphics guiGraphics, float x, float y, int sx, int sy, IconUI icon) {
		if (icon == null)
			return;

		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, OverlayRenderer.alpha);
		RenderSystem.setShaderTexture(0, GUI_ICONS_LOCATION);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();

		if (icon.bu != -1)
			DisplayHelper.drawTexturedModalRect(guiGraphics, x, y, icon.bu, icon.bv, sx, sy, icon.bsu, icon.bsv);
		DisplayHelper.drawTexturedModalRect(guiGraphics, x, y, icon.u, icon.v, sx, sy, icon.su, icon.sv);
		RenderSystem.setShaderColor(1, 1, 1, 1);
	}

	private static void setGLColorFromInt(int color) {
		float red = (color >> 16 & 0xFF) / 255.0F;
		float green = (color >> 8 & 0xFF) / 255.0F;
		float blue = (color & 0xFF) / 255.0F;
		float alpha = ((color >> 24) & 0xFF) / 255F;

		RenderSystem.setShaderColor(red, green, blue, alpha);
	}

	private static void drawTextureWithMasking(Matrix4f matrix, float xCoord, float yCoord, TextureAtlasSprite textureSprite, float maskTop, float maskRight, float zLevel) {
		float uMin = textureSprite.getU0();
		float uMax = textureSprite.getU1();
		float vMin = textureSprite.getV0();
		float vMax = textureSprite.getV1();
		uMax = uMax - (maskRight / 16F * (uMax - uMin));
		vMax = vMax - (maskTop / 16F * (vMax - vMin));

		Tesselator tessellator = Tesselator.getInstance();
		BufferBuilder bufferBuilder = tessellator.getBuilder();
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		bufferBuilder.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
		bufferBuilder.vertex(matrix, xCoord, yCoord + 16, zLevel).uv(uMin, vMax).endVertex();
		bufferBuilder.vertex(matrix, xCoord + 16 - maskRight, yCoord + 16, zLevel).uv(uMax, vMax).endVertex();
		bufferBuilder.vertex(matrix, xCoord + 16 - maskRight, yCoord + maskTop, zLevel).uv(uMax, vMin).endVertex();
		bufferBuilder.vertex(matrix, xCoord, yCoord + maskTop, zLevel).uv(uMin, vMin).endVertex();
		BufferUploader.drawWithShader(bufferBuilder.end());
	}

	public static void fill(GuiGraphics guiGraphics, float minX, float minY, float maxX, float maxY, int color) {
		fill(guiGraphics.pose().last().pose(), minX, minY, maxX, maxY, color);
	}

	private static void fill(Matrix4f matrix, float minX, float minY, float maxX, float maxY, int color) {
		if (minX < maxX) {
			float i = minX;
			minX = maxX;
			maxX = i;
		}

		if (minY < maxY) {
			float j = minY;
			minY = maxY;
			maxY = j;
		}

		float f3 = (color >> 24 & 255) / 255.0F * OverlayRenderer.alpha;
		float f = (color >> 16 & 255) / 255.0F;
		float f1 = (color >> 8 & 255) / 255.0F;
		float f2 = (color & 255) / 255.0F;
		BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		bufferbuilder.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		bufferbuilder.vertex(matrix, minX, maxY, 0.0F).color(f, f1, f2, f3).endVertex();
		bufferbuilder.vertex(matrix, maxX, maxY, 0.0F).color(f, f1, f2, f3).endVertex();
		bufferbuilder.vertex(matrix, maxX, minY, 0.0F).color(f, f1, f2, f3).endVertex();
		bufferbuilder.vertex(matrix, minX, minY, 0.0F).color(f, f1, f2, f3).endVertex();
		BufferUploader.drawWithShader(bufferbuilder.end());
		RenderSystem.disableBlend();
	}

	@Override
	public void drawItem(GuiGraphics guiGraphics, float x, float y, ItemStack stack, float scale, @Nullable String text) {
		if (OverlayRenderer.alpha < 0.5F) {
			return;
		}
		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(x, y, 0);
		guiGraphics.pose().scale(scale, scale, scale);
		guiGraphics.renderFakeItem(stack, 0, 0);
		renderGuiItemDecorations(guiGraphics, CLIENT.font, stack, 0, 0, text);
		guiGraphics.pose().popPose();
	}

	@Override
	public void drawGradientRect(GuiGraphics guiGraphics, float left, float top, float width, float height, int startColor, int endColor) {
		drawGradientRect(guiGraphics, left, top, width, height, startColor, endColor, false);
	}

	public void drawGradientRect(GuiGraphics guiGraphics, float left, float top, float width, float height, int startColor, int endColor, boolean horizontal) {
		float zLevel = 0.0F;
		Matrix4f matrix = guiGraphics.pose().last().pose();

		float f = (startColor >> 24 & 255) / 255.0F * OverlayRenderer.alpha;
		float f1 = (startColor >> 16 & 255) / 255.0F;
		float f2 = (startColor >> 8 & 255) / 255.0F;
		float f3 = (startColor & 255) / 255.0F;
		float f4 = (endColor >> 24 & 255) / 255.0F * OverlayRenderer.alpha;
		float f5 = (endColor >> 16 & 255) / 255.0F;
		float f6 = (endColor >> 8 & 255) / 255.0F;
		float f7 = (endColor & 255) / 255.0F;
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		Tesselator tessellator = Tesselator.getInstance();
		BufferBuilder buffer = tessellator.getBuilder();
		buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		if (horizontal) {
			buffer.vertex(matrix, left + width, top, zLevel).color(f5, f6, f7, f4).endVertex();
			buffer.vertex(matrix, left, top, zLevel).color(f1, f2, f3, f).endVertex();
			buffer.vertex(matrix, left, top + height, zLevel).color(f1, f2, f3, f).endVertex();
			buffer.vertex(matrix, left + width, top + height, zLevel).color(f5, f6, f7, f4).endVertex();
		} else {
			buffer.vertex(matrix, left + width, top, zLevel).color(f1, f2, f3, f).endVertex();
			buffer.vertex(matrix, left, top, zLevel).color(f1, f2, f3, f).endVertex();
			buffer.vertex(matrix, left, top + height, zLevel).color(f5, f6, f7, f4).endVertex();
			buffer.vertex(matrix, left + width, top + height, zLevel).color(f5, f6, f7, f4).endVertex();
		}
		BufferUploader.drawWithShader(buffer.end());
		RenderSystem.disableBlend();
	}

	@Override
	public void drawBorder(GuiGraphics guiGraphics, float minX, float minY, float maxX, float maxY, float width, int color, boolean corner) {
		fill(guiGraphics, minX + width, minY, maxX - width, minY + width, color);
		fill(guiGraphics, minX + width, maxY - width, maxX - width, maxY, color);
		if (corner) {
			fill(guiGraphics, minX, minY, minX + width, maxY, color);
			fill(guiGraphics, maxX - width, minY, maxX, maxY, color);
		} else {
			fill(guiGraphics, minX, minY + width, minX + width, maxY - width, color);
			fill(guiGraphics, maxX - width, minY + width, maxX, maxY - width, color);
		}
	}

	public void drawFluid(GuiGraphics guiGraphics, final float xPosition, final float yPosition, JadeFluidObject fluid, float width, float height, long capacityMb) {
		if (fluid.isEmpty()) {
			return;
		}

		long amount = JadeFluidObject.bucketVolume();
		MutableFloat scaledAmount = new MutableFloat((amount * height) / capacityMb);
		if (amount > 0 && scaledAmount.floatValue() < MIN_FLUID_HEIGHT) {
			scaledAmount.setValue(MIN_FLUID_HEIGHT);
		}
		if (scaledAmount.floatValue() > height) {
			scaledAmount.setValue(height);
		}

		ClientProxy.getFluidSpriteAndColor(fluid, (sprite, color) -> {
			if (sprite == null) {
				float maxY = yPosition + height;
				if (color == -1) {
					color = 0xAAAAAAAA;
				}
				fill(guiGraphics, xPosition, maxY - scaledAmount.floatValue(), xPosition + width, maxY, color);
			} else {
				if (OverlayRenderer.alpha != 1) {
					color = IWailaConfig.IConfigOverlay.applyAlpha(color, OverlayRenderer.alpha);
				}
				drawTiledSprite(guiGraphics, xPosition, yPosition, width, height, color, scaledAmount.floatValue(), sprite);
			}
		});
	}

	private void drawTiledSprite(GuiGraphics guiGraphics, final float xPosition, final float yPosition, final float tiledWidth, final float tiledHeight, int color, float scaledAmount, TextureAtlasSprite sprite) {
		RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
		Matrix4f matrix = guiGraphics.pose().last().pose();
		setGLColorFromInt(color);
		RenderSystem.enableBlend();

		final int xTileCount = (int) (tiledWidth / TEX_WIDTH);
		final float xRemainder = tiledWidth - (xTileCount * TEX_WIDTH);
		final int yTileCount = (int) (scaledAmount / TEX_HEIGHT);
		final float yRemainder = scaledAmount - (yTileCount * TEX_HEIGHT);

		final float yStart = yPosition + tiledHeight;

		for (int xTile = 0; xTile <= xTileCount; xTile++) {
			for (int yTile = 0; yTile <= yTileCount; yTile++) {
				float width = (xTile == xTileCount) ? xRemainder : TEX_WIDTH;
				float height = (yTile == yTileCount) ? yRemainder : TEX_HEIGHT;
				float x = xPosition + (xTile * TEX_WIDTH);
				float y = yStart - ((yTile + 1) * TEX_HEIGHT);
				if (width > 0 && height > 0) {
					float maskTop = TEX_HEIGHT - height;
					float maskRight = TEX_WIDTH - width;

					drawTextureWithMasking(matrix, x, y, sprite, maskTop, maskRight, 0);
				}
			}
		}
		RenderSystem.setShaderColor(1, 1, 1, 1);
		RenderSystem.disableBlend();
	}

	// https://programming.guide/worlds-most-copied-so-snippet.html
	@Override
	public String humanReadableNumber(double number, String unit, boolean milli) {
		StringBuilder sb = new StringBuilder();
		boolean n = number < 0;
		if (n) {
			number = -number;
			sb.append('-');
		}
		if (milli && number >= 1000) {
			number /= 1000;
			milli = false;
		}
		if (number < 1000) {
			sb.append(dfCommas.format(number));
			if (milli && number != 0) {
				sb.append('m');
			}
		} else {
			int exp = (int) (Math.log10(number) / 3);
			if (exp > 7)
				exp = 7;
			char pre = "kMGTPEZ".charAt(exp - 1);
			sb.append(dfCommas.format(number / Math.pow(1000, exp)));
			sb.append(pre);
		}
		sb.append(unit);
		return sb.toString();
	}

	@Override
	public void drawText(GuiGraphics guiGraphics, String text, float x, float y, int color) {
		drawText(guiGraphics, Component.literal(text), x, y, color);
	}

	@Override
	public void drawText(GuiGraphics guiGraphics, FormattedText text, float x, float y, int color) {
		FormattedCharSequence sequence;
		if (text instanceof Component component) {
			sequence = component.getVisualOrderText();
		} else {
			sequence = Language.getInstance().getVisualOrder(text);
		}
		drawText(guiGraphics, sequence, x, y, color);
	}

	@Override
	public void drawText(GuiGraphics guiGraphics, FormattedCharSequence text, float x, float y, int color) {
		boolean shadow = Jade.CONFIG.get().getOverlay().getTheme().textShadow;
		if (OverlayRenderer.alpha != 1) {
			color = IConfigOverlay.applyAlpha(color, OverlayRenderer.alpha);
		}
		guiGraphics.drawString(CLIENT.font, text, (int) x, (int) y, color, shadow);
	}

	public void drawGradientProgress(GuiGraphics guiGraphics, float left, float top, float width, float height, float progress, int progressColor) {
		Color color = Color.rgb(progressColor);
		Color highlight = Color.hsl(color.getHue(), color.getSaturation(), Math.min(color.getLightness() + 0.2, 1), color.getOpacity());
		if (progress < 0.1F) {
			drawGradientRect(guiGraphics, left, top, width * progress, height, progressColor, highlight.toInt(), true);
		} else {
			float hlWidth = width * 0.1F;
			float normalWidth = width * progress - hlWidth;
			fill(guiGraphics, left, top, left + normalWidth, top + height, progressColor);
			drawGradientRect(guiGraphics, left + normalWidth, top, hlWidth, height, progressColor, highlight.toInt(), true);
		}
	}

	@Override
	public MutableComponent stripColor(Component component) {
		MutableComponent mutableComponent = Component.empty();
		component.visit((style, string) -> {
			if (!string.isEmpty()) {
				MutableComponent literal = Component.literal(STRIP_COLOR.matcher(string).replaceAll(""));
				literal.withStyle(style.withColor((TextColor) null));
				mutableComponent.append(literal);
			}
			return Optional.empty();
		}, Style.EMPTY);
		return mutableComponent;
	}
}
