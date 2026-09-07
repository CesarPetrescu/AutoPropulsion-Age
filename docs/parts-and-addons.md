# Parts, catalogue and experimental addon surface

## Installable alpha parts

| Slot | Definitions | Current effect |
| --- | --- | --- |
| `engine_block` | `i4_block_cast`, `i4_block_billet` | Structural/RPM ceilings; engine remains the reference I4 |
| `connecting_rods` | `stock_connecting_rods`, `forged_connecting_rods` | 260 versus 650 Nm structural ceiling; RPM ceiling |
| `camshaft` | `stock_camshaft`, `race_camshaft` | Reference versus race VE curve; RPM limits remain weakest-link |
| `turbo` | `naturally_aspirated_intake`, `street_turbo`, `race_turbo` | Hardware boost maximum 0, 0.8 or 1.5 bar; target adjustable while stopped |
| `radiator` | `stock_radiator`, `aluminium_radiator` | Cooling coefficient 110 versus 180 W/K before airflow/thermostat factors |
| `tires` | `street_tires`, `sport_tires`, `offroad_tires` | Grip multiplier 1.0, 1.2 or 0.85; no terrain-specific off-road bonus yet |
| `ecu` | `stock_ecu`, `standalone_ecu` | RPM ceiling, not full fuel/ignition maps |

All other catalogue definitions are reference/proxy entries. They have models and names but are rejected for installation by the current vehicle adapter. Recipes exist for the 16 installable entries plus eight utility/block recipes. Material values and recipe costs are provisional.

## Authoritative files

`assets/catalog.json` is the checked-in authoring catalogue. `scripts/seed-catalog.py` was a bootstrap utility; do not rerun it over manual catalogue work. `scripts/generate-content.py` generates JSON definitions, built-in identifiers, item overrides, recipes and utility models. The Blender pipeline supplies part OBJ/MTL and vehicle geometry.

The current server reload listener reads definitions at:

```text
mod/src/main/resources/data/autopropulsion/autopropulsion/parts/<id>.json
```

The repeated `autopropulsion` segment is intentional in this alpha: first the resource namespace, then the listener's directory. A datapack override must preserve that layout. Model resources belong in a resource pack, not the server datapack.

Example installed-part override:

```json
{
  "id": "street_turbo",
  "slot": "turbo",
  "category": "turbo",
  "family": "piston",
  "functional": true,
  "tier": 1,
  "mass_kg": 1.0,
  "model": "autopropulsion:item/parts/street_turbo",
  "boost_bar": 0.8,
  "model_index": 117
}
```

The server derives the authoritative namespaced identifier from the resource path. Startup/reload requires starter entries and rejects non-finite or out-of-range statistics. Increasing `model_index` alone does not teach a client to render an unknown model.

## Stable indices and limitations

Built-in component stacks hold the definition in `minecraft:custom_data.Part` and their appearance in `minecraft:custom_model_data`. Do not reorder or reuse existing indices. `BuiltinCatalog`, creative entries, tooltips and model selection still have built-in assumptions; this is **not a finished general addon API**. Overrides of existing definitions are the supported experiment; arbitrary new slot types and new engine families need adapter work.

`mass_kg`, many category names and unimplemented-part metadata must not be advertised as live physics contributions. The full generic modifier/codec registry and data-driven chassis system are future work.

## Validation

```bash
python3 scripts/generate-content.py
python3 scripts/validate-content.py --assets
bash scripts/test-sim.sh
./gradlew build
```

The current validator checks JSON parsing, identifier/index continuity, generated-definition agreement, translation presence, 24 distinct recipe signatures and required asset/model files. It does not prove all OBJ normals/materials look correct, verify recipes through every crafting interaction, or certify addon compatibility. Client rendering and gameplay evidence are separate.
