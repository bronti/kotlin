/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.debugger

fun preprocess(out: String): String {
    val lines = out.lines().toMutableList()

    val connectedIndex = lines.indexOfFirst { it.startsWith("Connected to the target VM") }
    lines[connectedIndex] = "Connected to the target VM ~"

    val runCommandIndex = connectedIndex - 1
    lines[runCommandIndex] = "Run Java ~"

    val disconnectedIndex = lines.indexOfFirst { it.startsWith("Disconnected from the target VM") }
    lines[disconnectedIndex] = "Disconnected from the target VM ~"

    return lines.joinToString("\n")
}