package com.example.firebase

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class WorkspaceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        }
        return uid
    }

    fun observeScripts(userId: String): Flow<List<PythonScript>> = flow {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId/scripts"
        emitAll(
            db.collection("users").document(userId).collection("scripts")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PythonScript::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAutomatedApps(userId: String): Flow<List<AutomatedAppRecord>> = flow {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId/automated_apps"
        emitAll(
            db.collection("users").document(userId).collection("automated_apps")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AutomatedAppRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createScript(
        filename: String,
        folder: String,
        content: String,
        category: ScriptCategory,
        description: String
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/scripts"
        return try {
            val uid = requireUserId()
            val cleanName = filename.trim().take(120).ifEmpty { "untitled.py" }
            val cleanFolder = folder.trim().take(100)
            val cleanContent = content.take(100_000)
            val cleanDesc = description.trim().take(300)

            val docRef = db.collection("users").document(uid).collection("scripts").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "filename" to cleanName,
                "folder" to cleanFolder,
                "content" to cleanContent,
                "category" to category.name,
                "description" to cleanDesc,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateScript(
        scriptId: String,
        filename: String? = null,
        folder: String? = null,
        content: String? = null,
        category: ScriptCategory? = null,
        description: String? = null
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/scripts/$scriptId"
        return try {
            val uid = requireUserId()
            require(scriptId.isNotBlank()) { "scriptId must not be blank" }
            val docRef = db.collection("users").document(uid).collection("scripts").document(scriptId)
            docPath = docRef.path
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            filename?.let { updates["filename"] = it.trim().take(120).ifEmpty { "untitled.py" } }
            folder?.let { updates["folder"] = it.trim().take(100) }
            content?.let { updates["content"] = it.take(100_000) }
            category?.let { updates["category"] = it.name }
            description?.let { updates["description"] = it.trim().take(300) }

            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteScript(scriptId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/scripts/$scriptId"
        return try {
            val uid = requireUserId()
            require(scriptId.isNotBlank()) { "scriptId must not be blank" }
            val docRef = db.collection("users").document(uid).collection("scripts").document(scriptId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createAutomatedApp(
        name: String,
        summary: String,
        iconKey: String,
        accentHex: String,
        pythonCode: String,
        inputsSpec: String,
        triggerMode: TriggerMode,
        intervalSeconds: Int,
        has3dViewport: Boolean
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/automated_apps"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("automated_apps").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "name" to name.trim().take(80).ifEmpty { "Automated App" },
                "summary" to summary.trim().take(300),
                "iconKey" to iconKey.trim().take(40).ifEmpty { "bolt" },
                "accentHex" to accentHex.trim().take(12).ifEmpty { "#00E5FF" },
                "pythonCode" to pythonCode.take(100_000),
                "inputsSpec" to inputsSpec.take(20_000),
                "triggerMode" to triggerMode.name,
                "intervalSeconds" to intervalSeconds.coerceIn(0, 3600),
                "has3dViewport" to has3dViewport,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateAutomatedApp(
        appId: String,
        name: String? = null,
        summary: String? = null,
        iconKey: String? = null,
        accentHex: String? = null,
        pythonCode: String? = null,
        inputsSpec: String? = null,
        triggerMode: TriggerMode? = null,
        intervalSeconds: Int? = null,
        has3dViewport: Boolean? = null
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/automated_apps/$appId"
        return try {
            val uid = requireUserId()
            require(appId.isNotBlank()) { "appId must not be blank" }
            val docRef = db.collection("users").document(uid).collection("automated_apps").document(appId)
            docPath = docRef.path
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            name?.let { updates["name"] = it.trim().take(80).ifEmpty { "Automated App" } }
            summary?.let { updates["summary"] = it.trim().take(300) }
            iconKey?.let { updates["iconKey"] = it.trim().take(40).ifEmpty { "bolt" } }
            accentHex?.let { updates["accentHex"] = it.trim().take(12).ifEmpty { "#00E5FF" } }
            pythonCode?.let { updates["pythonCode"] = it.take(100_000) }
            inputsSpec?.let { updates["inputsSpec"] = it.take(20_000) }
            triggerMode?.let { updates["triggerMode"] = it.name }
            intervalSeconds?.let { updates["intervalSeconds"] = it.coerceIn(0, 3600) }
            has3dViewport?.let { updates["has3dViewport"] = it }

            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAutomatedApp(appId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/automated_apps/$appId"
        return try {
            val uid = requireUserId()
            require(appId.isNotBlank()) { "appId must not be blank" }
            val docRef = db.collection("users").document(uid).collection("automated_apps").document(appId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
