package com.photonspark.sparkmotors.gametest;

import com.photonspark.sparkmotors.entity.CarEntity;
import net.minecraft.client.Minecraft;

/** Acknowledged native service actions, not a burst of client ticks during a slow frame. */
final class CoolantRefill {
    private record Sample(int tick,float litres) {}
    private static volatile Sample sample;
    private static int waited,lastAction=-1,actions;
    private static boolean pending;
    private static double expected;
    static boolean tick(Minecraft mc,CarEntity car){
        var current=sample;int id=car.getId();var playerId=mc.player.getUUID();
        mc.getSingleplayerServer().execute(()->{
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
            var serverCar=(CarEntity)player.serverLevel().getEntity(id);
            sample=new Sample(serverCar.tickCount,serverCar.coolant());
        });
        if(++waited>200)throw new IllegalStateException("Coolant refill acknowledgment timed out: actions="+actions+" expected="+expected+" client="+car.coolant()+" server="+current);
        if(current==null)return false;
        if(lastAction<0){lastAction=current.tick();return false;}
        if(pending){
            if(car.coolant()<expected-.0001||current.litres()<expected-.0001)return false;
            pending=false;lastAction=current.tick();
            System.out.println("COOLANT_REFILL_ACK action="+actions+" litres="+car.coolant()+" serverTick="+current.tick());
        }
        // Four actual server ticks between packets respects the three-tick production limit.
        // No blind retries: an unacknowledged operation fails instead of spamming more bottles.
        if(current.tick()-lastAction<4)return false;
        if(car.coolant()>=7.999&&current.litres()>=7.999){
            if(actions==0)throw new IllegalStateException("Refill fixture started full without service");
            System.out.println("COOLANT_REFILL_CLIENT_PASS actions="+actions+" litres="+car.coolant());return true;
        }
        expected=Math.min(8,car.coolant()+1);pending=true;actions++;lastAction=current.tick();
        ClientSmoke.press(mc,"Fill coolant",0);return false;
    }
}
