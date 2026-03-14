package org.mcbst.msmc.client.match;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Simple full-screen UI shown while we are probing candidate servers.
 */
public class WaitingScreen extends Screen {
    private static final Component TITLE = Component.literal("正在为您匹配延迟最小的服务器…");
    private static final long SPINNER_PERIOD_MS = 200L;

    public WaitingScreen() {
        super(Component.literal("Msmc Matching"));
    }

    @Override
    protected void init() {
        // No buttons; prevent ESC closing.
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Opaque black background.
        graphics.fill(0, 0, this.width, this.height, 0xFF000000);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        graphics.drawCenteredString(this.font, TITLE, centerX, centerY - 10, 0xFFFFFF);
        graphics.drawCenteredString(this.font, spinner(), centerX, centerY + 10, 0xAAAAAA);
    }

    private String spinner() {
        int frame = (int) ((System.currentTimeMillis() / SPINNER_PERIOD_MS) % 4);
        return switch (frame) {
            case 0 -> "⠋ 正在检测…";
            case 1 -> "⠙ 正在检测…";
            case 2 -> "⠹ 正在检测…";
            default -> "⠸ 正在检测…";
        };
    }
}
