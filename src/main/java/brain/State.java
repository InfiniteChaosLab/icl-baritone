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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class State {
    public String name;
    public String description = "";
    public Map<String, Integer> individualStates;

    public State() {
        this.individualStates = new HashMap<>();
    }

    public State(String name) {
        this.name = name;
        this.individualStates = new HashMap<>();
    }

    public State(State other) {
        this.name = other.name;
        this.description = other.description;
        this.individualStates = new HashMap<>(other.individualStates);
    }

    public State(String name, String description) {
        this.name = name;
        this.description = description;
        this.individualStates = new HashMap<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        State state = (State) o;
        return Objects.equals(name, state.name) &&
                Objects.equals(description, state.description) &&
                individualStates.equals(state.individualStates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, individualStates);
    }

    @Override
    public String toString() {
        return description;
    }
}