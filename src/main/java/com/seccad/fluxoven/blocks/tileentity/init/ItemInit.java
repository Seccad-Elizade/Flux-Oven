package com.seccad.fluxoven.init;

import com.seccad.fluxoven.FluxOven;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;

public class ItemInit {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FluxOven.MOD_ID);

    public static final CreativeModeTab FLUX_OVEN_TAB = FluxOven.TAB;

    public static final RegistryObject<Item> FLUX_OVEN_CORE = ITEMS.register("flux_oven_core",
            () -> new BlockItem(BlockInit.FLUX_OVEN_CORE.get(), new Item.Properties().tab(FLUX_OVEN_TAB)) {
                @Override
                protected boolean updateCustomBlockEntityTag(BlockPos pos, Level world, @Nullable Player player, ItemStack stack, BlockState state) {
                    boolean superResult = super.updateCustomBlockEntityTag(pos, world, player, stack, state);
                    if (!world.isClientSide) {
                        BlockEntity tileEntity = world.getBlockEntity(pos);
                        if (tileEntity instanceof com.seccad.fluxoven.tileentity.FluxOvenCoreTile) {
                            tileEntity.setChanged();
                            world.sendBlockUpdated(pos, state, state, 3);
                        }
                    }
                    return superResult;
                }

                @Override
                @OnlyIn(Dist.CLIENT)
                public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(new TextComponent("The central processing hub of the multiblock grid.").withStyle(ChatFormatting.GRAY));
                    tooltip.add(new TextComponent(""));
                    tooltip.add(new TextComponent("Manages power distribution and upgrades across all").withStyle(ChatFormatting.BLUE));
                    tooltip.add(new TextComponent("connected peripheral processing channels.").withStyle(ChatFormatting.BLUE));
                    tooltip.add(new TextComponent(""));
                    tooltip.add(new TextComponent("Requires Forge Energy (FE) to operate.").withStyle(ChatFormatting.GOLD));
                }
            });

    public static final RegistryObject<Item> OVEN_PLATE = ITEMS.register("oven_plate",
            () -> new BlockItem(BlockInit.OVEN_PLATE.get(), new Item.Properties().tab(FLUX_OVEN_TAB)) {
                @Override
                @OnlyIn(Dist.CLIENT)
                public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(new TextComponent("A satellite thermal-processing surface.").withStyle(ChatFormatting.GRAY));
                    tooltip.add(new TextComponent(""));
                    tooltip.add(new TextComponent("Must be connected to a central Flux Oven Core via").withStyle(ChatFormatting.BLUE));
                    tooltip.add(new TextComponent("Oven Pipes to function properly in the grid.").withStyle(ChatFormatting.BLUE));
                }
            });

    public static final RegistryObject<Item> CHUNK_LOADER_UPGRADE = ITEMS.register("chunk_loader_upgrade",
            () -> new Item(new Item.Properties().tab(FLUX_OVEN_TAB).stacksTo(1)) {
                @Override
                @OnlyIn(Dist.CLIENT)
                public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(new TextComponent("Flux Oven Core Utility Module").withStyle(ChatFormatting.GRAY));
                    tooltip.add(new TextComponent(""));
                    tooltip.add(new TextComponent("Acts as a dimensional anchor, forcing the entire").withStyle(ChatFormatting.BLUE));
                    tooltip.add(new TextComponent("multiblock processing grid to remain active 24/7.").withStyle(ChatFormatting.BLUE));
                    tooltip.add(new TextComponent(""));
                    tooltip.add(new TextComponent("Anchor Parameters:").withStyle(ChatFormatting.DARK_GREEN));
                    tooltip.add(new TextComponent(" Max Capacity: 1 Module").withStyle(ChatFormatting.DARK_AQUA));
                    tooltip.add(new TextComponent("  • Status: ").withStyle(ChatFormatting.GRAY)
                            .append(new TextComponent("FORCED LOAD").withStyle(ChatFormatting.LIGHT_PURPLE)));
                }
            });

    public static final RegistryObject<Item> SPEED_UPGRADE = ITEMS.register("speed_upgrade",
            () -> new Item(new Item.Properties().tab(FLUX_OVEN_TAB).stacksTo(64)) {
                @Override
                @OnlyIn(Dist.CLIENT)
                public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(new TextComponent("Flux Oven Core Utility Module").withStyle(ChatFormatting.GRAY));
                    tooltip.add(new TextComponent(""));
                    tooltip.add(new TextComponent("Accelerates thermal decomposition cycles across").withStyle(ChatFormatting.BLUE));
                    tooltip.add(new TextComponent("all connected satellite channels synchronously.").withStyle(ChatFormatting.BLUE));
                    tooltip.add(new TextComponent(""));
                    tooltip.add(new TextComponent("Operational Multipliers:").withStyle(ChatFormatting.DARK_GREEN));
                    tooltip.add(new TextComponent(" Max Capacity: 64 Modules").withStyle(ChatFormatting.DARK_AQUA));
                    tooltip.add(new TextComponent("  • Per Module: ").withStyle(ChatFormatting.GRAY)
                            .append(new TextComponent("+5.0%").withStyle(ChatFormatting.GOLD))
                            .append(new TextComponent(" processing speed").withStyle(ChatFormatting.GRAY)));
                    tooltip.add(new TextComponent("  • Full Stack: ").withStyle(ChatFormatting.GRAY)
                            .append(new TextComponent("+320.0%").withStyle(ChatFormatting.GOLD))
                            .append(new TextComponent(" processing speed").withStyle(ChatFormatting.GRAY)));
                }
            });

    public static final RegistryObject<Item> EFFICIENCY_UPGRADE = ITEMS.register("efficiency_upgrade",
            () -> new Item(new Item.Properties().tab(FLUX_OVEN_TAB).stacksTo(64)) {
                @Override
                @OnlyIn(Dist.CLIENT)
                public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(new TextComponent("Flux Oven Core Utility Module").withStyle(ChatFormatting.GRAY));
                    tooltip.add(new TextComponent(""));
                    tooltip.add(new TextComponent("Optimizes the power regulation backplane of the").withStyle(ChatFormatting.BLUE));
                    tooltip.add(new TextComponent("central core, minimizing overall FE consumption.").withStyle(ChatFormatting.BLUE));
                    tooltip.add(new TextComponent(""));
                    tooltip.add(new TextComponent("Efficiency Bonus:").withStyle(ChatFormatting.DARK_GREEN));
                    tooltip.add(new TextComponent(" Max Capacity: 64 Modules").withStyle(ChatFormatting.DARK_AQUA));
                    tooltip.add(new TextComponent("  • Per Module: ").withStyle(ChatFormatting.GRAY)
                            .append(new TextComponent("-0.62%").withStyle(ChatFormatting.GOLD))
                            .append(new TextComponent(" FE/tick draw").withStyle(ChatFormatting.GRAY)));
                    tooltip.add(new TextComponent("  • Full Stack: ").withStyle(ChatFormatting.GRAY)
                            .append(new TextComponent("-39.68%").withStyle(ChatFormatting.GOLD))
                            .append(new TextComponent(" FE/tick draw").withStyle(ChatFormatting.GRAY)));
                    tooltip.add(new TextComponent("  • Total Power Draw: ").withStyle(ChatFormatting.GRAY)
                            .append(new TextComponent("60.32%").withStyle(ChatFormatting.GREEN)));
                }
            });

    public static final RegistryObject<Item> CONNECTION_UPGRADE = ITEMS.register("connection_upgrade",
            () -> new Item(new Item.Properties().tab(FLUX_OVEN_TAB).stacksTo(64)) {
                @Override
                @OnlyIn(Dist.CLIENT)
                public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(new TextComponent("Flux Oven Core Utility Module").withStyle(ChatFormatting.GRAY));
                    tooltip.add(new TextComponent(""));
                    tooltip.add(new TextComponent("Expands the signal bus of the central backplane,").withStyle(ChatFormatting.BLUE));
                    tooltip.add(new TextComponent("allowing more peripheral ovens to be managed.").withStyle(ChatFormatting.BLUE));
                    tooltip.add(new TextComponent(""));
                    tooltip.add(new TextComponent("Network Parameters:").withStyle(ChatFormatting.DARK_GREEN));
                    tooltip.add(new TextComponent(" Base Limit: 9 Linked Plates").withStyle(ChatFormatting.DARK_AQUA));
                    tooltip.add(new TextComponent("  • Per Module: ").withStyle(ChatFormatting.GRAY)
                            .append(new TextComponent("+2").withStyle(ChatFormatting.GOLD))
                            .append(new TextComponent(" Max usable plate channels").withStyle(ChatFormatting.GRAY)));
                    tooltip.add(new TextComponent("  • Full Stack: ").withStyle(ChatFormatting.GRAY)
                            .append(new TextComponent("+128").withStyle(ChatFormatting.GOLD))
                            .append(new TextComponent(" Max usable plate channels").withStyle(ChatFormatting.GRAY)));
                    tooltip.add(new TextComponent("  • Total Network Capability: ").withStyle(ChatFormatting.GRAY)
                            .append(new TextComponent("137 Plates").withStyle(ChatFormatting.GREEN)));
                }
            });

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}