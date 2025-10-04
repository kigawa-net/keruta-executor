package net.kigawa.keruta.executor.domain

import kotlin.time.Duration

interface Config {
    val apiToken: ApiToken
    val timeout: Duration
}
