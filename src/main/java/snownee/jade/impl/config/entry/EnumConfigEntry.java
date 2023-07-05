package snownee.jade.impl.config.entry;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.gui.config.WailaOptionsList;
import snownee.jade.gui.config.value.OptionValue;
import snownee.jade.impl.config.PluginConfig;

public class EnumConfigEntry<E extends Enum<E>> extends ConfigEntry<E> {

	public EnumConfigEntry(ResourceLocation id, E defaultValue) {
		super(id, defaultValue);
	}

	@Override
	public boolean isValidValue(Object value) {
		if (value.getClass() == String.class) {
			try {
				Enum.valueOf(getDefaultValue().getClass(), (String) value);
				return true;
			} catch (Throwable e) {
				return false;
			}
		}
		return value.getClass() == getDefaultValue().getClass();
	}

	@Override
	public void setValue(Object value) {
		if (value.getClass() == String.class) {
			value = Enum.valueOf(getDefaultValue().getClass(), (String) value);
		}
		super.setValue(value);
	}

	@Override
	public OptionValue<?> createUI(WailaOptionsList options, String optionName) {
		return options.choices(optionName, getValue(), e -> PluginConfig.INSTANCE.set(id, e), builder -> {
			builder.withTooltip(e -> {
				String key = WailaOptionsList.Entry.makeKey(optionName + "_" + e.name().toLowerCase(Locale.ENGLISH) + "_desc");
				if (!I18n.exists(key))
					return List.of();
				return Minecraft.getInstance().font.split(new TranslatableComponent(key), 200);
			});
		});
	}

}
