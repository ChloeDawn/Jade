package snownee.jade.addon.universal;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.Identifiers;
import snownee.jade.api.TooltipPosition;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElementHelper;
import snownee.jade.api.ui.ProgressStyle;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.EnergyView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;
import snownee.jade.impl.WailaClientRegistration;
import snownee.jade.impl.WailaCommonRegistration;
import snownee.jade.util.CommonProxy;

public enum EnergyStorageProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor>,
		IServerExtensionProvider<Object, CompoundTag>, IClientExtensionProvider<CompoundTag, EnergyView> {

	INSTANCE;

	public static void append(ITooltip tooltip, Accessor<?> accessor, IPluginConfig config) {
		if ((!accessor.showDetails() && config.get(Identifiers.UNIVERSAL_ENERGY_STORAGE_DETAILED))) {
			return;
		}
		if (accessor.getServerData().contains("JadeEnergyStorage")) {
			var provider = Optional.ofNullable(ResourceLocation.tryParse(accessor.getServerData().getString("JadeEnergyStorageUid"))).map(WailaClientRegistration.instance().energyStorageProviders::get);
			if (provider.isPresent()) {
				var groups = provider.get().getClientGroups(accessor, ViewGroup.readList(accessor.getServerData(), "JadeEnergyStorage", Function.identity()));
				if (groups.isEmpty()) {
					return;
				}

				IElementHelper helper = IElementHelper.get();
				boolean renderGroup = groups.size() > 1 || groups.get(0).shouldRenderGroup();
				ClientViewGroup.tooltip(tooltip, groups, renderGroup, (theTooltip, group) -> {
					if (renderGroup) {
						group.renderHeader(theTooltip);
					}
					for (var view : group.views) {
						Component text;
						if (view.overrideText != null) {
							text = view.overrideText;
						} else {
							text = Component.translatable("jade.fe", ChatFormatting.WHITE + view.current, view.max).withStyle(ChatFormatting.GRAY);
						}
						ProgressStyle progressStyle = helper.progressStyle().color(0xFFAA0000, 0xFF660000);
						theTooltip.add(helper.progress(view.ratio, text, progressStyle, BoxStyle.getNestedBox(), true));
					}
				});
			}
		}
	}

	public static void putData(Accessor<?> accessor) {
		CompoundTag tag = accessor.getServerData();
		Object target = accessor.getTarget();
		for (var provider : WailaCommonRegistration.instance().energyStorageProviders.get(target)) {
			var groups = provider.getGroups(accessor, target);
			if (groups != null) {
				if (ViewGroup.saveList(tag, "JadeEnergyStorage", groups, Function.identity()))
					tag.putString("JadeEnergyStorageUid", provider.getUid().toString());
				return;
			}
		}
	}

	@Override
	public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
		append(tooltip, accessor, config);
	}

	@Override
	public void appendServerData(CompoundTag data, BlockAccessor accessor) {
		putData(accessor);
	}

	@Override
	public ResourceLocation getUid() {
		return Identifiers.UNIVERSAL_ENERGY_STORAGE;
	}

	@Override
	public int getDefaultPriority() {
		return TooltipPosition.BODY + 1000;
	}

	@Override
	public List<ClientViewGroup<EnergyView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<CompoundTag>> groups) {
		return groups.stream().map($ -> {
			String unit = $.getExtraData().getString("Unit");
			return new ClientViewGroup<>($.views.stream().map(tag -> EnergyView.read(tag, unit)).filter(Objects::nonNull).toList());
		}).toList();
	}

	@Override
	public List<ViewGroup<CompoundTag>> getGroups(Accessor<?> accessor, Object target) {
		return CommonProxy.wrapEnergyStorage(accessor, target);
	}

}
