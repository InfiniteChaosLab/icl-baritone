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

import java.util.*;

public class GOAPPlanner {
    private static Brain brain;

    public GOAPPlanner(Brain brain) {
        GOAPPlanner.brain = brain;
    }

    public List<Action> plan() {
        // Check if we are already in the goal state, and return an empty list if we are
        for (Map.Entry<String, Integer> entry : brain.goalState.individualStates.entrySet()) {
            String key = entry.getKey();
            int value = entry.getValue();
            if (brain.currentState.individualStates.getOrDefault(key, 0) < value) {
                break;
            }
            return Collections.emptyList();
        }

        // Create a priority queue to store the open nodes
        PriorityQueue<Node> openNodes = new PriorityQueue<>(Comparator.comparingInt(n -> n.f));

        // Create a set to store the closed nodes
        Set<State> closedNodes = new HashSet<>();

        // Create the initial node with the goal state
        Node initialNode = new Node(brain.goalState, null, 0, heuristic(brain.goalState), null);

        // Add the initial node to the open list
        openNodes.add(initialNode);

        // TODO: Ensure no infinite loop
        while (!openNodes.isEmpty()) {
            // Get the node with the lowest f value from the open list
            Node currentNode = openNodes.poll();

            // If the current node is the current state, return the path
            boolean goalNotMet = false;
            // TODO: Add a way to detect if there are no actions because we have already met the goal.
            if (currentNode.action != null) {
                for (Map.Entry<String, Integer> entry : currentNode.action.dependencies.individualStates.entrySet()) {
                    String key = entry.getKey();
                    int value = entry.getValue();
                    if (brain.currentState.individualStates.getOrDefault(key, 0) < value) {
                        goalNotMet = true;
                    }
                }
            } else {
                goalNotMet = true;
            }
            if (!goalNotMet) {
                return getPath(currentNode);
            }

            // Add the current node to the closed list
            closedNodes.add(currentNode.state);

            // Generate the predecessor nodes
            for (Action action : brain.availableActions) {
                // Check if this action's results are contained within the current state
                if (actionLeadsToState(currentNode.state, action)) {
                    // Apply the inverse of the action to the current state to get the previous state
                    State prevState = applyInverseAction(currentNode.state, action);
                    // If the previous state is already in the closed list, skip it
                    if (closedNodes.contains(prevState)) {
                        continue;
                    }

                    // TODO: Modify so that the cost is calculated based on the action
                    // Calculate the cost and heuristic for the previous node
                    int g = currentNode.g + 1;
                    int h = heuristic(prevState);
                    int f = g + h;

                    // Create the previous node
                    Node prevNode = new Node(prevState, currentNode, g, f, action);

                    // Add the previous node to the open list
                    openNodes.add(prevNode);
                }
            }
        }

        // If the open list is empty and no path is found, return an empty list
        return Collections.emptyList();
    }

    private boolean actionLeadsToState(State state, Action action) {
        // Check if this action's results are contained within the current state
        // (Simplified: Does this action lead to this state)
        if (action.results.individualStates.entrySet().isEmpty()) {
            return false;
        }
        for (Map.Entry<String, Integer> entry : action.results.individualStates.entrySet()) {
            String key = entry.getKey();
            int resultingValue = entry.getValue();
            if (!state.individualStates.containsKey(key) || state.individualStates.get(key) < resultingValue) {
                return false;
            }
        }
        return true;
    }

    private static State applyInverseAction(State state, Action action) {
        State prevState = new State();
        // TODO: Check thoroughly.
        prevState.individualStates.putAll(state.individualStates);
        prevState.individualStates.putAll(action.dependencies.individualStates);

        // Apply the inverse of the action's effects to the new state
        for (Map.Entry<String, Integer> entry : action.results.individualStates.entrySet()) {
            String key = entry.getKey();
            int value = entry.getValue();
            prevState.individualStates.put(key, Math.max(prevState.individualStates.get(key) - value, 0));
        }

        return prevState;
    }

    private int heuristic(State state) {
        // Calculate the heuristic value based on the distance from the current state
        int distance = 0;
        for (Map.Entry<String, Integer> entry : state.individualStates.entrySet()) {
            String key = entry.getKey();
            int stateValue = entry.getValue();
            int currentValue = brain.currentState.individualStates.getOrDefault(key, 0);
            distance += Math.abs(currentValue - stateValue);
        }
        return distance;
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

    private static class Node {
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
}
