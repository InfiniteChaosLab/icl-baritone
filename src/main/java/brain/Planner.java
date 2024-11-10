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
import net.minecraft.world.item.Items;

import java.util.*;

public class Planner {
    private static Brain brain;

    // Map of item to actions that produce it
    private Map<String, List<Action>> effectToActions;

    // Add this constant at the class level
    private static final int MAX_OPEN_SET_SIZE = 1000000;
    private static final int PRUNED_SET_SIZE = 500000;

    public Planner(Brain brain) {
        Planner.brain = brain;
    }

    private void buildEffectToActionsMap() {
        effectToActions = new HashMap<>();
        for (Action action : brain.availableActions) {
            for (String resultKey : action.results.individualStates.keySet()) {
                effectToActions.computeIfAbsent(resultKey, k -> new ArrayList<>()).add(action);
            }
        }
    }

    public List<Action> plan() {
        State goalState = new State();

        if (effectToActions == null || effectToActions.isEmpty()) {
            buildEffectToActionsMap();
        }

        // Check if we are already in the goal state, and return an empty list if we are
        for (Map.Entry<String, Integer> entry : brain.goalState.individualStates.entrySet()) {
            String key = entry.getKey();
            int value = entry.getValue();
            if (brain.currentState.individualStates.getOrDefault(key, 0) < value) {
                break;
            }
            return Collections.emptyList();
        }

        if (brain.goalState.individualStates.size() > 1) {
            // Check each individual state to see if it is met. On finding the first unmet state, attempt to achieve it.
            for (Map.Entry<String, Integer> entry : brain.goalState.individualStates.entrySet()) {
                String key = entry.getKey();
                int value = entry.getValue();
                if (brain.currentState.individualStates.getOrDefault(key, 0) < value) {
                    goalState.individualStates.put(key, value - brain.currentState.individualStates.getOrDefault(key, 0));
                    break;
                }
            }
        } else {
            goalState.individualStates.putAll(brain.goalState.individualStates);
        }

        // Create a priority queue to store the open nodes
        PriorityQueue<Node> openNodes = new PriorityQueue<>(Comparator.comparingInt(n -> n.f));

        // Create a set to store the closed nodes
//        Set<State> closedNodes = new HashSet<>();

        // Create the initial node with the goal state
//        Node initialNode = new Node(goalState, null, 0, heuristic(goalState), null);
        Node initialNode = new Node(goalState, null, 0, basicHeuristic(goalState), null);

        // Add the initial node to the open list
        openNodes.add(initialNode);

        // TODO: Ensure no infinite loop
        while (!openNodes.isEmpty() && openNodes.size() <= MAX_OPEN_SET_SIZE) {
            // Get the node with the lowest f value from the open list
            Node currentNode = openNodes.poll();
            brain.currentlyConsideredNode = currentNode;

            // If the current node is the current state, return the path
            boolean goalNotMet = false;
            // TODO: Add a way to detect if there are no actions because we have already met the goal.
            if (currentNode.action != null) {
                for (Map.Entry<String, Integer> entry : currentNode.action.dependencies.individualStates.entrySet()) {
                    String key = entry.getKey();
                    int value = entry.getValue();
                    if (brain.currentState.individualStates.getOrDefault(key, 0) < value) {
                        goalNotMet = true;
                        break;
                    }
                }
            } else {
                goalNotMet = true;
            }
            if (!goalNotMet) {
                return getPath(currentNode);
            }

            // Add the current node to the closed list
//            closedNodes.add(currentNode.state);

            // Get relevant actions
            Set<Action> relevantActions = new HashSet<>();
            for (Map.Entry<String, Integer> entry : currentNode.state.individualStates.entrySet()) {
                String key = entry.getKey();
                int value = entry.getValue();
                if (value > 0) {
                    relevantActions.addAll(effectToActions.getOrDefault(key, Collections.emptyList()));
                }
            }

            // TODO: Somewhere in here we have to account for when a state requires 2 or more predecessor individual states.
            //  We need to split those up and try to achieve each of those.
            //  The way we're dealing with it for now is just achieving the first one, and relying on the planner to
            //  automatically recalculate periodically to then get the others.

            // Generate the predecessor nodes (♟️: for all possible squares on the board)
            for (Action action : relevantActions) {
                // Check if this action's results are contained within the current state (♟️: if the square is next to ours)
//                if (stateAchievedByAction(currentNode.state, action)) {
                if (anyPartOfStateAchievedByAction(currentNode.state, action)) {
                    // Apply the inverse of the action to the current state to get the previous state
                    State prevState = applyInverseAction(currentNode.state, action);
                    // If the previous state is already in the closed list, skip it
//                    if (closedNodes.contains(prevState)) {
//                        continue;
//                    }

                    // TODO: Modify so that the cost is calculated based on the action
                    // Calculate the cost and heuristic for the previous node (the state prior to this one in time)

//                    if (action.item == Items.BLOCK)

                    // g is the cost of this step
                    int g = currentNode.g + 1;
                    if (mineralBlocks.contains(action.item)) {
                        g += 9;
                    }

                    // h is the manhattan distance between this state and our current state in state space
//                    int h = heuristic(prevState);
                    int h = basicHeuristic(prevState);

                    // f is the priority
                    int f = g + h;

                    // Create the previous node
                    Node prevNode = new Node(prevState, currentNode, g, f, action);

                    // Add the previous node to the open list
                    openNodes.add(prevNode);
                }
            }

            // Add pruning check after adding new nodes
            if (openNodes.size() > MAX_OPEN_SET_SIZE) {
                PriorityQueue<Node> prunedQueue = new PriorityQueue<>(
                        Comparator.comparingInt(n -> n.f)
                );
                for (int i = 0; i < PRUNED_SET_SIZE; i++) {
                    if (!openNodes.isEmpty()) {
                        prunedQueue.add(openNodes.poll());
                    }
                }
                openNodes = prunedQueue;
            }
        }

        if (openNodes.size() > 1000000) {
            System.out.println("PLANNER: Open nodes exceeded 1000000!");
        }

        // If the open list is empty and no path is found, return an empty list
        return Collections.emptyList();
    }

    // Does this action lead to this state
    private boolean stateAchievedByAction(State state, Action action) {
        for (Map.Entry<String, Integer> entry : state.individualStates.entrySet()) {
            if (entry.getValue() == 0) {
                continue;
            }
            String key = entry.getKey();
            int value = entry.getValue();
            if (action.results.individualStates.getOrDefault(key, 0) < value) {
                return false;
            }
        }
        return true;
    }

    private boolean anyPartOfStateAchievedByAction(State state, Action action) {
        for (Map.Entry<String, Integer> actionIndividualStateResult : action.results.individualStates.entrySet()) {
            String key = actionIndividualStateResult.getKey();
            // If the action gives us at least one of the things we need
            if (actionIndividualStateResult.getValue() > 0 && state.individualStates.getOrDefault(key, 0) > 0) {
                return true;
            }
        }
        return false;
    }

    private static State applyInverseAction(State state, Action action) {
        State prevState = new State();
        // TODO: Check thoroughly.
        prevState.individualStates.putAll(state.individualStates);

        // Apply the inverse of the action's effects to the new state
        for (Map.Entry<String, Integer> entry : action.results.individualStates.entrySet()) {
            String key = entry.getKey();
            int value = entry.getValue();
            value = Math.max(prevState.individualStates.getOrDefault(key, 0) - value, 0);
            if (value > 0) {
                prevState.individualStates.put(key, value);
            } else {
                prevState.individualStates.remove(key);
            }
        }
        prevState.individualStates.putAll(action.dependencies.individualStates);
        return prevState;
    }

//    private int heuristic(State state) {
//        // Calculate the heuristic value based on the distance from the current state
//        int distance = 0;
//        for (Map.Entry<String, Integer> entry : state.individualStates.entrySet()) {
//            String key = entry.getKey();
//            int stateValue = entry.getValue();
//            int currentValue = brain.currentState.individualStates.getOrDefault(key, 0);
//            distance += Math.abs(currentValue - stateValue);
//        }
//        return distance;
//    }

    private int heuristic(State state) {
        return state.individualStates.entrySet().stream()
                .mapToInt(entry -> Math.abs(brain.currentState.individualStates.getOrDefault(entry.getKey(), 0) - entry.getValue()))
                .sum();
    }

    private int basicHeuristic(State state) {
        return state.individualStates.entrySet().stream()
                .mapToInt(entry -> (Math.abs(brain.currentState.individualStates.getOrDefault(entry.getKey(), 0) - entry.getValue())) > 0 ? 1 : 0)
                .sum();
    }

    private List<Action> getPath(Node node) {
        // Backtrack from the current node to the goal node to get the path
        List<Action> path = new ArrayList<>();
        while (node.parent != null) {
            path.add(node.action);
            node = node.parent;
        }
        return path;
    }

    public static class Node {
        State state;
        Node parent;
        int g;
        int f;
        Action action;

        Node(State state, Node parent, int g, int f, Action action) {
            this.state = state;
            this.parent = parent;
            this.g = g;
            this.f = f;
            this.action = action;
        }
    }

    private final List<Item> mineralBlocks = Arrays.asList(Items.IRON_BLOCK, Items.GOLD_BLOCK, Items.DIAMOND_BLOCK, Items.EMERALD_BLOCK, Items.LAPIS_BLOCK, Items.REDSTONE_BLOCK, Items.COAL_BLOCK, Items.DIAMOND_BLOCK, Items.AMETHYST_BLOCK, Items.COPPER_BLOCK, Items.NETHERITE_BLOCK, Items.QUARTZ_BLOCK);
}
