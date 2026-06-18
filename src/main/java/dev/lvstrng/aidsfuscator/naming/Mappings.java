package dev.lvstrng.aidsfuscator.naming;

import java.util.HashMap;
import java.util.Map;

public enum Mappings {
    CLASS, FIELD, METHOD;

    private final Map<String, Mapping> mappings;
    private final Map<String, Mapping> temp;
    private final Map<String, String> newToOld;

    Mappings() {
        this.mappings   = new HashMap<>();
        this.temp       = new HashMap<>();
        this.newToOld   = new HashMap<>();
    }

    public void register(String oldKey, Mapping newKey) {
        mappings.put(oldKey, newKey);
        temp.put(oldKey, newKey);
        newToOld.put(newKey.key(), oldKey);
    }

    public Mapping retrieve(String oldKey) {
        return mappings.getOrDefault(oldKey, new Mapping(oldKey, oldKey));
    }

    public Mapping retrieveTemp(String oldKey) {
        return temp.getOrDefault(oldKey, new Mapping(oldKey, oldKey));
    }

    public String retrieveOld(String newKey) {
        return newToOld.getOrDefault(newKey, newKey);
    }

    public boolean containsOld(String oldKey) {
        return mappings.containsKey(oldKey);
    }

    public boolean containsOldTemp(String oldKey) {
        return temp.containsKey(oldKey);
    }

    public boolean containsNew(String key) {
        return newToOld.containsKey(key);
    }

    public Map<String, Mapping> getMappings() {
        return mappings;
    }

    public Map<String, Mapping> tempMappings() {
        return temp;
    }

    public void clearTemp() {
        temp.clear();
    }
}
