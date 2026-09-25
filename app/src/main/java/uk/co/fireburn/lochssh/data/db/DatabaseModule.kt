package uk.co.fireburn.lochssh.data.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LochSshDatabase =
        Room.databaseBuilder(context, LochSshDatabase::class.java, LochSshDatabase.NAME)
            .addMigrations(
                LochSshDatabase.MIGRATION_1_2,
                LochSshDatabase.MIGRATION_2_3,
                LochSshDatabase.MIGRATION_3_4
            )
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    fun provideSshHostDao(db: LochSshDatabase): SshHostDao = db.sshHostDao()

    @Provides
    fun provideIdentityDao(db: LochSshDatabase): IdentityDao = db.identityDao()

    @Provides
    fun providePortForwardDao(db: LochSshDatabase): PortForwardDao = db.portForwardDao()
}
