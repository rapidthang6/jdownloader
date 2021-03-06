package org.jdownloader.gui.dialog;

import java.awt.Dialog.ModalityType;

import jd.controlling.AccountController;
import jd.plugins.PluginForHost;

import org.appwork.uio.UIOManager;
import org.appwork.utils.swing.dialog.ConfirmDialog;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.AbstractIcon;

public class AskToUsePremiumDialog extends ConfirmDialog implements AskToUsePremiumDialogInterface {
    private final String domain;

    public AskToUsePremiumDialog(String domain, PluginForHost plugin) {
        super(UIOManager.LOGIC_COUNTDOWN, _GUI.T.PluginForHost_showFreeDialog_title(domain), _GUI.T.PluginForHost_showFreeDialog_message(domain), new AbstractIcon(IconKey.ICON_PREMIUM, 32), _GUI.T.lit_yes(), _GUI.T.lit_no());
        setTimeout(5 * 60 * 1000);
        this.domain = domain;
    }

    @Override
    public ModalityType getModalityType() {
        return ModalityType.MODELESS;
    }

    @Override
    public String getPremiumUrl() {
        return AccountController.createFullBuyPremiumUrl(getDomain(), "freedialog");
    }

    @Override
    public String getDomain() {
        return domain;
    }
}