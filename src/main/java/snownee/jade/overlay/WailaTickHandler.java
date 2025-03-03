package snownee.jade.overlay;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.mojang.text2speech.Narrator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiSpriteManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.core.Direction;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import snownee.jade.Jade;
import snownee.jade.api.Accessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.Identifiers;
import snownee.jade.api.callback.JadeRayTraceCallback;
import snownee.jade.api.callback.JadeTooltipCollectedCallback;
import snownee.jade.api.config.IWailaConfig;
import snownee.jade.api.config.IWailaConfig.DisplayMode;
import snownee.jade.api.config.IWailaConfig.IConfigGeneral;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.theme.Theme;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElement;
import snownee.jade.gui.BaseOptionsScreen;
import snownee.jade.impl.ObjectDataCenter;
import snownee.jade.impl.Tooltip;
import snownee.jade.impl.WailaClientRegistration;
import snownee.jade.impl.WailaCommonRegistration;
import snownee.jade.impl.ui.BoxElement;
import snownee.jade.util.ClientProxy;

public class WailaTickHandler {

	private static final Supplier<Narrator> NARRATOR = Suppliers.memoize(Narrator::getNarrator);
	private static WailaTickHandler INSTANCE = new WailaTickHandler();
	private static String lastNarration = "";
	private static long lastNarrationTime = 0;
	public BoxElement rootElement;
	public ProgressTracker progressTracker = new ProgressTracker();

	public static WailaTickHandler instance() {
		if (INSTANCE == null)
			INSTANCE = new WailaTickHandler();
		return INSTANCE;
	}

	public static void narrate(ITooltip tooltip, boolean dedupe) {
		if (!NARRATOR.get().active() || tooltip.isEmpty())
			return;
		if (System.currentTimeMillis() - lastNarrationTime < 500) {
			return;
		}
		String narration = tooltip.getMessage();
		if (dedupe && narration.equals(lastNarration)) {
			return;
		}
		CompletableFuture.runAsync(() -> {
			Narrator narrator = NARRATOR.get();
			narrator.clear();
			narrator.say(StringUtil.stripColor(narration), false);
		});
		lastNarration = narration;
		lastNarrationTime = System.currentTimeMillis();
	}

	public static void clearLastNarration() {
		lastNarration = "";
	}

	public void tickClient() {
		progressTracker.tick();

		IConfigGeneral config = Jade.CONFIG.get().getGeneral();
		if (!config.shouldDisplayTooltip()) {
			rootElement = null;
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (!ClientProxy.shouldShowWithOverlay(client, client.screen)) {
			return;
		}

		Level world = client.level;
		Player player = client.player;
		if (world == null || player == null) {
			rootElement = null;
			return;
		}

		RayTracing.INSTANCE.fire();
		HitResult target = RayTracing.INSTANCE.getTarget();

		Tooltip tooltip = new Tooltip();

		if (target == null) {
			rootElement = null;
			return;
		}

		Accessor<?> accessor = null;
		if (target instanceof BlockHitResult blockTarget && blockTarget.getType() != HitResult.Type.MISS) {
			BlockState state = world.getBlockState(blockTarget.getBlockPos());
			BlockEntity tileEntity = world.getBlockEntity(blockTarget.getBlockPos());
			/* off */
			accessor = WailaClientRegistration.instance().blockAccessor()
					.blockState(state)
					.blockEntity(tileEntity)
					.hit(blockTarget)
					.build();
			/* on */
		} else if (target instanceof EntityHitResult entityTarget) {
			/* off */
			accessor = WailaClientRegistration.instance().entityAccessor()
					.hit(entityTarget)
					.entity(entityTarget.getEntity())
					.build();
			/* on */
		} else if (client.screen instanceof BaseOptionsScreen) {
			/* off */
			accessor = WailaClientRegistration.instance().blockAccessor()
					.blockState(Blocks.GRASS_BLOCK.defaultBlockState())
					.hit(new BlockHitResult(player.position(), Direction.UP, player.blockPosition(), false))
					.build();
			/* on */
		}

		Accessor<?> originalAccessor = accessor;
		for (JadeRayTraceCallback callback : WailaClientRegistration.instance().rayTraceCallback.callbacks()) {
			accessor = callback.onRayTrace(target, accessor, originalAccessor);
		}
		ObjectDataCenter.set(accessor);
		if (accessor == null || accessor.getHitResult() == null) {
			rootElement = null;
			return;
		}

		var handler = WailaClientRegistration.instance().getAccessorHandler(accessor.getAccessorType());
		if (!handler.shouldDisplay(accessor)) {
			rootElement = null;
			return;
		}
		if (accessor.isServerConnected()) {
			boolean request = handler.shouldRequestData(accessor);
			if (ObjectDataCenter.isTimeElapsed(ObjectDataCenter.rateLimiter)) {
				ObjectDataCenter.resetTimer();
				if (request)
					handler.requestData(accessor);
			}
			if (request && ObjectDataCenter.getServerData() == null) {
				return;
			}
		}

		OverlayRenderer.theme.setValue(IWailaConfig.get().getOverlay().getTheme());
		Accessor<?> accessor0 = accessor;
		WailaClientRegistration.instance().beforeTooltipCollectCallback.call(callback -> {
			callback.beforeCollecting(OverlayRenderer.theme, accessor0);
		});
		Theme theme = OverlayRenderer.theme.getValue();
		Preconditions.checkNotNull(theme, "Theme cannot be null");

		if (config.getDisplayMode() == DisplayMode.LITE && !ClientProxy.isShowDetailsPressed()) {
			Tooltip dummyTooltip = new Tooltip();
			handler.gatherComponents(accessor, $ -> {
				if (Math.abs(WailaCommonRegistration.instance().priorities.byValue($)) > 5000) {
					return tooltip;
				} else {
					return dummyTooltip;
				}
			});
			if (!dummyTooltip.isEmpty()) {
				tooltip.sneakyDetails = true;
			}
		} else {
			handler.gatherComponents(accessor, $ -> tooltip);
		}

		rootElement = new BoxElement(tooltip, IThemeHelper.get().theme().tooltipStyle);
		rootElement.tag(Identifiers.ROOT);
		if (IWailaConfig.get().getOverlay().shouldShowIcon()) {
			IElement icon = RayTracing.INSTANCE.getIcon();
			if (icon != null && theme.iconSlotSprite != null) {
				if (theme.iconSlotSpriteCache == null) {
					GuiSpriteManager guiSprites = Minecraft.getInstance().getGuiSprites();
					TextureAtlasSprite textureAtlasSprite = guiSprites.getSprite(theme.iconSlotSprite);
					GuiSpriteScaling scaling = guiSprites.getSpriteScaling(textureAtlasSprite);
					int[] padding = new int[4];
					Arrays.fill(padding, theme.iconSlotInflation);
					if (scaling instanceof GuiSpriteScaling.NineSlice nineSlice) {
						GuiSpriteScaling.NineSlice.Border border = nineSlice.border();
						padding[0] += border.top();
						padding[1] += border.right();
						padding[2] += border.bottom();
						padding[3] += border.left();
					}
					theme.iconSlotSpriteCache = new BoxElement(new Tooltip(), BoxStyle.getSprite(theme.iconSlotSprite, padding));
				}
				ITooltip tooltip1 = theme.iconSlotSpriteCache.getTooltip();
				tooltip1.clear();
				tooltip1.add(icon);
				icon = theme.iconSlotSpriteCache.size(null);
			}
			rootElement.setIcon(icon);
		}
		for (JadeTooltipCollectedCallback callback : WailaClientRegistration.instance().tooltipCollectedCallback.callbacks()) {
			callback.onTooltipCollected(rootElement, accessor);
		}
	}
}
