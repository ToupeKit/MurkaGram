package org.telegram.messenger;

import org.telegram.classic.ui.ChatActivity;

// MurkaGram: dual UI. Raise-to-ear in MediaController keeps a modern ChatActivity (raiseChat) and calls it from the
// sensor callbacks, so the classic chat cannot take part directly. TODO(E5): route through UiBridge.
public final class ClassicMediaController {

    private ClassicMediaController() {
    }

    public static void startRaiseToEarSensors(MediaController self, ChatActivity chatActivity) {
    }

    public static void stopRaiseToEarSensors(MediaController self, ChatActivity chatActivity, boolean fromChat, boolean stopRecording) {
    }
}
