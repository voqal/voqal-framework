package com.intellij.diff.fragments

interface LineFragment : DiffFragment {
    val startLine1: Int
    val endLine1: Int
    val startLine2: Int
    val endLine2: Int
    val innerFragments: List<DiffFragment>?
}