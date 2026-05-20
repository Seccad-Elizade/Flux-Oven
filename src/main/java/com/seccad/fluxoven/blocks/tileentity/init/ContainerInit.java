package com.seccad.fluxoven.init;

import com.seccad.fluxoven.FluxOven;
import com.seccad.fluxoven.inventory.container.FluxOvenCoreContainer;
import com.seccad.fluxoven.blocks.tileentity.FluxOvenCoreTile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.extensions.IForgeContainerType;
import net.minecraftforge.fmllegacy.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class ContainerInit {

    public static final DeferredRegister<MenuType<?>> CONTAINERS = DeferredRegister.create(ForgeRegistries.CONTAINERS, FluxOven.MOD_ID);

    public static final RegistryObject<MenuType<FluxOvenCoreContainer>> FLUX_OVEN_CORE_CONTAINER = CONTAINERS.register("flux_oven_core",
            () -> IForgeContainerType.create((windowId, inv, data) -> {
                BlockPos pos = data.readBlockPos();
                BlockEntity te = inv.player.level.getBlockEntity(pos);

                if (te instanceof FluxOvenCoreTile) {
                    return new FluxOvenCoreContainer(windowId, inv, (FluxOvenCoreTile) te);
                }

                throw new IllegalStateException("Expected FluxOvenCoreTile at " + pos + " but found " + te);
            }));
}