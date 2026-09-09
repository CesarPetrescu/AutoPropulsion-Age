"""Connected coachwork built from shared aperture coordinates, in metres.

Each sheet has an outer face, an inner face and boundary returns. Operable panels
are separate solids with their own pivots; seams have a stationary recessed seal.
No backface-culling override can substitute for this geometry.
"""
from __future__ import annotations
from collections import Counter
from math import sqrt, sin, cos, asin, pi


def sheet(api, name, points, faces, offset, color, **kw):
    """Extrude an orientable surface along a fixed vector and cap every free edge."""
    n = len(points)
    vertices = list(points) + [tuple(p[i] + offset[i] for i in range(3)) for p in points]
    edges = Counter(tuple(sorted((a, b))) for face in faces for a, b in zip(face, face[1:] + face[:1]))
    result = list(faces) + [tuple(i+n for i in reversed(face)) for face in faces]
    for face in faces:
        for a, b in zip(face, face[1:] + face[:1]):
            if edges[tuple(sorted((a, b)))] == 1:
                result.append((a, a+n, b+n, b))
    obj = api['make'](name, vertices, result, color, **kw)
    obj['apa_closed_shell'] = True
    return obj


def inset(poly, margin):
    """Inset a convex counter-clockwise polygon in its (z,y) plane."""
    area = sum(a[0]*b[1]-b[0]*a[1] for a,b in zip(poly,poly[1:]+poly[:1]))
    if area < 0:
        return list(reversed(inset(list(reversed(poly)),margin)))
    shifted=[]
    for a,b in zip(poly,poly[1:]+poly[:1]):
        dx,dy=b[0]-a[0],b[1]-a[1];length=sqrt(dx*dx+dy*dy)
        if length < 1e-8: raise ValueError('Degenerate aperture edge')
        shifted.append(((a[0]-margin*dy/length,a[1]+margin*dx/length),(dx,dy)))
    result=[]
    for i,(p,d) in enumerate(shifted):
        q,e=shifted[i-1];det=d[0]*e[1]-d[1]*e[0]
        if abs(det)<1e-8: raise ValueError('Collinear aperture vertices')
        t=((q[0]-p[0])*e[1]-(q[1]-p[1])*e[0])/det
        result.append((p[0]+t*d[0],p[1]+t*d[1]))
    return result


def author_shell(s, api):
    make,box,tube,copy_fitted=(api[k] for k in ('make','box','tube','copy_fitted'))
    PAINT,BLACK,PLASTIC,ALLOY,GLASS,CHROME,WHITE,RED,AMBER=(api[k] for k in ('PAINT','BLACK','PLASTIC','ALLOY','GLASS','CHROME','WHITE','RED','AMBER'))
    name=s['id'];w=s['halfWidth'];nose=s['nose'];tail=s['tail'];belt=s['belt'];roof=s['roof']
    cf=s['cabinFront'];cr=s['cabinRear'];rf=s['roofFront'];rr=s['roofRear']
    rear_z=-tail+.04;front_z=nose-.04
    floor=max(.435,.43+s['cabinY'])
    taper=.055 if name=='van' else .115
    wr=w-taper
    seam=.003                 # 3 mm operable panel seam, backed by rubber/return geometry.
    hood_y=max(belt+.015,1.057)
    rear_floor=max(floor,.56)

    def top(z):
        if z>rf:return belt+(roof-belt)*(cf-z)/(cf-rf)
        if z<rr:return belt+(roof-belt)*(z-cr)/(rr-cr)
        return roof
    def width(y):return w-taper*(y-belt)/(roof-belt)
    def bottom(z):
        return max([.435]+[.34+sqrt(max(0,.444**2-(z-a)**2)) for a in (1.35,-1.30) if abs(z-a)<.444])
    def side_points(poly, side):return [(side*width(y),y,z) for z,y in poly]
    def side_plate(label,side,poly,thickness=.030,color=PAINT,**kw):
        return sheet(api,label,side_points(poly,side),[tuple(range(len(poly)))],(-side*thickness,0,0),color,**kw)
    def upper_poly(a,b):
        poly=[(a,belt),(b,belt)]
        for z in sorted(set([a,b]+[k for k in (rf,rr) if a<k<b]),reverse=True):
            p=(z,top(z))
            if abs(p[1]-belt)>1e-8:poly.append(p)
        return poly
    def lower_poly(a,b,inset_height=0):
        n=max(2,int((b-a)/.02))
        zs=[a+(b-a)*i/n for i in range(n+1)]
        return [(z,bottom(z)+inset_height) for z in zs]+[(b,belt),(a,belt)]
    def skin(label,side,a,b,**kw):
        pts=[(side*(w-.012),y,z) if y<belt else (side*w,y,z) for z,y in lower_poly(a,b)]
        sheet(api,label,pts,[tuple(range(len(pts)))],(-side*.038,0,0),PAINT,**kw)
    def edge_tube(label,side,poly,radius=.014,color=BLACK,**kw):
        pts=side_points(poly,side);tube(label,pts+pts[:1],radius,color,**kw)
    def window(label,side,poly,**kw):
        # Outer frame shares exact corner coordinates with the body/roof/door.
        inner=inset(poly,.038)
        n=len(poly);pts=side_points(poly+inner,side)
        sheet(api,label+'_frame',pts,[(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)],(-side*.03,0,0),PAINT,category=5,**kw)
        edge_tube(label+'_rubber',side,inner,.010,BLACK,category=5,**kw)
        # The slab is seated within the frame, not separated from it by an unfilled strip.
        pts=[(x-side*.011,y,z) for x,y,z in side_points(inner,side)]
        sheet(api,label,pts,[tuple(range(n))],(-side*.004,0,0),GLASS,category=4,**kw)
    def transverse_window(label,outer,offset,hinge=0,pivot=(0,0,0),angle=0,margin=.045):
        # Linear interpolation of matching edges gives a coplanar inner trapezoid.
        # Vertices are bottom-left, bottom-right, top-right, top-left.
        import math
        height=math.dist(outer[0],outer[3]);t=margin/height
        l0=tuple((1-t)*a+t*b for a,b in zip(outer[0],outer[3]));l1=tuple(t*a+(1-t)*b for a,b in zip(outer[0],outer[3]))
        r0=tuple((1-t)*a+t*b for a,b in zip(outer[1],outer[2]));r1=tuple(t*a+(1-t)*b for a,b in zip(outer[1],outer[2]))
        inner=[(l0[0]+margin,*l0[1:]),(r0[0]-margin,*r0[1:]),(r1[0]-margin,*r1[1:]),(l1[0]+margin,*l1[1:])]
        md=dict(hinge=hinge,pivot=pivot,angle=angle)
        sheet(api,label+'_frame',outer+inner,[(i,(i+1)%4,(i+1)%4+4,i+4) for i in range(4)],offset,PAINT,category=5,**md)
        tube(label+'_rubber',inner+inner[:1],.012,BLACK,category=5,**md)
        sheet(api,label,inner,[(0,1,2,3)],tuple(v*.18 for v in offset),GLASS,category=4,**md)

    # Continuous passenger floor and center-tunnel returns, without wheel-volume intrusion.
    for side in (-1,1):
        a,b=(.215,w-.036) if side>0 else (-w+.036,-.215)
        box('floor_pan_'+str(side),(a,floor-.025,-.855),(b,floor,cf+.013),PLASTIC,category=1)
        a,b=(0,.68) if side>0 else (-.68,0)
        box('rear_floor_bridge_'+str(side),(a,floor-.025,-1.77),(b,floor,-.842),PLASTIC,category=1)
        # Inner sill closes the floor-to-door seam even on the high van/SUV floor.
        x=side*(w-.032)
        box('inner_sill_'+str(side),(x-.024,.411,-.852),(x+.024,floor+.015,cf+.018),PAINT,category=1)
        box('frame_rail_'+str(side),(side*.67-.025,.332,-tail+.07),(side*.67+.025,floor,cf+.02),PLASTIC,category=1)
    box('transmission_tunnel',(-.224,floor-.015,-.855),(.224,max(floor+.19,.75),cf+.012),PLASTIC,category=1)
    box('firewall',(-.78,.33,cf),(.78,belt+.018,cf+.025),PLASTIC,category=1)
    for side in (-1,1):
        a,b=(.76,w) if side>0 else (-w,-.76)
        box('firewall_return_'+str(side),(a,floor-.02,cf),(b,belt+.024,cf+.025),PLASTIC,category=1)
    box('luggage_floor',(-.68,rear_floor-.025,rear_z-.005),(.68,rear_floor,-.842),PLASTIC,category=1)
    # Ramp joins the raised luggage deck and front floor; no one-centimetre daylight slit.
    sheet(api,'rear_floor_riser',[(-.68,floor,-.85),(.68,floor,-.85),(.68,rear_floor,-1.06),(-.68,rear_floor,-1.06)],[(0,1,2,3)],(0,-.025,0),PLASTIC,category=1)
    for side in (-1,1):
        x0,x1=(.67,w-.026) if side>0 else (-w+.026,-.67)
        box('rear_floor_wing_'+str(side),(x0,rear_floor-.025,rear_z),(x1,rear_floor,-1.747),PLASTIC,category=1)
        # Fitted rear inner wheelhouse ties the cabin/luggage floor to the quarter skin.
        angles=[asin((.435-.34)/.444)+(pi-2*asin((.435-.34)/.444))*i/36 for i in range(37)]
        arc=[(-1.30+.444*cos(t),.34+.444*sin(t)) for t in angles]
        pts=[(side*x,y,z) for x in (.666,w-.019) for z,y in arc]
        sheet(api,'rear_wheelhouse_'+str(side),pts,[(i,i+1,i+38,i+37) for i in range(36)],(0,.022,0),PLASTIC,category=1)
        poly=[(arc[0][0],rear_floor)]+[(z,max(y,rear_floor)) for z,y in arc if y>rear_floor]+[(arc[-1][0],rear_floor)]
        # Inner wheelhouse wall is a deliberate vertical panel; confined below the belt.
        pts=[(side*.666,y,z) for z,y in poly]
        sheet(api,'wheelhouse_inner_'+str(side),pts,[tuple(range(len(pts)))],(-side*.018,0,0),PLASTIC,category=1)
    for z in (-.7,0,.7):box('floor_crossmember_'+str(z),(-.68,.322,z-.019),(.68,.345,z+.019),PLASTIC,category=1)

    # Per-body side apertures, all evaluated on the same planar upper surface.
    for side in (-1,1):
        suffix='left' if side<0 else 'right';fh=1 if side<0 else 2
        rh=(7 if side<0 else 8) if name=='van' else (3 if side<0 else 4)
        front_end=-.86 if name=='sports_car' else -.25
        rear_end=-1.77 if name=='van' else max(cr+.11,-1.075)
        angle=68 if side<0 else -68
        doors=[(front_end+seam,cf-seam,fh,(side*w,belt,cf),'front')]
        if name!='sports_car':doors.append((rear_end+seam,front_end-seam,rh,(side*w,belt,front_end),'rear'))
        skin('front_fender_'+suffix,side,cf+seam,front_z,category=3)
        skin('rear_quarter_'+suffix,side,rear_z,(front_end if name=='sports_car' else rear_end)-seam,category=3)
        for a,b,h,pivot,label in doors:
            md=dict(hinge=h,pivot=pivot,angle=angle)
            skin('door_'+label+'_'+suffix,side,a,b,category=2,**md)
            poly=upper_poly(a,b)
            if name=='van' and label=='rear':
                side_plate('cargo_upper_'+suffix,side,poly,category=2,**md)
                inset_poly=inset(poly,.085)
                pts=[(x+side*.003,y,z) for x,y,z in side_points(inset_poly,side)]
                sheet(api,'cargo_rebate_'+suffix,pts,[tuple(range(len(pts)))],(-side*.008,0,0),PAINT,category=5,**md)
            else:window('window_'+suffix+'_'+label,side,poly,**md)
            # The inner card follows the door's wheel-arch cut, not a free-standing rectangle.
            ca,cb=a+.05,b-.05
            n=max(2,int((cb-ca)/.035));zs=[ca+(cb-ca)*i/n for i in range(n+1)]
            pts=[(side*(w-.052),max(floor+.025,bottom(z)+.03),z) for z in zs]
            pts += [(side*(w-.052),belt-.065,cb),(side*(w-.052),belt-.065,ca)]
            sheet(api,'door_card_'+suffix+'_'+label,pts,[tuple(range(len(pts)))],(-side*.022,0,0),PLASTIC,category=11,**md)
            tube('door_armrest_'+suffix+'_'+label,[(side*(w-.095),belt-.24,a+.16),(side*(w-.095),belt-.24,b-.16)],.026,BLACK,category=11,**md)
            z=a+.16
            box('handle_'+suffix+'_'+label,(side*(w+.014)-.015,belt-.109,z-.09),(side*(w+.014)+.015,belt-.071,z+.09),CHROME,category=5,**md)
            # Stationary recessed aperture seals are continuous from sill to roof rail.
            outline=side_points(poly,side)
            edge=[(x-side*.035,y,z) for x,y,z in outline]
            tube('aperture_seal_'+suffix+'_'+label,edge+edge[:1],.012,BLACK,category=1)
        # Complete fixed rear quarter. No point sits above the roof's rear profile.
        fixed_end=front_end if name=='sports_car' else rear_end
        if name=='van':
            side_plate('cargo_rear_quarter_'+suffix,side,upper_poly(cr+seam,fixed_end-seam),category=1)
            # Close the 25 cm slot formerly left between slider and rear cargo panel.
            side_plate('cargo_rear_jamb_'+suffix,side,upper_poly(fixed_end-.032,fixed_end+.025),thickness=.048,color=PLASTIC,category=1)
        else:window('window_'+suffix+'_quarter',side,upper_poly(cr+seam,fixed_end-seam))
        # Exact roof edge and belt returns cover joins between the separately operable panes.
        tube('roof_side_rail_'+suffix,[(side*wr,roof-.012,rr),(side*wr,roof-.012,rf)],.026,PAINT,category=1)
        tube('a_pillar_'+suffix,[(side*w,belt,cf),(side*wr,roof,rf)],.031,PAINT,category=1)
        tube('c_pillar_'+suffix,[(side*w,belt,cr),(side*wr,roof,rr)],.038,PAINT,category=1)
        if name!='sports_car':
            tube('b_pillar_'+suffix,[(side*(w-.024),floor,front_end),(side*width(top(front_end)),top(front_end),front_end)],.027,PLASTIC,category=1)
        for a,b,h,pivot,label in doors:
            tube('door_belt_'+suffix+'_'+label,[(side*(w-.005),belt,a),(side*(w-.005),belt,b)],.018,BLACK,category=5,hinge=h,pivot=pivot,angle=angle)
        # Door-body lower perimeter joins are bounded in millimetres, not open centimetre gaps.
        for z in (cf,front_end,rear_end):
            tube('lower_jamb_'+suffix+str(z),[(side*(w-.035),bottom(z),z),(side*(w-.035),belt,z)],.030,PLASTIC,category=1)
        tube('mirror_mount_'+suffix,[(side*(w-.03),belt+.035,.64),(side*(w+.068),belt+.065,.62)],.019,BLACK,category=5)
        box('mirror_'+suffix,(side*(w+.07)-.073,belt+.025,.53),(side*(w+.07)+.073,belt+.126,.71),PLASTIC,category=5)
        box('mirror_glass_'+suffix,(side*(w+.07)-.056,belt+.043,.526),(side*(w+.07)+.056,belt+.111,.531),ALLOY,category=5)
        for axle,corner in ((1.35,'f'),(-1.30,'r')):
            start=asin((.435-.34)/.444)
            points=[(side*(w+.002),.34+.444*sin(start+(pi-2*start)*i/40),axle+.444*cos(start+(pi-2*start)*i/40)) for i in range(41)]
            tube('arch_trim_'+corner+suffix,points,.010 if name not in ('suv','van') else .024,BLACK)
            tube('sport_arch_'+corner+suffix,[(x+side*.018,y+.006,z) for x,y,z in points],.018,PAINT,group=5,variant=2)
        if name=='suv':
            # Four mounting feet physically connect rails to the roof rather than float above it.
            for z in (rr+.13,rf-.13):box('rail_foot_'+suffix+str(z),(side*(wr-.09)-.025,roof-.004,z-.04),(side*(wr-.09)+.025,roof+.072,z+.04),BLACK)
            tube('roof_rail_'+suffix,[(side*(wr-.09),roof+.065,rr+.13),(side*(wr-.09),roof+.085,rf-.13)],.023,ALLOY)
        if name=='van':
            tube('sliding_track_'+suffix,[(side*(w+.025),belt+.06,cr+.08),(side*(w+.025),belt+.06,-.27)],.012,ALLOY)
            # The rail is now seated on the roof-side joint and stops at the same rear header.
            tube('roof_gutter_'+suffix,[(side*wr,roof+.017,rr),(side*wr,roof+.017,rf)],.013,BLACK)
        box('sport_skirt_'+suffix,(side*(w+.021)-.016,.41,-.84),(side*(w+.021)+.016,.485,.84),PLASTIC,group=5,variant=2)

    # Real roof thickness all the way to its edges; matching side rails and window frames.
    points=[]
    for z in (rr,rf):
        for x,y in [(-wr,roof),(-wr+.10,roof+.018),(0,roof+.026),(wr-.10,roof+.018),(wr,roof)]:points.append((x,y,z))
    sheet(api,'roof',points,[(i,i+1,i+6,i+5) for i in range(4)],(0,-.034,0),PAINT,category=1)
    windshield=[(-w,belt,cf),(w,belt,cf),(wr,roof,rf),(-wr,roof,rf)]
    transverse_window('windshield',windshield,(0,-.013,-.022))
    for x in (-.42,.13):tube('wiper_'+str(x),[(x,belt+.04,cf+.012),(x+.27,belt+.085,cf-.05)],.009,BLACK,category=5)
    # Solid bonnet + inner face. Its pivot is shared by its edge seals and underside braces.
    pivot=(0,hood_y,cf);hmd=dict(hinge=5,pivot=pivot,angle=-65)
    points=[]
    for z in (cf+seam,1.62,front_z):
        for x in (-.77,-.65,0,.65,.77):points.append((x,hood_y+(.030 if abs(x)<.67 else 0),z))
    sheet(api,'hood',points,[(r*5+i,r*5+i+1,(r+1)*5+i+1,(r+1)*5+i) for r in range(2) for i in range(4)],(0,-.018,0),PAINT,category=2,**hmd)
    for side in (-1,1):
        tube('hood_edge_'+str(side),[(side*.772,hood_y,cf),(side*.772,hood_y,front_z)],.008,BLACK,category=5,**hmd)
        sheet(api,'bonnet_shoulder_'+str(side),[(side*.774,hood_y,cf),(side*w,belt,cf),(side*w,belt,front_z),(side*.774,hood_y,front_z)],[(0,1,2,3)],(0,-.021,0),PAINT)
        box('bonnet_recess_'+str(side),(side*.77-.018,hood_y-.031,cf),(side*.77+.018,hood_y-.017,front_z),BLACK,category=1)
    box('cowl',(-w,belt-.014,cf-.013),(w,hood_y+.004,cf+.002),PAINT,category=1)
    box('front_fascia',(-w,.75,front_z-.012),(w,hood_y+.015,nose),PAINT)
    for variant in (1,2):
        low=.395 if variant==2 else .44
        box('front_bumper',(-w+.004,low,nose-.12),(w-.004,.765,nose+.005),PLASTIC if name in ('suv','van') else PAINT,group=5,variant=variant,bevel=.035)
        box('front_lower_grille',(-.51,.49,nose+.006),(.51,.675,nose+.017),BLACK,group=5,variant=variant)
        for y in (.515,.55,.585,.62,.655):box('grille_slat_'+str(y),(-.485,y,nose+.017),(.485,y+.007,nose+.020),PLASTIC,group=5,variant=variant)
        if variant==2:box('front_splitter',(-w-.02,.389,nose-.07),(w+.02,.413,nose+.041),BLACK,group=5,variant=2)
    for side in (-1,1):
        xx=side*(w-.25);height=.075 if name=='sports_car' else .16 if name=='van' else .11;yy=.91 if name=='sports_car' else min(belt-.13,1.05)
        box('front_light_housing_'+str(side),(xx-.18,yy-height*.65,nose-.005),(xx+.18,yy+height*.65,nose+.016),BLACK,category=6)
        box('front_light_'+str(side),(xx-.155,yy-height*.40,nose+.017),(xx+.155,yy+height*.40,nose+.023),WHITE,category=6,kind=3)
        box('front_indicator_'+str(side),(xx-.14,yy-height*.55,nose+.024),(xx+.14,yy-height*.43,nose+.027),AMBER,category=6,kind=3)
    box('front_badge',(-.036,.94,nose+.022),(.036,1.002,nose+.03),CHROME,category=5)
    box('front_plate',(-.24,.685,nose+.021),(.24,.755,nose+.028),WHITE,category=5)

    # Rear construction: a shaped aperture, fixed quarters and one coherent moving assembly.
    box('rear_bumper',(-w+.01,.43,-tail),(w-.01,.785,rear_z+.035),PLASTIC if name in ('suv','van') else PAINT,bevel=.035)
    if name in ('hatchback','suv'):
        tw=w-.285;tr=wr-.08;pivot=(0,roof,rr);md=dict(hinge=6,pivot=pivot,angle=85)
        outer=[(-tw,belt,cr),(tw,belt,cr),(tr,roof,rr),(-tr,roof,rr)]
        transverse_window('rear_glass',outer,(0,-.016,.020),**md)
        # Thin fixed edge strips follow exactly the same rake. No full-height rear sails.
        for side in (-1,1):
            sheet(api,'rear_fixed_quarter_'+str(side),[(side*tw,belt,cr),(side*w,belt,cr),(side*wr,roof,rr),(side*tr,roof,rr)],[(0,1,2,3)],(0,-.015,.022),PAINT,category=1)
            x0,x1=(tw,w) if side>0 else (-w,-tw)
            box('rear_lamp_quarter_'+str(side),(x0,.767,rear_z-.004),(x1,belt+.012,cr+.028),PAINT,category=1)
            # Where the cabin tapers to the rear valance, a low triangular return closes the side.
            if abs(rear_z-cr)>.002:
                side_plate('rear_lower_return_'+str(side),side,[(rear_z,.78),(cr,belt),(cr,.78)],category=1)
        box('trunk_lid',(-tw+seam,.78,rear_z), (tw-seam,belt,cr+.023),PAINT,category=2,**md)
        tube('hatch_lower_seal',[(-tw,belt,cr),(-tw,.78,rear_z),(tw,.78,rear_z),(tw,belt,cr)],.011,BLACK,category=1)
        # Inner return follows the same pivot, making an opened hatch an actual panel.
        box('hatch_handle',(-.13,belt-.095,rear_z-.022),(.13,belt-.056,rear_z),BLACK,category=5,**md)
        plate_md=md
    elif name=='van':
        # Both cargo doors terminate at a roof-height header, not an isolated upper rectangle.
        for side in (-1,1):
            hinge=9 if side<0 else 10;pivot=(side*(w-.065),1.1,rear_z);angle=105 if side<0 else -105
            md=dict(hinge=hinge,pivot=pivot,angle=angle)
            x0,x1=(-w+.07,-seam) if side<0 else (seam,w-.07)
            pts=[(x0,.78,rear_z),(x1,.78,rear_z),(x1 if side<0 else wr-.055,roof-.045,rear_z),( -wr+.055 if side<0 else x0,roof-.045,rear_z)]
            sheet(api,'barn_door_'+str(side),pts,[(0,1,2,3)],(0,0,.032),PAINT,category=2,**md)
            inset_x0=x0+.06;inset_x1=x1-.06
            box('barn_inset_'+str(side),(inset_x0,1.05,rear_z-.006),(inset_x1,roof-.16,rear_z),PLASTIC,category=5,**md)
            box('barn_handle_'+str(side),(side*.095-.018,1.10,rear_z-.035),(side*.095+.018,1.30,rear_z-.005),BLACK,category=5,**md)
            # Door posts taper from lower body width to the common roof width.
            sheet(api,'rear_door_post_'+str(side),[(side*(w-.07),.77,rear_z),(side*w,.77,rear_z),(side*wr,roof,rear_z),(side*(wr-.055),roof,rear_z)],[(0,1,2,3)],(0,0,.048),PAINT,category=1)
        box('rear_roof_header',(-wr,roof-.050,rear_z), (wr,roof+.004,rr+.012),PAINT,category=1)
        tube('rear_center_seal',[(0,.785,rear_z+.019),(0,roof-.039,rear_z+.019)],.012,BLACK,category=5,hinge=9,pivot=(-w+.065,1.1,rear_z),angle=105)
        plate_md={} # Plate fixed to the lower valance, not suspended between moving doors.
    else:
        transverse_window('rear_glass',[(-w,belt,cr),(w,belt,cr),(wr,roof,rr),(-wr,roof,rr)],(0,-.014,.020))
        tw=.75
        for side in (-1,1):
            x0,x1=(tw,w) if side>0 else (-w,-tw)
            box('rear_deck_shoulder_'+str(side),(x0,belt-.026,rear_z),(x1,belt+.010,cr),PAINT,category=1)
        box('rear_deck_header',(-w,belt-.025,cr-.021),(w,belt+.010,cr+.004),PAINT,category=1)
        box('trunk_lid',(-tw+seam,belt-.012,rear_z), (tw-seam,belt+.015,cr-.018),PAINT,category=2,hinge=6,pivot=(0,belt,cr-.018),angle=64)
        box('rear_lamp_panel',(-w,.78,rear_z-.012),(w,belt+.010,rear_z+.030),PAINT,category=1)
        tube('boot_seal',[(-tw,belt-.013,rear_z),(tw,belt-.013,rear_z),(tw,belt-.013,cr-.018),(-tw,belt-.013,cr-.018),(-tw,belt-.013,rear_z)],.012,BLACK,category=1)
        plate_md={}
    # Model-specific lamp/plate mounting: never leave a lamp floating behind a sloped skin.
    for side in (-1,1):
        xx=side*(w-.16);yy=min(belt-.11,1.16);hh=.18 if name in ('suv','van') else .075
        if name=='van':
            x0,x1=(w-.28,w-.02) if side>0 else (-w+.02,-w+.28)
            box('rear_lamp_pedestal_'+str(side),(x0,.785,rear_z-.015),(x1,1.29,rear_z+.038),PAINT,category=1)
        box('rear_light_housing_'+str(side),(xx-.11,yy-hh/2,rear_z-.027),(xx+.11,yy+hh/2,rear_z),BLACK,category=6)
        box('rear_light_'+str(side),(xx-.088,yy-hh*.36,rear_z-.033),(xx+.088,yy+hh*.36,rear_z-.027),RED,category=6,kind=3)
        box('rear_reverse_'+str(side),(xx-.068,yy-hh*.12,rear_z-.037),(xx+.068,yy,rear_z-.033),WHITE,category=6,kind=3)
    # The van plate sits on the bumper face, not inside its opaque volume.
    plate_y=.60 if name=='van' else .82
    plate_z=-tail-.002 if name=='van' else rear_z
    box('rear_plate_recess',(-.28,plate_y,plate_z-.025),(.28,plate_y+.12,plate_z+.008),BLACK,category=5,**plate_md)
    box('rear_plate',(-.23,plate_y+.018,plate_z-.031),(.23,plate_y+.095,plate_z-.026),WHITE,category=5,**plate_md)
    # Interior only: preserves original part IDs/materials and fits tail exhaust after rear axle.
    copy_fitted(s)
