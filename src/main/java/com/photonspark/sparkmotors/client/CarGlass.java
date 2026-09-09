package com.photonspark.sparkmotors.client;

import com.mojang.blaze3d.vertex.*;
import com.photonspark.sparkmotors.AutoPropulsionAge;
import net.minecraft.client.renderer.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Glass is geometry, but must never be flushed between opaque car parts.
 * World: one sorted buffer for ALL visible cars, flushed after opaque entities/blocks.
 * GUI: a private sorted pass after the caller's opaque geometry and instrument text.
 * Closed panes retain one visible side per surface; legacy thin panes remain two-sided.
 */
@EventBusSubscriber(modid=AutoPropulsionAge.ID,value=Dist.CLIENT)
public final class CarGlass extends RenderType {
    private static final net.minecraft.resources.ResourceLocation WHITE=AutoPropulsionAge.id("textures/entity/white.png");
    private static final RenderType WORLD=glass("sparkmotors_glass_world",false,true);
    private static final RenderType PREVIEW=glass("sparkmotors_glass_preview",false,false);
    private static final RenderType PREVIEW_CULL=glass("sparkmotors_glass_preview_closed",true,false);
    private static final ByteBufferBuilder WORLD_MEMORY=new ByteBufferBuilder(131072);
    private static MultiBufferSource.BufferSource world=MultiBufferSource.immediate(WORLD_MEMORY);
    private static boolean pending;

    private CarGlass(){super("unused",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,()->{},()->{});}
    private static RenderType glass(String name,boolean cull,boolean particleTarget){
        return create(name,DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,131072,false,true,
            CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new TextureStateShard(WHITE,false,false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(cull?CULL:NO_CULL)
                .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).setDepthTestState(LEQUAL_DEPTH_TEST)
                // Fabulous needs the glass depth in its transparency target for compositing.
                // This runs only after opaque entities, so it cannot erase later cabin parts.
                .setWriteMaskState(particleTarget?COLOR_DEPTH_WRITE:COLOR_WRITE)
                .setOutputState(particleTarget?PARTICLES_TARGET:MAIN_TARGET).createCompositeState(false));
    }
    @SubscribeEvent public static void stage(RenderLevelStageEvent event){
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY){
            // Drop an aborted previous world pass without drawing it into another frame/world.
            if(pending){WORLD_MEMORY.clear();world=MultiBufferSource.immediate(WORLD_MEMORY);pending=false;}
        }else if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_PARTICLES&&pending){
            try{world.endBatch();}finally{pending=false;}
        }
    }
    static Pass begin(boolean preview,MultiBufferSource opaque){return new Pass(preview,opaque);}
    static final class Pass implements AutoCloseable {
        private final boolean preview;
        private final MultiBufferSource opaque;
        private final ByteBufferBuilder memory;
        private final MultiBufferSource.BufferSource local;
        Pass(boolean preview,MultiBufferSource opaque){
            this.preview=preview;this.opaque=opaque;
            memory=preview?new ByteBufferBuilder(32768):null;
            local=preview?MultiBufferSource.immediate(memory):null;
        }
        VertexConsumer buffer(boolean closed){
            if(preview)return local.getBuffer(closed?PREVIEW_CULL:PREVIEW);
            pending=true;return world.getBuffer(WORLD);
        }
        @Override public void close(){
            if(!preview)return;
            try{
                // Includes seats, gauges and any fixed-buffer text submitted by the caller.
                if(opaque instanceof MultiBufferSource.BufferSource source)source.endBatch();
                local.endBatch();
            }finally{memory.close();}
        }
    }
    /** The world pose is camera-relative. CPU culling allows closed and legacy panes to share
     * one globally sorted GPU batch, rather than sorting two separate material buckets.
     * Preview poses are screen-space and deliberately use the normal GPU culling path instead.
     */
    static boolean frontFacing(Matrix4f pose,float[] vertices,int triangle){
        int i=triangle*6;
        var a=pose.transformPosition(vertices[i],vertices[i+1],vertices[i+2],new Vector3f());
        var b=pose.transformPosition(vertices[i+6],vertices[i+7],vertices[i+8],new Vector3f());
        var c=pose.transformPosition(vertices[i+12],vertices[i+13],vertices[i+14],new Vector3f());
        return b.sub(a).cross(c.sub(a)).dot(a)<-1e-10f;
    }
}
