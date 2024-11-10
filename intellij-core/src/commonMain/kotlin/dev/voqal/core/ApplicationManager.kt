package dev.voqal.core

interface ApplicationManager {
    companion object {
        fun getApplication(): Application {
            return object : Application {
                override val isUnitTestMode: Boolean
                    get() = false
            }
        }
    }
}