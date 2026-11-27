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
T={'bot2':((0,0.03,0.2),.028),'bot':((0,0.022,0.24),(.07,.064)),'pel':((0,0.012,0.30),(.09,.084)),'bel':((0,-0.004,0.42),(.086,.09)),'wa':((0,-0.01,0.51),(.076,.08)),'che':((0,-0.016,0.6),(.078,.072)),
   'nb':((0,-0.022,0.685),.06),'nk':((0,-0.036,0.775),.049),'hd':((0,-0.06,0.868),(.074,.062)),'sn':((0,-0.148,0.855),.037),'tip':((0,-0.19,0.85),.019),
   't0':((0,0.07,0.255),.038),'t1':((0,0.16,0.2),.033),'t2':((0,0.26,0.205),.029),'t3':((0,0.33,0.27),.024),'t4':((0,0.36,0.35),.017),'t5':((0,0.365,0.4),.01)}
TE=[('bot2','bot'),('bot','pel'),('pel','bel'),('bel','wa'),('wa','che'),('che','nb'),('nb','nk'),('nk','hd'),('hd','sn'),('sn','tip'),('pel','t0'),('t0','t1'),('t1','t2'),('t2','t3'),('t3','t4'),('t4','t5')]
TB={('bot2','bot'):'root',('bot','pel'):'root',('pel','bel'):'root',('bel','wa'):'spine',('wa','che'):'spine',('che','nb'):'spine',('nb','nk'):'neck',('nk','hd'):'neck',('hd','sn'):'head',('sn','tip'):'head',
    ('pel','t0'):'root',('t0','t1'):'tail1',('t1','t2'):'tail2',('t2','t3'):'tail3',('t3','t4'):'tail3',('t4','t5'):'tail3'}
objs=[skinpart('rumpf',T,TE,'pel')]
SEG={'rumpf':[(V(T[a][0]),V(T[b][0]),TB[(a,b)]) for a,b in TE]}
# ---------- Arme (Vorderbeine) und Hinterbeine ----------
LIMB={}
for s,x in (('L',1),('R',-1)):
  A={'sh':((x*0.058,-0.028,0.628),.04),'el':((x*0.1,-0.07,0.52),.031),'wr':((x*0.098,-0.13,0.46),.024),'pw':((x*0.096,-0.158,0.444),(.029,.025))}
  AE=[('sh','el'),('el','wr'),('wr','pw')]
  objs.append(skinpart('arm'+s,A,AE,'sh'))
  SEG['arm'+s]=[(V(A['sh'][0]),V(A['el'][0]),'arm.'+s),(V(A['el'][0]),V(A['wr'][0]),'fore.'+s),(V(A['wr'][0]),V(A['pw'][0]),'fore.'+s)]
  # Hinterbein: kräftige Keule, die in den Po übergeht, Knie nach vorn, Ferse nach hinten, langer flacher Fuß
  # Hinterbein bis zum Knöchel; der Fuß ist ein eigenes Teil, in dem das Bein-Ende verschwindet
  Lg={'hip':((x*0.032,0.02,0.31),.045),'th':((x*0.058,0.005,0.235),(.056,.058)),'kn':((x*0.067,-0.03,0.165),.043),
      'sm':((x*0.068,-0.006,0.105),.038),'an':((x*0.07,0.012,0.045),.033)}
  LE=[('hip','th'),('th','kn'),('kn','sm'),('sm','an')]
  objs.append(skinpart('bein'+s,Lg,LE,'hip'))
  Fu={'up':((x*0.07,0.015,0.065),.032),'he':((x*0.07,0.03,0.032),(.034,.03)),'mf':((x*0.071,-0.025,0.024),(.033,.024)),'to':((x*0.072,-0.08,0.02),(.031,.02))}
  objs.append(skinpart('fuss'+s,Fu,[('up','he'),('he','mf'),('mf','to')],'up'))
  Lg['to']=Fu['to']
  SEG['bein'+s]=[(V(Lg['hip'][0]),V(Lg['th'][0]),'root'),(V(Lg['th'][0]),V(Lg['kn'][0]),'thigh.'+s),(V(Lg['kn'][0]),V(Lg['sm'][0]),'shin.'+s),(V(Lg['sm'][0]),V(Lg['an'][0]),'shin.'+s)]
  LIMB[s]=(A,Lg)
  # kleine Zehen an Vorder- und Hinterpfoten
  for k in (-1,0,1):
    objs.append(ball('zeheA'+s,(x*0.096+k*0.013,-0.173,0.44),(.011,.012,.009),seg=6,kind='zehe',grp='P_zeheA'+s))
    objs.append(ball('zeheB'+s,(x*0.072+k*0.015,-0.1,0.017),(.012,.013,.009),seg=6,kind='zehe',grp='P_zeheB'+s))
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
    elif nm=='bein':col=BR
    elif nm=='fuss':col=mix(BR,CR,ss(-0.045,-0.085,c.y)*.85)
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
  bone('thigh.'+s,Lg['th'][0],Lg['kn'][0],'root');bone('shin.'+s,Lg['kn'][0],Lg['an'][0],'thigh.'+s)
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
fixed={'P_fussL':'foot.L','P_fussR':'foot.R','P_ohr':'head','P_auge':'head','P_glanz':'head','P_nase':'head','P_zeheAL':'fore.L','P_zeheAR':'fore.R','P_zeheBL':'foot.L','P_zeheBR':'foot.R'}
gname={g.index:g.name for g in body.vertex_groups}
for v in body.data.vertices:
  part=None
  for ge in v.groups:
    n=gname[ge.group]
    if n.startswith('P_'):part=n
  if part in fixed:ws={fixed[part]:1.0}
  elif part and part[2:] in SEG:
    ds=sorted(((segd(v.co,a,b),n) for a,b,n in SEG[part[2:]]))[:3];ws={}
    for d,n in ds:ws[n]=ws.get(n,0)+1/(d+6e-3)**3
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
def druecken(f,ph):
  k=ss(0,10,f)*ss(24,14,f)                       # 0 -> 1 (gedrückt, Bild 10-14) -> 0
  d=base(f,ph,bob=0,tail=10,nod=0)
  d[('root','rotation_euler')]=(R(4+22*k),0,0);d[('spine','rotation_euler')]=(R(22*k),0,0);d[('neck','rotation_euler')]=(R(-18*k),0,0)
  d[('head','rotation_euler')]=(R(-10*k),0,0)
  for s,x in (('L',1),('R',-1)):d[('arm.'+s,'rotation_euler')]=(R(30+45*k),0,R(x*-8*k));d[('fore.'+s,'rotation_euler')]=(R(55-45*k),0,0)
  key(f,d);keyobj(f)
acts.append(dance('druecken',24,druecken))
# ---------- weitere Tanzschritte ----------
def armpose(d,s,ax,az,fx):d[('arm.'+s,'rotation_euler')]=(R(ax),0,R(az if s=='L' else -az));d[('fore.'+s,'rotation_euler')]=(R(fx),0,0)
# Roboter: steife Posen, die auf jeden Schlag springen
ROB=[(80,10,90, 30,10,10),(30,10,10, 80,10,90),(90,60,0, 90,60,0),(140,20,10, 20,5,90)]
def roboter(f,ph):
  b=int(f//12)%4;fr=f%12;q=ss(0,3,fr)                     # schnell in die neue Pose, dann stillhalten
  p0=ROB[(b-1)%4];p1=ROB[b];m=[p0[i]+(p1[i]-p0[i])*q for i in range(6)]
  d=base(f,ph,bob=.02,tail=6,nod=0)
  armpose(d,'L',m[0],m[1],m[2]);armpose(d,'R',m[3],m[4],m[5])
  d[('head','rotation_euler')]=(0,R([20,-20,0,0][b])*q+R([0,20,-20,0][b])*(1-q),0)
  d[('spine','rotation_euler')]=(0,R([10,-10,0,0][b]),0)
  key(f,d);keyobj(f)
acts.append(dance('roboter',48,roboter))
# Rasensprenger: eine Pfote hinterm Kopf, der andere Arm tickt zur Seite
def sprinkler(f,ph):
  b=f/12;tick=ss(0,2,f%6)                                     # 8 Ticks
  d=base(f,ph,bob=.03,tail=15)
  armpose(d,'L',140,55,110)
  side=(int(f//6)%8)/7.0 if f<48 else 1-((int((f-48)//6)%8)/7.0)
  d[('spine','rotation_euler')]=(0,R(-35+70*side),0)
  armpose(d,'R',95,0,5)
  key(f,d);keyobj(f)
acts.append(dance('sprinkler',96,sprinkler))
# Klatschen über dem Kopf, auf jeden Schlag
def klatschen(f,ph):
  c=(1+math.cos(2*ph))/2                                      # 1 = Pfoten zusammen
  d=base(f,ph,bob=.04)
  for s in 'LR':armpose(d,s,118,40*(1-c)-6*c,25)
  d[('head','rotation_euler')]=(R(6),0,0)
  key(f,d);keyobj(f)
acts.append(dance('klatschen',24,klatschen))
# Lasso: ein Arm kreist über dem Kopf, Hüfte schwingt
def lasso(f,ph):
  d=base(f,ph,bob=.03,sway=.02*math.sin(ph))
  d[('root','rotation_euler')]=(R(4),0,R(7)*math.sin(ph))
  d[('arm.R','rotation_euler')]=(R(145+12*math.sin(2*ph)),R(0),R(-45-25*math.cos(2*ph)))
  d[('fore.R','rotation_euler')]=(R(30+20*math.sin(2*ph)),0,0)
  armpose(d,'L',40,15,60)
  key(f,d);keyobj(f)
acts.append(dance('lasso',24,lasso))
# Luftgitarre: linke Pfote hält den Hals, rechte schrammelt, Kopf nickt kräftig
def gitarre(f,ph):
  d=base(f,ph,bob=.045,nod=18)
  armpose(d,'L',70,45,20)
  d[('arm.R','rotation_euler')]=(R(45),0,R(-5));d[('fore.R','rotation_euler')]=(R(70+30*math.sin(4*ph)),0,0)
  d[('spine','rotation_euler')]=(R(8),R(12),0)
  key(f,d);keyobj(f)
acts.append(dance('gitarre',24,gitarre))
# SPEZIAL 1 – Trucker: 4 Schläge lenken, dann zweimal an der Hupe ziehen
def trucker(f,ph):
  d=base(f,ph,bob=.035,tail=18)
  if f<48:
    w=math.sin(ph/2)                                            # Lenkrad hin und her
    armpose(d,'L',75+18*w,10,55);armpose(d,'R',75-18*w,10,55)
    d[('spine','rotation_euler')]=(R(6),R(-18*w),R(6*w))
    d[('head','rotation_euler')]=(R(-4),R(10*w),0)
  else:
    g=f-48;pull=max(0,math.sin((g%24)/24*math.pi*2))         # zweimal kräftig ziehen
    up=ss(0,6,g)
    d[('arm.R','rotation_euler')]=(R(75+(165-75)*up-50*pull),0,R(-10));d[('fore.R','rotation_euler')]=(R(55*(1-up)+15*pull),0,0)
    armpose(d,'L',40,20,70)
    d[('root','location')]=(0,-.03*pull,0);d[('head','rotation_euler')]=(R(-14*pull),0,0)
    d[('spine','rotation_euler')]=(R(10*pull),0,0)
  key(f,d);keyobj(f)
acts.append(dance('trucker',96,trucker))
# SPEZIAL 2 – Propeller: Pfoten in die Hüften, der Schwanz dreht Kreise
def propeller(f,ph):
  d=base(f,ph,bob=.03,tail=0)
  for s in 'LR':armpose(d,s,25,40,110)
  a2=ph*2
  d[('tail1','rotation_euler')]=(R(-35)+R(25)*math.cos(a2),0,R(35)*math.sin(a2))
  d[('tail2','rotation_euler')]=(R(15)*math.cos(a2-.5),0,R(30)*math.sin(a2-.5))
  d[('tail3','rotation_euler')]=(R(15)*math.cos(a2-1),0,R(30)*math.sin(a2-1))
  d[('root','rotation_euler')]=(R(4),0,R(5)*math.sin(a2))
  d[('head','rotation_euler')]=(R(-6),R(20)*math.sin(ph/2),0)
  key(f,d);keyobj(f)
acts.append(dance('propeller',96,propeller))
# Ruhe-Haltung als Standard
rig.animation_data.action=acts[0]
bpy.ops.wm.save_as_mainfile(filepath='/home/claude/wiesel/wiesel.blend')
print('ok',len(body.data.polygons),'Flächen')
