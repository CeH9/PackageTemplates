package core.state.util;

import core.state.models.StateModel;
import core.state.models.UserSettings;
import global.utils.i18n.Language;

/**
 * Хелпер для взаимодествия с конфигом
 * Created by Arsen on 09.10.2016.
 */
public class StateEditor {

    private SaveUtil saveUtil;
    private StateModel model;

    public StateEditor(SaveUtil saveUtil, StateModel model) {
        this.saveUtil = saveUtil;
        this.model = model;
    }

    private UserSettings userSettings(){
        return model.getUserSettings();
    }

    public StateEditor save(){
        saveUtil.save();
        return this;
    }


    public StateEditor setLanguage(Language lang) {
        userSettings().setLanguage(lang);
        return this;
    }

}
