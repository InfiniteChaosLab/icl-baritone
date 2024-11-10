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

import net.minecraft.world.inventory.ClickType;

public class Click {
    public final int slotIndex;
    public int clickButton = ClickButton.LEFT_CLICK;
    public ClickType clickType = ClickType.PICKUP;
    public GUIWindow window = GUIWindow.INVENTORY;

    public Click(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    public Click(int slotIndex, GUIWindow window) {
        this.slotIndex = slotIndex;
        this.window = window;
    }

    public Click(int slotIndex, int clickButton) {
        this.slotIndex = slotIndex;
        this.clickButton = clickButton;
    }

    public Click(int slotIndex, int clickButton, GUIWindow guiWindow) {
        this.slotIndex = slotIndex;
        this.clickButton = clickButton;
        this.window = guiWindow;
    }

    public Click(int slotIndex, int clickButton, ClickType clickType) {
        this.slotIndex = slotIndex;
        this.clickButton = clickButton;
        this.clickType = clickType;
    }

    public Click(int slotIndex, ClickType clickType, GUIWindow window) {
        this.slotIndex = slotIndex;
        this.clickType = clickType;
        this.window = window;
    }

    public Click(int slotIndex, int clickButton, ClickType clickType, GUIWindow window) {
        this.slotIndex = slotIndex;
        this.clickButton = clickButton;
        this.clickType = clickType;
        this.window = window;
    }
}
