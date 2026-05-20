package com.seccad.fluxoven.tileentity;

import com.seccad.fluxoven.init.TileInit;
import com.seccad.fluxoven.blocks.OvenPlateBlock;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.item.crafting.FurnaceRecipe;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

public class OvenPlateTile extends TileEntity implements ITickableTileEntity {

    public final ItemStackHandler inventory = new ItemStackHandler(4) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2 | 4 | 16);
            }
        }
    };

    private final LazyOptional<IItemHandler> handler = LazyOptional.of(() -> inventory);

    private int[] cookTime = new int[2];
    private int[] cookTimeTotal = new int[2];

    public OvenPlateTile() {
        super(TileInit.OVEN_PLATE_TILE.get());
    }

    @Override
    public void tick() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }

        boolean isCurrentlyCooking = this.cookTime[0] > 0 || this.cookTime[1] > 0;

        BlockState state = this.getBlockState();
        if (state.hasProperty(OvenPlateBlock.LIT)) {
            boolean isBlockLit = state.getValue(OvenPlateBlock.LIT);

            if (isCurrentlyCooking != isBlockLit) {
                this.level.setBlock(this.worldPosition, state.setValue(OvenPlateBlock.LIT, isCurrentlyCooking), 2 | 4);
                this.setChanged();

                this.level.sendBlockUpdated(this.worldPosition, state, state.setValue(OvenPlateBlock.LIT, isCurrentlyCooking), 2 | 4 | 16);
            }
        }
    }

    public void attemptCook(int speed) {
        if (level == null || level.isClientSide) return;

        boolean structureChanged = false;

        for (int i = 0; i < 2; i++) {
            ItemStack input = inventory.getStackInSlot(i);
            int outputSlot = i + 2;

            if (!input.isEmpty()) {
                Optional<FurnaceRecipe> recipe = level.getRecipeManager().getRecipeFor(
                        IRecipeType.SMELTING,
                        new net.minecraft.inventory.Inventory(input),
                        level
                );

                if (recipe.isPresent()) {
                    ItemStack result = recipe.get().getResultItem();
                    ItemStack currentOutput = inventory.getStackInSlot(outputSlot);

                    if (currentOutput.isEmpty() || (currentOutput.sameItem(result) && currentOutput.getCount() < currentOutput.getMaxStackSize())) {

                        this.cookTime[i] += speed;
                        this.cookTimeTotal[i] = recipe.get().getCookingTime();

                        if (this.cookTime[i] >= this.cookTimeTotal[i]) {
                            input.shrink(1);
                            if (currentOutput.isEmpty()) {
                                inventory.setStackInSlot(outputSlot, result.copy());
                            } else {
                                currentOutput.grow(1);
                            }
                            this.cookTime[i] = 0;
                            structureChanged = true;
                        }
                    } else {
                        if (this.cookTime[i] != 0) {
                            this.cookTime[i] = 0;
                            structureChanged = true;
                        }
                    }
                } else {
                    if (this.cookTime[i] != 0) {
                        this.cookTime[i] = 0;
                        structureChanged = true;
                    }
                }
            } else {
                if (this.cookTime[i] != 0) {
                    this.cookTime[i] = 0;
                    structureChanged = true;
                }
            }
        }

        if (structureChanged) {
            this.setChanged();
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, 2 | 4 | 16);
        }
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return handler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void load(BlockState state, CompoundNBT nbt) {
        super.load(state, nbt);
        inventory.deserializeNBT(nbt.getCompound("Inventory"));
        this.cookTime = nbt.getIntArray("CookTimes");
        this.cookTimeTotal = nbt.getIntArray("CookTotals");
    }

    @Override
    @Nonnull
    public CompoundNBT save(CompoundNBT nbt) {
        super.save(nbt);
        nbt.put("Inventory", inventory.serializeNBT());
        nbt.putIntArray("CookTimes", this.cookTime);
        nbt.putIntArray("CookTotals", this.cookTimeTotal);
        return nbt;
    }

    @Nullable
    @Override
    public SUpdateTileEntityPacket getUpdatePacket() {
        return new SUpdateTileEntityPacket(this.worldPosition, 0, this.getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
        this.load(this.getBlockState(), pkt.getTag());
        if (this.level != null && this.level.isClientSide) {
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, 2 | 4);
        }
    }

    @Override
    @Nonnull
    public CompoundNBT getUpdateTag() {
        CompoundNBT nbt = new CompoundNBT();
        this.save(nbt);
        return nbt;
    }
}