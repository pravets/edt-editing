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
     * Creation of new objects is a structural operation: it must not be allowed by an enabled child of a disabled
     * owner, so the check of the owner must ignore the rule that opens a top object with enabled children.
     *
     * @param frames class names of the calling stack.
     * @return true, if the call comes from a creation of new objects.
     */
    public static boolean isStructuralCall(List<String> frames)
    {
        return frames.stream().anyMatch(frame -> frame.contains("NewActionProvider") //$NON-NLS-1$
            || frame.contains("md.ui.wizards")); //$NON-NLS-1$
    }

}
