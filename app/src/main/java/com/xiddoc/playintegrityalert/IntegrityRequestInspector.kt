package com.xiddoc.playintegrityalert

import android.os.Bundle
import java.util.regex.Pattern

/**
 * Pure inspection of a hooked Finsky integrity call's arguments. Decides whether
 * the call is an outgoing *request* (rather than a response that carries a
 * verdict) and, if so, which app made it.
 *
 * Deliberately free of any Xposed dependency so the security-critical heuristics
 * — request/response discrimination and caller extraction — can be unit-tested
 * in full on the JVM.
 */
object IntegrityRequestInspector {

    private val packageNamePattern =
        Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z0-9_]+)+")

    /**
     * The requesting app's package if [args] look like an integrity *request* and a
     * caller package can be recovered from them, otherwise null.
     */
    fun callerPackage(args: Array<Any?>?): String? {
        if (args == null || !looksLikeRequest(args)) return null
        return extractCallerPackage(args)
    }

    /** A request carries a caller package and/or a nonce; a response carries token/error. */
    internal fun looksLikeRequest(args: Array<Any?>): Boolean {
        args.forEach { arg ->
            val bundle = arg as? Bundle ?: return@forEach
            if (bundle.containsKey("token") || bundle.containsKey("error")) return@forEach
            val hasPkg = Constants.CALLER_PACKAGE_KEYS.any { bundle.containsKey(it) }
            val hasNonce = runCatching {
                bundle.keySet().any { it.contains("nonce", ignoreCase = true) }
            }.getOrDefault(false)
            if (hasPkg || hasNonce) return true
        }
        return false
    }

    internal fun extractCallerPackage(args: Array<Any?>): String? {
        args.forEach { arg ->
            val bundle = arg as? Bundle ?: return@forEach
            Constants.CALLER_PACKAGE_KEYS.forEach { key ->
                bundle.getString(key)?.takeIf { it.isNotBlank() }?.let { return it }
            }
            // Fallback: any package-shaped string value in the request Bundle.
            runCatching {
                bundle.keySet().forEach { key ->
                    val value = bundle.getString(key) ?: return@forEach
                    if (packageNamePattern.matcher(value).matches()) return value
                }
            }
        }
        return null
    }
}
