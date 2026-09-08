package com.photonspark.sparkmotors.sim;

import java.nio.file.*;
import java.util.*;

/** Reproducible simulation-only workload. This deliberately excludes Minecraft world/render/network cost. */
public final class MechanicsBenchmark {
    private static final int CARS=20, TICKS=1000;
    private static volatile double checksum;
    public static void main(String[] args) throws Exception {
        run(400);
        long[] samples=run(TICKS);Arrays.sort(samples);
        double mean=Arrays.stream(samples).average().orElseThrow()/1e6,p95=samples[(int)(TICKS*.95)]/1e6;
        String json=String.format(Locale.ROOT,"""
            {
              "scope": "Version 2 individual combustion components, seven families; excludes Minecraft entity, rendering and networking costs",
              "java": "%s",
              "cars": %d, "ticks": %d, "substepsPerTick": 4,
              "meanBatchMilliseconds": %.4f, "p95BatchMilliseconds": %.4f,
              "meanMicrosecondsPerCarTick": %.4f,
              "checksum": %.4f
            }
            """,System.getProperty("java.version"),CARS,TICKS,mean,p95,mean*1000/CARS,checksum);
        Path out=Path.of(args.length>0?args[0]:"build/reports/mechanics-performance.json");Files.createDirectories(out.toAbsolutePath().getParent());Files.writeString(out,json);System.out.print(json);
    }
    private static long[] run(int ticks){
        var mechanics=new MechanicalState[CARS];var states=new VehicleDynamics.State[CARS];
        for(int c=0;c<CARS;c++){mechanics[c]=PowertrainTopology.fresh(com.photonspark.sparkmotors.sim.electric.Powertrain.COMBUSTION,DriveConfig.preset(DriveConfig.Layout.RWD),EngineFamily.values()[c%7],Assembly.stock(),EnginePart.boosted(1));states[c]=new VehicleDynamics.State(0,0,1,40,0,0);}
        var times=new long[ticks];double sum=0;
        for(int t=0;t<ticks;t++){
            long start=System.nanoTime();
            for(int c=0;c<CARS;c++){
                var m=mechanics[c];var s=states[c];
                var setup=new VehicleDynamics.Setup(Assembly.stock(),6800,3.7,EngineFamily.values()[c%7],EnginePart.boosted(1),m.coolantTemperature(),1.0,m);
                var input=new VehicleDynamics.Input(t%160<100?.8:.1,Math.sin(t*.02+c)*.3,t%160>130,false,false,false);
                for(int step=0;step<4;step++)s=VehicleDynamics.step(s,true,input,setup,.95,true,.0125);
                m=CircuitPhysics.step(m,s.rpm(),input.throttle(),s.engine().boost(),s.engine().mode()==EnginePhysics.Mode.RUNNING,s.engine().mode()==EnginePhysics.Mode.CRANKING,true,s.speed(),input.brake(),.05);
                m=InternalMechanics.step(m,setup.family(),s.rpm(),input.throttle(),s.engine().mode()==EnginePhysics.Mode.RUNNING,.05);
                mechanics[c]=WheelDynamics.wear(m,s.wheels(),s.speed(),.05);states[c]=s;sum+=s.speed()+m.coolantTemperature();
            }
            times[t]=System.nanoTime()-start;
        }
        checksum=sum;return times;
    }
}
