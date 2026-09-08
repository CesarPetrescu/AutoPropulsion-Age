package com.photonspark.sparkmotors.client;

import com.photonspark.sparkmotors.charging.ChargerBlockEntity;
import com.photonspark.sparkmotors.net.CarPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Locale;

/** Live block telemetry, kept inside a fitted panel at every GUI scale. */
public final class ChargerScreen extends WorkshopScreen {
    private final ChargerBlockEntity charger;
    private int x,y,w;
    private Button unplug;
    private static final int INK=0xFFE7F0F4,MUTED=0xFF9FB1BE,ACCENT=0xFF42D2C6;
    public ChargerScreen(ChargerBlockEntity charger){super(Component.literal("Vehicle charger"));this.charger=charger;}
    @Override public boolean isPauseScreen(){return false;}
    @Override protected void initWorkshop(){
        w=Math.min(640,width-32);x=(width-w)/2;y=(height-400)/2;
        addRenderableWidget(Button.builder(Component.literal("Close"),b->onClose()).bounds(x+w-86,y+14,70,22).build());
        unplug=addRenderableWidget(Button.builder(Component.literal("Unplug / recover cable"),b->PacketDistributor.sendToServer(new CarPackets.Charger(charger.getBlockPos(),1))).bounds(x+20,y+310,w-40,24).build());
        unplug.active=charger.connected()||charger.hasCable();
    }
    @Override public void tick(){
        if(charger.isRemoved()||minecraft.player==null||minecraft.player.distanceToSqr(Vec3.atCenterOf(charger.getBlockPos()))>64){onClose();return;}
        unplug.active=charger.connected()||charger.hasCable();
    }
    private static String format(String pattern,Object... args){return String.format(Locale.ROOT,pattern,args);}
    @Override protected void renderWorkshop(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0x99101922);g.fill(x,y,x+w,y+400,0xFA101B25);g.fill(x,y,x+w,y+3,ACCENT);
        g.drawString(font,"VEHICLE CHARGER",x+20,y+20,ACCENT,false);
        g.drawString(font,charger.tier().title,x+20,y+48,INK,false);
        g.fill(x+20,y+70,x+w-20,y+119,0xFF18333C);
        g.drawString(font,charger.status().replace('_',' '),x+32,y+81,ACCENT,false);
        g.drawString(font,charger.vehicleName(),x+32,y+101,INK,false);
        String[] labels={"Supply voltage / rated","Input power / maximum","Supplied energy (lifetime)","Conversion + battery losses","Battery / charge target","Battery temperature","Cable"};
        String[] values={format("%.0f V / %.0f V",charger.inputVoltage(),charger.tier().inputV),format("%.2f / %.1f kW",charger.inputKw(),charger.tier().inputLimitW()/1000),format("%.3f kWh",charger.deliveredJ()/3_600_000),format("%.3f kWh",charger.lossJ()/3_600_000),charger.soc()<0?"No vehicle":format("%.1f%% / %d%%",charger.soc()*100,charger.target()),charger.connected()?format("%.1f C",charger.packC()):"--",charger.connected()?(charger.hasCable()?"Installed":"Legacy pairing (item in inventory)"):charger.hasCable()?"Stored here - ready to recover":"Not installed"};
        for(int i=0;i<labels.length;i++){
            int by=y+134+i*22;g.drawString(font,labels[i],x+20,by,MUTED,false);
            g.drawString(font,values[i],x+w-20-font.width(values[i]),by,INK,false);
        }
        if(charger.soc()>=0){int left=x+20,right=x+w-20;g.fill(left,y+293,right,y+299,0xFF29404D);g.fill(left,y+293,left+(int)((right-left)*Math.clamp(charger.soc(),0,1)),y+299,ACCENT);}
        String help=charger.creativePower()?"CREATIVE TEST SUPPLY: unlimited power. ":"";
        help+=charger.connected()?"Unplug here, or sneak-click the car/charger with an empty hand. A full inventory drops the returned cable at your feet.":charger.hasCable()?"The connection ended, but your cable is safe here. Recover it before pairing again.":"Hold a charging cable: click this charger, then your parked EV or plug-in hybrid within 6 blocks. READY must be off.";
        g.drawWordWrap(font,Component.literal(help),x+20,y+349,w-40,MUTED);
        super.renderWorkshop(g,mx,my,partial);
    }
}
