package rrp.neoforge.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** What a builder shows while laying a block: the item in the main hand, the look, the arm swing, the sound, the dust. */
public final class Gesture {

    private Gesture() {
    }

    /** Puts the item of {@code state} in the main hand (the block item; nothing for a block that has none). */
    public static void hold(Villager v, BlockState state) {
        ItemStack item = new ItemStack(state.getBlock().asItem());
        if (!item.isEmpty()) {
            v.setItemSlot(EquipmentSlot.MAINHAND, item);
            v.setDropChance(EquipmentSlot.MAINHAND, 0f);
        }
    }

    /** Turns the builder towards the block it is about to lay. */
    public static void look(Villager v, BlockPos at) {
        v.getLookControl().setLookAt(Vec3.atCenterOf(at));
    }

    /** The gesture of a block that has just been laid. */
    public static void placed(ServerLevel level, Villager v, BlockPos at, BlockState state, int particles) {
        hold(v, state);
        v.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, at, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0f, 0.8f);
        dust(level, at, state, particles);
    }

    /** A few block particles around {@code at} ({@code particles} 0 = none). */
    public static void dust(ServerLevel level, BlockPos at, BlockState state, int particles) {
        if (particles > 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 5 * particles, 0.25, 0.25, 0.25, 0.05);
        }
    }
}
