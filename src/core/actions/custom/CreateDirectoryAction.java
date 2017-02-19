package core.actions.custom;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.jetbrains.cidr.lang.actions.newFile.OCNewFileHelper;
import com.jetbrains.cidr.lang.actions.newFile.OCNewFileHelperProvider;
import core.actions.custom.base.SimpleAction;
import core.actions.custom.interfaces.IHasPsiDirectory;
import core.actions.custom.interfaces.IHasWriteRules;
import core.search.SearchAction;
import core.search.SearchEngine;
import global.dialogs.impl.NeverShowAskCheckBox;
import global.models.BaseElement;
import global.models.Directory;
import core.writeRules.WriteRules;
import global.utils.Logger;
import global.utils.file.PsiHelper;
import global.utils.i18n.Localizer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * Created by Arsen on 09.01.2017.
 */
public class CreateDirectoryAction extends SimpleAction implements IHasPsiDirectory, IHasWriteRules {

    private Directory directory;
    private Project project;

    //result
    private PsiDirectory psiDirectoryResult;

    public CreateDirectoryAction(Directory directory, Project project) {
        this.directory = directory;
        this.project = project;
    }

    @Override
    public boolean run() {
        psiDirectoryResult = null;

        if (parentAction instanceof IHasPsiDirectory) {
            PsiDirectory psiParent = ((IHasPsiDirectory) parentAction).getPsiDirectory();

            // Custom Path
            if (directory.getCustomPath() != null) {
                psiParent = getPsiDirectoryFromCustomPath(directory, psiParent.getVirtualFile().getPath());

                if (psiParent == null) {
                    isDone = false;
                    return false;
                }
            }

            // check existence
            psiDirectoryResult = psiParent.findSubdirectory(directory.getName());
            if (psiDirectoryResult == null) {
                // create new one
                createNewOne(psiParent, directory.getName());
            } else {
                // WRITE CONFLICT
                WriteRules rules = directory.getWriteRules();
                if (rules == WriteRules.FROM_PARENT) {
                    rules = geWriteRulesFromParent(parentAction);
                }

                switch (rules) {
                    default:
                    case ASK_ME:
                        if (!onAsk(psiDirectoryResult)) {
                            return false;
                        }
                        break;
                    case OVERWRITE:
                        if (!onOverwrite(psiDirectoryResult)) {
                            return false;
                        }
                        break;
                    case USE_EXISTING:
                        if (!onUseExisting(psiDirectoryResult)) {
                            return false;
                        }
                        break;
                }
            }
        }

        if (psiDirectoryResult == null) {
            isDone = false;
            return false;
        }

        return super.run();
    }

    private boolean onAsk(PsiDirectory psiDuplicate) {
        int dialogAnswerCode = Messages.showYesNoDialog(project,
                String.format(Localizer.get("warning.ArgDirectoryAlreadyExists"), psiDuplicate.getName()),
                Localizer.get("title.WriteConflict"),
                Localizer.get("action.Overwrite"),
                Localizer.get("action.UseExisting"),
                Messages.getQuestionIcon(),
                new NeverShowAskCheckBox()
        );
        if (dialogAnswerCode == Messages.OK) {
            if (!onOverwrite(psiDuplicate)) {
                return false;
            }
        } else {
            if (!onUseExisting(psiDuplicate)) {
                return false;
            }
        }
        return true;
    }

    private boolean onOverwrite(PsiDirectory psiDuplicate) {
        PsiDirectory psiParent = psiDuplicate.getParent();
        String name = psiDuplicate.getName();

        try {
            //Remove
            psiDuplicate.delete();
            // Create
            createNewOne(psiParent, name);
            return true;
        } catch (Exception e) {
            Logger.log("CreateDirectoryAction " + e.getMessage());
            Logger.printStack(e);
            isDone = false;
            return false;
        }
    }

    private boolean onUseExisting(PsiDirectory psiDuplicate) {
        psiDirectoryResult = psiDuplicate;
        return true;
    }

    private void createNewOne(PsiDirectory psiParent, String name) {
        try {
            OCNewFileHelper helper = getFileHelperProvider();
//                DialogWrapper dialogWrapper = new XcodeCreateFileDialog();
            PsiFile[] psiFiles = new PsiFile[1];
            DialogWrapper dialogWrapper = null;

            helper.doCreateFiles(project, psiParent, new String[]{"myName.swift"}, psiFiles, dialogWrapper, null);
            psiDirectoryResult = (PsiDirectory) psiFiles[0];
            Logger.log("AppCode psiDirectoryResult:  " + psiDirectoryResult);
        } catch (NoClassDefFoundError e) {
            Logger.logAndPrintStack("AppCode CreateFiles " + name, e.fillInStackTrace());

            psiDirectoryResult = psiParent.createSubdirectory(name);
        } catch (Exception e) {
            Logger.logAndPrintStack("AppCode CreateFiles " + name, e);
            psiDirectoryResult = psiParent.createSubdirectory(name);
        }
    }

    private OCNewFileHelper getFileHelperProvider() {
        OCNewFileHelperProvider[] providers = Extensions.getExtensions(OCNewFileHelperProvider.EP_NAME);
//            OCNewFileHelperProvider[] providers = Extensions.getExtensions(ExtensionPointName.create("cidr.lang.newFileHelperProvider"));
        if (providers.length == 1) {
            return providers[0].createHelper();
        }

        return null;
    }


    //=================================================================
    //  Utils
    //=================================================================
    @Override
    public PsiDirectory getPsiDirectory() {
        return psiDirectoryResult;
    }

    @Override
    public WriteRules getWriteRules() {
        return directory.getWriteRules();
    }

    @Nullable
    private PsiDirectory getPsiDirectoryFromCustomPath(BaseElement element, String pathFrom) {
        ArrayList<SearchAction> actions = element.getCustomPath().getListSearchAction();

        java.io.File searchResultFile = SearchEngine.find(new java.io.File(pathFrom), actions);

        if (searchResultFile == null) {
            //print name of last action
            Logger.log("getCustomPath File Not Found: " + (actions.isEmpty() ? "" : actions.get(actions.size() - 1).getName()));
            return null;
        }

        return PsiHelper.findPsiDirByPath(project, searchResultFile.getPath());
    }

}
