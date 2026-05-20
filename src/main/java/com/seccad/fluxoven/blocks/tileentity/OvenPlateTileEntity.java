package com.seccad.fluxoven.blocks.tileentity;

import com.seccad.fluxoven.init.TileInit;
import com.seccad.fluxoven.blocks.OvenPlateBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class OvenPlateTileEntity extends BlockEntity {

    private BlockPos corePos;
    private int plateIndex = -1;

    public final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        protected void onContentsChanged(int slot) {
            markForUpdate();
        }
    };

    private final LazyOptional<IItemHandler> inventoryCapability = LazyOptional.of(() -> inventory);

    public OvenPlateTileEntity(BlockPos pos, BlockState state) {
        super(TileInit.OVEN_PLATE_TILE.get(), pos, state);
    }

    // Static server/client ticker loop used natively by 1.17 Block classes
    public static void tick(Level level, BlockPos pos, BlockState state, OvenPlateTileEntity blockEntity) {
        blockEntity.internalTick();
    }

    private void internalTick() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }

        BlockState state = this.getBlockState();
        if (state.hasProperty(OvenPlateBlock.LIT)) {
            boolean working = this.isWorking();
            boolean currentlyLit = state.getValue(OvenPlateBlock.LIT);

            if (currentlyLit != working) {
                Direction currentFacing = state.getValue(OvenPlateBlock.FACING);
                BlockState newState = state.setValue(OvenPlateBlock.LIT, working).setValue(OvenPlateBlock.FACING, currentFacing);

                this.level.setBlock(this.worldPosition, newState, 2 | 4 | 16);
                this.level.sendBlockUpdated(this.worldPosition, state, newState, 2 | 4 | 16);
            }
        }
    }

    public boolean isWorking() {
        if (this.level == null || this.corePos == null || this.plateIndex == -1) {
            return false;
        }

        FluxOvenCoreTile core = getCoreTile();
        if (core == null || core.energy == null || core.energy.getEnergyStored() <= 0) {
            return false;
        }

        boolean hasInputItems = !inventory.getStackInSlot(0).isEmpty() || !inventory.getStackInSlot(1).isEmpty();
        if (!hasInputItems) {
            return false;
        }

        int channelA = this.plateIndex * 2;
        int channelB = (this.plateIndex * 2) + 1;

        return core.getCookProgressForPlate(channelA) > 0 || core.getCookProgressForPlate(channelB) > 0;
    }

    public void setCookingVisuals(boolean isCooking) {
        if (this.level == null || this.level.isClientSide) return;

        BlockState currentState = this.getBlockState();
        if (currentState.hasProperty(OvenPlateBlock.LIT)) {
            if (currentState.getValue(OvenPlateBlock.LIT) != isCooking) {
                Direction currentFacing = currentState.getValue(OvenPlateBlock.FACING);
                BlockState newState = currentState.setValue(OvenPlateBlock.LIT, isCooking).setValue(OvenPlateBlock.FACING, currentFacing);

                this.level.setBlock(this.worldPosition, newState, 2 | 4 | 16);
                this.level.sendBlockUpdated(this.worldPosition, currentState, newState, 2 | 4 | 16);
            }
        }
    }

    public boolean isCorePowered() {
        FluxOvenCoreTile core = getCoreTile();
        return core != null && core.energy != null && core.energy.getEnergyStored() > 0;
    }

    public float getSmoothProgress(int subSlot) {
        FluxOvenCoreTile core = getCoreTile();
        if (core != null && this.plateIndex != -1) {
            int targetSlot = (this.plateIndex * 2) + subSlot;
            return core.getCookProgressForPlate(targetSlot);
        }
        return 0.0f;
    }

    @Nullable
    public FluxOvenCoreTile getCoreTile() {
        if (this.level != null && this.corePos != null) {
            if (this.level.isLoaded(this.corePos)) {
                BlockEntity te = this.level.getBlockEntity(this.corePos);
                if (te instanceof FluxOvenCoreTile) {
                    return (FluxOvenCoreTile) te;
                }
            }
        }
        return null;
    }

    @Nonnull
    public ItemStack[] getDisplayedItems() {
        ItemStack[] defaultStacks = new ItemStack[]{
                inventory.getStackInSlot(0),
                inventory.getStackInSlot(1),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        };

        FluxOvenCoreTile core = getCoreTile();
        if (core == null || this.plateIndex == -1) {
            return defaultStacks;
        }

        int outputStart = 516 + (this.plateIndex * 2);

        if (core.itemHandler != null && outputStart + 1 < core.itemHandler.getSlots()) {
            defaultStacks[2] = core.itemHandler.getStackInSlot(outputStart);
            defaultStacks[3] = core.itemHandler.getStackInSlot(outputStart + 1);
        }

        return defaultStacks;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            BlockState state = getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, 2 | 4 | 16);
        }
    }

    public void setCoreInfo(@Nullable BlockPos pos, int index) {
        this.corePos = pos;
        this.plateIndex = index;
        this.markForUpdate();
    }

    public void dropInventoryContents() {
        if (this.level == null || this.level.isClientSide) return;

        FluxOvenCoreTile core = getCoreTile();

        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                ItemStack remainder = stack.copy();

                if (core != null && core.recoveryHandler != null) {
                    remainder = ItemHandlerHelper.insertItemStacked(core.recoveryHandler, remainder, false);
                }

                if (!remainder.isEmpty()) {
                    double x = this.worldPosition.getX() + 0.5D;
                    double y = this.worldPosition.getY() + 0.5D;
                    double z = this.worldPosition.getZ() + 0.5D;
                    ItemEntity entity = new ItemEntity(this.level, x, y, z, remainder);
                    entity.setDefaultPickUpDelay();
                    this.level.addFreshEntity(entity);
                }

                inventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    public void markForUpdate() {
        this.setChanged();
    }

    @Override
    @Nonnull
    public CompoundTag getUpdateTag() {
        return this.save(new CompoundTag());
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return new ClientboundBlockEntityDataPacket(this.worldPosition, -1, this.getUpdateTag());
    }

    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (this.level != null) {
            this.load(pkt.getTag());
        }
    }

    @Override
    public void load(@Nonnull CompoundTag nbt) {
        super.load(nbt);
        this.inventory.deserializeNBT(nbt.getCompound("LocalPlateInv"));
        this.plateIndex = nbt.getInt("PlateIndex");

        if (nbt.contains("coreX", Tag.TAG_INT)) {
            this.corePos = new BlockPos(
                    nbt.getInt("coreX"),
                    nbt.getInt("coreY"),
                    nbt.getInt("coreZ")
            );
        } else {
            this.corePos = null;
        }
    }

    @Override
    public CompoundTag save(@Nonnull CompoundTag nbt) {
        super.save(nbt);
        nbt.put("LocalPlateInv", this.inventory.serializeNBT());
        nbt.putInt("PlateIndex", this.plateIndex);

        if (this.corePos != null) {
            nbt.putInt("coreX", this.corePos.getX());
            nbt.putInt("coreY", this.corePos.getY());
            nbt.putInt("coreZ", this.corePos.getZ());
        }

        return nbt;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return inventoryCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    public BlockPos getCorePos() {
        return this.corePos;
    }

    public int getPlateIndex() {
        return this.plateIndex;
    }

    public boolean isConnected() {
        return this.corePos != null && this.plateIndex != -1;
    }

    public void forceVisualUpdate() {
        if (this.level != null) {
            this.markForUpdate();
        }
    }
}