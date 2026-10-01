package uk.co.fireburn.lochssh.data

import kotlinx.coroutines.CancellationException

internal object SecretRemoval {
    fun remove(references: List<String>, delete: (String) -> Unit): List<String> =
        references.distinct().filter {
            try {
                delete(it)
                false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                true
            }
        }
}
