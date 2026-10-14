package com.example.mechframe.devtest;

import com.example.mechframe.MechFrame;
import com.example.mechframe.client.MechInput;
import com.example.mechframe.entity.MechEntity;
import com.example.mechframe.garage.AssemblyService;
import com.example.mechframe.garage.GarageBlockEntity;
import com.example.mechframe.item.MechItems;
import com.example.mechframe.item.MechPartItem;
import com.example.mechframe.part.MountSlot;
import com.example.mechframe.part.PartRegistry;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Development-only automated play test (not part of the release jar: only compiled with
 * -Pdevtest). Drives a singleplayer world through scripted steps - spawn frames, ride them, press
 * keys - logging physics and taking screenshots, then quits. */
public final class DevTest {

	private record Step(int at, Consumer<MinecraftClient> action) {
	}

	private static final List<Step> STEPS = new ArrayList<>();
	private static int tick = -1;
	private static PrintWriter log;
	private static MechEntity ridden;
	private static final java.util.Map<String, MechEntity> SPAWNED = new java.util.concurrent.ConcurrentHashMap<>();
	private static double lastX, lastZ;

	private DevTest() {
	}

	public static void init() {
		String scenario = System.getProperty("mechframe.devtest", "basic");
		try {
			log = new PrintWriter(new FileWriter("devtest.log"), true);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		switch (scenario) {
			case "falcon", "swallow", "manta", "mfx:kite", "mfx:corvus", "mfx:condor" -> transformer(scenario);
			case "legs" -> legs();
			case "tank" -> tank();
			case "gait" -> gait();
			case "turn" -> turn();
			case "hexboost" -> hexBoost();
			case "qbside" -> qbSide();
			case "pack" -> pack();
			case "special" -> special();
			case "melee" -> melee();
			case "fpv" -> fpv();
			case "keys" -> keys();
			case "ai" -> ai();
			case "aiesc" -> aiEscort();
			case "aires" -> aiRespawn();
			case "aiblock" -> aiBlock();
			case "turbo" -> turbo();
			case "garage" -> garage();
			default -> basic();
		}
		ClientTickEvents.END_CLIENT_TICK.register(DevTest::tick);
	}

	private static void at(int t, Consumer<MinecraftClient> action) {
		STEPS.add(new Step(t, action));
	}

	private static void server(MinecraftClient c, String... commands) {
		var server = c.getServer();
		server.execute(() -> {
			for (String cmd : commands) {
				server.getCommandManager().parseAndExecute(server.getCommandSource(), cmd);
			}
		});
	}

	private static void spawn(MinecraftClient c, String preset, double dx, double dz, float yaw, boolean ride) {
		var server = c.getServer();
		server.execute(() -> {
			ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
			ServerWorld world = (ServerWorld) player.getEntityWorld();
			MechEntity mech = MechFrame.MECH.create(world, SpawnReason.COMMAND);
			mech.refreshPositionAndAngles(player.getX() + dx, player.getY(), player.getZ() + dz, yaw, 0f);
			mech.setVehicleDefinitionId(preset.contains(":") ? Identifier.of(preset) : Identifier.of("mechframe", preset));
			world.spawnEntity(mech);
			mech.tudursvehiclemod$fillAllWeaponAmmoAndFuel();
			SPAWNED.put(preset, mech);
			if (ride) {
				player.startRiding(mech, true, true);
			}
			log.println("spawned " + preset + " id=" + mech.getId() + " asm=" + mech.getAssembly().encode()
					+ " hp=" + mech.getHealth() + "/" + mech.getMaxHealth());
		});
	}

	/** A shot from the frame's right side: an invisible armour stand is moved 9 m to the right of the
	 * frame (aheadZ further along +Z for fast movers), the view switched to it for one frame. */
	private static void sideShot(int t, String name, double aheadZ) {
		camShot(t, name, -7.5, 2.2, aheadZ, -90f, 8f);
	}

	/** A shot from straight above the frame. */
	private static void topShot(int t, String name) {
		camShot(t, name, 0.0, 12.0, 0.0, 0f, 90f);
	}

	private static void camShot(int t, String name, double dx, double dy, double dz, float yaw, float pitch) {
		at(t, c -> {
			MechEntity m = MechInput.pilotedMech(c);
			if (m == null) {
				return;
			}
			double x = m.getX() + dx, y = m.getY() + dy, z = m.getZ() + dz;
			server(c, "kill @e[type=minecraft:armor_stand,tag=dtcam]",
					String.format(Locale.ROOT, "summon minecraft:armor_stand %.2f %.2f %.2f {Tags:[\"dtcam\"],NoGravity:1b,Invisible:1b,Marker:1b,Rotation:[%.1ff,%.1ff]}", x, y, z, yaw, pitch));
		});
		at(t + 2, c -> {
			for (net.minecraft.entity.Entity e : c.world.getEntities()) {
				if (e instanceof net.minecraft.entity.decoration.ArmorStandEntity) {
					c.setCameraEntity(e);
				}
			}
		});
		at(t + 3, c -> shot(c, name));
		at(t + 4, c -> c.setCameraEntity(c.player));
	}

	private static void shot(MinecraftClient c, String name) {
		ScreenshotRecorder.saveScreenshot(c.runDirectory, "dt_" + name + ".png", c.getFramebuffer(), 1, text -> {
		});
		log.println("shot " + name + " @" + tick);
	}

	private static void key(KeyBinding key, boolean down) {
		key.setPressed(down);
	}

	private static void tap(KeyBinding key) {
		KeyBinding.onKeyPressed(key.getDefaultKey());
	}

	private static void common() {
		at(1, c -> server(c, "time set 6000", "weather clear", "gamerule doDaylightCycle false", "gamerule doMobSpawning false",
				"gamemode creative @a", "kill @e[type=minecraft:slime]", "tp @a 0 -60 0 0 0"));
		at(Integer.getInteger("mechframe.devtest.end", 520), c -> c.scheduleStop());
	}

	private static void basic() {
		common();
		String[] presets = {"striker", "hopper", "rampart", "bastion", "weaver", "strider", "falcon", "swallow", "manta"};
		at(20, c -> {
			for (int i = 0; i < presets.length; i++) {
				spawn(c, presets[i], (i - 4) * 7.0, 22, 180f, false);
			}
			server(c, "tp @a 0 -55 -6 0 12");
		});
		at(140, c -> shot(c, "lineup"));
		at(150, c -> server(c, "tp @a 20 -55 30 140 20"));
		at(175, c -> shot(c, "lineup_back"));
		at(185, c -> {
			server(c, "tp @a 0 -60 -30 0 0");
			spawn(c, "striker", 0, 3, 0f, true);
		});
		at(200, c -> {
			c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			ridden = MechInput.pilotedMech(c);
			log.println("riding " + ridden);
		});
		at(225, c -> shot(c, "ride_idle"));
		at(230, c -> key(c.options.forwardKey, true));
		at(250, c -> shot(c, "walk"));
		at(260, c -> tap(MechInput.boostKey));
		at(280, c -> shot(c, "boost"));
		at(290, c -> {
			tap(MechInput.boostKey);
			key(c.options.forwardKey, false);
		});
		at(293, c -> shot(c, "brake"));
		at(310, c -> {
			key(c.options.leftKey, true);
			tap(MechInput.quickBoostKey);
		});
		at(312, c -> shot(c, "quickboost"));
		at(320, c -> key(c.options.leftKey, false));
		at(330, c -> key(c.options.jumpKey, true));
		at(360, c -> shot(c, "hover"));
		at(370, c -> key(c.options.jumpKey, false));
		at(386, c -> shot(c, "falling"));
		at(400, c -> shot(c, "landed"));
		at(410, c -> c.options.setPerspective(Perspective.FIRST_PERSON));
		at(420, c -> shot(c, "first_person"));
	}

	/** Defence AI: an outpost's enemy unit engages the player, a friendly frame on the command terminal
	 * guards and then escorts, the enemy unit is destroyed (parts drop) and redeployed with a new frame. */
	private static void ai() {
		common();
		at(5, c -> {
			com.example.mechframe.ai.MechServerConfig.respawnMinutes = 0.1;
			com.example.mechframe.ai.MechServerConfig.partDropChance = 1.0;
			com.example.mechframe.ai.MechServerConfig.targetCreativePlayers = true;
		});
		at(20, c -> server(c, "place structure mechframe:outpost 0 -60 18", "tp @a 0 -60 -4 0 10"));
		at(60, c -> aiLog(c, "t60", true));
		at(90, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
		at(130, c -> { aiLog(c, "t130", false); shot(c, "ai_outpost"); });
		at(200, c -> aiLog(c, "t200", false));
		// friendly frame on the command terminal: guard, then wingman; zombies to fight
		at(210, c -> {
			spawn(c, "striker", -6, -2, 0f, false);
			server(c, "summon minecraft:zombie -14 -60 -14", "summon minecraft:zombie -16 -60 -10", "summon minecraft:husk -10 -60 -18");
		});
		at(214, c -> c.getServer().execute(() -> {
			ServerPlayerEntity p = c.getServer().getPlayerManager().getPlayerList().get(0);
			p.setStackInHand(Hand.MAIN_HAND, new net.minecraft.item.ItemStack(MechItems.COMMAND_TERMINAL));
			MechEntity m = SPAWNED.get("striker");
			log.println("terminal 1: " + m.interact(p, Hand.MAIN_HAND) + " mode=" + m.getBrain().mode + " faction=" + m.getFaction());
		}));
		at(300, c -> { aiLog(c, "t300", false); shot(c, "ai_friendly"); });
		at(310, c -> c.getServer().execute(() -> {
			ServerPlayerEntity p = c.getServer().getPlayerManager().getPlayerList().get(0);
			MechEntity m = SPAWNED.get("striker");
			log.println("terminal 2: " + m.interact(p, Hand.MAIN_HAND) + " mode=" + m.getBrain().mode);
		}));
		at(320, c -> server(c, "tp @a -40 -60 -40 0 10"));
		at(420, c -> aiLog(c, "t420 escort", false));
		// destroy the enemy unit: parts drop, then a new random unit is deployed
		at(430, c -> c.getServer().execute(() -> {
			ServerWorld w = c.getServer().getOverworld();
			for (MechEntity m : w.getEntitiesByClass(MechEntity.class, new net.minecraft.util.math.Box(-200, -100, -200, 200, 100, 200),
					m -> m.getFaction() == com.example.mechframe.ai.Faction.ENEMY)) {
				log.println("destroying " + m.getUuid() + " asm=" + m.getAssembly().encode());
				for (int i = 0; i < 40 && !m.tudursvehiclemod$isDestroyed(); i++) {
					m.damage(w, w.getDamageSources().generic(), 250f);
				}
				int drops = w.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, m.getBoundingBox().expand(6),
						it -> it.getStack().getItem() instanceof MechPartItem).size();
				log.println("destroyed=" + m.tudursvehiclemod$isDestroyed() + " part drops=" + drops);
			}
		}));
		at(700, c -> aiLog(c, "t700 after respawn", true));
	}

	/** Wingman: a frame put on escort follows its owner around (and catches up when left far behind). */
	private static void aiEscort() {
		common();
		at(20, c -> spawn(c, "striker", -6, -2, 0f, false));
		at(24, c -> c.getServer().execute(() -> {
			ServerPlayerEntity p = c.getServer().getPlayerManager().getPlayerList().get(0);
			p.setStackInHand(Hand.MAIN_HAND, new net.minecraft.item.ItemStack(MechItems.COMMAND_TERMINAL));
			MechEntity m = SPAWNED.get("striker");
			m.interact(p, Hand.MAIN_HAND);
			m.interact(p, Hand.MAIN_HAND);
			log.println("mode=" + m.getBrain().mode + " owner=" + m.getBrain().owner);
		}));
		at(40, c -> server(c, "tp @a -30 -60 -30 0 10"));
		for (int t = 50; t <= 250; t += 20) {
			int tt = t;
			at(t, c -> aiLog(c, "esc t" + tt, false));
		}
		at(260, c -> server(c, "tp @a 90 -60 90 0 10"));
		at(300, c -> aiLog(c, "esc after far tp", false));
		at(320, c -> { c.options.setPerspective(Perspective.THIRD_PERSON_BACK); });
		at(340, c -> shot(c, "ai_escort"));
	}

	/** An outpost unit destroyed: parts drop, the post redeploys a new random frame; no block damage. */
	private static void aiRespawn() {
		common();
		at(5, c -> {
			com.example.mechframe.ai.MechServerConfig.respawnMinutes = 0.1;
			com.example.mechframe.ai.MechServerConfig.partDropChance = 1.0;
			com.example.mechframe.ai.MechServerConfig.targetCreativePlayers = true;
		});
		at(20, c -> server(c, "place structure mechframe:fort 0 -60 14", "tp @a 0 -60 -4 0 10"));
		at(60, c -> aiLog(c, "t60", true));
		at(200, c -> aiLog(c, "t200", false));
		at(210, c -> c.getServer().execute(() -> {
			ServerWorld w = c.getServer().getOverworld();
			for (MechEntity m : w.getEntitiesByClass(MechEntity.class, new net.minecraft.util.math.Box(-200, -100, -200, 200, 100, 200),
					m -> m.getFaction() == com.example.mechframe.ai.Faction.ENEMY)) {
				for (int i = 0; i < 40 && !m.tudursvehiclemod$isDestroyed(); i++) {
					m.damage(w, w.getDamageSources().generic(), 250f);
				}
				log.println("destroyed " + m.getUuid().toString().substring(0, 8) + " -> " + m.tudursvehiclemod$isDestroyed());
			}
			log.println("part items dropped: " + w.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
					new net.minecraft.util.math.Box(-200, -100, -200, 200, 100, 200), it -> it.getStack().getItem() instanceof MechPartItem).size());
		}));
		at(260, c -> server(c, "kill @e[type=minecraft:item]"));
		at(420, c -> aiLog(c, "t420 after respawn", true));
		at(430, c -> server(c, "tp @a 22 -54 -10 0 10"));
		at(440, c -> {
			c.options.setPerspective(Perspective.FIRST_PERSON);
			c.player.setYaw(0f);
			c.player.setPitch(15f);
		});
		at(450, c -> shot(c, "ai_fort"));
	}

	/** An enemy unit firing explosive weapons (bazooka, shoulder cannon) at the player: no craters. */
	private static void aiBlock() {
		common();
		at(5, c -> com.example.mechframe.ai.MechServerConfig.targetCreativePlayers = true);
		at(20, c -> server(c, "place structure mechframe:outpost 0 -60 18", "tp @a 15 -60 6 0 0"));
		at(50, c -> c.getServer().execute(() -> {
			ServerWorld w = c.getServer().getOverworld();
			for (MechEntity m : w.getEntitiesByClass(MechEntity.class, new net.minecraft.util.math.Box(-200, -100, -200, 200, 100, 200),
					m -> m.getFaction() == com.example.mechframe.ai.Faction.ENEMY)) {
				m.setAssembly(m.getAssembly().with(MountSlot.R_ARM, Identifier.of("mechframe", "wa_bazooka"), false)
						.with(MountSlot.L_ARM, null, false).with(MountSlot.R_BACK, Identifier.of("mechframe", "wb_cannon"), false)
						.with(MountSlot.L_BACK, null, false));
				m.tudursvehiclemod$fillAllWeaponAmmoAndFuel();
				m.refreshPositionAndAngles(15.5, -60, 20, 180f, 0f);
				log.println("armed " + m.getAssembly().encode());
			}
		}));
		at(60, c -> aiLog(c, "t60", true));
		at(300, c -> aiLog(c, "t300", true));
	}

	private static int groundBlocks = -1;

	private static void aiLog(MinecraftClient c, String label, boolean countBlocks) {
		c.getServer().execute(() -> {
			ServerWorld w = c.getServer().getOverworld();
			ServerPlayerEntity p = c.getServer().getPlayerManager().getPlayerList().get(0);
			for (MechEntity m : w.getEntitiesByClass(MechEntity.class, new net.minecraft.util.math.Box(-200, -100, -200, 200, 100, 200), m -> true)) {
				var b = m.getBrain();
				log.println(label + " mech " + m.getName().getString() + " faction=" + m.getFaction() + " mode=" + (b == null ? "-" : b.mode)
						+ " destroyed=" + m.tudursvehiclemod$isDestroyed()
						+ String.format(Locale.ROOT, " pos=%.1f,%.1f,%.1f", m.getX(), m.getY(), m.getZ())
						+ " target=" + (m.getLockTarget() == null ? "-" : m.getLockTarget().getName().getString())
						+ String.format(Locale.ROOT, " in=%.1f/%.1f yaw=%.0f want=%.0f", m.inForward, m.inStrafe, m.getYaw(), b == null ? 0f : b.desiredYaw)
						+ " ammo=" + ammo(m) + " uuid=" + m.getUuid().toString().substring(0, 8)
						+ " asm=" + m.getAssembly().encode());
			}
			log.println(label + String.format(Locale.ROOT, " player at %.1f,%.1f zombies=%d", p.getX(), p.getZ(),
					w.getEntitiesByClass(net.minecraft.entity.mob.ZombieEntity.class, new net.minecraft.util.math.Box(-200, -100, -200, 200, 100, 200), z -> z.isAlive()).size()));
			if (countBlocks) {
				int n = 0;
				for (BlockPos q : BlockPos.iterate(-30, -64, -40, 30, -56, 60)) {
					if (!w.getBlockState(q).isAir()) {
						n++;
					}
				}
				log.println(label + " solid blocks in arena=" + n + (groundBlocks >= 0 ? " (was " + groundBlocks + ")" : ""));
				if (groundBlocks < 0) {
					groundBlocks = n;
				}
			}
		});
	}

	/** Weapon keys: the base mod's fire / switch keys do nothing in a frame; Swap Right moves the right
	 * trigger to the back weapon; each trigger fires only its side's active weapon. */
	private static void keys() {
		common();
		at(20, c -> spawn(c, "mfx:lynx", 0, 3, 0f, true));
		at(30, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
		at(40, c -> log.println("ammo start " + ammo(MechInput.pilotedMech(c))));
		at(42, c -> {
			key(KeyBinding.byId("key.tudursvehiclemod.fire_weapon"), true);
			tap(KeyBinding.byId("key.tudursvehiclemod.weapon_switch"));
		});
		at(60, c -> {
			key(KeyBinding.byId("key.tudursvehiclemod.fire_weapon"), false);
			log.println("ammo after base fire " + ammo(MechInput.pilotedMech(c)) + " suppressed=" + MechInput.suppressBaseWeaponKeys);
		});
		at(65, c -> tap(MechInput.swapRightKey));
		at(68, c -> {
			log.println("right back=" + MechInput.rightBack + " active=" + MechInput.activeMount(MechInput.pilotedMech(c), true));
			key(MechInput.fireRightKey, true);
		});
		at(72, c -> shot(c, "keys_hud"));
		at(85, c -> {
			key(MechInput.fireRightKey, false);
			log.println("ammo after right trigger (back) " + ammo(MechInput.pilotedMech(c)));
			key(MechInput.fireLeftKey, true);
		});
		at(100, c -> {
			key(MechInput.fireLeftKey, false);
			log.println("ammo after left trigger " + ammo(MechInput.pilotedMech(c)));
		});
	}

	/** First person in a Vesper frame (head hidden for the pilot), then third person (head drawn). */
	private static void fpv() {
		common();
		at(20, c -> spawn(c, "mfx:wisp", 0, 3, 0f, true));
		at(30, c -> c.options.setPerspective(Perspective.FIRST_PERSON));
		at(50, c -> shot(c, "fpv_first"));
		at(55, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
		at(70, c -> shot(c, "fpv_third"));
	}

	/** Transform test for one of the transforming presets (shots named after it). */
	private static void transformer(String p) {
		common();
		at(20, c -> spawn(c, p, 0, 3, 0f, true));
		at(35, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
		String f = p.replace(':', '_');
		at(45, c -> shot(c, f + "_robot"));
		// Robot -> jet on the ground.
		at(50, c -> tap(MechInput.transformKey));
		for (int d : new int[]{4, 8, 12, 18}) {
			at(50 + d, c -> shot(c, f + "_tf_" + d));
		}
		at(85, c -> shot(c, f + "_jet"));
		// Jet -> robot on the ground.
		at(90, c -> tap(MechInput.transformKey));
		for (int d : new int[]{4, 8, 12, 18}) {
			at(90 + d, c -> shot(c, f + "_back_" + d));
		}
		at(125, c -> tap(MechInput.transformKey));
		at(150, c -> key(c.options.forwardKey, true));
		at(200, c -> MechInput.flightPitch -= 30f);
		at(240, c -> shot(c, f + "_flying"));
		// Jet -> robot in the air.
		at(260, c -> {
			key(c.options.forwardKey, false);
			tap(MechInput.transformKey);
		});
		for (int d : new int[]{4, 8, 12, 18, 30}) {
			at(260 + d, c -> shot(c, f + "_air_" + d));
		}
		for (int t = 250; t < 300; t += 2) {
			at(t, DevTest::cameraLog);
		}
	}

	/** Garage + refit rules (server side, logged), the refit screen (screenshot), then firing at a
	 * target frame (ammo and target AP logged). */
	private static void garage() {
		common();
		BlockPos[] garagePos = new BlockPos[1];
		at(40, c -> {
			server(c, "tp @a 0 -60 0 0 0");
			spawn(c, "striker", 0, 6, 0f, false);
		});
		at(50, c -> {
			var server = c.getServer();
			server.execute(() -> {
				ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
				BlockPos pos = player.getBlockPos().add(3, 0, 0);
				garagePos[0] = pos;
				player.getEntityWorld().setBlockState(pos, MechItems.GARAGE.getDefaultState());
				GarageBlockEntity garage = (GarageBlockEntity) player.getEntityWorld().getBlockEntity(pos);
				for (String id : new String[]{"lg_hopper", "wb_cannon", "am_maul", "hd_visor"}) {
					garage.insert(MechPartItem.stackFor(PartRegistry.getOrNull(Identifier.of("mechframe", id))));
				}
				log.println("garage placed at " + pos + " stored=" + contents(garage));
			});
		});
		at(60, c -> refit(c, garagePos[0], MountSlot.LEGS, "lg_hopper", "legs <- hopper (stored)"));
		at(62, c -> refit(c, garagePos[0], MountSlot.R_BACK, "wb_cannon", "r_back <- cannon (stored, empty mount)"));
		at(64, c -> refit(c, garagePos[0], MountSlot.ARMS, "am_maul", "arms <- maul (stored)"));
		at(66, c -> refit(c, garagePos[0], MountSlot.LEGS, "lg_pillar", "legs <- pillar (NOT stored: must fail)"));
		at(68, c -> refit(c, garagePos[0], MountSlot.HEAD, "lg_stride", "head <- legs part (incompatible: must fail)"));
		at(70, c -> refit(c, garagePos[0], MountSlot.LEGS, "lg_stride", "legs <- stride (stock: free, hopper back to garage)"));
		at(72, c -> {
			var server = c.getServer();
			server.execute(() -> {
				ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
				AssemblyService.apply(player, garagePos[0], SPAWNED.get("striker").getId(), MountSlot.R_ARM, null);
				log.println("refit r_arm <- empty: ammo=" + ammo(SPAWNED.get("striker")));
			});
		});
		at(80, c -> {
			BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(garagePos[0]), Direction.UP, garagePos[0], false);
			c.interactionManager.interactBlock(c.player, Hand.MAIN_HAND, hit);
		});
		at(100, c -> {
			log.println("screen = " + c.currentScreen);
			shot(c, "garage_screen");
		});
		at(105, c -> {
			if (c.currentScreen != null) {
				c.currentScreen.close();
			}
		});
		at(110, c -> {
			var server = c.getServer();
			server.execute(() -> {
				ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
				MechEntity mech = SPAWNED.get("striker");
				AssemblyService.resetToStock(player, garagePos[0], mech.getId());
				GarageBlockEntity garage = (GarageBlockEntity) player.getEntityWorld().getBlockEntity(garagePos[0]);
				log.println("reset: asm=" + mech.getAssembly().encode() + " hp=" + mech.getHealth() + "/" + mech.getMaxHealth()
						+ " garage=" + contents(garage));
			});
		});
		// Firing: a target frame 30 m ahead, ride the striker, hold the trigger.
		at(130, c -> {
			spawn(c, "bastion", 0, 40, 180f, false);
			server(c, "tp @a 0 -60 4 0 0");
		});
		at(140, c -> {
			var server = c.getServer();
			server.execute(() -> {
				ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
				player.startRiding(SPAWNED.get("striker"), true, true);
			});
		});
		at(150, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
		at(170, c -> {
			MechEntity m = MechInput.pilotedMech(c);
			log.println("pre-fire: candidate=" + (m == null ? null : m.getCandidate()) + " target hp=" + SPAWNED.get("bastion").getHealth()
					+ " ammo=" + ammo(m));
			key(c.options.attackKey, true);
		});
		at(185, c -> shot(c, "firing"));
		for (int t = 172; t < 200; t += 3) {
			at(t, c -> {
				var server = c.getServer();
				server.execute(() -> {
					MechEntity target = SPAWNED.get("bastion");
					MechEntity shooter = SPAWNED.get("striker");
					var def = target.getDefinition();
					var mesh = com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.getMesh(def.model());
					StringBuilder b = new StringBuilder("  dbg model=" + def.model() + " mesh=" + mesh.map(m ->
							String.format(Locale.ROOT, "[%.1f..%.1f, %.1f..%.1f, %.1f..%.1f]", m.minX(), m.maxX(), m.minY(), m.maxY(), m.minZ(), m.maxZ())).orElse("NONE"));
					b.append(String.format(Locale.ROOT, " aim(yaw=%.1f pitch=%.1f)", shooter.getYaw(), shooter.getPitch()));
					var box = target.getBoundingBox().expand(60);
					for (var proj : target.getEntityWorld().getEntitiesByClass(net.minecraft.entity.projectile.ProjectileEntity.class, box, e -> true)) {
						b.append(String.format(Locale.ROOT, "\n    proj %s pos=%.1f,%.1f,%.1f v=%.2f,%.2f,%.2f", proj.getClass().getSimpleName(),
								proj.getX(), proj.getY(), proj.getZ(), proj.getVelocity().x, proj.getVelocity().y, proj.getVelocity().z));
					}
					log.println(b);
				});
			});
		}
		at(215, c -> {
			key(c.options.attackKey, false);
			MechEntity m = MechInput.pilotedMech(c);
			log.println("post-fire: target hp=" + SPAWNED.get("bastion").getHealth() + " ammo=" + ammo(m));
		});
		at(240, c -> log.println("settled: target hp=" + SPAWNED.get("bastion").getHealth()));
	}

	private static void cameraLog(MinecraftClient c) {
		var cam = c.gameRenderer.getCamera();
		MechEntity m = MechInput.pilotedMech(c);
		log.println(String.format(Locale.ROOT, "  cam pos=%.1f,%.1f,%.1f yaw=%.1f pitch=%.1f | player yaw=%.1f pitch=%.1f | mech %s pitch=%.1f roll=%.1f",
				cam.getCameraPos().x, cam.getCameraPos().y, cam.getCameraPos().z, cam.getYaw(), cam.getPitch(), c.player.getYaw(), c.player.getPitch(),
				m == null ? "-" : String.format(Locale.ROOT, "%.1f,%.1f,%.1f tf=%.2f jet=%s", m.getX(), m.getY(), m.getZ(), m.getTransformProgress(1f), m.isJet()),
				m == null ? 0f : m.getPitch(1f), m == null ? 0f : m.getRoll(1f)));
		if (m != null) {
			var items = com.example.mechframe.client.anim.MechPoser.pose(m, 1f);
			int nan = 0;
			StringBuilder b = new StringBuilder();
			for (var it : items) {
				org.joml.Vector3f tr = it.matrix().getTranslation(new org.joml.Vector3f());
				if (!Float.isFinite(tr.x) || !Float.isFinite(tr.y) || !Float.isFinite(tr.z)) {
					nan++;
				}
				if (it.group().equals("body") || b.length() == 0) {
					b.append(it.group()).append(String.format(Locale.ROOT, "@%.1f,%.1f,%.1f ", tr.x, tr.y, tr.z));
				}
			}
			var anim = com.example.mechframe.client.anim.MechAnimator.of(m);
			log.println("    renderReady=" + c.worldRenderer.isRenderingReady(m.getBlockPos()) + " pose items=" + items.size() + " nan=" + nan + " " + b + " mode=" + anim.mode
					+ " body=" + m.tudursvehiclemod$getBodyOrientation(1f) + " frame=" + m.tudursvehiclemod$getBodyFrameOffset(1f));
		}
	}

	private static String ammo(MechEntity m) {
		if (m == null) {
			return "-";
		}
		StringBuilder b = new StringBuilder();
		for (MountSlot mount : new MountSlot[]{MountSlot.R_ARM, MountSlot.L_ARM, MountSlot.R_BACK, MountSlot.L_BACK}) {
			int i = MechInput.weaponIndex(m, mount);
			b.append(mount).append('=').append(i < 0 ? "-" : String.valueOf(m.getWeaponAmmoState(i)[0])).append(' ');
		}
		return b.toString();
	}

	private static String contents(GarageBlockEntity garage) {
		StringBuilder b = new StringBuilder("[");
		for (int i = 0; i < garage.size(); i++) {
			var stack = garage.getStack(i);
			if (!stack.isEmpty()) {
				b.append(MechPartItem.partId(stack).getPath()).append('x').append(stack.getCount()).append(' ');
			}
		}
		return b.append(']').toString();
	}

	private static void refit(MinecraftClient c, BlockPos pos, MountSlot mount, String part, String what) {
		var server = c.getServer();
		server.execute(() -> {
			ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
			MechEntity mech = SPAWNED.get("striker");
			AssemblyService.apply(player, pos, mech.getId(), mount, Identifier.of("mechframe", part));
			GarageBlockEntity garage = (GarageBlockEntity) player.getEntityWorld().getBlockEntity(pos);
			log.println("refit " + what + ": asm=" + mech.getAssembly().encode() + " hp=" + mech.getHealth() + "/" + mech.getMaxHealth()
					+ " w=" + mech.getStats().totalWeight() + " ammo=" + ammo(mech) + " garage=" + contents(garage));
		});
	}

	private static void legs() {
		common();
		String[] presets = {"weaver", "strider", "rampart", "hopper"};
		for (int i = 0; i < presets.length; i++) {
			int base = 20 + i * 90;
			String p = presets[i];
			at(base, c -> {
				// Remove (not kill: a destroyed frame leaves a burning wreck that would hurt the next one).
				var server = c.getServer();
				server.execute(() -> {
					for (MechEntity m : SPAWNED.values()) {
						m.discard();
					}
					SPAWNED.clear();
				});
			});
			at(base + 5, c -> spawn(c, p, 0, 3, 0f, true));
			at(base + 15, c -> {
				c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				key(c.options.forwardKey, true);
				key(c.options.leftKey, true);
			});
			at(base + 40, c -> shot(c, p + "_walk1"));
			at(base + 44, c -> shot(c, p + "_walk2"));
			at(base + 60, c -> {
				key(c.options.forwardKey, false);
				key(c.options.leftKey, false);
			});
			at(base + 80, c -> shot(c, p + "_stand"));
		}
	}

	/** Gait: strafing (legs open and close), a sudden reversal, then the reverse-joint frame walking;
	 * every step start is logged per foot to check the legs alternate. */
	private static void gait() {
		common();
		at(20, c -> spawn(c, "striker", 0, 3, 0f, true));
		at(35, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
		at(40, c -> key(c.options.leftKey, true));
		for (int d : new int[]{20, 23, 26, 29}) {
			at(40 + d, c -> shot(c, "strafe_" + d));
		}

		at(80, c -> {
			key(c.options.leftKey, false);
			key(c.options.rightKey, true);
		});
		for (int d : new int[]{1, 2, 3, 5, 8}) {
			at(80 + d, c -> shot(c, "reverse_" + d));
		}
		at(110, c -> {
			key(c.options.rightKey, false);
			key(c.options.forwardKey, true);
		});

		for (int t : new int[]{113, 118, 123}) {
			sideShot(t, "striker_side_" + t, 0.0);
		}
		at(128, c -> key(c.options.forwardKey, false));
		at(130, c -> {
			var server = c.getServer();
			server.execute(() -> {
				for (MechEntity m : SPAWNED.values()) {
					m.discard();
				}
				SPAWNED.clear();
			});
		});
		at(135, c -> spawn(c, "hopper", 0, 3, 0f, true));
		at(150, c -> key(c.options.forwardKey, true));
		for (int d : new int[]{30, 32, 34, 36, 38}) {
			at(150 + d, c -> shot(c, "hopper_walk_" + d));
		}

		for (int t : new int[]{195, 200, 205, 210}) {
			sideShot(t, "hopper_side_" + t, 0.0);
		}
		at(230, c -> key(c.options.forwardKey, false));
		// Reverse-joint jump: legs stretched on the way up, folded on landing.
		at(240, c -> key(c.options.jumpKey, true));
		at(242, c -> key(c.options.jumpKey, false));
		sideShot(245, "hopper_jump_up", 0.0);
		for (int t = 262; t <= 282; t += 4) {
			sideShot(t, "hopper_land_" + t, 0.0);
		}
		for (int t = 240; t < 290; t++) {
			at(t, DevTest::stepLog);
		}
		for (int t = 40; t < 240; t++) {
			at(t, DevTest::stepLog);
		}
	}

	private static final boolean[] wasStepping = new boolean[8];

	private static void stepLog(MinecraftClient c) {
		MechEntity m = MechInput.pilotedMech(c);
		if (m == null || !(m.clientAnimation instanceof com.example.mechframe.client.anim.MechAnimator anim)) {
			return;
		}
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < anim.feet.size() && i < wasStepping.length; i++) {
			boolean st = anim.feet.get(i).stepping;
			if (st && !wasStepping[i]) {
				b.append(" start").append(i).append(anim.feet.get(i).trigger);
			}
			wasStepping[i] = st;
		}
		if (b.length() > 0) {
			log.println(String.format(Locale.ROOT, "step t=%d mode=%s speed=%.3f%s", tick, anim.mode, anim.speed, b));
		}
		log.println(String.format(Locale.ROOT, "pelvis t=%d dy=%.3f yaw=%.1f", tick, anim.pelvisDy, m.getYaw()));
		if (anim.feet.size() > 2) {
			double minD = 1e9;
			StringBuilder ang = new StringBuilder();
			double yr = Math.toRadians(m.getYaw());
			for (int i = 0; i < anim.feet.size(); i++) {
				var a = anim.feet.get(i).pos;
				double dx = a.x - m.getX(), dz = a.z - m.getZ();
				// body-local: +x left, +z forward
				double lz = -dx * Math.sin(yr) + dz * Math.cos(yr);
				double lx = dx * Math.cos(yr) + dz * Math.sin(yr);
				ang.append(String.format(Locale.ROOT, " %d:(%.2f,%.2f)", i, lx, lz));
				for (int j = i + 1; j < anim.feet.size(); j++) {
					var b2 = anim.feet.get(j).pos;
					minD = Math.min(minD, Math.hypot(a.x - b2.x, a.z - b2.z));
				}
			}
			log.println(String.format(Locale.ROOT, "feet t=%d mode=%s min=%.2f%s", tick, anim.mode, minD, ang));
		}
	}

	/** Sharp turns in place and while walking, for the multi-legged frames (steps logged, shots mid-turn). */
	private static void turn() {
		common();
		String[] presets = {"weaver", "strider"};
		for (int i = 0; i < presets.length; i++) {
			int base = 20 + i * 120;
			String p = presets[i];
			at(base, c -> {
				var server = c.getServer();
				server.execute(() -> {
					for (MechEntity m : SPAWNED.values()) {
						m.discard();
					}
					SPAWNED.clear();
				});
			});
			at(base + 5, c -> spawn(c, p, 0, 3, 0f, true));
			at(base + 15, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
			at(base + 25, c -> c.player.setYaw(c.player.getYaw() + 150f));
			for (int d : new int[]{27, 33, 39, 45}) {
				topShot(base + d, p + "_turn_" + d);
			}
			at(base + 60, c -> key(c.options.forwardKey, true));
			at(base + 70, c -> c.player.setYaw(c.player.getYaw() - 120f));
			for (int d : new int[]{73, 78}) {
				at(base + d, c -> shot(c, p + "_walkturn_" + d));
			}
			at(base + 95, c -> key(c.options.forwardKey, false));
			at(base + 110, c -> shot(c, p + "_settled"));
			for (int t = base + 20; t < base + 115; t++) {
				at(t, DevTest::stepLog);
			}
		}
	}

	/** Expansion pack: every mfx preset in a lineup, the fabricator screen and a fabrication. */
	private static void pack() {
		common();
		String[] presets = {"wisp", "gnat", "skiff", "kite", "raven", "recluse", "lynx", "corvus", "colossus", "mammoth", "bulwark", "condor"};
		// Three rows of four, one row at a time (the software-rendered test client runs out of memory
		// with all twelve frames' textures loaded at once).
		for (int row = 0; row < 3; row++) {
			int r = row;
			int t0 = 20 + row * 60;
			at(t0, c -> {
				for (int col = 0; col < 4; col++) {
					spawn(c, "mfx:" + presets[r * 4 + col], (col - 1.5) * 7.0, 14, 180f, false);
				}
				server(c, "tp @a 0 -51 -2 0 6");
			});
			at(t0 + 40, c -> {
				for (int col = 0; col < 4; col++) {
					String p = presets[r * 4 + col];
					MechEntity m = SPAWNED.get("mfx:" + p);
					log.println("preset " + p + " " + (m == null ? "MISSING" : m.getAssembly().encode() + " complete=" + m.getAssembly().isComplete()
							+ " hp=" + m.getMaxHealth() + " def=" + m.getDefinition().displayName(m.getVehicleDefinitionId())));
				}
				shot(c, "pack_row" + r);
			});
			at(t0 + 55, c -> server(c, "kill @e[type=mechframe:mech]"));
		}
		at(200, c -> log.println("recipes " + com.example.mechframe.recipe.MechRecipes.sorted().size()));
		// fabricator
		at(205, c -> {
			server(c, "tp @a 0 -60 -30 0 20", "setblock 0 -60 -28 mechframe:fabricator", "gamemode survival @a",
					"clear @a", "give @a minecraft:iron_ingot 64", "give @a minecraft:feather 8", "give @a minecraft:gold_ingot 10",
					"give @a minecraft:glass 4");
		});
		at(215, c -> c.setScreen(new com.example.mechframe.client.screen.FabricatorScreen(new net.minecraft.util.math.BlockPos(0, -60, -28))));
		at(225, c -> shot(c, "fab_screen"));
		at(227, c -> {
			if (c.currentScreen instanceof com.example.mechframe.client.screen.FabricatorScreen fs) {
				fs.selectForTest(net.minecraft.util.Identifier.of("mfx", "hd_pin"));
			}
		});
		at(233, c -> shot(c, "fab_selected"));
		at(235, c -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new com.example.mechframe.net.MechNetworking.FabricatePayload(new net.minecraft.util.math.BlockPos(0, -60, -28), "mfx:hd_pin")));
		at(245, c -> {
			var inv = c.player.getInventory();
			StringBuilder b = new StringBuilder();
			for (int i = 0; i < inv.size(); i++) {
				var st = inv.getStack(i);
				if (!st.isEmpty()) {
					b.append(st.getName().getString()).append(" x").append(st.getCount()).append("; ");
				}
			}
			log.println("inventory after fabricate: " + b);
		});
		at(247, c -> shot(c, "fab_after"));
		at(251, c -> c.setScreen(null));
	}

	/** Special-standard parts: hover legs (glide, drift), built-in rail arms (fire), pile driver thrust. */
	private static void special() {
		common();
		at(20, c -> spawn(c, "mfx:raven", 0, 3, 0f, true));
		at(24, c -> c.getServer().execute(() -> {
			MechEntity m = SPAWNED.get("mfx:raven");
			var a = m.getAssembly().with(com.example.mechframe.part.MountSlot.LEGS, Identifier.of("mfx", "lg_halo"), false)
					.with(com.example.mechframe.part.MountSlot.ARMS, Identifier.of("mfx", "am_arbalest"), false)
					.with(com.example.mechframe.part.MountSlot.BOOSTER, Identifier.of("mfx", "bs_comet"), false)
					.with(com.example.mechframe.part.MountSlot.HEAD, Identifier.of("mfx", "hd_armillary"), false);
			m.setAssembly(a);
			m.tudursvehiclemod$fillAllWeaponAmmoAndFuel();
			log.println("special asm " + a.encode() + " r_arm=" + a.part(com.example.mechframe.part.MountSlot.R_ARM)
					+ " locked=" + a.isLocked(com.example.mechframe.part.MountSlot.R_ARM));
		}));
		at(35, c -> {
			c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			c.player.setPitch(20f);
		});
		at(45, c -> shot(c, "hover_idle"));
		at(50, c -> key(c.options.forwardKey, true));
		at(70, c -> key(c.options.forwardKey, false));
		at(71, c -> key(c.options.leftKey, true));
		at(76, c -> shot(c, "hover_drift"));
		at(85, c -> key(c.options.leftKey, false));
		for (int t = 48; t < 110; t += 3) {
			at(t, c -> {
				MechEntity m = MechInput.pilotedMech(c);
				if (m != null) {
					log.println(String.format(Locale.ROOT, "hover t=%d pos=%.2f,%.2f,%.2f v=%.3f grounded=%s", tick, m.getX(), m.getY(), m.getZ(),
							m.getVelocity().horizontalLength(), m.isGroundedVisual()));
				}
			});
		}
		at(110, c -> {
			MechEntity m = MechInput.pilotedMech(c);
			int idx = m == null ? -1 : MechInput.weaponIndex(m, com.example.mechframe.part.MountSlot.R_ARM);
			log.println("rail index " + idx + (idx >= 0 ? " ammo " + java.util.Arrays.toString(m.getWeaponAmmoState(idx)) : ""));
			key(MechInput.fireRightKey, true);
		});
		at(114, c -> shot(c, "rail_fire"));
		at(125, c -> {
			key(MechInput.fireRightKey, false);
			MechEntity m = MechInput.pilotedMech(c);
			int idx = m == null ? -1 : MechInput.weaponIndex(m, com.example.mechframe.part.MountSlot.R_ARM);
			log.println("rail after " + (idx >= 0 ? java.util.Arrays.toString(m.getWeaponAmmoState(idx)) : "-"));
		});
		// pile driver on a normal arm set
		at(130, c -> c.getServer().execute(() -> {
			MechEntity m = SPAWNED.get("mfx:raven");
			m.setAssembly(m.getAssembly().with(com.example.mechframe.part.MountSlot.ARMS, Identifier.of("mfx", "am_shard"), false)
					.with(com.example.mechframe.part.MountSlot.L_ARM, Identifier.of("mfx", "wm_piledriver"), false));
		}));
		at(140, c -> spawn(c, "mechframe:rampart", 0, 14, 180f, false));
		at(160, c -> log.println("target hp before " + SPAWNED.get("mechframe:rampart").getHealth()));
		at(162, c -> tap(MechInput.fireLeftKey));
		for (int d : new int[]{164, 167, 169}) {
			at(d, c -> shot(c, "pile_" + d));
		}
		at(190, c -> c.getServer().execute(() -> log.println("target hp after " + SPAWNED.get("mechframe:rampart").getHealth())));
	}

	/** Melee: a Striker with the laser blade on its left arm dashes at a parked frame and slashes it. */
	private static void melee() {
		common();
		at(20, c -> {
			spawn(c, "striker", 0, 3, 0f, true);
			spawn(c, "rampart", 0, 12, 180f, false);
		});
		at(24, c -> c.getServer().execute(() -> {
			MechEntity m = SPAWNED.get("striker");
			m.setAssembly(m.getAssembly().with(com.example.mechframe.part.MountSlot.L_ARM, Identifier.of("mechframe", "wa_blade"), false));
			log.println("asm " + m.getAssembly().encode());
		}));
		at(35, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
		at(50, c -> log.println("target hp before " + SPAWNED.get("rampart").getHealth()));
		at(60, c -> tap(MechInput.fireLeftKey));
		for (int d : new int[]{62, 64, 66, 67, 69, 73}) {
			sideShot(d, "melee_" + d, 4.0);
		}
		at(66, c -> shot(c, "melee_back_66"));
		at(90, c -> c.getServer().execute(() -> log.println("target hp after " + SPAWNED.get("rampart").getHealth())));
		at(95, c -> tap(MechInput.fireLeftKey));
		at(101, c -> shot(c, "melee_back_101"));
		at(130, c -> c.getServer().execute(() -> log.println("target hp after2 " + SPAWNED.get("rampart").getHealth())));
		for (int t = 58; t < 110; t++) {
			at(t, c -> {
				MechEntity m = MechInput.pilotedMech(c);
				if (m != null) {
					MechEntity tg = SPAWNED.get("rampart");
					log.println(String.format(Locale.ROOT, "melee t=%d z=%.2f v=%.2f arm=%s time=%.1f en=%.0f fcs=%s lock=%s tgz=%.2f", tick, m.getZ(),
							m.getVelocity().horizontalLength(), m.getMeleeArm(), m.getMeleeTime(0f), m.getEn(), m.getFcsTarget(), m.getLockTarget(),
							tg != null ? tg.getZ() : 0.0));
				}
			});
		}
	}

	/** Sideways quick boosts (left, then right) for a biped and a reverse joint: the far leg is left behind. */
	private static void qbSide() {
		common();
		String[] presets = {"striker", "hopper"};
		for (int k = 0; k < presets.length; k++) {
			int base = 20 + k * 120;
			String p = presets[k];
			at(base, c -> spawn(c, p, 0, 3, 0f, true));
			at(base + 15, c -> {
				c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				c.player.setPitch(32f);
			});
			for (int t = base + 28; t < base + 60; t++) {
				at(t, c -> {
					MechEntity m = MechInput.pilotedMech(c);
					if (m != null && m.clientAnimation instanceof com.example.mechframe.client.anim.MechAnimator a) {
						log.println(String.format(Locale.ROOT, "drag t=%d mode=%s f0=%s f1=%s", tick, a.mode,
								a.feet.get(0).local, a.feet.get(1).local));
					}
				});
			}
			at(base + 30, c -> key(c.options.leftKey, true));
			at(base + 31, c -> tap(MechInput.quickBoostKey));
			at(base + 33, c -> key(c.options.leftKey, false));
			for (int d : new int[]{33, 36, 40, 46}) {
				at(base + d, c -> shot(c, p + "_qbl_" + d));
			}
			at(base + 70, c -> key(c.options.rightKey, true));
			at(base + 71, c -> tap(MechInput.quickBoostKey));
			at(base + 73, c -> key(c.options.rightKey, false));
			at(base + 76, c -> shot(c, p + "_qbr_76"));
		}
	}

	/** Six legs: boost, then let go of boost and walk on (the middle legs must not over-reach). */
	private static void hexBoost() {
		common();
		at(20, c -> spawn(c, "weaver", 0, 3, 0f, true));
		at(35, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
		at(40, c -> {
			key(c.options.forwardKey, true);
			tap(MechInput.boostKey);
		});
		at(70, c -> tap(MechInput.boostKey));
		for (int t : new int[]{74, 80, 86, 92, 98, 104}) {
			topShot(t, "hexboost_" + t);
		}
		at(110, c -> key(c.options.forwardKey, false));
		for (int t = 60; t < 115; t++) {
			at(t, DevTest::stepLog);
		}
	}

	/** Turbo: a non-transforming frame on the transform key - take off, climb, cruise, stop. */
	private static void turbo() {
		common();
		at(20, c -> spawn(c, "striker", 0, 3, 0f, true));
		at(35, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
		at(45, c -> tap(MechInput.transformKey));
		at(50, c -> c.player.setPitch(-15f));
		at(70, c -> shot(c, "turbo_climb"));
		at(90, c -> c.player.setPitch(5f));
		at(110, c -> shot(c, "turbo_cruise"));
		sideShot(96, "turbo_side_a", 5.0);
		sideShot(104, "turbo_side_b", 5.0);

		at(120, c -> tap(MechInput.quickBoostKey));
		at(140, c -> tap(MechInput.transformKey));
		at(150, c -> shot(c, "turbo_off"));
	}

	/** Tank controls: A turns the hull in place, W drives along it, the camera turns the turret. */
	private static void tank() {
		common();
		at(20, c -> {
			MechInput.tankControls = true;
			spawn(c, "rampart", 0, 3, 0f, true);
		});
		at(35, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
		at(45, c -> shot(c, "tank_start"));
		at(50, c -> key(c.options.leftKey, true));
		at(70, c -> {
			key(c.options.leftKey, false);
			MechEntity m = MechInput.pilotedMech(c);
			log.println("after turn: hull=" + (m != null ? m.getHullYaw() : 0) + " yaw=" + (m != null ? m.getYaw() : 0));
		});
		at(75, c -> shot(c, "tank_turned"));
		at(80, c -> key(c.options.forwardKey, true));
		at(110, c -> shot(c, "tank_drive"));
		at(120, c -> {
			key(c.options.forwardKey, false);
			c.player.setYaw(c.player.getYaw() + 70f);
		});
		at(150, c -> {
			MechEntity m = MechInput.pilotedMech(c);
			log.println("turret: hull=" + (m != null ? m.getHullYaw() : 0) + " yaw=" + (m != null ? m.getYaw() : 0));
			shot(c, "tank_turret");
		});
	}

	private static void tick(MinecraftClient c) {
		if (c.world == null || c.player == null || c.getServer() == null) {
			return;
		}
		if (tick < 0 && c.currentScreen != null) {
			// Still on "Loading terrain": opening/closing a screen releases every key, so start the clock after it.
			return;
		}
		tick++;
		for (Step s : STEPS) {
			if (s.at == tick) {
				try {
					s.action.accept(c);
				} catch (RuntimeException e) {
					log.println("step " + s.at + " failed: " + e);
					e.printStackTrace(log);
				}
			}
		}
		MechEntity mech = MechInput.pilotedMech(c);
		if (mech != null && tick % 2 == 0) {
			double dx = mech.getX() - lastX;
			double dz = mech.getZ() - lastZ;
			log.println(String.format(Locale.ROOT, "t=%d pos=%.2f,%.2f,%.2f v=%.3f vy=%.3f yaw=%.1f en=%.0f boost=%s hover=%s grounded=%s jet=%s tf=%.2f hp=%.0f",
					tick, mech.getX(), mech.getY(), mech.getZ(), Math.sqrt(dx * dx + dz * dz) / 2.0, mech.getVelocity().y,
					mech.getYaw(), mech.getEn(), mech.isBoostingVisual(), mech.isHoveringVisual(), mech.isGroundedVisual(),
					mech.isJet(), mech.getTransformProgress(1f), mech.getHealth()));
			lastX = mech.getX();
			lastZ = mech.getZ();
		}
	}
}
