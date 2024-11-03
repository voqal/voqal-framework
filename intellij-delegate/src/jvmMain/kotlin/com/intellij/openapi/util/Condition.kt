package com.intellij.openapi.util

import java.util.function.Predicate

interface Condition<T> : Predicate<T> {

}