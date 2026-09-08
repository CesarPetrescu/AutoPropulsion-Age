package com.photonspark.sparkmotors.gametest;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.charging.*;
import com.photonspark.sparkmotors.sim.electric.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("sparkmotors") @PrefixGameTestTemplate(false)
public final class ElnChargerGameTests {
    @GameTest(template="test_track",timeoutTicks=240) public void realElnCablePowersVehicleAndOvervoltageTrips(GameTestHelper h){
        var car=AutoPropulsionAge.CAR.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(8,2.05,8));car.moveTo(at.x,at.y,at.z,0,0);car.initializePowertrain(Powertrain.ELECTRIC_400,.25);h.getLevel().addFreshEntity(car);
        var player=h.makeMockServerPlayerInLevel();player.setGameMode(GameType.CREATIVE);player.moveTo(car.position());car.setOwner(player.getUUID());
        var pos=h.absolutePos(new BlockPos(11,2,8));h.getLevel().setBlock(pos,Electrification.CHARGERS.get(ChargingModel.Tier.RAPID).get().defaultBlockState(),3);
        var charger=(ChargerBlockEntity)h.getLevel().getBlockEntity(pos);h.assertTrue(charger.connect(player,car),"native circuit vehicle pair");
        var fixture=ElnCircuitFixture.power(h.getLevel(),pos,player,480);double before=car.tractionBattery().energyJ();double[] high={0};
        h.runAfterDelay(80,()->{
            h.assertTrue(charger.inputKw()>1&&car.tractionBattery().energyJ()>before,"real ELN cable supplies joules to production charger: "+charger.status()+" "+charger.inputKw()+" kW");
            h.assertTrue(fixture.sourceWatts()>1000,"ELN solver sees source current/load");
            h.assertTrue(car.tractionBattery().energyJ()-before<charger.deliveredJ(),"charger and battery losses prevent energy creation");
            fixture.voltage(560);
        });
        h.runAfterDelay(105,()->{high[0]=car.tractionBattery().energyJ();h.assertTrue(charger.status().equals("OVERVOLTAGE")||charger.status().equals("NO_POWER"),"overvoltage isolates load: "+charger.status());});
        h.runAfterDelay(130,()->{h.assertTrue(car.tractionBattery().energyJ()<=high[0]+1e-6,"no overvoltage charging");fixture.voltage(480);});
        h.runAfterDelay(175,()->{h.assertTrue(charger.inputKw()>1,"charging recovers on rated voltage");charger.disconnect();});
        h.runAfterDelay(200,()->{
            h.assertTrue(!car.plugged()&&fixture.sourceWatts()<1,"unplug opens native load, no continuous phantom consumption");
            h.getLevel().removeBlock(pos,false);fixture.close();System.out.println("ELN_CHARGER_CIRCUIT_PASS actual cable, native MNA load, joule accounting, overvoltage, recovery, unload");h.succeed();
        });
    }
}
