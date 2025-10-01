package net.kigawa.keruta.executor.action

import net.kigawa.keruta.executor.domain.ApiConnection

interface ApiConnectAction {
    suspend fun connect(): ApiConnection
}
