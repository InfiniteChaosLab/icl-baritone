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

import baritone.Baritone;
import baritone.api.event.events.TickEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.*;

// TODO: Implement Roles:
// President
// - Macro decision maker
// - Inhabits Palace
// Vice President
// - Takes over the role of President in the event of their death, until they re-enter owned land.
// - Inhabits bunker
// Governor
// - One per city
// - Role of President, but for that city
// Soldier
// - Kills hostile entities that threaten any role
// Builder
// - Builds structures
// Firefighter
// - Puts out fires
// Farmer
// - Farms crops and animals
// Scout
// - Continually traverses owned land
// - Tries to keep the time since a chunk was last completely observed as low as possible
// Miner
// - Obtains resources & stores them in storage facilities
// Postie
// - Delivers items to other roles
// Journalist
// - Records events

public class Brain {
    private final Baritone baritone;
    private final Minecraft minecraft;
    public BigDecimal $ = BigDecimal.ZERO;
    State currentState;
    public State goalState;
    public List<Action> availableActions;
    private List<Action> plan;
    private final Map<String, State> goalStates;
    public int currentTick = 0;
    private final String ROOT_FOLDER = "../../../src/main/java/brain/";
    private final GOAPPlanner planner;

    public Brain(Baritone baritone, Minecraft minecraft) {
        this.baritone = baritone;
        this.minecraft = minecraft;
        this.currentState = new State("current_state");
        this.goalState = new State("goal_state");
        this.goalStates = new HashMap<>();
        createGoals();
        this.availableActions = new ArrayList<>();
        setGoal();
        baritone.getGameEventHandler().registerEventListener(new AbstractGameEventListener() {
            @Override
            public void onTick(TickEvent event) {
                if (event.getType() == TickEvent.Type.IN) {
                    postTick();
                }
            }
        });
        planner = new GOAPPlanner(this);
    }

    private void setGoal() {
        goalState = goalStates.get("just_get_one_dirt");
//        if (!goalMet(goalStates.get("full_iron"))) {
//            goalState = goalStates.get("full_iron");
//            return;
//        }
//        if (!goalMet(goalStates.get("full_💎"))) {
//            goalState = goalStates.get("full_💎");
//        }
    }

    private void addActions() {
        availableActions.addAll(createPickupItemActions());
        availableActions.addAll(createMineBlockActions());
    }

    private List<Action> createPickupItemActions() {
        List<Action> actions = new ArrayList<>();
        for (Field field : Items.class.getDeclaredFields()) {
            if (field.getType() == Item.class) {
                try {
                    Item i = (Item) field.get(null);
                    Action action = new Action("Pick up " + i);
                    State dependencies = new State();
                    dependencies.individualStates.put(StateTypes.SEE_ITEM + " " + i, 1);
                    action.dependencies = dependencies;
                    State results = new State();
                    results.individualStates.put(StateTypes.IN_INVENTORY_AT_LEAST + " " + i.toString(), 1);
                    action.results = results;
                    actions.add(action);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return actions;
    }

    private List<Action> createMineBlockActions() {
        List<Action> actions = new ArrayList<>();
        Item[] tools = {
                Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE,
                Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE,
                Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.IRON_SHOVEL, Items.GOLDEN_SHOVEL, Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL
        };

        for (Field field : Blocks.class.getDeclaredFields()) {
            if (field.getType() == Block.class) {
                try {
                    Block block = (Block) field.get(null);
                    BlockState blockState = block.defaultBlockState();

                    ServerLevel level = Objects.requireNonNull(minecraft.getSingleplayerServer()).overworld();
                    BlockPos pos = BlockPos.ZERO;

                    for (Item tool : tools) {
                        LootParams.Builder builder = new LootParams.Builder(level);
                        builder.withParameter(LootContextParams.BLOCK_STATE, blockState);
                        builder.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos));
                        builder.withParameter(LootContextParams.TOOL, new ItemStack(tool));
                        builder.withParameter(LootContextParams.EXPLOSION_RADIUS, 0.0f);

                        // Get the block drops
                        List<ItemStack> drops = blockState.getDrops(builder);

                        // Create the Action for mining the block with the specific tool
                        Action mineAction = new Action("Mine " + block + " with " + tool);
                        mineAction.action = "Mine " + block + " with " + tool;

                        // Set the dependencies for mining the block with the specific tool
                        mineAction.dependencies = new State();
                        mineAction.dependencies.individualStates.put(StateTypes.SEE_BLOCK + " " + block, 1);
                        mineAction.dependencies.individualStates.put(StateTypes.IN_INVENTORY_AT_LEAST + " " + tool, 1);

                        // Set the effects for mining the block with the specific tool
                        mineAction.results = new State();
                        for (ItemStack drop : drops) {
                            Item item = drop.getItem();
                            int count = drop.getCount();
                            mineAction.results.individualStates.put(StateTypes.SEE_ITEM + " " + item, count);
                        }

                        // Add the mine action to the list of available actions
                        actions.add(mineAction);
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return actions;
    }

    private boolean goalMet(State goalState) {
        // For each state in the goal, check if we have satisfied it.
        for (Map.Entry<String, Integer> entry : goalState.individualStates.entrySet()) {
            String key = entry.getKey();
            if (currentState.individualStates.get(key) == null) {
                return false;
            } else if (currentState.individualStates.get(key) >= entry.getValue()) {
                return true;
            }
        }
        return false;
    }

    private void postTick() {
        if (currentTick == 0) {
            firstRun();
        }
        updateState();
        if (currentTick % Time.toTicks(1, Time.SECOND) == 0) {
            System.out.println("Current state: ");
            currentState.individualStates.forEach((key, value) -> System.out.println(key + ": " + value));
        }

        currentTick++;
    }

    private void firstRun() {
        minecraft.getToasts().addToast(new HMD(this));
        addActions();
        plan = planner.plan();
        System.out.println("PLAN CREATED!");
    }

    private void updateState() {
        updateInventoryState();
    }

    private void updateInventoryState() {
        Map<String, Integer> itemQuantities = new HashMap<>();
        final double[] totalValue = {0.0};

        // Read the item values from the JSON file
        Map<String, Double> itemValues = readItemValuesFromFile();

        baritone.getPlayerContext().player().getInventory().items.forEach(itemStack -> {
            String itemName = itemStack.getItem().toString();
            int quantity = itemStack.getCount();

            // Add the quantity to the existing quantity for the item type, or initialize it if not present
            itemQuantities.merge(itemName, quantity, Integer::sum);

            // Calculate the value of the item based on its quantity and value from the JSON file
            double itemValue = itemValues.getOrDefault(itemName, 0.0) * quantity;
            totalValue[0] += itemValue;
        });

        // Update the currentState.state with the summed quantities
        currentState.individualStates.putAll(itemQuantities);

        // Set the $ variable to the total value of the inventory
        $ = BigDecimal.valueOf(totalValue[0]);
    }

    private void createGoals() {
        State fullIron = new State("full_iron", "Obtain Full Iron");
        fullIron.individualStates.put(StateTypes.WEARING_AT_LEAST + " " + Items.IRON_HELMET, 1);
        fullIron.individualStates.put(StateTypes.WEARING_AT_LEAST + " " + Items.IRON_CHESTPLATE, 1);
        fullIron.individualStates.put(StateTypes.WEARING_AT_LEAST + " " + Items.IRON_LEGGINGS, 1);
        fullIron.individualStates.put(StateTypes.WEARING_AT_LEAST + " " + Items.IRON_BOOTS, 1);
        fullIron.individualStates.put("in_hotbar_>= " + Items.IRON_SWORD, 1);
        goalStates.put(fullIron.name, fullIron);

        State fullDiamond = new State("full_💎", "Obtain Full Diamond");
        fullDiamond.individualStates.put(StateTypes.WEARING_AT_LEAST + " " + Items.DIAMOND_HELMET, 1);
        fullDiamond.individualStates.put(StateTypes.WEARING_AT_LEAST + " " + Items.DIAMOND_CHESTPLATE, 1);
        fullDiamond.individualStates.put(StateTypes.WEARING_AT_LEAST + " " + Items.DIAMOND_LEGGINGS, 1);
        fullDiamond.individualStates.put(StateTypes.WEARING_AT_LEAST + " " + Items.DIAMOND_BOOTS, 1);
        fullDiamond.individualStates.put("in_hotbar_>= " + Items.DIAMOND_SWORD, 1);
        goalStates.put(fullDiamond.name, fullDiamond);

        State justGetOneDirt = new State("just_get_one_dirt", "Just get one dirt");
        justGetOneDirt.individualStates.put(StateTypes.IN_INVENTORY_AT_LEAST + " " + Items.DIRT, 1);
        goalStates.put(justGetOneDirt.name, justGetOneDirt);
    }

    private Map<String, Double> readItemValuesFromFile() {
        String filePath = ROOT_FOLDER + "item_values.json";
        try (FileReader reader = new FileReader(filePath)) {
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, Double>>() {
            }.getType();
            return gson.fromJson(reader, type);
        } catch (IOException e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }
}
