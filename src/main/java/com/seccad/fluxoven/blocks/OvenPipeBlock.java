package com.seccad.fluxoven.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext; // Correct 1.17.1 placement context class
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("deprecation") // Silences vanilla's internal block method warnings
public class OvenPipeBlock extends PipeBlock {

    private final Map<BlockState, VoxelShape> shapeCache = new HashMap<>();

    public OvenPipeBlock(BlockBehaviour.Properties properties) {
        super(2.0F, properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false));
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
        return 0;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockGetter world = context.getLevel();
        BlockPos pos = context.getClickedPos();

        return this.defaultBlockState()
                .setValue(NORTH, this.canConnectTo(world, pos.north(), Direction.NORTH))
                .setValue(SOUTH, this.canConnectTo(world, pos.south(), Direction.SOUTH))
                .setValue(WEST,  this.canConnectTo(world, pos.west(),  Direction.WEST))
                .setValue(EAST,  this.canConnectTo(world, pos.east(),  Direction.EAST))
                .setValue(UP,    this.canConnectTo(world, pos.above(), Direction.UP))
                .setValue(DOWN,  this.canConnectTo(world, pos.below(), Direction.DOWN));
    }

    @Override
    public BlockState updateShape(BlockState stateIn, Direction facing, BlockState facingState, LevelAccessor worldIn, BlockPos currentPos, BlockPos facingPos) {
        return stateIn.setValue(PROPERTY_BY_DIRECTION.get(facing), this.canConnectTo(worldIn, facingPos, facing));
    }

    public boolean canConnectTo(BlockGetter world, BlockPos neighborPos, Direction dir) {
        BlockState state = world.getBlockState(neighborPos);
        if (state.isAir()) return false;

        Block block = state.getBlock();
        if (block == this) return true;

        var registryKey = ForgeRegistries.BLOCKS.getKey(block);
        String registryName = registryKey != null ? registryKey.getPath() : "";
        return registryName.contains("oven") || registryName.contains("plate") || registryName.contains("core");
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        return this.shapeCache.computeIfAbsent(state, this::calculateShape);
    }

    private VoxelShape calculateShape(BlockState state) {
        VoxelShape shape = Block.box(6.0D, 6.0D, 6.0D, 10.0D, 10.0D, 10.0D);

        if (state.getValue(NORTH)) shape = Shapes.or(shape, Block.box(6.0D, 6.0D, 0.0D, 10.0D, 10.0D, 6.0D));
        if (state.getValue(SOUTH)) shape = Shapes.or(shape, Block.box(6.0D, 6.0D, 10.0D, 10.0D, 10.0D, 16.0D));
        if (state.getValue(WEST))  shape = Shapes.or(shape, Block.box(0.0D, 6.0D, 6.0D, 6.0D, 10.0D, 10.0D));
        if (state.getValue(EAST))  shape = Shapes.or(shape, Block.box(10.0D, 6.0D, 6.0D, 16.0D, 10.0D, 10.0D));
        if (state.getValue(UP))    shape = Shapes.or(shape, Block.box(6.0D, 10.0D, 6.0D, 10.0D, 16.0D, 10.0D));
        if (state.getValue(DOWN))  shape = Shapes.or(shape, Block.box(6.0D, 0.0D, 6.0D, 10.0D, 6.0D, 10.0D));

        return shape;
    }
}