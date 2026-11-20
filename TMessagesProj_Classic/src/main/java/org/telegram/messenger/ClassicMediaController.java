package org.telegram.messenger;

import org.telegram.classic.ui.ChatActivity;

// MurkaGram: dual UI. Raise-to-ear: MediaController keeps the open chat as MediaController.RaiseToEarChat, which both
// the modern and the classic ChatActivity implement, so the classic chat registers the same way as in 12.2.10.
public final class ClassicMediaController {

    private ClassicMediaController() {
    }

    public static void startRaiseToEarSensors(MediaController self, ChatActivity chatActivity) {
        self.startRaiseToEarSensors(chatActivity); // MurkaGram: dual UI
    }

    public static void stopRaiseToEarSensors(MediaController self, ChatActivity chatActivity, boolean fromChat, boolean stopRecording) {
        self.stopRaiseToEarSensors(chatActivity, fromChat, stopRecording); // MurkaGram: dual UI
    }
}
