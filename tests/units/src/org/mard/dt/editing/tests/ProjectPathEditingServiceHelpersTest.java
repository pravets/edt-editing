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

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.junit.Test;

import com._1c.g5.v8.dt.core.filesystem.IProjectFileSystemSupport;

import org.mard.dt.editing.EditCallKind;
import org.mard.dt.editing.EditingCallContext;
import org.mard.dt.editing.internal.ProjectPathEditingService;

/**
 * Tests for {@link ProjectPathEditingService} helper logic.
 */
public class ProjectPathEditingServiceHelpersTest
{

    @Test
    public void testIsEnabledByAncestor()
    {
        Set<String> enabled = Set.of( //
            "Task.ЗадачаИсполнителя.ТабличнаяЧасть.TabularSection", //$NON-NLS-1$
            "Catalog.Products"); //$NON-NLS-1$

        // an enabled tabular section opens its attributes
        assertTrue(ProjectPathEditingService.isEnabledByAncestor(enabled, //
            "Task.ЗадачаИсполнителя.ТабличнаяЧасть.TabularSection.Реквизит.Attribute")); //$NON-NLS-1$
        // an enabled object opens its content
        assertTrue(ProjectPathEditingService.isEnabledByAncestor(enabled, //
            "Catalog.Products.Goods.TabularSection")); //$NON-NLS-1$
        // unrelated objects stay disabled
        assertFalse(ProjectPathEditingService.isEnabledByAncestor(enabled, //
            "Task.ДругаяЗадача.Реквизит.Attribute")); //$NON-NLS-1$
        assertFalse(ProjectPathEditingService.isEnabledByAncestor(enabled, //
            "Catalog.ProductsExtra.Goods.TabularSection")); //$NON-NLS-1$
        // the object itself is not an ancestor of itself
        assertFalse(ProjectPathEditingService.isEnabledByAncestor(enabled, //
            "Task.ЗадачаИсполнителя.ТабличнаяЧасть.TabularSection")); //$NON-NLS-1$
        // empty cases
        assertFalse(ProjectPathEditingService.isEnabledByAncestor(Set.of(), //$NON-NLS-1$
            "Catalog.Products.Goods.TabularSection")); //$NON-NLS-1$
        assertFalse(ProjectPathEditingService.isEnabledByAncestor(null, //$NON-NLS-1$
            "Catalog.Products.Goods.TabularSection")); //$NON-NLS-1$
        assertFalse(ProjectPathEditingService.isEnabledByAncestor(enabled, null)); //$NON-NLS-1$
        assertFalse(ProjectPathEditingService.isEnabledByAncestor(enabled, " ")); //$NON-NLS-1$
    }

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
        assertTrue(ProjectPathEditingService.isSubObjectFqn("ExternalDataSource.Source.Table.Table1.Field1.Field"));
        assertTrue(ProjectPathEditingService.isSubObjectFqn("ExternalDataSource.Source.Cube.Cube1.Dim1.Dimension"));
        assertTrue(ProjectPathEditingService.isSubObjectFqn("ExternalDataSource.Source.Cube.Cube1.Res1.Resource"));
        assertTrue(ProjectPathEditingService.isSubObjectFqn("ExternalDataSource.Source.Cube.Cube1.DimensionTable.Dim"));
        // nested tabular section attribute
        assertTrue(ProjectPathEditingService.isSubObjectFqn("Catalog.Products.Goods.TabularSection.Name.Attribute"));
        // templates
        assertTrue(ProjectPathEditingService.isSubObjectFqn("ExternalDataSource.Source.Cube.Cube1.MyTemplate.Template"));
        assertTrue(ProjectPathEditingService.isSubObjectFqn("Catalog.Products.MyTemplate.Template"));
        // case-sensitive markers
        assertFalse(ProjectPathEditingService.isSubObjectFqn("catalog.x.name.attribute"));
    }

    @Test
    public void testStripMarkers()
    {
        assertEquals("ExternalDataSource.S.C.T",
            ProjectPathEditingService.stripMarkers("ExternalDataSource.S.Cube.C.T.Template"));
        assertEquals("ExternalDataSource.S.C.T",
            ProjectPathEditingService.stripMarkers("ExternalDataSource.S.Cube.C.Template.T"));
        assertEquals("Catalog.Products.Barcode",
            ProjectPathEditingService.stripMarkers("Catalog.Products.Barcode.Attribute"));
        assertEquals("Catalog.Products.Goods.Name",
            ProjectPathEditingService.stripMarkers("Catalog.Products.Goods.TabularSection.Name.Attribute"));
        assertEquals("Catalog.Products",
            ProjectPathEditingService.stripMarkers("Catalog.Products"));
        assertNull(ProjectPathEditingService.stripMarkers(null));
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

    @Test
    public void testMatchesEnablePattern()
    {
        // file level rules match exactly, never their siblings
        assertTrue(ProjectPathEditingService.matchesEnablePattern(
            "ExternalDataSource.ВнешнийИсточникДанных.Cube.Куб.Form.ФормаЗаписи.Form",
            "ExternalDataSource.ВнешнийИсточникДанных.Cube.Куб.Form.ФормаЗаписи.Form"));
        assertFalse(ProjectPathEditingService.matchesEnablePattern(
            "ExternalDataSource.ВнешнийИсточникДанных.Cube.Куб.Form.ФормаЗаписи.Form",
            "ExternalDataSource.ВнешнийИсточникДанных.Cube.Куб.Form.ФормаЗаписи.Form.Module"));
        assertTrue(ProjectPathEditingService.matchesEnablePattern(
            "ExternalDataSource.ВнешнийИсточникДанных.Cube.Куб.Form.ФормаЗаписи.Form.Module",
            "ExternalDataSource.ВнешнийИсточникДанных.Cube.Куб.Form.ФормаЗаписи.Form.Module"));
        assertFalse(ProjectPathEditingService.matchesEnablePattern(
            "ExternalDataSource.ВнешнийИсточникДанных.Cube.Куб.Form.ФормаЗаписи.Form.Module",
            "ExternalDataSource.ВнешнийИсточникДанных.Cube.Куб.Form.ФормаЗаписи.Form"));
        // ordinary rules still cover children
        assertTrue(ProjectPathEditingService.matchesEnablePattern("Catalog.Products",
            "Catalog.Products.Form.ItemForm.Form"));
        // marker order is normalised
        assertTrue(ProjectPathEditingService.matchesEnablePattern("Catalog.Products.Attribute.Barcode",
            "Catalog.Products.Barcode.Attribute"));
        assertFalse(ProjectPathEditingService.matchesEnablePattern(null, "Catalog.Products"));
        assertFalse(ProjectPathEditingService.matchesEnablePattern(" ", "Catalog.Products"));
        assertFalse(ProjectPathEditingService.matchesEnablePattern("Catalog.Products", null));
    }

    @Test
    public void testIsStructuralCall()
    {
        assertTrue(EditingCallContext.isStructuralCall(Arrays.asList(
            "com._1c.g5.v8.dt.ui.aef.actions.DtDeleteAction")));
        assertTrue(EditingCallContext.isStructuralCall(Arrays.asList(
            "com._1c.g5.v8.dt.md.help.ui.editor.MdHelpContentEditor")));
        // creation is a separate kind: it is allowed inside an enabled container
        assertFalse(EditingCallContext.isStructuralCall(Arrays.asList(
            "com._1c.g5.v8.dt.navigator.ui.actions.NewActionProvider")));
        assertFalse(EditingCallContext.isStructuralCall(Arrays.asList(
            "com._1c.g5.v8.dt.md.ui.wizards.base.aef.MdModelNewWizardPageComponent")));
        assertFalse(EditingCallContext.isStructuralCall(Arrays.asList(
            "com._1c.g5.v8.dt.navigator.ui.actions.OpenEditorAction")));
        assertFalse(EditingCallContext.isStructuralCall(Arrays.asList()));
    }

    @Test
    public void testClassifyCallKind()
    {
        assertEquals(EditCallKind.STRUCTURAL, EditingCallContext.classify(Arrays.asList(
            "com._1c.g5.v8.dt.ui.aef.actions.DtDeleteAction",
            "com._1c.g5.v8.dt.ui.editor.aef.components.DtGranularEditorPageSectionComponent")));
        assertEquals(EditCallKind.CREATE, EditingCallContext.classify(Arrays.asList(
            "com._1c.g5.v8.dt.ui.aef.actions.DtWizardAction")));
        // creation of a new child is allowed inside an enabled container
        assertEquals(EditCallKind.CREATE, EditingCallContext.classify(Arrays.asList(
            "com._1c.g5.v8.dt.navigator.ui.actions.NewActionProvider")));
        assertEquals(EditCallKind.CREATE, EditingCallContext.classify(Arrays.asList(
            "com._1c.g5.v8.dt.md.ui.wizards.base.aef.MdModelNewWizardPageComponent")));
        assertEquals(EditCallKind.STRUCTURAL, EditingCallContext.classify(Arrays.asList(
            "com._1c.g5.v8.dt.md.help.ui.editor.MdHelpContentEditor",
            "com._1c.g5.v8.dt.html.ui.editor.HtmlMultiPageEditor")));
        assertEquals(EditCallKind.CONTENT, EditingCallContext.classify(Arrays.asList(
            "com._1c.g5.v8.dt.md.ui.aef.models.SelectionModelFactory",
            "com._1c.g5.aef2.standard.definitions.models.AbstractFeatureFieldModelFactory")));
        assertEquals(EditCallKind.OTHER, EditingCallContext.classify(Arrays.asList(
            "com._1c.g5.v8.dt.internal.md.ui.AvailabilityPropertyTester")));
        // the properties editor edits the owner itself, so it is not a content call
        assertEquals(EditCallKind.OTHER, EditingCallContext.classify(Arrays.asList(
            "com._1c.g5.v8.dt.md.ui.properties.models.ModelFactory",
            "com._1c.g5.aef2.standard.definitions.models.AbstractFeatureFieldModelFactory")));
        assertEquals(EditCallKind.OTHER, EditingCallContext.classify(Arrays.asList()));
    }

    @Test
    public void testSubObjectFqnDoesNotCoverOwner()
    {
        // a rule for an enabled child does not enable its owner: the owner is decided by paths
        assertTrue(ProjectPathEditingService.isSubObjectFqn(
            "ExternalDataSource.Source.Cube.Cube1.DimensionTable.Dim.MyField.Field"));
        assertFalse(ProjectPathEditingService.isSubObjectFqn("Catalog.Products"));
        assertFalse(ProjectPathEditingService.isSubObjectFqn("Catalog.Products.Goods"));
    }

    @Test
    public void testResolvePathFallsBackToAncestor()
    {
        IPath ownerPath = new Path("/src/Catalogs/Products/Products.mdo");
        IPath ownPath = new Path("/src/Catalogs/Products/Products.mdo");
        EObject owner = stub(null);
        EObject child = stub(owner);

        IProjectFileSystemSupport fileSystemSupport = new IProjectFileSystemSupport()
        {
            @Override
            public IFile getFile(EObject object)
            {
                return null;
            }

            @Override
            public IPath getPath(EObject object)
            {
                if (object == owner)
                    return ownerPath;
                if (object == child)
                    return ownPath;
                return null;
            }

            @Override
            public IPath getPath(EObject object, EReference reference)
            {
                return getPath(object);
            }

            @Override
            public IFile getFile(EObject object, EReference reference)
            {
                return null;
            }
        };

        assertEquals(ownPath, ProjectPathEditingService.resolvePath(fileSystemSupport, child));

        // an object without its own resource inherits the path of the closest ancestor
        EObject orphan = stub(owner);
        assertEquals(ownerPath, ProjectPathEditingService.resolvePath(fileSystemSupport, orphan));

        // no path at all
        EObject detached = stub(null);
        assertNull(ProjectPathEditingService.resolvePath(fileSystemSupport, detached));
        assertNull(ProjectPathEditingService.resolvePath(null, detached));
    }

    private static EObject stub(EObject container)
    {
        return (EObject)Proxy.newProxyInstance(EObject.class.getClassLoader(),
            new Class<?>[] {EObject.class}, (proxy, method, args) -> {
                switch (method.getName())
                {
                    case "eContainer":
                        return container;
                    case "eIsProxy":
                        return Boolean.FALSE;
                    case "toString":
                        return "stub";
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return proxy == args[0];
                    default:
                        return null;
                }
            });
    }
}
