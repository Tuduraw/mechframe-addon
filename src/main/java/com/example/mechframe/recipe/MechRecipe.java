package com.example.mechframe.recipe;

import com.example.mechframe.item.MechItems;
import com.example.mechframe.item.MechPartItem;
import com.example.mechframe.part.MechPart;
import com.example.mechframe.part.PartRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * One Mech Fabricator recipe (data/&lt;ns&gt;/mech_recipe/&lt;name&gt;.json, or the "recipe" object of a part JSON).
 *
 * <pre>
 * {
 *   "category": "legs",                                   // optional - list grouping
 *   "result": {"part": "mfx:lg_wisp"},                    // or {"item": "minecraft:...", "count": 2}
 *   "ingredients": [
 *     {"item": "minecraft:iron_ingot", "count": 6},
 *     {"tag": "minecraft:planks", "count": 2},
 *     {"part": "mechframe:cr_swift"}                      // a part item with that part id
 *   ]
 * }
 * </pre>
 *
 * @param id        recipe id (file id, or the part id for an inline recipe)
 * @param category  list grouping (the result part's slot by default, "frames" / "items" otherwise)
 * @param order     sort key within the category (the part's tier, else 0)
 */
public record MechRecipe(Identifier id, String category, int order, Output result, List<Input> ingredients) {

	/** What a recipe makes: a part item (part != null) or a plain item. */
	public record Output(Identifier part, Identifier item, int count) {

		public ItemStack stack() {
			if (this.part != null) {
				MechPart p = PartRegistry.getOrNull(this.part);
				if (p == null) {
					return ItemStack.EMPTY;
				}
				ItemStack s = MechPartItem.stackFor(p);
				s.setCount(Math.max(1, Math.min(this.count, s.getMaxStackSize())));
				return s;
			}
			Item it = BuiltInRegistries.ITEM.getValue(this.item);
			if (it == Items.AIR) {
				return ItemStack.EMPTY;
			}
			return new ItemStack(it, Math.max(1, Math.min(this.count, it.getDefaultMaxStackSize())));
		}
	}

	/** One ingredient: exactly one of item / tag / part, and a count. */
	public record Input(Identifier item, Identifier tag, Identifier part, int count) {

		public boolean matches(ItemStack stack) {
			if (stack.isEmpty()) {
				return false;
			}
			if (this.part != null) {
				return stack.getItem() instanceof MechPartItem && this.part.equals(MechPartItem.partId(stack));
			}
			if (this.tag != null) {
				return stack.is(TagKey.create(Registries.ITEM, this.tag));
			}
			return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(this.item);
		}

		/** Stacks to show for this ingredient (several for a tag - the screen cycles through them). */
		public List<ItemStack> display() {
			List<ItemStack> out = new ArrayList<>();
			if (this.part != null) {
				MechPart p = PartRegistry.getOrNull(this.part);
				if (p != null) {
					out.add(MechPartItem.stackFor(p));
				}
			} else if (this.tag != null) {
				for (Holder<Item> e : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, this.tag))) {
					out.add(new ItemStack(e.value()));
				}
			} else {
				Item it = BuiltInRegistries.ITEM.getValue(this.item);
				if (it != Items.AIR) {
					out.add(new ItemStack(it));
				}
			}
			return out;
		}

		/** Whether it can ever be satisfied (the item / part exists; tags are trusted). */
		public boolean valid() {
			if (this.part != null) {
				return PartRegistry.getOrNull(this.part) != null;
			}
			return this.tag != null || BuiltInRegistries.ITEM.containsKey(this.item);
		}
	}

	public boolean valid() {
		if (this.ingredients.isEmpty() || this.result.stack().isEmpty()) {
			return false;
		}
		for (Input in : this.ingredients) {
			if (!in.valid()) {
				return false;
			}
		}
		return true;
	}

	/** Parses a recipe object. defaultPart: the part an inline recipe belongs to (its result if none is given). */
	public static MechRecipe parse(Identifier id, JsonObject json, Identifier defaultPart) {
		Output out;
		if (json.has("result")) {
			JsonObject r = json.getAsJsonObject("result");
			int count = r.has("count") ? r.get("count").getAsInt() : 1;
			if (r.has("part")) {
				out = new Output(Identifier.parse(r.get("part").getAsString()), null, count);
			} else {
				out = new Output(null, Identifier.parse(r.get("item").getAsString()), count);
			}
		} else if (defaultPart != null) {
			out = new Output(defaultPart, null, json.has("count") ? json.get("count").getAsInt() : 1);
		} else {
			throw new IllegalArgumentException("recipe without result");
		}
		List<Input> inputs = new ArrayList<>();
		JsonArray arr = json.getAsJsonArray("ingredients");
		for (JsonElement e : arr) {
			JsonObject o = e.getAsJsonObject();
			int count = o.has("count") ? Math.max(1, o.get("count").getAsInt()) : 1;
			if (o.has("part")) {
				inputs.add(new Input(null, null, Identifier.parse(o.get("part").getAsString()), count));
			} else if (o.has("tag")) {
				String tag = o.get("tag").getAsString();
				inputs.add(new Input(null, Identifier.parse(tag.startsWith("#") ? tag.substring(1) : tag), null, count));
			} else {
				inputs.add(new Input(Identifier.parse(o.get("item").getAsString()), null, null, count));
			}
		}
		String category = json.has("category") ? json.get("category").getAsString().toLowerCase(Locale.ROOT) : null;
		int order = json.has("order") ? json.get("order").getAsInt() : 0;
		if (out.part() != null) {
			MechPart p = PartRegistry.getOrNull(out.part());
			if (p != null) {
				if (category == null) {
					category = p.slot().id();
				}
				if (!json.has("order")) {
					order = p.tier();
				}
			}
		}
		if (category == null) {
			category = out.item() != null && BuiltInRegistries.ITEM.getKey(BuiltInRegistries.ITEM.getValue(out.item())).getPath().startsWith("mech_spawner")
					? "frames" : "items";
		}
		return new MechRecipe(id, category, order, out, List.copyOf(inputs));
	}

	public static final List<String> CATEGORY_ORDER = List.of("frames", "head", "core", "arms", "legs", "booster", "weapon", "items");

	/** Category rank for sorting (unknown categories after the built-in ones, alphabetically). */
	public static int categoryRank(String category) {
		int i = CATEGORY_ORDER.indexOf(category);
		return i < 0 ? CATEGORY_ORDER.size() : i;
	}

	/** MechItems is referenced so the part item exists before any recipe is resolved. */
	static void touch() {
		MechItems.PART_ITEMS.size();
	}
}
