package com.photonspark.autopropulsion.client;
import com.photonspark.autopropulsion.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public final class GarageScreen extends Screen {
    private final Packets.Garage data;
    private int left,top,w,h,tab;
    private final List<double[]> curve=new ArrayList<>();
    public GarageScreen(Packets.Garage data){super(Component.translatable("screen.autopropulsion.garage"));this.data=data;
        for(String row:data.curve().split(";")){if(row.isBlank())continue;String[] p=row.split(",");if(p.length==3&&curve.size()<64)curve.add(new double[]{Double.parseDouble(p[0]),Double.parseDouble(p[1]),Double.parseDouble(p[2])});}}
    @Override protected void init(){w=Math.min(width-16,550);h=Math.min(height-16,310);left=(width-w)/2;top=(height-h)/2;
        addRenderableWidget(Button.builder(Component.literal("Parts / live data"),b->tab=0).bounds(left+12,top+32,130,20).build());
        addRenderableWidget(Button.builder(Component.literal("Engine dyno"),b->tab=1).bounds(left+148,top+32,108,20).build());
        int y=top+h-28;
        addRenderableWidget(Button.builder(Component.literal("Ignition [I]"),b->action(1,0)).bounds(left+12,y,92,20).build());
        addRenderableWidget(Button.builder(Component.literal("Boost -"),b->action(4,data.boost()-.1f)).bounds(left+110,y,76,20).build());
        addRenderableWidget(Button.builder(Component.literal("Boost +"),b->action(4,data.boost()+.1f)).bounds(left+192,y,76,20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"),b->onClose()).bounds(left+w-76,y,64,20).build());
    }
    private void action(int code,float value){PacketDistributor.sendToServer(new Packets.Action(data.entity(),code,value));}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){if(minecraft==null||minecraft.level==null||!(minecraft.level.getEntity(data.entity()) instanceof VehicleEntity))onClose();}
    // Screen.render invokes renderBackground before rendering widgets in 1.21.1.
    // Draw the panel here once, otherwise a later super.render blurs our own labels.
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt) {
        super.renderBackground(g,mx,my,dt);g.fill(left,top,left+w,top+h,0xFA121C26);g.fill(left,top,left+w,top+3,0xFFFFAD42);
        g.drawString(font,"AUTOPROPULSION  /  GARAGE",left+12,top+13,0xFFF0F4F8,false);
        if(minecraft!=null&&minecraft.level!=null&&minecraft.level.getEntity(data.entity()) instanceof VehicleEntity car) {
            if(tab==0) {
                int x=left+14,y=top+65;
                g.drawString(font,"INSTALLED ASSEMBLY",x,y,0xFFFFAD42,false);y+=17;
                for(String row:data.slots().split("\n")){String[] kv=row.split("=",2);if(kv.length!=2||y>top+h-64)continue;
                    String name=Component.translatable("part."+kv[1].replace(':','.')).getString();
                    g.drawString(font,font.plainSubstrByWidth(kv[0]+": "+name,w/2-20),x,y,0xFFC2CFD9,false);y+=17;}
                x=left+w/2+4;y=top+65;
                String[] lines={"LIVE ECU / BASIC DIAGNOSTICS",String.format(Locale.ROOT,"Engine: %s  |  %04.0f RPM",car.running()?"RUN":"OFF",car.rpm()),
                    String.format(Locale.ROOT,"Speed: %.1f km/h",Math.abs(car.speed())*3.6),String.format(Locale.ROOT,"Fuel: %.1f / 60 L",car.fuel()),
                    String.format(Locale.ROOT,"Coolant: %.1f C",car.temp()),String.format(Locale.ROOT,"Boost: %.2f bar",car.boost()),
                    String.format(Locale.ROOT,"Engine condition: %.1f %%",car.health()),String.format(Locale.ROOT,"Target boost: %.2f bar",data.boost()),
                    data.fault().isBlank()?"DTC: no recorded fault":"DTC: "+data.fault()};
                for(int i=0;i<lines.length&&y<top+h-57;i++){g.drawString(font,font.plainSubstrByWidth(lines[i],w/2-16),x,y,i==0?0xFFFFAD42:0xFFC2CFD9,false);y+=17;}
            } else drawDyno(g);
            g.drawString(font,"Stop engine to change parts or boost. Held parts install by right-click.",left+12,top+h-45,0xFF8EABB9,false);
        }
    }
    private void drawDyno(GuiGraphics g){
        int x=left+42,y=top+76,gw=w-70,gh=Math.max(40,h-154);
        double maxT=1,maxP=1;for(double[] p:curve){maxT=Math.max(maxT,p[1]);maxP=Math.max(maxP,p[2]);}
        for(int i=0;i<=4;i++){int gy=y+gh*i/4;g.fill(x,gy,x+gw,gy+1,0xFF263949);}
        g.fill(x,y,x+1,y+gh,0xFF668399);g.fill(x,y+gh,x+gw,y+gh+1,0xFF668399);
        double lastX=-1,lastT=0,lastP=0;
        for(double[] p:curve){double px=x+(p[0]-1000)/7000*gw,ty=y+gh-p[1]/maxT*gh,py=y+gh-p[2]/maxP*gh;if(lastX>=0){line(g,(int)lastX,(int)lastT,(int)px,(int)ty,0xFFFFAD42);line(g,(int)lastX,(int)lastP,(int)px,(int)py,0xFF64D7DC);}lastX=px;lastT=ty;lastP=py;}
        g.drawString(font,String.format(Locale.ROOT,"Torque  %.1f Nm",maxT),x,y-16,0xFFFFAD42,false);g.drawString(font,String.format(Locale.ROOT,"Power  %.1f kW",maxP),x+180,y-16,0xFF64D7DC,false);
        g.drawString(font,"1000",x,y+gh+5,0xFFADBECA,false);g.drawString(font,"RPM  /  8000",x+gw-74,y+gh+5,0xFFADBECA,false);
        g.drawString(font,"Analytical engine estimate, NOT a measured chassis dyno pull.",left+12,top+h-61,0xFFADBECA,false);
    }
    private static void line(GuiGraphics g,int x0,int y0,int x1,int y1,int c){int n=Math.max(Math.abs(x1-x0),Math.abs(y1-y0));for(int i=0;i<=n;i++){double t=n==0?0:(double)i/n;int x=(int)(x0+(x1-x0)*t),y=(int)(y0+(y1-y0)*t);g.fill(x,y,x+2,y+2,c);}}
}
