package com.seccad.fluxoven.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.seccad.fluxoven.FluxOven;
import com.seccad.fluxoven.inventory.container.FluxOvenCoreContainer;
import com.seccad.fluxoven.network.CPacketSyncOvenGui;
import com.seccad.fluxoven.network.FluxOvenPacketHandler;
import com.seccad.fluxoven.tileentity.FluxOvenCoreTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.ChatFormatting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class FluxOvenCoreScreen extends AbstractContainerScreen<FluxOvenCoreContainer> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(FluxOven.MOD_ID, "textures/gui/oven_core_gui.png");
    private static final ResourceLocation WIDGETS = new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");

    private Button prevPageBtn;
    private Button nextPageBtn;

    public FluxOvenCoreScreen(FluxOvenCoreContainer container, Inventory inv, Component title) {
        super(container, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = -1000;
        this.inventoryLabelX = -1000;

        int x = this.leftPos;
        int y = this.topPos;

        this.addRenderableWidget(new TabButton(x - 62, y + 10, "Upgrades", 0, this));
        this.addRenderableWidget(new TabButton(x - 62, y + 32, "Input", 1, this));
        this.addRenderableWidget(new TabButton(x - 62, y + 54, "Output", 2, this));
        this.addRenderableWidget(new TabButton(x - 62, y + 76, "Recovery", 3, this));
        this.addRenderableWidget(new TabButton(x - 62, y + 98, "System", 4, this));

        this.prevPageBtn = this.addRenderableWidget(new Button(x + 35, y + 70, 20, 12, new TextComponent("<"), b -> handlePageNavigation(-1)));
        this.nextPageBtn = this.addRenderableWidget(new Button(x + 121, y + 70, 20, 12, new TextComponent(">"), b -> handlePageNavigation(1)));
    }

    private void handlePageNavigation(int direction) {
        int currentTab = this.menu.getCurrentTab();
        int currentPage = this.menu.getCurrentPage();
        int connUpgrades = this.menu.getSlot(3).getItem().getCount();
        int limit = FluxOvenCoreTile.BASE_CONNECTION_LIMIT + (connUpgrades * 2);
        int activeSlots = Math.min(this.menu.getConnectedPlates(), limit) * 2;
        int maxPages = (activeSlots > 0) ? (activeSlots - 1) / 15 : 0;

        if (direction > 0 && currentPage < maxPages) {
            this.menu.setPage(currentPage + 1);
            syncGuiStateToServer(currentTab, currentPage + 1);
        } else if (direction < 0 && currentPage > 0) {
            this.menu.setPage(currentPage - 1);
            syncGuiStateToServer(currentTab, currentPage - 1);
        }
    }

    public void syncGuiStateToServer(int tab, int page) {
        FluxOvenPacketHandler.sendToServer(new CPacketSyncOvenGui(tab, page));
    }

    @Override
    protected void renderBg(@Nonnull PoseStack matrixStack, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int x = this.leftPos;
        int y = this.topPos;

        RenderSystem.setShaderTexture(0, TEXTURE);
        blit(matrixStack, x, y, 0, 0, this.imageWidth, this.imageHeight, 176, 166);

        int activeTab = this.menu.getCurrentTab();

        if (activeTab == 0) {
            this.drawEnergyBar(matrixStack, x, y);
            drawSlotFrame(matrixStack, x + 69, y + 23);
            drawSlotFrame(matrixStack, x + 87, y + 23);
            drawSlotFrame(matrixStack, x + 69, y + 41);
            drawSlotFrame(matrixStack, x + 87, y + 41);
        } else if (activeTab == 1 || activeTab == 2) {
            this.drawInventorySlots(matrixStack, x, y);
        } else if (activeTab == 3) {
            RenderSystem.setShaderTexture(0, WIDGETS);
            for (int i = 0; i < 27; i++) {
                blit(matrixStack, x + 7 + ((i % 9) * 18), y + 17 + ((i / 9) * 18), 7, 17, 18, 18, 256, 256);
            }
        }

        this.prevPageBtn.visible = (activeTab == 1 || activeTab == 2);
        this.nextPageBtn.visible = (activeTab == 1 || activeTab == 2);
    }

    private void drawSlotFrame(PoseStack stack, int sx, int sy) {
        fill(stack, sx + 1, sy + 1, sx + 19, sy + 19, 0xFF101010);
        fill(stack, sx, sy, sx + 18, sy + 18, 0xFF252525);
        fill(stack, sx + 1, sy + 1, sx + 17, sy + 17, 0xFF8B8B8B);
        fill(stack, sx + 1, sy + 1, sx + 17, sy + 2, 0xFF373737);
        fill(stack, sx + 1, sy + 1, sx + 2, sy + 17, 0xFF373737);
    }

    private void drawEnergyBar(PoseStack stack, int x, int y) {
        RenderSystem.setShaderTexture(0, WIDGETS);
        blit(stack, x + 155, y + 14, 7, 17, 10, 42, 256, 256);

        int energy = this.menu.getEnergy();
        int max = this.menu.getMaxEnergy();
        int scaled = (max > 0) ? (int)((long)energy * 40 / max) : 0;

        if (scaled > 0) {
            RenderSystem.setShaderColor(1.0F, 0.2F, 0.2F, 1.0F);
            RenderSystem.setShaderTexture(0, TEXTURE);
            blit(stack, x + 156, y + 15 + (40 - scaled), 176, 40 - scaled, 8, scaled, 176, 166);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void drawInventorySlots(PoseStack stack, int x, int y) {
        RenderSystem.setShaderTexture(0, WIDGETS);
        int connUpgrades = this.menu.getSlot(3).getItem().getCount();
        int workLimit = (FluxOvenCoreTile.BASE_CONNECTION_LIMIT + (connUpgrades * 2)) * 2;
        int totalActive = Math.min(this.menu.getConnectedPlates() * 2, workLimit);
        int pageOffset = this.menu.getCurrentPage() * 15;

        for (int i = 0; i < 15; i++) {
            if (pageOffset + i >= totalActive) break;
            blit(stack, x + 48 + ((i % 5) * 18), y + 19 + ((i / 5) * 18), 7, 17, 18, 18, 256, 256);
        }
    }

    @Override
    protected void renderLabels(@Nonnull PoseStack matrixStack, int mouseX, int mouseY) {
        int tab = this.menu.getCurrentTab();
        String title = (tab == 0) ? "System Upgrades" : (tab == 1) ? "Input Stream" :
                (tab == 2) ? "Output Stream" : (tab == 3) ? "Recovery" : "System";

        float titlePos = (float)(this.imageWidth / 2 - this.font.width(title) / 2);
        this.font.draw(matrixStack, title, titlePos, 6.0F, 0x202020);

        if (tab == 4) {
            renderEfficiencyMetrics(matrixStack);
        } else if (tab == 1 || tab == 2) {
            String pageInfo = "Page " + (this.menu.getCurrentPage() + 1);
            this.font.draw(matrixStack, pageInfo, 76, 73, 0x404040);
        }
    }

    private void renderEfficiencyMetrics(PoseStack stack) {
        int speed = this.menu.getSlot(1).getItem().getCount();
        int eff = this.menu.getSlot(2).getItem().getCount();
        int conn = this.menu.getSlot(3).getItem().getCount();

        fill(stack, 12, 18, 164, 78, 0xAA101010);

        int lineY = 22;
        this.font.draw(stack, "Active Plates: " + this.menu.getConnectedPlates(), 18, lineY, 0xFFFFFF);
        this.font.draw(stack, "Plate Limit: " + (FluxOvenCoreTile.BASE_CONNECTION_LIMIT + (conn * 2)), 18, lineY + 10, 0xAAAAAA);
        this.font.draw(stack, "Process Speed: " + (100 + (speed * 5)) + "%", 18, lineY + 24, 0x55FF55);
        this.font.draw(stack, "Energy Usage: " + Math.max(1, (100 - (eff * 1))) + "%", 18, lineY + 34, 0x55FFFF);

        boolean isAnchored = this.menu.getSlot(0).hasItem();
        this.font.draw(stack, isAnchored ? "LINK: ESTABLISHED" : "LINK: OFFLINE", 18, 66, isAnchored ? 0xFFAA00 : 0x777777);
    }

    @Override
    public void render(@Nonnull PoseStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(matrixStack);
        super.render(matrixStack, mouseX, mouseY, partialTicks);
        this.renderTooltip(matrixStack, mouseX, mouseY);
    }

    @Override
    protected void renderTooltip(@Nonnull PoseStack matrixStack, int mouseX, int mouseY) {
        int tab = this.menu.getCurrentTab();

        if (isHovering(155, 14, 10, 42, mouseX, mouseY)) {
            List<Component> energyInfo = new ArrayList<>();
            energyInfo.add(new TextComponent("Internal Buffer").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            energyInfo.add(new TextComponent(String.format("%,d / %,d FE", this.menu.getEnergy(), this.menu.getMaxEnergy())));
            energyInfo.add(new TextComponent("Avg. Consumption: " + this.menu.getAvgConsumption() + " FE/t").withStyle(ChatFormatting.GRAY));
            this.renderComponentTooltip(matrixStack, energyInfo, mouseX, mouseY);
        }

        if (tab == 0) {
            if (isHovering(69, 23, 18, 18, mouseX, mouseY) && !this.menu.getSlot(0).hasItem())
                renderSimpleTip(matrixStack, "Dimensional Anchor", "Maintains chunk loading.", ChatFormatting.DARK_PURPLE, mouseX, mouseY);
            else if (isHovering(87, 23, 18, 18, mouseX, mouseY) && !this.menu.getSlot(1).hasItem())
                renderSimpleTip(matrixStack, "Speed Module", "Reduces processing time.", ChatFormatting.RED, mouseX, mouseY);
            else if (isHovering(69, 41, 18, 18, mouseX, mouseY) && !this.menu.getSlot(2).hasItem())
                renderSimpleTip(matrixStack, "Economy Module", "Reduces power cost.", ChatFormatting.GREEN, mouseX, mouseY);
            else if (isHovering(87, 41, 18, 18, mouseX, mouseY) && !this.menu.getSlot(3).hasItem())
                renderSimpleTip(matrixStack, "Connection Module", "Expands plate capacity.", ChatFormatting.AQUA, mouseX, mouseY);
        }
        super.renderTooltip(matrixStack, mouseX, mouseY);
    }

    private void renderSimpleTip(PoseStack stack, String head, String body, ChatFormatting color, int mx, int my) {
        List<Component> tips = new ArrayList<>();
        tips.add(new TextComponent(head).withStyle(color, ChatFormatting.BOLD));
        tips.add(new TextComponent(body).withStyle(ChatFormatting.GRAY));
        this.renderComponentTooltip(stack, tips, mx, my);
    }

    private static class TabButton extends Button {
        private final int tabIdx;
        private final FluxOvenCoreScreen parent;

        public TabButton(int x, int y, String name, int idx, FluxOvenCoreScreen screen) {
            super(x, y, 60, 20, new TextComponent(name), (b) -> {
                screen.menu.setTab(idx);
                screen.syncGuiStateToServer(idx, 0);
            });
            this.tabIdx = idx;
            this.parent = screen;
        }

        @Override
        public void renderButton(@Nonnull PoseStack stack, int mouseX, int mouseY, float partialTicks) {
            boolean isSelected = (parent.menu.getCurrentTab() == this.tabIdx);
            fill(stack, this.x, this.y, this.x + this.width, this.y + this.height, isSelected ? 0xDD000000 : 0x88000000);
            if (isSelected) fill(stack, this.x, this.y, this.x + 2, this.y + this.height, 0xFFFFAA00);
            int color = isSelected ? 0xFFFFAA00 : (this.isHovered() ? 0xFFFFFFFF : 0xFF808080);
            drawCenteredString(stack, Minecraft.getInstance().font, this.getMessage(), this.x + this.width / 2, this.y + (this.height - 8) / 2, color);
        }
    }
}