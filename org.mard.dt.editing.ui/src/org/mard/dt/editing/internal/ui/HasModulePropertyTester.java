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

/**
 * Tests that the selected metadata object has the requested module feature,
 * e.g. objectModule, managerModule, recordSetModule or module.
 */
public class HasModulePropertyTester extends PropertyTester
{

    @Override
    public boolean test(Object receiver, String property, Object[] args, Object expectedValue)
    {
        if (!(receiver instanceof EObject) || args == null || args.length == 0)
            return false;

        return ((EObject)receiver).eClass().getEStructuralFeature(String.valueOf(args[0])) != null;
    }

}
