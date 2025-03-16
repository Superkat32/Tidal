package net.superkat.tidal.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.superkat.tidal.TidalClient;
import net.superkat.tidal.sprite.TidalSpriteHandler;
import net.superkat.tidal.sprite.WaveSpriteProvider;
import net.superkat.tidal.wave.TidalWaveHandler;
import net.superkat.tidal.wave.Wave;
import org.joml.Matrix4f;

import java.util.Set;

public class WaveRenderer {
    public TidalWaveHandler handler;
    public ClientWorld world;

    public WaveRenderer(TidalWaveHandler handler, ClientWorld world) {
        this.handler = handler;
        this.world = world;
    }

    public void render(WorldRenderContext context) {
        ObjectArrayList<Wave> waves = this.handler.getWaves();
        if(waves == null || waves.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();

        LightmapTextureManager lightmapTextureManager = client.gameRenderer.getLightmapTextureManager();
        float tickDelta = context.tickCounter().getTickDelta(false);
        Camera camera = context.camera();

        lightmapTextureManager.enable();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getRenderTypeTripwireProgram);
        RenderSystem.setShaderTexture(0, TidalSpriteHandler.WAVE_ATLAS_ID);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        for (Wave wave : waves) {
            renderWave(camera, wave);
        }

        renderOverlays(camera, handler.coveredBlocks);

        if(MinecraftClient.getInstance().getEntityRenderDispatcher().shouldRenderHitboxes()) {
            for (Wave wave : waves) {
                MatrixStack matrixStack = new MatrixStack();
                VertexConsumer buffer = context.consumers().getBuffer(RenderLayer.getLines());
                Vec3d cameraPos = camera.getPos();
                matrixStack.translate(-cameraPos.getX(), -cameraPos.getY(), -cameraPos.getZ());
                WorldRenderer.drawBox(matrixStack, buffer, wave.getBoundingBox(), 1f, 1f, 1f, 1f);
            }
        }

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        lightmapTextureManager.disable();
    }

    public void renderWave(Camera camera, Wave wave) {
        if(wave == null) return;
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT);
        if(buffer == null) return;

        MatrixStack matrices = new MatrixStack();
        matrices.push();

        Box box = wave.getBoundingBox();
        Vec3d center = box.getBottomCenter();
        Vec3d cameraPos = camera.getPos();
        Vec3d transPos = center.subtract(cameraPos);

        matrices.push();
        matrices.translate(transPos.x, transPos.y, transPos.z); //offsets to the wave's position
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-wave.yaw + 90)); //rotate wave left/right
        matrices.translate(-wave.width / 3, 0, 0);
        matrices.scale(3f, 1, 3f);

        Matrix4f posMatrix = matrices.peek().getPositionMatrix();

        Sprite colorableSprite = wave.getColorableSprite();
        Sprite whiteSprite = wave.getWhiteSprite();

        boolean washingUp = wave.isWashingUp();
        Sprite washingColorableSprite = wave.getWashedColorableSprite();
        Sprite washingWhiteSprite = wave.getWashedWhiteSprite();

        int light = wave.getLight();

        //normal wave texture
        for (int i = 0; i < wave.width; i++) {
            waveQuad(posMatrix, buffer, colorableSprite, wave.age, i, 0, 0, 1, wave.length, wave.red, wave.green, wave.blue, wave.alpha, light);
            waveQuad(posMatrix, buffer, whiteSprite, wave.age, i, 0.05f, 0, 1, wave.length, 1f, 1f, 1f, wave.alpha, light);
        }

        //beneath wave texture after hitting shore
        if(washingUp && wave.bigWave) {
            float washingZ = (float) Math.sin((double) wave.getWashingAge() / 40) + 1.15f;
            matrices.scale(1.25f, 1, 1);
            for (int i = 0; i < wave.width; i++) {
                waveQuad(posMatrix, buffer, washingColorableSprite, wave.getWashingAge(), i - 0.15f, -0.05f, washingZ, 1, 2, wave.red, wave.green, wave.blue, wave.alpha, light);
                waveQuad(posMatrix, buffer, washingWhiteSprite, wave.getWashingAge(), i - 0.15f, -0.01f, washingZ, 1, 2, 1f, 1f, 1f, wave.alpha, light);
            }
        }

        matrices.pop();

        BuiltBuffer builtBuffer = buffer.endNullable();
        if(builtBuffer == null) return;
        BufferRenderer.drawWithGlobalProgram(builtBuffer);
    }

    private void waveQuad(Matrix4f matrix4f, BufferBuilder buffer, Sprite sprite, int waveAge, float x, float y, float z, float width, float length, float red, float green, float blue, float alpha, int light) {
        float halfWidth = width / 2f;
        float halfLength = length / 2f;

        int frame = WaveSpriteProvider.getFrameFromAge(sprite, waveAge);
        float u0 = WaveSpriteProvider.getMinU(sprite);
        float u1 = WaveSpriteProvider.getMaxU(sprite);
        float v0 = WaveSpriteProvider.getMinV(sprite, frame);
        float v1 = WaveSpriteProvider.getMaxV(sprite, frame);

//        float u0 = 0f;
//        float u1 = 1f;
//        float v0 = 0f;
//        float v1 = 1f;

        buffer.vertex(matrix4f, x - halfWidth, y, z - halfLength)
                .color(red, green, blue, alpha).texture(u0, v1).light(light);

        buffer.vertex(matrix4f, x - halfWidth, y, z + halfLength)
                .color(red, green, blue, alpha).texture(u0, v0).light(light);

        buffer.vertex(matrix4f, x + halfWidth, y, z + halfLength)
                .color(red, green, blue, alpha).texture(u1, v0).light(light);

        buffer.vertex(matrix4f, x + halfWidth, y, z - halfLength)
                .color(red, green, blue, alpha).texture(u1, v1).light(light);
    }

    public void renderOverlays(Camera camera, Set<BlockPos> coveredBlocks) {
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT);
        for (BlockPos covered : coveredBlocks) {
            renderCoverOverlay(buffer, camera, covered);
        }
        BuiltBuffer builtBuffer = buffer.endNullable();
        if(builtBuffer == null) return;
        BufferRenderer.drawWithGlobalProgram(builtBuffer);
    }

    public void renderCoverOverlay(BufferBuilder buffer, Camera camera, BlockPos pos) {
        MatrixStack matrices = new MatrixStack();
        Vec3d cameraPos = camera.getPos();
        Vec3d transPos = pos.toBottomCenterPos().subtract(cameraPos);

        Sprite sprite = TidalClient.TIDAL_SPRITE_HANDLER.getSprite(TidalSpriteHandler.WET_OVERLAY_TEXTURE_ID);
        float u0 = sprite.getMinU();
        float u1 = sprite.getMaxU();
        float v0 = sprite.getMinV();
        float v1 = sprite.getMaxV();

        int light = LightmapTextureManager.pack(0, 0);

        matrices.push();
        matrices.translate(transPos.x - 0.5, transPos.y + 1.01, transPos.z - 0.5); //offsets to the wave's position
        Matrix4f matrix4f = matrices.peek().getPositionMatrix();

        buffer.vertex(matrix4f, 0, 0, 0)
                .color(0.1f, 0.1f, 0.25f, 0.25f).texture(u0, v0).light(light);

        buffer.vertex(matrix4f, 0, 0, 1)
                .color(0.1f, 0.1f, 0.25f, 0.25f).texture(u0, v1).light(light);

        buffer.vertex(matrix4f, 1, 0, 1)
                .color(0.1f, 0.1f, 0.25f, 0.25f).texture(u1, v1).light(light);

        buffer.vertex(matrix4f, 1, 0, 0)
                .color(0.1f, 0.1f, 0.25f, 0.25f).texture(u1, v0).light(light);

        matrices.pop();
    }

}
