package snownee.jade.api;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.Level;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;

/**
 * Callback class interface used to provide Entity tooltip informations to Waila.<br>
 * All methods in this interface shouldn't to be called by the implementing mod. An instance of the class is to be
 * registered to Waila via the {@link IWailaClientRegistration} instance provided in the original registration callback method
 * (cf. {@link IWailaClientRegistration} documentation for more information).
 *
 * @author ProfMobius
 */
public interface IEntityComponentProvider extends IToggleableProvider {

	/**
     * Callback used to set an element to display alongside the entity name in the tooltip, similar to how blocks are treated.
     * Will only be called if the implementing class is registered via {@link IWailaClientRegistration#registerEntityIcon}
     *
     * This method is only called on the client side. If you require data from the server, you should also implement
     * {@link IServerDataProvider#appendServerData(net.minecraft.nbt.CompoundTag, net.minecraft.entity.player.ServerPlayer, Level, Object)}
     * and add the data to the {@link net.minecraft.nbt.CompoundTag} there, which can then be read back using {@link Accessor#getServerData()}.
     * If you rely on the client knowing the data you need, you are not guaranteed to have the proper values.
     *
     * @param accessor       Contains most of the relevant information about the current environment.
     * @param config         Current configuration of Waila.
     * @param currentElement Current icon to show
     * @return {@link null} if override is not required, an {@link IElement} otherwise.
     */
	@Nullable
	default IElement getIcon(EntityAccessor accessor, IPluginConfig config, IElement currentIcon) {
		return null;
	}

	/**
     * Callback used to add lines to one of the three sections of the tooltip (Head, Body, Tail).</br>
     * Will only be called if the implementing class is registered via {@link IWailaClientRegistration#registerEntityComponent(IEntityComponentProvider, TooltipPosition, Class)}.</br>
     * You are supposed to always return the modified input currenttip.</br>
     *
     * This method is only called on the client side. If you require data from the server, you should also implement
     * {@link IServerDataProvider#appendServerData(net.minecraft.nbt.CompoundTag, net.minecraft.entity.player.ServerPlayer, Level, Object)}
     * and add the data to the {@link net.minecraft.nbt.CompoundTag} there, which can then be read back using {@link IBlockAccessor#getServerData()}.
     * If you rely on the client knowing the data you need, you are not guaranteed to have the proper values.
     *
     * @param tooltip    Current list of tooltip lines (might have been processed by other providers and might be processed by other providers).
     * @param accessor   Contains most of the relevant information about the current environment.
     * @param config     Current configuration of Waila.
     */
	void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config);

}
