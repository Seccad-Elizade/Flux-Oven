package com.seccad.fluxoven.blocks;

import com.seccad.fluxoven.blocks.tileentity.FluxOvenCoreTile;
import com.seccad.fluxoven.blocks.tileentity.OvenPlateTileEntity;
import com.seccad.fluxoven.init.TileInit;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
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
        return new OvenPlateTileEntity(pos, state);
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
                    ItemStack takenStack = core.itemHandler.extractItem(slotId, extractCount, false);
                    if (!takenStack.isEmpty()) {
                        ItemHandlerHelper.giveItemToPlayer(player, takenStack);
                        taken = true;
                        world.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 1.0F);
                        break;
                    }
                }
            }

            if (!taken) {
                for (int i = 1; i >= 0; i--) {
                    ItemStack inInputSlot = plate.inventory.getStackInSlot(i);
                    if (!inInputSlot.isEmpty()) {
                        int extractCount = player.isShiftKeyDown() ? inInputSlot.getCount() : 1;
                        ItemStack takenStack = plate.inventory.extractItem(i, extractCount, false);
                        if (!takenStack.isEmpty()) {
                            ItemHandlerHelper.giveItemToPlayer(player, takenStack);
                            taken = true;
                            world.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F, 0.8F);
                            break;
                        }
                    }
                }
            }

            if (taken) {
                syncMachine(core, world, corePos, pos);
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }

    private void syncMachine(FluxOvenCoreTile core, Level world, BlockPos corePos, BlockPos platePos) {
        core.markForUpdate();
        BlockEntity plateTe = world.getBlockEntity(platePos);
        if (plateTe instanceof OvenPlateTileEntity) {
            ((OvenPlateTileEntity) plateTe).markForUpdate();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity te = world.getBlockEntity(pos);
            if (te instanceof OvenPlateTileEntity) {
                OvenPlateTileEntity plate = (OvenPlateTileEntity) te;
                for (int i = 0; i < plate.inventory.getSlots(); i++) {
                    Block.popResource(world, pos, plate.inventory.getStackInSlot(i));
                }
                BlockPos corePos = plate.getCorePos();
                if (corePos != null) {
                    BlockEntity coreTe = world.getBlockEntity(corePos);
                    if (coreTe instanceof FluxOvenCoreTile) {
                        ((FluxOvenCoreTile) coreTe).requestTopologyRebuild();
                    }
                }
            }
            super.onRemove(state, world, pos, newState, isMoving);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void animateTick(BlockState state, Level world, BlockPos pos, Random rand) {
        // Keeps the layout aligned for client particle logic if you use it later
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter reader, List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(new TextComponent("Heats up linked items using wireless flux currents.").withStyle(ChatFormatting.GRAY));
    }

    public void printPlateStatus(Level world, BlockPos pos) {
        // Layout holder for troubleshooting connectivity setups
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, pos, st, te) -> {
            if (te instanceof OvenPlateTileEntity tile) {
                OvenPlateTileEntity.tick(lvl, pos, st, tile);
            }
        };
    }
}