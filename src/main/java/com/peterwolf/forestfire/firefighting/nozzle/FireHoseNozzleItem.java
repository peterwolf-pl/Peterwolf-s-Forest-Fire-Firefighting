package com.peterwolf.forestfire.firefighting.nozzle;

import com.peterwolf.forestfire.block.ModBlocks;
import com.peterwolf.forestfire.fire.incident.IncidentManager;
import com.peterwolf.forestfire.fire.simulation.FireSimulation;
import com.peterwolf.forestfire.firefighting.hose.HoseNetwork;
import com.peterwolf.forestfire.firefighting.pump.PortablePumpBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class FireHoseNozzleItem extends Item {
	public FireHoseNozzleItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	public static NozzleMode getMode(ItemStack stack) {
		int ordinal = stack.getOrDefault(ModDataComponents.NOZZLE_MODE, 0);
		NozzleMode[] values = NozzleMode.values();
		if (ordinal < 0 || ordinal >= values.length) {
			return NozzleMode.STRAIGHT_STREAM;
		}
		return values[ordinal];
	}

	public static void setMode(ItemStack stack, NozzleMode mode) {
		stack.set(ModDataComponents.NOZZLE_MODE, mode.ordinal());
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.isShiftKeyDown()) {
			if (!level.isClientSide()) {
				NozzleMode next = getMode(stack).next();
				setMode(stack, next);
				player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_mode", next.label));
			}
			return InteractionResult.SUCCESS;
		}
		NozzleMode mode = getMode(stack);
		if (mode == NozzleMode.SHUTOFF) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_shutoff"));
			}
			return InteractionResult.FAIL;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 72000;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.BOW;
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
		if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		if (remainingUseDuration % 4 != 0) {
			return;
		}
		NozzleMode mode = getMode(stack);
		if (mode == NozzleMode.SHUTOFF) {
			return;
		}

		BlockPos hosePos = findNearbyHose(serverLevel, player.blockPosition());
		if (hosePos == null) {
			player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_no_hose"));
			player.stopUsingItem();
			return;
		}
		PortablePumpBlockEntity pump = HoseNetwork.findSupplyingPump(serverLevel, hosePos);
		if (pump == null || !pump.canSupplyNozzle()) {
			player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_no_pressure"));
			return;
		}

		float demand = 0.2F + mode.cooling * 0.15F;
		float supplied = pump.consumeForNozzle(demand);
		if (supplied <= 0.05F) {
			return;
		}

		// Raycast water stream
		double reach = mode.range * Math.min(1.0F, 0.4F + pump.getPressure());
		HitResult hit = player.pick(reach, 0.0F, false);
		Vec3 end;
		if (hit.getType() == HitResult.Type.BLOCK) {
			end = hit.getLocation();
		} else {
			Vec3 look = player.getLookAngle();
			end = player.getEyePosition().add(look.scale(reach));
		}

		BlockPos target = BlockPos.containing(end);
		float used = FireSimulation.get(serverLevel).applyWater(target, mode.cooling * supplied, mode.radius);
		// Wet line along path for wide fog defensive ops
		if (mode == NozzleMode.WIDE_FOG) {
			wetAlongLook(serverLevel, player, (int) Math.min(4, reach));
		}

		// Incident water accounting
		IncidentManager manager = IncidentManager.get(serverLevel);
		manager.openIncidents().stream().findFirst().ifPresent(incident -> {
			incident.recordWater(used * 10.0F);
			incident.addParticipant(player.getUUID());
			manager.markDirty();
		});

		// Particles
		Vec3 start = player.getEyePosition().add(player.getLookAngle().scale(0.6));
		for (int i = 0; i < 4; i++) {
			double t = i / 4.0;
			double px = start.x + (end.x - start.x) * t;
			double py = start.y + (end.y - start.y) * t;
			double pz = start.z + (end.z - start.z) * t;
			serverLevel.sendParticles(ParticleTypes.SPLASH, px, py, pz, 2, 0.05, 0.05, 0.05, 0.01);
			serverLevel.sendParticles(ParticleTypes.RAIN, px, py, pz, 1, 0.02, 0.02, 0.02, 0.0);
		}
		serverLevel.playSound(null, player.blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.35F, 1.4F);

		player.sendOverlayMessage(Component.literal(
			"Nozzle: " + mode.label + " | P=" + String.format("%.2f", pump.getPressure())
		));
	}

	private static void wetAlongLook(ServerLevel level, Player player, int steps) {
		Vec3 look = player.getLookAngle();
		Vec3 base = player.position();
		FireSimulation sim = FireSimulation.get(level);
		for (int i = 1; i <= steps; i++) {
			BlockPos pos = BlockPos.containing(base.add(look.scale(i)));
			sim.applyWater(pos, 0.25F, 1.0F);
		}
	}

	private static BlockPos findNearbyHose(ServerLevel level, BlockPos origin) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dy = -1; dy <= 2; dy++) {
				for (int dz = -3; dz <= 3; dz++) {
					cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					BlockState state = level.getBlockState(cursor);
					if (state.is(ModBlocks.FIRE_HOSE) || state.is(ModBlocks.HOSE_SPLITTER)
						|| state.is(ModBlocks.PORTABLE_PUMP) || state.is(ModBlocks.PORTABLE_SPRINKLER)) {
						return cursor.immutable();
					}
				}
			}
		}
		return null;
	}
}
