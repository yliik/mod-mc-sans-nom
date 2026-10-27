package com.geopoliticsmod.nation;

import com.geopoliticsmod.GeoPoliticsMod;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Registre persistant des nations (fichier data/geopoliticsmod_nations.dat de l'Overworld).
 *
 * <p>Seule classe qui modifie une {@link Nation} : chaque mutation met à jour les index en
 * mémoire (joueur → nation, nom → nation) et marque la sauvegarde comme modifiée. Les règles
 * métier (permissions, validations, messages) sont dans {@link NationManager}.
 */
public final class NationSavedData extends SavedData {
    private static final String NAME = GeoPoliticsMod.MODID + "_nations";
    private static final int FORMAT = 1;
    private static final Factory<NationSavedData> FACTORY = new Factory<>(NationSavedData::new, NationSavedData::load, null);

    private final Map<UUID, Nation> nations = new LinkedHashMap<>();
    private final Map<UUID, UUID> nationByMember = new HashMap<>();
    private final Map<String, UUID> nationByName = new HashMap<>();

    public static NationSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    // --- Lecture ---

    public Collection<Nation> all() {
        return Collections.unmodifiableCollection(nations.values());
    }

    public Optional<Nation> get(UUID id) {
        return Optional.ofNullable(nations.get(id));
    }

    public Optional<Nation> byName(String name) {
        return Optional.ofNullable(nationByName.get(nameKey(name))).map(nations::get);
    }

    public Optional<Nation> nationOf(UUID player) {
        return Optional.ofNullable(nationByMember.get(player)).map(nations::get);
    }

    // --- Mutations ---

    Nation create(String name, NationController controller, UUID leader, int color, long now) {
        Nation nation = Nation.found(name, controller, color, now);
        nation.putMember(leader, NationRole.LEADER);
        nations.put(nation.id(), nation);
        index(nation);
        setDirty();
        return nation;
    }

    void remove(Nation nation) {
        nations.remove(nation.id());
        nationByName.remove(nameKey(nation.name()));
        nation.members().keySet().forEach(nationByMember::remove);
        setDirty();
    }

    void putMember(Nation nation, UUID player, NationRole role) {
        nation.putMember(player, role);
        nationByMember.put(player, nation.id());
        setDirty();
    }

    void removeMember(Nation nation, UUID player) {
        nation.removeMember(player);
        nationByMember.remove(player);
        setDirty();
    }

    void setMotto(Nation nation, String motto) {
        nation.setMotto(motto);
        setDirty();
    }

    void addCity(Nation nation, City city) {
        nation.addCity(city);
        setDirty();
    }

    void removeCity(Nation nation, City city) {
        nation.removeCity(city);
        setDirty();
    }

    void setCapital(Nation nation, City city) {
        nation.setCapitalId(city == null ? null : city.id());
        setDirty();
    }

    // --- Persistance ---

    private void index(Nation nation) {
        nationByName.put(nameKey(nation.name()), nation.id());
        nation.members().keySet().forEach(member -> nationByMember.put(member, nation.id()));
    }

    private static String nameKey(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT);
        Nation.CODEC.listOf().encodeStart(NbtOps.INSTANCE, List.copyOf(nations.values()))
                .resultOrPartial(error -> GeoPoliticsMod.LOGGER.error("Sauvegarde des nations : {}", error))
                .ifPresent(list -> tag.put("nations", list));
        return tag;
    }

    private static NationSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        NationSavedData data = new NationSavedData();
        if (tag.contains("nations")) {
            Nation.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("nations"))
                    .resultOrPartial(error -> GeoPoliticsMod.LOGGER.error("Chargement des nations : {}", error))
                    .ifPresent(list -> list.forEach(nation -> {
                        data.nations.put(nation.id(), nation);
                        data.index(nation);
                    }));
        }
        return data;
    }
}
