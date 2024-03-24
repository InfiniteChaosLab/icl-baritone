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
import net.minecraft.world.item.Items;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Brain {
    private final Baritone baritone;
    private BigDecimal portfolioValue = BigDecimal.ZERO;
    private State currentState;
    private State goalState;
    private List<Action> actions;
    private Map<String, State> goalStates;
    private int currentTick = 0;

    public Brain(Baritone baritone) {
        this.baritone = baritone;
        this.currentState = new State("current_state");
        this.goalState = new State("goal_state");
        this.goalStates = new HashMap<>();
        createGoals();
        this.actions = new ArrayList<>();
        setGoal();
        baritone.getGameEventHandler().registerEventListener(new AbstractGameEventListener() {
            @Override
            public void onTick(TickEvent event) {
                if (event.getType() == TickEvent.Type.IN) {
                    postTick();
                }
            }
        });
    }

    private void setGoal() {
        if (!goalMet(goalStates.get("full_iron"))) {
            goalState = goalStates.get("full_iron");
            return;
        }
        if (!goalMet(goalStates.get("full_💎"))) {
            goalState = goalStates.get("full_💎");
            return;
        }
    }

    private boolean goalMet(State goalState) {
        // For each state in the goal, check if we have satisfied it.
        for (Map.Entry<String, Integer> entry : goalState.state.entrySet()) {
            String key = entry.getKey();
            if (currentState.state.get(key) == null) {
                return false;
            } else if (currentState.state.get(key) >= entry.getValue()) {
                return true;
            }
        }
        return false;
    }

    private void postTick() {
        updateState();
        if (currentTick % Time.toTicks(1, Time.SECOND) == 0) {
            System.out.println("Current state: ");
            currentState.state.forEach((key, value) -> {
                System.out.println(key + ": " + value);
            });
        }

        currentTick++;
    }

    private void updateState() {
        updateInventoryState();
    }

    private void updateInventoryState() {
        Map<String, Integer> itemQuantities = new HashMap<>();

        baritone.getPlayerContext().player().getInventory().items.forEach(itemStack -> {
            String itemName = itemStack.getItem().toString();
            int quantity = itemStack.getCount();

            // Add the quantity to the existing quantity for the item type, or initialize it if not present
            itemQuantities.merge(itemName, quantity, Integer::sum);
        });

        // Update the currentState.state with the summed quantities
        itemQuantities.forEach((itemName, quantity) -> {
            currentState.state.put(itemName, quantity);
        });
    }

    private void createGoals() {
        // Obtain full iron & have 20 🍗 units available.
        State fullIron = new State("full_iron");
        fullIron.state.put("👕>= " + Items.IRON_HELMET, 1);
        fullIron.state.put("👕>= " + Items.IRON_CHESTPLATE, 1);
        fullIron.state.put("👕>= " + Items.IRON_LEGGINGS, 1);
        fullIron.state.put("👕>= " + Items.IRON_BOOTS, 1);
        fullIron.state.put("in_hotbar_>= " + Items.IRON_SWORD, 1);
        goalStates.put(fullIron.name, fullIron);

        // Obtain full 💎 & have 20 🍗 units available.
        State fullDiamond = new State("full_💎");
        fullDiamond.state.put("👕>= " + Items.DIAMOND_HELMET, 1);
        fullDiamond.state.put("👕>= " + Items.DIAMOND_CHESTPLATE, 1);
        fullDiamond.state.put("👕>= " + Items.DIAMOND_LEGGINGS, 1);
        fullDiamond.state.put("👕>= " + Items.DIAMOND_BOOTS, 1);
        fullDiamond.state.put("in_hotbar_>= " + Items.DIAMOND_SWORD, 1);
        goalStates.put(fullDiamond.name, fullDiamond);
    }
}
