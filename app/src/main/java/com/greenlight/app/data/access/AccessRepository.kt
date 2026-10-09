package com.greenlight.app.data.access

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * Beta access. [check] asks the server to record this user's status (and to file a request for
 * approval when the admin requires one). The result lands on /users/{uid}.access, which the
 * session already observes, so approval shows up live.
 */
class AccessRepository(private val functions: FirebaseFunctions) {
    suspend fun check(): String? =
        (functions.getHttpsCallable("checkAccess").call().await().getData() as? Map<*, *>)?.get("status") as? String
}
