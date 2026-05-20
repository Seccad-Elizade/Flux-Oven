package com.seccad.fluxoven.network;

import com.seccad.fluxoven.inventory.container.FluxOvenCoreContainer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fmllegacy.network.NetworkEvent;

import java.util.function.Supplier;

public class CPacketSyncOvenGui {

    private final int tab;
    private final int page;

    public CPacketSyncOvenGui(int tab, int page) {
        this.tab = tab;
        this.page = page;
    }

    public static void encode(CPacketSyncOvenGui msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.tab);
        buf.writeInt(msg.page);
    }

    public static CPacketSyncOvenGui decode(FriendlyByteBuf buf) {
        return new CPacketSyncOvenGui(buf.readInt(), buf.readInt());
    }

    public static void handle(CPacketSyncOvenGui msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.containerMenu instanceof FluxOvenCoreContainer) {
                FluxOvenCoreContainer container = (FluxOvenCoreContainer) player.containerMenu;

                container.setTab(msg.tab);
                container.setPage(msg.page);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}