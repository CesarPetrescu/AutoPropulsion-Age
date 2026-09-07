package com.photonspark.autopropulsion.client;

import com.photonspark.autopropulsion.GarageMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.Locale;

/** Live server telemetry, a calculated sweep and transactional part removal. */
public final class GarageScreen extends AbstractContainerScreen<GarageMenu> {
    private int tab, page;
    public GarageScreen(GarageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 356; imageHeight = 246; tab = menu.dyno ? 1 : 0;
    }
    public void setTab(int value) { tab = Math.max(0, Math.min(2, value)); rebuildWidgets(); }
    @Override protected void init() { super.init(); buttons(); }
    private void action(int id) { if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }
    private void button(String label, int x, int y, int w, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> action.run()).bounds(leftPos + x, topPos + y, w, 20).build());
    }
    private void buttons() {
        button("Diagnostics", 12, 32, 106, () -> setTab(0)); button("Dyno", 125, 32, 106, () -> setTab(1)); button("Parts", 238, 32, 106, () -> setTab(2));
        if (tab == 0) {
            button("Start / stop", 12, 213, 106, () -> action(0));
            button("Limiter -200", 125, 213, 106, () -> action(1)); button("Limiter +200", 238, 213, 106, () -> action(2));
        } else if (tab == 2) {
            int start = page * 5;
            for (int i = start; i < Math.min(start + 5, menu.sessionPaths.size()); i++) {
                final int index = i;
                button("Remove", 278, 72 + (i - start) * 26, 66, () -> action(100 + index));
            }
            button("Previous", 12, 213, 94, () -> { page = Math.max(0, page - 1); rebuildWidgets(); });
            button("Next", 250, 213, 94, () -> { page = Math.min(Math.max(0, (menu.sessionPaths.size()-1)/5), page+1); rebuildWidgets(); });
        }
    }
    private String value(String format, Object... args) { return String.format(Locale.ROOT, format, args); }
    private void line(GuiGraphics g, String label, String value, int x, int y) {
        g.drawString(font, label, leftPos+x, topPos+y, 0xFF92A9BD, false);
        g.drawString(font, value, leftPos+x, topPos+y+13, 0xFFE8F0F6, false);
    }
    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos+imageWidth, topPos+imageHeight, 0xF5101923);
        g.fill(leftPos, topPos, leftPos+imageWidth, topPos+3, 0xFF4DD2BD);
        g.drawString(font, "AUTOPROPULSION / WORKSHOP", leftPos+12, topPos+13, 0xFFE8F0F6, false);
        if (tab == 0) {
            line(g,"ENGINE",value("%d rpm | %s",menu.data.get(0),menu.data.get(7)==1?"RUNNING":"STOPPED"),12,65);
            line(g,"ROAD SPEED",value("%.1f km/h",Math.abs(menu.data.get(1))/10d),190,65);
            line(g,"FUEL / BOOST",value("%.2f L / %.2f bar",menu.data.get(2)/100d,menu.data.get(5)/1000d),12,103);
            line(g,"COOLANT / OIL",value("%.1f C / %.1f C",menu.data.get(3)/10d,menu.data.get(14)/10d),190,103);
            line(g,"ENGINE HEALTH",value("%.1f%%",menu.data.get(4)/100d),12,141);
            line(g,"LIMITER / STRUCTURAL LIMIT",value("%d rpm / %d Nm",menu.data.get(9),menu.data.get(10)),190,141);
            String dtc = menu.car == null ? "Vehicle unavailable" : menu.car.dtc();
            g.drawString(font, dtc.isEmpty() ? "DTC: none | Missing required parts: " + menu.data.get(11) : "DTC: " + dtc,leftPos+12,topPos+184,dtc.isEmpty()?0xFF87D7B6:0xFFFFAA76,false);
            g.drawString(font,"Limiter edits require a stopped engine + standalone ECU.",leftPos+12,topPos+198,0xFF92A9BD,false);
        } else if (tab == 1) {
            int x=leftPos+35,y=topPos+76,w=290,h=112;
            g.fill(x,y,x+w,y+h,0xFF0A111A);
            double maxTorque=1,maxPower=1;
            for(int i=0;i<GarageMenu.SAMPLES;i++) {
                double torque=menu.data.get(16+i)/10d, rpm=menu.data.get(15)+(menu.data.get(9)-menu.data.get(15))*i/(double)(GarageMenu.SAMPLES-1);
                maxTorque=Math.max(maxTorque,torque); maxPower=Math.max(maxPower,torque*rpm*2*Math.PI/60000);
            }
            for(int j=0;j<=4;j++) { int gy=y+j*h/4; g.fill(x,gy,x+w,gy+1,0xFF25323F); }
            for(int i=1;i<GarageMenu.SAMPLES;i++) {
                double t0=menu.data.get(15)+(menu.data.get(9)-menu.data.get(15))*(i-1)/(double)(GarageMenu.SAMPLES-1), t1=menu.data.get(15)+(menu.data.get(9)-menu.data.get(15))*i/(double)(GarageMenu.SAMPLES-1);
                double a=menu.data.get(15+i)/10d,b=menu.data.get(16+i)/10d;
                segment(g,x+(i-1)*w/(GarageMenu.SAMPLES-1),y+h-(int)(a/maxTorque*h*.92),x+i*w/(GarageMenu.SAMPLES-1),y+h-(int)(b/maxTorque*h*.92),0xFF4DD2BD);
                segment(g,x+(i-1)*w/(GarageMenu.SAMPLES-1),y+h-(int)(a*t0*2*Math.PI/60000/maxPower*h*.92),x+i*w/(GarageMenu.SAMPLES-1),y+h-(int)(b*t1*2*Math.PI/60000/maxPower*h*.92),0xFFEDAB62);
            }
            g.drawString(font,value("Peak %.1f Nm",maxTorque),leftPos+12,topPos+60,0xFF4DD2BD,false);
            g.drawString(font,value("Peak %.1f kW",maxPower),leftPos+192,topPos+60,0xFFEDAB62,false);
            g.drawString(font,menu.data.get(15)+" rpm",x,y+h+5,0xFF92A9BD,false);
            g.drawString(font,menu.data.get(9)+" rpm",x+w-52,y+h+5,0xFF92A9BD,false);
            g.drawString(font,"Calculated WOT sweep, not a measured roller pull.",leftPos+12,topPos+217,0xFF92A9BD,false);
            g.drawString(font,"Torque and power use separate vertical scales.",leftPos+12,topPos+230,0xFF92A9BD,false);
        } else {
            g.drawString(font,"Stop engine. Remove children before parent parts.",leftPos+12,topPos+59,0xFF92A9BD,false);
            for(int i=page*5;i<Math.min(page*5+5,menu.sessionPaths.size());i++) {
                String path=menu.sessionPaths.get(i);
                boolean installed=menu.car!=null && menu.car.partsDisplay().contains(path+"=");
                g.drawString(font,font.plainSubstrByWidth(path,250),leftPos+12,topPos+78+(i-page*5)*26,installed?0xFFE8F0F6:0xFF65798B,false);
            }
            g.drawString(font,"Install by right-clicking the car with a part in hand.",leftPos+12,topPos+203,0xFF92A9BD,false);
        }
    }
    private void segment(GuiGraphics g,int x0,int y0,int x1,int y1,int color) {
        int steps=Math.max(Math.abs(x1-x0),Math.abs(y1-y0));
        for(int i=0;i<=steps;i++) { double t=steps==0?0:i/(double)steps; int x=(int)Math.round(x0+(x1-x0)*t),y=(int)Math.round(y0+(y1-y0)*t); g.fill(x,y,x+1,y+1,color); }
    }
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY) {}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick) { super.render(g,mouseX,mouseY,partialTick); renderTooltip(g,mouseX,mouseY); }
}
