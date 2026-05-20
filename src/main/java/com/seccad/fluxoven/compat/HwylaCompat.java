package com.seccad.fluxoven.compat;

import com.seccad.fluxoven.tileentity.FluxOvenCoreTile;
import com.seccad.fluxoven.tileentity.OvenPlateTileEntity;
import mcp.mobius.waila.api.IComponentProvider;
import mcp.mobius.waila.api.IDataAccessor;
import mcp.mobius.waila.api.IRegistrar;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.TooltipPosition;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class HwylaCompat implements IComponentProvider {

    private static final HwylaCompat INSTANCE = new HwylaCompat();

    public static void register(IRegistrar registrar) {
        registrar.registerComponentProvider(INSTANCE, TooltipPosition.BODY, FluxOvenCoreTile.class);
        registrar.registerComponentProvider(INSTANCE, TooltipPosition.BODY, OvenPlateTileEntity.class);
    }

    @Override
    public void appendBody(List<ITextComponent> tooltip, IDataAccessor accessor, IPluginConfig config) {
        CompoundNBT data = null;

        if (accessor.getTileEntity() instanceof FluxOvenCoreTile) {
            data = accessor.getServerData();
        }
        else if (accessor.getTileEntity() instanceof OvenPlateTileEntity) {
            if (accessor.getServerData().contains("CoreData")) {
                data = accessor.getServerData().getCompound("CoreData");
            }
        }

        if (data != null && data.contains("energyStored")) {
            renderTooltip(tooltip, data);
        }
    }

    private void renderTooltip(List<ITextComponent> tooltip, CompoundNBT nbt) {
        int currentEnergy = nbt.getInt("energyStored");
        int maxEnergy = 100000; // Match your Core's capacity

        tooltip.add(new StringTextComponent("Energy: ")
                .append(new StringTextComponent(String.format("%,d / %,d FE", currentEnergy, maxEnergy))
                        .withStyle(TextFormatting.GREEN)));

        int plates = nbt.getInt("connectedPlates");
        tooltip.add(new StringTextComponent("Connected Plates: ")
                .append(new StringTextComponent(String.valueOf(plates))
                        .withStyle(TextFormatting.AQUA)));

        if (nbt.contains("avgCons")) {
            int cons = nbt.getInt("avgCons");
            if (cons > 0) {
                tooltip.add(new StringTextComponent("Consumption: ")
                        .append(new StringTextComponent(cons + " FE/t")
                                .withStyle(TextFormatting.YELLOW)));
            }
        }
    }
}