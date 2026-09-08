package com.photonspark.sparkmotors.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Fits the workshop as a whole, including input and tooltips, without changing Minecraft's GUI setting. */
public abstract class WorkshopScreen extends Screen {
    private float fit=1;
    private List<FormattedCharSequence> tooltip;
    protected WorkshopScreen(Component title){super(title);}
    public float contentScale(){return fit;}
    @Override protected final void init(){
        int actualWidth=minecraft.getWindow().getGuiScaledWidth(),actualHeight=minecraft.getWindow().getGuiScaledHeight();
        fit=Math.min(1,Math.min(actualWidth/760f,actualHeight/460f));
        width=Math.round(actualWidth/fit);height=Math.round(actualHeight/fit);
        initWorkshop();
    }
    protected abstract void initWorkshop();
    protected void clip(GuiGraphics g,int left,int top,int right,int bottom){g.enableScissor(Math.round(left*fit),Math.round(top*fit),Math.round(right*fit),Math.round(bottom*fit));}
    protected void renderWorkshop(GuiGraphics g,int mx,int my,float partial){super.render(g,mx,my,partial);}
    @Override public final void render(GuiGraphics g,int mx,int my,float partial){
        tooltip=null;g.pose().pushPose();g.pose().scale(fit,fit,1);
        int sx=(int)(mx/fit),sy=(int)(my/fit);
        renderWorkshop(g,sx,sy,partial);
        if(tooltip!=null)g.renderTooltip(font,tooltip,(sw,sh,mx2,my2,tw,th)->new org.joml.Vector2i(Math.max(4,Math.min(width-tw-5,mx2+12)),Math.max(4,Math.min(height-th-5,my2-12))),sx,sy);
        g.pose().popPose();
    }
    @Override public void setTooltipForNextRenderPass(List<FormattedCharSequence> lines,ClientTooltipPositioner positioner,boolean override){
        if(tooltip==null||override)tooltip=lines;
    }
    @Override public final boolean mouseClicked(double x,double y,int b){return clickWorkshop(x/fit,y/fit,b);}
    protected boolean clickWorkshop(double x,double y,int b){return super.mouseClicked(x,y,b);}
    @Override public final boolean mouseReleased(double x,double y,int b){return super.mouseReleased(x/fit,y/fit,b);}
    @Override public final void mouseMoved(double x,double y){super.mouseMoved(x/fit,y/fit);}
    @Override public final boolean mouseDragged(double x,double y,int b,double dx,double dy){return dragWorkshop(x/fit,y/fit,b,dx/fit,dy/fit);}
    protected boolean dragWorkshop(double x,double y,int b,double dx,double dy){return super.mouseDragged(x,y,b,dx,dy);}
    @Override public final boolean mouseScrolled(double x,double y,double dx,double dy){return scrollWorkshop(x/fit,y/fit,dx,dy);}
    protected boolean scrollWorkshop(double x,double y,double dx,double dy){return super.mouseScrolled(x,y,dx,dy);}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
}
