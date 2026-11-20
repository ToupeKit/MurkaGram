package org.telegram.messenger;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.view.View;

import org.telegram.ui.ActionBar.Theme;

// MurkaGram: dual UI. What the core may use of a screen (fragment) of the active UI edition. Implemented by the modern
// org.telegram.ui.ActionBar.BaseFragment and by the classic one; anything UI-typed goes through UiBridge.
public interface UiHost {

    Activity getParentActivity();

    Context getContext();

    int getCurrentAccount();

    View getFragmentView();

    Theme.ResourcesProvider getResourceProvider();

    Dialog showDialog(Dialog dialog);
}
