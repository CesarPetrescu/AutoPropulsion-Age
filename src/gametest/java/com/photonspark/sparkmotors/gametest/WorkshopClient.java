package com.photonspark.sparkmotors.gametest;

import com.photonspark.sparkmotors.client.*;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.sim.ComponentSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.neoforge.client.gui.ModListScreen;
import net.neoforged.neoforge.client.gui.widget.ModListWidget;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;

/** Real rendered screens, real scaled mouse dispatch, several framebuffer sizes and GUI settings. */
final class WorkshopClient {
    private static int brandTicks,combo,page,ticks,cases;
    private static boolean branded,done;
    private static final int[][] SIZES={{1024,600,1},{1024,600,0},{1440,900,2},{1440,900,0},{1920,1080,2},{1920,1080,0}};
    private static final List<String> pages=new ArrayList<>();
    static {
        for(int i=0;i<6;i++)pages.add("garage-"+i);
        pages.add("engine-more");pages.add("engine-last");
        for(int i=0;i<ComponentSlot.ALL.size();i+=8)pages.add("parts-"+i);
        for(var type:com.photonspark.sparkmotors.sim.electric.Powertrain.values())if(type.electric())pages.add("electric-"+type.ordinal());
        pages.add("muffler");pages.add("tests");pages.add("drive");
    }
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException(text);}
    static boolean branding(Minecraft mc){
        if(branded)return true;
        try {
            if(brandTicks==0){
                if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return false;
                mc.options.guiScale().set(2);mc.resizeDisplay();
                var screen=new ModListScreen(mc.screen);mc.setScreen(screen);
                var list=(ModListWidget)screen.children().stream().filter(ModListWidget.class::isInstance).findFirst().orElseThrow();
                var entry=list.children().stream().filter(e->e.getInfo().getModId().equals("sparkmotors")).findFirst().orElseThrow();
                entry.setFocused(true);
                require(entry.getInfo().getLogoFile().orElse("").equals("autopropulsion-age.png"),"Missing loaded logo metadata");
                var field=ModListScreen.class.getDeclaredField("modInfo");field.setAccessible(true);var panel=field.get(screen);
                var logo=panel.getClass().getDeclaredField("logoPath");logo.setAccessible(true);
                require(logo.get(panel)!=null,"NeoForge could not load the PNG into the Mods screen");
            }
            brandTicks++;
            if(brandTicks==20)ClientSmoke.screenshot("branding-mods-list.png");
            if(brandTicks==30){branded=true;System.out.println("MOD_LOGO_CLIENT_PASS");mc.setScreen(new TitleScreen());}
        }catch(Exception e){fail(mc,"branding: "+e);}
        return false;
    }
    static void tick(Minecraft mc,CarEntity car){
        if(done)return;
        try{step(mc,car);}catch(Exception e){fail(mc,"UI combo "+combo+" page "+page+": "+e);}
    }
    private static void fail(Minecraft mc,String reason){done=true;ClientSmoke.write(mc,"FAILED: "+reason);mc.stop();}
    private static void step(Minecraft mc,CarEntity car)throws Exception{
        ticks++;
        if(ticks==1){
            if(combo==0)ConfiguredGeometryClient.verify(mc);
            int[] size=SIZES[combo];
            GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),size[0],size[1]);
            mc.options.guiScale().set(size[2]);mc.resizeDisplay();
        }
        if(ticks<25)return;
        int step=(ticks-25)%8;
        if(step==0){
            String name=pages.get(page);
            if(name.startsWith("electric-")){
                var id=car.getUUID();var type=com.photonspark.sparkmotors.sim.electric.Powertrain.byId(Integer.parseInt(name.substring(9)));
                mc.getSingleplayerServer().execute(()->{var serverCar=(CarEntity)mc.getSingleplayerServer().overworld().getEntity(id);if(serverCar!=null)serverCar.initializePowertrain(type,.5);});
                mc.setScreen(new ElectricScreen(car));
            }else if(name.startsWith("garage-"))mc.setScreen(new GarageScreen(car,Integer.parseInt(name.substring(7))));
            else if(name.startsWith("engine-")){
                mc.setScreen(new GarageScreen(car,4));ClientSmoke.press(mc,"More parts",0);
                if(name.endsWith("last"))ClientSmoke.press(mc,"More parts",0);
            }else if(name.equals("drive"))mc.setScreen(new DriveScreen(car));
            else {
                var service=new ServiceScreen(car);mc.setScreen(service);
                if(name.startsWith("parts-"))service.select(ComponentSlot.ALL.get(Integer.parseInt(name.substring(6))).key());
                if(name.equals("muffler"))service.select("exhaust.muffler");
                if(name.equals("tests"))ClientSmoke.press(mc,"Tests / fluids",0);
            }
        }
        if(step==3){
            var screen=(WorkshopScreen)mc.screen;
            var buttons=screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
            for(var a:buttons){
                require(a.getX()>=0&&a.getY()>=0&&a.getRight()<=screen.width&&a.getBottom()<=screen.height,"Off-screen button: "+a.getMessage().getString());
                require(mc.font.width(a.getMessage())<=a.getWidth()-4,"Clipped button label: "+a.getMessage().getString());
                for(var b:buttons)if(a!=b)require(a.getRight()<=b.getX()||b.getRight()<=a.getX()||a.getBottom()<=b.getY()||b.getBottom()<=a.getY(),"Overlapping controls: "+a.getMessage().getString()+" / "+b.getMessage().getString());
            }
            if(combo==1||combo==5||pages.get(page).equals("tests"))
                ClientSmoke.screenshot("ui-"+combo+"-"+pages.get(page)+".png");
            System.out.println("WORKSHOP_UI_CASE_PASS "+combo+" "+pages.get(page)+" widgets="+buttons.size());cases++;
        }
        if(step==5){
            var screen=(WorkshopScreen)mc.screen;
            var exit=screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b->b.active&&(b.getMessage().getString().equals("Garage")||b.getMessage().getString().equals("X"))).findFirst().orElseThrow();
            screen.mouseClicked((exit.getX()+exit.getWidth()/2.0)*screen.contentScale(),(exit.getY()+exit.getHeight()/2.0)*screen.contentScale(),0);
            require(mc.screen!=screen,"Scaled mouse input missed navigation button");
        }
        if(step==7){
            if(++page<pages.size())return;
            page=0;ticks=0;
            if(++combo<SIZES.length)return;
            done=true;
            Files.writeString(mc.gameDirectory.toPath().resolve("workshop-ui.json"),"{\"passed\":true,\"cases\":"+cases+",\"resolutions\":3,\"gui_settings\":[1,2,0],\"scaled_navigation\":true}\n");
            System.out.println("WORKSHOP_UI_PASS "+cases+" MOD_LOGO_CLIENT_PASS");
            ClientSmoke.write(mc,"PASS: all workshop pages at three resolutions, manual/Auto GUI scales, scaled mouse navigation and loaded mod logo.");mc.stop();
        }
    }
}
