package org.phalanx.android.core.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import org.phalanx.android.di.ApplicationScope

/** The language model, about 2 GB of weights, kept loaded between requests (see [LoadedNative]). */
@Singleton
internal class LoadedModel @Inject constructor(
    models: ModelStore,
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope
) : LoadedNative(
    models.installed,
    context,
    scope,
    load = { LlamaNative.load(it, CpuCores.replyThreads, CpuCores.promptThreads) },
    free = LlamaNative::free
)
