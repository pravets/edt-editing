/*******************************************************************************
 * Copyright (C) 2021, 2023, Dmitriy Marmyshev and others.
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
package org.mard.dt.editing.internal;

import java.lang.StackWalker.Option;
import java.lang.StackWalker.StackFrame;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.emf.ecore.EObject;
import org.mard.dt.editing.EditCallKind;
import org.mard.dt.editing.EditingCallContext;
import org.mard.dt.editing.IPathEditingService;

import com._1c.g5.v8.bm.core.IBmEngine;
import com._1c.g5.v8.bm.core.IBmObject;
import com._1c.g5.v8.dt.core.model.EditingMode;
import com._1c.g5.v8.dt.core.model.IModelEditingSupportProvider;
import com._1c.g5.v8.dt.core.platform.IResourceLookup;
import com.google.inject.Inject;

public class PathSettingsEditingSupportProvider
    implements IModelEditingSupportProvider
{

    private static final Set<String> IGNORE_STACK_CALLER_CLASS =
        Set.of("com._1c.g5.v8.dt.internal.md.compare.participant.MdObjectComparisonParticipant"); //$NON-NLS-1$

    private final IResourceLookup resourceLookup;

    private final IPathEditingService editingService;

    @Inject
    public PathSettingsEditingSupportProvider(IResourceLookup resourceLookup, IPathEditingService editingService)
    {
        this.resourceLookup = resourceLookup;
        this.editingService = editingService;
    }

    @Override
    public boolean canDelete(EObject object, EditingMode mode)
    {
        if (mode != EditingMode.DIRECT)
            return true;

        IProject project = resourceLookup.getProject(object);
        if (project == null)
            return true;

        // Deletion is a structural operation: an enabled child of a disabled owner must not be deletable.
        return editingService.canEdit(project, object, EditCallKind.STRUCTURAL);
    }

    @Override
    public boolean canEdit(EObject eObject, EditingMode mode)
    {

        if (mode != EditingMode.DIRECT)
            return true;

        if (eObject instanceof IBmObject)
        {
            IBmEngine engine = ((IBmObject)eObject).bmGetEngine();
            if (engine != null && !engine.isActive())
            {
                return true;
            }
        }

        IProject project = resourceLookup.getProject(eObject);
        if (project == null)
            return true;

        boolean canEdit = editingService.canEdit(project, eObject, callKind());
        debugCall(eObject, mode, canEdit);
        if (!canEdit && editingService.canEditInMerge(project) && isCallFromMerge())
        {
            return true;
        }
        return canEdit;
    }

    /** Detects the kind of the check by the calling stack of EDT. */
    private static EditCallKind callKind()
    {
        List<String> frames = StackWalker.getInstance().walk(s -> s.skip(1).limit(8) //$NON-NLS-1$
            .map(StackFrame::getClassName)
            .collect(Collectors.toList()));

        return EditingCallContext.classify(frames);
    }

    private static final boolean DEBUG_EDITING = Boolean.getBoolean("org.mard.dt.editing.debug"); //$NON-NLS-1$

    /** Log each distinct calling path once, so the log stays readable. */
    private static final Set<String> LOGGED_CALLERS = Collections.synchronizedSet(new LinkedHashSet<>());

    private static void debugCall(EObject eObject, EditingMode mode, boolean canEdit)
    {
        if (!DEBUG_EDITING)
            return;

        String frames = StackWalker.getInstance().walk(s -> s.skip(1).limit(8) //$NON-NLS-1$
            .map(StackFrame::getClassName)
            .collect(Collectors.joining(" <- "))); //$NON-NLS-1$

        String key = eObject.eClass().getName() + "|" + mode + "|" + frames; //$NON-NLS-1$
        if (LOGGED_CALLERS.add(key))
        {
            CorePlugin.log(CorePlugin.createWarningStatus(
                "canEdit call: type=" + eObject.eClass().getName() + ", mode=" + mode //$NON-NLS-1$
                    + ", result=" + canEdit + ", caller=" + frames)); //$NON-NLS-1$
        }
    }

    private boolean isCallFromMerge()
    {
        StackWalker walker = StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE);
        //@formatter:off
        Optional<String> callerClass = walker.walk(s ->
            s.limit(10)
            .map(StackFrame::getClassName)
            .filter(IGNORE_STACK_CALLER_CLASS::contains)
            .findFirst());
        //@formatter:on

        return callerClass.isPresent();
    }
}
