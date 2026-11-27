import bpy
bpy.ops.wm.open_mainfile(filepath='/home/claude/wiesel/wiesel.blend')
bpy.ops.export_scene.gltf(filepath='/home/claude/wiesel/wiesel.glb',export_format='GLB',export_animations=True,export_animation_mode='ACTIONS',
  export_force_sampling=True,export_apply=True,export_yup=True,export_def_bones=False,export_vertex_color='MATERIAL')
