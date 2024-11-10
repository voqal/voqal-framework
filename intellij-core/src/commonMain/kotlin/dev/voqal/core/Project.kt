package dev.voqal.core

interface Project {
    val name: String
    val isDisposed: Boolean
    val basePath: String
}