package dev.voqal.core

interface DiffContentFactory {
    fun create(oldText: String): DocumentContent

    companion object {
        fun getInstance(): DiffContentFactory {
            return object : DiffContentFactory {
                override fun create(oldText: String): DocumentContent {
                    TODO("Not yet implemented")
                }
            }
        }
    }

}