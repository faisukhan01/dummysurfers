package com.dummysurfers.core.utils

/**
 * Generic fixed-size object pool. Entities implement [Poolable] and are
 * recycled to avoid GC pressure in the game loop (60 FPS target).
 */
class ObjectPool<T : Any>(private val factory: () -> T, private val capacity: Int) {
    private val items = ArrayList<T>(capacity)
    private var activeCount = 0

    val size: Int get() = activeCount

    fun obtain(): T? {
        if (activeCount >= capacity) return null
        return if (activeCount < items.size) items[activeCount++] else {
            val t = factory()
            items.add(t)
            activeCount++
            t
        }
    }

    /** Access item i of the active range. */
    operator fun get(i: Int) = items[i]

    /** Swap-remove: item at [index] gets [recycle]d, last active fills its slot. */
    fun freeAt(index: Int, recycle: (T) -> Unit) {
        val last = activeCount - 1
        if (index < 0 || index > last) return
        val removed = items[index]
        recycle(removed)
        items[index] = items[last]
        items[last] = removed
        activeCount = last
    }

    fun freeAll(recycle: (T) -> Unit) {
        for (i in 0 until activeCount) recycle(items[i])
        activeCount = 0
    }

    fun clear() {
        activeCount = 0
    }
}
