package com.example.mechframe.client;

import com.example.mechframe.MechFrame;
import com.example.mechframe.entity.MechEntity;
import com.example.mechframe.net.MechNetworking;
import com.example.mechframe.part.MountSlot;
import com.example.tudursvehiclemod.asset.WeaponDefinition;
import com.example.tudursvehiclemod.network.FireWeaponPayload;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** The pilot's controls. Movement keys are the vanilla ones (read raw, so they work while riding);
 * this mod adds Boost (Left Ctrl, held), Quick Boost (X, or double-tap a direction), Transform (V)
 * and the weapons: Left Trigger (left button) and Right Trigger (right button) fire the active weapon
 * of their side, Swap Left / Swap Right Weapon (Z / C) switch a side between its arm weapon and its
 * back weapon. All rebindable in Controls. These are the only weapon keys while piloting: the base
 * mod's fire and weapon-switch keys are ignored then (KeyBindingSuppressMixin), so the frame does not
 * depend on how they are bound. Melee weapons swing on their side's trigger. Target lock is the base
 * mod's designation key (B). */
public final class MechInput {

	public static KeyBinding boostKey;
	public static KeyBinding quickBoostKey;
	public static KeyBinding transformKey;
	public static KeyBinding fireLeftKey;
	public static KeyBinding fireRightKey;
	public static KeyBinding swapLeftKey;
	public static KeyBinding swapRightKey;
	/** Per side: true = the trigger fires the back weapon, false = the arm weapon. */
	public static boolean leftBack;
	public static boolean rightBack;
	/** While true (piloting a frame) the base mod's fire / weapon-switch keys read as never pressed. */
	public static boolean suppressBaseWeaponKeys;
	private static int selectionMechId = -1;
	public static boolean doubleTapQuickBoost = true;
	/** config tank_controls: tank legs drive like a tank (hull on A/D + W/S, upper body as the turret). */
	public static boolean tankControls = false;
	/** config boost_toggle: the boost key switches boost on and off (false: boost while held). */
	public static boolean boostToggle = true;
	/** Toggle mode: whether boost is currently switched on. */
	public static boolean boostOn;

	/** Jet form: mouse movement captured by EntityLookMixin, consumed every tick. */
	public static float flightYaw;
	public static float flightPitch;

	private static final int DOUBLE_TAP_TICKS = 7;
	private static final long[] lastTap = new long[4];
	private static final boolean[] wasDown = new boolean[4];
	private static long tick;
	private static float lastForward = 99f;
	private static float lastStrafe = 99f;
	private static int lastFlags = -1;
	private static int resendCountdown;

	private MechInput() {
	}

	public static void register() {
		KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MechFrame.MOD_ID, "mech"));
		boostKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.mechframe.boost",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, category));
		quickBoostKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.mechframe.quick_boost",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, category));
		transformKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.mechframe.transform",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, category));
		fireLeftKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.mechframe.fire_left",
				InputUtil.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_LEFT, category));
		fireRightKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.mechframe.fire_right",
				InputUtil.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT, category));
		swapLeftKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.mechframe.swap_left",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, category));
		swapRightKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.mechframe.swap_right",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, category));
	}

	/** The mount whose weapon a side's trigger uses now: the selected one (arm or back), or the
	 * other one of that side when the selected mount is empty. Null when the side has none. */
	public static MountSlot activeMount(MechEntity mech, boolean right) {
		MountSlot arm = right ? MountSlot.R_ARM : MountSlot.L_ARM;
		MountSlot back = right ? MountSlot.R_BACK : MountSlot.L_BACK;
		boolean wantBack = right ? rightBack : leftBack;
		MountSlot first = wantBack ? back : arm;
		MountSlot second = wantBack ? arm : back;
		if (usable(mech, first)) {
			return first;
		}
		return usable(mech, second) ? second : null;
	}

	private static boolean usable(MechEntity mech, MountSlot mount) {
		var part = mech.getAssembly().part(mount);
		if (part != null && part.weapon() != null && part.weapon().isMelee()) {
			return !mech.isJet();
		}
		return weaponIndex(mech, mount) >= 0;
	}

	public static MechEntity pilotedMech(MinecraftClient client) {
		if (client.player == null) {
			return null;
		}
		return client.player.getVehicle() instanceof MechEntity mech && mech.getControllingPassenger() == client.player ? mech : null;
	}

	/** START of the client tick - before the world ticks the frame, so this tick's physics sees it. */
	public static void tick(MinecraftClient client) {
		tick++;
		MechEntity mech = pilotedMech(client);
		suppressBaseWeaponKeys = mech != null;
		if (mech != null && mech.getId() != selectionMechId) {
			// freshly boarded: both triggers on the arm weapons
			selectionMechId = mech.getId();
			leftBack = false;
			rightBack = false;
		} else if (mech == null) {
			selectionMechId = -1;
		}
		if (mech == null || client.currentScreen != null) {
			while (quickBoostKey.wasPressed()) {
			}
			while (transformKey.wasPressed()) {
			}
			while (swapLeftKey.wasPressed()) {
			}
			while (swapRightKey.wasPressed()) {
			}
			flightYaw = 0f;
			flightPitch = 0f;
			if (mech == null) {
				boostOn = false;
			}
			while (mech == null && boostKey.wasPressed()) {
			}
			if (mech != null) {
				mech.inForward = 0f;
				mech.inStrafe = 0f;
				mech.inJump = false;
				mech.inBoost = false;
				send(0f, 0f, tankControls ? 4 : 0);
			}
			return;
		}
		var o = client.options;
		float forward = (o.forwardKey.isPressed() ? 1f : 0f) - (o.backKey.isPressed() ? 1f : 0f);
		float strafe = (o.leftKey.isPressed() ? 1f : 0f) - (o.rightKey.isPressed() ? 1f : 0f);
		boolean jump = o.jumpKey.isPressed();
		boolean boost;
		if (boostToggle) {
			while (boostKey.wasPressed()) {
				boostOn = !boostOn;
			}
			if (mech.isJet() || mech.isTurbo()) {
				boostOn = false;
			}
			boost = boostOn;
		} else {
			while (boostKey.wasPressed()) {
			}
			boost = boostKey.isPressed();
		}
		mech.inForward = forward;
		mech.inStrafe = strafe;
		mech.inJump = jump;
		mech.inBoost = boost;
		mech.inTankControls = tankControls;
		send(forward, strafe, (jump ? 1 : 0) | (boost ? 2 : 0) | (tankControls ? 4 : 0));

		// Quick boost: the key, or a double tap of a direction.
		boolean qb = false;
		float qbX = strafe;
		float qbZ = forward;
		while (quickBoostKey.wasPressed()) {
			qb = true;
		}
		if (doubleTapQuickBoost) {
			KeyBinding[] keys = {o.forwardKey, o.backKey, o.leftKey, o.rightKey};
			float[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
			for (int i = 0; i < 4; i++) {
				boolean down = keys[i].isPressed();
				if (down && !wasDown[i]) {
					if (tick - lastTap[i] <= DOUBLE_TAP_TICKS) {
						qb = true;
						qbX = dirs[i][0];
						qbZ = dirs[i][1];
						lastTap[i] = 0;
					} else {
						lastTap[i] = tick;
					}
				}
				wasDown[i] = down;
			}
		}
		if (qb && mech.tryQuickBoost(qbX, qbZ)) {
			ClientPlayNetworking.send(new MechNetworking.ActionPayload(MechNetworking.ActionPayload.QUICK_BOOST, qbX, qbZ));
		}
		while (transformKey.wasPressed()) {
			ClientPlayNetworking.send(new MechNetworking.ActionPayload(MechNetworking.ActionPayload.TRANSFORM, 0f, 0f));
		}

		// Jet form: mouse steering.
		if (mech.isJet()) {
			if (flightYaw != 0f || flightPitch != 0f) {
				mech.pendingYawInput += flightYaw;
				mech.pendingPitchInput += flightPitch;
				ClientPlayNetworking.send(new MechNetworking.FlightInputPayload(flightYaw, flightPitch));
			}
		}
		flightYaw = 0f;
		flightPitch = 0f;

		// Weapon selection and triggers (vanilla attack is cancelled while piloting).
		while (swapLeftKey.wasPressed()) {
			leftBack = !leftBack;
		}
		while (swapRightKey.wasPressed()) {
			rightBack = !rightBack;
		}
		trigger(mech, fireLeftKey, false);
		trigger(mech, fireRightKey, true);
	}

	/** One side's trigger: fires that side's active weapon (activeMount) - a melee weapon swings
	 * once per press, a gun fires while held. */
	private static void trigger(MechEntity mech, KeyBinding key, boolean right) {
		boolean pressed = key.wasPressed();
		while (key.wasPressed()) {
		}
		MountSlot mount = activeMount(mech, right);
		if (mount == null) {
			return;
		}
		var part = mech.getAssembly().part(mount);
		if (part != null && part.weapon() != null && part.weapon().isMelee()) {
			if (pressed && mech.tryMelee(mount)) {
				ClientPlayNetworking.send(new MechNetworking.ActionPayload(MechNetworking.ActionPayload.MELEE,
						mount == MountSlot.R_ARM ? 1f : 0f, 0f));
			}
			return;
		}
		if (!key.isPressed()) {
			return;
		}
		int index = weaponIndex(mech, mount);
		if (index >= 0) {
			ClientPlayNetworking.send(new FireWeaponPayload(index));
		}
	}

	public static int weaponIndex(MechEntity mech, MountSlot mount) {
		List<WeaponDefinition> weapons = mech.getDefinition().weapons();
		for (int i = 0; i < weapons.size(); i++) {
			if (weapons.get(i).seatIndex() == mount.seatIndex()) {
				return i;
			}
		}
		return -1;
	}

	private static void send(float forward, float strafe, int flags) {
		if (--resendCountdown > 0 && forward == lastForward && strafe == lastStrafe && flags == lastFlags) {
			return;
		}
		resendCountdown = 10;
		lastForward = forward;
		lastStrafe = strafe;
		lastFlags = flags;
		ClientPlayNetworking.send(new MechNetworking.InputPayload(forward, strafe, (byte) flags));
	}
}
