package com.peterwolf.forestfire.item;

import com.peterwolf.forestfire.firefighting.pump.PortablePumpBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;

public class PumpFuelCanItem extends Item {
	public PumpFuelCanItem(Properties properties) {
		super(properties.stacksTo(16));
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		BlockPos pos = context.getClickedPos();
		BlockEntity be = context.getLevel().getBlockEntity(pos);
		if (be instanceof PortablePumpBlockEntity pump) {
			if (!context.getLevel().isClientSide()) {
				pump.addFuel(250);
				context.getItemInHand().shrink(1);
				if (context.getPlayer() != null) {
					context.getPlayer().sendOverlayMessage(
						Component.translatable("message.peterwolfs_forestfire.pump_refueled"));
				}
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}
}
