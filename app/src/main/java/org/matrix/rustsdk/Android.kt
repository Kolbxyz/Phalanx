package org.matrix.rustsdk

import android.content.Context

/**
 * Hands the Rust SDK an Android [Context], which its TLS verifier needs to reach the system trust
 * store. The package and names are fixed by the JNI symbol the native library exports
 * (`Java_org_matrix_rustsdk_Android_init`), so this file cannot move.
 */
object Android {
    init {
        System.loadLibrary("matrix_sdk_ffi")
    }

    @JvmStatic
    external fun init(context: Context)
}
