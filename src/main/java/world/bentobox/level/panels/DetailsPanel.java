package world.bentobox.level.panels;


import com.google.common.base.Enums;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import lv.id.bonne.panelutils.PanelUtils;
import world.bentobox.bentobox.api.panels.PanelItem;
import world.bentobox.bentobox.api.panels.TemplatedPanel;
import world.bentobox.bentobox.api.panels.builders.PanelItemBuilder;
import world.bentobox.bentobox.api.panels.builders.TemplatedPanelBuilder;
import world.bentobox.bentobox.api.panels.reader.ItemTemplateRecord;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.hooks.LangUtilsHook;
import world.bentobox.bentobox.util.Pair;
import world.bentobox.level.Level;
import world.bentobox.level.objects.IslandLevels;
import world.bentobox.level.util.Utils;


/**
 * This class opens GUI that shows generator view for user.
 */
public class DetailsPanel
{
    // ---------------------------------------------------------------------
    // Section: Internal Constructor
    // ---------------------------------------------------------------------


    /**
     * This is internal constructor. It is used internally in current class to avoid creating objects everywhere.
     *
     * @param addon Level object
     * @param world World where user is operating
     * @param user User who opens panel
     */
    private DetailsPanel(Level addon,
        World world,
        User user)
    {
        this.addon = addon;
        this.world = world;
        this.user = user;

        this.island = this.addon.getIslands().getIsland(world, user);

        if (this.island != null)
        {
            this.levelsData = this.addon.getManager().getLevelsData(this.island);
        }
        else
        {
            this.levelsData = null;
        }

        // By default no-filters are active.
        this.activeTab = Tab.ALL_BLOCKS;
        this.materialCountList = new ArrayList<>(Material.values().length);

        this.updateFilters();
    }


    /**
     * This method builds this GUI.
     */
    private void build()
    {
        if (this.island == null || this.levelsData == null)
        {
            // Nothing to see.
            Utils.sendMessage(this.user, this.user.getTranslation("general.errors.no-island"));
            return;
        }

        if (this.levelsData.getMdCount().isEmpty() && this.levelsData.getUwCount().isEmpty())
        {
            // Nothing to see.
            Utils.sendMessage(this.user, this.user.getTranslation("level.conversations.no-data"));
            return;
        }

        // Start building panel.
        TemplatedPanelBuilder panelBuilder = new TemplatedPanelBuilder();
        panelBuilder.user(this.user);
        panelBuilder.world(this.user.getWorld());

        panelBuilder.template("detail_panel", new File(this.addon.getDataFolder(), "panels"));

        panelBuilder.parameters("[name]", this.user.getName());

        panelBuilder.registerTypeBuilder("NEXT", this::createNextButton);
        panelBuilder.registerTypeBuilder("PREVIOUS", this::createPreviousButton);
        panelBuilder.registerTypeBuilder("BLOCK", this::createMaterialButton);

        // Register tabs
        panelBuilder.registerTypeBuilder("TAB", this::createTabButton);

        // Register unknown type builder.
        panelBuilder.build();
    }


    /**
     * This method updates filter of elements based on tabs.
     */
    private void updateFilters()
    {
        this.materialCountList.clear();

        switch (this.activeTab)
        {
            case ALL_BLOCKS -> {
                Map<Material, Integer> materialCountMap = new EnumMap<>(Material.class);

                materialCountMap.putAll(this.levelsData.getMdCount());

                // Add underwater blocks.
                this.levelsData.getUwCount().forEach((material, count) -> {
                    materialCountMap.put(material,
                        materialCountMap.computeIfAbsent(material, key -> 0) + count);
                });

                materialCountMap.entrySet().stream().sorted((Map.Entry.comparingByKey())).
                    forEachOrdered(entry ->
                        this.materialCountList.add(new Pair<>(entry.getKey(), entry.getValue())));
            }
            case ABOVE_SEA_LEVEL -> {
                this.levelsData.getMdCount().entrySet().stream().sorted((Map.Entry.comparingByKey())).
                    forEachOrdered(entry ->
                        this.materialCountList.add(new Pair<>(entry.getKey(), entry.getValue())));
            }
            case UNDERWATER -> {
                this.levelsData.getUwCount().entrySet().stream().sorted((Map.Entry.comparingByKey())).
                    forEachOrdered(entry ->
                        this.materialCountList.add(new Pair<>(entry.getKey(), entry.getValue())));
            }
            case SPAWNER -> {
                int aboveWater = this.levelsData.getMdCount().getOrDefault(Material.SPAWNER, 0);
                int underWater = this.levelsData.getUwCount().getOrDefault(Material.SPAWNER, 0);

                // TODO: spawners need some touch...
                this.materialCountList.add(new Pair<>(Material.SPAWNER, underWater + aboveWater));
            }
        }

        this.pageIndex = 0;
    }


// ---------------------------------------------------------------------
// Section: Tab Button Type
// ---------------------------------------------------------------------


    /**
     * Create tab button panel item.
     *
     * @param template the template
     * @param slot the slot
     * @return the panel item
     */
    private PanelItem createTabButton(ItemTemplateRecord template, TemplatedPanel.ItemSlot slot)
    {
        PanelItemBuilder builder = new PanelItemBuilder();

        if (template.icon() != null)
        {
            // Set icon
            builder.icon(template.icon().clone());
        }

        if (template.title() != null)
        {
            // Set title
            builder.name(this.user.getTranslation(this.world, template.title()));
        }

        if (template.description() != null)
        {
            // Set description
            builder.description(this.user.getTranslation(this.world, template.description()));
        }

        Tab tab = Enums.getIfPresent(Tab.class, String.valueOf(template.dataMap().get("tab"))).or(Tab.ALL_BLOCKS);

        // Get only possible actions, by removing all inactive ones.
        List<ItemTemplateRecord.ActionRecords> activeActions = new ArrayList<>(template.actions());

        activeActions.removeIf(action ->
            "VIEW".equalsIgnoreCase(action.actionType()) && this.activeTab == tab);

        // Add Click handler
        builder.clickHandler((panel, user, clickType, i) ->
        {
            for (ItemTemplateRecord.ActionRecords action : activeActions)
            {
                if (clickType == action.clickType() || ClickType.UNKNOWN.equals(action.clickType()))
                {
                    if ("VIEW".equalsIgnoreCase(action.actionType()))
                    {
                        this.activeTab = tab;

                        // Update filters.
                        this.updateFilters();
                        this.build();
                    }
                }
            }

            return true;
        });

        // Collect tooltips.
        List<String> tooltips = activeActions.stream().
            filter(action -> action.tooltip() != null).
            map(action -> this.user.getTranslation(this.world, action.tooltip())).
            filter(text -> !text.isBlank()).
            collect(Collectors.toCollection(() -> new ArrayList<>(template.actions().size())));

        // Add tooltips.
        if (!tooltips.isEmpty())
        {
            // Empty line and tooltips.
            builder.description("");
            builder.description(tooltips);
        }

        builder.glow(this.activeTab == tab);

        return builder.build();
    }


// ---------------------------------------------------------------------
// Section: Create common buttons
// ---------------------------------------------------------------------


    /**
     * Create next button panel item.
     *
     * @param template the template
     * @param slot the slot
     * @return the panel item
     */
    private PanelItem createNextButton(ItemTemplateRecord template, TemplatedPanel.ItemSlot slot)
    {
        long size = this.materialCountList.size();

        if (size <= slot.amountMap().getOrDefault("BLOCK", 1) ||
            1.0 * size / slot.amountMap().getOrDefault("BLOCK", 1) <= this.pageIndex + 1)
        {
            // There are no next elements
            return null;
        }

        int nextPageIndex = this.pageIndex + 2;

        PanelItemBuilder builder = new PanelItemBuilder();

        if (template.icon() != null)
        {
            ItemStack clone = template.icon().clone();

            if ((Boolean) template.dataMap().getOrDefault("indexing", false))
            {
                clone.setAmount(nextPageIndex);
            }

            builder.icon(clone);
        }

        if (template.title() != null)
        {
            builder.name(this.user.getTranslation(this.world, template.title()));
        }

        if (template.description() != null)
        {
            builder.description(this.user.getTranslation(this.world, template.description(),
                "[number]", String.valueOf(nextPageIndex)));
        }

        // Add ClickHandler
        builder.clickHandler((panel, user, clickType, i) ->
        {
            for (ItemTemplateRecord.ActionRecords action : template.actions())
            {
                if (clickType == action.clickType() || ClickType.UNKNOWN.equals(action.clickType()))
                {
                    if ("NEXT".equalsIgnoreCase(action.actionType()))
                    {
                        this.pageIndex++;
                        this.build();
                    }
                }
            }

            // Always return true.
            return true;
        });

        // Collect tooltips.
        List<String> tooltips = template.actions().stream().
            filter(action -> action.tooltip() != null).
            map(action -> this.user.getTranslation(this.world, action.tooltip())).
            filter(text -> !text.isBlank()).
            collect(Collectors.toCollection(() -> new ArrayList<>(template.actions().size())));

        // Add tooltips.
        if (!tooltips.isEmpty())
        {
            // Empty line and tooltips.
            builder.description("");
            builder.description(tooltips);
        }

        return builder.build();
    }


    /**
     * Create previous button panel item.
     *
     * @param template the template
     * @param slot the slot
     * @return the panel item
     */
    private PanelItem createPreviousButton(ItemTemplateRecord template, TemplatedPanel.ItemSlot slot)
    {
        if (this.pageIndex == 0)
        {
            // There are no next elements
            return null;
        }

        int previousPageIndex = this.pageIndex;

        PanelItemBuilder builder = new PanelItemBuilder();

        if (template.icon() != null)
        {
            ItemStack clone = template.icon().clone();

            if ((Boolean) template.dataMap().getOrDefault("indexing", false))
            {
                clone.setAmount(previousPageIndex);
            }

            builder.icon(clone);
        }

        if (template.title() != null)
        {
            builder.name(this.user.getTranslation(this.world, template.title()));
        }

        if (template.description() != null)
        {
            builder.description(this.user.getTranslation(this.world, template.description(),
                "[number]", String.valueOf(previousPageIndex)));
        }

        // Add ClickHandler
        builder.clickHandler((panel, user, clickType, i) ->
        {
            for (ItemTemplateRecord.ActionRecords action : template.actions())
            {
                if (clickType == action.clickType() || ClickType.UNKNOWN.equals(action.clickType()))
                {
                    if ("PREVIOUS".equalsIgnoreCase(action.actionType()))
                    {
                        this.pageIndex--;
                        this.build();
                    }
                }
            }

            // Always return true.
            return true;
        });

        // Collect tooltips.
        List<String> tooltips = template.actions().stream().
            filter(action -> action.tooltip() != null).
            map(action -> this.user.getTranslation(this.world, action.tooltip())).
            filter(text -> !text.isBlank()).
            collect(Collectors.toCollection(() -> new ArrayList<>(template.actions().size())));

        // Add tooltips.
        if (!tooltips.isEmpty())
        {
            // Empty line and tooltips.
            builder.description("");
            builder.description(tooltips);
        }

        return builder.build();
    }


// ---------------------------------------------------------------------
// Section: Create Material Button
// ---------------------------------------------------------------------


    /**
     * Create material button panel item.
     *
     * @param template the template
     * @param slot the slot
     * @return the panel item
     */
    private PanelItem createMaterialButton(ItemTemplateRecord template, TemplatedPanel.ItemSlot slot)
    {
        if (this.materialCountList.isEmpty())
        {
            // Does not contain any generators.
            return null;
        }

        int index = this.pageIndex * slot.amountMap().getOrDefault("BLOCK", 1) + slot.slot();

        if (index >= this.materialCountList.size())
        {
            // Out of index.
            return null;
        }

        return this.createMaterialButton(template, this.materialCountList.get(index));
    }


    /**
     * This method creates button for material.
     *
     * @param template the template of the button
     * @param materialCount materialCount which button must be created.
     * @return PanelItem for generator tier.
     */
    private PanelItem createMaterialButton(ItemTemplateRecord template,
        Pair<Material, Integer> materialCount)
    {
        PanelItemBuilder builder = new PanelItemBuilder();

        if (template.icon() != null)
        {
            builder.icon(template.icon().clone());
        }
        else
        {
            builder.icon(PanelUtils.getMaterialItem(materialCount.getKey()));
        }

        if (materialCount.getValue() < 64)
        {
            builder.amount(materialCount.getValue());
        }

        if (template.title() != null)
        {
            builder.name(this.user.getTranslation(this.world, template.title(),
                "[number]", String.valueOf(materialCount.getValue()),
                "[material]", DetailsPanel.prettifyObject(materialCount.getKey(), this.user)));
        }

        String description = DetailsPanel.prettifyDescription(materialCount.getKey(), this.user);

        final String reference = "level.gui.buttons.material.";
        String blockId = this.user.getTranslationOrNothing(reference + "id",
            "[id]", materialCount.getKey().name());

        int blockValue = this.addon.getBlockConfig().getBlockValues().getOrDefault(materialCount.getKey(), 0);
        String value = blockValue > 0 ? this.user.getTranslationOrNothing(reference + "value",
            "[number]", String.valueOf(blockValue)) : "";

        int blockLimit = this.addon.getBlockConfig().getBlockLimits().getOrDefault(materialCount.getKey(), 0);
        String limit = blockLimit > 0 ? this.user.getTranslationOrNothing(reference + "limit",
            "[number]",  String.valueOf(blockLimit)) : "";

        String count = this.user.getTranslationOrNothing(reference + "count",
            "[number]", String.valueOf(materialCount.getValue()));

        if (template.description() != null)
        {
            builder.description(this.user.getTranslation(this.world, template.description(),
                    "[description]", description,
                    "[id]", blockId,
                    "[value]", value,
                    "[limit]", limit,
                    "[count]", count).
                replaceAll("(?m)^[ \\t]*\\r?\\n", "").
                replaceAll("(?<!\\\\)\\|", "\n").
                replaceAll("\\\\\\|", "|"));
        }

        return builder.build();
    }


// ---------------------------------------------------------------------
// Section: Other Methods
// ---------------------------------------------------------------------


    /**
     * This method is used to open UserPanel outside this class. It will be much easier to open panel with single method
     * call then initializing new object.
     *
     * @param addon Level object
     * @param world World where user is operating
     * @param user User who opens panel
     */
    public static void openPanel(Level addon,
        World world,
        User user)
    {
        new DetailsPanel(addon, world, user).build();
    }


    /**
     * Prettify Material object for user.
     * @param object Object that must be pretty.
     * @param user User who will see the object.
     * @return Prettified string for Material.
     */
    private static String prettifyObject(Material object, User user)
    {
        // Nothing to translate
        if (object == null)
        {
            return "";
        }

        // Find addon structure with:
        // [addon]:
        //   materials:
        //     [material]:
        //       name: [name]
        String translation = user.getTranslationOrNothing("level.materials." + object.name().toLowerCase() + ".name");

        if (!translation.isEmpty())
        {
            // We found our translation.
            return translation;
        }

        // Find addon structure with:
        // [addon]:
        //   materials:
        //     [material]: [name]

        translation = user.getTranslationOrNothing("level.materials." + object.name().toLowerCase());

        if (!translation.isEmpty())
        {
            // We found our translation.
            return translation;
        }

        // Find general structure with:
        // materials:
        //   [material]: [name]

        translation = user.getTranslationOrNothing("materials." + object.name().toLowerCase());

        if (!translation.isEmpty())
        {
            // We found our translation.
            return translation;
        }

        // Use Lang Utils Hook to translate material
        return LangUtilsHook.getMaterialName(object, user);
    }


    /**
     * Prettify Material object description for user.
     * @param object Object that must be pretty.
     * @param user User who will see the object.
     * @return Prettified description string for Material.
     */
    public static String prettifyDescription(Material object, User user)
    {
        // Nothing to translate
        if (object == null)
        {
            return "";
        }

        // Find addon structure with:
        // [addon]:
        //   materials:
        //     [material]:
        //       description: [text]
        String translation = user.getTranslationOrNothing("level.materials." + object.name().toLowerCase() + ".description");

        if (!translation.isEmpty())
        {
            // We found our translation.
            return translation;
        }

        // No text to return.
        return "";
    }


// ---------------------------------------------------------------------
// Section: Enums
// ---------------------------------------------------------------------


    /**
     * This enum holds possible tabs for current gui.
     */
    private enum Tab
    {
        /**
         * All block Tab
         */
        ALL_BLOCKS,
        /**
         * Above Sea level Tab.
         */
        ABOVE_SEA_LEVEL,
        /**
         * Underwater Tab.
         */
        UNDERWATER,
        /**
         * Spawner Tab.
         */
        SPAWNER
    }


// ---------------------------------------------------------------------
// Section: Variables
// ---------------------------------------------------------------------

    /**
     * This variable holds targeted island.
     */
    private final Island island;

    /**
     * This variable holds targeted island level data.
     */
    private final IslandLevels levelsData;

    /**
     * This variable allows to access addon object.
     */
    private final Level addon;

    /**
     * This variable holds user who opens panel. Without it panel cannot be opened.
     */
    private final User user;

    /**
     * This variable holds a world to which gui referee.
     */
    private final World world;

    /**
     * This variable stores the list of elements to display.
     */
    private final List<Pair<Material, Integer>> materialCountList;

    /**
     * This variable holds current pageIndex for multi-page generator choosing.
     */
    private int pageIndex;

    /**
     * This variable stores which tab currently is active.
     */
    private Tab activeTab;
}
