package ru.ifmo.mpp.hashmap;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe Int-to-Int hash map with open addressing and linear probes.
 *
 * @author Курбонзода.
 */
public class IntIntHashMap {
    private static final int MAGIC = 0x9E3779B9; // golden ratio
    private static final int INITIAL_CAPACITY = 2; // !!! DO NOT CHANGE INITIAL CAPACITY !!!
    private static final int MAX_PROBES = 8; // max number of probes to find an item

    private static final int NULL_KEY = 0; // missing key (initial value)
    private static final int NULL_VALUE = 0; // missing value (initial value)
    private static final int DEL_VALUE = Integer.MAX_VALUE; // mark for removed value
    private static final int NEEDS_REHASH = -1; // returned by putInternal to indicate that rehash is needed

    // Checks if the value is in the range of allowed values
    private static boolean isValue(int value) {
        return value > 0 && value < DEL_VALUE; // the range of allowed values
    }

    // Converts internal value to the public results of the methods
    private static int toValue(int value) {
        assert value >= 0 : "The value should be positive";
        return isValue(value) ? value : 0;
    }

    private AtomicReference<Core> core = new AtomicReference<>(new Core(INITIAL_CAPACITY));

    /**
     * Returns value for the corresponding key or zero if this key is not present.
     * @param key a positive key.
     * @return value for the corresponding or zero if this key is not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    public int get(int key) {
        if (key <= 0) throw new IllegalArgumentException("Key must be positive: " + key);
        return toValue(getAndRehashWhileNeeded(key));
    }

    /**
     * Changes value for the corresponding key and returns old value or zero if key was not present.
     * @param key a positive key.
     * @param value a positive value.
     * @return old value or zero if this key was not present.
     * @throws IllegalArgumentException if key or value are not positive, or value is equal to
     *    {@link Integer#MAX_VALUE} which is reserved.
     */
    public int put(int key, int value) {
        if (key <= 0) throw new IllegalArgumentException("Key must be positive: " + key);
        if (!isValue(value)) throw new IllegalArgumentException("Invalid value: " + value);
        return toValue(putAndRehashWhileNeeded(key, value));
    }

    /**
     * Removes value for the corresponding key and returns old value or zero if key was not present.
     * @param key a positive key.
     * @return old value or zero if this key was not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    public int remove(int key) {
        if (key <= 0) throw new IllegalArgumentException("Key must be positive: " + key);
        return toValue(putAndRehashWhileNeeded(key, DEL_VALUE));
    }

    private int getAndRehashWhileNeeded(int key) {
        while (true) {
            Core currentCore = core.get();
            int value = currentCore.getInternal(key);
            if (value != NEEDS_REHASH)
                return value;

            assert currentCore.next.get() != null : "Someone must be moving elements";

            if (core.get() == currentCore) {
                currentCore.rehash();
                core.compareAndSet(currentCore, currentCore.next.get());
            }
        }
    }

    private int putAndRehashWhileNeeded(int key, int value) {
        while (true) {
            Core currentCore = core.get();
            int oldValue = currentCore.putInternal(key, value);
            if (oldValue != NEEDS_REHASH)
                return oldValue;

            if (core.get() == currentCore) {
                currentCore.rehash();
                core.compareAndSet(currentCore, currentCore.next.get());
            }
        }
    }

    private static class Core {
        final AtomicIntegerArray map; // pairs of key, value here
        final AtomicReference<Core> next;
        final int shift;
        final static int TAG_MOVED_VALUE = Integer.MIN_VALUE; // for tagging values that are already moved

        /**
         * Creates new core with a given capacity for (key, value) pair.
         * The actual size of the map is twice as big.
         */
        Core(int capacity) {
            map = new AtomicIntegerArray(2 * capacity);
            next = new AtomicReference<>(null);
            int mask = capacity - 1;
            assert mask > 0 && (mask & capacity) == 0 : "Capacity must be power of 2: " + capacity;
            shift = 32 - Integer.bitCount(mask);
        }

        int getInternal(int key) {
            for (int probes = 0, index = index(key); probes < MAX_PROBES; ++probes, index = nextIndex(index)) {
                int aValue = map.get(index + 1);
                int aKey = map.get(index);

                assert satisfiesConditionsOfGetValueFirst(aKey, aValue);

                if (isMoved(aValue))
                    return NEEDS_REHASH;
                if (aKey == key || aKey == NULL_KEY)
                    return untaggedOf(aValue);
            }

            return NULL_VALUE;
        }

        int putInternal(int key, int value) {
            for (int probes = 0, index = index(key); probes < MAX_PROBES; ) {
                int aValue = map.get(index + 1);
                int aKey = map.get(index);

                assert satisfiesConditionsOfGetValueFirst(aKey, aValue);

                if (isTagged(aValue))
                    return NEEDS_REHASH;

                if (aKey == NULL_KEY) {
                    if (value == DEL_VALUE)
                        return NULL_VALUE;

                    if (map.compareAndSet(index, aKey, key))
                        if(map.compareAndSet(index + 1, aValue, value))
                            return aValue;
                    continue;
                }

                if (aKey == key) {
                    if (map.compareAndSet(index + 1, aValue, value))
                        return aValue;
                    continue;
                }

                ++probes;
                index = nextIndex(index);
            }
            return NEEDS_REHASH;
        }

        void rehash() {
            next.compareAndSet(null, new Core(map.length())); // map.length is twice the current capacity

            for (int index = 0; index < map.length(); index += 2) {
                int aValue = map.get(index + 1);
                int aKey = map.get(index);

                assert satisfiesConditionsOfGetValueFirst(aKey, aValue);

                if (isMoved(aValue))
                    continue;

                if (!isTagged(aValue))
                    if (!map.compareAndSet(index + 1, aValue, taggedOf(aValue))) {
                        index -= 2;
                        continue;
                    }

                if (isValue(untaggedOf(aValue))) {
                    next.get().moveKeyValue(aKey, untaggedOf(aValue));
                    map.set(index + 1, TAG_MOVED_VALUE);
                }
            }
        }

        private void moveKeyValue(int key, int value) {
            assert key > 0 && isValue(value);

            for (int probes = 0, index = index(key); probes < MAX_PROBES; ) {
                int aValue = map.get(index + 1);
                int aKey = map.get(index);

                assert satisfiesConditionsOfGetValueFirst(aKey, aValue);

                if (aKey == NULL_KEY) {
                    if (map.compareAndSet(index, aKey, key)) {
                        map.compareAndSet(index + 1, NULL_VALUE, value);
                        return;
                    }
                    continue;
                }

                if (aKey == key) {
                    map.compareAndSet(index + 1, NULL_VALUE, value);
                    return;
                }

                ++probes;
                index = nextIndex(index);
            }
            throw new AssertionError("Couldn't put (" + key + ", " + value + ") pair during rehash");
        }

        /**
         * Returns true if the given (key, value) pair is a valid data if we access value before key in map.
         * Returns false otherwise.
         */
        private boolean satisfiesConditionsOfGetValueFirst(int key, int value) {
            return (key == 0 && value == 0)
                    || (key == 0 && isMoved(value))
                    || (key > 0 && value == 0)
                    || (key > 0 && isValue(value))
                    || (key > 0 && value == DEL_VALUE)
                    || (key > 0 && isTagged(value))
                    || (key > 0 && isMoved(value));
        }

        /**
         * Returns an initial index in map to look for a given key.
         */
        int index(int key) {
            return ((key * MAGIC) >>> shift) * 2;
        }

        /**
         * Returns the next index in map to look after given index.
         */
        int nextIndex(int index) {
            if (index == 0)
                index = map.length();
            return index - 2;
        }

        /**
         * Returns true if the given value if moved. False otherwise.
         */
        boolean isMoved(int value) {
            return value == TAG_MOVED_VALUE;
        }

        /**
         * Returns true if the given value is tagged as being moved or already moved.
         */
        boolean isTagged(int value) {
            return (value & (1 << 31)) != 0;
        }

        /**
         * Returns a value which is derived by tagging the given value if it is not already tagged.
         * If the given value is already tagged the given value is returned.
         */
        int taggedOf(int value) {
            if (value == DEL_VALUE)
                return TAG_MOVED_VALUE;
            return value | (1 << 31);
        }

        /**
         * Returns the value which was derived to the given value by tagging.
         * If the given value is not tagged the given value is returned.
         */
        int untaggedOf(int value) {
            return value & (~(1 << 31));
        }
    }
}
