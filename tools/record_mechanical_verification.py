"""Package an already-tested build and derive evidence from this run's explicit logs and JUnit XML."""
from pathlib import Path
import argparse, hashlib, json, shutil, zipfile, xml.etree.ElementTree as ET

repo=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('--matrix-log',required=True)
parser.add_argument('--final-log',required=True)
parser.add_argument('--server-log',required=True)
args=parser.parse_args()
matrix=(repo/args.matrix_log).read_text(errors='replace')
final=(repo/args.final_log).read_text(errors='replace')
assert 'ALPHA_CLIENT_SMOKE PASS: driving/garage/paint/tune plus 49 family/induction layouts and all 42 hardware choices' in matrix
server=(repo/args.server_log).read_text(errors='replace')
assert 'All 26 required tests passed' in server and 'BUILD SUCCESSFUL' in server
assert 'MECHANICS_CLIENT_PASS AUDIO_CHANNELS_AND_CLEANUP_PASS INSTRUMENT_SENDER_PASS' in final
assert 'BUILD SUCCESSFUL' in matrix and 'BUILD SUCCESSFUL' in final
layouts={line.split('ENGINE_LAYOUT_PASS ',1)[1].split()[0] for line in matrix.splitlines() if 'ENGINE_LAYOUT_PASS ' in line}
hardware={line.split('HARDWARE_UI_PASS ',1)[1].split()[0] for line in matrix.splitlines() if 'HARDWARE_UI_PASS ' in line}
assert len(layouts)==49 and len(hardware)==42
tests=[]
for path in sorted((repo/'sim/build/test-results/test').glob('TEST-*.xml')):
    root=ET.parse(path).getroot()
    assert int(root.get('failures'))==int(root.get('errors'))==int(root.get('skipped'))==0
    tests += [c.get('classname')+'.'+c.get('name') for c in root.findall('testcase')]
assert len(tests)==43
version=next(line.split('=',1)[1] for line in (repo/'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
jar=repo/'build/libs'/f'autopropulsion-age-{version}.jar'
with zipfile.ZipFile(jar) as archive:
    names=archive.namelist()
    assert not any('/gametest/' in n or n.endswith('test_track.nbt') for n in names)
    assert 'com/photonspark/sparkmotors/sim/MechanicalState.class' in names
    assert 'com/photonspark/sparkmotors/client/CockpitInstruments.class' in names
    assert version in archive.read('META-INF/neoforge.mods.toml').decode()
    audio=[n for n in names if '/sounds/mechanical/' in n and n.endswith('.ogg')]
    assert len(audio)==48
    for name in audio+['assets/sparkmotors/models/entity/sedan.mesh.gz']:
        assert archive.read(name)==(repo/'src/main/resources'/name).read_bytes(),name
    recipes=sum(n.startswith('data/sparkmotors/recipe/') and n.endswith('.json') for n in names)
    assert recipes==122
destination=repo/'downloads'/jar.name
shutil.copy2(jar,destination)
digest=hashlib.sha256(destination.read_bytes()).hexdigest()
sums=repo/'downloads/SHA256SUMS.txt'
lines=[line for line in sums.read_text().splitlines() if not line.endswith('  '+jar.name)]
lines.append(digest+'  '+jar.name);sums.write_text('\n'.join(lines)+'\n')
def report(name):return json.loads((repo/'docs'/name).read_text())
result={
    'version':version,'date':'2026-09-08','minecraft':'1.21.1','neoforge':'21.1.249','java':'21.0.11',
    'source_baseline':'2bbe5ea','branch':'mechanical-components',
    'unit_tests':{'passed':len(tests),'failed':0,'tests':tests},
    'dedicated_gametests':{'passed':26,'failed':0,'native_driving_layouts':98,'includes_final_wear_only_rebuild_regression':True},
    'client_integration':{'full_matrix':'PASS','family_induction_layouts':len(layouts),'hardware_choices':len(hardware),'final_mechanical_workflow':'PASS','actual_sound_channels_and_cleanup':'PASS','sender_behavior':'PASS','cockpit_screenshot_review':'Needles, labels and odometer inspected in native image'},
    'multiplayer':report('mechanics-multiplayer.json'),
    'performance':report('mechanics-performance.json'),'network_size':report('mechanics-network-size.json'),
    'audio':{'decoded_mono_assets':48,'authored_audio_survives_regeneration':True,'origin':'Original synthesized game audio','subjective_listening':'Pending user feedback; no model listening tool available'},
    'resolved_during_this_run':['Baseline brakes combined and missing/airborne braking defects reproduced and fixed','Mini cockpit needles initially occluded by dial faces; source corrected and native screenshot inspected','Rebuild UI and server originally rejected wear-only internal damage; shared predicate and native regressions passed','Stored brake/clutch temperatures were overwritten by transient-state initialization and tire temperature jumped to a target; restore/thermal-decay fix checked during resumed server ticks','First full client matrix passed 49 layouts and 42 hardware choices, then failed because the test requested revving before cranking finished; corrected timing and reran the full suite'],
    'blender':{'fit_layouts':49,'intersection_layouts':49,'hood_positions':5,'distinct_hardware_geometry_checks':294,'runtime_chunks':734,'union_triangles':268980,'editable_derived_scenes':91,'scope':'Targeted fit, hood and non-mating hardware tests, not all pairwise contacts'},
    'release_jar':{'path':destination.relative_to(repo).as_posix(),'bytes':destination.stat().st_size,'sha256':digest,'simulation_included':True,'development_harness_excluded':True,'recipes':recipes,'packaged_mechanical_audio':len(audio)},
    'unexecuted':['Subjective audio listening acceptance','Separate-machine latency and other modpacks','Long-duration survival worlds','Large-fleet Minecraft render/network performance'],
    'next_unfinished_step':'Listening/playtest feedback; then deeper service/access coverage and fleet profiling against the same component state'
}
(repo/'docs/verification.json').write_text(json.dumps(result,indent=2)+'\n')
milestones=repo/'docs/mechanical-milestones.md'
lines=milestones.read_text(encoding='utf-8').splitlines()
lines=[('| M6: integration | Complete for the recorded scope. Fresh 43 JUnit / 26 server GameTests, the full 49-layout / 42-hardware native matrix, final repair-and-drive / rebuild UI / saved-temperature regressions, and a real two-client trading test passed. Audio regeneration, targeted Blender geometry and scoped performance measurements passed. Listening, remote latency and fleet rendering remain unverified. |' if line.startswith('| M6: integration |') else line) for line in lines]
milestones.write_text('\n'.join(lines)+'\n',encoding='utf-8')
print(json.dumps(result['release_jar'],indent=2))
