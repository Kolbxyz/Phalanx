package org.phalanx.android.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.phalanx.android.core.matrix.RustAccountRepository
import org.phalanx.android.core.matrix.RustAuthRepository
import org.phalanx.android.core.matrix.RustBlockedUsersRepository
import org.phalanx.android.core.matrix.RustMediaRepository
import org.phalanx.android.core.matrix.RustRoomActionsRepository
import org.phalanx.android.core.matrix.RustRoomGalleryRepository
import org.phalanx.android.core.matrix.RustRoomListRepository
import org.phalanx.android.core.matrix.RustTimelineRepository
import org.phalanx.android.data.repository.AccountRepository
import org.phalanx.android.data.repository.AuthRepository
import org.phalanx.android.data.repository.BlockedUsersRepository
import org.phalanx.android.data.repository.MediaRepository
import org.phalanx.android.data.repository.RoomActionsRepository
import org.phalanx.android.data.repository.RoomGalleryRepository
import org.phalanx.android.data.repository.RoomListRepository
import org.phalanx.android.data.repository.TimelineRepository

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {
    @Binds
    abstract fun authRepository(impl: RustAuthRepository): AuthRepository

    @Binds
    abstract fun roomListRepository(impl: RustRoomListRepository): RoomListRepository

    @Binds
    abstract fun timelineRepository(impl: RustTimelineRepository): TimelineRepository

    @Binds
    abstract fun mediaRepository(impl: RustMediaRepository): MediaRepository

    @Binds
    abstract fun blockedUsersRepository(impl: RustBlockedUsersRepository): BlockedUsersRepository

    @Binds
    abstract fun roomActionsRepository(impl: RustRoomActionsRepository): RoomActionsRepository

    @Binds
    abstract fun roomGalleryRepository(impl: RustRoomGalleryRepository): RoomGalleryRepository

    @Binds
    abstract fun accountRepository(impl: RustAccountRepository): AccountRepository
}
