package com.pdfcraft.studio.core.settings

/**
 * Central place for runtime-permission checks (e.g. media/storage access
 * needed once PDFs and images are read from / written to device storage).
 * No permissions are requested in this stage — the home screen and empty
 * editor need none — but future stages should route all permission
 * requests through here rather than scattering them across screens.
 */
interface PermissionsManager {
    // fun hasStorageAccess(): Boolean
    // fun requestStorageAccess(onResult: (granted: Boolean) -> Unit)
}
