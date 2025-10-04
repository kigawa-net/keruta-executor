package net.kigawa.keruta.executor.domain

class ApiToken(
    val strToken: String
) {
    init {
        require(strToken.isNotEmpty()) {"token is empty"}
    }
}
