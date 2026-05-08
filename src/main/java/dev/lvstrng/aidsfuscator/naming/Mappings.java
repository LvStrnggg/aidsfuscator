package dev.lvstrng.aidsfuscator.naming;

import java.util.HashMap;
import java.util.Map;

public enum Mappings {
    CLASS, FIELD, METHOD, RESOURCE;

    private final Map<String, Mapping> mappings;

    Mappings() {
        this.mappings = new HashMap<>();
    }

    public void register(String oldKey, Mapping newKey) {
        mappings.put(oldKey, newKey);
    }

    public Mapping retrieve(String oldKey) {
        return mappings.getOrDefault(oldKey, new Mapping(oldKey, oldKey));
    }

    public boolean containsOld(String oldKey) {
        return mappings.containsKey(oldKey);
    }

    public boolean containsNew(String key) {
        return mappings.values().stream().anyMatch(e -> e.key().equals(key));
    }

    public Map<String, Mapping> getMappings() {
        return mappings;
    }
}
