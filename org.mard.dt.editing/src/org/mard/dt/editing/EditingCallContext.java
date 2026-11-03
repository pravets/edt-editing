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
package org.mard.dt.editing;

import java.util.List;

/**
 * Helpers to detect the context of an editing check.
 */
public final class EditingCallContext
{

    private EditingCallContext()
    {
        // static helper
    }

    /**
     * Structural modifications (deletion, moving, sorting, editing of owner properties) are not allowed by an
     * enabled child of a disabled owner, so the check of the owner must ignore such rules.
     *
     * @param frames class names of the calling stack.
     * @return true, if the call comes from a structural modification.
     */
    public static boolean isStructuralCall(List<String> frames)
    {
        return classify(frames) == EditCallKind.STRUCTURAL;
    }

    /**
     * Detects the kind of the editing check by the calling stack of EDT.
     *
     * @param frames class names of the calling stack.
     * @return the kind of the call.
     */
    public static EditCallKind classify(List<String> frames)
    {
        if (frames.stream().anyMatch(frame -> frame.contains("NewActionProvider") //$NON-NLS-1$
            || frame.contains("md.ui.wizards") //$NON-NLS-1$
            || frame.contains("DtWizardAction"))) //$NON-NLS-1$
        {
            // Creation of a new child: it is allowed, if the container of the new child is enabled.
            return EditCallKind.CREATE;
        }

        if (frames.stream().anyMatch(frame -> frame.contains("DtDeleteAction") //$NON-NLS-1$
            || frame.contains("DtMoveUpAction") //$NON-NLS-1$
            || frame.contains("DtMoveDownAction") //$NON-NLS-1$
            || frame.contains("DtSortAction") //$NON-NLS-1$
            || frame.contains("md.help.ui.editor") //$NON-NLS-1$
            || frame.contains("html.ui.editor"))) //$NON-NLS-1$
        {
            return EditCallKind.STRUCTURAL;
        }

        if (frames.stream().anyMatch(frame -> frame.contains("aef2.standard.definitions"))) //$NON-NLS-1$
        {
            // The properties editor uses the same model factories as the content editor, but it edits
            // the owner itself, so it must not open a disabled owner by its enabled children.
            if (frames.stream().noneMatch(frame -> frame.contains("md.ui.properties"))) //$NON-NLS-1$
            {
                return EditCallKind.CONTENT;
            }
        }

        return EditCallKind.OTHER;
    }

}
