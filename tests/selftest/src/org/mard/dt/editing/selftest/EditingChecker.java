/*******************************************************************************
 * Copyright (c) 2026 mard
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.mard.dt.editing.selftest;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

import com._1c.g5.v8.bm.core.IBmEngine;
import com._1c.g5.v8.bm.core.IBmObject;
import com._1c.g5.v8.bm.core.IBmTransaction;
import com._1c.g5.v8.bm.integration.IBmModel;
import com._1c.g5.v8.dt.core.filesystem.IProjectFileSystemSupport;
import com._1c.g5.v8.dt.core.filesystem.IProjectFileSystemSupportProvider;
import com._1c.g5.v8.dt.core.model.EditingMode;
import com._1c.g5.v8.dt.core.model.IModelEditingSupportProvider;
import com._1c.g5.v8.dt.core.platform.IBmModelManager;
import com.google.inject.Injector;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.IConfigurationElement;
import org.mard.dt.editing.EditCallKind;
import org.mard.dt.editing.IPathEditingService;

/**
 * Walks all metadata objects of a project and records what the editing
 * restriction plugin answers for every object, member and call kind.
 */
public final class EditingChecker
{

    private EditingChecker()
    {
        // utility class
    }

    public static void run(IProject project, File report) throws Exception
    {
        BundleContext context = FrameworkUtil.getBundle(EditingChecker.class).getBundleContext();
        Injector injector = coreInjector(context);
        IPathEditingService service = injector.getInstance(IPathEditingService.class);
        IProjectFileSystemSupportProvider fileSystemSupportProvider =
            injector.getInstance(IProjectFileSystemSupportProvider.class);
        IBmModelManager modelManager = injector.getInstance(IBmModelManager.class);
        IModelEditingSupportProvider provider = modelEditingProvider(injector);

        List<String[]> rows = new ArrayList<>();
        IBmModel model = modelManager.getModel(project);
        if (model == null)
        {
            throw new IllegalStateException("No model for project " + project.getName());
        }
        IBmEngine engine = model.getEngine();
        IBmTransaction transaction = engine.beginReadOnlyTransaction();
        try
        {
            for (Iterator<IBmObject> objects = transaction.getTopObjectIterator(); objects.hasNext();)
            {
                EObject top = objects.next();
                rows.add(row(top, project, service, fileSystemSupportProvider, provider));
                java.util.Iterator<?> children = (java.util.Iterator<?>) EcoreUtil.getAllContents(List.of(top));
                while (children.hasNext())
                {
                    Object child = children.next();
                    rows.add(row((EObject) child, project, service, fileSystemSupportProvider, provider));
                }
            }
        }
        finally
        {
            transaction.rollback();
        }
        collectBsl(project, service, rows);
        write(report, project, rows);
    }

    private static void collectBsl(IProject project, IPathEditingService service, List<String[]> rows)
        throws CoreException
    {
        // Form and command modules are not BM top objects: check them by path,
        // the way the editor consults editing for module text.
        project.accept(resource -> {
            if (resource.getType() == IResource.FILE && "bsl".equals(resource.getFileExtension())) //$NON-NLS-1$
            {
                String relative = resource.getProjectRelativePath().toString();
                boolean editable = service.canEdit(project, resource.getProjectRelativePath());
                rows.add(new String[] { "BslModule", "", "", relative,
                    String.valueOf(editable), String.valueOf(editable), String.valueOf(editable),
                    String.valueOf(editable), String.valueOf(editable) });
            }
            return true;
        });
    }

    private static String[] row(EObject object, IProject project, IPathEditingService service,
        IProjectFileSystemSupportProvider fileSystemSupportProvider, IModelEditingSupportProvider provider)
    {
        return new String[] {
            type(object),
            bmFqn(object),
            String.valueOf(service.getObjectFullName(object)),
            path(fileSystemSupportProvider, project, object),
            String.valueOf(providerCanEdit(provider, object)),
            String.valueOf(service.canEdit(project, object, EditCallKind.CONTENT)),
            String.valueOf(service.canEdit(project, object, EditCallKind.CREATE)),
            String.valueOf(service.canEdit(project, object, EditCallKind.STRUCTURAL)),
            String.valueOf(service.canEdit(project, object, EditCallKind.OTHER)) };
    }

    private static String type(EObject object)
    {
        return object.eClass().getName();
    }

    private static String bmFqn(EObject object)
    {
        if (object instanceof IBmObject bmObject)
        {
            try
            {
                return String.valueOf(bmObject.bmGetFqn());
            }
            catch (RuntimeException e)
            {
                return "<error>";
            }
        }
        return "";
    }

    private static String path(IProjectFileSystemSupportProvider provider, IProject project, EObject object)
    {
        try
        {
            IProjectFileSystemSupport support = provider.getProjectFileSystemSupport(project);
            if (support == null)
            {
                return "";
            }
            return String.valueOf(support.getPath(object));
        }
        catch (RuntimeException e)
        {
            return "<error>";
        }
    }

    private static String providerCanEdit(IModelEditingSupportProvider provider, EObject object)
    {
        try
        {
            return String.valueOf(provider.canEdit(object, EditingMode.DIRECT));
        }
        catch (RuntimeException e)
        {
            return "error:" + e.getClass().getSimpleName();
        }
    }

    private static Bundle coreBundle(BundleContext context)
    {
        Bundle core = context.getBundle("org.mard.dt.editing"); //$NON-NLS-1$
        if (core != null)
        {
            return core;
        }
        for (Bundle candidate : context.getBundles())
        {
            if ("org.mard.dt.editing".equals(candidate.getSymbolicName())) //$NON-NLS-1$
            {
                return candidate;
            }
        }
        return null;
    }

    private static Injector coreInjector(BundleContext context) throws Exception
    {
        Bundle core = coreBundle(context);
        if (core == null)
        {
            throw new IllegalStateException("Bundle org.mard.dt.editing is not installed");
        }
        if (core.getState() != Bundle.ACTIVE)
        {
            try
            {
                core.start();
            }
            catch (Exception e)
            {
                throw new IllegalStateException("Cannot start bundle " + core.getSymbolicName(), e);
            }
        }
        Class<?> pluginClass = core.loadClass("org.mard.dt.editing.internal.CorePlugin");
        Object plugin = pluginClass.getMethod("getDefault").invoke(null);
        Method injectorMethod = pluginClass.getDeclaredMethod("getInjector");
        injectorMethod.setAccessible(true);
        return (Injector) injectorMethod.invoke(plugin);
    }

    private static IModelEditingSupportProvider modelEditingProvider(Injector injector) throws Exception
    {
        Bundle core = coreBundle(FrameworkUtil.getBundle(EditingChecker.class).getBundleContext());
        if (core == null)
        {
            throw new IllegalStateException("Bundle org.mard.dt.editing is not installed");
        }
        Class<?> providerClass = core.loadClass("org.mard.dt.editing.internal.PathSettingsEditingSupportProvider"); //$NON-NLS-1$
        for (java.lang.reflect.Constructor<?> constructor : providerClass.getConstructors())
        {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length != 2)
            {
                continue;
            }
            Object[] arguments = new Object[2];
            for (int i = 0; i < 2; i++)
            {
                arguments[i] = injector.getInstance(parameters[i]);
            }
            Object created = constructor.newInstance(arguments);
            if (created instanceof IModelEditingSupportProvider modelProvider)
            {
                return modelProvider;
            }
        }
        throw new IllegalStateException("Cannot create model editing provider");
    }

    private static void write(File report, IProject project, List<String[]> rows) throws IOException
    {
        File parent = report.getParentFile();
        if (parent != null)
        {
            parent.mkdirs();
        }
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(report.toPath(), StandardCharsets.UTF_8)))
        {
            writer.println("# project\t" + project.getName());
            writer.println("# type\tbmFqn\tourFqn\tpath\tprovider\tcontent\tcreate\tstructural\tother");
            for (String[] row : rows)
            {
                writer.println(String.join("\t", row));
            }
        }
    }

    @SuppressWarnings("unused")
    private static IProgressMonitor monitor()
    {
        return new NullProgressMonitor();
    }
}
