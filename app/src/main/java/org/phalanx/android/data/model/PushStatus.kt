package org.phalanx.android.data.model

/** A UnifiedPush distributor app installed on the phone. */
data class PushDistributor(val packageName: String, val label: String)

/** Whether pushes reach this account, and if not, what the user can do about it. */
sealed interface PushStatus {
    /** Signed out, or not tried yet. */
    data object Off : PushStatus

    /** No distributor app is installed, so nothing can wake the app for new messages. */
    data object NoDistributor : PushStatus

    /** Several are installed and none is the phone's default: the user picks one. */
    data object ChooseDistributor : PushStatus

    data object Registering : PushStatus

    data class Active(val distributor: PushDistributor, val gatewayUrl: String) : PushStatus

    data class Failed(val reason: PushFailure) : PushStatus
}

enum class PushFailure {
    /** The distributor could not reach its server. */
    Network,

    /** The distributor needs the user's attention (a sign-in, say) before it can register. */
    DistributorActionRequired,

    /** The homeserver refused the pusher, or could not be reached. */
    Homeserver,

    /** The distributor failed for its own reasons. */
    Distributor
}
