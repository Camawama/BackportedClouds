package net.camacraft.backportedclouds.mixins;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.camacraft.backportedclouds.Config;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow
    @Nullable
    private ClientLevel level;
    @Shadow
    private int ticks;
    @Shadow
    private int prevCloudX;
    @Shadow
    private int prevCloudY;
    @Shadow
    private int prevCloudZ;
    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    private Vec3 prevCloudColor;
    @Shadow
    @Nullable
    private CloudStatus prevCloudsType;
    @Shadow
    private boolean generateClouds;
    @Shadow
    @Nullable
    private VertexBuffer cloudBuffer;
    @Unique
    private float backported__oldFogEnd = 0;

    @Shadow
    protected abstract BufferBuilder.RenderedBuffer buildClouds(BufferBuilder bufferBuilder, double x, double y, double z, Vec3 cloudColor);

    @Shadow
    private int lastViewDistance;
    @Shadow @Final private static ResourceLocation CLOUDS_LOCATION;
    @Unique
    private final BufferBuilder backported__cloudTesselator = new BufferBuilder(1000);
    @Unique
    private Future<BufferBuilder.RenderedBuffer> backported__cloudBuildTask = null;
    @Unique
    private final ExecutorService backported__cloudMesher = Executors.newSingleThreadExecutor(r -> new Thread(r, "CloudMesher"));
    @Unique
    private int backported__cloudsMeshX = 0;
    @Unique
    private int backported__cloudsMeshY = 0;
    @Unique
    private int backported__cloudsMeshZ = 0;
    @Unique
    private int backported__cloudMeshTargetX = 0;
    @Unique
    private int backported__cloudMeshTargetY = 0;
    @Unique
    private int backported__cloudMeshTargetZ = 0;

    @Unique
    private static Boolean backported__isEmbeddiumLoaded = null;

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    public void backported__renderClouds(PoseStack poseStack, Matrix4f matrix4f, float partialTicks, double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
        if (backported__isEmbeddiumLoaded == null) {
            backported__isEmbeddiumLoaded = net.minecraftforge.fml.ModList.get().isLoaded("embeddium") || net.minecraftforge.fml.ModList.get().isLoaded("rubidium") || net.minecraftforge.fml.ModList.get().isLoaded("oculus");
        }

        if (backported__isEmbeddiumLoaded) {
            return; // Let Sodium/Embeddium handle cloud rendering
        }

        ci.cancel();

        if (level.effects().renderClouds(level, ticks, partialTicks, poseStack, cameraX, cameraY, cameraZ, matrix4f))
            return;
        float cloudHeight = this.level.effects().getCloudHeight();
        if (!Float.isNaN(cloudHeight)) {
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.depthMask(true);
            double timeOffset = (double)(((float)this.ticks + partialTicks) * 0.03F);
            double cloudX = (cameraX + timeOffset) / 12.0D;
            double cloudY = (double)(cloudHeight - (float)cameraY + 0.33F);
            double cloudZ = cameraZ / 12.0D + (double)0.33F;
            float cloudXOffset = (float)(cloudX - (double)Mth.floor(cloudX));
            float cloudYOffset = (float)(cloudY / 4.0D - (double)Mth.floor(cloudY / 4.0D)) * 4.0F;
            float cloudZOffset = (float)(cloudZ - (double)Mth.floor(cloudZ));
            Vec3 cloudColor = this.level.getCloudColor(partialTicks);
            int floorCloudX = (int)Math.floor(cloudX);
            int floorCloudY = (int)Math.floor(cloudY / 4.0D);
            int floorCloudZ = (int)Math.floor(cloudZ);
            if (floorCloudX != this.prevCloudX || floorCloudY != this.prevCloudY || floorCloudZ != this.prevCloudZ || this.minecraft.options.getCloudsType() != this.prevCloudsType || this.prevCloudColor.distanceToSqr(cloudColor) > 2.0E-4D) {
                this.prevCloudX = floorCloudX;
                this.prevCloudY = floorCloudY;
                this.prevCloudZ = floorCloudZ;
                this.prevCloudColor = cloudColor;
                this.prevCloudsType = this.minecraft.options.getCloudsType();
                this.generateClouds = true;
            }

            if (this.backported__cloudBuildTask != null) {
                if (backported__cloudBuildTask.isDone()) {
                    try {
                        if (this.cloudBuffer != null) {
                            this.cloudBuffer.close();
                        }

                        this.cloudBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                        BufferBuilder.RenderedBuffer builtBuffer = backported__cloudBuildTask.get();
                        this.cloudBuffer.bind();
                        this.cloudBuffer.upload(builtBuffer);
                        VertexBuffer.unbind();

                        this.backported__cloudsMeshX = this.backported__cloudMeshTargetX;
                        this.backported__cloudsMeshY = this.backported__cloudMeshTargetY;
                        this.backported__cloudsMeshZ = this.backported__cloudMeshTargetZ;
                        this.backported__cloudBuildTask = null;
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                }
            } else {
                if (this.generateClouds) {
                    this.backported__cloudMeshTargetX = floorCloudX;
                    this.backported__cloudMeshTargetY = floorCloudY;
                    this.backported__cloudMeshTargetZ = floorCloudZ;
                    this.backported__cloudBuildTask = this.backported__cloudMesher.submit(() -> {
                        this.backported__cloudTesselator.clear();
                        return this.buildClouds(this.backported__cloudTesselator,  cloudX, cloudY, cloudZ, cloudColor);
                    });
                    this.generateClouds = false;
                }
            }

            RenderSystem.setShader(GameRenderer::getPositionTexColorNormalShader);
            RenderSystem.setShaderTexture(0, CLOUDS_LOCATION);
            backported__oldFogEnd = RenderSystem.getShaderFogEnd();
            // Reverted to original behavior for fog extension, as per user's request to avoid artifacts
            RenderSystem.setShaderFogEnd((float) (backported__oldFogEnd * Config.COMMON.cloudDistanceMultiplier.get()));

            poseStack.pushPose();
            poseStack.scale(12.0F, 1.0F, 12.0F);
            poseStack.translate((-cloudXOffset) + (this.backported__cloudsMeshX - floorCloudX), cloudYOffset - ((this.backported__cloudsMeshY - floorCloudY) * 4.0), (-cloudZOffset) + (this.backported__cloudsMeshZ - floorCloudZ));
            if (this.cloudBuffer != null) {
                this.cloudBuffer.bind();
                int fancyClouds = this.prevCloudsType == CloudStatus.FANCY ? 0 : 1;

                for(int i = fancyClouds; i < 2; ++i) {
                    if (i == 0) {
                        RenderSystem.colorMask(false, false, false, false);
                    } else {
                        RenderSystem.colorMask(true, true, true, true);
                    }

                    ShaderInstance shaderinstance = RenderSystem.getShader();
                    this.cloudBuffer.drawWithShader(poseStack.last().pose(), matrix4f, shaderinstance);
                }

                VertexBuffer.unbind();
            }

            RenderSystem.setShaderFogEnd(backported__oldFogEnd);

            poseStack.popPose();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    @ModifyConstant(
            method = "buildClouds",
            constant = @Constant(intValue = -3)
    )
    private int backported__fancyForStart(int constant) {
        return -(backported__viewDistanceModified() - 1);
    }

    @ModifyConstant(
            method = "buildClouds",
            constant = @Constant(intValue = 4)
    )
    private int backported__fancyForEnd(int constant) {
        return backported__viewDistanceModified();
    }

    @ModifyConstant(
            method = "buildClouds",
            constant = @Constant(intValue = -32)
    )
    private int backported__fastForStart(int constant) {
        return -(backported__viewDistanceModified() * 4);
    }

    @ModifyConstant(
            method = "buildClouds",
            constant = @Constant(intValue = 32)
    )
    private int backported__fastForEnd(int constant) {
        return (backported__viewDistanceModified() * 4);
    }

    @Unique
    private int backported__viewDistanceModified() {
        // This is the critical fix: use cloudRenderDistance directly
        return Config.COMMON.cloudRenderDistance.get();
    }
}