/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package brain;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.Block;

public class Action {
    public Action(ActionType actionType, String actionString) {
        this.actionType = actionType;
        this.actionString = actionString;
    }

    public Action(ActionType actionType, String actionString, String description) {
        this.actionType = actionType;
        this.actionString = actionString;
        this.description = description;
    }

    public Action(ActionType actionType, String actionString, String description, Block block) {
        this.actionType = actionType;
        this.actionString = actionString;
        this.description = description;
        this.block = block;
    }

    public Action(ActionType actionType, String actionString, String description, Block block, Item tool) {
        this.actionType = actionType;
        this.actionString = actionString;
        this.description = description;
        this.block = block;
        this.tool = tool;
    }

    public Action(ActionType actionType, String actionString, String description, Item item) {
        this.actionType = actionType;
        this.actionString = actionString;
        this.description = description;
        this.item = item;
    }

    public Action(ActionType actionType, String actionString, Block block) {
        this.actionType = actionType;
        this.actionString = actionString;
        this.block = block;
    }

    public Action(ActionType actionType, String actionString, Item item) {
        this.actionType = actionType;
        this.actionString = actionString;
        this.item = item;
    }

    public Action(Action other) {
        this.dependencies = other.dependencies != null ? new State(other.dependencies) : null;
        this.results = other.results != null ? new State(other.results) : null;
        this.actionString = other.actionString;
        this.description = other.description;
        this.block = other.block;
        this.item = other.item;
        this.tool = other.tool;
        this.actionType = other.actionType;
        // If weird issue happens with copying actions & changing recipes, may be because we need to make a new recipe based on the old.
        this.recipe = other.recipe;
        this.requiresCraftingTable = other.requiresCraftingTable;
    }

    public State dependencies;
    public State results;
    public String actionString;
    public String description;
    public Block block;
    public Item item;
    public Item tool;
    public ActionType actionType;
    public CraftingRecipe recipe;
    public boolean requiresCraftingTable = false;
}
