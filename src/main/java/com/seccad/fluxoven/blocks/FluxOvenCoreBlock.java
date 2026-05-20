package com.seccad.fluxoven.blocks;

import com.seccad.fluxoven.tileentity.FluxOvenCoreTile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fmllegacy.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class FluxOvenCoreBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public FluxOvenCoreBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluxOvenCoreTile(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!world.isClientSide) {
            BlockEntity te = world.getBlockEntity(pos);
            if (te instanceof FluxOvenCoreTile) {
                NetworkHooks.openGui((ServerPlayer) player, (MenuProvider) te, pos);
            } else {
                System.out.println("TileEntity at " + pos + " is not an instance of FluxOvenCoreTile!");
            }
        }
        return InteractionResult.sidedSuccess(world.isClientSide);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootContext.Builder builder) {
        List<ItemStack> drops = new ArrayList<>();
        ItemStack stack = new ItemStack(this);
        BlockEntity tileEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (tileEntity instanceof FluxOvenCoreTile) {
            FluxOvenCoreTile coreTile = (FluxOvenCoreTile) tileEntity;
            CompoundTag tileData = new CompoundTag();
            coreTile.save(tileData);
            CompoundTag stackTag = new CompoundTag();
            stackTag.put("BlockEntityTag", tileData);
            stack.setTag(stackTag);
        }
        drops.add(stack);
        return drops;
    }
}