import sys
from preview_set import build, show_assembly
from mg import export
import parts_kd, parts_gw, parts_ar, parts_weapons

built = build(parts_kd.ALL + parts_gw.ALL + parts_ar.ALL + parts_weapons.ALL)
for pid, (m, meta) in built.items():
    r = export(pid, m, meta)
    print(r)
B = built
combos = {
 "striker": dict(legs="lg_stride", core="cr_swift", head="hd_lark", arms="am_talon", booster="bs_zephyr", r_arm="wa_rifle", l_back="wb_missile"),
 "hopper": dict(legs="lg_hopper", core="cr_swift", head="hd_lark", arms="am_talon", booster="bs_zephyr", r_arm="wa_rifle", l_arm="wa_rifle"),
 "bastion": dict(legs="lg_pillar", core="cr_bastion", head="hd_visor", arms="am_maul", booster="bs_thrust", r_arm="wa_mg", l_arm="wa_bazooka", r_back="wb_cannon", l_back="wb_missile"),
 "rampart": dict(legs="lg_rampart", core="cr_bastion", head="hd_visor", arms="am_maul", booster="bs_thrust", r_arm="wa_bazooka", r_back="wb_cannon", l_back="wb_cannon"),
 "weaver": dict(legs="lg_weaver", core="cr_thorax", head="hd_ocellus", arms="am_mandible", booster="bs_glide", r_arm="wa_laser", l_back="wb_gatling"),
 "strider": dict(legs="lg_strider", core="cr_thorax", head="hd_ocellus", arms="am_mandible", booster="bs_glide", r_arm="wa_laser", l_arm="wa_mg", r_back="wb_gatling", l_back="wb_missile"),
}
only = sys.argv[1:] or list(combos)
for name in only:
    parts = {k: B[v] for k, v in combos[name].items()}
    print(name, show_assembly(name, parts))
