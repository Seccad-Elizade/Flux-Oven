package com.seccad.fluxoven;

import com.seccad.fluxoven.client.render.OvenPlateRenderer;
import com.seccad.fluxoven.client.gui.FluxOvenCoreScreen;
import com.seccad.fluxoven.init.BlockInit;
import com.seccad.fluxoven.init.ContainerInit;
import com.seccad.fluxoven.init.ItemInit;
import com.seccad.fluxoven.init.TileInit;
import com.seccad.fluxoven.network.FluxOvenPacketHandler;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(FluxOven.MOD_ID)
public class FluxOven {

    public static final String MOD_ID = "fluxoven";

    public static final Logger LOGGER = LogManager.getLogger();

    public static final CreativeModeTab TAB = new CreativeModeTab("flux_oven_tab") {
        @Override
        @OnlyIn(Dist.CLIENT)
        public ItemStack makeIcon() {
            return new ItemStack(BlockInit.FLUX_OVEN_CORE.get());
        }
    };

    public FluxOven() {
        final IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        BlockInit.BLOCKS.register(bus);
        ItemInit.ITEMS.register(bus);
        TileInit.TILE_ENTITIES.register(bus);
        ContainerInit.CONTAINERS.register(bus);

        bus.addListener(this::setup);
        bus.addListener(this::enqueueIMC);
        bus.addListener(this::doClientStuff);
        bus.addListener(this::registerRenderers);

        FluxOvenPacketHandler.register();

        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("FluxOven: Mod Instance constructed. Creative tab registries unified.");
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("FluxOven: Initializing Common Setup (Capabilities and Networking)...");
        });
    }

    private void enqueueIMC(final InterModEnqueueEvent event) {
        LOGGER.info("FluxOven: Sending IMC messages to external mods...");

        InterModComms.sendTo("hwyla", "register",
                () -> "com.seccad.fluxoven.compat.HwylaCompat::register");
    }

    @OnlyIn(Dist.CLIENT)
    private void doClientStuff(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ContainerInit.FLUX_OVEN_CORE_CONTAINER.get(), FluxOvenCoreScreen::new);

            LOGGER.info("FluxOven: Client Rendering and GUI Mappings successfully initialized.");
        });
    }

    @OnlyIn(Dist.CLIENT)
    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(TileInit.OVEN_PLATE_TILE.get(), OvenPlateRenderer::new);
    }

    public static void log(String message) {
        LOGGER.info("[" + MOD_ID.toUpperCase() + "]: " + message);
    }
}