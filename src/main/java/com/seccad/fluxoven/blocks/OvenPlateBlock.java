package com.seccad.fluxoven.blocks;

import com.seccad.fluxoven.tileentity.FluxOvenCoreTile;
import com.seccad.fluxoven.tileentity.OvenPlateTileEntity;
import com.seccad.fluxoven.init.TileInit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.core.particles.ParticleTypes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

public class OvenPlateBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private static final VoxelShape SHAPE_NORTH = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);
    private static final VoxelShape SHAPE_SOUTH = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);
    private static final VoxelShape SHAPE_EAST  = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);
    private static final VoxelShape SHAPE_WEST  = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);

    public OvenPlateBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
    }

    @Override
    @Nonnull
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        Direction direction = state.getValue(FACING);
        switch (direction) {
            case SOUTH:
                return SHAPE_SOUTH;
            case EAST:
                return SHAPE_EAST;
            case WEST:
                return SHAPE_WEST;
            case NORTH:
            default:
                return SHAPE_NORTH;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
        return 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(LIT, false);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (TileInit.OVEN_PLATE_TILE == null) {
            return null;
        }
        return TileInit.OVEN_PLATE_TILE.get().create(pos, state);
    }

    @Override
    @Nonnull
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity te = world.getBlockEntity(pos);
        if (!(te instanceof OvenPlateTileEntity)) {
            return InteractionResult.PASS;
        }

        OvenPlateTileEntity plate = (OvenPlateTileEntity) te;
        BlockPos corePos = plate.getCorePos();
        int plateIndex = plate.getPlateIndex();

        if (corePos == null || plateIndex == -1) {
            player.displayClientMessage(
                    new TextComponent("Plate not linked to a central Flux Oven Core!").withStyle(ChatFormatting.YELLOW),
                    true
            );
            return InteractionResult.PASS;
        }

        BlockEntity coreTe = world.getBlockEntity(corePos);
        if (!(coreTe instanceof FluxOvenCoreTile)) {
            return InteractionResult.PASS;
        }

        FluxOvenCoreTile core = (FluxOvenCoreTile) coreTe;

        int connectionUpgrades = core.itemHandler.getStackInSlot(3).getCount();
        int maxWorkablePlates = 9 + (connectionUpgrades * 2);

        if (plateIndex >= maxWorkablePlates) {
            player.displayClientMessage(
                    new TextComponent("Plate Channel Locked! Core requires additional Connection Upgrades.").withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResult.FAIL;
        }

        ItemStack stackInHand = player.getItemInHand(hand);

        int myOutputBase = 516 + (plateIndex * 2);

        if (!stackInHand.isEmpty()) {
            boolean inserted = false;
            int totalToInsert = player.isShiftKeyDown() ? stackInHand.getCount() : 1;

            for (int i = 0; i < 2; i++) {
                ItemStack slotStack = plate.inventory.getStackInSlot(i);
                if (slotStack.isEmpty() || (ItemStack.isSame(slotStack, stackInHand) && ItemStack.tagMatches(slotStack, stackInHand) && slotStack.getCount() < 64)) {
                    ItemStack toInsert = stackInHand.copy();
                    toInsert.setCount(totalToInsert);

                    ItemStack remaining = plate.inventory.insertItem(i, toInsert, false);
                    int accepted = totalToInsert - remaining.getCount();

                    if (accepted > 0) {
                        stackInHand.shrink(accepted);
                        inserted = true;
                        world.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 0.5F, 1.2F);
                        break;
                    }
                }
            }

            if (inserted) {
                syncMachine(core, world, corePos, pos);
                return InteractionResult.SUCCESS;
            }
        } else {
            boolean taken = false;

            int[] centralOutputSlots = {myOutputBase, myOutputBase + 1};

            for (int slotId : centralOutputSlots) {
                if (slotId >= core.itemHandler.getSlots()) continue;

                ItemStack inSlot = core.itemHandler.getStackInSlot(slotId);
                if (!inSlot.isEmpty()) {
                    int extractCount = player.isShiftKeyDown() ? inSlot.getCount() : 1;
                    ItemStack extracted = core.itemHandler.extractItem(slotId, extractCount, false);

                    if (!extracted.isEmpty()) {
                        ItemHandlerHelper.giveItemToPlayer(player, extracted);
                        taken = true;
                        world.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F, 1.0F);
                        if (!player.isShiftKeyDown()) break;
                    }
                }
            }

            if (!taken || player.isShiftKeyDown()) {
                for (int i = 0; i < 2; i++) {
                    ItemStack inSlot = plate.inventory.getStackInSlot(i);
                    if (!inSlot.isEmpty()) {
                        int extractCount = player.isShiftKeyDown() ? inSlot.getCount() : 1;
                        ItemStack extracted = plate.inventory.extractItem(i, extractCount, false);

                        if (!extracted.isEmpty()) {
                            ItemHandlerHelper.giveItemToPlayer(player, extracted);
                            taken = true;
                            world.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F, 1.0F);
                            if (!player.isShiftKeyDown()) break;
                        }
                    }
                }
            }

            if (taken) {
                syncMachine(core, world, corePos, pos);
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.CONSUME;
    }

    private void syncMachine(FluxOvenCoreTile core, Level world, BlockPos corePos, BlockPos platePos) {
        core.setChanged();
        BlockState coreState = world.getBlockState(corePos);
        BlockState plateState = world.getBlockState(platePos);
        world.sendBlockUpdated(corePos, coreState, coreState, 2 | 4);
        world.sendBlockUpdated(platePos, plateState, plateState, 2 | 4);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            BlockEntity te = world.getBlockEntity(pos);
            if (te instanceof OvenPlateTileEntity) {
                OvenPlateTileEntity plate = (OvenPlateTileEntity) te;
                BlockPos corePos = plate.getCorePos();

                plate.dropInventoryContents();

                if (corePos != null) {
                    BlockEntity coreTe = world.getBlockEntity(corePos);
                    if (coreTe instanceof FluxOvenCoreTile) {
                        ((FluxOvenCoreTile) coreTe).processPlateRemoval(plate.getPlateIndex(), pos);
                    }
                }
            }
            super.onRemove(state, world, pos, newState, isMoving);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void animateTick(BlockState state, Level world, BlockPos pos, Random rand) {
        if (!state.getValue(LIT)) {
            return;
        }

        double centerX = (double) pos.getX() + 0.5D;
        double centerY = (double) pos.getY() + 0.15D;
        double centerZ = (double) pos.getZ() + 0.5D;

        if (rand.nextInt(4) == 0) {
            world.playLocalSound(centerX, centerY, centerZ, SoundEvents.SMOKER_SMOKE, SoundSource.BLOCKS, 0.2F, 1.0F, false);
        }

        for (int offset = 0; offset < 2; offset++) {
            double driftX = rand.nextDouble() * 0.6D - 0.3D;
            double driftZ = rand.nextDouble() * 0.6D - 0.3D;

            world.addParticle(ParticleTypes.SMOKE, centerX + driftX, centerY, centerZ + driftZ, 0.0D, 0.05D, 0.0D);
            if (rand.nextInt(3) == 0) {
                world.addParticle(ParticleTypes.FLAME, centerX + driftX, centerY, centerZ + driftZ, 0.0D, 0.01D, 0.0D);
            }
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter reader, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(new TextComponent("Multiblock Structure Satellite Element").withStyle(ChatFormatting.GRAY));
        tooltip.add(new TextComponent("Storage Model: ").withStyle(ChatFormatting.DARK_GREEN)
                .append(new TextComponent("Hybrid (Local Chest Inputs / Central Core Outputs)").withStyle(ChatFormatting.WHITE)));
    }

    public void printPlateStatus(Level world, BlockPos pos) {
        if (world == null || pos == null) {
            return;
        }

        BlockEntity te = world.getBlockEntity(pos);
        if (te instanceof OvenPlateTileEntity) {
            OvenPlateTileEntity plate = (OvenPlateTileEntity) te;
            BlockPos linkedCore = plate.getCorePos();

            String coreCoordString = linkedCore != null ? linkedCore.toShortString() : "UNLINKED";
            System.out.println("[FluxOven Diagnostics] Plate Index: " + plate.getPlateIndex() + " | Registered Master Node Core Pos: " + coreCoordString);
        }
    }
}