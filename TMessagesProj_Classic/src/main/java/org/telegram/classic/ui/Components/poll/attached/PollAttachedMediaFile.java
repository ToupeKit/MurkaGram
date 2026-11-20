package org.telegram.classic.ui.Components.poll.attached;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.ui.Components.poll.PollAttachedMedia;

// MurkaGram: stub (core-13) — data only, the 13.2.2 drawing code needs the new UI
public class PollAttachedMediaFile extends PollAttachedMedia {
    public @Nullable final String path;
    public @Nullable final Uri uri;

    public final String name;
    public final long size;
    public final String ext;

    public PollAttachedMediaFile(@NonNull String path) {
        this.path = path;
        this.uri = null;
        this.name = null;
        this.size = 0;
        this.ext = null;
    }

    public PollAttachedMediaFile(@NonNull Uri uri) {
        this.path = null;
        this.uri = uri;
        this.name = null;
        this.size = 0;
        this.ext = null;
    }
}
