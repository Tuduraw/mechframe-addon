package com.example.mechframe.client.mixin;

import com.example.mechframe.client.MechInput;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While the local player pilots a frame, the base mod's own fire and weapon-switch keys read as
 * not pressed (their presses are dropped, so nothing fires late after leaving the frame): the frame's
 * weapons answer only to this mod's triggers and swap keys (MechInput). Every other key, and these two
 * in any other vehicle, behave as usual. */
@Mixin(KeyMapping.class)
public abstract class KeyBindingSuppressMixin {

	@Shadow
	private int clickCount;

	@Shadow
	public abstract String getName();

	private boolean mechframe$suppressed() {
		if (!MechInput.suppressBaseWeaponKeys) {
			return false;
		}
		String id = getName();
		return "key.tudursvehiclemod.fire_weapon".equals(id) || "key.tudursvehiclemod.weapon_switch".equals(id);
	}

	@Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
	private void mechframe$notPressed(CallbackInfoReturnable<Boolean> cir) {
		if (mechframe$suppressed()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "consumeClick", at = @At("HEAD"), cancellable = true)
	private void mechframe$notWasPressed(CallbackInfoReturnable<Boolean> cir) {
		if (mechframe$suppressed()) {
			clickCount = 0;
			cir.setReturnValue(false);
		}
	}
}
