package uk.co.fireburn.lochssh.data

import kotlinx.coroutines.CancellationException
import uk.co.fireburn.lochssh.data.db.SavedCommandDao
import uk.co.fireburn.lochssh.data.db.SavedCommandEntity

class SavedCommandStore(private val dao: SavedCommandDao) {
    suspend fun save(id: Long, name: String, command: String): String? {
        if (name.isBlank() || command.isBlank() || command.any { it == '\n' || it == '\r' }) {
            return "Enter a name and a single-line command"
        }
        return attempt("save") {
            val saved = SavedCommandEntity(id, name.trim(), command.trim())
            if (id == 0L) dao.insert(saved) else dao.update(saved)
        }
    }

    suspend fun delete(command: SavedCommandEntity): String? = attempt("delete") { dao.delete(command) }

    private suspend fun attempt(action: String, block: suspend () -> Unit): String? = try {
        block()
        null
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        "Could not $action command: ${error.message ?: "storage error"}"
    }
}
