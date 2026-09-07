# Security and multiplayer boundaries

The server owns simulation, items, part compatibility, tuning constraints, ownership and persistence. Input packets may contain controls only. Reject non-finite/out-of-range data, non-drivers, unauthorized owners and repeated input within one server tick. Inventory operations must validate a candidate before consuming or returning items; stale GUI indices must not target a different slot.

The current limits include 128 installed parts, eight nested levels, bounded path lengths, 4,096 loaded definitions, per-definition slot/modifier budgets and bounded menu actions. These are safeguards, not a formal security audit or proof against every malicious datapack. Datapacks and resource packs are administrator-installed content; the public addon schema is still alpha.

There is no external telemetry, account login, arbitrary script execution, livery upload or downloadable code in the mod. Development asset generation invokes repository-owned Python scripts during builds; only build trusted source.

The automated Minecraft test server uses offline authentication **only on 127.0.0.1** in a disposable world. Never expose that configuration publicly or enable `apa.serverSmoke` on a normal world. It intentionally creates a test track at login. Normal servers should retain authenticated online mode and operator-only administrative commands.

For an ownership bypass, item duplication or crash exploit, provide the smallest reproduction and redacted logs to the repository owner privately rather than posting credentials or a working attack against a public server. This repository currently has no separately configured private disclosure address.
