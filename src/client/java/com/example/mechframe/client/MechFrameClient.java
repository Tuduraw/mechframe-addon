package com.example.mechframe.client;

import com.example.mechframe.MechFrame;
import com.example.mechframe.client.fx.MechEffects;
import com.example.mechframe.client.hud.MechHud;
import com.example.mechframe.client.render.MechRenderer;
import com.example.mechframe.client.screen.GarageScreen;
import com.example.mechframe.entity.MechEntity;
import com.example.mechframe.net.MechNetworking;
import com.example.tudursvehiclemod.client.hud.HudVariableProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

/** Client entry: renderer, keys, camera/feel, HUD variables, garage screen, and the data sync. */
public class MechFrameClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(MechFrame.MECH, MechRenderer::new);
		HandledScreens.register(MechFrame.GARAGE_SCREEN, GarageScreen::new);
		com.example.mechframe.garage.FabricatorBlock.openScreen = pos ->
				net.minecraft.client.MinecraftClient.getInstance().setScreen(new com.example.mechframe.client.screen.FabricatorScreen(pos));
		MechClientConfig.load();
		MechInput.register();
		MechEntity.clientHooks = new MechEffects();
		HudVariableProvider.EVENT.register(new MechHud());
		ClientPlayNetworking.registerGlobalReceiver(MechNetworking.DataSyncPayload.ID, (payload, context) ->
				context.client().execute(payload::apply));
		ClientTickEvents.START_CLIENT_TICK.register(MechInput::tick);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			CameraFx.tick(client);
			MechHud.tickMarkers(client);
		});
		// Development builds only (-Pdevtest): the automated play test is not in a release jar.
		if (System.getProperty("mechframe.devtest") != null) {
			try {
				Class.forName("com.example.mechframe.devtest.DevTest").getMethod("init").invoke(null);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("devtest requested but not compiled in", e);
			}
		}
	}
}
