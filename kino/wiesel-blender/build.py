# Wiesel für den bTUNES-Kino-Modus: Low-Poly-Figur mit Skelett und Tanzbewegungen
# Körper, Arme und Beine sind eigene Teile (überlappend), damit sich der Rumpf beim Tanzen nicht verzieht.
import bpy, math
from mathutils import Vector
bpy.ops.wm.read_factory_settings(use_empty=True)
sc=bpy.context.scene
V=Vector
def skinpart(name,pts,edges,root,sub=2):
  names=list(pts);idx={n:i for i,n in enumerate(names)}
  me=bpy.data.meshes.new(name);me.from_pydata([pts[n][0] for n in names],[(idx[a],idx[b]) for a,b in edges],[])
  o=bpy.data.objects.new(name,me);sc.collection.objects.link(o)
  sk=o.modifiers.new('skin','SKIN');sk.use_smooth_shade=True
  for i,n in enumerate(names):
    r=pts[n][1];me.skin_vertices[0].data[i].radius=r if isinstance(r,tuple) else (r,r)
  me.skin_vertices[0].data[idx[root]].use_root=True
  sd=o.modifiers.new('sub','SUBSURF');sd.levels=sub;sd.render_levels=sub
  bpy.ops.object.select_all(action='DESELECT');o.select_set(True);bpy.context.view_layer.objects.active=o
  bpy.ops.object.modifier_apply(modifier='skin');bpy.ops.object.modifier_apply(modifier='sub')
  bpy.ops.object.mode_set(mode='EDIT');bpy.ops.mesh.select_all(action='SELECT');bpy.ops.mesh.normals_make_consistent(inside=False);bpy.ops.object.mode_set(mode='OBJECT')
  o['kind']=name.rstrip('LR')
  g=o.vertex_groups.new(name='P_'+name);g.add(list(range(len(o.data.vertices))),1.0,'REPLACE')
  return o
def ball(name,loc,scale,rot=(0,0,0),seg=10,kind=None,grp=None):
  bpy.ops.mesh.primitive_uv_sphere_add(segments=seg,ring_count=max(5,seg//2),radius=1,location=loc,rotation=rot)
  o=bpy.context.active_object;o.name=name;o.scale=scale;bpy.ops.object.transform_apply(scale=True,rotation=True,location=False)
  o['kind']=kind or name.rstrip('LR')
  g=o.vertex_groups.new(name=grp or 'P_'+o['kind']);g.add(list(range(len(o.data.vertices))),1.0,'REPLACE');return o
# ---------- Rumpf mit Hals, Kopf und Schwanz ----------
T={'bot':((0,0.02,0.235),(.062,.058)),'pel':((0,0.012,0.30),(.09,.084)),'bel':((0,-0.004,0.42),(.086,.09)),'wa':((0,-0.01,0.51),(.076,.08)),'che':((0,-0.016,0.6),(.078,.072)),
   'nb':((0,-0.022,0.685),.06),'nk':((0,-0.036,0.775),.049),'hd':((0,-0.06,0.868),(.074,.062)),'sn':((0,-0.148,0.855),.037),'tip':((0,-0.19,0.85),.019),
   't0':((0,0.07,0.255),.038),'t1':((0,0.16,0.2),.033),'t2':((0,0.26,0.205),.029),'t3':((0,0.33,0.27),.024),'t4':((0,0.36,0.35),.017),'t5':((0,0.365,0.4),.01)}
TE=[('bot','pel'),('pel','bel'),('bel','wa'),('wa','che'),('che','nb'),('nb','nk'),('nk','hd'),('hd','sn'),('sn','tip'),('pel','t0'),('t0','t1'),('t1','t2'),('t2','t3'),('t3','t4'),('t4','t5')]
TB={('bot','pel'):'root',('pel','bel'):'root',('bel','wa'):'spine',('wa','che'):'spine',('che','nb'):'spine',('nb','nk'):'neck',('nk','hd'):'neck',('hd','sn'):'head',('sn','tip'):'head',
    ('pel','t0'):'root',('t0','t1'):'tail1',('t1','t2'):'tail2',('t2','t3'):'tail3',('t3','t4'):'tail3',('t4','t5'):'tail3'}
objs=[skinpart('rumpf',T,TE,'pel')]
SEG={'rumpf':[(V(T[a][0]),V(T[b][0]),TB[(a,b)]) for a,b in TE]}
# ---------- Arme (Vorderbeine) und Hinterbeine ----------
LIMB={}
for s,x in (('L',1),('R',-1)):
  A={'sh':((x*0.058,-0.028,0.628),.039),'el':((x*0.094,-0.075,0.548),.03),'wr':((x*0.09,-0.122,0.497),.023),'pw':((x*0.088,-0.148,0.482),(.028,.024))}
  AE=[('sh','el'),('el','wr'),('wr','pw')]
  objs.append(skinpart('arm'+s,A,AE,'sh'))
  SEG['arm'+s]=[(V(A['sh'][0]),V(A['el'][0]),'arm.'+s),(V(A['el'][0]),V(A['wr'][0]),'fore.'+s),(V(A['wr'][0]),V(A['pw'][0]),'fore.'+s)]
  Lg={'hip':((x*0.045,0.02,0.32),(.058,.064)),'kn':((x*0.072,-0.045,0.172),.046),'an':((x*0.07,0.032,0.048),.026),'he':((x*0.071,0.0,0.026),.024),'to':((x*0.073,-0.082,0.02),(.027,.02))}
  LE=[('hip','kn'),('kn','an'),('an','he'),('he','to')]
  objs.append(skinpart('bein'+s,Lg,LE,'hip'))
  SEG['bein'+s]=[(V(Lg['hip'][0]),V(Lg['kn'][0]),'thigh.'+s),(V(Lg['kn'][0]),V(Lg['an'][0]),'shin.'+s),(V(Lg['an'][0]),V(Lg['he'][0]),'foot.'+s),(V(Lg['he'][0]),V(Lg['to'][0]),'foot.'+s)]
  LIMB[s]=(A,Lg)
  # kleine Zehen an Vorder- und Hinterpfoten
  for k in (-1,0,1):
    objs.append(ball('zeheA'+s,(x*0.088+k*0.013,-0.163,0.478),(.011,.012,.009),seg=6,kind='zehe',grp='P_zeheA'+s))
    objs.append(ball('zeheB'+s,(x*0.073+k*0.014,-0.096,0.017),(.012,.013,.009),seg=6,kind='zehe',grp='P_zeheB'+s))
# ---------- Ohren, Augen, Nase ----------
for s,x in (('L',1),('R',-1)):
  objs.append(ball('ohr'+s,(x*0.062,-0.025,0.905),(.024,.008,.018),(math.radians(-5),math.radians(x*20),x*math.radians(55))))
  objs.append(ball('auge'+s,(x*0.042,-0.1,0.882),(.0105,.009,.011),seg=8))
  objs.append(ball('glanz'+s,(x*0.045,-0.108,0.887),(.003,.0025,.003),seg=6))
objs.append(ball('nase',(0,-0.205,0.857),(.014,.011,.011),seg=8))
# ---------- Materialien und Fellfarben ----------
def mat(n,c,rough=.7):
  m=bpy.data.materials.new(n);m.use_nodes=True;b=m.node_tree.nodes['Principled BSDF'];b.inputs['Base Color'].default_value=(*c,1);b.inputs['Roughness'].default_value=rough;return m
fell=mat('Fell',(0.12,0.05,0.018));dunkel=mat('Dunkel',(0.02,0.015,0.01),.35);weiss=mat('Glanz',(1,1,1),.2);innen=mat('Ohr',(0.2,0.08,0.05))
nt=fell.node_tree;cn=nt.nodes.new('ShaderNodeVertexColor');cn.layer_name='Col'
nt.links.new(cn.outputs['Color'],nt.nodes['Principled BSDF'].inputs['Base Color'])
def ss(a,b,x):t=max(0,min(1,(x-a)/(b-a)));return t*t*(3-2*t)
BR=(0.12,0.05,0.018);CR=(0.86,0.76,0.58);DK=(0.03,0.015,0.008)
def mix(a,b,k):return [a[i]*(1-k)+b[i]*k for i in range(3)]
for o in objs:
  me=o.data;nm=o['kind']
  m={'ohr':innen,'auge':dunkel,'glanz':weiss,'nase':dunkel}.get(nm,fell);me.materials.append(m)
  ca=me.color_attributes.new('Col','FLOAT_COLOR','POINT')
  for v in me.vertices:
    c=v.co;n=v.normal;col=BR
    if nm=='rumpf':
      k=ss(-0.3,-0.75,n.y)*ss(0.27,0.37,c.z)*ss(0.9,0.84,c.z)*ss(0.052,0.03,abs(c.x))
      if c.z>0.78 and c.y<-0.06:k=max(k,ss(-0.1,-0.6,n.z)*ss(0.07,0.05,abs(c.x)))
      col=mix(BR,CR,k);col=mix(col,DK,ss(0.31,0.36,c.y)*ss(0.26,0.32,c.z))
    elif nm=='arm':col=mix(BR,CR,ss(0.52,0.49,c.z)*.85+ss(-0.4,-0.8,n.y)*.25)      # helle Pfoten
    elif nm=='bein':col=mix(BR,CR,ss(0.06,0.025,c.z)*ss(-0.0,-0.06,c.y)*.8)
    elif nm=='zehe':col=CR
    else:col=(1,1,1)
    ca.data[v.index].color=(*col,1)
bpy.ops.object.select_all(action='DESELECT')
for o in objs:o.select_set(True)
bpy.context.view_layer.objects.active=objs[0];bpy.ops.object.join()
body=bpy.context.active_object;body.name='Wiesel'
bpy.ops.object.shade_smooth()
# ---------- Skelett ----------
arm=bpy.data.armatures.new('Skelett');rig=bpy.data.objects.new('Rig',arm);sc.collection.objects.link(rig)
bpy.ops.object.select_all(action='DESELECT');rig.select_set(True);bpy.context.view_layer.objects.active=rig
bpy.ops.object.mode_set(mode='EDIT')
B={}
def bone(n,h,t,par=None,deform=True):
  b=arm.edit_bones.new(n);b.head=h;b.tail=t;b.use_deform=deform
  if par:b.parent=B[par];b.use_connect=(V(B[par].tail)-V(h)).length<1e-4
  B[n]=b;return b
bone('root',(0,0,0.30),(0,0,0.42))
bone('spine',(0,0,0.42),(0,-0.016,0.60),'root')
bone('neck',(0,-0.016,0.60),(0,-0.036,0.775),'spine')
bone('head',(0,-0.036,0.775),(0,-0.17,0.86),'neck')
bone('tail1',(0,0.07,0.255),(0,0.16,0.2),'root');bone('tail2',(0,0.16,0.2),(0,0.26,0.205),'tail1');bone('tail3',(0,0.26,0.205),(0,0.36,0.35),'tail2')
for s,x in (('L',1),('R',-1)):
  A,Lg=LIMB[s]
  bone('arm.'+s,A['sh'][0],A['el'][0],'spine');bone('fore.'+s,A['el'][0],A['pw'][0],'arm.'+s)
  bone('thigh.'+s,Lg['hip'][0],Lg['kn'][0],'root');bone('shin.'+s,Lg['kn'][0],Lg['an'][0],'thigh.'+s)
  bone('foot.'+s,Lg['an'][0],Lg['to'][0],'shin.'+s)
  bone('ik.'+s,Lg['an'][0],(Lg['an'][0][0],Lg['an'][0][1],0.0),None,False)
  bone('pole.'+s,(x*0.09,-0.35,0.17),(x*0.09,-0.35,0.12),None,False)
for s in 'LR':B['foot.'+s].parent=B['ik.'+s];B['foot.'+s].use_connect=False
for b in arm.edit_bones:
  d=V(b.tail)-V(b.head)
  b.align_roll(V((0,0,1)) if abs(d.z)<abs(d.y)*0.8 else V((0,-1,0)))
bpy.ops.object.mode_set(mode='POSE')
for s in 'LR':
  c=rig.pose.bones['shin.'+s].constraints.new('IK');c.target=rig;c.subtarget='ik.'+s;c.pole_target=rig;c.pole_subtarget='pole.'+s;c.pole_angle=math.radians(-90);c.chain_count=2
for pb in rig.pose.bones:pb.rotation_mode='XYZ'
bpy.ops.object.mode_set(mode='OBJECT')
# ---------- Gewichte: jedes Teil nur an seine eigenen Knochen ----------
def segd(p,a,b):
  ab=b-a;t=max(0,min(1,(p-a).dot(ab)/ab.length_squared));return (p-(a+ab*t)).length
for b in arm.bones:
  if b.use_deform:body.vertex_groups.new(name=b.name)
fixed={'P_ohr':'head','P_auge':'head','P_glanz':'head','P_nase':'head','P_zeheAL':'fore.L','P_zeheAR':'fore.R','P_zeheBL':'foot.L','P_zeheBR':'foot.R'}
gname={g.index:g.name for g in body.vertex_groups}
for v in body.data.vertices:
  part=None
  for ge in v.groups:
    n=gname[ge.group]
    if n.startswith('P_'):part=n
  if part in fixed:ws={fixed[part]:1.0}
  elif part and part[2:] in SEG:
    ds=sorted(((segd(v.co,a,b),n) for a,b,n in SEG[part[2:]]))[:3];ws={}
    for d,n in ds:ws[n]=ws.get(n,0)+1/(d+2e-3)**4
    tot=sum(ws.values());ws={n:w/tot for n,w in ws.items()}
  else:ws={'head':1.0}
  for n,w in ws.items():
    if w>.03:body.vertex_groups[n].add([v.index],w,'REPLACE')
for g in list(body.vertex_groups):
  if g.name.startswith('P_'):body.vertex_groups.remove(g)
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
