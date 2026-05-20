package com.seccad.fluxoven.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import com.seccad.fluxoven.blocks.OvenPlateBlock;
import com.seccad.fluxoven.blocks.tileentity.OvenPlateTileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

public class OvenPlateRenderer implements BlockEntityRenderer<OvenPlateTileEntity> {

    private static final ResourceLocation TEXTURE_WORKING = new ResourceLocation("fluxoven", "textures/block/oven_plate_working.png");
    private final BlockEntityRendererProvider.Context context;

    public OvenPlateRenderer(BlockEntityRendererProvider.Context context) {
        this.context = context;
    }

    @Override
    public void render(OvenPlateTileEntity tile, float partialTicks, @Nonnull PoseStack poseStack,
                       @Nonnull MultiBufferSource buffer, int combinedLight, int combinedOverlay) {

        if (tile.getLevel() == null) return;
        if (!(tile.getBlockState().getBlock() instanceof OvenPlateBlock)) return;

        Direction facing = tile.getBlockState().getValue(OvenPlateBlock.FACING);
        ItemStack[] stacks = tile.getDisplayedItems();

        if (stacks == null || stacks.length < 4) return;

        if (tile.isWorking()) {
            this.renderPlateSurface(poseStack, buffer, facing, combinedLight);
        }

        LocalPlayer player = Minecraft.getInstance().player;
        boolean showNumbers = false;

        if (player != null && player.isShiftKeyDown()) {
            double distSq = player.distanceToSqr(
                    tile.getBlockPos().getX() + 0.5,
                    tile.getBlockPos().getY() + 0.5,
                    tile.getBlockPos().getZ() + 0.5
            );
            if (distSq < 9.0D) {
                showNumbers = true;
            }
        }

        for (int i = 0; i < 4; i++) {
            ItemStack stack = stacks[i];
            if (stack == null || stack.isEmpty()) continue;

            poseStack.pushPose();

            poseStack.translate(0.5D, 0.135D, 0.5D);

            poseStack.mulPose(Vector3f.YP.rotationDegrees(-facing.toYRot()));

            float xShift = 0;
            if (i < 2) {
                float rawProgress = tile.getSmoothProgress(i);
                if (rawProgress > 0) {
                    xShift = (rawProgress / 200.0f) * 0.40f;
                }
            }

            float xBase = (i < 2) ? -0.22f : 0.22f;
            float zOffset = (i % 2 == 0) ? -0.21f : 0.21f;

            poseStack.translate(xBase + xShift, 0, zOffset);

            this.drawItemStack(poseStack, buffer, stack, combinedLight, combinedOverlay);

            if (showNumbers) {
                this.renderStackCount(String.valueOf(stack.getCount()), poseStack, buffer, combinedLight, facing);
            }

            poseStack.popPose();
        }
    }

    private void drawItemStack(PoseStack poseStack, MultiBufferSource buffer, ItemStack stack, int light, int overlay) {
        poseStack.pushPose();

        int renderCount = 1;
        if (stack.getCount() > 1) renderCount = 2;
        if (stack.getCount() >= 64) renderCount = 3;
        if (stack.getCount() >= 100) renderCount = 4;

        for (int layer = 0; layer < renderCount; layer++) {
            poseStack.pushPose();

            poseStack.translate(0, layer * 0.015D, 0);

            poseStack.scale(0.30f, 0.30f, 0.30f);

            poseStack.mulPose(Vector3f.XP.rotationDegrees(90));
            poseStack.mulPose(Vector3f.ZP.rotationDegrees(180));

            Minecraft.getInstance().getItemRenderer().renderStatic(
                    stack,
                    ItemTransforms.TransformType.FIXED,
                    light,
                    overlay,
                    poseStack,
                    buffer,
                    0
            );

            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private void renderStackCount(String text, PoseStack poseStack, MultiBufferSource buffer, int light, Direction facing) {
        poseStack.pushPose();

        poseStack.translate(0, 0.22D, 0);

        poseStack.mulPose(Vector3f.YP.rotationDegrees(facing.toYRot()));
        poseStack.mulPose(this.context.getBlockEntityRenderDispatcher().camera.rotation());

        poseStack.scale(-0.012f, -0.012f, 0.012f);

        Font font = Minecraft.getInstance().font;
        float xOffset = (float)(-font.width(text) / 2);

        font.drawInBatch(text, xOffset, 0, 0xFFFFFF, true, poseStack.last().pose(), buffer, false, 0, light);

        poseStack.popPose();
    }

    private void renderPlateSurface(PoseStack poseStack, MultiBufferSource buffer, Direction facing, int light) {
        poseStack.pushPose();

        poseStack.translate(0.5, 0.1265, 0.5);
        poseStack.mulPose(Vector3f.YP.rotationDegrees(-facing.toYRot()));

        VertexConsumer builder = buffer.getBuffer(RenderType.entityCutout(TEXTURE_WORKING));
        Matrix4f matrix = poseStack.last().pose();

        float s = 0.48f;

        this.renderQuad(builder, matrix, -s, s, -s, s, light);

        poseStack.popPose();
    }

    private void renderQuad(VertexConsumer builder, Matrix4f matrix, float minX, float maxX, float minZ, float maxZ, int light) {
        builder.vertex(matrix, minX, 0, minZ).color(255, 255, 255, 255).uv(0, 0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 1, 0).endVertex();

        builder.vertex(matrix, minX, 0, maxZ).color(255, 255, 255, 255).uv(0, 1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 1, 0).endVertex();

        builder.vertex(matrix, maxX, 0, maxZ).color(255, 255, 255, 255).uv(1, 1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 1, 0).endVertex();

        builder.vertex(matrix, maxX, 0, minZ).color(255, 255, 255, 255).uv(1, 0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 1, 0).endVertex();
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    @Override
    public boolean shouldRenderOffScreen(OvenPlateTileEntity te) {
        return false;
    }
}