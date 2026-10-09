package com.example.mechframe.client.screen;

import com.example.mechframe.net.MechNetworking;
import com.example.mechframe.recipe.MechRecipe;
import com.example.mechframe.recipe.MechRecipes;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mech Fabricator: the recipe list on the left (grouped by category, filtered by the search box,
 * scrolled with the wheel), the selected recipe on the right - what it makes, its materials with
 * "have / need" (red when short) and the Fabricate button. Crafting takes the materials from the whole
 * inventory (MechNetworking.FabricatePayload); creative players need none.
 */
public class FabricatorScreen extends Screen {

	private static final int W = 400;
	private static final int H = 222;
	private static final int LIST_W = 172;
	private static final int ROW = 18;
	private static final int HEADER = 12;
	private static final int LIST_Y = 22;
	private static final int BG = 0xFF2B3036;
	private static final int EDGE = 0xFF59636E;
	private static final int TEXT = 0xFFE6E6E6;
	private static final int DIM = 0xFF9AA3AD;
	private static final int GOOD = 0xFF7CE07C;
	private static final int BAD = 0xFFFF7070;
	private static final int SEL = 0xFF46607A;
	private static final int HOVER = 0xFF3A4652;
	private static final int HEAD = 0xFFE0B040;

	private final BlockPos pos;
	private TextFieldWidget search;
	private ButtonWidget craft;
	private final List<Object> rows = new ArrayList<>();
	private MechRecipe selected;
	private int scroll;
	private int x;
	private int y;
	private int lastRecipeCount = -1;

	public FabricatorScreen(BlockPos pos) {
		super(Text.translatable("block.mechframe.fabricator"));
		this.pos = pos;
	}

	@Override
	protected void init() {
		this.x = (this.width - W) / 2;
		this.y = (this.height - H) / 2;
		String old = this.search != null ? this.search.getText() : "";
		this.search = new TextFieldWidget(this.textRenderer, this.x + 6, this.y + 6, LIST_W - 8, 12,
				Text.translatable("gui.mechframe.fab.search"));
		this.search.setText(old);
		this.search.setPlaceholder(Text.translatable("gui.mechframe.fab.search").copy().withColor(DIM));
		this.search.setChangedListener(s -> {
			this.scroll = 0;
			this.rebuildRows();
		});
		this.addDrawableChild(this.search);
		this.craft = ButtonWidget.builder(Text.translatable("gui.mechframe.fab.craft"), b -> {
			if (this.selected != null) {
				ClientPlayNetworking.send(new MechNetworking.FabricatePayload(this.pos, this.selected.id().toString()));
			}
		}).dimensions(this.x + W - 96, this.y + H - 20, 90, 14).build();
		this.addDrawableChild(this.craft);
		this.rebuildRows();
	}

	private void rebuildRows() {
		this.rows.clear();
		String q = this.search == null ? "" : this.search.getText().trim().toLowerCase(Locale.ROOT);
		String category = null;
		for (MechRecipe r : MechRecipes.sorted()) {
			ItemStack out = r.result().stack();
			String name = out.getName().getString();
			if (!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q) && !r.id().toString().contains(q)
					&& !categoryName(r.category()).toLowerCase(Locale.ROOT).contains(q)) {
				continue;
			}
			if (!r.category().equals(category)) {
				category = r.category();
				this.rows.add(category);
			}
			this.rows.add(r);
		}
		if (this.selected != null && MechRecipes.get(this.selected.id()) == null) {
			this.selected = null;
		}
		this.lastRecipeCount = MechRecipes.sorted().size();
	}

	private static String categoryName(String category) {
		String key = "gui.mechframe.fab.category." + category;
		if (I18n.hasTranslation(key)) {
			return I18n.translate(key);
		}
		return category.isEmpty() ? "" : Character.toUpperCase(category.charAt(0)) + category.substring(1);
	}

	private int rowHeight(Object row) {
		return row instanceof String ? HEADER : ROW;
	}

	private int listHeight() {
		return H - LIST_Y - 6;
	}

	private int contentHeight() {
		int h = 0;
		for (Object r : this.rows) {
			h += this.rowHeight(r);
		}
		return h;
	}

	@Override
	public void tick() {
		super.tick();
		if (MechRecipes.sorted().size() != this.lastRecipeCount) {
			this.rebuildRows();
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseX >= this.x && mouseX < this.x + LIST_W && mouseY >= this.y + LIST_Y && mouseY < this.y + H) {
			int max = Math.max(0, this.contentHeight() - this.listHeight());
			this.scroll = Math.max(0, Math.min(max, this.scroll - (int) Math.round(verticalAmount * ROW)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		Object row = this.rowAt(click.x(), click.y());
		if (row instanceof MechRecipe r) {
			this.selected = r;
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	private Object rowAt(double mx, double my) {
		int top = this.y + LIST_Y;
		if (mx < this.x + 4 || mx >= this.x + LIST_W - 4 || my < top || my >= top + this.listHeight()) {
			return null;
		}
		int yy = top - this.scroll;
		for (Object r : this.rows) {
			int h = this.rowHeight(r);
			if (my >= yy && my < yy + h) {
				return r;
			}
			yy += h;
		}
		return null;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		int x = this.x;
		int y = this.y;
		context.fill(x, y, x + W, y + H, EDGE);
		context.fill(x + 2, y + 2, x + LIST_W - 1, y + H - 2, BG);
		context.fill(x + LIST_W + 1, y + 2, x + W - 2, y + H - 2, BG);

		// recipe list
		int top = y + LIST_Y;
		context.enableScissor(x + 2, top, x + LIST_W - 1, top + this.listHeight());
		int yy = top - this.scroll;
		Object hover = this.rowAt(mouseX, mouseY);
		ItemStack hoverStack = ItemStack.EMPTY;
		for (Object r : this.rows) {
			int h = this.rowHeight(r);
			if (yy + h >= top && yy < top + this.listHeight()) {
				if (r instanceof String cat) {
					context.drawText(this.textRenderer, categoryName(cat), x + 6, yy + 2, HEAD, false);
				} else if (r instanceof MechRecipe rec) {
					if (rec == this.selected) {
						context.fill(x + 4, yy, x + LIST_W - 4, yy + h, SEL);
					} else if (rec == hover) {
						context.fill(x + 4, yy, x + LIST_W - 4, yy + h, HOVER);
					}
					ItemStack out = rec.result().stack();
					context.drawItem(out, x + 6, yy + 1);
					boolean can = this.client != null && this.client.player != null && MechRecipes.canCraft(this.client.player, rec);
					String name = this.textRenderer.trimToWidth(out.getName().getString(), LIST_W - 34);
					context.drawText(this.textRenderer, name, x + 26, yy + 5, can ? TEXT : DIM, false);
					if (rec == hover) {
						hoverStack = out;
					}
				}
			}
			yy += h;
		}
		context.disableScissor();
		int content = this.contentHeight();
		if (content > this.listHeight()) {
			int barH = Math.max(12, this.listHeight() * this.listHeight() / content);
			int barY = top + (this.listHeight() - barH) * this.scroll / Math.max(1, content - this.listHeight());
			context.fill(x + LIST_W - 4, barY, x + LIST_W - 2, barY + barH, EDGE);
		}

		// selected recipe
		int px = x + LIST_W + 8;
		int pw = W - LIST_W - 16;
		if (this.selected == null) {
			context.drawText(this.textRenderer, Text.translatable("gui.mechframe.fab.pick"), px, y + 10, DIM, false);
			this.craft.active = false;
		} else {
			ItemStack out = this.selected.result().stack();
			context.drawItem(out, px, y + 6);
			if (out.getCount() > 1) {
				context.drawStackOverlay(this.textRenderer, out, px, y + 6);
			}
			context.drawText(this.textRenderer, this.textRenderer.trimToWidth(out.getName().getString(), pw - 22), px + 20, y + 10, TEXT, false);
			int ly = y + 26;
			if (this.client != null) {
				List<Text> lines = Screen.getTooltipFromItem(this.client, out);
				for (int i = 1; i < lines.size() && i < 8; i++) {
					context.drawText(this.textRenderer, this.textRenderer.trimToWidth(lines.get(i), pw).getString(), px, ly, DIM, false);
					ly += 10;
				}
			}
			ly = Math.max(ly + 4, y + 86);
			context.drawText(this.textRenderer, Text.translatable("gui.mechframe.fab.materials"), px, ly, HEAD, false);
			ly += 12;
			long t = Util.getMeasuringTimeMs() / 1000;
			boolean creative = this.client != null && this.client.player != null && this.client.player.isCreative();
			for (MechRecipe.Input in : this.selected.ingredients()) {
				List<ItemStack> show = in.display();
				ItemStack icon = show.isEmpty() ? ItemStack.EMPTY : show.get((int) (t % show.size()));
				context.drawItem(icon, px, ly);
				int have = this.client != null && this.client.player != null ? MechRecipes.count(this.client.player.getInventory(), in) : 0;
				String label = in.tag() != null ? "#" + in.tag() : icon.getName().getString();
				context.drawText(this.textRenderer, this.textRenderer.trimToWidth(label, pw - 70), px + 20, ly + 4, TEXT, false);
				String cnt = have + " / " + in.count();
				context.drawText(this.textRenderer, cnt, px + pw - this.textRenderer.getWidth(cnt), ly + 4,
						creative || have >= in.count() ? GOOD : BAD, false);
				if (mouseX >= px && mouseX < px + 16 && mouseY >= ly && mouseY < ly + 16) {
					hoverStack = icon;
				}
				ly += 18;
				if (ly > y + H - 40) {
					break;
				}
			}
			this.craft.active = this.client != null && this.client.player != null && MechRecipes.canCraft(this.client.player, this.selected);
			if (creative) {
				context.drawText(this.textRenderer, Text.translatable("gui.mechframe.fab.creative"), px, y + H - 16, DIM, false);
			}
		}
		super.render(context, mouseX, mouseY, delta);
		if (!hoverStack.isEmpty()) {
			context.drawItemTooltip(this.textRenderer, hoverStack, mouseX, mouseY);
		}
	}

	/** Development test hook: select a recipe by id. */
	public void selectForTest(net.minecraft.util.Identifier id) {
		this.selected = MechRecipes.get(id);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
