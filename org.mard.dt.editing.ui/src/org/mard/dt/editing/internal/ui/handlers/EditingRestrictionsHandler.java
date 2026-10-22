/*******************************************************************************
 * Copyright (C) 2026, Dmitriy Marmyshev and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Dmitriy Marmyshev - initial API and implementation
 *******************************************************************************/
package org.mard.dt.editing.internal.ui.handlers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Objects;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.handlers.HandlerUtil;
import org.mard.dt.editing.EditingSettingsYamlUpdater;
import org.mard.dt.editing.IPathEditingService;
import org.mard.dt.editing.internal.ui.Messages;
import org.mard.dt.editing.internal.ui.UiPlugin;

import com._1c.g5.v8.dt.core.filesystem.IQualifiedNameFilePathConverter;
import com._1c.g5.v8.dt.core.platform.IResourceLookup;
import com._1c.g5.v8.dt.metadata.mdclass.AbstractForm;
import com._1c.g5.v8.dt.metadata.mdclass.BasicForm;
import com.google.inject.Inject;

/**
 * The handler of the configuration tree context menu commands: copies the FQN of the selected
 * target or adds the FQN to the {@code disable.fullname} or {@code enable.fullname} settings.
 *
 * @author Dmitriy Marmyshev
 */
public class EditingRestrictionsHandler
    extends AbstractHandler
{
    private static final String PARAM_TARGET = "org.mard.dt.editing.ui.commands.param.target"; //$NON-NLS-1$

    private static final String COMMAND_COPY = "copyFqn"; //$NON-NLS-1$

    private static final String COMMAND_ENABLE = "addEnable"; //$NON-NLS-1$

    private static final String COMMAND_REFRESH = "refreshRestrictions"; //$NON-NLS-1$

    private static final String TARGET_SELF = "self"; //$NON-NLS-1$

    private static final String TARGET_OBJECT_MODULE = "objectModule"; //$NON-NLS-1$

    private static final String TARGET_MANAGER_MODULE = "managerModule"; //$NON-NLS-1$

    private static final String TARGET_RECORD_SET_MODULE = "recordSetModule"; //$NON-NLS-1$

    private static final String TARGET_FORM_MODULE = "formModule"; //$NON-NLS-1$

    @Inject
    private IResourceLookup resourceLookup;

    @Inject
    private IPathEditingService pathEditingService;

    @Inject
    private IQualifiedNameFilePathConverter qualifiedNameFilePathConverter;

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException
    {
        IStructuredSelection selection = HandlerUtil.getCurrentStructuredSelection(event);
        if (selection.size() != 1 || !(selection.getFirstElement() instanceof EObject selected))
            return null;

        String commandId = event.getCommand().getId();
        String targetParam = event.getParameter(PARAM_TARGET);

        IProject project = resourceLookup.getProject((EObject)selected);
        if (project == null)
        {
            error(event, Messages.EditingRestrictionsHandler_Project_not_found, null);
            return null;
        }

        try
        {
            if (commandId.endsWith(COMMAND_COPY))
            {
                copy(event, selected, targetParam);
            }
            else if (commandId.endsWith(COMMAND_REFRESH))
            {
                // Drop the cached rules so the next check re-reads editing.yml.
                pathEditingService.invalidate(project);
            }
            else
            {
                addToSettings(event, project, selected, targetParam, commandId.endsWith(COMMAND_ENABLE));
            }
        }
        catch (IllegalArgumentException e)
        {
            error(event, MessageFormat.format(Messages.EditingRestrictionsHandler_Unable_to_update,
                IPathEditingService.SETTING_FILE_PATH, e.getLocalizedMessage()), e);
        }
        catch (Exception e)
        {
            UiPlugin.logError(e);
            error(event, e.getLocalizedMessage() != null ? e.getLocalizedMessage() : e.getClass().getSimpleName(), e);
        }
        return null;
    }

    private void copy(ExecutionEvent event, EObject selected, String targetParam)
        throws CoreException, ExecutionException
    {
        EObject target = resolveTarget(selected, targetParam);
        if (target == null)
        {
            error(event, Messages.EditingRestrictionsHandler_Module_not_found, null);
            return;
        }

        String fqn = getFqn(target);
        if (fqn == null || fqn.isBlank())
        {
            error(event, Messages.EditingRestrictionsHandler_Fqn_not_resolved, null);
            return;
        }

        Display display = HandlerUtil.getActiveShellChecked(event).getDisplay();
        Clipboard clipboard = new Clipboard(display);
        try
        {
            clipboard.setContents(new Object[] {fqn}, new Transfer[] {TextTransfer.getInstance()});
        }
        finally
        {
            clipboard.dispose();
        }
    }

    private void addToSettings(ExecutionEvent event, IProject project, EObject selected, String targetParam,
        boolean enable)
        throws CoreException, IOException, ExecutionException
    {
        EObject target = resolveTarget(selected, targetParam);
        if (target == null)
        {
            error(event, Messages.EditingRestrictionsHandler_Module_not_found, null);
            return;
        }

        String fqn = getFqn(target);
        if (fqn == null || fqn.isBlank())
        {
            error(event, Messages.EditingRestrictionsHandler_Fqn_not_resolved, null);
            return;
        }

        String section = enable ? "enable" : "disable"; //$NON-NLS-1$ //$NON-NLS-2$

        IFile file = project.getFile(IPathEditingService.SETTING_FILE_PATH);

        if (isDirtyEditor(event, file))
        {
            MessageDialog.openWarning(HandlerUtil.getActiveShellChecked(event),
                Messages.EditingRestrictionsHandler_Dialog_title,
                MessageFormat.format(Messages.EditingRestrictionsHandler_Unsaved_changes, file.getFullPath()));
            return;
        }

        String oldContent = file.exists() ? new String(file.getContents().readAllBytes(), StandardCharsets.UTF_8) : null;

        String newContent = EditingSettingsYamlUpdater.appendFullname(oldContent, section, fqn);
        if (Objects.equals(newContent, oldContent))
        {
            // The FQN is already in the list, nothing to change.
            return;
        }

        Display display = HandlerUtil.getActiveShellChecked(event).getDisplay();
        String title = Messages.EditingRestrictionsHandler_Dialog_title;

        Job.create(title, monitor -> {
            try
            {
                byte[] bytes = newContent.getBytes(StandardCharsets.UTF_8);
                if (!file.exists())
                {
                    IContainer parent = file.getParent();
                    if (!parent.exists() && parent instanceof IFolder folder)
                        folder.create(true, true, monitor);
                    file.create(new ByteArrayInputStream(bytes), true, monitor);
                }
                else
                {
                    file.setContents(new ByteArrayInputStream(bytes), IResource.FORCE, monitor);
                }
            }
            catch (CoreException e)
            {
                UiPlugin.logError(e);
                display.asyncExec(() -> MessageDialog.openError(HandlerUtil.getActiveShell(event), title,
                    MessageFormat.format(Messages.EditingRestrictionsHandler_Unable_to_update,
                        IPathEditingService.SETTING_FILE_PATH, e.getLocalizedMessage())));
            }
        }).schedule();
    }

    private EObject resolveTarget(EObject selected, String targetParam)
    {
        if (targetParam == null || TARGET_SELF.equals(targetParam))
        {
            // Navigator form nodes live in the parent catalog BM resource, the form model itself is in Form.form.
            if (selected instanceof BasicForm basicForm && basicForm.getForm() != null)
                return basicForm.getForm();
            return selected;
        }

        return switch (targetParam)
        {
            // Module features exist on different md classes (BasicDbObject, registers, etc.),
            // so resolve them reflectively to support every metadata type.
            case TARGET_OBJECT_MODULE -> module(selected, TARGET_OBJECT_MODULE);
            case TARGET_MANAGER_MODULE -> module(selected, TARGET_MANAGER_MODULE);
            case TARGET_RECORD_SET_MODULE -> module(selected, TARGET_RECORD_SET_MODULE);
            case TARGET_FORM_MODULE -> {
                if (selected instanceof AbstractForm form && form.getModule() != null)
                    yield (EObject)form.getModule();
                if (selected instanceof BasicForm basicForm && basicForm.getForm() != null
                    && basicForm.getForm().getModule() != null)
                    yield (EObject)basicForm.getForm().getModule();
                yield null;
            }
            default -> null;
        };
    }

    private EObject module(EObject object, String feature)
    {
        var structuralFeature = object.eClass().getEStructuralFeature(feature);
        return structuralFeature == null ? null : (EObject)object.eGet(structuralFeature);
    }

    private String getFqn(EObject target)
    {
        String subObjectFqn = pathEditingService.getObjectFullName(target);
        if (subObjectFqn != null)
            return subObjectFqn;

        IFile file = resourceLookup.getPlatformResource(target);
        if (file == null)
            return null;
        // BSL modules aren't attached to BM, so the FQN is derived from the resource file like EDT does.
        return qualifiedNameFilePathConverter.getFqn(file).toString();
    }

    private boolean isDirtyEditor(ExecutionEvent event, IFile file)
    {
        try
        {
            IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindowChecked(event).getActivePage();
            for (IEditorReference reference : page.getEditorReferences())
            {
                IEditorInput input = reference.getEditorInput();
                if (input instanceof IFileEditorInput fileInput && file.equals(fileInput.getFile())
                    && reference.isDirty())
                    return true;
            }
        }
        catch (Exception e)
        {
            UiPlugin.logError(e);
        }
        return false;
    }

    private void error(ExecutionEvent event, String message, Exception exception)
    {
        if (exception != null)
        {
            UiPlugin.logError(exception);
        }
        MessageDialog.openError(HandlerUtil.getActiveShell(event),
            Messages.EditingRestrictionsHandler_Dialog_title, message);
    }

}
