package snownee.jade.impl;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import snownee.jade.Jade;
import snownee.jade.api.AccessorImpl;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.util.ClientProxy;
import snownee.jade.util.WailaExceptionHandler;

/**
 * Class to get information of entity target and context.
 */
public class EntityAccessorImpl extends AccessorImpl<EntityHitResult> implements EntityAccessor {

	private final Supplier<Entity> entity;

	public EntityAccessorImpl(Builder builder) {
		super(builder.level, builder.player, builder.serverData, builder.hit, builder.connected, builder.showDetails);
		entity = builder.entity;
	}

	public static void handleRequest(FriendlyByteBuf buf, ServerPlayer player, Consumer<Runnable> executor, Consumer<CompoundTag> responseSender) {
		EntityAccessor accessor;
		try {
			accessor = fromNetwork(buf, player);
		} catch (Exception e) {
			WailaExceptionHandler.handleErr(e, null, null);
			return;
		}
		executor.accept(() -> {
			Entity entity = accessor.getEntity();
			if (entity == null || player.distanceToSqr(entity) > Jade.MAX_DISTANCE_SQR)
				return;
			List<IServerDataProvider<EntityAccessor>> providers = WailaCommonRegistration.instance().getEntityNBTProviders(entity);
			if (providers.isEmpty())
				return;

			CompoundTag tag = accessor.getServerData();
			for (IServerDataProvider<EntityAccessor> provider : providers) {
				try {
					provider.appendServerData(tag, accessor);
				} catch (Exception e) {
					WailaExceptionHandler.handleErr(e, provider, null);
				}
			}

			tag.putInt("WailaEntityID", entity.getId());
			responseSender.accept(tag);
		});
	}

	public static EntityAccessor fromNetwork(FriendlyByteBuf buf, ServerPlayer player) {
		Builder builder = new Builder();
		builder.level(player.level());
		builder.player(player);
		builder.showDetails(buf.readBoolean());
		int id = buf.readVarInt();
		float hitX = buf.readFloat();
		float hitY = buf.readFloat();
		float hitZ = buf.readFloat();
		// you can only get block entity from the main thread
		Supplier<Entity> entity = Suppliers.memoize(() -> builder.level.getEntity(id));
		builder.entity(entity);
		builder.hit(Suppliers.memoize(() -> new EntityHitResult(entity.get(), new Vec3(hitX, hitY, hitZ))));
		return builder.build();
	}

	@Override
	public void toNetwork(FriendlyByteBuf buf) {
		buf.writeBoolean(showDetails());
		buf.writeVarInt(entity.get().getId());
		Vec3 hitVec = getHitResult().getLocation();
		buf.writeFloat((float) hitVec.x);
		buf.writeFloat((float) hitVec.y);
		buf.writeFloat((float) hitVec.z);
	}

	@Override
	public Entity getEntity() {
		return entity.get();
	}

	@Override
	public ItemStack getPickedResult() {
		return ClientProxy.getEntityPickedResult(entity.get(), getPlayer(), getHitResult());
	}

	@Override
	public Object getTarget() {
		return getEntity();
	}

	public static class Builder implements EntityAccessor.Builder {

		public boolean showDetails;
		private Level level;
		private Player player;
		private CompoundTag serverData;
		private boolean connected;
		private Supplier<EntityHitResult> hit;
		private Supplier<Entity> entity;

		@Override
		public Builder level(Level level) {
			this.level = level;
			return this;
		}

		@Override
		public Builder player(Player player) {
			this.player = player;
			return this;
		}

		@Override
		public Builder serverData(CompoundTag serverData) {
			this.serverData = serverData;
			return this;
		}

		@Override
		public Builder serverConnected(boolean connected) {
			this.connected = connected;
			return this;
		}

		@Override
		public Builder showDetails(boolean showDetails) {
			this.showDetails = showDetails;
			return this;
		}

		@Override
		public Builder hit(Supplier<EntityHitResult> hit) {
			this.hit = hit;
			return this;
		}

		@Override
		public Builder entity(Supplier<Entity> entity) {
			this.entity = entity;
			return this;
		}

		@Override
		public Builder from(EntityAccessor accessor) {
			level = accessor.getLevel();
			player = accessor.getPlayer();
			serverData = accessor.getServerData();
			connected = accessor.isServerConnected();
			showDetails = accessor.showDetails();
			hit = accessor::getHitResult;
			entity = accessor::getEntity;
			return this;
		}

		@Override
		public EntityAccessor build() {
			return new EntityAccessorImpl(this);
		}

	}

}
