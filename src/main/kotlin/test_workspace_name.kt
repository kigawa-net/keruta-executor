fun main() {
    val sessionId = "0fcfba18-b832-4008-97c9-21c67d92ac74"
    val sessionName = "session-0fcfba1"
    
    // Old naming (causing the error)
    val oldName = "session-${sessionId.take(8)}-${sessionName.replace("[^a-zA-Z0-9-_]".toRegex(), "-")}-${System.currentTimeMillis().toString().takeLast(6)}"
    println("Old naming: $oldName (${oldName.length} chars)")
    
    // New naming (should be Coder-compliant)
    val prefix = "ws"
    val sessionIdShort = sessionId.take(8).lowercase()
    val sanitizedName = sessionName.lowercase().replace("[^a-z0-9]".toRegex(), "").take(10).ifEmpty { "session" }
    val timestamp = (System.currentTimeMillis() % 10000).toString().padStart(4, '0')
    val newName = "$prefix-$sessionIdShort-$sanitizedName-$timestamp"
    
    println("New naming: $newName (${newName.length} chars)")
    println("Validation: starts with letter? ${newName.first().isLetter()}, only valid chars? ${newName.matches(Regex("[a-z0-9-_]+"))}, length ok? ${newName.length in 1..32}")
}
