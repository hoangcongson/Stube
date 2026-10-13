package com.siro.stube.mobile.ui.browse;

import android.app.Activity;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * STUBE(you-subscreen): Browse's top bar. On a bottom-nav tab it is the "Stube" wordmark; a
 * section opened from a You row (Sports, Playlists, ...) is a sub-screen of You, so it gets a back
 * arrow and the section's name - like YouTube's You sub-pages - instead of a wordmark that gave
 * no hint where the user was or that back returns to You.
 */
final class BrowseTopBar {
    private static final float WORDMARK_SP = 22f;
    private static final float SUBSCREEN_TITLE_SP = 20f;

    private final TextView mTitle;
    private final View mBack;
    private final View mLogo;
    private final int mWordmarkInset;
    private final int mBesideBackInset;

    BrowseTopBar(Activity activity, Runnable onBack) {
        mTitle = activity.findViewById(R.id.mobile_title_bar);
        mBack = activity.findViewById(R.id.mobile_title_back);
        mLogo = activity.findViewById(R.id.mobile_title_logo);
        mBack.setOnClickListener(v -> onBack.run());
        mWordmarkInset = mTitle.getPaddingStart();
        mBesideBackInset = activity.getResources()
                .getDimensionPixelSize(R.dimen.mobile_dialog_title_inset_with_back);
    }

    /**
     * A tab shows the wordmark; a sub-screen shows the back arrow and {@code title} (the wordmark
     * text if the section came without one - the arrow is what matters).
     */
    void show(boolean subScreen, CharSequence title) {
        mBack.setVisibility(subScreen ? View.VISIBLE : View.GONE);
        mLogo.setVisibility(subScreen ? View.GONE : View.VISIBLE);
        // Keep the title view INVISIBLE on the main tabs so its 56dp constraint anchor remains
        // intact for the feed, while the transparent PNG wordmark occupies the same header.
        mTitle.setVisibility(subScreen ? View.VISIBLE : View.INVISIBLE);
        mTitle.setText(subScreen && title != null && title.length() > 0
                ? title : mTitle.getContext().getString(R.string.app_name));
        mTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, subScreen ? SUBSCREEN_TITLE_SP : WORDMARK_SP);
        mTitle.setPaddingRelative(subScreen ? mBesideBackInset : mWordmarkInset,
                mTitle.getPaddingTop(), mTitle.getPaddingEnd(), mTitle.getPaddingBottom());
    }
}
