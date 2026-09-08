import com.photonspark.sparkmotors.sim.*;
import com.photonspark.sparkmotors.sim.electric.*;
import java.util.*;

/** Independent, deterministic audit. Failures are retained; no production code is patched here. */
public final class IndependentAudit {
    private static int assertions, failed;
    private static final List<String> results = new ArrayList<>();
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    private static void test(String name, Runnable body) {
        try { body.run(); results.add("PASS " + name); }
        catch (Throwable e) { failed++; results.add("FAIL " + name + ": " + e); }
    }
    private static double kinetic(VehicleDynamics.State s, double mass) {
        double e = .5 * mass * s.groundSpeed()*s.groundSpeed()
            + .5 * VehicleDynamics.YAW_INERTIA * mass/VehicleDynamics.MASS
            * s.transmission().yawRate()*s.transmission().yawRate();
        for (var c : s.wheels().corners()) e += .5*WheelDynamics.INERTIA*c.omega()*c.omega();
        return e;
    }
    public static void main(String[] args) {
        for (double power : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            test("invalid_requested_power_"+power+"_must_not_charge", () -> {
                var type=Powertrain.ELECTRIC_400;
                var before=BatteryModel.State.initial(type.battery,.5);
                try {
                    var e=BatteryModel.exchange(type.battery,before,power,.0125,20,0);
                    check(e.state().energyJ() <= before.energyJ()+1e-6,
                        "invalid request added "+(e.state().energyJ()-before.energyJ())+" J; terminalJ="+e.terminalJ());
                    check(e.terminalJ()==0, "non-finite input must be rejected or treated as zero demand");
                } catch (IllegalArgumentException expected) { assertions++; }
            });
        }
        test("4000_seeded_finite_battery_energy_balances", () -> {
            Random r=new Random(20260908);
            for (int n=0;n<4000;n++) {
                var type=Powertrain.values()[1+n%4];var spec=type.battery;
                double health=.12+.88*r.nextDouble();
                var b=new BatteryModel.State(spec.capacityJ()*health*r.nextDouble(),-20+80*r.nextDouble(),health,0);
                double dt=.0025+.0475*r.nextDouble();
                var e=BatteryModel.exchange(spec,b,(r.nextDouble()-.5)*1e6,dt,20,25);
                double delta=b.energyJ()-e.state().energyJ();
                check(Math.abs(delta-e.terminalJ()-e.ohmicJ()-e.fadeJ())<1e-5,"energy residual "+n);
                check(e.ohmicJ()>=0 && e.fadeJ()>=0,"negative loss "+n);
                check(e.state().energyJ()>=0 && e.state().energyJ()<=e.state().capacityJ(spec),"SOC escaped bounds "+n);
                check(Double.isFinite(e.voltageV()) && Double.isFinite(e.currentA()),"non-finite terminal "+n);
            }
        });
        test("all_charger_vehicle_budget_and_voltage_interlocks", () -> {
            for(var type:Powertrain.values()) {
                if(type.battery==null)continue;
                for(var tier:ChargingModel.Tier.values()) {
                    var b=BatteryModel.State.initial(type.battery,.4);
                    var c=ChargingModel.step(type,b,tier,tier.inputV,tier.inputLimitW()*.05,.8,true,false,0,.05,20);
                    check(c.inputJ()<=tier.inputLimitW()*.05+1e-7,"charger exceeded grid allocation");
                    check(Math.abs(c.inputJ()-c.storedJ()-c.lossJ()-c.heaterJ())<1e-6,"charger energy balance");
                    check(c.storedJ()>=-1e-6 && c.lossJ()>=-1e-6,"charger created negative losses");
                    for(double voltage:new double[]{0,tier.inputV*.79,tier.inputV*1.11,Double.NaN}) {
                        var x=ChargingModel.step(type,b,tier,voltage,1e9,.8,true,false,0,.05,20);
                        check(x.inputJ()==0 && x.storedJ()==0,"unsafe voltage accepted "+voltage);
                    }
                    for(int mode=0;mode<3;mode++) {
                        var x=ChargingModel.step(type,b,tier,tier.inputV,1e9,.8,mode!=0,mode==1,mode==2?1:0,.05,20);
                        check(x.inputJ()==0 && x.storedJ()==0,"connection/READY/movement interlock failed");
                    }
                }
            }
        });
        test("cold_preheat_is_grid_paid_and_does_not_add_charge", () -> {
            for(var tier:ChargingModel.Tier.values()) {
                var type=Powertrain.ELECTRIC_400;var b=new BatteryModel.State(type.battery.capacityJ()*.4,-10,1,0);
                var x=ChargingModel.step(type,b,tier,tier.inputV,1e9,.8,true,false,0,.05,20);
                check(x.storedJ()==0 && x.battery().energyJ()==b.energyJ(),"cold charge");
                check(x.heaterJ()>0 && Math.abs(x.inputJ()-x.heaterJ()-x.lossJ())<1e-7,"unpaid preheat");
            }
        });
        test("6000_seeded_passive_chassis_cases_do_not_create_kinetic_energy", () -> {
            Random rnd=new Random(20260908);var type=Powertrain.ELECTRIC_400;
            for(int n=0;n<6000;n++) {
                double v=rnd.nextDouble()*40+.05,lat=(rnd.nextDouble()-.5)*30,yaw=(rnd.nextDouble()-.5)*4;
                var drive=DriveConfig.preset(DriveConfig.Layout.values()[n%3]);
                var m=MechanicalState.legacy(Assembly.stock(),EnginePart.stock(),90,90,100);
                var setup=new VehicleDynamics.Setup(Assembly.stock(),6800,3.7,EngineFamily.I4,EnginePart.stock(),90,1.4,m,drive).withMass(type.massKg);
                var corners=new ArrayList<WheelDynamics.Corner>();
                for(int c=0;c<4;c++)corners.add(new WheelDynamics.Corner((v-yaw*(c%2==0?.83:-.83))/.34,0,0,true,0,0,20));
                var before=new VehicleDynamics.State(v,0,1,0,0,0,EnginePhysics.State.stopped(20,100),new WheelDynamics.State(corners),TransmissionPhysics.State.stopped().motion(lat,yaw,0,0,0));
                var x=ElectricDynamics.step(type,ElectricDynamics.State.initial(type,.5),ElectricDynamics.Mode.AUTO,v,0,before.engine(),before.wheels(),before.transmission(),false,new VehicleDynamics.Input(0,(rnd.nextDouble()-.5)*2,n%2==0,false),setup,new double[]{1,1,1,1},new boolean[]{true,true,true,true},new double[4],false,.0125,20);
                check(kinetic(x.road(),type.massKg)<=kinetic(before,type.massKg)+1,"passive energy gain at sample "+n);
            }
        });
        results.forEach(System.out::println);
        System.out.println("AUDIT_SUMMARY tests="+results.size()+" failed="+failed+" assertions="+assertions);
        if(failed>0)System.exit(1);
    }
}
