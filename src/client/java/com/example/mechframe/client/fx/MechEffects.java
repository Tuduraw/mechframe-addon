package com.example.mechframe.client.fx;

import com.example.mechframe.MechSounds;
import com.example.mechframe.client.CameraFx;
import com.example.mechframe.client.anim.MechAnimator;
import com.example.mechframe.entity.MechClientHooks;
import com.example.mechframe.entity.MechEntity;
import com.example.mechframe.part.MechLayout;
import com.example.mechframe.part.MechPart;
import com.example.mechframe.part.MountSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/** Client hooks: ticks the animator and turns movement into visible/audible feedback - booster
 * exhaust (blue while boosting, a burst on quick boost), dust where feet land, sparks on a hard stop,
 * camera punches for the pilot. The local pilot's own events come straight from its simulation;
 * other players' frames are read from the synced action counters. */
public final class MechEffects implements MechClientHooks {

	private final Map<Integer, Integer> lastAction = new HashMap<>();

	@Override
	public void tick(MechEntity mech) {
		MechAnimator anim = MechAnimator.of(mech);
		anim.tick(mech);
		ClientLevel world = (ClientLevel) mech.level();
		MechLayout layout = mech.getLayout();
		if (layout == null) {
			return;
		}
		if (!mech.isLocalPilot()) {
			int action = mech.getSyncedAction();
			Integer before = this.lastAction.put(mech.getId(), action);
			if (before != null) {
				if (((action >> 8) & 15) != ((before >> 8) & 15)) {
					this.burst(mech, world, false);
				}
				if (((action >> 16) & 15) != ((before >> 16) & 15)) {
					this.landing(mech, world, 1.2f, false);
				}
			}
		}
		RandomSource random = world.getRandom();
		boolean boosting = mech.isBoostingVisual();
		boolean hovering = mech.isHoveringVisual();
		// Exhaust inherits the frame's own motion: at boost speed a frame outruns its own flames,
		// which otherwise end up streaming straight through the chase camera as screen-sized blobs.
		Vec3 carry = motion(mech).scale(0.9);
		if (boosting || hovering || mech.isAfterburnerVisual()) {
			for (Vec3[] nozzle : this.nozzles(mech, layout)) {
				Vec3 p = nozzle[0];
				Vec3 d = nozzle[1];
				ParticleOptions flame = mech.isAfterburnerVisual() ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME;
				for (int i = 0; i < 2; i++) {
					exhaust(flame, p.x + random.nextGaussian() * 0.05, p.y + random.nextGaussian() * 0.05,
							p.z + random.nextGaussian() * 0.05, carry.x + d.x * 0.3, carry.y + d.y * 0.3, carry.z + d.z * 0.3,
							5 + random.nextInt(4));
				}
				if (random.nextInt(3) == 0) {
					exhaust(ParticleTypes.SMOKE, p.x, p.y, p.z, carry.x * 0.5 + d.x * 0.2, carry.y * 0.5 + d.y * 0.2,
							carry.z * 0.5 + d.z * 0.2, 10 + random.nextInt(6));
				}
			}
		}
		if (mech.isBrakingVisual()) {
			for (MechAnimator.Foot foot : anim.feet) {
				world.addParticle(ParticleTypes.ELECTRIC_SPARK, foot.pos.x, foot.pos.y + 0.1, foot.pos.z,
						random.nextGaussian() * 0.2, 0.15, random.nextGaussian() * 0.2);
				this.dust(world, foot.pos, 2, 0.12);
			}
			if (anim.feet.isEmpty()) {
				Vec3 p = mech.position();
				this.dust(world, p, 4, 0.2);
			}
		}
		if (anim.mode == MechAnimator.Mode.SKATE && mech.tickCount % 2 == 0) {
			for (MechAnimator.Foot foot : anim.feet) {
				this.dust(world, foot.pos, 1, 0.06);
			}
		}
		if (layout.stats.legType() == MechPart.LegType.TANK && anim.speed > 0.2f && mech.isGroundedVisual()) {
			this.dust(world, mech.position(), 2, 0.1);
		}
		if (layout.stats.legType() == MechPart.LegType.HOVER && mech.isGroundedVisual() && mech.tickCount % 2 == 0) {
			// Downwash: a ring of dust blown out from under the skirt, more at speed.
			Vec3 p = mech.position();
			double r = layout.collisionWidth * 0.5;
			int n = anim.speed > 0.3f ? 4 : 2;
			for (int i = 0; i < n; i++) {
				double a = random.nextDouble() * Math.PI * 2;
				double dx = Math.cos(a), dz = Math.sin(a);
				world.addParticle(ParticleTypes.POOF, p.x + dx * r, p.y + 0.1, p.z + dz * r, dx * 0.12, 0.01, dz * 0.12);
			}
		}
		for (Vec3 step : anim.footfalls) {
			this.dust(world, step, 3, 0.05);
			float volume = Mth.clamp(layout.stats.totalWeight() / 60f, 0.3f, 1.2f);
			world.playLocalSound(step.x, step.y, step.z, MechSounds.STEP, SoundSource.NEUTRAL, volume,
					0.8f + random.nextFloat() * 0.2f, false);
		}
		if (mech.isJet() && mech.getDeltaMovement().length() > 2.2) {
			Vec3 p = mech.position().add(0, 0.8, 0);
			world.addParticle(ParticleTypes.CLOUD, p.x, p.y, p.z, 0, 0, 0);
		}
	}

	@Override
	public void onEvent(MechEntity mech, int event, float a, float b) {
		ClientLevel world = (ClientLevel) mech.level();
		switch (event) {
			case EVENT_QUICK_BOOST, EVENT_JINK -> {
				this.burst(mech, world, true);
				CameraFx.kickFov(0.07f);
				CameraFx.addShake(0.55f);
			}
			case EVENT_LAND -> {
				this.landing(mech, world, a, true);
			}
			case EVENT_JUMP -> {
				this.play(world, mech.position(), MechSounds.JUMP, 0.9f, 1f);
				this.dust(world, mech.position(), 8, 0.15);
			}
			case EVENT_BRAKE -> {
				this.play(world, mech.position(), MechSounds.BRAKE, 1f, 0.9f + world.getRandom().nextFloat() * 0.2f);
				CameraFx.addShake(0.25f);
			}
			case EVENT_TRANSFORM -> {
				this.play(world, mech.position().add(0, 2, 0), MechSounds.TRANSFORM, 1.2f, 1f);
				CameraFx.addShake(0.2f);
			}
			default -> {
			}
		}
	}

	/** A short-lived exhaust particle. Vanilla flames live 12-44 ticks - long enough for a frame at
	 * boost speed to leave a trail that reaches back to the chase camera - so cap the lifetime. */
	private static void exhaust(ParticleOptions effect, double x, double y, double z, double vx, double vy, double vz, int maxAge) {
		Particle particle = Minecraft.getInstance().particleEngine.createParticle(effect, x, y, z, vx, vy, vz);
		if (particle != null) {
			particle.setLifetime(maxAge);
		}
	}

	/** Per-tick motion from positions (velocity isn't reliable for frames other clients pilot). */
	private static Vec3 motion(MechEntity mech) {
		return new Vec3(mech.getX() - mech.xo, mech.getY() - mech.yo, mech.getZ() - mech.zo);
	}

	private void burst(MechEntity mech, ClientLevel world, boolean local) {
		MechLayout layout = mech.getLayout();
		if (layout == null) {
			return;
		}
		RandomSource random = world.getRandom();
		Vec3 carry = motion(mech).scale(0.6);
		for (Vec3[] nozzle : this.nozzles(mech, layout)) {
			Vec3 p = nozzle[0];
			Vec3 d = nozzle[1];
			for (int i = 0; i < 6; i++) {
				exhaust(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z,
						carry.x + d.x * 0.6 + random.nextGaussian() * 0.12, carry.y + d.y * 0.6 + random.nextGaussian() * 0.12,
						carry.z + d.z * 0.6 + random.nextGaussian() * 0.12, 6 + random.nextInt(5));
			}
			world.addParticle(ParticleTypes.POOF, p.x, p.y, p.z, carry.x + d.x * 0.3, carry.y + d.y * 0.3, carry.z + d.z * 0.3);
		}
		this.play(world, mech.position().add(0, 2, 0), MechSounds.QUICK_BOOST, local ? 1.4f : 1.8f, 0.95f + random.nextFloat() * 0.1f);
	}

	private void landing(MechEntity mech, ClientLevel world, float impact, boolean local) {
		MechAnimator.of(mech).kick(impact);
		Vec3 p = mech.position();
		this.dust(world, p, (int) (6 + impact * 8), 0.25);
		this.play(world, p, MechSounds.LAND, Mth.clamp(0.6f + impact * 0.5f, 0.6f, 2f), 0.8f + world.getRandom().nextFloat() * 0.15f);
		if (local) {
			CameraFx.addShake(Math.min(1.6f, impact * 0.7f / mech.getStats().stability()));
		}
	}

	private void dust(ClientLevel world, Vec3 at, int count, double spread) {
		BlockPos below = BlockPos.containing(at.x, at.y - 0.2, at.z);
		BlockState state = world.getBlockState(below);
		RandomSource random = world.getRandom();
		ParticleOptions effect = state.isAir() ? ParticleTypes.POOF : new BlockParticleOption(ParticleTypes.BLOCK, state);
		for (int i = 0; i < count; i++) {
			world.addParticle(effect, at.x + random.nextGaussian() * 0.6, at.y + 0.1, at.z + random.nextGaussian() * 0.6,
					random.nextGaussian() * spread, 0.1 + random.nextFloat() * spread, random.nextGaussian() * spread);
		}
	}

	private void play(ClientLevel world, Vec3 at, SoundEvent sound, float volume, float pitch) {
		world.playLocalSound(at.x, at.y, at.z, sound, SoundSource.NEUTRAL, volume, pitch, false);
	}

	/** World position and exhaust direction of every booster nozzle (robot or jet placement). */
	private Vec3[][] nozzles(MechEntity mech, MechLayout layout) {
		MechPart booster = layout.assembly.part(MountSlot.BOOSTER);
		if (booster == null) {
			return new Vec3[0][];
		}
		boolean jet = mech.getTransformProgress(1f) > 0.5f && layout.canTransform;
		java.util.List<MechPart.Nozzle> list = jet && !booster.booster().jetNozzles().isEmpty()
				? booster.booster().jetNozzles() : booster.booster().nozzles();
		if (list.isEmpty()) {
			return new Vec3[0][];
		}
		Matrix4f place;
		if (jet) {
			place = booster.booster().jetNozzles().isEmpty() ? layout.jetMatrix(MountSlot.BOOSTER, "*") : new Matrix4f();
		} else {
			MechPart core = layout.assembly.part(MountSlot.CORE);
			place = new Matrix4f(layout.robot.get(MechLayout.Place.CORE)).translate(core.point("back"));
			place.translateLocal(0, MechAnimator.of(mech).pelvisDy, 0);
		}
		Quaternionf body = mech.tudursvehiclemod$getBodyOrientation(1f);
		Vec3 origin = mech.position().add(mech.tudursvehiclemod$getBodyFrameOffset(1f));
		Vec3[][] out = new Vec3[list.size()][];
		int i = 0;
		for (MechPart.Nozzle nozzle : list) {
			Vector3f p = body.transform(place.transformPosition(new Vector3f(nozzle.pos())));
			Vector3f d = body.transform(place.transformDirection(new Vector3f(nozzle.dir()))).normalize();
			out[i++] = new Vec3[]{origin.add(p.x, p.y, p.z), new Vec3(d.x, d.y, d.z)};
		}
		return out;
	}
}
