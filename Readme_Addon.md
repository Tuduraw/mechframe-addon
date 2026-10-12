# Mech Frame アドオン作成ガイド

Mech Frame(tudursvehiclemod のロボットアドオン)に、パーツ・標準機・製作台のレシピを追加する「拡張パック」の作り方です。
Java のコードは不要で、JSON・OBJ・PNG・武器設定ファイル(.txt)だけで作れます。
同梱の拡張パック1「Frontier Frames」(`packs/mechframe_expansion1`、名前空間 `mfx`)が、このガイドどおりに作った完全な実例です。

- 前提: tudursvehiclemod(作者 Tuduraw 氏)、Mech Frame 0.6.0 以降。
- 前提MOD自体のアドオン仕様(武器設定・HUD・音声など)は、前提MODの `Readme_Addon.md` / `Readme_Weapon*.md` を参照してください。このガイドは Mech Frame 固有の部分を扱います。

---

## 1. パックの構成

```
tudursvehiclemod-addons/
  <パック名>/
    README.md                                  (任意。パックの説明)
    assets/<名前空間>/
      models/obj/parts/<パーツID>.obj           パーツのモデル
      textures/vehicle/part/<パーツID>.png      パーツのテクスチャ(必ず textures/vehicle/ の下)
      weapons/<武器名>.txt                      新しい射撃武器の設定(前提MODの形式)
    data/<名前空間>/
      mech_parts/<パーツID>.json                パーツの定義(+製作レシピ)
      vehicles/<機体ID>.json                    標準機(スポナーで出せる完成機)
      mech_recipe/<名前>.json                   製作台のレシピ(パーツJSONに書かない場合)
```

- **名前空間**はパック専用のものを決めます(例: `mfx`)。`mechframe` や他のパックと同じにしないでください。
- **パーツID**は `<名前空間>:<ファイル名>` です(`data/mfx/mech_parts/lg_wisp.json` → `mfx:lg_wisp`)。
- **テクスチャは必ず `assets/<名前空間>/textures/vehicle/` の下**に置きます。前提MODはアドオンフォルダのテクスチャをここしか読み込まないため、ほかの場所に置くと紫と黒の市松模様になります。
- 前提MODは実行中にパックの中へ `tudursvehiclemod-generated/` フォルダ(キャッシュ)を作ります。**配布するときは除いてください。**
- 同じ構成でデータパック・MOD の jar にも入れられます(`data/` はデータパック、`assets/` はリソースパックとして)。ただし通常はアドオンフォルダが簡単です。
- ゲーム内で `/reload` すると、パーツ・標準機・レシピが再読み込みされ、接続中の全員に送られます。モデルやテクスチャを変えたときは、ゲームの再起動が確実です。

---

## 2. 座標と単位

- 1単位 = 1ブロック = 1 m。
- **+X = 左、+Y = 上、+Z = 前**(機体の左右は機体から見た向き)。
- 各パーツは「自分の取り付け点が原点」になるようにモデルを作ります(次の表)。

| パーツ | 原点 | 取り付け点(`points`) |
|---|---|---|
| 脚部 `legs` | 機体の真下の地面 | `hip`:コアの原点を置く位置 |
| コア `core` | 股の関節(脚の `hip` に置かれる) | `neck`(頭)、`shoulder_l`(左肩。右は左右反転、または `shoulder_r`)、`back`(ブースター)、`back_l`(左肩の武器。右は反転、または `back_r`)、`seat`(操縦者)、`eye`(一人称カメラ)、変形機は任意で `jet_eye` |
| 頭部 `head` | 首の関節 | `top`(頭頂。機体の高さの計算に使う) |
| 腕部 `arms` | 左腕・右腕とも肩の関節(1つのOBJに両腕) | `elbow`、`grip`(手。左腕の値を書けば右は反転) |
| ブースター `booster` | コアの `back` | (なし) |
| 武器 `weapon` | 握り(腕)または取り付け台(肩) | `muzzle`:銃口(弾が出る位置) |

---

## 3. モデル(OBJ)

- 前提MODと同じ OBJ(三角形または凸多角形、UV・法線つき、マテリアルなし)。**背面カリング**で描かれるので、すべての形を閉じた外向きの立体にしてください(穴や裏返った面は透けて見えます)。
- `o` / `g` の名前が**グループ名**で、動く単位になります。パーツごとに決まった名前を使います。

| パーツ | グループ名 |
|---|---|
| 頭部 | 何でも可(全体が首で回ります)。慣例は `head` |
| コア | 何でも可(全体が一体)。変形機は `core` を必ず含める(戦闘機形態の基準) |
| 腕部 | 左腕は `l_` で始まる名前、右腕は `r_` で始まる名前(例 `l_upper`、`l_fore`)。それ以外の名前のグループは描かれません。変形機は前腕を `l_fore` / `r_fore` にする(武器はここに付く) |
| 脚部 | 骨盤(`stats.pelvis_groups` に列挙)、各脚の腿・すね・足(`chains` に列挙)、タンクの転輪(`wheels` に列挙) |
| ブースター | 何でも可。変形機の翼など、動かす物は別グループに |
| 武器 | 何でも可(全体が一体で照準方向へ向きます) |

- 武器は「前(+Z)を向いた」状態で作ります。腕の武器は機体の照準方向へ、肩の武器は肩の上で旋回して向きます。
- **三角形の目安**:武器を含めて1機 5000 以下(標準機・拡張パック1の基準)。細い部材は3〜4角柱、関節の軸は6〜8角柱で十分です。
- **頭部**は、一人称で搭乗している本人には描かれません(カメラの直前にあるため)。

## 4. テクスチャ

- PNG(RGBA)。大きさは 128〜512 px 程度。半透明はアルファで表せます(前提MODの設定により4×4のディザ)。
- パーツJSONの `texture` に `"<名前空間>:textures/vehicle/part/<パーツID>.png"` のように**必ずフルパスで**書きます(省略時の既定値は `textures/part/` で、アドオンフォルダでは読み込まれません)。

---

## 5. パーツJSON(`data/<ns>/mech_parts/<パーツID>.json`)

### 共通のキー

| キー | 内容 | 既定値 |
|---|---|---|
| `slot` | `head` / `core` / `arms` / `legs` / `booster` / `weapon`(必須) | — |
| `display_name` | 表示名(そのまま表示。翻訳ファイル不要) | ファイル名 |
| `maker` | メーカー名(ガレージ・ツールチップ・製作台で表示) | "" |
| `tier` | 1〜5。製作台の並び順など | 1 |
| `model` | `"<ns>:models/obj/parts/<ID>.obj"` | 左と同じ |
| `texture` | `"<ns>:textures/vehicle/part/<ID>.png"`(必ず書く) | `<ns>:textures/part/<ID>.png` |
| `weight` | 重量 | 5 |
| `armor` | 耐久(AP)。全パーツの合計が機体のAP | 100 |
| `points` | 取り付け点(2章) | — |
| `stats` | パーツ種別ごとの性能(下記) | — |
| `transform_set` | 変形セット名(7章) | なし |
| `jet_pose` | 戦闘機形態の配置(7章) | なし |
| `recipe` | 製作台のレシピ(6章) | なし |

知らないキーは無視され、値が無い・型が違うキーは既定値になります(ログに出ないことがあるので、綴りに注意してください)。

### 性能の考え方

- **重量と積載**:脚部以外の全パーツ(武器を含む)の重量の合計が、脚部の `load_limit` を超えると、全速度が最大 65% まで落ち、クイックブーストのEN消費が増えます。腕の武器2つの重量が腕部の `load_limit` を超えると照準が遅くなります。
- 速度は「ブロック/tick」(1 = 時速72km)。
- AP は全パーツの `armor` の合計。

### 頭部 `stats`

| キー | 内容 | 既定値 |
|---|---|---|
| `lock_range` | ロックオン距離(ブロック) | 110 |
| `lock_cone` | ロックできる範囲(視線からの角度、度) | 14 |
| `stability` | 安定性(コアの値と掛け合わせ、加減速での傾き・着地の揺れを抑える。1が標準) | 1 |

### コア `stats`

| キー | 内容 | 既定値 |
|---|---|---|
| `en_capacity` | EN容量 | 1500 |
| `en_recharge` | EN回復量/tick | 30 |
| `recharge_delay` | EN回復が始まるまでのtick | 12 |
| `defense` | 防御倍率 | 1 |
| `fuel` | 燃料 | 800 |
| `stability` | 安定性 | 1 |
| `jet` | 変形機のみ:戦闘機形態の性能 `{max_speed, acceleration, turn_speed, stall_fraction, afterburner, afterburner_cost, collision_height}` | — |

### 腕部 `stats`

| キー | 内容 | 既定値 |
|---|---|---|
| `load_limit` | 腕の武器2つの積載上限 | 20 |
| `aim_speed` | 照準速度(度/秒) | 360 |
| `recoil_control` | 反動の抑え | 1 |
| `integrated` | 武器内蔵腕(特殊規格):`{"r_arm": "<武器パーツID>", "l_arm": "..."}`。その箇所はこの武器に固定され、ほかの武器は付けられない | なし |

### 脚部 `stats`

| キー | 内容 | 既定値 |
|---|---|---|
| `type` | `biped`(二脚)/ `reverse_joint`(逆関節)/ `quad`(四脚)/ `hexapod`(六脚)/ `tank` / `hover` | biped |
| `load_limit` | 積載上限 | 60 |
| `walk_speed` | 歩行速度 | 0.3(タンク 0.42) |
| `boost_multiplier` | ブースト速度の倍率 | 1 |
| `jump` | ジャンプ力(タンク・ホバーは0) | 0.95(タンク 0) |
| `turn_rate` | 旋回速度(度/tick) | 14 |
| `step_height` | 乗り越えられる段差 | 1.2 |
| `traction` | 接地の効き(小さいほど滑る) | 0.35 |
| `qb_multiplier` / `hover_multiplier` | クイックブースト・ホバーの倍率 | 1 |
| `collision_width` | 当たり判定の幅 | 1.8 |
| `step_length` / `step_lift` / `step_ticks` | 一歩の長さ・足を上げる高さ・一歩のtick数 | 1.4 / 0.5 / 6 |
| `core_tilt` | コアの前傾(度) | 0 |
| `pelvis_groups` | 骨盤として胴と一緒に動くグループ名の配列(タンクは車体上の旋回リングなど) | — |
| `chains` | 脚の配列(歩く脚のみ。下記) | — |
| `wheels` | タンクの転輪の配列 `{group, pivot, radius}`(回転するグループ、回転中心、半径) | — |

`chains` の1本:

| キー | 内容 |
|---|---|
| `name` | 脚の名前(例 `l`、`fl`) |
| `upper` / `lower` / `foot` | 腿・すね・足のグループ名(`foot` は任意) |
| `hip` / `knee` / `ankle` | 静止時の股・膝・足首の位置(脚部のモデル空間)。脚はこの3点でIK(足が地面に着く)で動く |
| `pole` | 膝が曲がる向き(二脚は `[0,0,1]` = 前、多脚は `[0,1,0]` = 上) |
| `group` | 歩くときの組(二脚は 0 と 1、四脚は対角で組、六脚は3本ずつ) |
| `phase` | 組の中でのずれ(任意) |
| `coxa` / `coxa_pivot` | 多脚:胴と股の間で縦軸に回る付け根のグループと回転中心(任意) |
| `mid` / `knee2` | 逆関節:中間節のグループと第2関節の位置(任意) |

- 膝には静止時に 20〜40 度の曲がりを付けてください(真っ直ぐだと曲がる向きが決まりません)。
- 関節の軸(円柱)は子の側のグループに入れ、親の端を軸に食い込ませると、曲げても隙間が出ません。

### ブースター `stats`

| キー | 内容 | 既定値 |
|---|---|---|
| `boost_speed` | ブースト速度 | 0.9 |
| `qb_speed` / `qb_cost` / `qb_cooldown` | クイックブーストの速度・EN消費・間隔(tick) | 2.4 / 320 / 10 |
| `hover_thrust` / `hover_cost` | ホバーの推力・EN消費 | 0.16 / 14 |
| `air_boost_cost` | 空中ブーストのEN消費 | 6 |
| `nozzles` | 噴射炎 `[{pos, dir, size}]`(ブースターのモデル空間) | — |
| `jet_nozzles` | 変形機:戦闘機形態の噴射炎(戦闘機形態の機体空間) | — |

### 武器 `stats`

| キー | 内容 | 既定値 |
|---|---|---|
| `weapon` | 武器設定ファイルの名前(`assets/<ns>/weapons/<名前>.txt`、拡張子なし) | パーツID |
| `mount` | `arm`(腕)/ `back`(肩)/ `any`(どちらも)/ `integrated`(内蔵腕専用) | any |
| `projectile_item` | 補給に使うアイテム | minecraft:iron_nugget |
| `recoil_distance` | 発射時に後退する距離 | 0.1 |
| `min_yaw` / `max_yaw` / `min_pitch` / `max_pitch` | 肩の武器の旋回・俯仰範囲(度) | -75 / 75 / -80 / 60 |
| `fcs` | FCS(ロックオン先へ照準を補正する)を使うか | true |
| `melee` | 近接武器(下記)。書くと射撃ではなく近接武器になる(`weapon` の .txt は不要) | なし |

`melee`:

| キー | 内容 | 既定値 |
|---|---|---|
| `damage` | 威力 | 300 |
| `reach` | 届く距離 | 4.5 |
| `arc` | 当たる範囲(正面からの角度、度) | 70 |
| `lunge_speed` / `lunge_range` | 踏み込みの速さ・ロック目標へ踏み込む最大距離 | 1.5 / 11 |
| `en_cost` / `cooldown` | EN消費・間隔(tick) | 300 / 28 |
| `style` | `slash`(横に斬る)/ `thrust`(真っすぐ突く) | slash |

- 射撃武器の性能(威力・弾数・弾速・音など)は、前提MODの武器設定ファイル(.txt)に書きます(前提MODの `Readme_Weapon.md`)。**同じ名前の武器ファイルは全パックで1つの定義**として扱われるので、名前には名前空間などの接頭辞を付けてください(例 `mfx_arbalest_rail`)。
- 本体の武器パーツ(`mechframe:wa_rifle` など)は、そのまま標準機に使えます。

---

## 6. 製作台のレシピ

パーツは「メック製作台」で作ります。レシピはパーツJSONの `recipe` に書くか、`data/<ns>/mech_recipe/<名前>.json` に置きます(同じIDのファイルがあれば、ファイルが優先)。

```json
"recipe": {
  "ingredients": [
    {"item": "minecraft:iron_ingot", "count": 6},
    {"tag": "minecraft:planks", "count": 2},
    {"part": "mechframe:cr_swift"}
  ]
}
```

| キー | 内容 |
|---|---|
| `ingredients` | 材料の配列。各要素は `item`(アイテムID)/ `tag`(アイテムタグ)/ `part`(パーツのアイテム)のどれか1つと `count`(既定1) |
| `result` | ファイルのレシピでは必須:`{"part": "<パーツID>"}` または `{"item": "<アイテムID>", "count": n}`。パーツJSON内のレシピでは省略(そのパーツ1個) |
| `category` | 製作台の一覧の分類(既定はパーツ種別。ほかに `frames`、`items` や任意の名前) |
| `order` | 分類の中の並び順(既定はTier) |

- 存在しないアイテム・パーツを材料にしたレシピは読み込まれません(ログに警告)。
- レシピを書かないパーツは製作台に出ません(内蔵武器のように単体で作らせないパーツ向け)。
- 拡張パック1は「Tierの素材+メーカーの素材+箇所ごとの追加材料」で決めています(パックの README 参照)。

---

## 7. 変形機(変形セット)

頭部・コア・腕部・脚部・ブースターの5つが同じ `transform_set` で、コアに `stats.jet` があり、5つすべてに `jet_pose` があると、変形キーで戦闘機形態になれます。

`jet_pose` は、パーツのグループごとの戦闘機形態での配置です。

```json
"jet_pose": {
  "head": {"pos": [0, 1.13, 2.28], "quat": [0, 0, 1, 0], "delay": 0.2,
           "parent": "core:core", "joint": [0, 0, 0]}
}
```

| キー | 内容 |
|---|---|
| `pos` | 戦闘機形態の機体空間での位置(原点は機体の下の地面、+Zが機首) |
| `quat` または `rot` | 向き(四元数 [x, y, z, w]、または度での回転 [x, y, z]、Y·X·Z の順) |
| `delay` | 変形の中で動き出す時刻(0〜1。0.5なら半分過ぎてから) |
| `parent` / `joint` | 親グループ(`"箇所:グループ"`)と、このグループ側の関節位置。指定すると変形の途中もその関節でつながったまま動く |

- 手で数値を書くのは困難なので、スクリプトで関節の回転から計算するのが現実的です。同梱の `tools/mechgen/rig.py`(Python)と、拡張パック1の `parts_va_kite.py` などが実例です。
- 変形の途中(25% / 50% / 75% / 100%)で部品が離れて浮かないか、必ず確かめてください。
- 武器は腕の `l_fore` / `r_fore` と肩に付いたまま、機首方向へ向きます。

---

## 8. 標準機(`data/<ns>/vehicles/<機体ID>.json`)

前提MODの車両JSONに、Mech Frame 用の `mechframe` オブジェクトを加えたものです。スポナー(機体キット)の一覧に、`spawn_item.tier` のTierで追加されます。

```json
{
  "entity_type": "mechframe:mech",
  "model": "mfx:models/obj/parts/cr_filament.obj",
  "texture": "mfx:textures/vehicle/part/cr_filament.png",
  "width": 1.8, "height": 4.0,
  "spawn_item": {"display_name": "VA) Wisp", "tier": 2},
  "hud": "mech_hud",
  "engine_sound": "mech_engine",
  "engine_sound_volume": 1.2,
  "weight_type": "car",
  "max_health": 1000,
  "armor_damage_factor": 0.8,
  "armor_min_damage": 1.0,
  "armor_max_damage": 260,
  "damage_factor": 1.0,
  "fuel_consumption": 0.25,
  "inventory_size": 18,
  "submerged_damage_height": 3.0,
  "flare_type": 0,
  "passenger_display": {"hide_entity": true},
  "mechframe": {
    "assembly": {
      "head": "mfx:hd_pin", "core": "mfx:cr_filament", "arms": "mfx:am_reed",
      "legs": "mfx:lg_wisp", "booster": "mfx:bs_flare",
      "r_arm": "mechframe:wa_rifle", "l_arm": "mechframe:wa_blade"
    }
  }
}
```

- `entity_type` は `mechframe:mech`、`hud` は `mech_hud`。`model` / `texture` は前提MODの必須項目なので、コアのものを書いておきます(実際の見た目・当たり判定・AP・武器は `assembly` のパーツから組み立てられます)。
- `assembly` の箇所:`head`、`core`、`arms`、`legs`、`booster`(この5つ。コア・腕部・脚部は必須)と、武器 `r_arm`、`l_arm`、`r_back`、`l_back`(任意)。
- 変形機の標準機は `flare_type` を 1 にするとフレアを撒けます。
- 標準機の表示名は、英語でメーカーの略号を先頭に付けるのが本体・拡張パック1の慣例です(例 `VA) Wisp`)。

---

## 9. 特殊規格のパーツ

標準機を持たない、組み合わせで使うパーツも作れます。拡張パック1の Orrery Experimental が実例です。

- **ホバー脚**:`legs` の `type: "hover"`。脚の `chains` は不要で、全体が1つのスカートとして浮いて滑ります(ジャンプ不可)。
- **武器内蔵腕**:腕部の `stats.integrated` に、`mount: "integrated"` の武器パーツを指定します。その武器パーツには通常 `recipe` を付けません。
- **突き型の近接武器**:`melee.style: "thrust"`。
- 極端な性能のブースター・頭部などは、数値だけで作れます(例:`hover_thrust: 0` で空中ホバーなし、`lock_cone` を小さく `lock_range` を大きく)。

---

## 10. 確認の手順

1. パックを `tudursvehiclemod-addons/` に置いてゲームを起動します。
2. ログ(`logs/latest.log`)で `MechFrame` の行にエラーがないか確かめます。「Loaded … mech part(s)」「Loaded … mech fabricator recipe(s)」の数も確かめます。
3. クリエイティブのタブ、またはガレージ(`mechframe:garage`)でパーツを確かめ、組み替えます。
4. 製作台(`mechframe:fabricator`)で、レシピと材料の消費を確かめます。
5. 標準機をスポナーで出し、歩行・ブースト・旋回・射撃・(変形機は)変形を試します。
6. 一人称と三人称、他人から見た見た目を確かめます。

よくある問題:

| 症状 | 原因 |
|---|---|
| 紫と黒の市松模様 | テクスチャが `textures/vehicle/` の下にない、または `texture` のパスの誤り |
| 部品が透けて見える | 面の向きが裏返っている、閉じていない形 |
| 腕が描かれない | 腕部のグループ名が `l_` / `r_` で始まっていない |
| 脚が伸びたまま・逆に曲がる | 膝の休止時の曲がりがない、`pole` の向き |
| 機体として組めない | コア・腕部・脚部のどれかがない、脚部に `stats` がない |
| 変形しない | 5つの `transform_set` が揃っていない、コアに `stats.jet` がない、`jet_pose` のないパーツがある |
| レシピが出ない | 材料のアイテムIDの誤り、`result` のパーツが読み込まれていない |

---

## 11. 生成ツール(任意)

本体と拡張パック1のモデル・テクスチャ・JSONは、Python のスクリプトで生成しています。手作業のモデリングの代わりに使えます。

- `tools/mechgen/`:本体のパーツ生成(`mg.py` が共通の関数と書き出し)。
- `tools/mechgen/packs/mfx/`:拡張パック1の生成(`build_pack.py` が全体を作り直す)。新しいパックはこのフォルダを写して作れます。
- **meshkit**(別配布):ロボット用の形状の関数(`meshkit.mech`)と、デザインの考え方(`AI_GUIDE.md` の7章:コンセプトからの形の決め方、脚の種類別の作り方、変形、三角形の予算、検査)。

---

## 12. 権利とクレジット

- 前提MOD tudursvehiclemod の作者は Tuduraw 氏です。前提MODのガイドライン(`GUIDELINES.md`)に従い、他者が権利を持つデータ(モデル・テクスチャ・音声)は使わないでください。
- 本体の武器パーツ・効果音・弾のモデルは、標準機や自作パーツから参照して使えます(ファイルをコピーして再配布する必要はありません)。
