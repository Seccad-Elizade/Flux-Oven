package com.seccad.fluxoven.init;

import com.seccad.fluxoven.FluxOven;
import com.seccad.fluxoven.tileentity.FluxOvenCoreTile;
import com.seccad.fluxoven.tileentity.OvenPlateTileEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.RegistryObject; // Correct package location for 1.17 Forge toolchains

public class TileInit {

    public static final DeferredRegister<BlockEntityType<?>> TILE_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITIES, FluxOven.MOD_ID);

    public static final RegistryObject<BlockEntityType<OvenPlateTileEntity>> OVEN_PLATE_TILE = TILE_ENTITIES.register("oven_plate_tile",
            () -> BlockEntityType.Builder.of(OvenPlateTileEntity::new, BlockInit.OVEN_PLATE.get()).build(null));

    public static final RegistryObject<BlockEntityType<FluxOvenCoreTile>> FLUX_OVEN_CORE_TILE = TILE_ENTITIES.register("flux_oven_core_tile",
            () -> BlockEntityType.Builder.of(FluxOvenCoreTile::new, BlockInit.FLUX_OVEN_CORE.get()).build(null));
}