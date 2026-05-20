package com.seccad.fluxoven.init;

import com.seccad.fluxoven.FluxOven;
import com.seccad.fluxoven.blocks.FluxOvenCoreBlock;
import com.seccad.fluxoven.blocks.OvenPlateBlock;
import com.seccad.fluxoven.blocks.OvenPipeBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fmllegacy.RegistryObject;

import java.util.function.Supplier;

public class BlockInit {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FluxOven.MOD_ID);

    public static final RegistryObject<Block> OVEN_PLATE = registerBlockOnly("oven_plate",
            () -> new OvenPlateBlock(BlockBehaviour.Properties.of(Material.METAL)
                    .strength(3.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Block> FLUX_OVEN_CORE = registerBlockOnly("flux_oven_core",
            () -> new FluxOvenCoreBlock(BlockBehaviour.Properties.of(Material.METAL)
                    .strength(5.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> OVEN_PIPE = registerBlock("oven_pipe",
            () -> new OvenPipeBlock(BlockBehaviour.Properties.of(Material.METAL)
                    .strength(2.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .isSuffocating((state, reader, pos) -> false)
                    .isViewBlocking((state, reader, pos) -> false)));

    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block) {
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> RegistryObject<T> registerBlockOnly(String name, Supplier<T> block) {
        return BLOCKS.register(name, block);
    }

    private static <T extends Block> void registerBlockItem(String name, RegistryObject<T> block) {
        ItemInit.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties().tab(FluxOven.TAB)));
    }
}