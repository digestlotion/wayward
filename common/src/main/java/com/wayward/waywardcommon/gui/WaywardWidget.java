package com.wayward.waywardcommon.gui;

import com.wayward.waywardcommon.network.WaywardService;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class WaywardWidget extends AbstractContainerEventHandler implements Renderable, NarratableEntry {

    private final int x, y, w, h;
    private final EditBox uuid, world;
    private final Button join, invite, add;
    private final List<AbstractWidget> children;

    public WaywardWidget(Minecraft mc, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;

        int bW = (w / 3) - 4;
        int contentH = 20 + 4 + 20 + 8 + 20;
        int startY = y + (h - contentH) / 2;

        uuid = new EditBox(mc.font, x, startY, w, 20, Component.literal("uuid"));
        uuid.setMaxLength(256);
        uuid.setHint(Component.literal("uuid"));

        world = new EditBox(mc.font, x, startY + 24, w, 20, Component.literal("world"));
        world.setMaxLength(256);
        world.setHint(Component.literal("world"));

        int bY = startY + 24 + 20 + 8;

        join = Button.builder(Component.literal("Join"), b -> WaywardService.joinWorld(uuid.getValue(), world.getValue()))
            .bounds(x, bY, bW, 20)
            .build();

        invite = Button.builder(Component.literal("Invite"), b -> WaywardService.invite(uuid.getValue(), world.getValue()))
            .bounds(x + bW + 4, bY, bW, 20)
            .build();

        add = Button.builder(Component.literal("Add"), b -> WaywardService.addWorld(world.getValue()))
            .bounds(x + (bW + 4) * 2, bY, bW, 20)
            .build();

        children = List.of(uuid, world, join, invite, add);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return children;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public void updateNarration(NarrationElementOutput output) {
        for (AbstractWidget child : children) {
            child.updateNarration(output);
        }
    }

    @Override
    public NarrationPriority narrationPriority() {
        for (AbstractWidget child : children) {
            NarrationPriority p = child.narrationPriority();
            if (p != NarrationPriority.NONE) return p;
        }
        return NarrationPriority.NONE;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        for (AbstractWidget child : children) {
            child.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }
}
