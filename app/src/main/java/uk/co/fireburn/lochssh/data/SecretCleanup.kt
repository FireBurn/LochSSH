package uk.co.fireburn.lochssh.data

import kotlinx.coroutines.CancellationException

internal object SecretCleanup {
    suspend fun retry(
        references: List<String>,
        isUsed: suspend (String) -> Boolean,
        deleteSecret: (String) -> Unit,
        removeEntry: suspend (String) -> Unit
    ): List<String> {
        val remaining = mutableListOf<String>()
        for (reference in references.distinct()) {
            try {
                if (!isUsed(reference)) deleteSecret(reference)
                removeEntry(reference)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                remaining += reference
            }
        }
        return remaining
    }
}
