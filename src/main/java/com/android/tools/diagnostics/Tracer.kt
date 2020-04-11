package com.android.tools.diagnostics

// Things to improve:
// - GC the state for dead threads.

/** Builds and manages the call tree for each active thread. */
object Tracer {

    // Synchronized by monitor lock.
    private class ThreadState {
        var callTreeBuilder: CallTreeBuilder = CallTreeBuilder(Thread.currentThread())
    }

    private val threadState: ThreadLocal<ThreadState> = ThreadLocal.withInitial {
        ThreadState().also {
            synchronized(allThreadState) {
                allThreadState.add(it)
            }
        }
    }

    // Synchronized by monitor lock.
    private val allThreadState = mutableListOf<ThreadState>()

    fun enter(tracepoint: Tracepoint) {
        val state = threadState.get()
        synchronized(state) {
            state.callTreeBuilder.push(tracepoint)
        }
    }

    fun leave(tracepoint: Tracepoint) {
        val state = threadState.get()
        synchronized(state) {
            state.callTreeBuilder.pop(tracepoint)
        }
    }

    /** Collect and reset the call trees from all threads. */
    fun swapBuffers(): List<CallTree> {
        val allState = synchronized(allThreadState) { ArrayList(allThreadState) }
        val oldTrees = ArrayList<CallTree>(allState.size)
        for (state in allState) {
            val tree = synchronized(state) { state.callTreeBuilder.buildAndReset() }
            oldTrees.add(tree)
        }
        return oldTrees
    }
}