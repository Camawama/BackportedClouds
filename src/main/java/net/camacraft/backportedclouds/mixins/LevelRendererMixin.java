package net.camacraft.backportedclouds.mixins;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.camacraft.backportedclouds.BackportedClouds;
import net.camacraft.backportedclouds.Config;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Replaces vanilla cloud rendering with a version that meshes the configured distance
 * off-thread. The mesh is built colorless (white) at relative Y 0 with both top and
 * bottom faces, so camera movement and cloud color changes never require a rebuild:
 * color is applied via the shader's ColorModulator and height via the pose translate.
 * Rebuilds only happen when the camera drifts more than one cloud cell (96 blocks)
 * from the meshed center, or when cloud type / distance settings change.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Unique
    private static final Logger backported$LOGGER = LogUtils.getLogger();

    // One cloud texel spans 12 blocks; one fancy cloud cell is 8 texels = 96 blocks = 6 chunks.
    @Unique
    private static final int backported$BLOCKS_PER_TEXEL = 12;
    @Unique
    private static final int backported$BLOCKS_PER_CELL = 96;
    // Mesh survives up to one cell (8 texels) of camera drift before a rebuild is scheduled.
    @Unique
    private static final int backported$REBUILD_MARGIN_TEXELS = 8;

    @Shadow
    @Nullable
    private ClientLevel level;
    @Shadow
    private int ticks;
    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    private boolean generateClouds;
    @Shadow
    @Nullable
    private VertexBuffer cloudBuffer;
    @Shadow
    @Final
    private static ResourceLocation CLOUDS_LOCATION;

    @Unique
    @Nullable
    private ExecutorService backported$mesher;
    @Unique
    @Nullable
    private Future<BufferBuilder.RenderedBuffer> backported$buildTask;
    // Only ever touched from the mesher thread.
    @Unique
    @Nullable
    private BufferBuilder backported$builder;
    @Unique
    private int backported$builderCapacity;

    // Parameters the uploaded mesh was built with.
    @Unique
    private int backported$meshX;
    @Unique
    private int backported$meshZ;
    @Unique
    @Nullable
    private CloudStatus backported$meshType;
    @Unique
    private int backported$meshDistance = -1;

    // Parameters of the in-flight build task.
    @Unique
    private int backported$taskX;
    @Unique
    private int backported$taskZ;
    @Unique
    private CloudStatus backported$taskType;
    @Unique
    private int backported$taskDistance;

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void backported$renderClouds(PoseStack poseStack, Matrix4f projectionMatrix, float partialTicks, double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
        if (BackportedClouds.sodiumCloudsActive()) {
            return; // A Sodium-based renderer replaces cloud rendering; leave the vanilla path alone.
        }

        ci.cancel();
        if (this.level == null) {
            return;
        }
        if (this.level.effects().renderClouds(this.level, this.ticks, partialTicks, poseStack, cameraX, cameraY, cameraZ, projectionMatrix)) {
            return;
        }
        float cloudHeight = this.level.effects().getCloudHeight();
        if (Float.isNaN(cloudHeight)) {
            return;
        }

        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(true);

        // Drift from world game time (not the client-local render tick counter) so all
        // players on a server see identical cloud positions, matching 1.21.2+ behavior.
        // Computed in double: game time can be large enough that float precision would
        // make the drift visibly stutter.
        double drift = ((double) this.level.getGameTime() + (double) partialTicks) * 0.03D;
        double cloudX = (cameraX + drift) / 12.0D;
        double cloudY = (double) (cloudHeight - (float) cameraY + 0.33F);
        double cloudZ = cameraZ / 12.0D + (double) 0.33F;
        cloudX -= (double) (Mth.floor(cloudX / 2048.0D) * 2048);
        cloudZ -= (double) (Mth.floor(cloudZ / 2048.0D) * 2048);
        float xFraction = (float) (cloudX - (double) Mth.floor(cloudX));
        float zFraction = (float) (cloudZ - (double) Mth.floor(cloudZ));
        int floorX = Mth.floor(cloudX);
        int floorZ = Mth.floor(cloudZ);
        Vec3 cloudColor = this.level.getCloudColor(partialTicks);
        CloudStatus cloudType = this.minecraft.options.getCloudsType();
        int distance = Config.CLIENT.cloudRenderDistance.get();

        this.backported$finishPendingUpload();

        boolean needsRebuild = this.generateClouds
                || this.cloudBuffer == null
                || this.backported$meshType != cloudType
                || this.backported$meshDistance != distance
                || Math.abs(floorX - this.backported$meshX) >= backported$REBUILD_MARGIN_TEXELS
                || Math.abs(floorZ - this.backported$meshZ) >= backported$REBUILD_MARGIN_TEXELS;
        if (needsRebuild && this.backported$buildTask == null) {
            this.generateClouds = false;
            this.backported$taskX = floorX;
            this.backported$taskZ = floorZ;
            this.backported$taskType = cloudType;
            this.backported$taskDistance = distance;
            final boolean fancy = cloudType == CloudStatus.FANCY;
            final int taskDistance = distance;
            final float u0 = (float) floorX * 0.00390625F;
            final float v0 = (float) floorZ * 0.00390625F;
            if (this.backported$mesher == null) {
                this.backported$mesher = Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "Backported Clouds Mesher");
                    thread.setDaemon(true);
                    return thread;
                });
            }
            this.backported$buildTask = this.backported$mesher.submit(() -> this.backported$buildMesh(fancy, taskDistance, u0, v0));
        }

        RenderSystem.setShader(GameRenderer::getPositionTexColorNormalShader);
        RenderSystem.setShaderTexture(0, CLOUDS_LOCATION);
        FogRenderer.levelFogColor();
        RenderSystem.setShaderColor((float) cloudColor.x, (float) cloudColor.y, (float) cloudColor.z, 1.0F);
        float oldFogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogEnd(oldFogEnd * Config.CLIENT.cloudDistanceMultiplier.get().floatValue());

        poseStack.pushPose();
        poseStack.scale(12.0F, 1.0F, 12.0F);
        // The mesh is centered on (meshX, meshZ) at relative Y 0; shift it to the current
        // camera-relative position so a stale mesh stays world-anchored while a rebuild runs.
        poseStack.translate(-xFraction + (float) (this.backported$meshX - floorX), (float) cloudY, -zFraction + (float) (this.backported$meshZ - floorZ));
        if (this.cloudBuffer != null) {
            this.cloudBuffer.bind();
            int firstPass = this.backported$meshType == CloudStatus.FANCY ? 0 : 1;

            for (int pass = firstPass; pass < 2; ++pass) {
                if (pass == 0) {
                    RenderSystem.colorMask(false, false, false, false);
                } else {
                    RenderSystem.colorMask(true, true, true, true);
                }

                ShaderInstance shader = RenderSystem.getShader();
                this.cloudBuffer.drawWithShader(poseStack.last().pose(), projectionMatrix, shader);
            }

            VertexBuffer.unbind();
        }
        poseStack.popPose();

        RenderSystem.setShaderFogEnd(oldFogEnd);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void backported$close(CallbackInfo ci) {
        if (this.backported$mesher != null) {
            this.backported$mesher.shutdownNow();
        }
    }

    @Unique
    private void backported$finishPendingUpload() {
        if (this.backported$buildTask == null || !this.backported$buildTask.isDone()) {
            return;
        }
        try {
            BufferBuilder.RenderedBuffer rendered = this.backported$buildTask.get();
            if (this.cloudBuffer == null) {
                this.cloudBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            }
            this.cloudBuffer.bind();
            this.cloudBuffer.upload(rendered);
            VertexBuffer.unbind();
            this.backported$meshX = this.backported$taskX;
            this.backported$meshZ = this.backported$taskZ;
            this.backported$meshType = this.backported$taskType;
            this.backported$meshDistance = this.backported$taskDistance;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            backported$LOGGER.error("Cloud mesh build failed; keeping the previous cloud mesh", e);
        } finally {
            this.backported$buildTask = null;
        }
    }

    /**
     * Runs on the mesher thread. Geometry mirrors vanilla {@code LevelRenderer#buildClouds}
     * with three deliberate changes: the loop bounds cover the configured distance, vertex
     * colors are the face shade over white (tint comes from ColorModulator at draw time),
     * and both top and bottom faces are always emitted so the mesh is valid from any camera
     * height (the fancy-mode depth pre-pass prevents translucent double-blending).
     */
    @Unique
    private BufferBuilder.RenderedBuffer backported$buildMesh(boolean fancy, int distanceChunks, float u0, float v0) {
        int blocks = distanceChunks * 16;
        int cellRadius = Mth.positiveCeilDiv(blocks, backported$BLOCKS_PER_CELL) + 1;
        int fastRadius = Mth.positiveCeilDiv(Mth.positiveCeilDiv(blocks + backported$REBUILD_MARGIN_TEXELS * backported$BLOCKS_PER_TEXEL, backported$BLOCKS_PER_TEXEL), 32) * 32;

        int quads = fancy
                ? 72 * cellRadius * cellRadius + 64 * cellRadius
                : (2 * fastRadius / 32) * (2 * fastRadius / 32);
        int capacity = quads * 4 * DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL.getVertexSize() + 4096;
        BufferBuilder builder = this.backported$builder;
        if (builder == null || this.backported$builderCapacity < capacity) {
            builder = new BufferBuilder(capacity);
            this.backported$builder = builder;
            this.backported$builderCapacity = capacity;
        }

        try {
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
            if (fancy) {
                for (int k = -cellRadius; k < cellRadius; ++k) {
                    for (int l = -cellRadius; l < cellRadius; ++l) {
                        float x0 = (float) (k * 8);
                        float z0 = (float) (l * 8);

                        builder.vertex((double) (x0 + 0.0F), 0.0D, (double) (z0 + 8.0F)).uv((x0 + 0.0F) * 0.00390625F + u0, (z0 + 8.0F) * 0.00390625F + v0).color(0.7F, 0.7F, 0.7F, 0.8F).normal(0.0F, -1.0F, 0.0F).endVertex();
                        builder.vertex((double) (x0 + 8.0F), 0.0D, (double) (z0 + 8.0F)).uv((x0 + 8.0F) * 0.00390625F + u0, (z0 + 8.0F) * 0.00390625F + v0).color(0.7F, 0.7F, 0.7F, 0.8F).normal(0.0F, -1.0F, 0.0F).endVertex();
                        builder.vertex((double) (x0 + 8.0F), 0.0D, (double) (z0 + 0.0F)).uv((x0 + 8.0F) * 0.00390625F + u0, (z0 + 0.0F) * 0.00390625F + v0).color(0.7F, 0.7F, 0.7F, 0.8F).normal(0.0F, -1.0F, 0.0F).endVertex();
                        builder.vertex((double) (x0 + 0.0F), 0.0D, (double) (z0 + 0.0F)).uv((x0 + 0.0F) * 0.00390625F + u0, (z0 + 0.0F) * 0.00390625F + v0).color(0.7F, 0.7F, 0.7F, 0.8F).normal(0.0F, -1.0F, 0.0F).endVertex();

                        builder.vertex((double) (x0 + 0.0F), (double) (4.0F - 9.765625E-4F), (double) (z0 + 8.0F)).uv((x0 + 0.0F) * 0.00390625F + u0, (z0 + 8.0F) * 0.00390625F + v0).color(1.0F, 1.0F, 1.0F, 0.8F).normal(0.0F, 1.0F, 0.0F).endVertex();
                        builder.vertex((double) (x0 + 8.0F), (double) (4.0F - 9.765625E-4F), (double) (z0 + 8.0F)).uv((x0 + 8.0F) * 0.00390625F + u0, (z0 + 8.0F) * 0.00390625F + v0).color(1.0F, 1.0F, 1.0F, 0.8F).normal(0.0F, 1.0F, 0.0F).endVertex();
                        builder.vertex((double) (x0 + 8.0F), (double) (4.0F - 9.765625E-4F), (double) (z0 + 0.0F)).uv((x0 + 8.0F) * 0.00390625F + u0, (z0 + 0.0F) * 0.00390625F + v0).color(1.0F, 1.0F, 1.0F, 0.8F).normal(0.0F, 1.0F, 0.0F).endVertex();
                        builder.vertex((double) (x0 + 0.0F), (double) (4.0F - 9.765625E-4F), (double) (z0 + 0.0F)).uv((x0 + 0.0F) * 0.00390625F + u0, (z0 + 0.0F) * 0.00390625F + v0).color(1.0F, 1.0F, 1.0F, 0.8F).normal(0.0F, 1.0F, 0.0F).endVertex();

                        if (k > -1) {
                            for (int i = 0; i < 8; ++i) {
                                builder.vertex((double) (x0 + (float) i + 0.0F), 0.0D, (double) (z0 + 8.0F)).uv((x0 + (float) i + 0.5F) * 0.00390625F + u0, (z0 + 8.0F) * 0.00390625F + v0).color(0.9F, 0.9F, 0.9F, 0.8F).normal(-1.0F, 0.0F, 0.0F).endVertex();
                                builder.vertex((double) (x0 + (float) i + 0.0F), 4.0D, (double) (z0 + 8.0F)).uv((x0 + (float) i + 0.5F) * 0.00390625F + u0, (z0 + 8.0F) * 0.00390625F + v0).color(0.9F, 0.9F, 0.9F, 0.8F).normal(-1.0F, 0.0F, 0.0F).endVertex();
                                builder.vertex((double) (x0 + (float) i + 0.0F), 4.0D, (double) (z0 + 0.0F)).uv((x0 + (float) i + 0.5F) * 0.00390625F + u0, (z0 + 0.0F) * 0.00390625F + v0).color(0.9F, 0.9F, 0.9F, 0.8F).normal(-1.0F, 0.0F, 0.0F).endVertex();
                                builder.vertex((double) (x0 + (float) i + 0.0F), 0.0D, (double) (z0 + 0.0F)).uv((x0 + (float) i + 0.5F) * 0.00390625F + u0, (z0 + 0.0F) * 0.00390625F + v0).color(0.9F, 0.9F, 0.9F, 0.8F).normal(-1.0F, 0.0F, 0.0F).endVertex();
                            }
                        }

                        if (k <= 1) {
                            for (int i = 0; i < 8; ++i) {
                                builder.vertex((double) (x0 + (float) i + 1.0F - 9.765625E-4F), 0.0D, (double) (z0 + 8.0F)).uv((x0 + (float) i + 0.5F) * 0.00390625F + u0, (z0 + 8.0F) * 0.00390625F + v0).color(0.9F, 0.9F, 0.9F, 0.8F).normal(1.0F, 0.0F, 0.0F).endVertex();
                                builder.vertex((double) (x0 + (float) i + 1.0F - 9.765625E-4F), 4.0D, (double) (z0 + 8.0F)).uv((x0 + (float) i + 0.5F) * 0.00390625F + u0, (z0 + 8.0F) * 0.00390625F + v0).color(0.9F, 0.9F, 0.9F, 0.8F).normal(1.0F, 0.0F, 0.0F).endVertex();
                                builder.vertex((double) (x0 + (float) i + 1.0F - 9.765625E-4F), 4.0D, (double) (z0 + 0.0F)).uv((x0 + (float) i + 0.5F) * 0.00390625F + u0, (z0 + 0.0F) * 0.00390625F + v0).color(0.9F, 0.9F, 0.9F, 0.8F).normal(1.0F, 0.0F, 0.0F).endVertex();
                                builder.vertex((double) (x0 + (float) i + 1.0F - 9.765625E-4F), 0.0D, (double) (z0 + 0.0F)).uv((x0 + (float) i + 0.5F) * 0.00390625F + u0, (z0 + 0.0F) * 0.00390625F + v0).color(0.9F, 0.9F, 0.9F, 0.8F).normal(1.0F, 0.0F, 0.0F).endVertex();
                            }
                        }

                        if (l > -1) {
                            for (int i = 0; i < 8; ++i) {
                                builder.vertex((double) (x0 + 0.0F), 4.0D, (double) (z0 + (float) i + 0.0F)).uv((x0 + 0.0F) * 0.00390625F + u0, (z0 + (float) i + 0.5F) * 0.00390625F + v0).color(0.8F, 0.8F, 0.8F, 0.8F).normal(0.0F, 0.0F, -1.0F).endVertex();
                                builder.vertex((double) (x0 + 8.0F), 4.0D, (double) (z0 + (float) i + 0.0F)).uv((x0 + 8.0F) * 0.00390625F + u0, (z0 + (float) i + 0.5F) * 0.00390625F + v0).color(0.8F, 0.8F, 0.8F, 0.8F).normal(0.0F, 0.0F, -1.0F).endVertex();
                                builder.vertex((double) (x0 + 8.0F), 0.0D, (double) (z0 + (float) i + 0.0F)).uv((x0 + 8.0F) * 0.00390625F + u0, (z0 + (float) i + 0.5F) * 0.00390625F + v0).color(0.8F, 0.8F, 0.8F, 0.8F).normal(0.0F, 0.0F, -1.0F).endVertex();
                                builder.vertex((double) (x0 + 0.0F), 0.0D, (double) (z0 + (float) i + 0.0F)).uv((x0 + 0.0F) * 0.00390625F + u0, (z0 + (float) i + 0.5F) * 0.00390625F + v0).color(0.8F, 0.8F, 0.8F, 0.8F).normal(0.0F, 0.0F, -1.0F).endVertex();
                            }
                        }

                        if (l <= 1) {
                            for (int i = 0; i < 8; ++i) {
                                builder.vertex((double) (x0 + 0.0F), 4.0D, (double) (z0 + (float) i + 1.0F - 9.765625E-4F)).uv((x0 + 0.0F) * 0.00390625F + u0, (z0 + (float) i + 0.5F) * 0.00390625F + v0).color(0.8F, 0.8F, 0.8F, 0.8F).normal(0.0F, 0.0F, 1.0F).endVertex();
                                builder.vertex((double) (x0 + 8.0F), 4.0D, (double) (z0 + (float) i + 1.0F - 9.765625E-4F)).uv((x0 + 8.0F) * 0.00390625F + u0, (z0 + (float) i + 0.5F) * 0.00390625F + v0).color(0.8F, 0.8F, 0.8F, 0.8F).normal(0.0F, 0.0F, 1.0F).endVertex();
                                builder.vertex((double) (x0 + 8.0F), 0.0D, (double) (z0 + (float) i + 1.0F - 9.765625E-4F)).uv((x0 + 8.0F) * 0.00390625F + u0, (z0 + (float) i + 0.5F) * 0.00390625F + v0).color(0.8F, 0.8F, 0.8F, 0.8F).normal(0.0F, 0.0F, 1.0F).endVertex();
                                builder.vertex((double) (x0 + 0.0F), 0.0D, (double) (z0 + (float) i + 1.0F - 9.765625E-4F)).uv((x0 + 0.0F) * 0.00390625F + u0, (z0 + (float) i + 0.5F) * 0.00390625F + v0).color(0.8F, 0.8F, 0.8F, 0.8F).normal(0.0F, 0.0F, 1.0F).endVertex();
                            }
                        }
                    }
                }
            } else {
                for (int x = -fastRadius; x < fastRadius; x += 32) {
                    for (int z = -fastRadius; z < fastRadius; z += 32) {
                        builder.vertex((double) (x + 0), 0.0D, (double) (z + 32)).uv((float) (x + 0) * 0.00390625F + u0, (float) (z + 32) * 0.00390625F + v0).color(1.0F, 1.0F, 1.0F, 0.8F).normal(0.0F, -1.0F, 0.0F).endVertex();
                        builder.vertex((double) (x + 32), 0.0D, (double) (z + 32)).uv((float) (x + 32) * 0.00390625F + u0, (float) (z + 32) * 0.00390625F + v0).color(1.0F, 1.0F, 1.0F, 0.8F).normal(0.0F, -1.0F, 0.0F).endVertex();
                        builder.vertex((double) (x + 32), 0.0D, (double) (z + 0)).uv((float) (x + 32) * 0.00390625F + u0, (float) (z + 0) * 0.00390625F + v0).color(1.0F, 1.0F, 1.0F, 0.8F).normal(0.0F, -1.0F, 0.0F).endVertex();
                        builder.vertex((double) (x + 0), 0.0D, (double) (z + 0)).uv((float) (x + 0) * 0.00390625F + u0, (float) (z + 0) * 0.00390625F + v0).color(1.0F, 1.0F, 1.0F, 0.8F).normal(0.0F, -1.0F, 0.0F).endVertex();
                    }
                }
            }

            return builder.end();
        } catch (Throwable t) {
            builder.discard();
            throw t;
        }
    }
}
