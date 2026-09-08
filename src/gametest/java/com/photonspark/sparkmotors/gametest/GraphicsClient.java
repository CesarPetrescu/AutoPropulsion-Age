package com.photonspark.sparkmotors.gametest;

import com.photonspark.sparkmotors.entity.CarEntity;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.options.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** APA-3 regression: configure startup before loading a world, then use vanilla video widgets.
 * Never set graphicsMode directly in a loaded world: that bypasses vanilla's renderer rebuild. */
final class GraphicsClient {
    private static final GraphicsStatus[] MODES={GraphicsStatus.FABULOUS,GraphicsStatus.FAST,GraphicsStatus.FANCY,GraphicsStatus.FABULOUS,GraphicsStatus.FAST};
    private static final String[] CASES={"startup-fabulous","menu-fast","menu-fancy","menu-fabulous","menu-fast-again"};
    private static int stage,ticks,frames;
    private static boolean observing,done;
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    static void beforeWorld(Minecraft mc){
        require(mc.level==null,"Startup graphics must be selected before a world exists");
        mc.options.graphicsMode().set(GraphicsStatus.FABULOUS);
        mc.options.save();
    }
    static void rendered(RenderLevelStageEvent event){
        if(observing&&!done&&event.getStage()==RenderLevelStageEvent.Stage.AFTER_LEVEL)frames++;
    }
    static void tick(Minecraft mc,CarEntity car){
        if(done){if(++ticks>=5)mc.stop();return;}
        try{step(mc,car);}catch(Exception e){
            done=true;ClientSmoke.write(mc,"FAILED: graphics case "+CASES[stage]+": "+e);mc.stop();
        }
    }
    private static void step(Minecraft mc,CarEntity car){
        ticks++;require(ticks<240,"Video setting/renderer did not converge");
        if(ticks==1){
            observing=false;frames=0;
            if(stage>0){mc.setScreen(new VideoSettingsScreen(null,mc,mc.options));return;}
            else mc.setScreen(null);
        }
        if(stage>0&&!observing){
            if(mc.screen instanceof UnsupportedGraphicsWarningScreen){
                String accept=Component.translatable("options.graphics.warning.accept").getString();
                var button=(Button)mc.screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals(accept)).findFirst().orElseThrow();
                require(mc.screen.mouseClicked(button.getX()+button.getWidth()/2.,button.getY()+button.getHeight()/2.,0),"GPU warning accept click rejected");
                return;
            }
            require(mc.screen instanceof VideoSettingsScreen,"Expected vanilla Video Settings");
            if(mc.options.graphicsMode().get()!=MODES[stage]){
                var list=(OptionsList)mc.screen.children().stream().filter(OptionsList.class::isInstance).findFirst().orElseThrow();
                var button=list.findOption(mc.options.graphicsMode());
                require(button!=null&&button.active,"Graphics widget unavailable");
                require(mc.screen.mouseClicked(button.getX()+button.getWidth()/2.,button.getY()+button.getHeight()/2.,0),"Graphics widget click rejected");
                return;
            }
            mc.screen.onClose();require(mc.screen==null,"Video settings did not close");
        }
        if(!observing){
            require(mc.options.graphicsMode().get()==MODES[stage],"Wrong active graphics mode");
            // Point the real first-person camera at the spawned car, including its glazing and wheels.
            var delta=car.position().add(0,.65,0).subtract(mc.player.getEyePosition());
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
            mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
            mc.player.yRotO=mc.player.getYRot();mc.player.xRotO=mc.player.getXRot();
            observing=true;frames=0;
        }
        require(mc.options.graphicsMode().get()==MODES[stage],"Graphics silently fell back");
        boolean fabulous=MODES[stage]==GraphicsStatus.FABULOUS;
        require((mc.levelRenderer.getTranslucentTarget()!=null)==fabulous,"Transparency framebuffer does not match active mode");
        if(fabulous){
            var target=mc.levelRenderer.getTranslucentTarget();
            require(target.width==mc.getMainRenderTarget().width&&target.height==mc.getMainRenderTarget().height,"Wrong transparency framebuffer size");
        }
        if(frames>=45){
            ClientSmoke.screenshot("graphics-"+CASES[stage]+".png");
            System.out.println("GRAPHICS_CASE_PASS "+CASES[stage]+" frames="+frames+" mode="+mc.options.graphicsMode().get());
            observing=false;ticks=0;
            if(++stage==MODES.length){
                done=true;System.out.println("GRAPHICS_CLIENT_PASS 5");
                ClientSmoke.write(mc,"PASS: Fabulous before world loading and native Video Settings transitions through Fast, Fancy, Fabulous and Fast; car scene rendered in every mode with matching transparency framebuffers.");
                // tick() allows the final queued framebuffer screenshot to complete before shutdown.
            }
        }
    }
}
