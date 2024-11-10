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
import baritone.api.BaritoneAPI;
import baritone.api.event.events.TickEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.CalculationContext;
import baritone.process.MineProcess;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

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

// Desired Inventory Arrangement:
// [Hel]
// [Che]                    [Cra][Cra]     [Res]
// [Leg]                    [Cra][Cra]
// [Boo]               [Tot]
// [ ? ][ ? ][ ? ][ ? ][ ? ][ ? ][ ? ][ ? ][ ? ]
// [ ? ][ ? ][ ? ][ ? ][ ? ][ ? ][ ? ][ ? ][ ? ]
// [ ? ][ ? ][ ? ][ ? ][ ? ][ ? ][ ? ][ ? ][---]
// [Swo][Pic][Sho][Axe][Fis][Hoe][Foo][Blo][Use]

// Inventory Indices:
// [ 5 ] Helmet              Crafting      Result
// [ 6 ] Chestplate         [ 1 ][ 2 ]     [ 0 ]
// [ 7 ] Leggings    Shield [ 3 ][ 4 ]
// [ 8 ] Boots         [45 ]
// [ 9 ][10 ][11 ][12 ][13 ][14 ][15 ][16 ][17 ] - Row 1
// [18 ][19 ][20 ][21 ][22 ][23 ][24 ][25 ][26 ] - Row 2
// [27 ][28 ][29 ][30 ][31 ][32 ][33 ][34 ][35 ] - Row 3
// [36 ][37 ][38 ][39 ][40 ][41 ][42 ][43 ][44 ] - Hotbar

// https://wiki.vg/Inventory (also includes other inventories eg. Furnace)

// TODO: Implement things it can see (line of sight). This exists in Baritone. See legitMine.

// TODO: Update Baritone to account for Cave Air and Void Air if it does not already.

public class Brain {
    private final Baritone baritone;
    private final Minecraft minecraft;
    public BigDecimal $ = BigDecimal.ZERO;
    public volatile State currentState;
    public volatile State previousState;
    Vec3 previousLocation;
    public volatile State inventoryState;
    public volatile State goalState;
    public List<Action> availableActions;
    public volatile List<Action> plan;
    private Action actionInExecution;
    private final Map<String, State> goalStates;
    public int currentTick = 0;
    private final String ROOT_FOLDER = "../../../src/main/java/brain/";
    private final Planner planner;
    private final Map<BlockPos, List<ItemEntity>> droppedItems = new HashMap<>();
    private final ConcurrentHashMap<Vec3, Block> knownBlocks = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Queue<Click> queuedClicks = new LinkedList<>();
    private boolean inventoryIsOpen = false;
    public volatile boolean isPlanning = false;
    public volatile boolean isUpdatingStateAndGoal = false;
    public List<ChunkPos> claimedChunks = new ArrayList<>();
    private boolean neverPlacedBefore = true;
    private BlockPos nearestCraftingTable = null;
    private BlockPos nearestFurnace = null;
    private final int CLOSE_BY_DISTANCE = 128;
    private final int SEE_BLOCK_RANGE = 128;
    public volatile Planner.Node currentlyConsideredNode;


    public Brain(Baritone baritone, Minecraft minecraft) {
        this.baritone = baritone;
        this.minecraft = minecraft;
        this.currentState = new State("current_state");
        this.inventoryState = new State("inventory_state");
        this.goalState = new State("goal_state");
        this.goalStates = new HashMap<>();
        createGoals();
        this.availableActions = new ArrayList<>();
        updateGoal();
        baritone.getGameEventHandler().registerEventListener(new AbstractGameEventListener() {
            @Override
            public void onTick(TickEvent event) {
                if (event.getType() == TickEvent.Type.IN) {
                    postTick();
                }
            }
        });
        planner = new Planner(this);
        Runtime.getRuntime().addShutdownHook(new Thread(executorService::shutdown));
    }

    private void postTick() {
        if (currentTick == 0) {
            firstRun();
        }
        if (currentTick % (Time.toTicks(1, Time.SECOND) / 2) == 0) {
            executeClicks();
        }
        if (currentTick % Time.toTicks(1, Time.SECOND) == 0) {
            if (!isUpdatingStateAndGoal) {
                isUpdatingStateAndGoal = true;
                executorService.execute(() -> {
                    updateState();
                    updateGoal();
                    isUpdatingStateAndGoal = false;
                });
            }
            if (queuedClicks.isEmpty()) {
                executePlan();
            }
        }

        if (currentTick % Time.toTicks(3, Time.SECOND) == 0) {

            if (!isPlanning && !currentState.individualStates.isEmpty() && !goalState.individualStates.isEmpty() && !knownBlocks.isEmpty()) {
                isPlanning = true;
                executorService.execute(() -> {
                    plan = planner.plan();
                    isPlanning = false;
                });
            }
        }
        currentTick++;
    }

    private void firstRun() {
        minecraft.getToasts().addToast(new HMD(this));
        addActions();
        updateState();
    }

    private void updateGoal() {
        if (goalNotMet(goalStates.get("just_get_one_dirt")))
            goalState = goalStates.get("just_get_one_dirt");
        else if (goalNotMet(goalStates.get("obtain_stick")))
            goalState = goalStates.get("obtain_stick");
        else if (goalNotMet(goalStates.get("full_iron"))) {
            goalState = goalStates.get("full_iron");
        } else if (goalNotMet(goalStates.get("full_💎"))) {
            goalState = goalStates.get("full_💎");
        }
    }

    private void addActions() {
        availableActions.addAll(createPickupItemActions());
        availableActions.addAll(createMineBlockActions());
        availableActions.addAll(createCraftingActions());
        availableActions.addAll(createPlaceBlockActions());
        availableActions.addAll(createWearActions());
        availableActions.addAll(createSmeltActions());
    }

    private List<Action> createPickupItemActions() {
        List<Action> actions = new ArrayList<>();
        for (Field field : Items.class.getDeclaredFields()) {
            if (field.getType() == Item.class) {
                try {
                    Item item = (Item) field.get(null);
                    if (item == Items.AIR) {
                        continue;
                    }
                    String itemName = item.toString();
                    Action action = new Action(ActionType.PICKUP, ActionType.PICKUP + " " + itemName, "🫳 " + itemName, item);
                    State dependencies = new State();
                    dependencies.individualStates.put(StateTypes.SEE_ITEM + " " + itemName, 1);
                    action.dependencies = dependencies;
                    State results = new State();
                    results.individualStates.put(StateTypes.IN_INVENTORY_AT_LEAST + " " + itemName, 1);
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
                    if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) {
                        continue;
                    }
                    String blockName = getBlockName(block);
                    BlockState blockState = block.defaultBlockState();
                    ServerLevel level = Objects.requireNonNull(minecraft.getSingleplayerServer()).overworld();
                    BlockPos pos = BlockPos.ZERO;

                    // Create the Action for mining the block with your hand
                    Action mineActionWithHand = new Action(ActionType.MINE, ActionType.MINE + " " + blockName + " with " + ItemStack.EMPTY.getItem(), "✊ " + blockName, block);

                    // Set the dependencies for mining the block with your hand
                    mineActionWithHand.dependencies = new State();
                    mineActionWithHand.dependencies.individualStates.put(StateTypes.SEE_BLOCK + " " + blockName, 1);

                    // Set the effects for mining the block with your hand
                    ItemStack emptyHand = ItemStack.EMPTY;
                    List<ItemStack> handDrops = new ArrayList<>();

                    // Check if block is plausible to break and if we can get drops with hand
                    CalculationContext context = new CalculationContext(baritone);
                    if (MineProcess.plausibleToBreak(context, pos)) {
                        if (!blockState.requiresCorrectToolForDrops() || emptyHand.isCorrectToolForDrops(blockState)) {
                            handDrops = blockState.getDrops(new LootParams.Builder(level)
                                    .withParameter(LootContextParams.BLOCK_STATE, blockState)
                                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                                    .withParameter(LootContextParams.TOOL, emptyHand));
                        }
                    }
                    mineActionWithHand.results = new State();
                    updateResultsWithDrops(mineActionWithHand.results, handDrops);

                    // Add the mine action with hand to the list of available actions
                    actions.add(mineActionWithHand);

                    for (Item tool : tools) {
                        ItemStack toolStack = new ItemStack(tool);

                        if (!blockState.requiresCorrectToolForDrops() || toolStack.isCorrectToolForDrops(blockState)) {
                            LootParams.Builder builder = new LootParams.Builder(level);
                            builder.withParameter(LootContextParams.BLOCK_STATE, blockState);
                            builder.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos));
                            builder.withParameter(LootContextParams.TOOL, toolStack);
                            builder.withParameter(LootContextParams.EXPLOSION_RADIUS, 0.0f);

                            // Get the block drops
                            List<ItemStack> drops = blockState.getDrops(builder);

                            // Create the Action for mining the block with the specific tool
                            Action mineAction = new Action(ActionType.MINE, ActionType.MINE + " " + blockName + " with " + tool, "⛏ " + blockName + " with " + tool, block, tool);

                            // Set the dependencies for mining the block with the specific tool
                            mineAction.dependencies = new State();
                            mineAction.dependencies.individualStates.put(StateTypes.SEE_BLOCK + " " + blockName, 1);
                            mineAction.dependencies.individualStates.put(StateTypes.IN_INVENTORY_AT_LEAST + " " + tool, 1);

                            // Set the effects for mining the block with the specific tool
                            mineAction.results = new State();
                            updateResultsWithDrops(mineAction.results, drops);

                            // Add the mine action to the list of available actions
                            actions.add(mineAction);
                        }
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return actions;
    }

    private void updateResultsWithDrops(State results, List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            Item item = drop.getItem();
            String itemName = item.toString();
            int count = drop.getCount();
            results.individualStates.put(StateTypes.SEE_ITEM + " " + itemName, count);
        }
    }

    private List<Action> createCraftingActions() {
        List<Action> actions = new ArrayList<>();
        RecipeManager recipeManager = Objects.requireNonNull(minecraft.level).getRecipeManager();
        Collection<RecipeHolder<?>> recipeHolders = recipeManager.getRecipes();

        for (RecipeHolder<?> recipeHolder : recipeHolders) {
            Recipe<?> recipe = recipeHolder.value();
            if (recipe instanceof CraftingRecipe) {
                RegistryAccess registryAccess = Objects.requireNonNull(minecraft.getConnection()).registryAccess();

                ItemStack result = recipe.getResultItem(registryAccess);
                Item resultItem = result.getItem();
                if (resultItem == Items.AIR) {
                    continue;
                }
                String itemName = resultItem.toString();
                Action craftAction = new Action(ActionType.CRAFT, ActionType.CRAFT + " " + itemName, "🛠 " + itemName, resultItem);

                craftAction.dependencies = new State();

                // Check if recipe requires a crafting table
                if (recipe instanceof ShapedRecipe shapedRecipe) {
                    if (shapedRecipe.isSpecial()) {
                        craftAction.dependencies.individualStates.put(StateTypes.SEE_BLOCK + " " + getBlockName(Blocks.CRAFTING_TABLE), 1);
                        craftAction.dependencies.individualStates.put(StateTypes.CLOSE_BY + " " + getBlockName(Blocks.CRAFTING_TABLE), 1);
                        craftAction.requiresCraftingTable = true;
                    }
                }
                for (Ingredient ingredient : recipe.getIngredients()) {
                    ItemStack[] itemVariants = ingredient.getItems();
                    if (itemVariants.length == 0) {
                        continue;
                    }

                    Item item = itemVariants[0].getItem();
                    String dependencyItemName = item.toString();
                    int count = itemVariants[0].getCount();
                    craftAction.dependencies.individualStates.merge(StateTypes.IN_INVENTORY_AT_LEAST + " " + dependencyItemName, count, Integer::sum);
                }
                craftAction.results = new State();
                craftAction.results.individualStates.put(StateTypes.IN_INVENTORY_AT_LEAST + " " + itemName, result.getCount());
                craftAction.recipe = (CraftingRecipe) recipe;

                actions.add(craftAction);
            }
        }

        return actions;
    }

    private List<Action> createPlaceBlockActions() {
        List<Action> actions = new ArrayList<>();
        for (Field field : Blocks.class.getDeclaredFields()) {
            if (field.getType() == Block.class) {
                try {
                    Block block = (Block) field.get(null);
                    if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) {
                        continue;
                    }
                    String blockName = getBlockName(block);
                    Action placeAction = new Action(ActionType.PLACE, ActionType.PLACE + " " + blockName, "🧱 " + blockName, block);
                    State dependencies = new State();
                    dependencies.individualStates.put(StateTypes.IN_INVENTORY_AT_LEAST + " " + blockName, 1);
                    placeAction.dependencies = dependencies;
                    State results = new State();
                    results.individualStates.put(StateTypes.SEE_BLOCK + " " + blockName, 1);
                    results.individualStates.put(StateTypes.CLOSE_BY + " " + blockName, 1);
                    placeAction.results = results;
                    actions.add(placeAction);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return actions;
    }

    private List<Action> createWearActions() {
        Item[] wearableItems = {
                Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS,
                Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS,
                Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
                Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS,
                Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
                Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
                Items.TURTLE_HELMET, Items.ELYTRA
        };
        List<Action> actions = new ArrayList<>();
        for (Item wearableItem : wearableItems) {
            Action wearAction = new Action(ActionType.WEAR, ActionType.WEAR + " " + wearableItem.toString(), "👕 " + wearableItem, wearableItem);
            State dependencies = new State();
            dependencies.individualStates.put(StateTypes.IN_INVENTORY_AT_LEAST + " " + wearableItem, 1);
            wearAction.dependencies = dependencies;
            State results = new State();
            results.individualStates.put(StateTypes.WEARING_AT_LEAST + " " + wearableItem, 1);
            wearAction.results = results;
            actions.add(wearAction);
        }
        return actions;
    }

    private List<Action> createSmeltActions() {
        List<Action> actions = new ArrayList<>();
        RecipeManager recipeManager = Objects.requireNonNull(minecraft.level).getRecipeManager();
        Collection<RecipeHolder<?>> recipeHolders = recipeManager.getRecipes();

        for (RecipeHolder<?> recipeHolder : recipeHolders) {
            Recipe<?> recipe = recipeHolder.value();
            if (recipe instanceof SmeltingRecipe smeltingRecipe) {
                RegistryAccess registryAccess = Objects.requireNonNull(minecraft.getConnection()).registryAccess();

                ItemStack result = recipe.getResultItem(registryAccess);
                Item resultItem = result.getItem();
                if (resultItem == Items.AIR) {
                    continue;
                }

                String itemName = resultItem.toString();
                Action smeltAction = new Action(ActionType.SMELT, ActionType.SMELT + " " + itemName, "🔥 " + itemName, resultItem);

                // Set dependencies
                smeltAction.dependencies = new State();

                // Need a furnace nearby
                smeltAction.dependencies.individualStates.put(StateTypes.SEE_BLOCK + " " + getBlockName(Blocks.FURNACE), 1);
                smeltAction.dependencies.individualStates.put(StateTypes.CLOSE_BY + " " + getBlockName(Blocks.FURNACE), 1);

                // Need the ingredient
                Ingredient ingredient = smeltingRecipe.getIngredients().get(0); // Smelting recipes only have one ingredient
                ItemStack[] itemVariants = ingredient.getItems();
                if (itemVariants.length > 0) {
                    Item ingredientItem = itemVariants[0].getItem();
                    String dependencyItemName = ingredientItem.toString();
                    smeltAction.dependencies.individualStates.put(StateTypes.IN_INVENTORY_AT_LEAST + " " + dependencyItemName, 1);
                }

                // Need fuel (coal, etc.)
                smeltAction.dependencies.individualStates.put(StateTypes.IN_INVENTORY_AT_LEAST + " " + Items.COAL, 1);

                // Set results
                smeltAction.results = new State();
                smeltAction.results.individualStates.put(StateTypes.IN_INVENTORY_AT_LEAST + " " + itemName, result.getCount());

                actions.add(smeltAction);
            }
        }

        return actions;
    }

    private String getBlockName(Block block) {
        return block.getDescriptionId().replace("block.minecraft.", "");
    }

    private boolean weDontHaveWhatWeNeed(RecipeBookMenu<CraftingContainer> menu, Action action, Map<Ingredient, Slot> slotsToPickUpItemsFrom) {
        NonNullList<Ingredient> ingredients = action.recipe.getIngredients();

        // Check if we have enough of each ingredient in our inventory
        for (Ingredient ingredient : ingredients) {
            ItemStack[] itemStacksThatWouldMatchIngredient = ingredient.getItems();

            if (itemStacksThatWouldMatchIngredient.length > 0) {
                // Check if we indeed have a matching stack in our inventory, and store its slot.
                boolean weHaveOne = false;
                for (ItemStack itemStack : itemStacksThatWouldMatchIngredient) {
                    if (menu.getItems().stream().anyMatch(ourItemStack -> ourItemStack.getItem() == itemStack.getItem())) {
                        weHaveOne = true;
                        // Iterate over the slots in the menu
                        for (int j = 0; j < menu.slots.size(); j++) {
                            if (menu.getSlot(j).getItem().getItem() == itemStack.getItem()) {
                                slotsToPickUpItemsFrom.put(ingredient, menu.getSlot(j));
                                // We got the slot for that ingredient, stop looking for it
                                break;
                            }
                        }
                        // We finished looking for the item.
                        break;
                    }
                }
                if (!weHaveOne) {
                    // We are missing an item! We should never have got here since we shouldn't even consider crafting the item if we don't have or expect to have the ingredients.
                    return true;
                }
            } else {
                return true;
            }
        }
        return false;
    }

    // TODO: Check if this ever needs to account for variants. Eg. some different type of wood or something. If Gurt is to use wood,
    // then it will need to check for variants.
    private int getInventorySlotForItem(RecipeBookMenu<CraftingContainer> menu, Item item) {
        for (int i = 0; i < menu.slots.size(); i++) {
            if (menu.getSlot(i).getItem().getItem() == item) {
                return menu.getSlot(i).index;
            }
        }
        return -1;
    }

    // TODO: Check if this works
    private int getFurnaceSlotForItem(FurnaceMenu menu, Item item) {
        for (int i = 0; i < menu.slots.size(); i++) {
            if (menu.getSlot(i).getItem().getItem() == item) {
                return menu.getSlot(i).index;
            }
        }
        return -1;
    }

    private boolean populateShapedRecipePatternData(ShapedRecipe shapedRecipe, List<String> patternRows, Map<Character, Ingredient> patternKey) {
        try {
            // Get the ShapedRecipePattern field from the ShapedRecipe class
            Field patternField = ShapedRecipe.class.getDeclaredField("pattern");
            patternField.setAccessible(true);

            // Get the ShapedRecipePattern object from the shapedRecipe instance
            ShapedRecipePattern pattern = (ShapedRecipePattern) patternField.get(shapedRecipe);

            // Get the pattern data from the ShapedRecipePattern
            Optional<ShapedRecipePattern.Data> optionalPatternData = pattern.data();
            if (optionalPatternData.isPresent()) {
                ShapedRecipePattern.Data patternData = optionalPatternData.get();
                patternRows.clear();
                patternRows.addAll(patternData.pattern());
                patternKey.putAll(patternData.key());
                return true;
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void executePlan() {
        if (plan == null || plan.isEmpty()) {
            stopAllProcesses();
            actionInExecution = null;
            return;
        }
        if (actionInExecution != null
                && actionInExecution.equals(plan.get(0))
                && (!currentState.equals(previousState) || !baritone.getPlayerContext().player().position().equals(previousLocation))) {
            // Keep doing it
            previousState = new State(currentState);
            previousLocation = baritone.getPlayerContext().player().position();
            return;
        }
        // Stop all existing processes
        stopAllProcesses();

        Action action = plan.get(0);
        if (action == null) {
            return;
        }
        System.out.println("Executing action: " + action.actionString);
        if (action.actionType == ActionType.MINE) {
            Block block = action.block;
            if (block == null) {
                return;
            }
            BaritoneAPI.getProvider().getWorldScanner().repack(baritone.getPlayerContext());
            baritone.getMineProcess().mine(block);
            actionInExecution = action;
        } else if (action.actionType == ActionType.PICKUP) {
            Item item = action.item;
            if (item == null) {
                return;
            }
            // Get the nearest position of the dropped item we want from droppedItems
            BlockPos nearestPos = null;
            double nearestDistance = Double.MAX_VALUE;
            for (Map.Entry<BlockPos, List<ItemEntity>> entry : droppedItems.entrySet()) {
                BlockPos pos = entry.getKey();
                double distance = baritone.getPlayerContext().player().position().distanceTo(Vec3.atCenterOf(pos));
                if (distance < nearestDistance) {
                    nearestPos = pos;
                    nearestDistance = distance;
                }
            }
            if (nearestPos == null) {
                return;
            }
            // Set the goal to the nearest one's position
            Goal goal = new GoalBlock(nearestPos);
            baritone.getCustomGoalProcess().setGoal(goal);
            // Request pathing to the goal
            baritone.getCustomGoalProcess().path();

            actionInExecution = action;
        } else if (action.actionType == ActionType.CRAFT) {
            Item item = action.item;
            if (item == null) {
                return;
            }

            if (action.requiresCraftingTable) {
                // Always path to on top of the crafting table. This ensures there is never anything in the way that prevents Gurt from interacting with it.
                // ... Except for vines, ladders, webs, and other 'transparent?' blocks. Todo.
                // TODO: Make this specifically so we always end up on top. >1 might be too much. Maybe != 0? or > 0.01?
                if (baritone.getPlayerContext().player().position().distanceTo(Vec3.atCenterOf(nearestCraftingTable)) > 1) {
                    // We're already pathing to it, return; and keep going.
                    if (baritone.getCustomGoalProcess().getGoal().isInGoal(nearestCraftingTable.above())) {
                        return;
                    }
                    BlockPos blockAboveCraftingTable = nearestCraftingTable.above();
                    Goal goal = new GoalBlock(blockAboveCraftingTable);
                    baritone.getCustomGoalProcess().setGoal(goal);
                    baritone.getCustomGoalProcess().path();
                    return;
                } else {
                    // Look at the crafting table (straight down)
                    baritone.getLookBehavior().updateTarget(new Rotation(0, 0), true);
                    baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                    // We should now be in the crafting table GUI.
                    if (!(baritone.getPlayerContext().player().containerMenu instanceof CraftingMenu craftingMenu)) {
                        // We are not in the crafting table GUI, return; and try again.
                        return;
                    }
                    // We are in the crafting table GUI.
                    // Arrange the items in the crafting container according to the recipe pattern

                    Map<Ingredient, Slot> slotsToPickUpItemsFrom = new HashMap<>();
                    if (weDontHaveWhatWeNeed(craftingMenu, action, slotsToPickUpItemsFrom)) {
                        System.out.println("Something is wrong! We don't have everything we need to craft this item!");
                        return;
                    }
                    // Get the slots into which we will put the items.
                    // Get the pattern of the recipe
                    // Force the pattern variable to be public, and pull the pattern from it.
                    // I had to do this as there doesn't appear to be any access mechanism for the pattern aside from re-reading it from data/minecraft/recipes/<recipe>.json
                    // Maybe we should do that though. Probably should. Todo.
                    if (action.recipe instanceof ShapedRecipe shapedRecipe) {
                        List<String> patternRows = new ArrayList<>();
                        Map<Character, Ingredient> patternKey = new HashMap<>();
                        if (!populateShapedRecipePatternData(shapedRecipe, patternRows, patternKey)) {
                            System.out.println("Something is wrong! No pattern/key data for the item!");
                            return;
                        }
                        /*
                         We have all the items we need and the slots they are contained within, and we have the pattern into which they need to be arranged.
                         TODO: This might operate too fast, eg. waiting for the resulting item to appear after arranging our items. May need to build a system to do clicks sequentially after delays
                         Arrange the items in the crafting container according to the recipe pattern
                         For every row in the recipe
                         If we already have clicks queued, don't add them again.
                        */

                        for (int i = 0; i < patternRows.size(); i++) {
                            String rowText = patternRows.get(i);
                            // For every character in the row
                            for (int j = 0; j < rowText.length(); j++) {
                                char c = rowText.charAt(j);
                                // TODO: What about ' 's? What happens?
                                Ingredient ingredient = patternKey.get(c);
                                int inventorySlot = slotsToPickUpItemsFrom.get(ingredient).index;
                                // Pick up the whole item stack from the inventory slot
                                queuedClicks.add(new Click(inventorySlot, GUIWindow.CRAFTING_TABLE));
                                int craftingSlotNumber = (i * 3) + j + 1;
                                // Place one of it into the correct crafting slot
                                queuedClicks.add(new Click(craftingSlotNumber, ClickButton.RIGHT_CLICK, GUIWindow.CRAFTING_TABLE));
                                // Place the rest back where we got it from
                                queuedClicks.add(new Click(inventorySlot, GUIWindow.CRAFTING_TABLE));
                            }
                        }

                    } else if (action.recipe instanceof ShapelessRecipe) {
                        craftShapelessRecipe(slotsToPickUpItemsFrom);
                    }

                    queuedClicks.add(new Click(0, ClickButton.LEFT_CLICK, ClickType.PICKUP, GUIWindow.CRAFTING_TABLE));

                    // Find the first available inventory slot
                    int firstAvailableSlot = -1;
                    for (int i = 9; i < 45; i++) {
                        if (baritone.getPlayerContext().player().getInventory().getItem(i).isEmpty()) {
                            // + 1 because the crafting table's inventory slots are +1 from the inventory's.
                            // See https://wiki.vg/Inventory
                            firstAvailableSlot = i + 1;
                            break;
                        }
                    }

                    if (firstAvailableSlot != -1) {
                        queuedClicks.add(new Click(firstAvailableSlot, ClickButton.LEFT_CLICK, ClickType.PICKUP, GUIWindow.CRAFTING_TABLE));
                    } else {
                        // Handle the case when there are no available inventory slots
                        System.out.println("No available inventory slots!");
                    }
//                    queuedClicks.add(new Click(-999, ClickButton.LEFT_CLICK, ClickType.THROW));
                }
            } else {
                // The recipe does not require a crafting table.
                // Get the player's inventory menu
                InventoryMenu inventoryMenu = baritone.getPlayerContext().player().inventoryMenu;
                Map<Ingredient, Slot> slotsToPickUpItemsFrom = new HashMap<>();
                // NOTE: This actually populates the slotsToPickUpItemsFrom map.
                if (weDontHaveWhatWeNeed(inventoryMenu, action, slotsToPickUpItemsFrom)) {
                    System.out.println("Something is wrong! We don't have everything we need to craft this item!");
                    return;
                }
                // Get the slots into which we will put the items from our inventory slots.
                // Get the pattern of the recipe
                // Force the pattern variable to be public, and pull the pattern from it.
                // I had to do this as there doesn't appear to be any access mechanism for the pattern aside from re-reading it from data/minecraft/recipes/<recipe>.json
                // Maybe we should do that though. Probably should. Todo.
                if (action.recipe instanceof ShapedRecipe shapedRecipe) {
                    List<String> patternRows = new ArrayList<>();
                    Map<Character, Ingredient> patternKey = new HashMap<>();
                    if (!populateShapedRecipePatternData(shapedRecipe, patternRows, patternKey)) {
                        System.out.println("Something is wrong! No pattern/key data for the item!");
                        return;
                    }
                    // We have all the items we need and the slots they are contained within, and we have the pattern into which they need to be arranged.
                    for (int i = 0; i < patternRows.size(); i++) {
                        String rowText = patternRows.get(i);
                        // For every character in the row
                        for (int j = 0; j < rowText.length(); j++) {
                            char c = rowText.charAt(j);
                            // TODO: What about ' 's? What happens?
                            Ingredient ingredient = patternKey.get(c);
                            int inventorySlot = slotsToPickUpItemsFrom.get(ingredient).index;
                            // Pick up the whole item stack from the inventory slot
                            queuedClicks.add(new Click(inventorySlot));
                            // Crafting slots start at 1, not 0, so we +1 to the result. 0 is the result slot.
                            int craftingSlotNumber = i * 2 + j + 1;
                            // Place one of it into the correct crafting slot
                            queuedClicks.add(new Click(craftingSlotNumber, ClickButton.RIGHT_CLICK));
                            // Place the rest back where we got it from
                            queuedClicks.add(new Click(inventorySlot));
                        }
                    }
                } else if (action.recipe instanceof ShapelessRecipe) {
                    craftShapelessRecipe(slotsToPickUpItemsFrom);
                }
                queuedClicks.add(new Click(0, ClickButton.LEFT_CLICK, ClickType.PICKUP));
                // Find the first available inventory slot
                int firstAvailableSlot = -1;
                for (int i = 9; i < 45; i++) {
                    if (baritone.getPlayerContext().player().getInventory().getItem(i).isEmpty()) {
                        firstAvailableSlot = i;
                        break;
                    }
                }

                if (firstAvailableSlot != -1) {
                    queuedClicks.add(new Click(firstAvailableSlot, ClickButton.LEFT_CLICK, ClickType.PICKUP));
                } else {
                    System.out.println("No available inventory slots!");
                    // TODO: Throw the least valuable item that is not involved in any future action onto the floor, or store it in a chest.
                }
            }
            actionInExecution = action;
        } else if (action.actionType == ActionType.PLACE) {
            if (neverPlacedBefore) {
                neverPlacedBefore = false;
            }
            Block block = action.block;
            if (block == null) {
                return;
            }
            String blockName = getBlockName(block);
            baritone.getBuilderProcess().build(blockName, new File(ROOT_FOLDER + "schematics/" + blockName + ".schem"), baritone.getPlayerContext().player().blockPosition());
            actionInExecution = action;
        } else if (action.actionType == ActionType.WEAR) {
            Item item = action.item;
            if (item == null) {
                return;
            }
            // Get the player's inventory menu
            InventoryMenu inventoryMenu = baritone.getPlayerContext().player().inventoryMenu;

            // Get the inventory slot that contains the item we need to wear
            int inventorySlot = getInventorySlotForItem(inventoryMenu, item);
            if (inventorySlot == -1) {
                System.out.println("Something is wrong! We don't have the item we need to wear in our inventory!");
                return;
            }
            // Equip the item
            queuedClicks.add(new Click(inventorySlot, ClickButton.LEFT_CLICK, ClickType.QUICK_MOVE));
            actionInExecution = action;
        } else if (action.actionType == ActionType.SMELT) {
            if (nearestFurnace == null) {
                System.out.println("Something is wrong! We don't know where the nearest furnace is!");
                return;
            }
            if (baritone.getPlayerContext().player().position().distanceTo(Vec3.atCenterOf(nearestFurnace)) > 1) {
                // We are not close enough to the furnace, path to it.

                // If we're already pathing to it, return; and keep going.
                if (baritone.getCustomGoalProcess().getGoal().isInGoal(nearestFurnace.above())) {
                    return;
                }

                // Go to the block above the furnace.
                Goal goal = new GoalBlock(nearestFurnace.above());
                baritone.getCustomGoalProcess().setGoal(goal);
                baritone.getCustomGoalProcess().path();
            } else {
                // We are close enough to the furnace, interact with it.
                // Look at the furnace (straight down)
                baritone.getLookBehavior().updateTarget(new Rotation(0, 0), true);
                // Open the furnace GUI
                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                // We should now be in the furnace GUI.
                if (!(baritone.getPlayerContext().player().containerMenu instanceof FurnaceMenu furnaceMenu)) {
                    // We are not in the furnace GUI, return; and try again.
                    return;
                }
                // We are in the furnace GUI.

                // Check if there's anything in the result slot (slot 2)
                ItemStack resultStack = furnaceMenu.getSlot(2).getItem();
                if (!resultStack.isEmpty()) {
                    // Find first empty inventory slot
                    int firstEmptySlot = -1;
                    for (int i = 3; i < furnaceMenu.slots.size(); i++) {
                        if (furnaceMenu.getSlot(i).getItem().isEmpty()) {
                            firstEmptySlot = i;
                            break;
                        }
                    }

                    if (firstEmptySlot != -1) {
                        // Click result slot to pick up item
                        queuedClicks.add(new Click(2, ClickType.QUICK_MOVE, GUIWindow.FURNACE));
                    } else {
                        System.out.println("No empty inventory slots to collect smelted item!");
                    }
                    return;
                }

                // Check if the furnace is already smelting what we want
                ItemStack inputStack = furnaceMenu.getSlot(0).getItem();
                if (!inputStack.isEmpty() && inputStack.getItem() == action.item) {
                    // Already smelting the right item, just wait
                    return;
                }

                // Place the item we want to smelt into the top slot
                Item item = action.item;
                if (item == null) {
                    System.out.println("Something is wrong! The action doesn't have the item we need to smelt!");
                    return;
                }
                int itemSlot = getFurnaceSlotForItem(furnaceMenu, item);
                if (itemSlot == -1) {
                    System.out.println("Something is wrong! We don't have the item we need to smelt in our inventory!");
                    return;
                }
                queuedClicks.add(new Click(itemSlot, ClickType.QUICK_MOVE, GUIWindow.FURNACE));

                // Handle fuel management
                ItemStack fuelStack = furnaceMenu.getSlot(1).getItem();
                if (!fuelStack.isEmpty()) {
                    if (fuelStack.getItem() == Items.COAL) {
                        // If there's less than 8 coal, and we have more, add more
                        if (fuelStack.getCount() < 8) {
                            int coalSlot = getFurnaceSlotForItem(furnaceMenu, Items.COAL);
                            if (coalSlot != -1) {
                                queuedClicks.add(new Click(coalSlot, ClickType.QUICK_MOVE, GUIWindow.FURNACE));
                            }
                        }
                    } else {
                        // Wrong fuel type, remove it
                        queuedClicks.add(new Click(1, ClickType.QUICK_MOVE, GUIWindow.FURNACE));
                        if (!furnaceMenu.getSlot(1).getItem().isEmpty()) {
                            // If quick move didn't work (inventory full), throw it out
                            queuedClicks.add(new Click(1, GUIWindow.FURNACE));
                            queuedClicks.add(new Click(-999, ClickButton.LEFT_CLICK, ClickType.THROW, GUIWindow.FURNACE));
                        }
                    }
                } else {
                    // No fuel, add coal if we have it
                    int coalSlot = getFurnaceSlotForItem(furnaceMenu, Items.COAL);
                    if (coalSlot != -1) {
                        queuedClicks.add(new Click(coalSlot, ClickType.QUICK_MOVE, GUIWindow.FURNACE));
                    }
                }

                actionInExecution = action;
            }
        }
    }

    private void craftShapelessRecipe(Map<Ingredient, Slot> slotsToPickUpItemsFrom) {
        int craftingSlotIndex = 1;
        for (Slot slot : slotsToPickUpItemsFrom.values()) {
            int inventorySlot = slot.index;
            // Pick up the whole item stack from the inventory slot
            queuedClicks.add(new Click(inventorySlot));
            // Place one of it into the correct crafting slot
            queuedClicks.add(new Click(craftingSlotIndex++, ClickButton.RIGHT_CLICK));
            // Place the rest back where we got it from
            queuedClicks.add(new Click(inventorySlot));
        }
    }

    private void executeClicks() {
        if (queuedClicks.isEmpty()) {
            return;
        }
        Click click = queuedClicks.poll();
        if (click.window == GUIWindow.INVENTORY) {
            if (!inventoryIsOpen) {
                // TODO: Fix inventory not visually opening
                baritone.getPlayerContext().player().sendOpenInventory();
                inventoryIsOpen = true;
            }
            clickInventorySlot(click.slotIndex, click.clickButton, click.clickType);
            // If there are no clicks left to do, or if the next click doesn't happen in the inventory, close the inventory.
            if (queuedClicks.isEmpty() || queuedClicks.peek().window != GUIWindow.INVENTORY) {
                baritone.getPlayerContext().player().closeContainer();
                inventoryIsOpen = false;
            }
        } else if (click.window == GUIWindow.FURNACE) {
            AbstractContainerMenu container = baritone.getPlayerContext().player().containerMenu;
            if (container instanceof FurnaceMenu) {
//                if (click.slotIndex == -999) {
//                    // Throw item
//                    baritone.getPlayerContext().playerController().drop(baritone.getPlayerContext().player(), click.clickType);
//                } else {
                baritone.getPlayerContext().playerController().windowClick(
                        container.containerId,
                        click.slotIndex,
                        click.clickButton,
                        click.clickType,
                        baritone.getPlayerContext().player()
                );
            }
        }
    }

    private void clickInventorySlot(int slotIndex, int clickButton, ClickType clickType) {
        baritone.getPlayerContext().playerController().windowClick(baritone.getPlayerContext().player().inventoryMenu.containerId, slotIndex, clickButton, clickType, baritone.getPlayerContext().player());
    }

    private BlockPos findClosestBlock(Block block) {
        List<Map.Entry<Vec3, Block>> filteredEntries = knownBlocks.entrySet().stream()
                .filter(e -> e.getValue().equals(block))
                .toList();

        BlockPos closestPos = null;
        double minDistance = Double.MAX_VALUE;
        for (Map.Entry<Vec3, Block> entry : filteredEntries) {
            BlockPos pos = new BlockPos((int) entry.getKey().x, (int) entry.getKey().y, (int) entry.getKey().z);
            double distance = baritone.getPlayerContext().player().position().distanceTo(entry.getKey());
            if (distance < minDistance) {
                closestPos = pos;
                minDistance = distance;
            }
        }
        return closestPos;
    }

    private void stopAllProcesses() {
        baritone.getGetToBlockProcess().onLostControl();
        baritone.getMineProcess().onLostControl();
        baritone.getCustomGoalProcess().onLostControl();
    }

    private boolean goalNotMet(State goalState) {
        // For each state in the goal, check if we have satisfied it.
        for (Map.Entry<String, Integer> entry : goalState.individualStates.entrySet()) {
            String key = entry.getKey();
            if (currentState.individualStates.get(key) == null) {
                return true;
            } else if (currentState.individualStates.get(key) >= entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private void updateState() {
        updateInventoryState();
        updateDroppedItemsState();
        updateKnownBlocksState();
        updateProximityStates();
    }

    private void updateInventoryState() {
        Map<String, Integer> itemQuantities = new HashMap<>();
        final double[] totalValue = {0.0};

        // Read the item values from the JSON file
        Map<String, Double> itemValues = readItemValuesFromFile();

        baritone.getPlayerContext().player().getInventory().items.forEach(itemStack -> {
            if (itemStack.getItem() != Items.AIR) {
                String itemName = itemStack.getItem().toString();
                int quantity = itemStack.getCount();

                // Add the quantity to the existing quantity for the item type, or initialize it if not present
                itemQuantities.merge(StateTypes.IN_INVENTORY_AT_LEAST + " " + itemName, quantity, Integer::sum);

                // Calculate the value of the item based on its quantity and value from the JSON file
                double itemValue = itemValues.getOrDefault(itemName, 0.0) * quantity;
                totalValue[0] += itemValue;
            }
        });

        // Remove all current knowledge of inventory
        currentState.individualStates.keySet().removeIf(key -> key.startsWith(String.valueOf(StateTypes.IN_INVENTORY_AT_LEAST)));
        inventoryState.individualStates.clear();

        // Update the inventory knowledge with the summed quantities
        currentState.individualStates.putAll(itemQuantities);
        inventoryState.individualStates.putAll(itemQuantities);

        // Set the $ variable to the total value of the inventory
        $ = BigDecimal.valueOf(totalValue[0]);
    }

    private void updateDroppedItemsState() {
        // Scan for dropped items
        droppedItems.clear();
        for (Entity entity : ((ClientLevel) baritone.getPlayerContext().world()).entitiesForRendering()) {
            if (entity instanceof ItemEntity itemEntity) {
                BlockPos blockPos = itemEntity.blockPosition();
                droppedItems.computeIfAbsent(blockPos, k -> new ArrayList<>()).add(itemEntity);
            }
        }

        // Clear the currentState.individualStates entries starting with StateTypes.SEE_ITEM
        currentState.individualStates.keySet().removeIf(key -> key.startsWith(String.valueOf(StateTypes.SEE_ITEM)));

        // Add the visible items and their quantities to currentState.individualStates
        Map<Item, Integer> visibleItems = new HashMap<>();
        for (List<ItemEntity> itemEntities : droppedItems.values()) {
            for (ItemEntity itemEntity : itemEntities) {
                Item item = itemEntity.getItem().getItem();
                int quantity = itemEntity.getItem().getCount();
                visibleItems.merge(item, quantity, Integer::sum);
            }
        }
        for (Map.Entry<Item, Integer> entry : visibleItems.entrySet()) {
            String itemName = entry.getKey().toString();
            int quantity = entry.getValue();
            currentState.individualStates.put(StateTypes.SEE_ITEM + " " + itemName, quantity);
        }
    }

    private void updateKnownBlocksState() {
        // TODO: Update this so we keep track of all blocks we know of in the world, and set them to 0 if we notice in their previous location there now is nothing.
        knownBlocks.clear();

        // Iterate over the blocks in the world and add them to the knownBlocks map
        ClientLevel world = (ClientLevel) baritone.getPlayerContext().world();
        for (BlockPos pos : BlockPos.betweenClosed(
                new BlockPos((int) (baritone.getPlayerContext().player().getX() - SEE_BLOCK_RANGE), 0, (int) (baritone.getPlayerContext().player().getZ() - SEE_BLOCK_RANGE)),
                new BlockPos((int) (baritone.getPlayerContext().player().getX() + SEE_BLOCK_RANGE), 255, (int) (baritone.getPlayerContext().player().getZ() + SEE_BLOCK_RANGE)))) {
            Block block = world.getBlockState(pos).getBlock();
            if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) {
                continue;
            }
            knownBlocks.put(pos.getCenter(), block);
        }

        // Clear the existing StateTypes.SEE_BLOCK entries in currentState.individualStates
        currentState.individualStates.keySet().removeIf(key -> key.startsWith(String.valueOf(StateTypes.SEE_BLOCK)));

        // Count the occurrences of each block type and update currentState.individualStates
        Map<Block, Integer> blockCounts = new HashMap<>();
        for (Block block : knownBlocks.values()) {
            // TODO: Verify this is what we want. Long term intention is to store blocks even far away from player.
            blockCounts.merge(block, 1, Integer::sum);
        }
        for (Map.Entry<Block, Integer> entry : blockCounts.entrySet()) {
            Block block = entry.getKey();
            int count = entry.getValue();
            String blockName = getBlockName(block);
            currentState.individualStates.put(StateTypes.SEE_BLOCK + " " + blockName, count);
        }
    }

    private void updateProximityStates() {
        // TODO: Update to do this for a list of blocks we track our proximity to, not just crafting table.
        currentState.individualStates.keySet().removeIf(key -> key.startsWith(String.valueOf(StateTypes.CLOSE_BY)));
        nearestCraftingTable = findClosestBlock(Blocks.CRAFTING_TABLE);
        if (nearestCraftingTable == null) {
            return;
        }
        // If the nearest crafting table is too far away, delete the CLOSE_BY state for crafting_table in our currentState.
        if (baritone.getPlayerContext().player().position().distanceTo(Vec3.atCenterOf(nearestCraftingTable)) > CLOSE_BY_DISTANCE) {
            currentState.individualStates.remove(StateTypes.CLOSE_BY + " " + getBlockName(Blocks.CRAFTING_TABLE));
            // Otherwise add that state.
        } else {
            currentState.individualStates.put(StateTypes.CLOSE_BY + " " + getBlockName(Blocks.CRAFTING_TABLE), 1);
        }

        nearestFurnace = findClosestBlock(Blocks.FURNACE);
        if (nearestFurnace == null) {
            return;
        }
        // If the nearest furnace is too far away, delete the CLOSE_BY state for furnace in our currentState.
        if (baritone.getPlayerContext().player().position().distanceTo(Vec3.atCenterOf(nearestFurnace)) > CLOSE_BY_DISTANCE) {
            currentState.individualStates.remove(StateTypes.CLOSE_BY + " " + getBlockName(Blocks.FURNACE));
            // Otherwise add that state.
        } else {
            currentState.individualStates.put(StateTypes.CLOSE_BY + " " + getBlockName(Blocks.FURNACE), 1);
        }
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

        State obtainStick = new State("obtain_stick", "Obtain Stick");
        obtainStick.individualStates.put(StateTypes.IN_INVENTORY_AT_LEAST + " " + Items.STICK, 1);
        goalStates.put(obtainStick.name, obtainStick);
    }

    private void claimChunk() {
        // Get the player's position
        BlockPos playerPos = baritone.getPlayerContext().playerFeet();
        ChunkPos playerChunk = new ChunkPos(playerPos);
        claimedChunks.add(playerChunk);

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
