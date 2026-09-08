"""Executed by the Blender MCP runtime exporter; native meshes for previously grouped service targets."""
for corner,(x,y) in {'fl':(-.83,-1.35),'fr':(.83,-1.35),'rl':(-.83,1.30),'rr':(.83,1.30)}.items():
    pipe('brake_hose_'+corner,[(x*.7,y,.59),(x*.85,y+.08,.5),(x,y+.08,.36)],.007,black,meta('brake_hose_'+corner,cat=15,group=3))
pipe('oil_supply_line',[(.12,-1.64,.53),(.31,-1.68,.55),(.36,-1.45,.74)],.009,black,meta('oil_supply_line',cat=28))
box('coolant_sender',(-.23,-1.68,.72),(.028,.028,.045),gold,meta('coolant_sender',cat=29))
box('oil_sender',(.13,-1.62,.53),(.028,.025,.045),gold,meta('oil_sender',cat=28))
box('main_fuse',(.60,-1.04,.85),(.07,.065,.028),red,meta('main_fuse',cat=29))
pipe('essential_wiring',[(.58,-1.05,.84),(.60,-1.25,.73),(.58,-1.58,.58),(.22,-1.62,.57)],.007,black,meta('essential_wiring',cat=29))
pipe('accessory_belt',[(0,-1.805,.53),(-.22,-1.805,.63),(-.18,-1.805,.50),(0,-1.805,.53)],.006,black,meta('accessory_belt',cat=29))

# Supplement the two existing large dials with four small, separately animated needles.
for name,x in [('fuel',-.22),('coolant',-.13),('oil',-.04),('voltage',.05)]:
    center=(x,-.369,1.035)
    pipe('gauge_face_'+name,[(x,-.39,1.035),(x,-.374,1.035)],.035,black,meta('gauge_face_'+name,cat=8,group=5),sides=32)
    ring('gauge_rim_'+name,center,.037,.033,.006,(0,1,0),metal,meta('gauge_face_'+name,cat=8,group=5),32)
    pipe('gauge_needle_'+name,[center,(x+.026,-.369,1.035)],.0018,red,meta('gauge_needle_'+name,cat=8,group=5,pivot=center),sides=6)

# Reuse the authored floor jack under the raised chassis without moving the master source object.
jack=bpy.data.objects['SM_floor_jack'];target=Vector((0,0,-.5))
for ob in jack.children_recursive:
    if ob.type!='MESH':continue
    deps=bpy.context.evaluated_depsgraph_get();data=bpy.data.meshes.new_from_object(ob.evaluated_get(deps),depsgraph=deps)
    data.transform(ob.matrix_world);data.transform(Matrix.Translation(target-jack.matrix_world.translation))
    cp=bpy.data.objects.new('APA_runtime_service_jack',data);scene.collection.objects.link(cp);temporary.append(cp)
    collect(cp,meta('service_jack',cat=37,group=-1))
pipe('jack_extension',[(0,0,-.36),(0,0,.13)],.04,metal,meta('service_jack',cat=37,group=-1))
