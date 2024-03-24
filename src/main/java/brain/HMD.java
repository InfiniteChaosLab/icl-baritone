package brain;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.NotNull;

// Head-Mounted Display
public class HMD implements Toast {
    private final Brain brain;
    private int w = 0;
    private int h = 0;
    private final int LINE_HEIGHT = 12;
    private final int MARGIN = 10;
    private final int textColor = rgb(122, 255, 122);

    public HMD(Brain brain) {
        this.brain = brain;
    }

    @Override
    public @NotNull Visibility render(@NotNull GuiGraphics guiGraphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        Minecraft minecraft = toastComponent.getMinecraft();
        Window window = minecraft.getWindow();
        w = window.getGuiScaledWidth();
        h = window.getGuiScaledHeight();
        drawName(minecraft, guiGraphics);
        drawGoal(minecraft, guiGraphics);
        assert minecraft.player != null;
        drawString(minecraft, guiGraphics, String.valueOf(Math.round(minecraft.player.getYRot() % 360)), x(0), y(LINE_HEIGHT * 2));
        drawHeading(minecraft, guiGraphics, 360 - minecraft.player.getYRot());
        return Visibility.SHOW;
    }

    private void drawString(Minecraft minecraft, GuiGraphics guiGraphics, String text, int x, int y) {
        guiGraphics.drawString(minecraft.font, text, x, y, textColor);
    }

    private void drawName(Minecraft minecraft, GuiGraphics guiGraphics) {
        Style style = Style.EMPTY.withColor(textColor).withBold(true);
        Style shadow = Style.EMPTY.withColor(rgb(0, 0, 0)).withBold(true);
        FormattedText text = FormattedText.of("Gurt v0.1", style);
        FormattedText shadowText = FormattedText.of("Gurt v0.1", shadow);
        guiGraphics.drawWordWrap(minecraft.font, shadowText, x(1), y(1), w - (MARGIN * 2), rgb(128, 128, 128));
        guiGraphics.drawWordWrap(minecraft.font, text, x(0), y(0), w - (MARGIN * 2), textColor);
    }

    private void drawGoal(Minecraft minecraft, GuiGraphics guiGraphics) {
        guiGraphics.drawString(minecraft.font, "▶ " + brain.goalState.description, x(0), y(LINE_HEIGHT), textColor);
    }

    private void drawHeading(Minecraft minecraft, GuiGraphics guiGraphics, float yaw) {
        int northPos = Math.round(((yaw + 180) % 360 + 360) % 360);
        int eastPos = Math.round(((yaw + 180 + 90) % 360 + 360) % 360);
        int southPos = Math.round(((yaw + 180 + 180) % 360 + 360) % 360);
        int westPos = Math.round(((yaw + 180 + 270) % 360 + 360) % 360);
        int sideMargin = 100;
        if ((w / 2) - 180 + northPos >= sideMargin && (w / 2) - 180 + northPos <= w - sideMargin) {
            guiGraphics.drawString(minecraft.font, "N", x((w / 2) - 180 + northPos - MARGIN), y(0), textColor);
            guiGraphics.drawString(minecraft.font, "|", x((w / 2) - 180 + northPos + 2 - MARGIN), y(10), textColor);
        }
        if ((w / 2) - 180 + eastPos >= sideMargin && (w / 2) - 180 + eastPos <= w - sideMargin) {
            guiGraphics.drawString(minecraft.font, "E", x((w / 2) - 180 + eastPos - MARGIN), y(0), textColor);
            guiGraphics.drawString(minecraft.font, "|", x((w / 2) - 180 + eastPos + 2 - MARGIN), y(10), textColor);
        }
        if ((w / 2) - 180 + southPos >= sideMargin && (w / 2) - 180 + southPos <= w - sideMargin) {
            guiGraphics.drawString(minecraft.font, "S", x((w / 2) - 180 + southPos - MARGIN), y(0), textColor);
            guiGraphics.drawString(minecraft.font, "|", x((w / 2) - 180 + southPos + 2 - MARGIN), y(10), textColor);
        }
        if ((w / 2) - 180 + westPos >= sideMargin && (w / 2) - 180 + westPos <= w - sideMargin) {
            guiGraphics.drawString(minecraft.font, "W", x((w / 2) - 180 + westPos - MARGIN), y(0), textColor);
            guiGraphics.drawString(minecraft.font, "|", x((w / 2) - 180 + westPos + 2 - MARGIN), y(10), textColor);
        }
    }

    // Converts RGB to a hex color
    private int rgb(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }

    private int rgba(int r, int g, int b, int a) {
        return (r << 24) | (g << 16) | (b << 8) | a;
    }

    private int x(int x) {
        return x + MARGIN - w + 160;
    }

    private int y(int y) {
        return MARGIN + y;
    }
}