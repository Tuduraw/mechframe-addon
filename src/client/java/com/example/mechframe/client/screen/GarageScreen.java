package com.example.mechframe.client.screen;

import com.example.mechframe.entity.MechEntity;
import com.example.mechframe.garage.GarageBlockEntity;
import com.example.mechframe.garage.GarageScreenHandler;
import com.example.mechframe.item.MechPartItem;
import com.example.mechframe.net.MechNetworking;
import com.example.mechframe.part.Assembly;
import com.example.mechframe.part.MechLayout;
import com.example.mechframe.part.MechPart;
import com.example.mechframe.part.MechStats;
import com.example.mechframe.part.MountSlot;
import com.example.mechframe.part.PartRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Garage: part storage on the left, the refit panel on the right.
 *
 * <p>Pick a mount, then pick from what is available for it - the frame's own stock part (always,
 * free) and every compatible part stored in this garage. Hovering an option previews the resulting
 * stats next to the current ones. Changes are requested from the server (AssemblePayload) and appear
 * once it applies them. */
public class GarageScreen extends HandledScreen<GarageScreenHandler> {

	private static final int LEFT_W = 176;
	private static final int PANEL_W = 240;
	/** Panel content: inner width, and the rows (relative to the panel top). Everything fits the
	 * smallest common scaled GUI (427 x 240): 176 + 240 = 416 wide, 222 high. */
	private static final int INNER_W = PANEL_W - 12;
	private static final int MOUNTS_Y = 21;
	private static final int MOUNT_W = 74;
	private static final int CURRENT_Y = 66;
	private static final int OPTIONS_Y = 77;
	private static final int STATS_Y = 159;
	private static final int RESET_Y = 205;
	private static final int PANEL_BG = 0xFF2B3036;
	private static final int PANEL_EDGE = 0xFF59636E;
	private static final int TEXT = 0xFFE6E6E6;
	private static final int DIM = 0xFF9AA3AD;
	private static final int GOOD = 0xFF7CE07C;
	private static final int BAD = 0xFFFF7070;
	private static final int OPTION_ROWS = 6;

	private final List<MechEntity> frames = new ArrayList<>();
	private int frameIndex;
	private MountSlot selected = MountSlot.CORE;
	private int scroll;
	private String lastKey = "";
	private Assembly preview;

	private record Option(Identifier part, String label, boolean stock) {
	}

	public GarageScreen(GarageScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = LEFT_W + PANEL_W;
		this.backgroundHeight = 222;
		this.playerInventoryTitleY = GarageScreenHandler.PLAYER_Y - 11;
	}

	@Override
	protected void init() {
		super.init();
		this.rebuild();
	}

	private MechEntity frame() {
		if (this.frames.isEmpty()) {
			return null;
		}
		this.frameIndex = Math.floorMod(this.frameIndex, this.frames.size());
		MechEntity mech = this.frames.get(this.frameIndex);
		return mech.isRemoved() ? null : mech;
	}

	private void findFrames() {
		this.frames.clear();
		if (this.client == null || this.client.world == null) {
			return;
		}
		Vec3d center = Vec3d.ofCenter(this.handler.getPos());
		double r = GarageBlockEntity.RANGE;
		this.frames.addAll(this.client.world.getEntitiesByClass(MechEntity.class, new Box(center, center).expand(r),
				m -> m.squaredDistanceTo(center) <= r * r && m.getFaction() != com.example.mechframe.ai.Faction.ENEMY));
		this.frames.sort((a, b) -> Double.compare(a.squaredDistanceTo(center), b.squaredDistanceTo(center)));
	}

	/** Re-creates the buttons; called whenever the frame, its assembly or the storage changes. */
	private void rebuild() {
		this.clearChildren();
		this.findFrames();
		int x0 = this.x + LEFT_W + 6;
		int y0 = this.y + 6;
		MechEntity mech = this.frame();
		this.addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
			this.frameIndex--;
			this.rebuild();
		}).dimensions(x0, y0, 14, 12).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
			this.frameIndex++;
			this.rebuild();
		}).dimensions(x0 + INNER_W - 14, y0, 14, 12).build());
		if (mech == null) {
			this.lastKey = "none";
			return;
		}
		Assembly assembly = mech.getAssembly();
		// 3 x 3 grid of mounts; the installed part is in the tooltip and on the line below the grid.
		MountSlot[] mounts = MountSlot.values();
		for (int i = 0; i < mounts.length; i++) {
			MountSlot mount = mounts[i];
			MechPart part = assembly.part(mount);
			ButtonWidget button = ButtonWidget.builder(Text.translatable("mount.mechframe." + mount.id()), b -> {
				this.selected = mount;
				this.scroll = 0;
				this.rebuild();
			}).dimensions(x0 + (i % 3) * (MOUNT_W + 3), this.y + MOUNTS_Y + (i / 3) * 14, MOUNT_W, 13)
					.tooltip(Tooltip.of(Text.literal(part != null ? part.displayName() : "-"))).build();
			button.active = mount != this.selected;
			this.addDrawableChild(button);
		}
		List<Option> options = this.options(mech, this.selected);
		int maxScroll = Math.max(0, options.size() - OPTION_ROWS);
		this.scroll = Math.max(0, Math.min(this.scroll, maxScroll));
		int oy = this.y + OPTIONS_Y;
		for (int i = 0; i < OPTION_ROWS && i + this.scroll < options.size(); i++) {
			Option option = options.get(i + this.scroll);
			Identifier current = assembly.get(this.selected);
			boolean installed = option.part == null ? current == null : option.part.equals(current);
			ButtonWidget button = ButtonWidget.builder(Text.literal(option.label), b -> this.send(mech, this.selected, option.part))
					.dimensions(x0, oy + i * 13, INNER_W - 16, 12).build();
			button.active = !installed && !assembly.isLocked(this.selected);
			this.addDrawableChild(button);
		}
		ButtonWidget up = ButtonWidget.builder(Text.literal("^"), b -> {
			this.scroll--;
			this.rebuild();
		}).dimensions(x0 + INNER_W - 14, oy, 14, 12).build();
		up.active = this.scroll > 0;
		this.addDrawableChild(up);
		ButtonWidget down = ButtonWidget.builder(Text.literal("v"), b -> {
			this.scroll++;
			this.rebuild();
		}).dimensions(x0 + INNER_W - 14, oy + 13 * (OPTION_ROWS - 1), 14, 12).build();
		down.active = this.scroll < maxScroll;
		this.addDrawableChild(down);
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mechframe.reset_stock"), b ->
				ClientPlayNetworking.send(new MechNetworking.AssemblePayload(this.handler.getPos(), mech.getId(), "",
						MechNetworking.AssemblePayload.RESET)))
				.dimensions(x0 + INNER_W - 100, this.y + RESET_Y, 100, 12).build());
		this.lastKey = this.stateKey();
	}

	private void send(MechEntity mech, MountSlot mount, Identifier part) {
		ClientPlayNetworking.send(new MechNetworking.AssemblePayload(this.handler.getPos(), mech.getId(), mount.id(),
				part == null ? "" : part.toString()));
	}

	/** Stock part first, then every compatible part in storage (with counts), then "empty". */
	private List<Option> options(MechEntity mech, MountSlot mount) {
		MechPart builtIn = mech.getAssembly().integrated(mount);
		if (mech.getAssembly().isLocked(mount)) {
			// Weapons built into the arms: shown, but nothing else can go there.
			return List.of(new Option(builtIn != null ? builtIn.id() : null, "[" + Text.translatable("gui.mechframe.integrated").getString() + "] "
					+ (builtIn != null ? builtIn.displayName() : "?"), false));
		}
		Map<Identifier, Integer> stored = new LinkedHashMap<>();
		for (int i = 0; i < GarageBlockEntity.SIZE; i++) {
			ItemStack stack = this.handler.getSlot(i).getStack();
			MechPart part = MechPartItem.part(stack);
			if (part != null && mount.accepts(part)) {
				stored.merge(part.id(), stack.getCount(), Integer::sum);
			}
		}
		List<Option> list = new ArrayList<>();
		Identifier stock = mech.getStockAssembly().get(mount);
		MechPart stockPart = PartRegistry.getOrNull(stock);
		if (stockPart != null) {
			list.add(new Option(stock, "[" + Text.translatable("gui.mechframe.stock").getString() + "] " + stockPart.displayName(), true));
		}
		for (Map.Entry<Identifier, Integer> e : stored.entrySet()) {
			MechPart part = PartRegistry.getOrNull(e.getKey());
			if (part != null && !e.getKey().equals(stock)) {
				list.add(new Option(e.getKey(), part.displayName() + " x" + e.getValue(), false));
			}
		}
		if (!mount.required()) {
			list.add(new Option(null, "(" + Text.translatable("gui.mechframe.empty").getString() + ")", false));
		}
		return list;
	}

	private String stateKey() {
		MechEntity mech = this.frame();
		StringBuilder sb = new StringBuilder();
		sb.append(this.frames.size()).append('|');
		if (mech != null) {
			sb.append(mech.getId()).append(mech.getAssembly().encode());
		}
		for (int i = 0; i < GarageBlockEntity.SIZE; i++) {
			ItemStack s = this.handler.getSlot(i).getStack();
			if (!s.isEmpty()) {
				sb.append(i).append(MechPartItem.partId(s)).append(s.getCount());
			}
		}
		return sb.toString();
	}

	@Override
	protected void handledScreenTick() {
		super.handledScreenTick();
		if (this.client != null && this.client.world != null && this.client.world.getTime() % 10 == 0) {
			this.findFrames();
		}
		if (!this.stateKey().equals(this.lastKey)) {
			this.rebuild();
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.preview = null;
		MechEntity mech = this.frame();
		if (mech != null) {
			// Preview the option under the cursor.
			List<Option> options = this.options(mech, this.selected);
			int x0 = this.x + LEFT_W + 6;
			int oy = this.y + OPTIONS_Y;
			if (mouseX >= x0 && mouseX < x0 + INNER_W - 16 && mouseY >= oy && mouseY < oy + OPTION_ROWS * 13) {
				int i = (mouseY - oy) / 13 + this.scroll;
				if (i >= 0 && i < options.size()) {
					Option option = options.get(i);
					this.preview = mech.getAssembly().with(this.selected, option.part, option.stock);
				}
			}
		}
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int x = this.x;
		int y = this.y;
		context.fill(x, y, x + LEFT_W, y + this.backgroundHeight, 0xFFC6C6C6);
		for (int row = 0; row < 6; row++) {
			for (int col = 0; col < 9; col++) {
				slot(context, x + GarageScreenHandler.STORAGE_X + col * 18, y + GarageScreenHandler.STORAGE_Y + row * 18);
			}
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				slot(context, x + 8 + col * 18, y + GarageScreenHandler.PLAYER_Y + row * 18);
			}
		}
		for (int col = 0; col < 9; col++) {
			slot(context, x + 8 + col * 18, y + GarageScreenHandler.PLAYER_Y + 58);
		}
		int px = x + LEFT_W;
		context.fill(px, y, px + PANEL_W, y + this.backgroundHeight, PANEL_EDGE);
		context.fill(px + 2, y + 2, px + PANEL_W - 2, y + this.backgroundHeight - 2, PANEL_BG);
	}

	/** Vanilla-style recessed slot (the item itself is drawn at x, y). */
	private static void slot(DrawContext context, int x, int y) {
		context.fill(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
		context.fill(x, y, x + 17, y + 17, 0xFFFFFFFF);
		context.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		context.drawText(this.textRenderer, this.title, 8, 6, 0xFF404040, false);
		context.drawText(this.textRenderer, this.playerInventoryTitle, 8, this.playerInventoryTitleY, 0xFF404040, false);
		int x0 = LEFT_W + 6;
		MechEntity mech = this.frame();
		if (mech == null) {
			context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("gui.mechframe.no_frame"), LEFT_W + PANEL_W / 2, 8, TEXT);
			return;
		}
		String name = mech.getDefinition().displayName(mech.getVehicleDefinitionId());
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(name + " (" + (this.frameIndex + 1) + "/" + this.frames.size() + ")"),
				LEFT_W + PANEL_W / 2, 8, TEXT);
		MechPart current = mech.getAssembly().part(this.selected);
		Text currentLine = Text.translatable("mount.mechframe." + this.selected.id()).append(": ")
				.append(current != null ? current.displayName() : "-");
		context.drawText(this.textRenderer, this.textRenderer.trimToWidth(currentLine, INNER_W).getString(), x0, CURRENT_Y, DIM, false);
		MechStats now = MechStats.compute(mech.getAssembly());
		MechStats next = this.preview != null ? MechStats.compute(this.preview) : null;
		MechLayout nextLayout = this.preview != null ? MechLayout.of(this.preview) : null;
		int lx = x0;
		int rx = x0 + INNER_W / 2 + 2;
		int sy = STATS_Y;
		this.stat(context, lx, sy, "AP", now.maxAp(), next == null ? null : next.maxAp(), "%.0f", true);
		this.stat(context, rx, sy, "SPD", now.walkSpeed() * 72f, next == null ? null : next.walkSpeed() * 72f, "%.0f", true);
		sy += 11;
		this.stat(context, lx, sy, "WT", now.totalWeight(), next == null ? null : next.totalWeight(), "%.1f", false);
		this.stat(context, rx, sy, "BST", now.boostSpeed() * 72f, next == null ? null : next.boostSpeed() * 72f, "%.0f", true);
		sy += 11;
		this.loadLine(context, lx, sy, now, next);
		this.stat(context, rx, sy, "QB", now.qbSpeed(), next == null ? null : next.qbSpeed(), "%.2f", true);
		sy += 11;
		this.stat(context, lx, sy, "EN", now.enCapacity(), next == null ? null : next.enCapacity(), "%.0f", true);
		boolean canT = mech.getLayout() != null && mech.getLayout().canTransform;
		Text t = Text.translatable(canT ? "gui.mechframe.transform_yes" : "gui.mechframe.transform_no");
		int color = canT ? GOOD : DIM;
		if (nextLayout != null && nextLayout.canTransform != canT) {
			t = Text.translatable(nextLayout.canTransform ? "gui.mechframe.transform_yes" : "gui.mechframe.transform_no");
			color = nextLayout.canTransform ? GOOD : BAD;
		}
		context.drawText(this.textRenderer, t, rx, sy, color, false);
	}

	private int stat(DrawContext context, int x, int y, String label, float now, Float next, String fmt, boolean higherBetter) {
		context.drawText(this.textRenderer, label, x, y, DIM, false);
		context.drawText(this.textRenderer, String.format(Locale.ROOT, fmt, now), x + 22, y, TEXT, false);
		if (next != null && Math.abs(next - now) > 1e-3f) {
			boolean better = higherBetter ? next > now : next < now;
			context.drawText(this.textRenderer, ">" + String.format(Locale.ROOT, fmt, next), x + 58, y, better ? GOOD : BAD, false);
		}
		return y + 11;
	}

	private int loadLine(DrawContext context, int x, int y, MechStats now, MechStats next) {
		MechStats s = next != null ? next : now;
		boolean over = s.legLoad() > s.legLoadLimit();
		context.drawText(this.textRenderer, "LD", x, y, DIM, false);
		context.drawText(this.textRenderer, String.format(Locale.ROOT, "%.0f/%.0f", s.legLoad(), s.legLoadLimit()), x + 22, y,
				over ? BAD : (next != null ? GOOD : TEXT), false);
		return y + 11;
	}
}
