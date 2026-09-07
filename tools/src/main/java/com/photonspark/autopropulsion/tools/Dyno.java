package com.photonspark.autopropulsion.tools;

import com.photonspark.autopropulsion.sim.*;
import java.util.Locale;

/** CSV engine dyno and deterministic straight-line acceleration reference. */
public final class Dyno {
    public static void main(String[] args) {
        EngineSpec spec = EngineSpec.reference();
        boolean drive = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--engine" -> {
                    if (++i >= args.length) throw new IllegalArgumentException("--engine requires an id");
                    spec = switch (args[i]) { case "ref_i4_2.0" -> EngineSpec.reference(); case "ref_rotary_2" -> EngineSpec.rotary(); default -> throw new IllegalArgumentException("unknown engine " + args[i]); };
                }
                case "--race-cam" -> spec = spec.withRaceCam();
                case "--boost" -> {
                    if (++i >= args.length) throw new IllegalArgumentException("--boost requires bar gauge");
                    spec = spec.withBoost(Double.parseDouble(args[i]), spec.structuralTorqueNm());
                }
                case "--drive" -> drive = true;
                default -> throw new IllegalArgumentException("unknown argument " + args[i]);
            }
        }
        if (drive) {
            VehicleModel model = new VehicleModel(spec); model.setRunning(true);
            System.out.println("seconds,speed_kmh,rpm,gear,fuel_l,coolant_k,health");
            boolean reached = false;
            for (int tick = 0; tick <= 1200; tick++) {
                var s = model.snapshot();
                int gear = s.speed() < 13 ? 1 : s.speed() < 23 ? 2 : s.speed() < 32 ? 3 : s.speed() < 42 ? 4 : 5;
                if (tick % 10 == 0) System.out.printf(Locale.ROOT, "%.2f,%.4f,%.2f,%d,%.6f,%.3f,%.3f%n", tick * .05, s.speed() * 3.6, s.rpm(), s.gear(), s.fuel(), s.coolantK(), s.health());
                if (!reached && s.speed() >= 100 / 3.6) { System.err.printf(Locale.ROOT, "zero_to_100_s=%.2f%n", tick * .05); reached = true; }
                model.tick(new VehicleInput(1, 0, 0, 0, gear, false), 4, 1, 1, 1);
            }
        } else {
            System.out.println("rpm,torque_nm,power_kw,boost_bar");
            for (int rpm = (int)spec.idleRpm(); rpm <= spec.redlineRpm(); rpm += 100) {
                double boost = EngineModel.targetBoost(spec, rpm, 1);
                double torque = EngineModel.torqueNm(spec, rpm, boost);
                System.out.printf(Locale.ROOT, "%d,%.6f,%.6f,%.4f%n", rpm, torque, EngineModel.powerKw(torque, rpm), boost);
            }
        }
    }
}
