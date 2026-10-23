/*******************************************************************************
 * Copyright (C) 2026.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.mard.dt.editing.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.LinkedList;

import org.eclipse.core.runtime.Path;
import org.junit.Test;

import org.mard.dt.editing.internal.ProjectPathEditingService;

/**
 * Tests for {@link ProjectPathEditingService} helper logic.
 */
public class ProjectPathEditingServiceHelpersTest
{

    @Test
    public void testIsSubObjectFqnMarkers()
    {
        assertTrue(ProjectPathEditingService.isSubObjectFqn("Catalog.Products.Barcode.Attribute"));
        assertTrue(ProjectPathEditingService.isSubObjectFqn("Catalog.Products.Goods.TabularSection"));
        assertTrue(ProjectPathEditingService.isSubObjectFqn("Enum.Genders.Male.Value"));
        assertTrue(ProjectPathEditingService.isSubObjectFqn("DocumentJournal.Journal.Column1.Column"));
        assertTrue(ProjectPathEditingService.isSubObjectFqn("Task.Tasks.MyCommand.Command"));
        // canonical form used by BM for top-level EDS tables/cubes
        assertTrue(ProjectPathEditingService.isSubObjectFqn("ExternalDataSource.Source.Cube.Cube1"));
        assertTrue(ProjectPathEditingService.isSubObjectFqn("ExternalDataSource.Source.Table.Table1"));
        assertTrue(ProjectPathEditingService.isSubObjectFqn("ExternalDataSource.Source.Table.Table"));
        assertTrue(ProjectPathEditingService.isSubObjectFqn("ExternalDataSource.Source.Cube.Cube"));
        assertTrue(ProjectPathEditingService.isSubObjectFqn("ExternalDataSource.Source.Function.Function"));
        assertTrue(ProjectPathEditingService.isSubObjectFqn("ExternalDataSource.Source.Cube.Cube1.DimensionTable.Dim"));
        // nested tabular section attribute
        assertTrue(ProjectPathEditingService.isSubObjectFqn("Catalog.Products.Goods.TabularSection.Name.Attribute"));
        // case-sensitive markers
        assertFalse(ProjectPathEditingService.isSubObjectFqn("catalog.x.name.attribute"));
    }

    @Test
    public void testIsSubObjectFqnNonSubObjects()
    {
        assertFalse(ProjectPathEditingService.isSubObjectFqn("Catalog.Products"));
        assertFalse(ProjectPathEditingService.isSubObjectFqn("Catalog.Products.Form.ItemForm"));
        assertFalse(ProjectPathEditingService.isSubObjectFqn("CommonModule.My"));
        assertFalse(ProjectPathEditingService.isSubObjectFqn("Attribute"));
        assertFalse(ProjectPathEditingService.isSubObjectFqn("x.Attribute"));
        assertFalse(ProjectPathEditingService.isSubObjectFqn(null));
        assertFalse(ProjectPathEditingService.isSubObjectFqn(""));
    }

    @Test
    public void testBuildFullName()
    {
        assertEquals("Catalog.Products.Barcode.Attribute",
            ProjectPathEditingService.buildFullName("Catalog.Products", Arrays.asList("Barcode", "Attribute")));
        assertNull(ProjectPathEditingService.buildFullName(null, Arrays.asList("A", "Attribute")));
        assertNull(ProjectPathEditingService.buildFullName("  ", Arrays.asList("A", "Attribute")));
        assertNull(ProjectPathEditingService.buildFullName("Catalog.Products", new LinkedList<>()));
    }

    @Test
    public void testModuleFilePath()
    {
        assertEquals(new Path("Catalogs/Products/Forms/ItemForm/Module.bsl"),
            ProjectPathEditingService.moduleFilePath(new Path("Catalogs/Products/Forms/ItemForm/Form.form")));
        assertEquals(new Path("CommonModules/My/Module.bsl"),
            ProjectPathEditingService.moduleFilePath(new Path("CommonModules/My/Module.bsl")));
        assertNull(ProjectPathEditingService.moduleFilePath(null));
    }
}
