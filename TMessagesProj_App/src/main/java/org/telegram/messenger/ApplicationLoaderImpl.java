package org.telegram.messenger;

import org.telegram.messenger.regular.BuildConfig;

public class ApplicationLoaderImpl extends ApplicationLoader {
    @Override
    protected String onGetApplicationId() {
        return BuildConfig.APPLICATION_ID;
    }

    // MurkaGram: dual UI, this app ships the classic 12.2.10 UI too (TMessagesProj_Classic); register it before anything
    // starts. Which edition is used is decided by UiEdition (prefs, read once per process).
    @Override
    public void onCreate() {
        UiEdition.registerClassic(new org.telegram.classic.ui.ClassicUiBridge());
        super.onCreate();
    }
}
