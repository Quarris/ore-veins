/*
 * Part of the Ore Veins Mod by alcatrazEscapee
 * Work under Copyright. Licensed under the GPL-3.0.
 * See the project LICENSE.md for more information.
 */

package com.alcatrazescapee.oreveins.vein;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

import com.google.common.base.Strings;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.block.BlockState;

import com.alcatrazescapee.oreveins.api.IVeinType;
import com.alcatrazescapee.oreveins.util.IWeightedList;
import com.alcatrazescapee.oreveins.util.json.BlockStateDeserializer;
import com.alcatrazescapee.oreveins.util.json.BlockStateListDeserializer;
import com.alcatrazescapee.oreveins.util.json.VeinTypeDeserializer;
import com.alcatrazescapee.oreveins.util.json.WeightedListDeserializer;

import static com.alcatrazescapee.oreveins.OreVeins.MOD_ID;

public final class VeinRegistry
{
    private static final Logger LOGGER = LogManager.getLogger();
    private static final BiMap<String, IVeinType> VEINS = HashBiMap.create();
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(IVeinType.class, new VeinTypeDeserializer())
            .registerTypeAdapter(new TypeToken<List<BlockState>>() {}.getType(), new BlockStateListDeserializer())
            .registerTypeAdapter(IWeightedList.class, new WeightedListDeserializer())
            .registerTypeAdapter(BlockState.class, new BlockStateDeserializer())
            .create();

    public static Collection<IVeinType> getVeins()
    {
        return VEINS.values();
    }

    public static IVeinType getVein(String key)
    {
        return VEINS.get(key);
    }

    public static String getName(IVeinType key)
    {
        return VEINS.inverse().get(key);
    }

    public static void preInit()
    {
        LOGGER.info("Loading or creating ore generation config file");

        final File configFolder = new File(System.getProperty("user.dir") + "/config", MOD_ID);
        if (!configFolder.exists() && !configFolder.mkdir())
        {
            LOGGER.warn("Problem creating Ore Veins config directory.");
        }
        else
        {
            final File defaultFile = new File(configFolder, "ore_veins.json");
            String defaultData = null;
            if (defaultFile.exists())
            {
                try
                {
                    defaultData = FileUtils.readFileToString(defaultFile, Charset.defaultCharset());
                }
                catch (IOException e)
                {
                    LOGGER.warn("Error reading default file.", e);
                }
            }
            if (Strings.isNullOrEmpty(defaultData))
            {
                // todo: write default generation
                try (BufferedWriter writer = Files.newBufferedWriter(defaultFile.toPath()))
                {
                    writer.write("{\n\n}");
                }
                catch (IOException e)
                {
                    LOGGER.warn("Error initializing default file", e);
                }
            }
        }

        File[] worldGenFiles = configFolder.listFiles((file, name) -> name != null && name.toLowerCase(Locale.US).endsWith(".json"));
        if (worldGenFiles == null)
        {
            LOGGER.warn("There are no valid files in the world gen folder, this mod will not do anything!");
        }
        else
        {
            String worldGenData;
            for (File worldGenFile : worldGenFiles)
            {
                worldGenData = null;
                if (worldGenFile.exists())
                {
                    try
                    {
                        worldGenData = FileUtils.readFileToString(worldGenFile, Charset.defaultCharset());
                    }
                    catch (IOException e)
                    {
                        LOGGER.warn("Error reading world gen file.", e);
                        continue;
                    }
                }

                if (Strings.isNullOrEmpty(worldGenData))
                {
                    LOGGER.warn("There is no data in a world gen file.");
                    continue;
                }

                try
                {
                    Set<Map.Entry<String, JsonElement>> allVeinsJson = new JsonParser().parse(worldGenData).getAsJsonObject().entrySet();
                    for (Map.Entry<String, JsonElement> entry : allVeinsJson)
                    {
                        try
                        {
                            IVeinType<?> vein = GSON.fromJson(entry.getValue(), IVeinType.class);
                            if (vein.isValid())
                            {
                                VEINS.put(entry.getKey(), vein);
                            }
                            else
                            {
                                LOGGER.warn("Vein {} is invalid. This is likely caused by one or more required parameters being left out.", entry.getKey());
                            }
                        }
                        catch (Throwable e)
                        {
                            LOGGER.warn("Vein {} failed to parse. This is most likely caused by incorrectly specified JSON.", entry.getKey());
                            LOGGER.warn("Error: ", e);
                        }
                    }
                }
                catch (Throwable e)
                {
                    LOGGER.warn("File {} failed to parse. This is most likely caused by invalid JSON. Error: {}", worldGenFile, e);
                    LOGGER.warn("Error: ", e);
                }
            }
        }

        LOGGER.info("Registered {} Veins Successfully.", VeinRegistry.getVeins().size());
    }

    private VeinRegistry() {}
}
