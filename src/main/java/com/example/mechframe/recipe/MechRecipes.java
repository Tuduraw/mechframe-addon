package com.example.mechframe.recipe;

import com.google.gson.JsonParser;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every Mech Fabricator recipe. Raw JSON text per recipe id, filled by MechDataLoader on the server
 * (data packs, mod jars, tudursvehiclemod-addons/) and by the data sync on clients, like PartRegistry.
 * Parsed AFTER the parts (an inline recipe's category and order come from its part).
 */
public final class MechRecipes {

	private static final Logger LOGGER = LoggerFactory.getLogger("MechFrame/Recipes");

	/** Recipe id -> {part id the recipe was declared in (inline) or "" , JSON text}. */
	private static Map<Identifier, String[]> raw = Map.of();
	private static Map<Identifier, MechRecipe> recipes = Map.of();
	private static List<MechRecipe> sorted = List.of();

	private MechRecipes() {
	}

	public static Map<Identifier, String[]> raw() {
		return raw;
	}

	public static MechRecipe get(Identifier id) {
		return recipes.get(id);
	}

	/** Valid recipes in display order: category, then order (tier), then result name. */
	public static List<MechRecipe> sorted() {
		return sorted;
	}

	public static synchronized void load(Map<Identifier, String[]> json) {
		MechRecipe.touch();
		Map<Identifier, MechRecipe> parsed = new LinkedHashMap<>();
		for (Map.Entry<Identifier, String[]> e : json.entrySet()) {
			try {
				String owner = e.getValue()[0];
				MechRecipe r = MechRecipe.parse(e.getKey(), JsonParser.parseString(e.getValue()[1]).getAsJsonObject(),
						owner.isEmpty() ? null : Identifier.of(owner));
				if (r.valid()) {
					parsed.put(e.getKey(), r);
				} else {
					LOGGER.warn("Mech recipe {} refers to a missing item or part - skipped", e.getKey());
				}
			} catch (Exception ex) {
				LOGGER.error("Failed to parse mech recipe {}: {}", e.getKey(), ex.getMessage());
			}
		}
		List<MechRecipe> list = new ArrayList<>(parsed.values());
		list.sort(Comparator.comparingInt((MechRecipe r) -> MechRecipe.categoryRank(r.category()))
				.thenComparing(MechRecipe::category)
				.thenComparingInt(MechRecipe::order)
				.thenComparing(r -> r.result().stack().getName().getString()));
		raw = Map.copyOf(json);
		recipes = Map.copyOf(parsed);
		sorted = List.copyOf(list);
		LOGGER.info("Loaded {} mech fabricator recipe(s)", recipes.size());
	}

	// ---------------------------------------------------------------------------------- inventory

	/** How many items in the player's inventory match the ingredient. */
	public static int count(PlayerInventory inv, MechRecipe.Input in) {
		int n = 0;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack s = inv.getStack(i);
			if (in.matches(s)) {
				n += s.getCount();
			}
		}
		return n;
	}

	public static boolean canCraft(PlayerEntity player, MechRecipe recipe) {
		if (player.isCreative()) {
			return true;
		}
		// Two ingredients could match the same stack (a tag and an item): count each against what
		// is left after the earlier ones.
		Map<Integer, Integer> used = new LinkedHashMap<>();
		PlayerInventory inv = player.getInventory();
		for (MechRecipe.Input in : recipe.ingredients()) {
			int need = in.count();
			for (int i = 0; i < inv.size() && need > 0; i++) {
				ItemStack s = inv.getStack(i);
				if (in.matches(s)) {
					int free = s.getCount() - used.getOrDefault(i, 0);
					int take = Math.min(free, need);
					if (take > 0) {
						used.merge(i, take, Integer::sum);
						need -= take;
					}
				}
			}
			if (need > 0) {
				return false;
			}
		}
		return true;
	}

	/** Server: takes the ingredients and hands over the result. False if the player lacks something. */
	public static boolean craft(PlayerEntity player, MechRecipe recipe) {
		if (!canCraft(player, recipe)) {
			return false;
		}
		ItemStack result = recipe.result().stack();
		if (result.isEmpty()) {
			return false;
		}
		if (!player.isCreative()) {
			PlayerInventory inv = player.getInventory();
			for (MechRecipe.Input in : recipe.ingredients()) {
				int need = in.count();
				for (int i = 0; i < inv.size() && need > 0; i++) {
					ItemStack s = inv.getStack(i);
					if (in.matches(s)) {
						int take = Math.min(s.getCount(), need);
						s.decrement(take);
						need -= take;
					}
				}
			}
			inv.markDirty();
		}
		player.getInventory().offerOrDrop(result);
		return true;
	}
}
