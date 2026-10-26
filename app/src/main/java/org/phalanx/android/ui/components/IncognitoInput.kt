package org.phalanx.android.ui.components

import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/** The incognito keyboard setting, as the app's root read it. */
val LocalIncognitoKeyboard = compositionLocalOf { false }

/**
 * With the incognito keyboard setting on, asks the keyboard of every text field in [content] not
 * to learn from what is typed: no personalised dictionary, history or suggestions built from it.
 * Keyboards are asked, not forced; the common ones (Gboard, HeliBoard, FUTO, SwiftKey) honour it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun IncognitoInput(content: @Composable () -> Unit) {
    if (!LocalIncognitoKeyboard.current) {
        content()
        return
    }
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            val incognito = PlatformTextInputMethodRequest { outAttributes ->
                request.createInputConnection(outAttributes).also {
                    outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                }
            }
            nextHandler.startInputMethod(incognito)
        },
        content = content
    )
}
