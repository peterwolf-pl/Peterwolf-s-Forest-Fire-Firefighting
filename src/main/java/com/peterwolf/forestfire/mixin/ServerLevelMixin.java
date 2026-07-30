package com.peterwolf.forestfire.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Reserved extension point for world-level fire hooks.
 * Primary simulation runs via ServerTickEvents — keep mixin surface small.
 */
@Mixin(ServerLevel.class)
public class ServerLevelMixin {
}
