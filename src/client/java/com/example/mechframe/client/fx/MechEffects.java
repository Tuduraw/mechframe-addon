package com.example.mechframe.client.fx;

import com.example.mechframe.MechSounds;
import com.example.mechframe.client.CameraFx;
import com.example.mechframe.client.anim.MechAnimator;
import com.example.mechframe.entity.MechClientHooks;
import com.example.mechframe.entity.MechEntity;
import com.example.mechframe.part.MechLayout;
import com.example.mechframe.part.MechPart;
import com.example.mechframe.part.MountSlot;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.particle.Particle;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
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
		ClientWorld world = (ClientWorld) mech.getEntityWorld();
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
		Random random = world.getRandom();
		boolean boosting = mech.isBoostingVisual();
		boolean hovering = mech.isHoveringVisual();
		// Exhaust inherits the frame's own motion: at boost speed a frame outruns its own flames,
		// which otherwise end up streaming straight through the chase camera as screen-sized blobs.
		Vec3d carry = motion(mech).multiply(0.9);
		if (boosting || hovering || mech.isAfterburnerVisual()) {
			for (Vec3d[] nozzle : this.nozzles(mech, layout)) {
				Vec3d p = nozzle[0];
				Vec3d d = nozzle[1];
				ParticleEffect flame = mech.isAfterburnerVisual() ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME;
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
				world.addParticleClient(ParticleTypes.ELECTRIC_SPARK, foot.pos.x, foot.pos.y + 0.1, foot.pos.z,
						random.nextGaussian() * 0.2, 0.15, random.nextGaussian() * 0.2);
				this.dust(world, foot.pos, 2, 0.12);
			}
			if (anim.feet.isEmpty()) {
				Vec3d p = mech.getEntityPos();
				this.dust(world, p, 4, 0.2);
			}
		}
		if (anim.mode == MechAnimator.Mode.SKATE && mech.age % 2 == 0) {
			for (MechAnimator.Foot foot : anim.feet) {
				this.dust(world, foot.pos, 1, 0.06);
			}
		}
		if (layout.stats.legType() == MechPart.LegType.TANK && anim.speed > 0.2f && mech.isGroundedVisual()) {
			this.dust(world, mech.getEntityPos(), 2, 0.1);
		}
		if (layout.stats.legType() == MechPart.LegType.HOVER && mech.isGroundedVisual() && mech.age % 2 == 0) {
			// Downwash: a ring of dust blown out from under the skirt, more at speed.
			Vec3d p = mech.getEntityPos();
			double r = layout.collisionWidth * 0.5;
			int n = anim.speed > 0.3f ? 4 : 2;
			for (int i = 0; i < n; i++) {
				double a = random.nextDouble() * Math.PI * 2;
				double dx = Math.cos(a), dz = Math.sin(a);
				world.addParticleClient(ParticleTypes.POOF, p.x + dx * r, p.y + 0.1, p.z + dz * r, dx * 0.12, 0.01, dz * 0.12);
			}
		}
		for (Vec3d step : anim.footfalls) {
			this.dust(world, step, 3, 0.05);
			float volume = MathHelper.clamp(layout.stats.totalWeight() / 60f, 0.3f, 1.2f);
			world.playSoundClient(step.x, step.y, step.z, MechSounds.STEP, SoundCategory.NEUTRAL, volume,
					0.8f + random.nextFloat() * 0.2f, false);
		}
		if (mech.isJet() && mech.getVelocity().length() > 2.2) {
			Vec3d p = mech.getEntityPos().add(0, 0.8, 0);
			world.addParticleClient(ParticleTypes.CLOUD, p.x, p.y, p.z, 0, 0, 0);
		}
	}

	@Override
	public void onEvent(MechEntity mech, int event, float a, float b) {
		ClientWorld world = (ClientWorld) mech.getEntityWorld();
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
				this.play(world, mech.getEntityPos(), MechSounds.JUMP, 0.9f, 1f);
				this.dust(world, mech.getEntityPos(), 8, 0.15);
			}
			case EVENT_BRAKE -> {
				this.play(world, mech.getEntityPos(), MechSounds.BRAKE, 1f, 0.9f + world.getRandom().nextFloat() * 0.2f);
				CameraFx.addShake(0.25f);
			}
			case EVENT_TRANSFORM -> {
				this.play(world, mech.getEntityPos().add(0, 2, 0), MechSounds.TRANSFORM, 1.2f, 1f);
				CameraFx.addShake(0.2f);
			}
			default -> {
			}
		}
	}

	/** A short-lived exhaust particle. Vanilla flames live 12-44 ticks - long enough for a frame at
	 * boost speed to leave a trail that reaches back to the chase camera - so cap the lifetime. */
	private static void exhaust(ParticleEffect effect, double x, double y, double z, double vx, double vy, double vz, int maxAge) {
		Particle particle = MinecraftClient.getInstance().particleManager.addParticle(effect, x, y, z, vx, vy, vz);
		if (particle != null) {
			particle.setMaxAge(maxAge);
		}
	}

	/** Per-tick motion from positions (velocity isn't reliable for frames other clients pilot). */
	private static Vec3d motion(MechEntity mech) {
		return new Vec3d(mech.getX() - mech.lastX, mech.getY() - mech.lastY, mech.getZ() - mech.lastZ);
	}

	private void burst(MechEntity mech, ClientWorld world, boolean local) {
		MechLayout layout = mech.getLayout();
		if (layout == null) {
			return;
		}
		Random random = world.getRandom();
		Vec3d carry = motion(mech).multiply(0.6);
		for (Vec3d[] nozzle : this.nozzles(mech, layout)) {
			Vec3d p = nozzle[0];
			Vec3d d = nozzle[1];
			for (int i = 0; i < 6; i++) {
				exhaust(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z,
						carry.x + d.x * 0.6 + random.nextGaussian() * 0.12, carry.y + d.y * 0.6 + random.nextGaussian() * 0.12,
						carry.z + d.z * 0.6 + random.nextGaussian() * 0.12, 6 + random.nextInt(5));
			}
			world.addParticleClient(ParticleTypes.POOF, p.x, p.y, p.z, carry.x + d.x * 0.3, carry.y + d.y * 0.3, carry.z + d.z * 0.3);
		}
		this.play(world, mech.getEntityPos().add(0, 2, 0), MechSounds.QUICK_BOOST, local ? 1.4f : 1.8f, 0.95f + random.nextFloat() * 0.1f);
	}

	private void landing(MechEntity mech, ClientWorld world, float impact, boolean local) {
		MechAnimator.of(mech).kick(impact);
		Vec3d p = mech.getEntityPos();
		this.dust(world, p, (int) (6 + impact * 8), 0.25);
		this.play(world, p, MechSounds.LAND, MathHelper.clamp(0.6f + impact * 0.5f, 0.6f, 2f), 0.8f + world.getRandom().nextFloat() * 0.15f);
		if (local) {
			CameraFx.addShake(Math.min(1.6f, impact * 0.7f / mech.getStats().stability()));
		}
	}

	private void dust(ClientWorld world, Vec3d at, int count, double spread) {
		BlockPos below = BlockPos.ofFloored(at.x, at.y - 0.2, at.z);
		BlockState state = world.getBlockState(below);
		Random random = world.getRandom();
		ParticleEffect effect = state.isAir() ? ParticleTypes.POOF : new BlockStateParticleEffect(ParticleTypes.BLOCK, state);
		for (int i = 0; i < count; i++) {
			world.addParticleClient(effect, at.x + random.nextGaussian() * 0.6, at.y + 0.1, at.z + random.nextGaussian() * 0.6,
					random.nextGaussian() * spread, 0.1 + random.nextFloat() * spread, random.nextGaussian() * spread);
		}
	}

	private void play(ClientWorld world, Vec3d at, SoundEvent sound, float volume, float pitch) {
		world.playSoundClient(at.x, at.y, at.z, sound, SoundCategory.NEUTRAL, volume, pitch, false);
	}

	/** World position and exhaust direction of every booster nozzle (robot or jet placement). */
	private Vec3d[][] nozzles(MechEntity mech, MechLayout layout) {
		MechPart booster = layout.assembly.part(MountSlot.BOOSTER);
		if (booster == null) {
			return new Vec3d[0][];
		}
		boolean jet = mech.getTransformProgress(1f) > 0.5f && layout.canTransform;
		java.util.List<MechPart.Nozzle> list = jet && !booster.booster().jetNozzles().isEmpty()
				? booster.booster().jetNozzles() : booster.booster().nozzles();
		if (list.isEmpty()) {
			return new Vec3d[0][];
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
		Vec3d origin = mech.getEntityPos().add(mech.tudursvehiclemod$getBodyFrameOffset(1f));
		Vec3d[][] out = new Vec3d[list.size()][];
		int i = 0;
		for (MechPart.Nozzle nozzle : list) {
			Vector3f p = body.transform(place.transformPosition(new Vector3f(nozzle.pos())));
			Vector3f d = body.transform(place.transformDirection(new Vector3f(nozzle.dir()))).normalize();
			out[i++] = new Vec3d[]{origin.add(p.x, p.y, p.z), new Vec3d(d.x, d.y, d.z)};
		}
		return out;
	}
}
