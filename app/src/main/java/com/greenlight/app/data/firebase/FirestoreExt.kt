package com.greenlight.app.data.firebase

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

fun DocumentReference.snapshots(): Flow<DocumentSnapshot> = callbackFlow {
    val reg = addSnapshotListener { snap, err ->
        if (err != null) close(err) else if (snap != null) trySend(snap)
    }
    awaitClose { reg.remove() }
}

fun Query.snapshots(includeMetadata: Boolean = false): Flow<QuerySnapshot> = callbackFlow {
    val mode = if (includeMetadata) MetadataChanges.INCLUDE else MetadataChanges.EXCLUDE
    val reg = addSnapshotListener(mode) { snap, err ->
        if (err != null) close(err) else if (snap != null) trySend(snap)
    }
    awaitClose { reg.remove() }
}

fun DocumentSnapshot.millis(field: String): Long? = (get(field) as? Timestamp)?.toDate()?.time

@Suppress("UNCHECKED_CAST")
fun DocumentSnapshot.stringList(field: String): List<String> = (get(field) as? List<String>).orEmpty()

/**
 * A Firestore listener stops for good after its first error (a network blip, or a moment after
 * joining a group when the rules can't see the membership yet). Without this, a list would stay
 * empty until the app restarts. Retries with backoff up to 30 s. Pass
 * [retryPermissionDenied] = false where "denied" is a real answer (e.g. someone's private likes).
 */
fun <T> Flow<T>.retryingListener(tag: String, retryPermissionDenied: Boolean = true): Flow<T> = retryWhen { cause, attempt ->
    if (cause is CancellationException) return@retryWhen false
    val denied = (cause as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    if (denied && !retryPermissionDenied) return@retryWhen false
    Log.w("Firestore", "$tag listener failed (attempt ${attempt + 1}), retrying", cause)
    delay(min(30_000L, 1_000L shl attempt.toInt().coerceIn(0, 5)))
    true
}
