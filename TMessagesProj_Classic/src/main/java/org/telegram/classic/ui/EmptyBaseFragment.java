package org.telegram.classic.ui;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.classic.ui.ActionBar.BaseFragment;
import org.telegram.classic.ui.Components.SizeNotifierFrameLayout;

public class EmptyBaseFragment extends BaseFragment {

    @Override
    public View createView(Context context) {
        return fragmentView = new SizeNotifierFrameLayout(context);
    }

}
