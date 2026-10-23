/*******************************************************************************
 * Copyright (c) 2026 Mard. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.mard.dt.editing.internal.ui;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.emf.ecore.EObject;
import com._1c.g5.v8.dt.metadata.mdclass.BasicForm;

/**
 * Tests that the selected metadata object has the requested module feature,
 * e.g. objectModule, managerModule, recordSetModule or module.
 */
public class HasModulePropertyTester extends PropertyTester
{

    @Override
    public boolean test(Object receiver, String property, Object[] args, Object expectedValue)
    {
        if (!(receiver instanceof EObject))
            return false;
        if (args == null || args.length == 0)
            return false;

        String feature = String.valueOf(args[0]);
        EObject object = (EObject)receiver;
        // mdclass form nodes have no own 'module' feature; the module lives on getForm()
        if ("module".equals(feature) && object instanceof BasicForm) //$NON-NLS-1$
            return true;

        return object.eClass().getEStructuralFeature(feature) != null;
    }

}
