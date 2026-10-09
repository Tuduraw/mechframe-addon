package com.example.mechframe.client.render;

import com.example.mechframe.client.anim.MechPoser;
import com.example.mechframe.entity.MechEntity;
import com.example.tudursvehiclemod.client.render.DitherCutoutLayers;
import com.example.tudursvehiclemod.client.render.ObjModel;
import com.example.tudursvehiclemod.client.render.ObjModelLoader;
import com.example.tudursvehiclemod.client.render.VehicleEntityRenderer;
import com.example.tudursvehiclemod.client.render.VehicleRenderState;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;

import java.util.List;

/** Draws an assembled frame from its part models. Everything else the base renderer does (search
 * lights, wakes, destroyed tint) is kept by calling it with the model cleared - the frame's own
 * "model" id is only an in-memory hit mesh, never a drawable OBJ. The body transform is the base
 * renderer's own (render offset, body orientation incl. lean, body-frame offset via
 * getPositionOffset), so parts line up with seats, muzzles and hit detection. */
public class MechRenderer extends VehicleEntityRenderer {

	private static final Identifier NO_MODEL = Identifier.of("mechframe", "models/obj/none.obj");

	public MechRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public VehicleRenderState createRenderState() {
		return new MechRenderState();
	}

	@Override
	public boolean shouldRender(AbstractVehicleEntity entity, Frustum frustum, double x, double y, double z) {
		if (entity instanceof MechEntity mech && mech.getLayout() != null) {
			float r = Math.max(mech.getLayout().height, 6f);
			Box box = new Box(entity.getX() - r, entity.getY() - 2, entity.getZ() - r, entity.getX() + r, entity.getY() + r, entity.getZ() + r);
			return frustum.isVisible(box);
		}
		return super.shouldRender(entity, frustum, x, y, z);
	}

	@Override
	public void updateRenderState(AbstractVehicleEntity entity, VehicleRenderState state, float tickProgress) {
		super.updateRenderState(entity, state, tickProgress);
		if (state instanceof MechRenderState mechState && entity instanceof MechEntity mech) {
			mechState.items = MechPoser.pose(mech, tickProgress);
			state.model = NO_MODEL;
		}
	}

	@Override
	public void render(VehicleRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
		super.render(state, matrices, queue, cameraState);
		if (!(state instanceof MechRenderState mechState) || mechState.items.isEmpty()) {
			return;
		}
		int tint = state.destroyed ? 0xFF2A2A2A : state.tintColorOverride != -1 ? state.tintColorOverride : 0xFFFFFFFF;
		int overlay = OverlayTexture.DEFAULT_UV;
		matrices.push();
		matrices.translate(0.0, state.renderYOffset, 0.0);
		matrices.multiply(state.bodyOrientation);
		matrices.scale(state.scale, state.scale, state.scale);
		for (MechPoser.DrawItem item : mechState.items) {
			ObjModel model = ObjModelLoader.get(item.model()).orElse(null);
			if (model == null) {
				continue;
			}
			ObjModel.Triangles tris = item.group() == null ? model.getTriangles() : model.getGroup(item.group());
			if (tris == null || tris.isEmpty()) {
				continue;
			}
			RenderLayer layer = DitherCutoutLayers.entityDitherCutout(item.texture());
			matrices.push();
			matrices.peek().getPositionMatrix().mul(item.matrix());
			matrices.peek().getNormalMatrix().mul(item.matrix().normal(new org.joml.Matrix3f()));
			VehicleEntityRenderer.renderTriangles(queue, matrices, layer, tris, state.light, overlay, tint);
			matrices.pop();
		}
		matrices.pop();
	}

	public static class MechRenderState extends VehicleRenderState {
		public List<MechPoser.DrawItem> items = List.of();
	}
}
