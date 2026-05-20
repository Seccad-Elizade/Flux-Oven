package com.seccad.fluxoven.blocks.tileentity;

import com.seccad.fluxoven.init.BlockInit;
import com.seccad.fluxoven.init.TileInit;
import com.seccad.fluxoven.inventory.container.FluxOvenCoreContainer;
import com.seccad.fluxoven.blocks.OvenPlateBlock;
import com.seccad.fluxoven.blocks.OvenPipeBlock;
import com.seccad.fluxoven.blocks.FluxOvenCoreBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RangedWrapper;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class FluxOvenCoreTile extends BlockEntity implements MenuProvider {

    public static final int MAX_PLATES_LIMIT = 512;
    public static final int INPUT_CHANNEL_COUNT = 512;
    public static final int OUTPUT_CHANNEL_OFFSET = 516;
    public static final int TOTAL_STORAGE_CAPACITY = 1035;

    private static final int DEFAULT_SCAN_INTERVAL = 20;
    private static final int BASE_FE_CAPACITY = 500000;
    private static final int BASE_FE_TRANSFER = 100000;
    private static final int MIN_WORKING_FE = 100;
    public static final int STANDARD_COOK_TIME = 200;
    private static final int ENERGY_COST_PER_TICK = 25;

    public static final int BASE_CONNECTION_LIMIT = 9;

    private static final float SPEED_MODIFIER_BASE = 0.05f;
    private static final double ECONOMY_MODIFIER_PER_CARD = 0.0062;
    private static final int CONNECTION_STEP_INCREMENT = 2;

    public final CustomEnergyStorage energy = new CustomEnergyStorage(BASE_FE_CAPACITY, BASE_FE_TRANSFER, BASE_FE_TRANSFER);
    private final LazyOptional<IEnergyStorage> energyCapability = LazyOptional.of(() -> energy);

    private int connectedNodeCount = 0;
    private final List<BlockPos> mappedPlatePositions = new ArrayList<>();

    private final Map<BlockPos, Integer> coordinateToSlotMap = new HashMap<>();
    private final boolean[] allocatedSlotsTracker = new boolean[MAX_PLATES_LIMIT / 2];

    private final float[] processingProgress = new float[INPUT_CHANNEL_COUNT];
    private int instantaneousDraw = 0;
    private boolean isActiveProcessing = false;
    private int networkScanTimer = 0;
    private boolean isChunkForced = false;

    public final ItemStackHandler itemHandler = new ItemStackHandler(TOTAL_STORAGE_CAPACITY) {
        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            if (stack.isEmpty()) return false;
            var loc = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (loc == null) return false;
            String path = loc.getPath();

            if (slot < 4) {
                if (slot == 0) return path.contains("chunk_loader") || path.contains("anchor");
                if (slot == 1) return path.contains("speed");
                if (slot == 2) return path.contains("efficiency") || path.contains("economy");
                if (slot == 3) return path.contains("connection");
                return false;
            }

            if (slot >= 4 && slot < OUTPUT_CHANNEL_OFFSET) {
                return isItemSmeltable(stack);
            }

            return false;
        }

        @Override
        protected void onContentsChanged(int slot) {
            if (slot == 0) {
                refreshChunkLoading();
            }
            updateModuleCalibrations();
            markForUpdate();
        }
    };

    public final ItemStackHandler recoveryHandler = new ItemStackHandler(54) {
        @Override
        protected void onContentsChanged(int slot) {
            markForUpdate();
        }
    };

    private final LazyOptional<IItemHandler> inventoryCapabilityGeneral = LazyOptional.of(() -> itemHandler);
    private final LazyOptional<IItemHandler> automationOutputHandler = LazyOptional.of(() -> new RangedWrapper(itemHandler, OUTPUT_CHANNEL_OFFSET, 1028));

    public FluxOvenCoreTile(BlockPos pos, BlockState state) {
        super(TileInit.FLUX_OVEN_CORE_TILE.get(), pos, state);
    }

    private boolean isItemSmeltable(ItemStack stack) {
        if (this.level == null || stack.isEmpty()) return false;
        return this.level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SimpleContainer(stack), this.level)
                .isPresent();
    }

    public void requestTopologyRebuild() {
        this.networkScanTimer = 0;
    }

    private void executeNetworkTopologyScan() {
        if (this.level == null || this.level.isClientSide) return;

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        List<BlockPos> foundPlates = new ArrayList<>();

        queue.add(this.worldPosition);
        visited.add(this.worldPosition);

        while (!queue.isEmpty() && foundPlates.size() < MAX_PLATES_LIMIT) {
            BlockPos current = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);

                if (!this.level.isLoaded(neighbor)) continue;
                if (visited.contains(neighbor)) continue;
                visited.add(neighbor);

                BlockState state = this.level.getBlockState(neighbor);
                if (state == null || state.isAir()) continue;
                Block block = state.getBlock();

                if (block instanceof OvenPlateBlock) {
                    foundPlates.add(neighbor);
                    queue.add(neighbor);
                } else if (block instanceof OvenPipeBlock || block instanceof FluxOvenCoreBlock) {
                    queue.add(neighbor);
                }
            }
        }

        foundPlates.sort((p1, p2) -> {
            if (p1.getY() != p2.getY()) return Integer.compare(p2.getY(), p1.getY());
            if (p1.getZ() != p2.getZ()) return Integer.compare(p1.getZ(), p2.getZ());
            if (p1.getX() != p2.getX()) return Integer.compare(p1.getX(), p2.getX());
            return 0;
        });

        // Use a set for faster lookup
        Set<BlockPos> foundPlatesSet = new HashSet<>(foundPlates);
        
        Iterator<Map.Entry<BlockPos, Integer>> iterator = coordinateToSlotMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Integer> entry = iterator.next();
            BlockPos oldPos = entry.getKey();
            int assignedIndex = entry.getValue();

            if (!foundPlatesSet.contains(oldPos)) {
                if (assignedIndex >= 0 && assignedIndex < allocatedSlotsTracker.length) {
                    allocatedSlotsTracker[assignedIndex] = false;
                }

                int outStart = OUTPUT_CHANNEL_OFFSET + (assignedIndex * 2);
                itemHandler.setStackInSlot(outStart, ItemStack.EMPTY);
                itemHandler.setStackInSlot(outStart + 1, ItemStack.EMPTY);

                if (assignedIndex * 2 < processingProgress.length) {
                    processingProgress[assignedIndex * 2] = 0;
                    processingProgress[(assignedIndex * 2) + 1] = 0;
                }

                if (this.level.isLoaded(oldPos)) {
                    BlockEntity oldTe = this.level.getBlockEntity(oldPos);
                    if (oldTe instanceof OvenPlateTileEntity) {
                        ((OvenPlateTileEntity) oldTe).setCoreInfo(null, -1);
                        ((OvenPlateTileEntity) oldTe).setCookingVisuals(false);
                    }
                }
                iterator.remove();
            }
        }

        synchronized (this.mappedPlatePositions) {
            this.mappedPlatePositions.clear();
            this.mappedPlatePositions.addAll(foundPlates);
        }

        coordinateToSlotMap.clear();
        Arrays.fill(allocatedSlotsTracker, false);
        for (int i = 0; i < foundPlates.size(); i++) {
            BlockPos pos = foundPlates.get(i);
            coordinateToSlotMap.put(pos, i);
            if (i < allocatedSlotsTracker.length) {
                allocatedSlotsTracker[i] = true;
            }
            
            BlockEntity te = this.level.getBlockEntity(pos);
            if (te instanceof OvenPlateTileEntity) {
                OvenPlateTileEntity satellitePlate = (OvenPlateTileEntity) te;
                satellitePlate.setCoreInfo(this.worldPosition, i);
                satellitePlate.markForUpdate();
            }
        }

        if (this.connectedNodeCount != foundPlates.size()) {
            this.connectedNodeCount = foundPlates.size();
            this.markForUpdate();
        }
    }

    @Nullable
    public BlockPos getConnectedPlatePos(int index) {
        for (Map.Entry<BlockPos, Integer> entry : coordinateToSlotMap.entrySet()) {
            if (entry.getValue() == index) return entry.getKey();
        }
        return null;
    }

    public boolean isPlateIndexLive(int index) {
        synchronized (this.mappedPlatePositions) {
            return index >= 0 && index < this.mappedPlatePositions.size();
        }
    }

    public void processPlateRemoval(int traditionalIndex, BlockPos brokenPos) {
        if (this.level == null || this.level.isClientSide) return;

        Integer targetNodeIndex = coordinateToSlotMap.get(brokenPos);
        if (targetNodeIndex == null) {
            targetNodeIndex = traditionalIndex;
        }
        if (targetNodeIndex < 0) return;

        int outStart = OUTPUT_CHANNEL_OFFSET + (targetNodeIndex * 2);
        int[] centralOutputSlots = {outStart, outStart + 1};

        for (int id : centralOutputSlots) {
            if (id < itemHandler.getSlots()) {
                ItemStack stack = itemHandler.getStackInSlot(id);
                if (!stack.isEmpty()) {
                    ItemStack remaining = ItemHandlerHelper.insertItemStacked(recoveryHandler, stack.copy(), false);

                    if (!remaining.isEmpty()) {
                        double dropX = brokenPos.getX() + 0.5D;
                        double dropY = brokenPos.getY() + 0.3D;
                        double dropZ = brokenPos.getZ() + 0.5D;
                        ItemEntity entity = new ItemEntity(this.level, dropX, dropY, dropZ, remaining);
                        entity.setDefaultPickUpDelay();
                        this.level.addFreshEntity(entity);
                    }
                    itemHandler.setStackInSlot(id, ItemStack.EMPTY);
                }
            }
        }

        if ((targetNodeIndex * 2) < processingProgress.length) {
            processingProgress[targetNodeIndex * 2] = 0;
            processingProgress[(targetNodeIndex * 2) + 1] = 0;
        }

        coordinateToSlotMap.remove(brokenPos);
        if (targetNodeIndex < allocatedSlotsTracker.length) {
            allocatedSlotsTracker[targetNodeIndex] = false;
        }

        executeNetworkTopologyScan();
        this.markForUpdate();
    }

    private void refreshChunkLoading() {
        if (this.level == null || this.level.isClientSide || !(this.level instanceof ServerLevel)) return;

        ItemStack loaderStack = itemHandler.getStackInSlot(0);
        var loc = ForgeRegistries.ITEMS.getKey(loaderStack.getItem());
        boolean hasLoader = !loaderStack.isEmpty() && loc != null && loc.getPath().contains("chunk_loader");

        ServerLevel sworld = (ServerLevel) this.level;
        ChunkPos cPos = new ChunkPos(this.worldPosition);

        if (hasLoader && !isChunkForced) {
            sworld.setChunkForced(cPos.x, cPos.z, true);
            isChunkForced = true;
        } else if (!hasLoader && isChunkForced) {
            sworld.setChunkForced(cPos.x, cPos.z, false);
            isChunkForced = false;
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FluxOvenCoreTile blockEntity) {
        if (blockEntity.networkScanTimer-- <= 0) {
            blockEntity.executeNetworkTopologyScan();
            blockEntity.refreshChunkLoading();
            blockEntity.networkScanTimer = DEFAULT_SCAN_INTERVAL;
        }

        blockEntity.handleIndustrialCycles();
    }

    private void handleIndustrialCycles() {
        int speedMod = itemHandler.getStackInSlot(1).getCount();
        int effMod = itemHandler.getStackInSlot(2).getCount();
        int connMod = itemHandler.getStackInSlot(3).getCount();

        float baseSpeed = 1.0f + (speedMod * SPEED_MODIFIER_BASE);
        double economyEfficiencyMultiplier = Math.max(0.0D, 1.0D - (effMod * ECONOMY_MODIFIER_PER_CARD));
        int hardwareLimit = BASE_CONNECTION_LIMIT + (connMod * CONNECTION_STEP_INCREMENT);
        int energyPerTick = (int) Math.round((double) ENERGY_COST_PER_TICK * economyEfficiencyMultiplier);

        List<Integer> activeWorkChannels = new ArrayList<>();

        for (Map.Entry<BlockPos, Integer> entry : coordinateToSlotMap.entrySet()) {
            int index = entry.getValue();
            if (index < hardwareLimit && this.mappedPlatePositions.contains(entry.getKey())) {
                IItemHandler remotePlateInv = getPlateInventory(index);
                if (remotePlateInv != null) {
                    int chanA = index * 2;
                    int chanB = (index * 2) + 1;

                    ItemStack inputA = remotePlateInv.getStackInSlot(0);
                    if (!inputA.isEmpty() && canProduceResult(inputA, chanA + OUTPUT_CHANNEL_OFFSET)) {
                        activeWorkChannels.add(chanA);
                    }

                    ItemStack inputB = remotePlateInv.getStackInSlot(1);
                    if (!inputB.isEmpty() && canProduceResult(inputB, chanB + OUTPUT_CHANNEL_OFFSET)) {
                        activeWorkChannels.add(chanB);
                    }
                }
            }
        }

        int energyRequirement = activeWorkChannels.size() * energyPerTick;
        float actualOperatingSpeed = 0;

        if (energyRequirement > 0 && energy.getEnergyStored() >= MIN_WORKING_FE) {
            int stored = energy.getEnergyStored();
            if (stored >= energyRequirement) {
                actualOperatingSpeed = baseSpeed;
                energy.consumeInternal(energyRequirement);
                this.instantaneousDraw = energyRequirement;
            } else {
                actualOperatingSpeed = ((float) stored / energyRequirement) * baseSpeed;
                energy.consumeInternal(stored);
                this.instantaneousDraw = stored;
            }
        } else {
            this.instantaneousDraw = 0;
        }

        updateProcessingStatus(actualOperatingSpeed > 0);

        boolean dirty = false;
        for (Map.Entry<BlockPos, Integer> entry : coordinateToSlotMap.entrySet()) {
            int index = entry.getValue();
            if (index < hardwareLimit && this.mappedPlatePositions.contains(entry.getKey())) {
                IItemHandler remotePlateInv = getPlateInventory(index);
                if (remotePlateInv instanceof ItemStackHandler) {
                    ItemStackHandler targetPlateInv = (ItemStackHandler) remotePlateInv;
                    int chanA = index * 2;
                    int chanB = (index * 2) + 1;

                    if (activeWorkChannels.contains(chanA)) {
                        processingProgress[chanA] += actualOperatingSpeed;
                        dirty = true;
                        if (processingProgress[chanA] >= STANDARD_COOK_TIME) {
                            executeHybridSmelt(index, targetPlateInv, 0, chanA + OUTPUT_CHANNEL_OFFSET);
                            processingProgress[chanA] = 0;
                        }
                    } else if (processingProgress[chanA] > 0) {
                        processingProgress[chanA] = 0;
                        dirty = true;
                    }

                    if (activeWorkChannels.contains(chanB)) {
                        processingProgress[chanB] += actualOperatingSpeed;
                        dirty = true;
                        if (processingProgress[chanB] >= STANDARD_COOK_TIME) {
                            executeHybridSmelt(index, targetPlateInv, 1, chanB + OUTPUT_CHANNEL_OFFSET);
                            processingProgress[chanB] = 0;
                        }
                    } else if (processingProgress[chanB] > 0) {
                        processingProgress[chanB] = 0;
                        dirty = true;
                    }
                }
            }
        }

        for (Map.Entry<BlockPos, Integer> entry : coordinateToSlotMap.entrySet()) {
            BlockPos targetPlatePos = entry.getKey();
            int index = entry.getValue();

            int chanA = index * 2;
            int chanB = (index * 2) + 1;
            boolean plateShouldBeLit = actualOperatingSpeed > 0 && this.mappedPlatePositions.contains(targetPlatePos) && (activeWorkChannels.contains(chanA) || activeWorkChannels.contains(chanB));

            if (this.level.isLoaded(targetPlatePos)) {
                BlockEntity satelliteTe = this.level.getBlockEntity(targetPlatePos);
                if (satelliteTe instanceof OvenPlateTileEntity) {
                    ((OvenPlateTileEntity) satelliteTe).setCookingVisuals(plateShouldBeLit);
                }
            }
        }

        if (dirty) markForUpdate();
    }

    private boolean canProduceResult(ItemStack raw, int outSlot) {
        if (this.level == null || outSlot >= itemHandler.getSlots()) return false;
        return this.level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(raw), this.level)
                .map(recipe -> {
                    ItemStack res = recipe.getResultItem();
                    ItemStack cur = itemHandler.getStackInSlot(outSlot);
                    if (cur.isEmpty()) return true;
                    if (!ItemHandlerHelper.canItemStacksStack(cur, res)) return false;
                    return (cur.getCount() + res.getCount() <= itemHandler.getSlotLimit(outSlot));
                }).orElse(false);
    }

    private void executeHybridSmelt(int plateIdx, ItemStackHandler plateInv, int localInId, int centralOutId) {
        ItemStack input = plateInv.getStackInSlot(localInId);
        this.level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(input), this.level)
                .ifPresent(recipe -> {
                    ItemStack result = recipe.getResultItem().copy();
                    ItemStack existingCentOut = itemHandler.getStackInSlot(centralOutId);

                    boolean success = false;
                    if (existingCentOut.isEmpty()) {
                        itemHandler.setStackInSlot(centralOutId, result);
                        input.shrink(1);
                        success = true;
                    } else if (ItemHandlerHelper.canItemStacksStack(existingCentOut, result)) {
                        int canAdd = Math.min(result.getCount(), 64 - existingCentOut.getCount());
                        if (canAdd >= result.getCount()) {
                            existingCentOut.grow(result.getCount());
                            input.shrink(1);
                            success = true;
                        }
                    }

                    if (success) {
                        BlockPos platePos = getConnectedPlatePos(plateIdx);
                        if (platePos != null && this.level.isLoaded(platePos)) {
                            BlockEntity te = this.level.getBlockEntity(platePos);
                            if (te instanceof OvenPlateTileEntity) {
                                ((OvenPlateTileEntity) te).markForUpdate();
                            }
                        }
                    }
                });
    }

    public float getCookProgressForPlate(int channel) {
        return (channel >= 0 && channel < processingProgress.length) ? processingProgress[channel] : 0.0f;
    }

    private void updateModuleCalibrations() {
        this.energy.setCapacity(BASE_FE_CAPACITY);
    }

    private void updateProcessingStatus(boolean active) {
        if (active != this.isActiveProcessing) {
            this.isActiveProcessing = active;
            this.markForUpdate();
        }
    }

    public void markForUpdate() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 2 | 4 | 16);
        }
    }

    @Override
    @Nullable
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return new ClientboundBlockEntityDataPacket(this.worldPosition, -1, this.getUpdateTag());
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag nbt = new CompoundTag();
        this.save(nbt);
        return nbt;
    }

    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        this.load(pkt.getTag());
    }

    @Override
    public void setRemoved() {
        if (isChunkForced && this.level instanceof ServerLevel) {
            ((ServerLevel)this.level).setChunkForced(this.worldPosition.getX() >> 4, this.worldPosition.getZ() >> 4, false);
        }
        super.setRemoved();
    }

    @Override
    public void load(@Nonnull CompoundTag nbt) {
        super.load(nbt);
        this.itemHandler.deserializeNBT(nbt.getCompound("InvMain"));
        this.recoveryHandler.deserializeNBT(nbt.getCompound("InvRecovery"));
        this.energy.setEnergyDirect(nbt.getInt("MachineFE"));
        this.connectedNodeCount = nbt.getInt("NodesFound");
        this.instantaneousDraw = nbt.getInt("DrawFE");
        this.isActiveProcessing = nbt.getBoolean("IsActive");
        this.isChunkForced = nbt.getBoolean("ChunkForced");

        coordinateToSlotMap.clear();
        Arrays.fill(allocatedSlotsTracker, false);
        if (nbt.contains("CoordSlotMap", Tag.TAG_LIST)) {
            ListTag mapList = nbt.getList("CoordSlotMap", Tag.TAG_COMPOUND);
            for (int i = 0; i < mapList.size(); i++) {
                CompoundTag entryNbt = mapList.getCompound(i);
                BlockPos pos = new BlockPos(entryNbt.getInt("x"), entryNbt.getInt("y"), entryNbt.getInt("z"));
                int idx = entryNbt.getInt("idx");
                coordinateToSlotMap.put(pos, idx);
                if (idx >= 0 && idx < allocatedSlotsTracker.length) {
                    allocatedSlotsTracker[idx] = true;
                }
            }
        }

        List<BlockPos> sortedPositions = new ArrayList<>(Collections.nCopies(coordinateToSlotMap.size(), BlockPos.ZERO));
        for (Map.Entry<BlockPos, Integer> entry : coordinateToSlotMap.entrySet()) {
            int idx = entry.getValue();
            if (idx >= 0 && idx < sortedPositions.size()) {
                sortedPositions.set(idx, entry.getKey());
            }
        }
        synchronized (this.mappedPlatePositions) {
            this.mappedPlatePositions.clear();
            for (BlockPos p : sortedPositions) {
                if (p != null && p != BlockPos.ZERO) {
                    this.mappedPlatePositions.add(p);
                }
            }
        }

        CompoundTag progNbt = nbt.getCompound("PipelineProg");
        for (int i = 0; i < INPUT_CHANNEL_COUNT; i++) {
            String key = "ch_" + i;
            if (progNbt.contains(key)) {
                this.processingProgress[i] = progNbt.getFloat(key);
            }
        }
        updateModuleCalibrations();
    }

    @Override
    public CompoundTag save(@Nonnull CompoundTag nbt) {
        super.save(nbt);
        nbt.put("InvMain", this.itemHandler.serializeNBT());
        nbt.put("InvRecovery", this.recoveryHandler.serializeNBT());
        nbt.putInt("MachineFE", this.energy.getEnergyStored());
        nbt.putInt("NodesFound", this.connectedNodeCount);
        nbt.putInt("DrawFE", this.instantaneousDraw);
        nbt.putBoolean("IsActive", this.isActiveProcessing);
        nbt.putBoolean("ChunkForced", this.isChunkForced);

        ListTag mapList = new ListTag();
        for (Map.Entry<BlockPos, Integer> entry : coordinateToSlotMap.entrySet()) {
            CompoundTag entryNbt = new CompoundTag();
            entryNbt.putInt("x", entry.getKey().getX());
            entryNbt.putInt("y", entry.getKey().getY());
            entryNbt.putInt("z", entry.getKey().getZ());
            entryNbt.putInt("idx", entry.getValue());
            mapList.add(entryNbt);
        }
        nbt.put("CoordSlotMap", mapList);
        CompoundTag progNbt = new CompoundTag();
        for (int i = 0; i < INPUT_CHANNEL_COUNT; i++) {
            if (this.processingProgress[i] > 0) {
                progNbt.putFloat("ch_" + i, this.processingProgress[i]);
            }
        }
        nbt.put("PipelineProg", progNbt);
        return nbt;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == CapabilityEnergy.ENERGY) {
            return energyCapability.cast();
        }

        if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            if (side == null) {
                return inventoryCapabilityGeneral.cast();
            }
            if (side == Direction.DOWN) {
                return automationOutputHandler.cast();
            }
            return LazyOptional.empty();
        }

        return super.getCapability(cap, side);
    }

    @Override
    public Component getDisplayName() {
        return new TranslatableComponent("container.fluxoven.core");
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int id, @Nonnull Inventory inv, @Nonnull Player p) {
        this.markForUpdate();
        return new FluxOvenCoreContainer(id, inv, this);
    }

    public int getConnectedPlates() { return this.connectedNodeCount; }
    public int getAvgConsumption() { return this.instantaneousDraw; }
    public void setAvgConsumptionClient(int val) { this.instantaneousDraw = val; }

    @Nullable
    public IItemHandler getPlateInventory(int plateIdx) {
        BlockPos platePos = getConnectedPlatePos(plateIdx);
        if (platePos != null && this.level != null && this.level.isLoaded(platePos)) {
            BlockEntity te = this.level.getBlockEntity(platePos);
            if (te instanceof OvenPlateTileEntity) {
                return ((OvenPlateTileEntity) te).inventory;
            }
        }
        return null;
    }

    public static class CustomEnergyStorage extends EnergyStorage {
        public CustomEnergyStorage(int cap, int rec, int ext) { super(cap, rec, ext); }
        public void setCapacity(int cap) { this.capacity = cap; if (this.energy > cap) this.energy = cap; }
        public void setEnergyDirect(int val) { this.energy = Math.min(val, this.capacity); }
        public void consumeInternal(int amt) { this.energy = Math.max(0, this.energy - amt); }
        @Override public boolean canReceive() { return true; }
    }
}