package com.intellij.diff.fragments

class LineFragmentImpl : LineFragment {
    constructor(
        startLine1: Int,
        endLine1: Int,
        startLine2: Int,
        endLine2: Int,
        startOffset1: Int,
        endOffset1: Int,
        startOffset2: Int,
        endOffset2: Int
    ) {
    }


    override val startOffset1: Int = 0
    override val startOffset2: Int = 0
    override val endOffset1: Int = 0
    override val endOffset2: Int = 0
    override val startLine1: Int = 0
    override val endLine1: Int = 0
    override val startLine2: Int = 0
    override val endLine2: Int = 0
    override val innerFragments: List<DiffFragment> = emptyList()
}