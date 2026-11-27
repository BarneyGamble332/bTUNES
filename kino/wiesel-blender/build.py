# Wiesel für den bTUNES-Kino-Modus: Low-Poly-Figur mit Skelett und Tanzbewegungen
import bpy, bmesh, math
from mathutils import Vector, Euler
bpy.ops.wm.read_factory_settings(use_empty=True)
sc=bpy.context.scene
V=Vector
# ---------- Körper per Skin-Modifier aus einem Gerüst ----------
pts={  # Name: (Position, Radius)
 'pel':((0,0.0,0.30),.085),'bel':((0,-0.005,0.45),.088),'che':((0,-0.01,0.59),.075),'nb':((0,-0.015,0.68),.058),
 'nk':((0,-0.03,0.78),.05),'hd':((0,-0.06,0.87),.07),'sn':((0,-0.15,0.855),.036),'tip':((0,-0.188,0.85),.018),
 't1':((0,0.08,0.25),.034),'t2':((0,0.18,0.20),.03),'t3':((0,0.28,0.22),.026),'t4':((0,0.35,0.31),.02),'t5':((0,0.37,0.38),.012),
}
for s,x in (('L',1),('R',-1)):
  pts.update({
   'hip'+s:((x*0.06,0.0,0.29),.05),'kn'+s:((x*0.085,-0.055,0.17),.034),'an'+s:((x*0.08,0.02,0.05),.024),'to'+s:((x*0.08,-0.06,0.02),.022),
   'sh'+s:((x*0.065,-0.03,0.62),.034),'el'+s:((x*0.1,-0.08,0.53),.027),'pw'+s:((x*0.085,-0.13,0.465),.024)})
edges=[('pel','bel'),('bel','che'),('che','nb'),('nb','nk'),('nk','hd'),('hd','sn'),('sn','tip'),
 ('pel','t1'),('t1','t2'),('t2','t3'),('t3','t4'),('t4','t5')]
for s in 'LR':edges+= [('pel','hip'+s),('hip'+s,'kn'+s),('kn'+s,'an'+s),('an'+s,'to'+s),('che','sh'+s),('sh'+s,'el'+s),('el'+s,'pw'+s)]
names=list(pts);idx={n:i for i,n in enumerate(names)}
me=bpy.data.meshes.new('wiesel');me.from_pydata([pts[n][0] for n in names],[(idx[a],idx[b]) for a,b in edges],[])
body=bpy.data.objects.new('Wiesel',me);sc.collection.objects.link(body)
sk=body.modifiers.new('skin','SKIN');sk.use_smooth_shade=True
for i,n in enumerate(names):
  r=pts[n][1];me.skin_vertices[0].data[i].radius=(r,r)
me.skin_vertices[0].data[idx['pel']].use_root=True
# Kopf etwas breiter als hoch, Bauch etwas tiefer
me.skin_vertices[0].data[idx['hd']].radius=(.074,.06)
me.skin_vertices[0].data[idx['bel']].radius=(.085,.095)
sub=body.modifiers.new('sub','SUBSURF');sub.levels=2;sub.render_levels=2
bpy.context.view_layer.objects.active=body;body.select_set(True)
bpy.ops.object.modifier_apply(modifier='skin');bpy.ops.object.modifier_apply(modifier='sub')
bpy.ops.object.mode_set(mode='EDIT');bpy.ops.mesh.select_all(action='SELECT');bpy.ops.mesh.normals_make_consistent(inside=False);bpy.ops.object.mode_set(mode='OBJECT')
# ---------- Ohren, Augen, Nase ----------
def ball(name,loc,scale,rot=(0,0,0),seg=10):
  bpy.ops.mesh.primitive_uv_sphere_add(segments=seg,ring_count=max(5,seg//2),radius=1,location=loc,rotation=rot)
  o=bpy.context.active_object;o.name=name;o.scale=scale;bpy.ops.object.transform_apply(scale=True,rotation=True,location=False);return o
parts=[]
for s,x in (('L',1),('R',-1)):
  parts.append(ball('ohr'+s,(x*0.062,-0.025,0.905),(.024,.008,.018),(math.radians(-5),math.radians(x*20),x*math.radians(55))))
  parts.append(ball('auge'+s,(x*0.042,-0.1,0.882),(.0105,.009,.011),seg=8))
  parts.append(ball('glanz'+s,(x*0.045,-0.108,0.887),(.003,.0025,.003),seg=6))
parts.append(ball('nase',(0,-0.203,0.857),(.014,.011,.011),seg=8))
# ---------- Materialien ----------
def mat(n,c,rough=.7):
  m=bpy.data.materials.new(n);m.use_nodes=True;b=m.node_tree.nodes['Principled BSDF'];b.inputs['Base Color'].default_value=(*c,1);b.inputs['Roughness'].default_value=rough;return m
fell=mat('Fell',(0.12,0.05,0.018));bauch=mat('Bauch',(0.92,0.84,0.68));dunkel=mat('Dunkel',(0.02,0.015,0.01),.35);weiss=mat('Glanz',(1,1,1),.2);innen=mat('Ohr',(0.2,0.08,0.05))
body.data.materials.append(fell);body.data.materials.append(bauch);body.data.materials.append(dunkel)
for p in parts:
  p.data.materials.append({'o':innen,'a':dunkel,'g':weiss,'n':dunkel}[p.name[0]])
# Fellfarbe als weicher Verlauf (Vertex-Farben): Rücken braun, Bauch/Kehle/Kinn hell, Schwanzspitze dunkel
def ss(a,b,x):t=max(0,min(1,(x-a)/(b-a)));return t*t*(3-2*t)
BR=(0.12,0.05,0.018);CR=(0.86,0.76,0.58);DK=(0.03,0.015,0.008)
ca=body.data.color_attributes.new('Col','FLOAT_COLOR','POINT')
for v in body.data.vertices:
  c=v.co;n=v.normal
  k=ss(-0.15,-0.6,n.y)*ss(0.22,0.32,c.z)*ss(0.9,0.84,c.z)*ss(0.08,0.05,abs(c.x))
  if c.z>0.78 and c.y<-0.06:k=max(k,ss(-0.1,-0.6,n.z)*ss(0.07,0.05,abs(c.x)))   # Kinn/Unterkiefer
  for s2,x2 in (('L',1),('R',-1)):pass
  col=[BR[i]*(1-k)+CR[i]*k for i in range(3)]
  d=ss(0.3,0.36,c.y)*ss(0.27,0.33,c.z)
  col=[col[i]*(1-d)+DK[i]*d for i in range(3)]
  ca.data[v.index].color=(*col,1)
nt=fell.node_tree;cn=nt.nodes.new('ShaderNodeVertexColor');cn.layer_name='Col'
nt.links.new(cn.outputs['Color'],nt.nodes['Principled BSDF'].inputs['Base Color'])
for f in body.data.polygons:f.material_index=0
# alles zu einem Objekt verbinden
for p in parts:p.select_set(True)
body.select_set(True);bpy.context.view_layer.objects.active=body;bpy.ops.object.join()
body=bpy.context.active_object;body.name='Wiesel'
bpy.ops.object.shade_smooth()
# Augen, Ohren, Nase: Vertex-Farbe weiß, damit ihre eigene Farbe gilt
ca=body.data.color_attributes['Col']
for f in body.data.polygons:
  if f.material_index!=0:
    for vi in f.vertices:ca.data[vi].color=(1,1,1,1)
# ---------- Skelett ----------
arm=bpy.data.armatures.new('Skelett');rig=bpy.data.objects.new('Rig',arm);sc.collection.objects.link(rig)
bpy.ops.object.select_all(action='DESELECT');rig.select_set(True);bpy.context.view_layer.objects.active=rig
bpy.ops.object.mode_set(mode='EDIT')
B={}
def bone(n,h,t,par=None,deform=True):
  b=arm.edit_bones.new(n);b.head=h;b.tail=t;b.use_deform=deform
  if par:b.parent=B[par];b.use_connect=(V(B[par].tail)-V(h)).length<1e-4
  B[n]=b;return b
bone('root',(0,0,0.30),(0,0,0.40))
bone('spine',(0,0,0.40),(0,-0.01,0.60),'root')
bone('neck',(0,-0.01,0.60),(0,-0.03,0.79),'spine')
bone('head',(0,-0.03,0.79),(0,-0.17,0.86),'neck')
bone('tail1',(0,0.05,0.27),(0,0.18,0.20),'root');bone('tail2',(0,0.18,0.20),(0,0.28,0.22),'tail1');bone('tail3',(0,0.28,0.22),(0,0.37,0.38),'tail2')
for s,x in (('L',1),('R',-1)):
  bone('arm.'+s,(x*0.065,-0.03,0.62),(x*0.1,-0.08,0.53),'spine');bone('fore.'+s,(x*0.1,-0.08,0.53),(x*0.085,-0.13,0.465),'arm.'+s)
  bone('thigh.'+s,(x*0.06,0,0.29),(x*0.085,-0.055,0.17),'root');bone('shin.'+s,(x*0.085,-0.055,0.17),(x*0.08,0.02,0.05),'thigh.'+s)
  bone('foot.'+s,(x*0.08,0.02,0.05),(x*0.08,-0.06,0.02),'shin.'+s)
  # IK-Ziel für den Fuß (bleibt am Boden) und Kniezeiger
  bone('ik.'+s,(x*0.08,0.02,0.05),(x*0.08,0.02,0.0),None,False)
  bone('pole.'+s,(x*0.09,-0.35,0.17),(x*0.09,-0.35,0.12),None,False)
B['foot.L'].parent=B['ik.L'];B['foot.R'].parent=B['ik.R']
B['foot.L'].use_connect=False;B['foot.R'].use_connect=False
for b in arm.edit_bones:
  d=V(b.tail)-V(b.head)
  b.align_roll(V((0,0,1)) if abs(d.z)<abs(d.y)*0.8 else V((0,-1,0)))
bpy.ops.object.mode_set(mode='POSE')
for s in 'LR':
  c=rig.pose.bones['shin.'+s].constraints.new('IK');c.target=rig;c.subtarget='ik.'+s;c.pole_target=rig;c.pole_subtarget='pole.'+s;c.pole_angle=math.radians(-90);c.chain_count=2
for pb in rig.pose.bones:pb.rotation_mode='XYZ'
bpy.ops.object.mode_set(mode='OBJECT')
# ---------- Gewichte: nächster Knochen, weich überblendet ----------
def segd(p,a,b):
  ab=b-a;t=max(0,min(1,(p-a).dot(ab)/ab.length_squared));return (p-(a+ab*t)).length
defb=[b for b in arm.bones if b.use_deform]
for b in defb:body.vertex_groups.new(name=b.name)
emap={('pel','bel'):'root',('bel','che'):'spine',('che','nb'):'spine',('nb','nk'):'neck',('nk','hd'):'neck',('hd','sn'):'head',('sn','tip'):'head',
 ('pel','t1'):'root',('t1','t2'):'tail1',('t2','t3'):'tail2',('t3','t4'):'tail3',('t4','t5'):'tail3'}
for s2 in 'LR':emap.update({('pel','hip'+s2):'root',('hip'+s2,'kn'+s2):'thigh.'+s2,('kn'+s2,'an'+s2):'shin.'+s2,('an'+s2,'to'+s2):'foot.'+s2,
  ('che','sh'+s2):'spine',('sh'+s2,'el'+s2):'arm.'+s2,('el'+s2,'pw'+s2):'fore.'+s2})
segs=[(V(pts[a][0]),V(pts[b][0]),emap[(a,b)]) for a,b in edges]
for v in body.data.vertices:
  p=v.co
  if p.z>0.8 and p.y<0.0 and (abs(p.x)>0.045 or p.y<-0.19 or p.z>0.9):ws={'head':1.0}   # Ohren/Augen/Nase starr
  else:
    ds=sorted(((segd(p,a,b),n) for a,b,n in segs))[:3];ws={}
    for d,n in ds:ws[n]=ws.get(n,0)+1/(d+2e-3)**4
    tot=sum(ws.values());ws={n:w/tot for n,w in ws.items()}
  for n,w in ws.items():
    if w>.03:body.vertex_groups[n].add([v.index],w,'REPLACE')
mod=body.modifiers.new('rig','ARMATURE');mod.object=rig;body.parent=rig
# ---------- Tänze (24 Bilder/s, 12 Bilder = 1 Schlag bei 120 BPM) ----------
sc.render.fps=24
P=rig.pose.bones
def key(f,vals):
  for (bn,attr),v in vals.items():
    pb=P[bn];setattr(pb,attr,v);pb.keyframe_insert(attr,frame=f)
def dance(name,length,fn):
  rig.animation_data_create()
  act=bpy.data.actions.new(name);act.use_fake_user=True;rig.animation_data.action=act
  for pb in P:pb.location=(0,0,0);pb.rotation_euler=(0,0,0)
  for f in range(0,length+1,2):fn(f,f/12*math.pi)
  # alle Kurven weich und nahtlos
  return act
R=math.radians
def base(f,ph,bob=.035,sway=0,tail=20,nod=8):
  d={('root','location'):(sway,0,-abs(math.sin(ph))*bob*0+(-(1-math.cos(2*ph))/2*bob)),
     ('tail1','rotation_euler'):(0,0,R(tail)*math.sin(ph)),('tail2','rotation_euler'):(0,0,R(tail)*math.sin(ph-.6)),('tail3','rotation_euler'):(R(10)*math.sin(ph*2),0,R(tail)*math.sin(ph-1.2)),
     ('head','rotation_euler'):(R(nod)*math.sin(2*ph),0,0),('neck','rotation_euler'):(R(-nod*.5)*math.sin(2*ph),0,0)}
  for s in 'LR':d[('ik.'+s,'location')]=(0,0,0);d[('pole.'+s,'location')]=(0,0,0)
  return d
acts=[]
# Achsen (geprüft): Arm x+ = nach vorn/oben, Arm z = seitlich (L +, R -), Rumpf x+ = nach vorn beugen, z = seitlich neigen,
# y = um sich selbst drehen, Kopf x- = nicken, Schwanz z = wedeln. Becken-Position: (seitlich, hoch, -)
def base(f,ph,bob=.035,sway=0,tail=22,nod=8):
  d={('root','location'):(sway,-(1-math.cos(2*ph))/2*bob,0),('root','rotation_euler'):(R(4),0,0),
     ('tail1','rotation_euler'):(R(-10),0,R(tail)*math.sin(ph)),('tail2','rotation_euler'):(0,0,R(tail)*math.sin(ph-.6)),('tail3','rotation_euler'):(R(8)*math.sin(ph*2),0,R(tail)*math.sin(ph-1.2)),
     ('head','rotation_euler'):(R(-nod)*(1-math.cos(2*ph))/2,0,0),('neck','rotation_euler'):(R(nod*.4)*math.sin(2*ph),0,0),('spine','rotation_euler'):(0,0,0)}
  for s in 'LR':d[('ik.'+s,'location')]=(0,0,0);d[('arm.'+s,'rotation_euler')]=(R(30),0,0);d[('fore.'+s,'rotation_euler')]=(R(55),0,0)
  return d
def keyobj(f,rz=0):rig.rotation_euler=(0,0,rz);rig.keyframe_insert('rotation_euler',frame=f)
def wippen(f,ph):
  d=base(f,ph,bob=.05)
  for s in 'LR':d[('arm.'+s,'rotation_euler')]=(R(30+12*math.sin(2*ph)),0,0);d[('fore.'+s,'rotation_euler')]=(R(60),0,0)
  key(f,d);keyobj(f)
acts.append(dance('wippen',24,wippen))
def huefte(f,ph):
  d=base(f,ph,bob=.03,sway=.03*math.sin(ph))
  d[('root','rotation_euler')]=(R(4),0,R(9)*math.sin(ph))
  d[('spine','rotation_euler')]=(0,R(10)*math.sin(ph),R(-14)*math.sin(ph))
  d[('neck','rotation_euler')]=(0,0,R(6)*math.sin(ph))
  for s,o in (('L',0),('R',math.pi)):d[('arm.'+s,'rotation_euler')]=(R(35+40*math.sin(ph+o)),0,0);d[('fore.'+s,'rotation_euler')]=(R(50),0,0)
  key(f,d);keyobj(f)
acts.append(dance('huefte',24,huefte))
def zeigen(f,ph):
  up=(1-math.cos(ph/2))/2          # 0 = rechter Arm oben, 1 = linker Arm oben
  d=base(f,ph,bob=.035,sway=.025*(up*2-1))
  d[('spine','rotation_euler')]=(0,R(12)*(up*2-1),R(-8)*(up*2-1))
  d[('arm.L','rotation_euler')]=(R(35+95*up),0,R(55*up));d[('arm.R','rotation_euler')]=(R(35+95*(1-up)),0,R(-55*(1-up)))
  d[('fore.L','rotation_euler')]=(R(55*(1-up)),0,0);d[('fore.R','rotation_euler')]=(R(55*up),0,0)
  d[('head','rotation_euler')]=(R(12)*(up*2-1)*0+R(8),R(15)*(up*2-1),0)
  key(f,d);keyobj(f)
acts.append(dance('zeigen',48,zeigen))
def drehen(f,ph):
  d=base(f,ph,bob=.03)
  for s,x in (('L',1),('R',-1)):d[('arm.'+s,'rotation_euler')]=(R(40),0,R(x*75));d[('fore.'+s,'rotation_euler')]=(R(10),0,0)
  key(f,d);keyobj(f,ph/2)   # 48 Bilder = einmal ganz herum
acts.append(dance('drehen',48,drehen))
def huepfen(f,ph):
  j=max(0,math.sin(ph))**1.5*.08
  d=base(f,ph,bob=0)
  d[('root','location')]=(0,j,0)
  for s,x in (('L',1),('R',-1)):
    d[('ik.'+s,'location')]=(0,-j*.85,0)
    d[('arm.'+s,'rotation_euler')]=(R(50+70*j/.08),0,R(x*(25+30*j/.08)));d[('fore.'+s,'rotation_euler')]=(R(20),0,0)
  d[('tail1','rotation_euler')]=(R(-10+25*j/.08),0,0)
  key(f,d);keyobj(f)
acts.append(dance('huepfen',24,huepfen))
# Ruhe-Haltung als Standard
rig.animation_data.action=acts[0]
bpy.ops.wm.save_as_mainfile(filepath='/home/claude/wiesel/wiesel.blend')
print('ok',len(body.data.polygons),'Flächen')
