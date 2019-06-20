// !USE_EXPERIMENTAL: kotlin.Experimental
// !DIAGNOSTICS: -UNUSED_PARAMETER -UNUSED_VARIABLE
// !WITH_NEW_INFERENCE

// FILE: a.kt

package a

private class Queue {
    private companion object
}

// FILE: b/Queue.java

package b;

public class Queue {
    public static Queue empty() { return null; }
}

// FILE: c.kt

@file:UseExperimental(ExperimentalTypeInference::class)

package c

import a.*
import b.Queue
import kotlin.experimental.ExperimentalTypeInference

interface Inv<T> {
    fun emit(e: T)
}

fun <T> invBuilder(@BuilderInference block: Inv<T>.() -> Unit) {}

fun test() {
    invBuilder {
        val q = <!NI;INVISIBLE_MEMBER!>Queue<!>.empty()
        emit(42)
    }
}

