package org.telegram.classic.ui.Wallet;

import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.tl.TL_update;
import org.telegram.tgnet.tl.TL_wallet;

import java.math.BigDecimal;
import java.math.RoundingMode;

// MurkaGram: stub (core-13) — the 13.2.2 wallet (native wallet engine + UI) is not taken; updates are ignored
public class GramWallet {

    private static final GramWallet[] Instance = new GramWallet[UserConfig.MAX_ACCOUNT_COUNT];

    public final TonConnect tonConnect = new TonConnect();

    public static GramWallet getInstance(int num) {
        GramWallet localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (GramWallet.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new GramWallet();
                }
            }
        }
        return localInstance;
    }

    public static void cleanup(int currentAccount) {
    }

    public void onUpdate(TL_update.TL_updateWalletState update) {
    }

    public void onUpdate(TL_update.TL_updateWalletGaslessInfo update) {
    }

    public void onUpdate(TL_update.TL_updateSentWalletTransaction update) {
    }

    public static CharSequence formatGramsWithUnit(long amount) {
        return BigDecimal.valueOf(amount, 9).setScale(2, RoundingMode.DOWN).stripTrailingZeros().toPlainString() + " GRAM";
    }

    public static class TonConnect {

        public void onSessionUpdate(TL_wallet.tonConnectSession session) {
        }

        public void markProcessed(long sessionId, int messageId) {
        }
    }
}
