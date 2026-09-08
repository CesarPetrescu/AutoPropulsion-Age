"""Fill missing service-unit art inside existing V6/flat-four cores, using the original mounts."""
def internal_meta(name,key,family):
    bindings[name]=key
    return meta(name,cat=20,group=0,family=1<<family,slot=4)
for family,prefix,banks in [(1,'v6',3),(2,'flat4',2)]:
    for bank in [-1,1]:
        for n in range(banks):
            number=n+1+(banks if bank==1 else 0)
            center=Vector(lookup[f'sparkmotors:{prefix}_cylinder_{bank}_{n}']['mount_world_m'])
            axis=Vector((bank*.5,0,.866)) if family==1 else Vector((bank,0,0))
            u=Vector((0,1,0));v=axis.cross(u).normalized()
            # Closed piston crown and actual separate ring loops; each removable service slot has visible geometry.
            key=f'cylinder.{number}.piston';name=f'service_{prefix}_piston_{number}'
            pipe(name,[center-axis*.035,center+axis*.01],.038,metal,internal_meta(name,key,family),32)
            for j in [-.012,0,.012]:
                key=f'cylinder.{number}.rings';name=f'service_{prefix}_rings_{number}'
                points=[center+axis*j+.040*(u*math.cos(i*math.tau/32)+v*math.sin(i*math.tau/32)) for i in range(33)]
                pipe(name,points,.002,black,internal_meta(name,key,family),6)
            key=f'cylinder.{number}.bearing';name=f'service_{prefix}_bearing_{number}'
            pipe(name,[center-axis*.10-u*.025,center-axis*.10+u*.025],.025,metal,internal_meta(name,key,family),24)
            key=f'cylinder.{number}.valves';name=f'service_{prefix}_valves_{number}'
            for offset in [-.021,.021]:pipe(name,[center+axis*.018+u*offset,center+axis*.072+u*offset],.007,metal,internal_meta(name,key,family),12)
    name=f'service_{prefix}_crank';key='internal.crank'
    pipe(name,[(0,-1.64,.62),(0,-1.08,.62)],.023,metal,internal_meta(name,key,family),32)
