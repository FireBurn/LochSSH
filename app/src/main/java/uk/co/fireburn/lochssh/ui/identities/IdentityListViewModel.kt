package uk.co.fireburn.lochssh.ui.identities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.fireburn.lochssh.data.EncryptedStorageManager
import uk.co.fireburn.lochssh.data.db.IdentityDao
import uk.co.fireburn.lochssh.data.db.IdentityEntity
import javax.inject.Inject

@HiltViewModel
class IdentityListViewModel @Inject constructor(
    private val identityDao: IdentityDao,
    private val secrets: EncryptedStorageManager
) : ViewModel() {

    val identities: Flow<List<IdentityEntity>> = identityDao.observeAll()

    fun delete(identity: IdentityEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO + NonCancellable) {
                identityDao.delete(identity)
                listOfNotNull(identity.secretRef, identity.keyMaterialRef)
                    .forEach { runCatching { secrets.deleteSecret(it) } }
            }
        }
    }
}
