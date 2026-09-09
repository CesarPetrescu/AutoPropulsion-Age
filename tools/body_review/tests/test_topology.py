"""Negative controls: prove the new gate detects what a bounds-only test missed."""
import sys,unittest
from pathlib import Path
import numpy as np
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from topology import topology,SurfaceRays,probes


def cube():
    p=np.array([(0,0,0),(1,0,0),(1,1,0),(0,1,0),(0,0,1),(1,0,1),(1,1,1),(0,1,1)],dtype=float)
    faces=[(0,3,2,1),(4,5,6,7),(0,1,5,4),(3,7,6,2),(0,4,7,3),(1,2,6,5)]
    vertices=[]
    for a,b,c,d in faces:
        for ids in ((a,b,c),(a,c,d)):
            xyz=p[list(ids)];n=np.cross(xyz[1]-xyz[0],xyz[2]-xyz[0]);n/=np.linalg.norm(n)
            vertices.extend([(*v,*n,0xffffffff) for v in xyz])
    return {'vertices':vertices}


class TopologyTests(unittest.TestCase):
    def test_closed_cube_has_positive_volume_and_no_edges(self):
        r=topology(cube());self.assertAlmostEqual(r['signedVolume'],1)
        for k in ('boundaryEdges','nonManifoldEdges','windingErrors','normalErrors','degenerateTriangles'):self.assertEqual(r[k],0,k)
    def test_removed_face_is_a_real_hole(self):
        c=cube();c['vertices']=c['vertices'][6:];r=topology(c);self.assertEqual(r['boundaryEdges'],4)
    def test_single_reversed_triangle_breaks_winding(self):
        c=cube();c['vertices'][:3]=list(reversed(c['vertices'][:3]));r=topology(c);self.assertEqual(r['windingErrors'],3);self.assertEqual(r['normalErrors'],3)
    def test_complete_inversion_cannot_pass_as_consistent_outside(self):
        c=cube();c['vertices']=[(*v[:3],*[-n for n in v[3:6]],v[6]) for i in range(0,len(c['vertices']),3) for v in reversed(c['vertices'][i:i+3])]
        r=topology(c);self.assertLess(r['signedVolume'],0);self.assertEqual(r['windingErrors'],0)
    def test_duplicated_triangle_is_not_a_double_sided_fix(self):
        c=cube();c['vertices']+=c['vertices'][:3];self.assertGreater(topology(c)['nonManifoldEdges'],0)
    def test_culling_rays_reject_back_faces(self):
        rays=SurfaceRays([cube()]);self.assertTrue(rays.hit((.5,.5,-.1),(0,0,1),.2));self.assertFalse(rays.hit((.5,.5,.1),(0,0,-1),.2))
    def test_broad_bounds_do_not_hide_a_gap(self):
        c=cube();c['vertices']=c['vertices'][6:];rays=SurfaceRays([c]);self.assertFalse(rays.hit((.5,.5,-.1),(0,0,1),.2))
    def test_both_sides_of_seams_are_sampled(self):
        b=dict(id='suv',halfWidth=.995,roof=1.88,belt=1.24,roofFront=.3,roofRear=-1.9,cabinFront=.775,cabinRear=-2.23,cabinY=.2)
        names=[p[0] for p in probes(b)];self.assertTrue(any(n.endswith('True') for n in names));self.assertTrue(any(n.endswith('False') for n in names));self.assertTrue(any('floor' in n for n in names))
if __name__=='__main__':unittest.main()
