package com.geopoliticsmod.nation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Nation (joueurs ou PNJ). Lecture publique, modification réservée à {@link NationSavedData}
 * (setters package-private) pour que les index et la sauvegarde restent cohérents.
 */
public final class Nation {
    public static final Codec<Nation> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Nation::id),
            Codec.STRING.fieldOf("name").forGetter(Nation::name),
            Codec.STRING.optionalFieldOf("motto", "").forGetter(Nation::motto),
            Codec.INT.fieldOf("color").forGetter(Nation::color),
            NationController.CODEC.fieldOf("controller").forGetter(Nation::controller),
            Codec.LONG.fieldOf("created_at").forGetter(Nation::createdAt),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, NationRole.CODEC).fieldOf("members").forGetter(n -> n.members),
            City.CODEC.listOf().fieldOf("cities").forGetter(n -> n.cities),
            UUIDUtil.CODEC.optionalFieldOf("capital").forGetter(n -> Optional.ofNullable(n.capitalId))
    ).apply(i, Nation::new));

    private final UUID id;
    private final String name;
    private String motto;
    private final int color;
    private final NationController controller;
    private final long createdAt;
    private final Map<UUID, NationRole> members;
    private final List<City> cities;
    private UUID capitalId;

    private Nation(UUID id, String name, String motto, int color, NationController controller, long createdAt,
                   Map<UUID, NationRole> members, List<City> cities, Optional<UUID> capitalId) {
        this.id = id;
        this.name = name;
        this.motto = motto;
        this.color = color;
        this.controller = controller;
        this.createdAt = createdAt;
        this.members = new LinkedHashMap<>(members);
        this.cities = new ArrayList<>(cities);
        this.capitalId = capitalId.orElse(null);
    }

    static Nation found(String name, NationController controller, int color, long now) {
        return new Nation(UUID.randomUUID(), name, "", color, controller, now, Map.of(), List.of(), Optional.empty());
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public String motto() { return motto; }
    public int color() { return color; }
    public NationController controller() { return controller; }
    public long createdAt() { return createdAt; }

    /** Nom affiché dans la couleur de la nation. */
    public MutableComponent displayName() {
        return Component.literal(name).withColor(color);
    }

    public Map<UUID, NationRole> members() {
        return Collections.unmodifiableMap(members);
    }

    public Optional<NationRole> roleOf(UUID player) {
        return Optional.ofNullable(members.get(player));
    }

    public Optional<UUID> leader() {
        return members.entrySet().stream().filter(e -> e.getValue() == NationRole.LEADER).map(Map.Entry::getKey).findFirst();
    }

    public List<City> cities() {
        return Collections.unmodifiableList(cities);
    }

    public Optional<City> city(String cityName) {
        return cities.stream().filter(c -> c.name().equalsIgnoreCase(cityName)).findFirst();
    }

    public Optional<City> capital() {
        return capitalId == null ? Optional.empty() : cities.stream().filter(c -> c.id().equals(capitalId)).findFirst();
    }

    void setMotto(String motto) { this.motto = motto; }
    void putMember(UUID player, NationRole role) { members.put(player, role); }
    void removeMember(UUID player) { members.remove(player); }
    void addCity(City city) { cities.add(city); }
    void removeCity(City city) { cities.removeIf(c -> c.id().equals(city.id())); }
    void setCapitalId(UUID cityId) { this.capitalId = cityId; }
}
