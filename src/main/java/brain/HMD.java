package brain;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

// Head-Mounted Display
public class HMD implements Toast {
    private final Brain brain;
    private int w = 0;
    private int h = 0;
    private final int LINE_HEIGHT = 12;
    private final int MARGIN = 10;
    private final int BOTTOM_TEXT_OFFSET = 6;
    private final int textColor = rgb(122, 255, 122);
    private final String[] loadingIndicator = {
            "⠋",
            "⠙",
            "⠹",
            "⠸",
            "⠼",
            "⠴",
            "⠦",
            "⠧",
            "⠇",
            "⠏"
    };
    private int loadingIndicatorIndex = 0;
    private int frameCounter = 0;

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
        drawGoalAndPlan(minecraft, guiGraphics);
        assert minecraft.player != null;
        // Check if neither the inventory screen nor the escape screen is open
        if (!(minecraft.screen instanceof InventoryScreen || minecraft.screen instanceof PauseScreen)) {
            guiGraphics.drawString(minecraft.font, "|", x(w / 2 - MARGIN - 1), y(20), textColor);
            drawString(minecraft, guiGraphics, String.format("%03d°", Math.round(((minecraft.player.getYRot()) % 360 + 360) % 360)), x((w / 2) - 19), y(6 + LINE_HEIGHT * 2));
        }
        if (!(minecraft.screen instanceof InventoryScreen || minecraft.screen instanceof PauseScreen || minecraft.screen instanceof ChatScreen)) {
            drawHealthInformation(minecraft, guiGraphics);
            drawFoodInformation(minecraft, guiGraphics);
        }
        if (!(minecraft.screen instanceof ChatScreen)) {
            drawTime(minecraft, guiGraphics);
            drawCoordinates(minecraft, guiGraphics);
        }
        drawHeading(minecraft, guiGraphics, 360 - minecraft.player.getYRot());
        draw$(minecraft, guiGraphics);
        if (!brain.claimedChunks.isEmpty()) {
            drawClaimedChunksBorder(minecraft, guiGraphics);
        }
        frameCounter++;
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

    private void drawGoalAndPlan(Minecraft minecraft, GuiGraphics guiGraphics) {
        int lineCount = 1;
        guiGraphics.drawString(minecraft.font, "🎯 " + brain.goalState.description, x(0), y(LINE_HEIGHT * lineCount), textColor);
        lineCount++;

        if (brain.plan == null || brain.plan.isEmpty()) {
            guiGraphics.drawString(minecraft.font, "📜 No plan", x(8), y(LINE_HEIGHT * lineCount), textColor);
        } else {
            for (Action action : brain.plan) {
                guiGraphics.drawString(minecraft.font, "▶ " + action.description, x(8), y(LINE_HEIGHT * lineCount), textColor);
                lineCount++;
            }
        }
        if (brain.isPlanning) {
            guiGraphics.drawString(minecraft.font, "📝 " + loadingIndicator[loadingIndicatorIndex], x(64), y(0), textColor);
            if (frameCounter % 10 == 0) {
                loadingIndicatorIndex = (loadingIndicatorIndex + 1) % loadingIndicator.length;
            }
        }
    }

    private void drawFoodInformation(Minecraft minecraft, GuiGraphics guiGraphics) {
        assert minecraft.player != null;
        drawString(minecraft, guiGraphics, "🍗", x(-MARGIN + (w / 2) + 22), y(h - MARGIN - BOTTOM_TEXT_OFFSET - LINE_HEIGHT * 2 - 20 - LINE_HEIGHT));
        drawString(minecraft, guiGraphics, "S", x(-MARGIN + (w / 2) + 45), y(h - MARGIN - BOTTOM_TEXT_OFFSET - LINE_HEIGHT * 2 - 20 - LINE_HEIGHT));
        drawString(minecraft, guiGraphics, "🥵", x(-MARGIN + (w / 2) + 68), y(h - MARGIN - BOTTOM_TEXT_OFFSET - LINE_HEIGHT * 2 - 20 - LINE_HEIGHT));

        drawString(minecraft, guiGraphics, String.valueOf(minecraft.player.getFoodData().getFoodLevel()), x(-MARGIN + (w / 2) + 20), y(h - MARGIN - BOTTOM_TEXT_OFFSET - LINE_HEIGHT * 2 - 20));
        drawString(minecraft, guiGraphics, String.valueOf(minecraft.player.getFoodData().getSaturationLevel()), x(-MARGIN + (w / 2) + 42), y(h - MARGIN - BOTTOM_TEXT_OFFSET - LINE_HEIGHT * 2 - 20));
        drawString(minecraft, guiGraphics, String.valueOf(minecraft.player.getFoodData().getExhaustionLevel()), x(-MARGIN + (w / 2) + 66), y(h - MARGIN - BOTTOM_TEXT_OFFSET - LINE_HEIGHT * 2 - 20));
    }

    private void drawHealthInformation(Minecraft minecraft, GuiGraphics guiGraphics) {
        assert minecraft.player != null;
        int lineMultiplier = minecraft.player.getArmorValue() > 0 ? 3 : 2;
        drawString(minecraft, guiGraphics, "❤", x(-MARGIN + (w / 2) - 80), y(h - MARGIN - BOTTOM_TEXT_OFFSET - LINE_HEIGHT * lineMultiplier - 20 - LINE_HEIGHT));
        drawString(minecraft, guiGraphics, "🛡", x(-MARGIN + (w / 2) - 52), y(h - MARGIN - BOTTOM_TEXT_OFFSET - LINE_HEIGHT * lineMultiplier - 20 - LINE_HEIGHT));
        drawString(minecraft, guiGraphics, "🪖", x(-MARGIN + (w / 2) - 24), y(h - MARGIN - BOTTOM_TEXT_OFFSET - LINE_HEIGHT * lineMultiplier - 20 - LINE_HEIGHT));

        String formattedHealth = String.format("%.2f", minecraft.player.getHealth());
        formattedHealth = formattedHealth.replaceAll("\\.?0*$", "");

        drawString(minecraft, guiGraphics, formattedHealth, x(-MARGIN + (w / 2) - 86), y(h - MARGIN - BOTTOM_TEXT_OFFSET - LINE_HEIGHT * lineMultiplier - 20));
        drawString(minecraft, guiGraphics, String.valueOf(minecraft.player.getAbsorptionAmount()), x(-MARGIN + (w / 2) - 53), y(h - MARGIN - BOTTOM_TEXT_OFFSET - LINE_HEIGHT * lineMultiplier - 20));
        drawString(minecraft, guiGraphics, String.valueOf(minecraft.player.getArmorValue()), x(-MARGIN + (w / 2) - 24), y(h - MARGIN - BOTTOM_TEXT_OFFSET - LINE_HEIGHT * lineMultiplier - 20));
    }

    private void drawCoordinates(Minecraft minecraft, GuiGraphics guiGraphics) {
        assert minecraft.player != null;
        int x = (int) minecraft.player.getX();
        int y = (int) minecraft.player.getY();
        int z = (int) minecraft.player.getZ();
        drawString(minecraft, guiGraphics, "x(W) " + x, x(0), y(h - MARGIN - BOTTOM_TEXT_OFFSET - LINE_HEIGHT * 3));
        drawString(minecraft, guiGraphics, "z(N) " + z, x(0), y(h - MARGIN - BOTTOM_TEXT_OFFSET - LINE_HEIGHT * 2));
        drawString(minecraft, guiGraphics, "y(A) " + y, x(0), y(h - MARGIN - BOTTOM_TEXT_OFFSET - LINE_HEIGHT));
    }

    private void drawHeading(Minecraft minecraft, GuiGraphics guiGraphics, float yaw) {
        String[] directions = {"N", "E", "S", "W"};
        int[] positions = {
                Math.round(((yaw + 180) % 360 + 360) % 360),
                Math.round(((yaw + 180 + 90) % 360 + 360) % 360),
                Math.round(((yaw + 180 + 180) % 360 + 360) % 360),
                Math.round(((yaw + 180 + 270) % 360 + 360) % 360)
        };
        int sideMargin = 120;
        int spacing = 90 / 10; // Spacing between each '|' character

        for (int i = 0; i < directions.length; i++) {
            int pos = (w / 2) - 180 + positions[i];
            if (pos >= sideMargin && pos <= w - sideMargin) {
                guiGraphics.drawString(minecraft.font, directions[i], x(pos - MARGIN - 3), y(0), textColor);
                guiGraphics.drawString(minecraft.font, "|", x(pos - MARGIN - 1), y(15), textColor);
            }
        }
        for (int j = 0; j < 40; j++) {
            int pipePos = (w / 2) - 180 + ((positions[0] + j * spacing) % 360);
            if (pipePos >= sideMargin && pipePos <= w - sideMargin) {
                guiGraphics.drawString(minecraft.font, "|", x(pipePos - MARGIN - 1), y(10), textColor);
            }
        }
    }

    private void drawTime(Minecraft minecraft, GuiGraphics guiGraphics) {
        Instant now = Instant.now();
        ZonedDateTime utcDateTime = now.atZone(ZoneId.of("UTC"));
        DateTimeFormatter formatterDate = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter formatterTime = DateTimeFormatter.ofPattern("HH:mm:ss:SSS");
        String utcDate = formatterDate.format(utcDateTime);
        String utcTime = formatterTime.format(utcDateTime);
        assert minecraft.level != null;
        double minecraftSecondsPassed = minecraft.level.getDayTime() / 0.27777777777777777777777777777777777777777777777;
        long minecraftHours = (long) ((minecraftSecondsPassed / 3600) + 6) % 24;
        long minecraftMinutes = (long) ((minecraftSecondsPassed % 3600) / 60);
        long minecraftSeconds = (long) (minecraftSecondsPassed % 60);
        String minecraftTime = String.format("%02d:%02d:%02d", minecraftHours, minecraftMinutes, minecraftSeconds);
        String tickString = "🤖 t " + String.format("%,d", brain.currentTick);
        String gameTickString = "🎮 t " + String.format("%,d", minecraft.level.getDayTime());
        drawString(minecraft, guiGraphics, tickString, x(w - 5 - tickString.length() * 6), y(h - MARGIN - BOTTOM_TEXT_OFFSET - LINE_HEIGHT * 5));
        drawString(minecraft, guiGraphics, gameTickString, x(w - 6 - gameTickString.length() * 6), y(h - MARGIN - BOTTOM_TEXT_OFFSET - LINE_HEIGHT * 4));
        drawString(minecraft, guiGraphics, "⏹ " + minecraftTime, x(w - 70), y(h - MARGIN - BOTTOM_TEXT_OFFSET - LINE_HEIGHT * 3));
        drawString(minecraft, guiGraphics, "UTC " + utcTime + "Z", x(w - 108), y(h - MARGIN - BOTTOM_TEXT_OFFSET - LINE_HEIGHT * 2));
        drawString(minecraft, guiGraphics, utcDate, x(w - 80), y(h - MARGIN - BOTTOM_TEXT_OFFSET - LINE_HEIGHT));
    }

    private void draw$(Minecraft minecraft, GuiGraphics guiGraphics) {
        // Format the $ amount with comma separators
        String moneyString = "💰 $" + String.format("%,.2f", brain.$);

        drawString(minecraft, guiGraphics, moneyString, x(w - 8 - moneyString.length() * 6), y(0));
    }
    private void drawClaimedChunksBorder(Minecraft minecraft, GuiGraphics guiGraphics) {
        int borderColor = rgb(0, 0, 255); // Blue color

        for (ChunkPos chunkPos : brain.claimedChunks) {
            int minBlockX = chunkPos.getMinBlockX();
            int minBlockZ = chunkPos.getMinBlockZ();
            int maxBlockX = chunkPos.getMaxBlockX();
            int maxBlockZ = chunkPos.getMaxBlockZ();

            for (int x = minBlockX; x <= maxBlockX; x++) {
                drawVerticalLine(minecraft, guiGraphics, x, minBlockZ, borderColor);
                drawVerticalLine(minecraft, guiGraphics, x, maxBlockZ, borderColor);
            }

            for (int z = minBlockZ; z <= maxBlockZ; z++) {
                drawVerticalLine(minecraft, guiGraphics, minBlockX, z, borderColor);
                drawVerticalLine(minecraft, guiGraphics, maxBlockX, z, borderColor);
            }
        }
    }

    private void drawVerticalLine(Minecraft minecraft, GuiGraphics guiGraphics, int x, int z, int color) {
        ClientLevel level = minecraft.level;
        if (level == null) return;

        int maxY = level.getMaxBuildHeight() - 1;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos(x, maxY, z);

        while (mutablePos.getY() >= level.getMinBuildHeight()) {
            BlockState blockState = level.getBlockState(mutablePos);
            VoxelShape shape = blockState.getShape(level, mutablePos);

            if (!shape.isEmpty()) {
                Vec3 vec3d = minecraft.gameRenderer.getMainCamera().getPosition();
                double d0 = vec3d.x();
                double d1 = vec3d.y();
                double d2 = vec3d.z();

                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(x - d0, mutablePos.getY() - d1, z - d2);

                Matrix4f matrix = guiGraphics.pose().last().pose();
                Tesselator tesselator = Tesselator.getInstance();
                BufferBuilder bufferBuilder = tesselator.getBuilder();
                bufferBuilder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
                bufferBuilder.vertex(matrix, 0.0F, 0.0F, 0.0F).color(FastColor.ARGB32.red(color), FastColor.ARGB32.green(color), FastColor.ARGB32.blue(color), FastColor.ARGB32.alpha(color)).endVertex();
                bufferBuilder.vertex(matrix, 0.0F, 1.0F, 0.0F).color(FastColor.ARGB32.red(color), FastColor.ARGB32.green(color), FastColor.ARGB32.blue(color), FastColor.ARGB32.alpha(color)).endVertex();
                tesselator.end();
                BufferUploader.draw(bufferBuilder.end());

                guiGraphics.pose().popPose();
                break;
            }

            mutablePos.setY(mutablePos.getY() - 1);
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