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
    private int lastEngineParts,lastFamily,engineScroll,engineRows;
    private final int[] selectedParts=new int[EnginePart.values().length];
    private int draftBoost;
    private EngineFamily selectedFamily;
    private float engineViewYaw=325;
    private boolean cutaway;
    private final Map<Button,java.util.function.BooleanSupplier> serviceButtons=new LinkedHashMap<>();
    private static final int INK=0xFFE7F0F4,MUTED=0xFF9FB1BE,ACCENT=0xFF42D2C6;
    private static final int[] PALETTE={0x168A91,0xBF333B,0x255EB3,0xE2E5DB,0x282C33,0xE8AF38,0x7448A7,0xDB793E};
    private static final String[] COLOR_NAMES={"Lagoon","Crimson","Cobalt","Pearl","Graphite","Sunburst","Violet","Copper"};
    public GarageScreen(CarEntity car){this(car,0);}
    public GarageScreen(CarEntity car,int initialTab){super(Component.literal("AutoPropulsion Garage"));this.car=car;tab=initialTab;draftLimiter=car.limiter();draftDrive=Math.round(car.finalDrive()*100);draftBoost=Math.round(car.boostTarget()*1000);lastConfig=car.config();lastPaint=car.paint();selectedFamily=car.engineFamily();lastFamily=selectedFamily.ordinal();lastEngineParts=car.engineParts();for(var p:EnginePart.values())selectedParts[p.ordinal()]=p.variant(car.engineParts());}
    @Override public boolean isPauseScreen(){return false;}
    private void request(int action,int a,int b){CarClient.send(car,action,a,b);}
    private boolean serviceAllowed(){return Math.abs(car.speed())<.3&&!car.ignition();}
    private boolean engineServiceAllowed(){return serviceAllowed()&&car.hoodOpen()&&car.hoodProgress>=.95;}
    private Item assemblyItem(Assembly slot,int v){return slot==Assembly.ENGINE?AutoPropulsionAge.engineItem(car.engineFamily(),v):AutoPropulsionAge.partItem(slot,v);}
    private Button button(String label,int bx,int by,int bw,int bh,Runnable action,String tooltip,boolean service){
        Button b=Button.builder(Component.literal(label),ignored->action.run()).bounds(bx,by,bw,bh).build();
        if(tooltip!=null)b.setTooltip(Tooltip.create(Component.literal(tooltip)));
        addRenderableWidget(b);if(service)serviceButtons.put(b,this::serviceAllowed);return b;
    }
    @Override protected void init(){
        clearWidgets();serviceButtons.clear();w=Math.min(780,width-16);h=Math.min(430,height-16);x=(width-w)/2;y=(height-h)/2;
        previewWidth=Math.max(120,(int)(w*.38));rx=x+previewWidth+20;rw=w-previewWidth-32;
        button("X",x+w-29,y+9,20,20,this::onClose,"Close garage",false);
        String[] names={"Garage","Paint","Tuner","Car","Engine","Live","Service"};
        for(int i=0;i<names.length;i++){final int selected=i;button(names[i],rx+i*rw/names.length,y+43,rw/names.length-3,20,()->{if(selected==6)minecraft.setScreen(new ServiceScreen(car));else{tab=selected;init();}},null,false).active=i!=tab;}
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
                            v==0?"Returns the installed assembly to your inventory. Engines retain their service parts.":"Requires "+assemblyItem(slot,v).getDescription().getString()+". Engines need the hood open.",true);
                        java.util.function.BooleanSupplier available=()->serviceAllowed()&&(slot!=Assembly.ENGINE||engineServiceAllowed())&&slot.variant(car.config())!=variant&&(variant==0||minecraft.player.isCreative()||count(assemblyItem(slot,variant))>0);
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
                button("-",rx,row+60,24,20,()->{draftBoost=Math.max(200,draftBoost-100);},"Lower boost target",false);
                button("+",rx+rw-24,row+60,24,20,()->{draftBoost=Math.min(1400,draftBoost+100);},"Raise boost target within the compressor limit",false);
                button("Apply tune",rx,y+h-48,rw/2-3,22,()->request(CarPackets.TUNE,draftLimiter,draftDrive),"Saves rev limiter and final drive.",true);
                button("Apply boost",rx+rw/2+2,y+h-48,rw/2-2,22,()->request(CarPackets.BOOST_TUNE,draftBoost,0),"Saves wastegate / blower bypass target. Actual boost depends on hardware and load.",true);
            }
            case 3 -> {
                int bw=(rw-5)/2;
                button("Start / stop",rx,top,bw,23,()->request(CarPackets.IGNITION,0,0),"Toggle the engine.",false);
                button("Headlights",rx+bw+5,top,bw,23,()->request(CarPackets.LIGHTS,0,0),"Toggle head and tail lights.",false);
                button("Open / close panels",rx,top+32,rw,23,()->request(CarPackets.PANELS,0,0),"Open all doors, hood and trunk while parked.",false);
                button("Refuel +10 L",rx,top+64,bw,23,()->request(CarPackets.REFUEL,0,0),"Requires one fuel can. Maximum tank capacity: 50 L.",true);
                button("Repair",rx+bw+5,top+64,bw,23,()->request(CarPackets.REPAIR,0,0),"Requires four iron ingots.",true);
                button("Open / close hood",rx,top+94,rw,22,()->request(CarPackets.HOOD,0,0),"Operate just the hood. Park before opening it.",false);
            }
            case 4 -> {
                button(cutaway?"Show covers":"Inspect internals",x+18,y+71,previewWidth-36,19,()->{cutaway=!cutaway;init();},"Inspection view hides covers without removing inventory parts.",false);
                button("<",rx,top-2,22,18,()->{selectedFamily=EngineFamily.values()[(selectedFamily.ordinal()+6)%7];init();},"Previous engine family",false);
                button(">",rx+rw-22,top-2,22,18,()->{selectedFamily=EngineFamily.values()[(selectedFamily.ordinal()+1)%7];init();},"Next engine family",false);
                for(int v=1;v<=2;v++){
                    final int grade=v;var fit=button(v==1?"Fit stock":"Fit sport",rx+(v-1)*(rw/2+2),top+22,rw/2-2,20,
                        ()->request(CarPackets.ENGINE_SWAP,selectedFamily.ordinal(),grade),"Swaps the whole engine. Removed engines retain their installed parts.",true);
                    serviceButtons.put(fit,()->engineServiceAllowed()&&(car.engineFamily()!=selectedFamily||Assembly.ENGINE.variant(car.config())!=grade)&&
                        (minecraft.player.isCreative()||count(AutoPropulsionAge.engineItem(selectedFamily,grade))>0));
                }
                engineRows=Math.max(1,Math.min(EnginePart.values().length,(h-232)/40));engineScroll=Math.clamp(engineScroll,0,EnginePart.values().length-engineRows);
                for(int i=0;i<engineRows;i++){
                    EnginePart slot=EnginePart.values()[engineScroll+i];int by=top+55+i*40,variant=selectedParts[slot.ordinal()];
                    button("<",rx,by+13,23,20,()->{selectedParts[slot.ordinal()]=(variant+slot.maxVariant())%(slot.maxVariant()+1);init();},"Previous "+slot.title.toLowerCase(Locale.ROOT)+" option",false);
                    button(">",rx+rw-23,by+13,23,20,()->{selectedParts[slot.ordinal()]=(variant+1)%(slot.maxVariant()+1);init();},"Next "+slot.title.toLowerCase(Locale.ROOT)+" option",false);
                    String label=(variant==0?"Remove": "Fit: "+slot.label(variant));
                    if(slot==EnginePart.INDUCTION&&variant==0)label="Naturally aspirated";
                    String problem=slot.installationProblem(car.engineParts(),variant);
                    var fit=button(font.plainSubstrByWidth(label,rw-66),rx+27,by+13,rw-54,20,()->request(CarPackets.ENGINE_PART,slot.ordinal(),variant),slot.description(variant)+(problem.isEmpty()?"":" "+problem),true);
                    serviceButtons.put(fit,()->engineServiceAllowed()&&Assembly.ENGINE.variant(car.config())>0&&slot.variant(car.engineParts())!=variant&&slot.installationProblem(car.engineParts(),variant).isEmpty()&&
                        (variant==0||minecraft.player.isCreative()||count(AutoPropulsionAge.enginePartItem(slot,variant))>0));
                }
                button("Previous parts",rx,y+h-77,rw/2-3,18,()->{engineScroll=Math.max(0,engineScroll-engineRows);init();},"Browse the ten service slots",false).active=engineScroll>0;
                button("More parts",rx+rw/2+2,y+h-77,rw/2-2,18,()->{engineScroll=Math.min(EnginePart.values().length-engineRows,engineScroll+engineRows);init();},"Browse the ten service slots",false).active=engineScroll+engineRows<EnginePart.values().length;
                button(car.hoodOpen()?"Close hood":"Open hood",rx,y+h-49,rw/2-2,22,()->request(CarPackets.HOOD,0,0),"Open the hood fully before servicing. Doors stay independent.",false);
                button("Start / stop",rx+rw/2+2,y+h-49,rw/2-2,22,()->request(CarPackets.IGNITION,0,0),"Test the assembled engine. Stop it before changing parts.",false);
            }
            case 5 -> {
                button("Start / stop",rx,y+h-49,rw/3-3,22,()->request(CarPackets.IGNITION,0,0),"Start the engine for live readings. Hold C + W while driving to rev with the clutch disengaged.",false);
                var rev=button("Rev test / 2s",rx+rw/3+1,y+h-49,rw/3-3,22,()->request(CarPackets.REV_TEST,0,0),"A two-second throttle test with the clutch disengaged and brakes held. Open the hood, park and start the engine first.",false);
                serviceButtons.put(rev,()->car.engineRunning()&&car.hoodOpen()&&Math.abs(car.speed())<.3);
                var rebuild=button("Rebuild engine",rx+2*rw/3+2,y+h-49,rw/3-2,22,()->request(CarPackets.ENGINE_REBUILD,0,0),"Consumes 12 iron ingots to restore engine condition. Open the hood, park and stop the engine.",true);
                serviceButtons.put(rebuild,()->engineServiceAllowed()&&car.engineHealth()<100&&Assembly.ENGINE.variant(car.config())>0);
            }
        }
    }
    private int count(Item item){int count=0;if(minecraft.player!=null)for(var s:minecraft.player.getInventory().items)if(s.is(item))count+=s.getCount();return count;}
    @Override public void tick(){
        if(car.isRemoved()||minecraft.player==null||car.distanceToSqr(minecraft.player)>160){onClose();return;}
        if(lastConfig!=car.config()||lastPaint!=car.paint()||lastEngineParts!=car.engineParts()||lastFamily!=car.engineFamily().ordinal()){
            for(var p:EnginePart.values())if(p.variant(lastEngineParts)!=p.variant(car.engineParts()))selectedParts[p.ordinal()]=p.variant(car.engineParts());
            if(lastFamily!=car.engineFamily().ordinal())selectedFamily=car.engineFamily();
            lastConfig=car.config();lastPaint=car.paint();lastEngineParts=car.engineParts();lastFamily=car.engineFamily().ordinal();init();
        }
        if(tab==4)for(var widget:children())if(widget instanceof Button b&&(b.getMessage().getString().equals("Open hood")||b.getMessage().getString().equals("Close hood")))b.setMessage(Component.literal(car.hoodOpen()?"Close hood":"Open hood"));
        // Re-evaluate parked/engine state without rebuilding the screen or losing focus.
        serviceButtons.forEach((button,available)->button.active=available.getAsBoolean());
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(tab==0&&mx>=rx){scroll=Math.clamp(scroll-(int)Math.signum(vertical),0,6-rows);init();return true;}
        if(tab==4&&mx>=rx){engineScroll=Math.clamp(engineScroll-(int)Math.signum(vertical),0,EnginePart.values().length-engineRows);init();return true;}
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){
        if(tab==4&&button==0&&mx>=x+10&&mx<rx-10){engineViewYaw+=(float)dx;return true;}
        return super.mouseDragged(mx,my,button,dx,dy);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        g.fill(0,0,width,height,0x99101922);g.fill(x,y,x+w,y+h,0xFA101B25);g.fill(x,y,x+w,y+2,ACCENT);
        g.drawString(font,"AUTOPROPULSION",x+13,y+11,ACCENT,false);g.drawString(font,"GARAGE / SEDAN 01",x+13,y+25,MUTED,false);
        g.fill(x+previewWidth+8,y+43,x+previewWidth+9,y+h-14,0xFF2D4050);
        g.fill(x+10,y+44,x+previewWidth,y+h-94,0xFF152735);
        g.drawString(font,tab==4?"ENGINE / DRAG TO ORBIT":"LIVE VEHICLE",x+20,y+54,MUTED,false);
        drawPreview(g,partial);
        int stats=y+h-83;
        g.drawString(font,car.ignition()?"ENGINE RUNNING":"ENGINE OFF",x+18,stats,car.ignition()?ACCENT:0xFFFFC675,false);
        g.drawString(font,String.format(Locale.ROOT,"Fuel  %.1f / 50 L",car.fuel()),x+18,stats+15,INK,false);
        g.drawString(font,tab==4?String.format(Locale.ROOT,"%.0f C  /  %.2f bar",car.temperature(),car.boost()):"Installed parts: "+car.mechanics().parts().size(),x+18,stats+29,INK,false);
        g.drawString(font,"Warnings: "+car.mechanics().faultHistory().size(),x+18,stats+43,MUTED,false);
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
                g.drawCenteredString(font,String.format(Locale.ROOT,"Boost target: %.2f bar",draftBoost/1000.0),rx+rw/2,top+89,INK);
                if(h>=310)drawCurve(g,rx,top+118,rw,Math.min(80,h-258));
            }
            case 3 -> {
                if(h>300){
                    g.drawString(font,"CONTROLS",rx,top+124,ACCENT,false);
                    String[] lines={"W / S   Accelerate / brake","A / D   Steer","Space   Handbrake","Z   Forward / reverse (stopped)","R   Ignition      H   Lights","Shift   Leave the car"};
                    for(int i=0;i<lines.length;i++)g.drawString(font,lines[i],rx,top+139+i*13,MUTED,false);
                }
            }
            case 4 -> {
                g.drawCenteredString(font,selectedFamily.title,rx+rw/2,top+3,INK);
                for(int i=0;i<engineRows;i++){
                    var slot=EnginePart.values()[engineScroll+i];int by=top+55+i*40;
                    g.drawString(font,slot.title,rx,by,INK,false);
                    String state=slot.label(slot.variant(car.engineParts()));
                    state=font.plainSubstrByWidth(state,rw-font.width(slot.title)-8);g.drawString(font,state,rx+rw-font.width(state),by,ACCENT,false);
                }
                String label=car.engineFamily().title+" / "+(Assembly.ENGINE.variant(car.config())==2?"SPORT":"STOCK");
                g.drawString(font,Assembly.ENGINE.variant(car.config())==0?"NO ENGINE":label,x+18,y+h-111,INK,false);
                g.drawString(font,"Slots "+(engineScroll+1)+"-"+(engineScroll+engineRows)+" / "+EnginePart.values().length+"  |  Arrows choose, Fit installs",rx,top+43,MUTED,false);
            }
            case 5 -> {
                g.drawString(font,"ASSISTED LIVE TELEMETRY",rx,top,ACCENT,false);
                String[] labels={"Crank speed","Throttle / turbo speed","Boost / target","Air:fuel ratio","Coolant / oil","Oil pressure","Shaft torque","Blower drive load","Engine condition"};
                String[] values={String.format(Locale.ROOT,"%.0f RPM",car.rpm()),String.format(Locale.ROOT,"%.0f%% / %.0f%%",car.throttle()*100,car.spool()*100),String.format(Locale.ROOT,"%.2f / %.2f bar",car.boost(),car.boostTarget()),String.format(Locale.ROOT,"%.2f : 1",car.afr()),String.format(Locale.ROOT,"%.0f / %.0f C",car.temperature(),car.oilTemperature()),String.format(Locale.ROOT,"%.2f bar",car.oilPressure()),String.format(Locale.ROOT,"%.0f Nm",car.shaftTorque()),String.format(Locale.ROOT,"%.1f kW",car.blowerKw()),String.format(Locale.ROOT,"%.1f%%",car.engineHealth())};
                int spacing=Math.min(25,Math.max(12,(h-180)/labels.length));
                for(int i=0;i<labels.length;i++){int by=top+22+i*spacing;g.drawString(font,labels[i],rx,by,MUTED,false);g.drawString(font,values[i],rx+rw-font.width(values[i]),by,i==3&&car.boost()>.1&&car.afr()>13.5?0xFFFF8E60:INK,false);}
            }
        }
        String footer=serviceAllowed()?"Changes save automatically. Survival uses items from your inventory.":"Park and switch off the engine to change parts, paint or tuning.";
        if(tab==4)footer=!engineServiceAllowed()?"Park, stop the engine and open the hood fully to work.":!car.engineProblem().isEmpty()?car.engineProblem():"42 hardware choices. Hover a Fit button for the part's behavior and requirements.";
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
        float scale=tab==4?Math.min((previewWidth-22)/1.8f,(bottom-top)/1.45f):Math.min((previewWidth-22)/5.5f,(bottom-top)/3.3f);
        g.pose().scale(scale,-scale,scale);g.pose().mulPose(Axis.XP.rotationDegrees(tab==4?30:23));g.pose().mulPose(Axis.YP.rotationDegrees(tab==4?engineViewYaw:325));g.pose().translate(0,tab==4?-.75:-.65,tab==4?-1.4:0);
        Lighting.setupForEntityInInventory();RenderSystem.enableDepthTest();
        CarMesh.render(car,partial,g.pose(),g.bufferSource(),LightTexture.FULL_BRIGHT,true,tab==4,cutaway);g.flush();
        g.pose().popPose();Lighting.setupFor3DItems();g.disableScissor();
    }
    private void drawCurve(GuiGraphics g,int bx,int by,int bw,int bh){
        g.fill(bx,by,bx+bw,by+bh,0xFF182F3F);
        double maxPower=1;
        for(int rpm=800;rpm<7000;rpm+=100)maxPower=Math.max(maxPower,estimatePower(rpm));
        double peak=0;
        for(int i=0;i<bw;i++){
            double rpm=800+i/(double)bw*6200;
            double kw=estimatePower(rpm);peak=Math.max(peak,kw);
            int height=(int)Math.min(bh-18,kw/(maxPower*1.12)*(bh-18));
            g.fill(bx+i,by+bh-5-height,bx+i+1,by+bh-4-height,ACCENT);
        }
        g.drawString(font,String.format(Locale.ROOT,"Estimated %.0f hp",peak*1.341),bx+5,by+5,INK,false);
        g.drawString(font,"800",bx,by+bh+4,MUTED,false);g.drawString(font,"RPM 7000",bx+bw-font.width("RPM 7000"),by+bh+4,MUTED,false);
    }
    private double estimatePower(double rpm){return EngineBuild.torqueAtBoost(rpm,car.engineFamily(),Assembly.ENGINE.variant(car.config()),car.engineParts(),draftLimiter,90,Math.min(draftBoost/1000.0,EngineBuild.boost(rpm,car.engineParts())))*rpm*Math.PI/30000;}
}
