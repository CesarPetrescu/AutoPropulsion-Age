package com.photonspark.sparkmotors.client;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.*;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import java.util.*;

public final class GarageScreen extends Screen {
    private final CarEntity car;
    private int tab,scroll,x,y,w,h,previewWidth,rx,rw,rows,draftLimiter,draftDrive;
    private int lastConfig,lastPaint;
    private final Map<Button,java.util.function.BooleanSupplier> serviceButtons=new LinkedHashMap<>();
    private static final int INK=0xFFE7F0F4,MUTED=0xFF9FB1BE,ACCENT=0xFF42D2C6;
    private static final int[] PALETTE={0x168A91,0xBF333B,0x255EB3,0xE2E5DB,0x282C33,0xE8AF38,0x7448A7,0xDB793E};
    private static final String[] COLOR_NAMES={"Lagoon","Crimson","Cobalt","Pearl","Graphite","Sunburst","Violet","Copper"};
    public GarageScreen(CarEntity car){super(Component.literal("AutoPropulsion Garage"));this.car=car;draftLimiter=car.limiter();draftDrive=Math.round(car.finalDrive()*100);lastConfig=car.config();lastPaint=car.paint();}
    @Override public boolean isPauseScreen(){return false;}
    private void request(int action,int a,int b){CarClient.send(car,action,a,b);}
    private boolean serviceAllowed(){return Math.abs(car.speed())<.3&&!car.ignition();}
    private Button button(String label,int bx,int by,int bw,int bh,Runnable action,String tooltip,boolean service){
        Button b=Button.builder(Component.literal(label),ignored->action.run()).bounds(bx,by,bw,bh).build();
        if(tooltip!=null)b.setTooltip(Tooltip.create(Component.literal(tooltip)));
        addRenderableWidget(b);if(service)serviceButtons.put(b,this::serviceAllowed);return b;
    }
    @Override protected void init(){
        clearWidgets();serviceButtons.clear();w=Math.min(780,width-16);h=Math.min(430,height-16);x=(width-w)/2;y=(height-h)/2;
        previewWidth=Math.max(120,(int)(w*.38));rx=x+previewWidth+20;rw=w-previewWidth-32;
        button("X",x+w-29,y+9,20,20,this::onClose,"Close garage",false);
        String[] names={"Garage","Paint","Tuner","Car"};
        for(int i=0;i<4;i++){final int selected=i;button(names[i],rx+i*rw/4,y+43,rw/4-3,20,()->{tab=selected;init();},null,false).active=i!=tab;}
        int top=y+82;
        switch(tab){
            case 0 -> {
                rows=Math.max(1,Math.min(6,(h-142)/44));scroll=Math.clamp(scroll,0,6-rows);
                for(int i=0;i<rows;i++){
                    Assembly slot=Assembly.values()[scroll+i];int by=top+i*44,bw=(rw-6)/3;
                    for(int v=0;v<=2;v++){
                        final int variant=v;String label=v==0?"Remove":v==1?"Stock":"Sport";
                        int bx=rx+(v==0?2:v-1)*(bw+3);
                        var b=button(label,bx,by+14,bw,20,()->request(CarPackets.INSTALL,slot.ordinal(),variant),
                            v==0?"Returns the installed assembly to your inventory.":"Requires "+slot.itemName(v).replace('_',' ')+". You have "+count(AutoPropulsionAge.partItem(slot,v))+".",true);
                        java.util.function.BooleanSupplier available=()->serviceAllowed()&&slot.variant(car.config())!=variant&&(variant==0||minecraft.player.isCreative()||count(AutoPropulsionAge.partItem(slot,variant))>0);
                        serviceButtons.put(b,available);b.active=available.getAsBoolean();
                    }
                }
                if(rows<6){button("<",rx,y+h-49,23,18,()->{scroll=Math.max(0,scroll-rows);init();},"Previous assemblies",false);button(">",rx+27,y+h-49,23,18,()->{scroll=Math.min(6-rows,scroll+rows);init();},"More assemblies",false);}
            }
            case 1 -> {
                int columns=rw<280?2:4,bw=(rw-9)/columns;
                for(int i=0;i<8;i++){
                    final int color=PALETTE[i];button(COLOR_NAMES[i],rx+(i%columns)*(bw+3),top+26+(i/columns)*30,bw-2,23,()->request(CarPackets.PAINT,color,0),"Apply paint. Consumes one dye of any color in survival.",true);
                }
            }
            case 2 -> {
                int row=top+23;
                button("-",rx,row,24,20,()->{draftLimiter=Math.max(4000,draftLimiter-200);},"Lower rev limiter",false);
                button("+",rx+rw-24,row,24,20,()->{draftLimiter=Math.min(7000,draftLimiter+200);},"Raise rev limiter",false);
                button("-",rx,row+30,24,20,()->{draftDrive=Math.max(280,draftDrive-10);},"Taller final drive",false);
                button("+",rx+rw-24,row+30,24,20,()->{draftDrive=Math.min(480,draftDrive+10);},"Shorter final drive",false);
                button("Apply tune",rx,y+h-48,rw,22,()->request(CarPackets.TUNE,draftLimiter,draftDrive),"Saves both settings to this car. Engine must be off.",true);
            }
            case 3 -> {
                int bw=(rw-5)/2;
                button("Start / stop",rx,top,bw,23,()->request(CarPackets.IGNITION,0,0),"Toggle the engine.",false);
                button("Headlights",rx+bw+5,top,bw,23,()->request(CarPackets.LIGHTS,0,0),"Toggle head and tail lights.",false);
                button("Open / close panels",rx,top+32,rw,23,()->request(CarPackets.PANELS,0,0),"Open all doors, hood and trunk while parked.",false);
                button("Refuel +10 L",rx,top+64,bw,23,()->request(CarPackets.REFUEL,0,0),"Requires one fuel can. Maximum tank capacity: 50 L.",true);
                button("Repair",rx+bw+5,top+64,bw,23,()->request(CarPackets.REPAIR,0,0),"Requires four iron ingots.",true);
            }
        }
    }
    private int count(Item item){int count=0;if(minecraft.player!=null)for(var s:minecraft.player.getInventory().items)if(s.is(item))count+=s.getCount();return count;}
    @Override public void tick(){
        if(car.isRemoved()||minecraft.player==null||car.distanceToSqr(minecraft.player)>160){onClose();return;}
        if(lastConfig!=car.config()||lastPaint!=car.paint()){lastConfig=car.config();lastPaint=car.paint();init();}
        // Re-evaluate parked/engine state without rebuilding the screen or losing focus.
        serviceButtons.forEach((button,available)->button.active=available.getAsBoolean());
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(tab==0&&mx>=rx){scroll=Math.clamp(scroll-(int)Math.signum(vertical),0,6-rows);init();return true;}
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        g.fill(0,0,width,height,0x99101922);g.fill(x,y,x+w,y+h,0xFA101B25);g.fill(x,y,x+w,y+2,ACCENT);
        g.drawString(font,"AUTOPROPULSION",x+13,y+11,ACCENT,false);g.drawString(font,"GARAGE / SEDAN 01",x+13,y+25,MUTED,false);
        g.fill(x+previewWidth+8,y+43,x+previewWidth+9,y+h-14,0xFF2D4050);
        g.fill(x+10,y+44,x+previewWidth,y+h-94,0xFF152735);
        g.drawString(font,"LIVE VEHICLE",x+20,y+54,MUTED,false);
        drawPreview(g,partial);
        int stats=y+h-83;
        g.drawString(font,car.ignition()?"ENGINE RUNNING":"ENGINE OFF",x+18,stats,car.ignition()?ACCENT:0xFFFFC675,false);
        g.drawString(font,String.format(Locale.ROOT,"Fuel  %.1f / 50 L",car.fuel()),x+18,stats+15,INK,false);
        g.drawString(font,"Condition  "+Math.round(car.health())+"%",x+18,stats+29,INK,false);
        g.fill(x+18,stats+43,x+previewWidth-10,stats+47,0xFF30424F);g.fill(x+18,stats+43,x+18+(int)((previewWidth-28)*car.health()/100),stats+47,ACCENT);
        int top=y+82;
        switch(tab){
            case 0 -> {
                for(int i=0;i<rows;i++){
                    Assembly slot=Assembly.values()[scroll+i];int variant=slot.variant(car.config());
                    g.drawString(font,slot.title,rx,top+i*44,INK,false);
                    String current=variant==0?"EMPTY":variant==1?"STOCK":"SPORT";
                    g.drawString(font,current,rx+rw-font.width(current),top+i*44,variant==0?0xFFFFA776:ACCENT,false);
                }
            }
            case 1 -> {
                g.drawString(font,"BODY COLOR / 1 DYE",rx,top,INK,false);
                if(h>300)g.drawWordWrap(font,Component.literal("Paint applies to the body panels. Mechanical parts keep their material colors."),rx,top+165,rw,MUTED);
            }
            case 2 -> {
                g.drawString(font,"ECU / DRIVELINE",rx,top,INK,false);
                g.drawCenteredString(font,"Limiter: "+draftLimiter+" RPM",rx+rw/2,top+29,INK);
                g.drawCenteredString(font,String.format(Locale.ROOT,"Final drive: %.2f",draftDrive/100.0),rx+rw/2,top+59,INK);
                if(h>=280)drawCurve(g,rx,top+88,rw,Math.min(80,h-228));
            }
            case 3 -> {
                if(h>300){
                    g.drawString(font,"CONTROLS",rx,top+111,ACCENT,false);
                    String[] lines={"W / S   Accelerate / brake","A / D   Steer","Space   Handbrake","Z   Forward / reverse (stopped)","R   Ignition      H   Lights","Shift   Leave the car"};
                    for(int i=0;i<lines.length;i++)g.drawString(font,lines[i],rx,top+129+i*13,MUTED,false);
                }
            }
        }
        String footer=serviceAllowed()?"Changes save automatically. Survival uses items from your inventory.":"Park and switch off the engine to change parts, paint or tuning.";
        String clipped=font.plainSubstrByWidth(footer,w-26);
        g.drawString(font,clipped,x+13,y+h-15,serviceAllowed()?MUTED:0xFFFFC675,false);
        super.render(g,mouseX,mouseY,partial);
    }
    @Override public void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float partial){
        // This screen draws its own dimmed world and panels before the widgets.
        // Calling Screen's blur here would blur those already-rendered panels.
    }
    private void drawPreview(GuiGraphics g,float partial){
        int top=y+72,bottom=y+h-97;if(bottom-top<24)return;
        g.enableScissor(x+11,top,x+previewWidth-1,bottom);
        g.pose().pushPose();
        g.pose().translate(x+previewWidth/2.0,(top+bottom)/2.0+16,150);
        float scale=Math.min((previewWidth-22)/5.5f,(bottom-top)/3.3f);
        g.pose().scale(scale,-scale,scale);g.pose().mulPose(Axis.XP.rotationDegrees(23));g.pose().mulPose(Axis.YP.rotationDegrees(325));g.pose().translate(0,-.65,0);
        Lighting.setupForEntityInInventory();RenderSystem.enableDepthTest();
        CarMesh.render(car,partial,g.pose(),g.bufferSource(),LightTexture.FULL_BRIGHT,true);g.flush();
        g.pose().popPose();Lighting.setupFor3DItems();g.disableScissor();
    }
    private void drawCurve(GuiGraphics g,int bx,int by,int bw,int bh){
        g.fill(bx,by,bx+bw,by+bh,0xFF182F3F);
        boolean sport=Assembly.ENGINE.variant(car.config())==2;
        double peak=0;
        for(int i=0;i<bw;i++){
            double rpm=800+i/(double)bw*6200;
            double kw=Assembly.ENGINE.variant(car.config())==0?0:PistonEngine.powerKw(rpm,sport,draftLimiter);peak=Math.max(peak,kw);
            int height=(int)Math.min(bh-18,kw/170*(bh-18));
            g.fill(bx+i,by+bh-5-height,bx+i+1,by+bh-4-height,ACCENT);
        }
        g.drawString(font,String.format(Locale.ROOT,"Estimated %.0f hp",peak*1.341),bx+5,by+5,INK,false);
        g.drawString(font,"800",bx,by+bh+4,MUTED,false);g.drawString(font,"RPM 7000",bx+bw-font.width("RPM 7000"),by+bh+4,MUTED,false);
    }
}
