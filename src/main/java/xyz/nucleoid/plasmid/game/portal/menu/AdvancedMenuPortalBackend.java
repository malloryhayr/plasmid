package xyz.nucleoid.plasmid.game.portal.menu;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.elements.GuiElementInterface;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.portal.GamePortal.GuiProvider;
import xyz.nucleoid.plasmid.game.portal.GamePortalBackend;
import xyz.nucleoid.plasmid.game.portal.GamePortalDisplay;
import xyz.nucleoid.plasmid.util.Guis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class AdvancedMenuPortalBackend implements GamePortalBackend {
    private final Text name;

    private final List<Text> description;
    private final ItemStack icon;

    private final List<MenuEntryConfig> entryConfigs;
    private List<MenuEntry> entries;

    AdvancedMenuPortalBackend(Text name, List<Text> description, ItemStack icon, List<MenuEntryConfig> entryConfigs) {
        this.name = name;

        this.description = description;
        this.icon = icon;

        this.entryConfigs = entryConfigs;
    }

    @Override
    public Text getName() {
        return this.name;
    }

    @Override
    public List<Text> getDescription() {
        return this.description;
    }

    @Override
    public ItemStack getIcon() {
        return this.icon;
    }

    @Override
    public int getPlayerCount() {
        int count = 0;
        var list = new ObjectOpenCustomHashSet<GameSpace>(Util.identityHashStrategy());
        provideGameSpaces(list::add);
        for (var entry : list) {
            count += Math.max(0, entry.getPlayers().size());
        }
        return count;
    }

    @Override
    public void provideGameSpaces(Consumer<GameSpace> consumer) {
        for (var entry : this.getEntries()) {
            entry.provideGameSpaces(consumer);
        }
    }

    private List<GuiElementInterface> getGuiElements() {
        List<GuiElementInterface> elements = new ArrayList<>();

        for (var entry : this.getEntries()) {
            var uiEntry = entry.createGuiElement();
            elements.add(uiEntry);
        }

        return elements;
    }

    @Override
    public GuiProvider getGuiProvider() {
        return this::getGuiElements;
    }

    private List<MenuEntry> getEntries() {
        if (this.entries == null) {
            this.entries = new ArrayList<MenuEntry>(this.entryConfigs.size());
            for (var configEntry : this.entryConfigs) {
                this.entries.add(configEntry.createEntry());
            }
        }

        return this.entries;
    }

    @Override
    public void applyTo(ServerPlayerEntity player) {
        var ui = Guis.createSelectorGui(player, this.name.copy(), true, this.getGuiElements());
        ui.open();
    }
}
