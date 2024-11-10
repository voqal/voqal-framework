package dev.voqal.core

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

    override val startLine1: Int
        get() = TODO("Not yet implemented")
    override val endLine1: Int
        get() = TODO("Not yet implemented")
    override val startLine2: Int
        get() = TODO("Not yet implemented")
    override val endLine2: Int
        get() = TODO("Not yet implemented")
    override val innerFragments: List<DiffFragment>
        get() = TODO("Not yet implemented")
    override val startOffset1: Int
        get() = TODO("Not yet implemented")
    override val endOffset1: Int
        get() = TODO("Not yet implemented")
    override val startOffset2: Int
        get() = TODO("Not yet implemented")
    override val endOffset2: Int
        get() = TODO("Not yet implemented")
}