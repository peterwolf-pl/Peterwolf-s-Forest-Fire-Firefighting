package com.peterwolf.forestfire.firefighting.nozzle;

import com.peterwolf.forestfire.firefighting.hose.HoseEndpoint;
import com.peterwolf.forestfire.firefighting.hose.HoseEndpointManager;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Handheld fire hose nozzle.
 *
 * Free nozzle: right-click ground near hose OR right-click hose → places connected ground nozzle.
 * Connected: hold use to spray; sneak+use cycles mode; sneak+use on ground places down.
 */
public class FireHoseNozzleItem extends Item {
	private static final int HOSE_SEARCH = 8;

	public FireHoseNozzleItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	public static NozzleMode getMode(ItemStack stack) {
		return NozzleMode.byOrdinal(stack.getOrDefault(ModDataComponents.NOZZLE_MODE, NozzleMode.STRAIGHT_STREAM.ordinal()));
	}

	public static void setMode(ItemStack stack, NozzleMode mode) {
		stack.set(ModDataComponents.NOZZLE_MODE, mode.ordinal());
	}

	@Nullable
	public static UUID getEndpointId(ItemStack stack) {
		String raw = stack.get(ModDataComponents.NOZZLE_ENDPOINT_ID);
		if (raw == null || raw.isEmpty()) {
			return null;
		}
		try {
			return UUID.fromString(raw);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		ItemStack stack = context.getItemInHand();
		BlockPos clicked = context.getClickedPos();
		UUID endpointId = getEndpointId(stack);
		HoseEndpointManager manager = HoseEndpointManager.get(serverLevel);

		// 1) Free nozzle on hose block → connect + place next to hose
		if (endpointId == null && HoseEndpointManager.isHoseAnchor(serverLevel, clicked)) {
			BlockPos ground = resolvePlacePos(serverLevel, clicked, context.getClickedFace(), player);
			if (ground == null) {
				serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_invalid_place"));
				return InteractionResult.FAIL;
			}
			HoseEndpoint ep = manager.connectAndPlace(
				serverLevel, serverPlayer, ground, clicked,
				player.getYRot(), player.getXRot(), getMode(stack)
			);
			if (ep != null) {
				serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_connected"));
				return InteractionResult.CONSUME;
			}
			return InteractionResult.FAIL;
		}

		// 2) Connected nozzle on hose → disconnect (must be closed)
		if (endpointId != null && HoseEndpointManager.isHoseAnchor(serverLevel, clicked) && !player.isShiftKeyDown()) {
			HoseEndpoint ep = manager.get(endpointId).orElse(null);
			if (ep != null) {
				if (manager.disconnect(serverLevel, serverPlayer, ep)) {
					// disconnect already gives free nozzle item
					return InteractionResult.CONSUME;
				}
				return InteractionResult.FAIL;
			}
		}

		// 3) Place on ground (free or connected) — no shift required for free near hose
		BlockPos placePos = resolvePlacePos(serverLevel, clicked, context.getClickedFace(), player);
		if (placePos == null) {
			serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_invalid_place"));
			return InteractionResult.FAIL;
		}

		if (endpointId != null) {
			// Connected: place back on ground (prefer sneak, but allow normal click on solid floor)
			HoseEndpoint ep = manager.get(endpointId).orElse(null);
			if (ep == null) {
				stack.remove(ModDataComponents.NOZZLE_ENDPOINT_ID);
				return placeFreeNearHose(serverLevel, serverPlayer, stack, placePos);
			}
			// Only place when sneaking OR clicking a solid top face (not air interaction)
			if (player.isShiftKeyDown() || context.getClickedFace() == Direction.UP) {
				if (ep.valve == NozzleValveState.OPEN) {
					ep.closeValve();
				}
				if (manager.placeFromHand(serverLevel, serverPlayer, ep, placePos, player.getYRot(), player.getXRot())) {
					return InteractionResult.CONSUME;
				}
			}
			return InteractionResult.PASS;
		}

		// Free nozzle on any solid surface near hose
		return placeFreeNearHose(serverLevel, serverPlayer, stack, placePos);
	}

	private static InteractionResult placeFreeNearHose(
		ServerLevel level,
		ServerPlayer player,
		ItemStack stack,
		BlockPos placePos
	) {
		BlockPos hose = HoseEndpointManager.findNearbyHose(level, placePos, HOSE_SEARCH);
		if (hose == null) {
			// Also search from player feet
			hose = HoseEndpointManager.findNearbyHose(level, player.blockPosition(), HOSE_SEARCH);
		}
		if (hose == null) {
			player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_need_hose"));
			return InteractionResult.FAIL;
		}
		HoseEndpointManager manager = HoseEndpointManager.get(level);
		HoseEndpoint ep = manager.connectAndPlace(
			level, player, placePos, hose,
			player.getYRot(), player.getXRot(), getMode(stack)
		);
		if (ep != null) {
			player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_connected"));
			return InteractionResult.CONSUME;
		}
		return InteractionResult.FAIL;
	}

	/**
	 * Prefer the cell above the clicked face; if blocked, try top of clicked block or adjacent air.
	 */
	@Nullable
	private static BlockPos resolvePlacePos(ServerLevel level, BlockPos clicked, Direction face, Player player) {
		BlockPos preferred = clicked.relative(face);
		if (canPlaceAt(level, preferred)) {
			return preferred;
		}
		if (face != Direction.UP && canPlaceAt(level, clicked.above())) {
			return clicked.above();
		}
		// Replaceable vegetation on the target cell
		if (isReplaceableSurface(level, preferred)) {
			return preferred;
		}
		if (isReplaceableSurface(level, clicked.above())) {
			return clicked.above();
		}
		// Player feet if near click
		BlockPos feet = player.blockPosition();
		if (canPlaceAt(level, feet) && feet.closerThan(clicked, 3.0)) {
			return feet;
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos p = clicked.relative(d);
			if (canPlaceAt(level, p)) {
				return p;
			}
		}
		return null;
	}

	private static boolean canPlaceAt(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.isAir() || state.canBeReplaced() || isReplaceableSurface(level, pos);
	}

	private static boolean isReplaceableSurface(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.is(BlockTags.REPLACEABLE) || state.is(BlockTags.FLOWERS)
			|| state.canBeReplaced();
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		UUID endpointId = getEndpointId(stack);

		// Free nozzle in air
		if (endpointId == null) {
			if (player.isShiftKeyDown()) {
				if (!level.isClientSide()) {
					NozzleMode next = getMode(stack).cycleCombat();
					setMode(stack, next);
					player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_mode", next.label));
				}
				return InteractionResult.SUCCESS;
			}
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_need_hose"));
			}
			return InteractionResult.FAIL;
		}

		if (level.isClientSide()) {
			if (!player.isShiftKeyDown()) {
				player.startUsingItem(hand);
			}
			return InteractionResult.CONSUME;
		}

		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		HoseEndpointManager manager = HoseEndpointManager.get(serverLevel);
		HoseEndpoint ep = manager.get(endpointId).orElse(null);
		if (ep == null || !ep.connected) {
			stack.remove(ModDataComponents.NOZZLE_ENDPOINT_ID);
			serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_orphaned"));
			return InteractionResult.FAIL;
		}

		if (player.isShiftKeyDown()) {
			NozzleMode next = ep.mode.cycleCombat();
			ep.mode = next;
			setMode(stack, next);
			ep.closeValve();
			manager.setDirty();
			serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_mode", next.label));
			return InteractionResult.SUCCESS;
		}

		if (!ep.mode.allowsFlow()) {
			serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_shutoff"));
			return InteractionResult.FAIL;
		}

		ep.location = NozzleLocationState.HELD;
		ep.operatorId = serverPlayer.getUUID();
		ep.valve = NozzleValveState.OPEN;
		manager.setDirty();
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
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
		if (level.isClientSide() || !(entity instanceof ServerPlayer) || !(level instanceof ServerLevel serverLevel)) {
			return false;
		}
		UUID endpointId = getEndpointId(stack);
		if (endpointId == null) {
			return false;
		}
		HoseEndpointManager manager = HoseEndpointManager.get(serverLevel);
		manager.get(endpointId).ifPresent(ep -> {
			ep.closeValve();
			manager.setDirty();
		});
		return true;
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
		if (level.isClientSide() || !(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		UUID endpointId = getEndpointId(stack);
		if (endpointId == null) {
			player.stopUsingItem();
			return;
		}
		HoseEndpointManager manager = HoseEndpointManager.get(serverLevel);
		HoseEndpoint ep = manager.get(endpointId).orElse(null);
		if (ep == null || !ep.connected) {
			player.stopUsingItem();
			return;
		}
		if (ep.mode.allowsFlow()) {
			ep.valve = NozzleValveState.OPEN;
			ep.operatorId = player.getUUID();
			ep.location = NozzleLocationState.HELD;
		}
	}
}
