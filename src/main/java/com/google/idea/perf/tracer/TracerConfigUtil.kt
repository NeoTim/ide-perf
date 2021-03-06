/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.perf.tracer

import com.google.idea.perf.AgentLoader
import com.google.idea.perf.util.GlobMatcher
import org.objectweb.asm.Type
import java.lang.reflect.Method

class TraceRequest(
    val matcher: MethodFqMatcher,
    val config: MethodConfig,
)

data class MethodFqName(
    val clazz: String, // Fully qualified class name or glob pattern.
    val method: String,
    val desc: String, // See Type.getMethodDescriptor in the ASM library.
)

class MethodConfig(
    val enabled: Boolean = true, // Whether the method should be traced.
    val countOnly: Boolean = false, // Whether to skip measuring wall time.
    val tracedParams: List<Int> = emptyList(), // Support for parameter tracing.
)

class MethodTraceData(
    val methodId: Int,
    val config: MethodConfig,
)

class MethodFqMatcher(methodPattern: MethodFqName) {
    private val classMatcher = GlobMatcher.create(methodPattern.clazz)
    private val methodMatcher = GlobMatcher.create(methodPattern.method)
    private val descMatcher = GlobMatcher.create(methodPattern.desc)

    fun matches(m: MethodFqName): Boolean {
        return classMatcher.matches(m.clazz) &&
                methodMatcher.matches(m.method) &&
                descMatcher.matches(m.desc)
    }

    fun mightMatchMethodInClass(className: String): Boolean = classMatcher.matches(className)
}

object TracerConfigUtil {

    fun appendTraceRequest(methodPattern: MethodFqName, methodConfig: MethodConfig): TraceRequest {
        val matcher = MethodFqMatcher(methodPattern)
        val request = TraceRequest(matcher, methodConfig)
        TracerConfig.appendTraceRequest(request)
        return request
    }

    fun getAffectedClasses(traceRequests: Collection<TraceRequest>): List<Class<*>> {
        if (traceRequests.isEmpty()) return emptyList()
        val instrumentation = AgentLoader.instrumentation ?: return emptyList()

        // Currently O(n) in the number of trace requests---could be optimized if needed.
        fun classMightBeAffected(clazz: Class<*>) =
            traceRequests.any { it.matcher.mightMatchMethodInClass(clazz.name) }

        return instrumentation.allLoadedClasses.filter(::classMightBeAffected)
    }

    fun createMethodFqName(method: Method): MethodFqName {
        return MethodFqName(
            clazz = method.declaringClass.name,
            method = method.name,
            desc = Type.getMethodDescriptor(method)
        )
    }
}
