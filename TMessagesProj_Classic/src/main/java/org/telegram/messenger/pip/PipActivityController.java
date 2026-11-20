package org.telegram.messenger.pip;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.session.MediaSession;


import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.pip.activity.IPipActivityActionListener;
import org.telegram.messenger.pip.activity.IPipActivityAnimationListener;
import org.telegram.messenger.pip.activity.IPipActivityHandler;
import org.telegram.messenger.pip.activity.IPipActivityListener;
import org.telegram.messenger.pip.utils.PipUtils;

import java.util.HashMap;

public class PipActivityController {
    private final HashMap<String, PipSource> sources = new HashMap<>();
    private final PipActivityHandler handler;
    private PipActivityContentLayout pipContentView;

    public final Activity activity;

    public PipActivityController(Activity activity) {
        this.activity = activity;
        this.handler = new PipActivityHandler(activity);

        handler.addPipListener(new IPipActivityListener() {
            @Override
            public void onStartEnterToPip() {
                Log.d(PipUtils.TAG, "onStartEnterToPip");
            }

            @Override
            public void onCompleteEnterToPip() {
                Log.d(PipUtils.TAG, "onCompleteEnterToPip");
            }

            @Override
            public void onStartExitFromPip(boolean byActivityStop) {
                Log.d(PipUtils.TAG, "onStartExitFromPip: byActivityStop=" + byActivityStop);
            }

            @Override
            public void onCompleteExitFromPip(boolean byActivityStop) {
                Log.d(PipUtils.TAG, "onCompleteExitFromPip: byActivityStop=" + byActivityStop);
            }
        });
        handler.addAnimationListener(new IPipActivityAnimationListener() {
            @Override
            public void onEnterAnimationStart(long estimatedDuration) {
                Log.d(PipUtils.TAG, "onEnterAnimationStart: estimatedDuration=" + estimatedDuration);
            }

            @Override
            public void onEnterAnimationEnd(long duration) {
                Log.d(PipUtils.TAG, "onEnterAnimationEnd: duration=" + duration);
            }

            @Override
            public void onLeaveAnimationStart(long estimatedDuration) {
                Log.d(PipUtils.TAG, "onLeaveAnimationStart: estimatedDuration=" + estimatedDuration);
            }

            @Override
            public void onLeaveAnimationEnd(long duration) {
                Log.d(PipUtils.TAG, "onLeaveAnimationEnd: duration=" + duration);
            }

            @Override
            public void onTransitionAnimationFrame() {
                Log.d(PipUtils.TAG, "onTransitionAnimationFrame");
            }

            @Override
            public void onTransitionAnimationProgress(float estimatedProgress) {
                Log.d(PipUtils.TAG, "onTransitionAnimationProgress: estimatedProgress=" + estimatedProgress);
            }
        });
    }

    public IPipActivityHandler getHandler() {
        return handler;
    }

    public ViewGroup getPipContentView() {
        if (this.pipContentView == null) {
            this.pipContentView = new PipActivityContentLayout(activity);
        }

        return this.pipContentView;
    }









    private @Nullable PipSource maxPrioritySource;

    private void updateSources() {
        final PipSource oldSource = maxPrioritySource;
        PipSource newSource = null;

        for (PipSource source : sources.values()) {
            if (!source.isAvailable() && !source.state2.isAttachedToPip()) {
                continue;
            }

            if (newSource == null) {
                newSource = source;
            } else if (source.priority > newSource.priority) {
                newSource = source;
            }
        }

        if (oldSource != newSource) {
            maxPrioritySource = newSource;
            onMaxPrioritySourceChanged(oldSource, newSource);
        }
    }

    private void onMaxPrioritySourceChanged(PipSource oldSource, PipSource newSource) {
        Log.i("PIP_DEBUG", "onMaxPrioritySourceChanged " + (newSource != null ? newSource.tag : null));

        PipUtils.applyPictureInPictureParams(activity, newSource);

        // MurkaGram: dual UI, 12.2.10 MediaSessionCompat + exoplayer2 MediaSessionConnector -> a media3 MediaSession on the player
        updateMediaSession(newSource);

        if (oldSource != null) {
            oldSource.state2.onLoseMaxPriority();
        }

        if (newSource != null) {
            pipContentView.bringToFront();
            newSource.state2.onReceiveMaxPriority();
        } else if (oldSource != null) {
            if (AndroidUtilities.isInPictureInPictureMode(activity)) {
                activity.moveTaskToBack(false);
            }
        }



        pipContentView.invalidate();
    }

    // MurkaGram: dual UI. media3 has no MediaSessionConnector (exoplayer2 ext): a media3 MediaSession is built on the
    // player of the max priority source (as the modern org.telegram.utils.pip.SystemHost does), giving PiP the
    // play/pause/seek controls and the media notification.
    private MediaSession mediaSession;

    private void updateMediaSession(@Nullable PipSource source) {
        final Player player = source != null && source.needMediaSession ? source.player : null;
        if (player == null) {
            releaseMediaSession();
            return;
        }
        if (mediaSession != null && mediaSession.getPlayer() == player) {
            return;
        }
        releaseMediaSession();
        try {
            mediaSession = new MediaSession.Builder(activity, player)
                .setId("classic-pip-media-session")
                .build();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void releaseMediaSession() {
        if (mediaSession != null) {
            try {
                mediaSession.release();
            } catch (Exception e) {
                FileLog.e(e);
            }
            mediaSession = null;
        }
    }





    /* Source Internal */

    void dispatchSourceRegister(PipSource source) {
        this.sources.put(source.tag, source);
        updateSources();
    }

    void dispatchSourceUnregister(PipSource source) {
        if (this.sources.remove(source.tag) != null) {
            updateSources();
        }
    }

    void dispatchSourceAvailabilityChanged(PipSource source) {
        updateSources();
        pipContentView.invalidate();
    }

    void dispatchSourceParamsChanged(PipSource source) {
        if (maxPrioritySource == source) {
            PipUtils.applyPictureInPictureParams(activity, source);
            updateMediaSession(source); // MurkaGram: dual UI, 12.2.10 mediaSessionConnector.setPlayer(source.player)
        }
        pipContentView.invalidate();
    }



    /* * */

    public boolean hasContentForPictureInPictureMode() {
        return maxPrioritySource != null;
    }

    public void addPipListener(IPipActivityListener listener) {
        handler.addPipListener(listener);
    }

    public void removePipListener(IPipActivityListener listener) {
        handler.removePipListener(listener);
    }

    public void addAnimationListener(IPipActivityAnimationListener listener) {
        handler.addAnimationListener(listener);
    }

    public void removeAnimationListener(IPipActivityAnimationListener listener) {
        handler.removeAnimationListener(listener);
    }

    public void addActionListener(String sourceId, IPipActivityActionListener listener) {
        handler.addActionListener(sourceId, listener);
    }

    public void removeActionListener(String sourceId, IPipActivityActionListener listener) {
        handler.removeActionListener(sourceId, listener);
    }
}
