package com.photonspark.sparkmotors.client;

import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.LightTexture;
import com.mojang.math.Axis;
import com.mojang.blaze3d.platform.Lighting;
import java.util.*;

/** Physical service operations use the same server component objects as driving and inventory. */
public final class ServiceScreen extends Screen {
    private final CarEntity car;
    private int x,y,w,h,scroll,selected=10,rows;
    private boolean tests;
    private float yaw=325,pitch=25,zoom=1,panX,panY;
    private static final int TEXT=0xFFE6F1F3, MUTED=0xFF9CB1BD, ACCENT=0xFF42D2C6;
    public ServiceScreen(CarEntity car){super(Component.literal("Component workshop"));this.car=car;}
    public String selectedKey(){return ComponentSlot.ALL.get(selected).key();}
    public void select(String key){selected=ComponentSlot.ALL.indexOf(ComponentSlot.byKey(key));scroll=Math.clamp(selected-2,0,ComponentSlot.ALL.size()-rows);init();}
    @Override public boolean isPauseScreen(){return false;}
    private void send(int action,int a,int b){CarClient.send(car,action,a,b);}
    private void button(String label,int bx,int by,int bw,Runnable callback){addRenderableWidget(Button.builder(Component.literal(label),b->callback.run()).bounds(bx,by,bw,20).build());}
    @Override protected void init(){
        clearWidgets();w=Math.min(800,width-16);h=Math.min(430,height-16);x=(width-w)/2;y=(height-h)/2;int rx=x+w/2+10,rw=w/2-24;
        rows=Math.max(3,(h-220)/21);scroll=Math.clamp(scroll,0,ComponentSlot.ALL.size()-rows);
        button("Garage",x+w-80,y+9,68,()->minecraft.setScreen(new GarageScreen(car)));
        button("Hood",x+16,y+h-43,65,()->send(CarPackets.HOOD,0,0));
        button("Jack",x+85,y+h-43,65,()->send(CarPackets.JACK,0,0));
        button("Underside",x+154,y+h-43,84,()->{pitch=-70;zoom=1.2f;panX=panY=0;});
        button("Reset view",x+242,y+h-43,85,()->{pitch=25;yaw=325;zoom=1;panX=panY=0;});
        button("Parts",rx,y+29,rw/2-3,()->{tests=false;init();});button("Tests / fluids",rx+rw/2+2,y+29,rw/2-2,()->{tests=true;init();});
        if(tests){
            button("Pressure test / 10s",rx,y+81,rw,()->send(CarPackets.DIAGNOSE,0,0));
            button("Inspect fluids",rx,y+108,rw/2-3,()->send(CarPackets.DIAGNOSE,1,0));
            button("Compression test",rx+rw/2+2,y+108,rw/2-2,()->send(CarPackets.DIAGNOSE,5,0));
            button("Measure tire",rx,y+135,rw/2-3,()->send(CarPackets.DIAGNOSE,2,Math.max(0,ComponentSlot.ALL.get(selected).corner())));
            button("Inflate tire",rx+rw/2+2,y+135,rw/2-2,()->send(CarPackets.FLUID_SERVICE,3,Math.max(0,ComponentSlot.ALL.get(selected).corner())));
            button("Multimeter",rx,y+162,rw/2-3,()->send(CarPackets.DIAGNOSE,3,0));
            button("Oil pressure test",rx+rw/2+2,y+162,rw/2-2,()->send(CarPackets.DIAGNOSE,4,0));
            button("Fill coolant",rx,y+197,rw/2-3,()->send(CarPackets.FLUID_SERVICE,0,0));
            button("Fill oil",rx+rw/2+2,y+197,rw/2-2,()->send(CarPackets.FLUID_SERVICE,1,0));
            button("Fill brake fluid",rx,y+224,rw/2-3,()->send(CarPackets.FLUID_SERVICE,2,0));
            button("Clear history",rx+rw/2+2,y+224,rw/2-2,()->send(CarPackets.CLEAR_FAULTS,0,0));return;
        }
        for(int i=0;i<rows;i++){int index=scroll+i;var slot=ComponentSlot.ALL.get(index);button((index==selected?"> ":"")+font.plainSubstrByWidth(slot.title(),rw-20),rx,y+54+i*21,rw,()->{selected=index;init();});}
        button("Previous",rx,y+58+rows*21,rw/2-3,()->{scroll=Math.max(0,scroll-rows);init();});
        button("More",rx+rw/2+2,y+58+rows*21,rw/2-2,()->{scroll=Math.min(ComponentSlot.ALL.size()-rows,scroll+rows);init();});
        var slot=ComponentSlot.ALL.get(selected);
        button("Remove part",rx,y+h-70,rw/2-3,()->send(CarPackets.COMPONENT_SWAP,selected,0));
        button("Install part",rx+rw/2+2,y+h-70,rw/2-2,()->send(CarPackets.COMPONENT_SWAP,selected,1));
        if(slot.key().equals("exhaust.muffler"))button("Fit sport muffler",rx,y+h-44,rw,()->send(CarPackets.COMPONENT_SWAP,selected,2));
    }
    @Override public void tick(){if(car.isRemoved()||minecraft.player==null||car.distanceToSqr(minecraft.player)>160)onClose();}
    @Override public boolean mouseScrolled(double mx,double my,double hx,double vy){if(mx<x+w/2){zoom=(float)Math.clamp(zoom*Math.pow(1.13,vy),.35,4);return true;}scroll=Math.clamp(scroll-(int)Math.signum(vy),0,ComponentSlot.ALL.size()-rows);init();return true;}
    @Override public boolean mouseDragged(double mx,double my,int b,double dx,double dy){if(mx<x+w/2){if(b==0){yaw+=dx;pitch=(float)Math.clamp(pitch+dy,-89,89);}else{panX+=dx;panY+=dy;}return true;}return super.mouseDragged(mx,my,b,dx,dy);}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0x99101922);g.fill(x,y,x+w,y+h,0xFA101B25);g.fill(x,y,x+w,y+2,ACCENT);
        g.drawString(font,"COMPONENT WORKSHOP",x+16,y+14,ACCENT,false);g.drawString(font,"Orbit: drag / Pan: right drag / Zoom: scroll",x+16,y+36,MUTED,false);
        int rx=x+w/2+10,rw=w/2-24;
        g.enableScissor(x+10,y+53,x+w/2-4,y+h-100);g.pose().pushPose();
        g.pose().translate(x+w/4+panX,y+h/2+panY,150);float scale=Math.min((w/2-30)/5.5f,(h-130)/3.3f)*zoom;g.pose().scale(scale,-scale,scale);
        g.pose().mulPose(Axis.XP.rotationDegrees(pitch));g.pose().mulPose(Axis.YP.rotationDegrees(yaw));g.pose().translate(0,-.65,0);
        Lighting.setupForEntityInInventory();CarMesh.render(car,partial,g.pose(),g.bufferSource(),LightTexture.FULL_BRIGHT,true,false,false,selectedKey());g.flush();g.pose().popPose();Lighting.setupFor3DItems();g.disableScissor();
        var slot=ComponentSlot.ALL.get(selected);var part=car.mechanics().get(slot.key());int by=y+89+rows*21;
        if(tests){
            g.drawString(font,"TEST RESULTS",rx,y+267,ACCENT,false);g.drawWordWrap(font,Component.literal(car.diagnostic()),rx,y+284,rw,TEXT);
            g.drawWordWrap(font,Component.literal("Stored warnings: "+String.join(", ",car.mechanics().faultHistory())),rx,y+h-82,rw,MUTED);
        }else{
        g.drawString(font,font.plainSubstrByWidth(slot.key(),rw),rx,by,ACCENT,false);
        g.drawString(font,part==null?"Empty mount":"Installed: "+font.plainSubstrByWidth(part.item(),rw-65),rx,by+15,TEXT,false);
        g.drawString(font,"Access: "+slot.access().name().toLowerCase(Locale.ROOT),rx,by+30,MUTED,false);
        g.drawString(font,slot.hardware()!=null?"Change this assembly in the Engine tab.":"Removed parts retain wear, faults and serial.",rx,y+h-90,MUTED,false);
        if(part!=null&&CircuitPhysics.leak(part,1)>.05&&(slot.key().contains("hose")||slot.key().equals("engine.cooling")))g.drawString(font,"Observation: wet residue at this component",rx,by+45,0xFFFFC675,false);
        }
        g.drawString(font,car.raised()?"SERVICE JACK RAISED":"CAR ON GROUND",x+16,y+h-90,ACCENT,false);
        g.drawString(font,String.format(Locale.ROOT,"Coolant %.1f L / %.0f C",car.coolant(),car.temperature()),x+16,y+h-75,TEXT,false);
        g.drawString(font,"Park and stop the engine before service. Fluids are retained separately.",x+16,y+h-15,MUTED,false);
        super.render(g,mx,my,partial);
    }
}
