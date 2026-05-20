package com.seccad.fluxoven.inventory.container;

import com.seccad.fluxoven.init.BlockInit;
import com.seccad.fluxoven.init.ContainerInit;
import com.seccad.fluxoven.tileentity.FluxOvenCoreTile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nonnull;
import java.util.Objects;

public class FluxOvenCoreContainer extends AbstractContainerMenu {

    private final FluxOvenCoreTile tileEntity;
    private final ContainerLevelAccess canInteractWith;
    private final ContainerData data;

    private int currentTab = 0;
    private int currentPage = 0;
    private final int connectedPlates;

    private final int upgradeSlotsStart = 0;
    private final int upgradeSlotsEnd = 4;
    private final int inputSlotsStart = 4;
    private final int inputSlotsEnd = 516;
    private final int outputSlotsStart = 516;
    private final int outputSlotsEnd = 1028;
    private final int recoverySlotsStart = 1028;
    private final int recoverySlotsEnd = 1055;
    private final int playerInventoryStart = 1055;
    private final int playerInventoryEnd = 1082;
    private final int playerHotbarStart = 1082;
    private final int playerHotbarEnd = 1091;

    private final int totalMachineSlots = 1055;

    public FluxOvenCoreContainer(int id, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(id, playerInventory, getTileEntity(playerInventory, buffer), new SimpleContainerData(3));
    }

    public FluxOvenCoreContainer(int id, Inventory playerInventory, FluxOvenCoreTile tileEntity) {
        this(id, playerInventory, tileEntity, createDataArray(tileEntity));
    }

    public FluxOvenCoreContainer(int id, Inventory playerInventory, FluxOvenCoreTile tileEntity, ContainerData data) {
        super(ContainerInit.FLUX_OVEN_CORE_CONTAINER.get(), id);
        checkContainerDataCount(data, 3);

        this.tileEntity = tileEntity;
        this.data = data;
        this.canInteractWith = ContainerLevelAccess.create(Objects.requireNonNull(tileEntity.getLevel()), tileEntity.getBlockPos());
        this.connectedPlates = tileEntity.getConnectedPlates();

        this.addDataSlots(data);

        this.tileEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY).ifPresent(coreHandler -> {
            this.addSlot(new PagingSlot(coreHandler, 0, 71, 25, 0, true, 0)); // Anchor Slot
            this.addSlot(new PagingSlot(coreHandler, 1, 89, 25, 0, true, 0)); // Speed Slot
            this.addSlot(new PagingSlot(coreHandler, 2, 71, 43, 0, true, 0)); // Efficiency Slot
            this.addSlot(new PagingSlot(coreHandler, 3, 89, 43, 0, true, 0)); // Connection Slot
        });

        int connUpgrades = this.tileEntity.itemHandler.getStackInSlot(3).getCount();
        int maxWorkableOvens = FluxOvenCoreTile.BASE_CONNECTION_LIMIT + (connUpgrades * 2);

        for (int i = 0; i < 512; i++) {
            int plateIdx = i / 2;
            int subSlotId = i % 2;

            int pageIdx = i / 15;
            int slotInPage = i % 15;
            int col = slotInPage % 5;
            int row = slotInPage / 5;

            boolean isSlotActive = plateIdx < maxWorkableOvens && this.tileEntity.isPlateIndexLive(plateIdx);
            IItemHandler remotePlateInv = this.tileEntity.getPlateInventory(plateIdx);

            IItemHandler targetInputHandler = (remotePlateInv != null && isSlotActive) ? remotePlateInv : new ItemStackHandler(2);
            this.addSlot(new PagingSlot(targetInputHandler, subSlotId, 48 + (col * 18), 19 + (row * 18), 1, isSlotActive, pageIdx));
        }

        for (int i = 0; i < 512; i++) {
            int pageIdx = i / 15;
            int slotInPage = i % 15;
            int col = slotInPage % 5;
            int row = slotInPage / 5;

            int plateIdx = i / 2;
            boolean isSlotActive = plateIdx < maxWorkableOvens && this.tileEntity.isPlateIndexLive(plateIdx);

            this.addSlot(new PagingSlot(this.tileEntity.itemHandler, 516 + i, 48 + (col * 18), 19 + (row * 18), 2, isSlotActive, pageIdx));
        }

        IItemHandler recoveryHandler = this.tileEntity.recoveryHandler;
        for (int i = 0; i < 27; i++) {
            int col = i % 9;
            int row = i / 9;
            this.addSlot(new PagingSlot(recoveryHandler, i, 8 + (col * 18), 18 + (row * 18), 3, true, 0));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    private class PagingSlot extends SlotItemHandler {
        private final int tabId;
        private final boolean isSlotConnected;
        private final int pageIdx;

        public PagingSlot(IItemHandler itemHandler, int index, int x, int y, int tabId, boolean isConnected, int pageIdx) {
            super(itemHandler, index, x, y);
            this.tabId = tabId;
            this.isSlotConnected = isConnected;
            this.pageIdx = pageIdx;
        }

        @Override
        public boolean isActive() {
            if (tabId == 0) return currentTab == 0;
            if (tabId == 3) return currentTab == 3;
            return isSlotConnected && currentTab == this.tabId && currentPage == this.pageIdx;
        }

        @Override
        public boolean mayPlace(@Nonnull ItemStack stack) {
            if (tabId == 2 || tabId == 3) return false;
            return isSlotConnected && getItemHandler().isItemValid(getSlotIndex(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return getItemHandler().getSlotLimit(getSlotIndex());
        }
    }

    public int getConnectedPlates() { return this.connectedPlates; }
    public void setTab(int tabId) { this.currentTab = tabId; this.currentPage = 0; }
    public void setPage(int pageId) { this.currentPage = pageId; }
    public int getCurrentTab() { return this.currentTab; }
    public int getCurrentPage() { return this.currentPage; }
    public int getEnergy() { return this.data.get(0); }
    public int getMaxEnergy() { return this.data.get(1); }
    public int getAvgConsumption() { return this.data.get(2); }

    @Override
    @Nonnull
    public ItemStack quickMoveStack(@Nonnull Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();

            if (this.currentTab == 3) {
                if (index >= recoverySlotsStart && index < recoverySlotsEnd) {
                    if (!this.moveItemStackTo(itemstack1, playerInventoryStart, playerHotbarEnd, true)) {
                        return ItemStack.EMPTY;
                    }
                    slot.onQuickCraft(itemstack1, itemstack);
                }
                else if (index >= playerInventoryStart && index < playerHotbarEnd) {
                    if (!this.moveItemStackTo(itemstack1, recoverySlotsStart, recoverySlotsEnd, false)) {
                        return ItemStack.EMPTY;
                    }
                }
                else {
                    return ItemStack.EMPTY;
                }
            }

            else {
                if (index >= recoverySlotsStart && index < recoverySlotsEnd) {
                    return ItemStack.EMPTY;
                }

                if (index < totalMachineSlots) {
                    if (!this.moveItemStackTo(itemstack1, playerInventoryStart, playerHotbarEnd, true)) {
                        return ItemStack.EMPTY;
                    }
                }
                else {
                    if (isUpgrade(itemstack1)) {
                        if (!this.moveItemStackTo(itemstack1, upgradeSlotsStart, upgradeSlotsEnd, false)) {
                            return ItemStack.EMPTY;
                        }
                    }
                    else if (isSmeltable(itemstack1)) {
                        int connUpgrades = tileEntity.itemHandler.getStackInSlot(3).getCount();
                        int maxWorkableOvens = FluxOvenCoreTile.BASE_CONNECTION_LIMIT + (connUpgrades * 2);
                        int activeInputsLimit = Math.min(this.connectedPlates, maxWorkableOvens) * 2;

                        if (activeInputsLimit > 0) {
                            if (!this.moveItemStackTo(itemstack1, inputSlotsStart, inputSlotsStart + activeInputsLimit, false)) {
                                return ItemStack.EMPTY;
                            }
                        } else {
                            return ItemStack.EMPTY;
                        }
                    } else {
                        return ItemStack.EMPTY;
                    }
                }
            }

            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, itemstack1);
        }
        return itemstack;
    }

    private boolean isSmeltable(ItemStack stack) {
        if (tileEntity.getLevel() == null) return false;
        return tileEntity.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(stack), tileEntity.getLevel()).isPresent();
    }

    private boolean isUpgrade(ItemStack stack) {
        var loc = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (loc == null) return false;
        String path = loc.getPath();
        return path.contains("chunk_loader") || path.contains("anchor") ||
                path.contains("speed") || path.contains("economy") ||
                path.contains("efficiency") || path.contains("connection");
    }

    private static ContainerData createDataArray(FluxOvenCoreTile tile) {
        return new ContainerData() {
            @Override public int get(int index) {
                switch (index) {
                    case 0: return tile.energy.getEnergyStored();
                    case 1: return tile.energy.getMaxEnergyStored();
                    case 2: return tile.getAvgConsumption();
                    default: return 0;
                }
            }
            @Override public void set(int index, int value) {
                if (index == 2) tile.setAvgConsumptionClient(value);
            }
            @Override public int getCount() { return 3; }
        };
    }

    private static FluxOvenCoreTile getTileEntity(final Inventory playerInventory, final FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        BlockEntity te = playerInventory.player.level.getBlockEntity(pos);
        if (te instanceof FluxOvenCoreTile) return (FluxOvenCoreTile) te;
        throw new IllegalStateException("FluxOvenCoreContainer: Invalid TileEntity context!");
    }

    @Override
    public boolean stillValid(@Nonnull Player player) {
        return stillValid(canInteractWith, player, BlockInit.FLUX_OVEN_CORE.get());
    }
}