package com.intellij.diff.fragments;

interface DiffFragment {
    val startOffset1: Int
    val endOffset1: Int
    val startOffset2: Int
    val endOffset2: Int
}
