package org.telegram.classic.ui.Wallet;

import androidx.annotation.Nullable;

// MurkaGram: stub (core-13) — the 13.2.2 wallet engine is native and not taken
public class WalletEngine2 {

    public static boolean isValidAddress(@Nullable String address) {
        return false;
    }

    public enum SendPhase {
        IDLE, VALIDATING, AUTHORIZING, PREPARING, PERSISTING,
        READY_TO_SUBMIT, SUBMITTING, SUBMISSION_UNKNOWN, SUBMITTED,
        HANDED_OFF, CONFIRMED, REPLACED, SEQUENCE_NUMBER_CONSUMED,
        EXPIRED, SUPERSEDED, FAILED, CANCELLED
    }
}
