package uk.co.fireburn.lochssh.ui.identities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
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
            identity.secretRef?.let { secrets.deleteSecret(it) }
            identityDao.delete(identity)
        }
    }
}
