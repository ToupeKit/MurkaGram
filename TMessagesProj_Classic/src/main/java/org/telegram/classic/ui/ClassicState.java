package org.telegram.classic.ui;

import android.util.SparseArray;

import androidx.collection.LongSparseArray;

import org.telegram.classic.ui.Components.ImageUpdater;
import org.telegram.classic.ui.Stories.SelfStoryViewsPage;
import org.telegram.messenger.UserConfig;

// MurkaGram: dual UI. Per-account UI state that the core keeps in fields typed with modern UI classes
// (MessagesController.photoSuggestion, StoriesController.selfViewsModel); the classic UI keeps its own copies here.
public final class ClassicState {

    private ClassicState() {
    }

    // the classic wallpaper being uploaded by ClassicMessagesController.saveWallpaperToServer (the core gets a modern copy)
    public static org.telegram.classic.ui.ActionBar.Theme.OverrideWallpaperInfo uploadingWallpaperInfo;

    private static final SparseArray<ImageUpdater>[] photoSuggestion = new SparseArray[UserConfig.MAX_ACCOUNT_COUNT];
    private static final LongSparseArray<SparseArray<SelfStoryViewsPage.ViewsModel>>[] selfViewsModel = new LongSparseArray[UserConfig.MAX_ACCOUNT_COUNT];

    public static synchronized SparseArray<ImageUpdater> photoSuggestion(int account) {
        if (photoSuggestion[account] == null) {
            photoSuggestion[account] = new SparseArray<>();
        }
        return photoSuggestion[account];
    }

    public static synchronized LongSparseArray<SparseArray<SelfStoryViewsPage.ViewsModel>> selfViewsModel(int account) {
        if (selfViewsModel[account] == null) {
            selfViewsModel[account] = new LongSparseArray<>();
        }
        return selfViewsModel[account];
    }
}
