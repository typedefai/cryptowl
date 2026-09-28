package com.typedefai.cryptowl.vault

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Thrown when a backup/restore cannot proceed (bad source, corrupt meta...). */
class VaultBackupException(message: String) : Exception(message)

/**
 * Vault backup / restore over the Storage Access Framework — the migration
 * path for desktop-produced vaults (`wechat_sns_export/migrate_moments.py`):
 *
 *   restore: picked tree (vault.meta, vault.db, config.json, config.sig,
 *            device_secret, attachments/, thumbnails/) is copied into a
 *            staging dir, validated, then atomically swapped into
 *            <filesDir>/vaults/<vaultId>/. After a restore the vault needs
 *            its master password to unlock (first unlock re-binds a desktop
 *            `device_secret` to the Android Keystore — UnlockService).
 *
 *   backup:  the whole vault dir is copied into a timestamped subdirectory
 *            of the picked tree (never overwrites existing data).
 */
class VaultBackup(private val context: Context) {

    enum class Phase { Listing, Copying, Validating, Finalizing, Done }

    data class Progress(
        val phase: Phase,
        val filesDone: Int = 0,
        val filesTotal: Int = 0,
        val bytesDone: Long = 0,
        val bytesTotal: Long = 0,
    )

    private data class SourceFile(val docUri: Uri, val relativePath: String, val size: Long)

    private data class TreeNode(val docId: String, val name: String, val isDir: Boolean, val size: Long)

    // ---------------------------------------------------------------- restore

    /** Copies the vault in [treeUri] into app storage, replacing the current [vaultId] vault. */
    fun restore(treeUri: Uri, vaultId: String, onProgress: (Progress) -> Unit) {
        try {
            Log.i(TAG, "restore: start tree=$treeUri vaultId=$vaultId")
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            onProgress(Progress(Phase.Listing))
            val files = collectFiles(treeUri, rootId)
            val meta = files.firstOrNull { it.relativePath == "vault.meta" }
                ?: throw VaultBackupException("not a vault directory: vault.meta missing")
            val db = files.firstOrNull { it.relativePath == "vault.db" }
                ?: throw VaultBackupException("not a vault directory: vault.db missing")
            val totalBytes = files.sumOf { it.size }
            Log.i(TAG, "restore: ${files.size} files, $totalBytes bytes")

            val staging = File(VaultStore.vaultsDir(context), "$vaultId.restore")
            if (staging.exists()) staging.deleteRecursively()
            staging.mkdirs()
            try {
                copyFiles(files, treeUri, staging, totalBytes, onProgress)
                onProgress(Progress(Phase.Validating, files.size, files.size, totalBytes, totalBytes))
                validateStaged(staging)

                onProgress(Progress(Phase.Finalizing, files.size, files.size, totalBytes, totalBytes))
                swapIntoPlace(staging, VaultStore.vaultDir(context, vaultId))
                Log.i(TAG, "restore: done (${files.size} files, $totalBytes bytes)")
                onProgress(Progress(Phase.Done, files.size, files.size, totalBytes, totalBytes))
            } catch (e: Throwable) {
                staging.deleteRecursively()
                throw e
            }
        } catch (e: VaultBackupException) {
            Log.e(TAG, "restore failed", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "restore failed", e)
            throw VaultBackupException(e.message ?: "restore failed")
        }
    }

    /** Sanity-checks the staged copy before it replaces the live vault. */
    private fun validateStaged(staging: File) {
        val metaFile = File(staging, "vault.meta")
        val meta = try {
            VaultMetaJson.decode(metaFile.readText())
        } catch (e: Exception) {
            Log.e(TAG, "restore: staged vault.meta failed to parse", e)
            throw VaultBackupException("corrupt vault.meta in the selected backup: ${e.message}")
        }
        if (meta.vaultId != VaultStore.DEFAULT_VAULT_ID) {
            Log.e(TAG, "restore: meta vaultId=${meta.vaultId} != ${VaultStore.DEFAULT_VAULT_ID}")
            throw VaultBackupException("vault id mismatch: ${meta.vaultId}")
        }
        val dbFile = File(staging, "vault.db")
        if (dbFile.length() < 4096) {
            throw VaultBackupException("vault.db is missing or truncated (${dbFile.length()} bytes)")
        }
    }

    /** staging dir → live vault dir, with rollback if the final rename fails. */
    private fun swapIntoPlace(staging: File, target: File) {
        val backup = File(target.parentFile, "${target.name}.old-${System.currentTimeMillis()}")
        val hadExisting = target.exists()
        if (hadExisting && !target.renameTo(backup)) {
            throw VaultBackupException("cannot move the existing vault aside")
        }
        if (!staging.renameTo(target)) {
            if (hadExisting) backup.renameTo(target)
            throw VaultBackupException("cannot move the restored vault into place")
        }
        if (hadExisting) backup.deleteRecursively()
    }

    // ----------------------------------------------------------------- backup

    /** Copies the current [vaultId] vault into a timestamped subdirectory of [treeUri]. */
    fun backup(treeUri: Uri, vaultId: String, onProgress: (Progress) -> Unit) {
        try {
            Log.i(TAG, "backup: start tree=$treeUri vaultId=$vaultId")
            val vaultDir = VaultStore.vaultDir(context, vaultId)
            if (!VaultStore.isOnboarded(context, vaultId)) {
                throw VaultBackupException("no vault to back up")
            }
            onProgress(Progress(Phase.Listing))
            val files = vaultDir.walkTopDown()
                .filter { it.isFile }
                .map { it.relativeTo(vaultDir).invariantSeparatorsPath to it }
                .toList()
            val totalBytes = files.sumOf { (_, f) -> f.length() }
            Log.i(TAG, "backup: ${files.size} files, $totalBytes bytes")

            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val resolver = context.contentResolver
            val rootDoc = DocumentsContract.buildDocumentUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri),
            )
            val backupDirDoc = DocumentsContract.createDocument(
                resolver, rootDoc, DocumentsContract.Document.MIME_TYPE_DIR, "cryptowl-backup-$stamp",
            ) ?: throw VaultBackupException("cannot create the backup folder")

            var done = 0
            var bytes = 0L
            for ((rel, file) in files.sortedBy { (rel, _) -> rel }) {
                ensureDir(backupDirDoc, treeUri, rel.substringBeforeLast('/', ""))
                val target = DocumentsContract.createDocument(
                    resolver, backupDirDoc, mimeFor(file.name), file.name,
                ) ?: throw VaultBackupException("cannot create $rel in the backup folder")
                resolver.openOutputStream(target)?.use { out ->
                    file.inputStream().use { it.copyTo(out, COPY_BUFFER) }
                } ?: throw VaultBackupException("cannot write $rel")
                done++
                bytes += file.length()
                onProgress(Progress(Phase.Copying, done, files.size, bytes, totalBytes))
            }
            Log.i(TAG, "backup: done (${files.size} files, $totalBytes bytes)")
            onProgress(Progress(Phase.Done, files.size, files.size, totalBytes, totalBytes))
        } catch (e: VaultBackupException) {
            Log.e(TAG, "backup failed", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "backup failed", e)
            throw VaultBackupException(e.message ?: "backup failed")
        }
    }

    /** Creates the intermediate directories of [rel] under [parentDoc]. */
    private fun ensureDir(parentDoc: Uri, treeUri: Uri, rel: String) {
        if (rel.isEmpty()) return
        var parent = parentDoc
        for (part in rel.split('/')) {
            val existing = findChild(treeUri, parent, part)
            val dir = existing ?: DocumentsContract.createDocument(
                context.contentResolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, part,
            ) ?: throw VaultBackupException("cannot create folder $part")
            parent = dir
        }
    }

    private fun findChild(treeUri: Uri, parentDoc: Uri, name: String): Uri? {
        val parentId = DocumentsContract.getDocumentId(parentDoc)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0))
                }
            }
        }
        return null
    }

    // ------------------------------------------------------------------- copy

    /** Recursively lists every file under [rootId], files first sorted by path. */
    private fun collectFiles(treeUri: Uri, rootId: String): List<SourceFile> {
        val out = mutableListOf<SourceFile>()
        fun walk(parentId: String, prefix: String) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val nodes = mutableListOf<TreeNode>()
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    nodes.add(
                        TreeNode(
                            docId = c.getString(0),
                            name = c.getString(1),
                            isDir = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                            size = c.getLong(3),
                        ),
                    )
                }
            } ?: throw VaultBackupException("cannot read the selected folder")
            for (node in nodes.sortedBy { it.name }) {
                if (node.isDir) {
                    walk(node.docId, "$prefix${node.name}/")
                } else {
                    out.add(
                        SourceFile(
                            docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, node.docId),
                            relativePath = prefix + node.name,
                            size = node.size,
                        ),
                    )
                }
            }
        }
        walk(rootId, "")
        if (out.isEmpty()) throw VaultBackupException("the selected folder is empty")
        return out
    }

    private fun copyFiles(files: List<SourceFile>, treeUri: Uri, staging: File, totalBytes: Long, onProgress: (Progress) -> Unit) {
        var done = 0
        var bytes = 0L
        for (f in files) {
            val target = File(staging, f.relativePath)
            target.parentFile?.mkdirs()
            context.contentResolver.openInputStream(f.docUri)?.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output, COPY_BUFFER)
                }
            } ?: throw VaultBackupException("cannot read ${f.relativePath} from the selected folder")
            if (target.length() != f.size && f.size >= 0) {
                throw VaultBackupException("size mismatch for ${f.relativePath}: expected ${f.size}, got ${target.length()}")
            }
            done++
            bytes += f.size
            onProgress(Progress(Phase.Copying, done, files.size, bytes, totalBytes))
        }
    }

    private fun mimeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "meta", "sig", "json", "tmp" -> "application/json"
            "db" -> "application/octet-stream"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            else -> "application/octet-stream"
        }
    }

    private companion object {
        const val TAG = "cwl:VaultBackup"
        const val COPY_BUFFER = 64 * 1024
    }
}
