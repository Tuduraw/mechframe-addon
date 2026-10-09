from preview_set import build, show_assembly
from mg import export
import parts_kf, parts_weapons
B = build(parts_kf.ALL + [parts_weapons.gunpod, parts_weapons.missile_pod])
for pid,(m,meta) in B.items(): print(export(pid,m,meta))
parts = dict(legs=B["lg_falcon"], core=B["cr_falcon"], head=B["hd_falcon"], arms=B["am_falcon"], booster=B["bs_falcon"], r_arm=B["wa_gunpod"], l_arm=B["wa_gunpod"], l_back=B["wb_missile"])
print(show_assembly("falcon", parts))
print(show_assembly("falcon", parts, form="jet", views=("iso","iso_rear","left","front","top","iso_below")))
