package snownee.jade.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import snownee.jade.Jade;
import snownee.jade.impl.config.PluginConfig;

public class HomeConfigScreen extends Screen {

	private final Screen parent;

	public HomeConfigScreen(Screen parent) {
		super(Component.translatable("gui.jade.configuration", Jade.NAME));
		this.parent = parent;
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.translatable("gui.jade.jade_settings", Jade.NAME), w -> {
			minecraft.setScreen(new WailaConfigScreen(HomeConfigScreen.this));
		}).bounds(width / 2 - 105, height / 2 - 10, 100, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.jade.plugin_settings"), w -> {
			minecraft.setScreen(new PluginsConfigScreen(HomeConfigScreen.this));
		}).bounds(width / 2 + 5, height / 2 - 10, 100, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), w -> {
			Jade.CONFIG.save();
			PluginConfig.INSTANCE.save();
			minecraft.setScreen(parent);
		}).bounds(width / 2 - 50, height / 2 + 20, 100, 20).build());
	}

	@Override
	public void render(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
		renderBackground(guiGraphics);
		guiGraphics.drawCenteredString(font, title, (int) (width * .5F), height / 3, 16777215);
		super.render(guiGraphics, x, y, partialTicks);
		guiGraphics.drawCenteredString(font, "§b❄§r Made with §c❤§r by Snownee §b❄", (int) (width * .5F), (int) (height * .75F), 0x55FFFFFF);
	}
}
