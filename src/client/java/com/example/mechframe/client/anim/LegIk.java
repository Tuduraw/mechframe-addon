package com.example.mechframe.client.anim;

import com.example.mechframe.part.MechPart;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;

/** Two-bone inverse kinematics for one leg chain (three-segment legs: see below), in the frame's root space.
 *
 * <p>The leg is authored at rest (hip -> knee -> ankle in leg-part space). Given where the ankle
 * must be, the solver places the knee on the side the chain's pole vector points to, then builds a
 * rigid matrix per segment that carries the REST geometry onto the solved pose: each segment's rest
 * frame (its direction plus the pole, Gram-Schmidt) is rotated onto its solved frame, so the
 * segment never twists about its own axis. A coxa (spider/quad legs) first swings the whole chain
 * about the vertical through its pivot toward the target, then the IK runs in that swung plane. The
 * foot keeps the orientation it is given (level with the world while the body leans).
 *
 * <p>Joint limits keep the leg mechanical: the hip swings at most maxSwing from rest, and the knee
 * stays between nearly straight and maxBend. A target outside them is not reached - the foot ends
 * where the limited leg puts it (the animator steps the foot before that is visible). */
public final class LegIk {

	/** Coxa (spider / quad legs) swing range each way from rest, radians. */
	static final float COXA_RANGE = (float) Math.toRadians(50.0);

	/** Three-segment legs: how much of the knee's fold change knee2 follows (negative: knee2 opens as
	 * the knee folds, which keeps the middle segment clear of the thigh). */
	static final float KNEE2_COUPLING = -0.3f;
	/** Three-segment legs: knee2 follows the knee this much as the leg stretches out. */
	static final float KNEE2_STRETCH_COUPLING = 1.4f;

	/** Three-segment legs: how far (degrees) the knee may fold beyond its rest fold. */
	static final float THREE_SEGMENT_KNEE_RANGE = 35f;

	private LegIk() {
	}

	/**
	 * @param pelvis   leg space -> root space for the hip end (translation/rotation of the pelvis)
	 * @param target   ankle target, root space
	 * @param footRot  root-space orientation the foot should keep
	 */
	public static void solve(MechPart.LegChain chain, Matrix4f pelvis, Vector3f target, Quaternionf footRot,
							 Map<String, Matrix4f> out) {
		solve(chain, pelvis, target, footRot, out, 125f, 60f);
	}

	/**
	 * @param maxBendDeg  how far the knee may fold from straight (0 = locked straight)
	 * @param maxSwingDeg how far the hip may swing the leg (hip -> ankle) away from its rest direction
	 */
	public static void solve(MechPart.LegChain chain, Matrix4f pelvis, Vector3f target, Quaternionf footRot,
							 Map<String, Matrix4f> out, float maxBendDeg, float maxSwingDeg) {
		Matrix4f base = new Matrix4f(pelvis);
		Vector3f pole = pelvis.transformDirection(new Vector3f(chain.pole()));
		Quaternionf coxaRot = new Quaternionf();
		if (chain.coxa().isPresent()) {
			Vector3f cp = pelvis.transformPosition(new Vector3f(chain.coxaPivot()));
			Vector3f restAnkle = pelvis.transformPosition(new Vector3f(chain.ankle()));
			double restAngle = Math.atan2(restAnkle.x - cp.x, restAnkle.z - cp.z);
			double targetAngle = Math.atan2(target.x - cp.x, target.z - cp.z);
			// The coxa turns the leg toward its foot, within its range (the animator re-steps a foot that
			// would need more, so a sharp turn is followed in several steps instead of a wrung-round leg).
			float delta = (float) Math.max(-COXA_RANGE, Math.min(COXA_RANGE, wrap(targetAngle - restAngle)));
			coxaRot.rotationY(delta);
			base = new Matrix4f().translation(cp).rotate(coxaRot).translate(-cp.x, -cp.y, -cp.z).mul(pelvis);
			coxaRot.transform(pole);
			out.put(chain.coxa().get(), new Matrix4f(base));
		}
		Vector3f hip = base.transformPosition(new Vector3f(chain.hip()));
		Vector3f knee0 = base.transformPosition(new Vector3f(chain.knee()));
		Vector3f ankle0 = base.transformPosition(new Vector3f(chain.ankle()));
		boolean three = chain.mid().isPresent();
		Vector3f knee20 = base.transformPosition(new Vector3f(chain.knee2()));
		// Three-segment legs: knee -> knee2 -> ankle is one "virtual" lower bone whose length follows the
		// fold at knee2, which is coupled to the knee's fold (both joints flex together, keeping the shape).
		float m = three ? chain.knee2().distance(chain.knee()) : 0f;
		float n = three ? chain.ankle().distance(chain.knee2()) : 0f;
		float theta2Rest = three ? new Vector3f(chain.knee()).sub(chain.knee2()).angle(new Vector3f(chain.ankle()).sub(chain.knee2())) : 0f;
		float l1 = chain.knee().distance(chain.hip());
		float l2 = chain.ankle().distance(chain.knee());

		Vector3f d = new Vector3f(target).sub(hip);
		float len = d.length();
		if (len < 1.0e-4f) {
			d.set(0, -1, 0);
			len = 1.0e-4f;
		}
		Vector3f f = new Vector3f(d).div(len);
		// Joint limits. Hip: the leg may swing at most maxSwing away from its rest direction (a planted
		// foot left far behind by a dash, or a target above the hip, no longer twists the leg around).
		Vector3f rest = new Vector3f(ankle0).sub(hip);
		if (rest.lengthSquared() > 1.0e-8f) {
			rest.normalize();
			float maxSwing = (float) Math.toRadians(maxSwingDeg);
			float angle = (float) Math.acos(clamp(f.dot(rest), -1f, 1f));
			if (angle > maxSwing) {
				Vector3f axis = new Vector3f(rest).cross(f);
				if (axis.lengthSquared() < 1.0e-8f) {
					axis = perpendicular(pole, rest).cross(rest);
				}
				axis.normalize();
				f = new Quaternionf().rotationAxis(maxSwing, axis).transform(new Vector3f(rest));
			}
		}
		// Knee: between nearly straight and maxBend folded - never folded flat or snapped straight.
		// Legs authored already well bent (spider legs) keep at least 30 degrees of fold beyond rest.
		Vector3f kh = new Vector3f(chain.hip()).sub(chain.knee());
		Vector3f ka = new Vector3f(chain.ankle()).sub(chain.knee());
		float betaRest = kh.angle(ka);
		float restBend = 180f - (float) Math.toDegrees(betaRest);
		float bend = Math.min(170f, Math.max(maxBendDeg, restBend + 30f));
		if (three) {
			// Double-jointed legs fold mostly at knee2; the knee itself only closes a little further than
			// at rest, so the middle segment never swings into the thigh.
			bend = Math.min(170f, restBend + THREE_SEGMENT_KNEE_RANGE);
		}
		float interiorMin = (float) Math.toRadians(180f - bend);
		float dist = len;
		float cosA = 1f;
		for (int it = 0; it < (three ? 6 : 1); it++) {
			float minDist = (float) Math.sqrt(Math.max(1.0e-6f, l1 * l1 + l2 * l2 - 2f * l1 * l2 * (float) Math.cos(interiorMin)));
			dist = clamp(len, Math.max(minDist, Math.abs(l1 - l2) + 1.0e-3f), (l1 + l2) * 0.985f);
			cosA = clamp((l1 * l1 + dist * dist - l2 * l2) / (2f * l1 * dist), -1f, 1f);
			if (three) {
				// Virtual knee interior now; knee2 follows half of its change.
				float beta = (float) Math.acos(clamp((l1 * l1 + l2 * l2 - dist * dist) / (2f * l1 * l2), -1f, 1f));
				// Folding: knee2 opens a little (keeps the middle segment off the thigh). Stretching: knee2
				// opens with the knee, so a reaching leg straightens at both joints.
				float coupling = beta > betaRest ? KNEE2_STRETCH_COUPLING : KNEE2_COUPLING;
				float theta2 = clamp(theta2Rest + coupling * (beta - betaRest), 0.3f, 3.05f);
				l2 = (float) Math.sqrt(m * m + n * n - 2f * m * n * Math.cos(theta2));
			}
		}
		if (three) {
			// Final pass with the settled virtual length, so every segment keeps its own length.
			float minDist = (float) Math.sqrt(Math.max(1.0e-6f, l1 * l1 + l2 * l2 - 2f * l1 * l2 * (float) Math.cos(interiorMin)));
			dist = clamp(len, Math.max(minDist, Math.abs(l1 - l2) + 1.0e-3f), (l1 + l2) * 0.985f);
			cosA = clamp((l1 * l1 + dist * dist - l2 * l2) / (2f * l1 * dist), -1f, 1f);
		}
		float sinA = (float) Math.sqrt(1f - cosA * cosA);
		Vector3f s = perpendicular(pole, f);
		Vector3f knee = new Vector3f(hip).fma(cosA * l1, f).fma(sinA * l1, s);
		Vector3f ankle = new Vector3f(hip).fma(dist, f);

		Matrix3f r1 = alignFrames(new Vector3f(knee0).sub(hip), new Vector3f(knee).sub(hip), pole);
		out.put(chain.upper(), new Matrix4f().translation(hip).mul(new Matrix4f().set(r1))
				.translate(-hip.x, -hip.y, -hip.z).mul(base));
		Vector3f joint = knee;
		Vector3f joint0 = knee0;
		if (three) {
			// knee2: on the same side of the knee -> ankle line as at rest, in the leg's plane.
			Vector3f ka0 = new Vector3f(ankle0).sub(knee0).normalize();
			Vector3f side0 = perpendicular(new Vector3f(knee20).sub(knee0), ka0);
			float sense = Math.signum(side0.dot(perpendicular(pole, ka0)));
			if (sense == 0f) {
				sense = 1f;
			}
			Vector3f kaNow = new Vector3f(ankle).sub(knee);
			float lk = Math.max(kaNow.length(), 1.0e-4f);
			Vector3f u = kaNow.div(lk);
			Vector3f side = perpendicular(pole, u).mul(sense);
			float ca = clamp((m * m + lk * lk - n * n) / (2f * m * lk), -1f, 1f);
			float sa = (float) Math.sqrt(1f - ca * ca);
			Vector3f knee2 = new Vector3f(knee).fma(ca * m, u).fma(sa * m, side);
			Matrix3f rm = alignFrames(new Vector3f(knee20).sub(knee0), new Vector3f(knee2).sub(knee), pole);
			out.put(chain.mid().get(), new Matrix4f().translation(knee).mul(new Matrix4f().set(rm))
					.translate(-knee0.x, -knee0.y, -knee0.z).mul(base));
			joint = knee2;
			joint0 = knee20;
		}
		Matrix3f r2 = alignFrames(new Vector3f(ankle0).sub(joint0), new Vector3f(ankle).sub(joint), pole);
		out.put(chain.lower(), new Matrix4f().translation(joint).mul(new Matrix4f().set(r2))
				.translate(-joint0.x, -joint0.y, -joint0.z).mul(base));
		if (chain.foot().isPresent()) {
			// Foot: keep footRot (world-level), plus the coxa swing, about the solved ankle.
			// v -> ankle + q * Rp * (v - restAnkle), Rp = the pelvis' own rotation part (identity for walkers).
			Quaternionf q = new Quaternionf(footRot).mul(coxaRot);
			Vector3f a = new Vector3f(chain.ankle());
			Matrix4f pelvisRot = new Matrix4f(pelvis).setTranslation(0, 0, 0);
			out.put(chain.foot().get(), new Matrix4f().translation(ankle).rotate(q).mul(pelvisRot)
					.translate(-a.x, -a.y, -a.z));
		}
	}

	/** Rotation carrying direction `from` onto `to` while keeping `pole` on the same side - no twist. */
	static Matrix3f alignFrames(Vector3f from, Vector3f to, Vector3f pole) {
		Matrix3f a = frame(from, pole);
		Matrix3f b = frame(to, pole);
		return b.mul(a.transpose());
	}

	/** Columns x, y, z with z along dir and x toward the pole. */
	static Matrix3f frame(Vector3f dir, Vector3f pole) {
		Vector3f z = new Vector3f(dir);
		if (z.lengthSquared() < 1.0e-10f) {
			z.set(0, -1, 0);
		}
		z.normalize();
		Vector3f x = perpendicular(pole, z);
		Vector3f y = new Vector3f(z).cross(x);
		return new Matrix3f(x, y, z);
	}

	/** The component of v perpendicular to unit vector n, normalized (with a fallback axis). */
	static Vector3f perpendicular(Vector3f v, Vector3f n) {
		Vector3f p = new Vector3f(v).fma(-v.dot(n), n);
		if (p.lengthSquared() < 1.0e-8f) {
			Vector3f alt = Math.abs(n.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(0, 0, 1);
			p = new Vector3f(alt).fma(-alt.dot(n), n);
		}
		return p.normalize();
	}

	private static float clamp(float v, float lo, float hi) {
		return v < lo ? lo : (v > hi ? hi : v);
	}

	private static double wrap(double a) {
		while (a > Math.PI) {
			a -= Math.PI * 2;
		}
		while (a < -Math.PI) {
			a += Math.PI * 2;
		}
		return a;
	}
}
