package com.seccad.fluxoven.tileentity;

import com.seccad.fluxoven.init.TileInit;
import com.seccad.fluxoven.blocks.OvenPlateBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

public class OvenPlateTile extends BlockEntity {

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

    public OvenPlateTile(BlockPos pos, BlockState state) {
        super(TileInit.OVEN_PLATE_TILE.get(), pos, state);
    }

    // Static server/client ticker loop used natively by 1.17 Block classes
    public static void tick(Level level, BlockPos pos, BlockState state, OvenPlateTile blockEntity) {
        blockEntity.tick();
    }

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
                Optional<SmeltingRecipe> recipe = level.getRecipeManager().getRecipeFor(
                        RecipeType.SMELTING,
                        new SimpleContainer(input),
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
    public void load(@Nonnull CompoundTag nbt) {
        super.load(nbt);
        inventory.deserializeNBT(nbt.getCompound("Inventory"));
        this.cookTime = nbt.getIntArray("CookTimes");
        this.cookTimeTotal = nbt.getIntArray("CookTotals");
    }

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag nbt) {
        super.save(nbt);
        nbt.put("Inventory", inventory.serializeNBT());
        nbt.putIntArray("CookTimes", this.cookTime);
        nbt.putIntArray("CookTotals", this.cookTimeTotal);
        return nbt;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return new ClientboundBlockEntityDataPacket(this.worldPosition, 0, this.getUpdateTag());
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        this.load(pkt.getTag());
        if (this.level != null && this.level.isClientSide) {
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, 2 | 4);
        }
    }

    @Override
    @Nonnull
    public CompoundTag getUpdateTag() {
        CompoundTag nbt = new CompoundTag();
        this.save(nbt);
        return nbt;
    }
}