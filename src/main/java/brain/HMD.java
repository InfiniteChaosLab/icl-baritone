package brain;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.jetbrains.annotations.NotNull;

// Head-Mounted Display
public class HMD implements Toast {
    private int w = 0;
    private int h = 0;
    @Override
    public @NotNull Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        Minecraft minecraft = toastComponent.getMinecraft();
        Window window = minecraft.getWindow();
        w = window.getGuiScaledWidth();
        h = window.getGuiScaledHeight();
        int color = rgb(122, 255, 122);
        guiGraphics.drawString(minecraft.font, "Gurt v0.1", x(10), 10, color);
        return Visibility.SHOW;
    }

    // Converts RGB to a hex color
    private int rgb(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }

    private int x(int x) {
        return  x - w + 160;
    }
}