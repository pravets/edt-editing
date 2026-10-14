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
package org.mard.dt.editing.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import org.mard.dt.editing.EditingSettingsYamlUpdater;

/**
 * Tests of {@link EditingSettingsYamlUpdater}.
 */
public class EditingSettingsYamlUpdaterTest
{

    private static final String FQN = "Catalog.Products.ObjectModule"; //$NON-NLS-1$

    @Test
    public void testCreateMinimalDisableDocument()
    {
        String expected = "disable:\n  fullname:\n    - " + FQN + "\n"; //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(expected, EditingSettingsYamlUpdater.appendFullname(null, "disable", FQN)); //$NON-NLS-1$
        assertEquals(expected, EditingSettingsYamlUpdater.appendFullname("   \n", "disable", FQN)); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testCreateMinimalEnableDocument()
    {
        String expected = "enable:\n  fullname:\n    - " + FQN + "\n"; //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(expected, EditingSettingsYamlUpdater.appendFullname(null, "enable", FQN)); //$NON-NLS-1$
    }

    @Test
    public void testAppendToExistingListKeepsComments()
    {
        String content = """
            # section allows to disable editing in UI
            disable:
              # List of project relative path to file or to folder
              path:
                - src/CommonModules/Locked/Module.bsl

              # Full qualified name of top objects (resources)
              fullname:
                - Catalog.Products.Form.ItemForm.Form

              # List of branches which content should be disabled
              branch:
                - vendor
            """; //$NON-NLS-1$

        String updated = EditingSettingsYamlUpdater.appendFullname(content, "disable", FQN);

        String expected = """
            # section allows to disable editing in UI
            disable:
              # List of project relative path to file or to folder
              path:
                - src/CommonModules/Locked/Module.bsl

              # Full qualified name of top objects (resources)
              fullname:
                - Catalog.Products.Form.ItemForm.Form
                - Catalog.Products.ObjectModule

              # List of branches which content should be disabled
              branch:
                - vendor
            """; //$NON-NLS-1$
        assertEquals(expected, updated);
    }

    @Test
    public void testAddFullnameToSectionWithoutIt()
    {
        String content = """
            disable:
              path:
                - src/CommonModules/Locked
                - src/Catalogs/Products
            """; //$NON-NLS-1$

        String expected = """
            disable:
              path:
                - src/CommonModules/Locked
                - src/Catalogs/Products
              fullname:
                - %s
            """.formatted(FQN); //$NON-NLS-1$

        assertEquals(expected, EditingSettingsYamlUpdater.appendFullname(content, "disable", FQN)); //$NON-NLS-1$
    }

    @Test
    public void testAddMissingRootSection()
    {
        String content = """
            disable:
              fullname:
                - Catalog.Products
            """; //$NON-NLS-1$

        String expected = """
            disable:
              fullname:
                - Catalog.Products

            enable:
              fullname:
                - %s
            """.formatted(FQN); //$NON-NLS-1$

        assertEquals(expected, EditingSettingsYamlUpdater.appendFullname(content, "enable", FQN)); //$NON-NLS-1$
    }

    @Test
    public void testDuplicateIsNoOp()
    {
        String content = """
            disable:
              fullname:
                - Catalog.Products
                - %s
            """.formatted(FQN); //$NON-NLS-1$

        assertSame(content, EditingSettingsYamlUpdater.appendFullname(content, "disable", FQN)); //$NON-NLS-1$
    }

    @Test
    public void testUnknownFieldsPreserved()
    {
        String content = """
            disable:
              subsystem:
                - DisabledSubsystem
              fullname:
                - Catalog.Products
              customUnknownField:
                key: value
            """; //$NON-NLS-1$

        String expected = """
            disable:
              subsystem:
                - DisabledSubsystem
              fullname:
                - Catalog.Products
                - %s
              customUnknownField:
                key: value
            """.formatted(FQN); //$NON-NLS-1$

        assertEquals(expected, EditingSettingsYamlUpdater.appendFullname(content, "disable", FQN)); //$NON-NLS-1$
    }

    @Test
    public void testCrlfPreserved()
    {
        String content = "disable:\r\n  fullname:\r\n    - Catalog.Products\r\n"; //$NON-NLS-1$

        String expected = "disable:\r\n  fullname:\r\n    - Catalog.Products\r\n    - " + FQN + "\r\n"; //$NON-NLS-1$ //$NON-NLS-2$

        assertEquals(expected, EditingSettingsYamlUpdater.appendFullname(content, "disable", FQN)); //$NON-NLS-1$
    }

    @Test
    public void testInvalidYamlThrows()
    {
        assertThrows(IllegalArgumentException.class,
            () -> EditingSettingsYamlUpdater.appendFullname("disable: [unclosed", "disable", FQN)); //$NON-NLS-1$ //$NON-NLS-2$

        assertThrows(IllegalArgumentException.class,
            () -> EditingSettingsYamlUpdater.appendFullname("- just\n- scalars\n", "disable", FQN)); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testFlowStyleFullnameThrows()
    {
        String flow = "disable:\n  fullname: [Catalog.Products, Catalog.Clients]\n"; //$NON-NLS-1$
        assertThrows(IllegalArgumentException.class, () -> EditingSettingsYamlUpdater.appendFullname(flow, "disable", FQN)); //$NON-NLS-1$

        String emptyFlow = "disable:\n  fullname: []\n"; //$NON-NLS-1$
        assertThrows(IllegalArgumentException.class, () -> EditingSettingsYamlUpdater.appendFullname(emptyFlow, "disable", FQN)); //$NON-NLS-1$
    }

    @Test
    public void testNonListFullnameThrows()
    {
        String content = "disable:\n  fullname: Catalog.Products\n"; //$NON-NLS-1$
        assertThrows(IllegalArgumentException.class, () -> EditingSettingsYamlUpdater.appendFullname(content, "disable", FQN)); //$NON-NLS-1$
    }

    @Test
    public void testWrongArgumentsThrows()
    {
        assertThrows(IllegalArgumentException.class, () -> EditingSettingsYamlUpdater.appendFullname(null, "wrong", FQN)); //$NON-NLS-1$
        assertThrows(IllegalArgumentException.class, () -> EditingSettingsYamlUpdater.appendFullname(null, "enable", " ")); //$NON-NLS-1$ //$NON-NLS-2$
    }

}
