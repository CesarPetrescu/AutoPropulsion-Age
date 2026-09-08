package com.photonspark.sparkmotors.gametest;

import com.photonspark.sparkmotors.client.CarClient;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.ComponentSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid="sparkmotors",value=Dist.CLIENT)
public final class MultiplayerClient {
    private static boolean connecting;private static int lastStage=-1;private static final long start=System.nanoTime();
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        String client=System.getProperty("sparkmotors.multiClient");if(client==null)return;var mc=Minecraft.getInstance();GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;
        try{
            if((System.nanoTime()-start)/1e9>280)throw new IllegalStateException("Network client timeout at stage "+lastStage);
            if(mc.screen instanceof AccessibilityOnboardingScreen){mc.options.onboardAccessibility=false;mc.setScreen(new TitleScreen());}
            if(!connecting&&mc.screen instanceof TitleScreen){connecting=true;mc.options.renderDistance().set(4);mc.options.framerateLimit().set(30);ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString("127.0.0.1:25576"),new ServerData("Local mechanics test","127.0.0.1:25576",ServerData.Type.OTHER),false,null);}
            Path root=MultiplayerServer.root();if(mc.player==null||mc.level==null||!Files.exists(root.resolve("stage.txt")))return;
            int stage=Integer.parseInt(Files.readString(root.resolve("stage.txt")).trim());if(stage==lastStage)return;
            var ids=Files.readString(root.resolve("cars.txt")).trim().split(",");if(!(mc.level.getEntity(Integer.parseInt(ids[0])) instanceof CarEntity first)||!(mc.level.getEntity(Integer.parseInt(ids[1])) instanceof CarEntity second))return;
            int slot=ComponentSlot.ALL.indexOf(ComponentSlot.byKey("cooling.upper_hose"));
            if(stage==1&&client.equals("B"))CarClient.send(first,CarPackets.COMPONENT_SWAP,slot,0);
            if(stage==2&&client.equals("A"))CarClient.send(first,CarPackets.COMPONENT_SWAP,slot,0);
            if(stage==3){if(first.mechanics().get("cooling.upper_hose")!=null)return;if(client.equals("A")){mc.player.getInventory().selected=0;mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(0));mc.player.drop(false);}}
            if(stage==4&&client.equals("B"))CarClient.send(second,CarPackets.COMPONENT_SWAP,slot,1);
            if(stage==5){var used=second.mechanics().get("cooling.upper_hose");if(used==null||used.wear()!=.45||used.damage()!=.60)return;Files.writeString(root.resolve("client-"+client+".pass"),"PASS: worn traded part visible through actual entity/item network state");System.out.println("MULTIPLAYER_CLIENT_"+client+"_PASS");CarClient.stopSounds();mc.stop();}
            lastStage=stage;
        }catch(Throwable failure){try{Files.writeString(MultiplayerServer.root().resolve("client-"+client+".failed"),failure.toString());}catch(Exception ignored){}failure.printStackTrace();mc.stop();}
    }
}
