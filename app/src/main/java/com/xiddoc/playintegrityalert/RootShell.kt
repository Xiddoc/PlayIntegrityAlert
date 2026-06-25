package com.xiddoc.playintegrityalert

import com.topjohnwu.superuser.Shell

/**
 * Thin abstraction over root command execution. Pulled out behind an interface so
 * the restart logic in [MainActivity] can be unit-tested with a fake, without ever
 * spawning a real shell. The default implementation is [LibsuRootShell].
 */
interface RootShell {
    /** True if a root (su) shell is actually available on this device. */
    fun isAvailable(): Boolean

    /** Runs [commands] in a root shell and returns true if they all succeeded. */
    fun exec(vararg commands: String): Boolean
}

/**
 * Default [RootShell] backed by [libsu](https://github.com/topjohnwu/libsu). Kept
 * deliberately branch-free so it stays trivially coverable: all decision-making
 * lives in [MainActivity] against the [RootShell] interface.
 */
object LibsuRootShell : RootShell {
    override fun isAvailable(): Boolean = Shell.getShell().isRoot

    override fun exec(vararg commands: String): Boolean =
        Shell.cmd(*commands).exec().isSuccess
}
