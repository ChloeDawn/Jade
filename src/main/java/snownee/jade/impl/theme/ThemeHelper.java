package snownee.jade.impl.theme;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.NotNull;

import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import snownee.jade.Jade;
import snownee.jade.JadeClient;
import snownee.jade.api.config.IWailaConfig;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.theme.Theme;
import snownee.jade.impl.config.WailaConfig;
import snownee.jade.overlay.DisplayHelper;
import snownee.jade.overlay.OverlayRenderer;
import snownee.jade.util.JsonConfig;

public class ThemeHelper extends SimpleJsonResourceReloadListener implements IThemeHelper {
	public static final ThemeHelper INSTANCE = new ThemeHelper();
	public static final ResourceLocation ID = new ResourceLocation(Jade.MODID, "themes");
	private static final Int2ObjectMap<Style> styleCache = new Int2ObjectOpenHashMap<>(6);
	private final Map<ResourceLocation, Theme> themes = Maps.newTreeMap(Comparator.comparing(ResourceLocation::toString));
	private final MinMaxBounds.Ints allowedVersions = MinMaxBounds.Ints.between(100, 199);
	private Theme fallback;
	private Style[] modNameStyleCache = new Style[3];

	public ThemeHelper() {
		super(JsonConfig.DEFAULT_GSON, "jade_themes");
	}

	public static Style colorStyle(int color) {
		return styleCache.computeIfAbsent(color, Style.EMPTY::withColor);
	}

	@Override
	public Theme theme() {
		return OverlayRenderer.theme.getValue();
	}

	@Override
	public Collection<Theme> getThemes() {
		return themes.values();
	}

	@Override
	@NotNull
	public Theme getTheme(ResourceLocation id) {
		return Objects.requireNonNull(themes.getOrDefault(id, fallback));
	}

	@Override
	public MutableComponent info(Object componentOrString) {
		return color(componentOrString, theme().text.colors().info());
	}

	@Override
	public MutableComponent success(Object componentOrString) {
		return color(componentOrString, theme().text.colors().success());
	}

	@Override
	public MutableComponent warning(Object componentOrString) {
		return color(componentOrString, theme().text.colors().warning());
	}

	@Override
	public MutableComponent danger(Object componentOrString) {
		return color(componentOrString, theme().text.colors().danger());
	}

	@Override
	public MutableComponent failure(Object componentOrString) {
		return color(componentOrString, theme().text.colors().failure());
	}

	@Override
	public MutableComponent title(Object componentOrString) {
		Component component;
		if (componentOrString instanceof MutableComponent) {
			component = (MutableComponent) componentOrString;
		} else {
			component = Component.literal(Objects.toString(componentOrString));
		}
		return color(DisplayHelper.INSTANCE.stripColor(component), theme().text.colors().title());
	}

	@Override
	public MutableComponent modName(Object componentOrString) {
		MutableComponent component;
		if (componentOrString instanceof MutableComponent) {
			component = (MutableComponent) componentOrString;
		} else {
			component = Component.literal(Objects.toString(componentOrString));
		}
		Style itemStyle = IWailaConfig.get().getFormatting().getItemModNameStyle();
		Style themeStyle = theme().text.modNameStyle();
		if (modNameStyleCache[0] != itemStyle || modNameStyleCache[1] != themeStyle) {
			Style style = itemStyle;
			if (themeStyle != null) {
				style = themeStyle.applyTo(style);
			}
			modNameStyleCache[0] = itemStyle;
			modNameStyleCache[1] = themeStyle;
			modNameStyleCache[2] = style;
		}
		return component.withStyle(modNameStyleCache[2]);
	}

	@Override
	public MutableComponent seconds(int ticks) {
		return info(JadeClient.format("jade.seconds", ticks / 20));
	}

	protected MutableComponent color(Object componentOrString, int color) {
		if (componentOrString instanceof MutableComponent component) {
			if (component.getStyle().isEmpty()) {
				return component.setStyle(colorStyle(color));
			} else {
				return component.setStyle(component.getStyle().withColor(color));
			}
		} else {
			return Component.literal(Objects.toString(componentOrString)).setStyle(colorStyle(color));
		}
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
		Set<ResourceLocation> existingKeys = Set.copyOf(themes.keySet());
		MutableObject<Theme> enable = new MutableObject<>();
		WailaConfig.ConfigOverlay config = Jade.CONFIG.get().getOverlay();
		themes.clear();
		map.forEach((id, json) -> {
			JsonObject o = json.getAsJsonObject();
			int version = GsonHelper.getAsInt(o, "version", 0);
			if (!allowedVersions.matches(version)) {
				Jade.LOGGER.warn("Theme {} has unsupported version {}. Skipping.", id, version);
				return;
			}
			try {
				ThemeCodecs.CODEC.decode(JsonOps.INSTANCE, o).resultOrPartial(Jade.LOGGER::error).map(Pair::getFirst).ifPresent(theme -> {
					theme.id = id;
					themes.put(id, theme);
					if (enable.getValue() == null && GsonHelper.getAsBoolean(o, "autoEnable", false) && !existingKeys.contains(id)) {
						enable.setValue(theme);
					}
				});
			} catch (Exception e) {
				Jade.LOGGER.error("Failed to load theme {}", id, e);
			}
		});
		fallback = themes.get(Theme.DEFAULT_THEME_ID);
		if (fallback == null) {
			CrashReport crashreport = CrashReport.forThrowable(new NullPointerException(), "Missing default theme");
			throw new ReportedException(crashreport);
		}
		int hash = 0;
		for (ResourceLocation id : themes.keySet()) {
			hash = 31 * hash + id.hashCode();
		}
		if (hash != config.themesHash) {
			if (hash != 0 && enable.getValue() != null) {
				Theme theme = enable.getValue();
				config.activeTheme = theme.id;
				Jade.LOGGER.info("Auto enabled theme {}", theme.id);
				if (theme.changeRoundCorner != null) {
					config.setSquare(theme.changeRoundCorner);
				}
				if (theme.changeOpacity != 0) {
					config.setAlpha(theme.changeOpacity);
				}
			}
			config.themesHash = hash;
			Jade.CONFIG.save();
		}
		config.applyTheme(config.activeTheme);
	}
}
