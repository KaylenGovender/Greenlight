package com.greenlight.app.data.account

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/** Self-service privacy tools (functions/src/account.ts). */
class AccountRepository(private val functions: FirebaseFunctions) {

    /** Everything Greenlight stores about me, as pretty-printed JSON. */
    suspend fun exportMyData(): String {
        val data = functions.getHttpsCallable("exportMyData").call().await().getData() as? Map<*, *> ?: emptyMap<String, Any>()
        return JSONObject(data).toString(2)
    }

    /** Permanently deletes my account and data. Sign out afterwards. */
    suspend fun deleteMyAccount() {
        functions.getHttpsCallable("deleteMyAccount").call(mapOf("confirm" to DELETE_PHRASE)).await()
    }

    companion object {
        /** Must match DELETE_ACCOUNT_PHRASE in functions/src/account.ts. */
        const val DELETE_PHRASE = "DELETE"
    }
}
