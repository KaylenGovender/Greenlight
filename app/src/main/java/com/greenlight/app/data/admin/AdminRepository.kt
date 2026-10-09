package com.greenlight.app.data.admin

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * Only used to decide whether to *show* admin menu items. Every admin action is checked again on
 * the server (functions/src/config.ts → ADMIN_EMAILS), so editing this can't grant access.
 */
object AdminConfig {
    val ADMIN_EMAILS = setOf("kaylengovender@gmail.com")
}

data class AdminUserRow(
    val uid: String,
    val email: String?,
    val displayName: String,
    val photoUrl: String?,
    val groupCount: Int,
    val createdAt: Long?,
    val lastSignInAt: Long?,
    val disabled: Boolean,
    val providers: List<String>,
    val isAdmin: Boolean,
)

data class SwipeCount(val interested: Int, val passed: Int)

data class AdminUserGroup(
    val id: String,
    val type: String,
    val name: String?,
    val status: String,
    val memberCount: Int,
    val swipes: Map<String, SwipeCount>,
)

data class AdminUserDetail(
    val uid: String,
    val email: String?,
    val displayName: String,
    val photoUrl: String?,
    val onboarded: Boolean,
    val notificationsEnabled: Boolean,
    val disabled: Boolean,
    val providers: List<String>,
    val createdAt: Long?,
    val lastSignInAt: Long?,
    val deviceCount: Int,
    val groups: List<AdminUserGroup>,
)

data class AdminGroupRow(
    val id: String,
    val type: String,
    val name: String?,
    val status: String,
    val code: String?,
    val memberCount: Int,
    val memberNames: List<String>,
    val createdAt: Long?,
) {
    val title get() = name ?: if (type == "couple") memberNames.joinToString(" & ").ifBlank { "Couple" } else "Group"
}

data class AdminMember(val uid: String, val displayName: String, val email: String?, val photoUrl: String?)
data class AdminMatch(val kind: String, val id: String, val title: String, val interestedCount: Int, val memberCount: Int, val matchedAt: Long?)
data class AdminPlan(val id: String, val title: String, val startAt: Long?, val going: Int)

data class AdminGroupDetail(
    val id: String,
    val type: String,
    val name: String?,
    val status: String,
    val code: String?,
    val ownerId: String?,
    val members: List<AdminMember>,
    val matches: List<AdminMatch>,
    val plans: List<AdminPlan>,
) {
    val isCouple get() = type == "couple"
    val title get() = name ?: if (isCouple) members.joinToString(" & ") { it.displayName }.ifBlank { "Couple" } else "Group"
}

/** Admin-only callables (functions/src/admin.ts). The server refuses everyone but admins. */
class AdminRepository(private val functions: FirebaseFunctions) {

    private suspend fun call(name: String, data: Map<String, Any?> = emptyMap()): Map<*, *> =
        functions.getHttpsCallable(name).call(data).await().getData() as? Map<*, *> ?: emptyMap<String, Any>()

    suspend fun listUsers(query: String = ""): List<AdminUserRow> =
        (call("adminListUsers", mapOf("query" to query))["users"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.toUserRow() }

    suspend fun getUser(uid: String): AdminUserDetail {
        val r = call("adminGetUser", mapOf("uid" to uid))
        return AdminUserDetail(
            uid = r.s("uid") ?: uid,
            email = r.s("email"),
            displayName = r.s("displayName").orEmpty(),
            photoUrl = r.s("photoUrl"),
            onboarded = r["onboarded"] as? Boolean ?: false,
            notificationsEnabled = ((r["notifications"] as? Map<*, *>)?.get("enabled") as? Boolean) ?: true,
            disabled = r["disabled"] as? Boolean ?: false,
            providers = r.strings("providers"),
            createdAt = r.l("createdAt"),
            lastSignInAt = r.l("lastSignInAt"),
            deviceCount = r.l("deviceCount")?.toInt() ?: 0,
            groups = (r["groups"] as? List<*>).orEmpty().mapNotNull { g ->
                (g as? Map<*, *>)?.let {
                    AdminUserGroup(
                        id = it.s("id").orEmpty(),
                        type = it.s("type") ?: "couple",
                        name = it.s("name"),
                        status = it.s("status") ?: "",
                        memberCount = it.l("memberCount")?.toInt() ?: 0,
                        swipes = (it["swipes"] as? Map<*, *>).orEmpty().entries.associate { (k, v) ->
                            val m = v as? Map<*, *>
                            k.toString() to SwipeCount(m?.l("interested")?.toInt() ?: 0, m?.l("passed")?.toInt() ?: 0)
                        },
                    )
                }
            },
        )
    }

    suspend fun createUser(displayName: String, email: String, password: String): String =
        call("adminCreateUser", mapOf("displayName" to displayName, "email" to email, "password" to password)).s("uid").orEmpty()

    suspend fun updateUser(uid: String, displayName: String? = null, onboarded: Boolean? = null, disabled: Boolean? = null, notificationsEnabled: Boolean? = null) {
        val data = mutableMapOf<String, Any?>("uid" to uid)
        displayName?.let { data["displayName"] = it }
        onboarded?.let { data["onboarded"] = it }
        disabled?.let { data["disabled"] = it }
        notificationsEnabled?.let {
            data["notifications"] = mapOf("enabled" to it, "matches" to true, "partnerActivity" to true, "dateNightReminders" to true, "swipeReminders" to true)
        }
        call("adminUpdateUser", data)
    }

    suspend fun deleteUser(uid: String) { call("adminDeleteUser", mapOf("uid" to uid)) }

    suspend fun listGroups(query: String = ""): List<AdminGroupRow> =
        (call("adminListGroups", mapOf("query" to query))["groups"] as? List<*>).orEmpty().mapNotNull { g ->
            (g as? Map<*, *>)?.let {
                AdminGroupRow(
                    id = it.s("id").orEmpty(), type = it.s("type") ?: "couple", name = it.s("name"), status = it.s("status") ?: "",
                    code = it.s("code"), memberCount = it.l("memberCount")?.toInt() ?: 0, memberNames = it.strings("memberNames"), createdAt = it.l("createdAt"),
                )
            }
        }

    suspend fun getGroup(groupId: String): AdminGroupDetail {
        val r = call("adminGetGroup", mapOf("groupId" to groupId))
        return AdminGroupDetail(
            id = r.s("id") ?: groupId,
            type = r.s("type") ?: "couple",
            name = r.s("name"),
            status = r.s("status") ?: "",
            code = r.s("code"),
            ownerId = r.s("ownerId"),
            members = (r["members"] as? List<*>).orEmpty().mapNotNull { m ->
                (m as? Map<*, *>)?.let { AdminMember(it.s("uid").orEmpty(), it.s("displayName").orEmpty(), it.s("email"), it.s("photoUrl")) }
            },
            matches = (r["matches"] as? List<*>).orEmpty().mapNotNull { m ->
                (m as? Map<*, *>)?.let {
                    AdminMatch(it.s("kind").orEmpty(), it.s("id").orEmpty(), it.s("title").orEmpty(), it.l("interestedCount")?.toInt() ?: 0, it.l("memberCount")?.toInt() ?: 0, it.l("matchedAt"))
                }
            },
            plans = (r["plans"] as? List<*>).orEmpty().mapNotNull { p ->
                (p as? Map<*, *>)?.let { AdminPlan(it.s("id").orEmpty(), it.s("title").orEmpty(), it.l("startAt"), it.l("going")?.toInt() ?: 0) }
            },
        )
    }

    suspend fun renameGroup(groupId: String, name: String) { call("adminUpdateGroup", mapOf("groupId" to groupId, "name" to name)) }
    suspend fun addMember(groupId: String, uid: String) { call("adminAddMember", mapOf("groupId" to groupId, "uid" to uid)) }
    suspend fun removeMember(groupId: String, uid: String) { call("adminRemoveMember", mapOf("groupId" to groupId, "uid" to uid)) }
    suspend fun resetCode(groupId: String): String = call("adminResetGroupCode", mapOf("groupId" to groupId)).s("code").orEmpty()
    suspend fun deleteGroup(groupId: String) { call("adminDeleteGroup", mapOf("groupId" to groupId)) }

    suspend fun deleteMatch(groupId: String, kind: String, id: String) =
        deleteRecord("groups/$groupId/${kind}Matches/$id")

    suspend fun deletePlan(groupId: String, id: String) = deleteRecord("groups/$groupId/plans/$id")

    private suspend fun deleteRecord(path: String) { call("adminDeleteRecord", mapOf("path" to path)) }

    /** Deletes every group, match, plan and code but keeps user accounts. Returns (groups, users). */
    suspend fun wipeData(): Pair<Int, Int> {
        val r = call("adminWipeData", mapOf("confirm" to WIPE_PHRASE))
        return (r.l("groups")?.toInt() ?: 0) to (r.l("users")?.toInt() ?: 0)
    }

    /** Start fresh AND delete every user account except mine. Returns (groups, users deleted). */
    suspend fun deleteEverything(): Pair<Int, Int> {
        val r = call("adminDeleteEverything", mapOf("confirm" to NUKE_PHRASE))
        return (r.l("groups")?.toInt() ?: 0) to (r.l("users")?.toInt() ?: 0)
    }

    /** Blocks app builds older than [minVersionCode] (0 allows every build). */
    suspend fun setAppConfig(minVersionCode: Long, message: String) {
        call("adminSetAppConfig", mapOf("minVersionCode" to minVersionCode, "message" to message))
    }

    companion object {
        /** Must match WIPE_PHRASE in functions/src/admin.ts. */
        const val WIPE_PHRASE = "DELETE EVERYTHING"

        /** Must match NUKE_PHRASE in functions/src/admin.ts. */
        const val NUKE_PHRASE = "DELETE ALL USERS"
    }

    /** Returns a one-time sign-in token for [uid]. */
    suspend fun impersonate(uid: String): String = call("adminImpersonate", mapOf("uid" to uid)).s("token").orEmpty()

    /** Returns a one-time sign-in token for the admin's own account. */
    suspend fun stopImpersonating(): String = call("stopImpersonating").s("token").orEmpty()
}

private fun Map<*, *>.s(k: String) = this[k] as? String
private fun Map<*, *>.l(k: String) = (this[k] as? Number)?.toLong()
private fun Map<*, *>.strings(k: String) = (this[k] as? List<*>).orEmpty().filterIsInstance<String>()

private fun Map<*, *>.toUserRow() = AdminUserRow(
    uid = s("uid").orEmpty(),
    email = s("email"),
    displayName = s("displayName").orEmpty(),
    photoUrl = s("photoUrl"),
    groupCount = l("groupCount")?.toInt() ?: 0,
    createdAt = l("createdAt"),
    lastSignInAt = l("lastSignInAt"),
    disabled = this["disabled"] as? Boolean ?: false,
    providers = strings("providers"),
    isAdmin = this["isAdmin"] as? Boolean ?: false,
)
