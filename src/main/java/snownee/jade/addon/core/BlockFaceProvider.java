package snownee.jade.addon.core;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.Identifiers;
import snownee.jade.api.TooltipPosition;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement.Align;

public enum BlockFaceProvider implements IBlockComponentProvider {

	INSTANCE;

	@Override
	public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
		for (int i = 0; i < tooltip.size(); i++) {
			if (tooltip.get(i, Align.LEFT).stream().anyMatch($ -> Identifiers.CORE_OBJECT_NAME.equals($.getTag()))) {
				tooltip.append(new TranslatableComponent("jade.blockFace", I18n.get("jade." + accessor.getSide().getName())));
				return;
			}
		}
	}

	@Override
	public ResourceLocation getUid() {
		return Identifiers.CORE_BLOCK_FACE;
	}

	@Override
	public int getDefaultPriority() {
		return TooltipPosition.HEAD;
	}

	@Override
	public boolean enabledByDefault() {
		return false;
	}

}
