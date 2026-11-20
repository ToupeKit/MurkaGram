package org.telegram.messenger;

import org.telegram.classic.ui.ActionBar.BaseFragment;

// MurkaGram: dual UI. The files-migration sheet (legacy external storage -> scoped storage, Android 11) is a modern
// BottomSheet nested in FilesMigrationService; the classic UI does not offer it. TODO(E5): route through UiBridge if needed.
public final class ClassicFilesMigrationService {

    private ClassicFilesMigrationService() {
    }

    public static void checkBottomSheet(BaseFragment fragment) {
    }
}
