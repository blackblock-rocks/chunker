package rocks.blackblock.chunker.utils;

import java.util.LinkedHashMap;

/**
 * A simple LRU cache
 *
 * @author   Jelle De Loecker   <jelle@elevenways.be>
 * @since    0.2.0
 */
public class LRUCache<K, V> extends LinkedHashMap<K, V> {

    private final int cache_size;

    public LRUCache(int cache_size) {
        super(16, 0.75f, true);
        this.cache_size = cache_size;
    }

    @Override
    protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
        return size() > cache_size;
    }
}
