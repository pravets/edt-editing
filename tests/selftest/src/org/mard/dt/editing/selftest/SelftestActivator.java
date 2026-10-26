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

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Runs the editing restriction self test once the configuration model of the
 * project is loaded and writes a report file.
 */
public final class SelftestActivator implements BundleActivator
{

    public static final String PROJECT_PROPERTY = "org.mard.dt.editing.selftest.project"; //$NON-NLS-1$
    public static final String REPORT_PROPERTY = "org.mard.dt.editing.selftest.report"; //$NON-NLS-1$
    public static final String ENABLED_PROPERTY = "org.mard.dt.editing.selftest.enabled"; //$NON-NLS-1$

    @Override
    public void start(BundleContext context) throws Exception
    {
        if (!Boolean.getBoolean(ENABLED_PROPERTY))
        {
            // shortcut: inert unless explicitly enabled via -D (safe in shared installs)
            return;
        }
        log("Self test bundle started"); //$NON-NLS-1$
        new SelftestJob().schedule(15000L);
    }

    @Override
    public void stop(BundleContext context) throws Exception
    {
        // nothing to do
    }

    public static void log(String message)
    {
        Platform.getLog(SelftestActivator.class).log(new Status(IStatus.INFO, "org.mard.dt.editing.selftest", message)); //$NON-NLS-1$
    }

    private static final class SelftestJob extends Job
    {
        private int attempt;

        private SelftestJob()
        {
            super("EDT editing self test");
        }

        @Override
        protected IStatus run(IProgressMonitor monitor)
        {
            String projectName = System.getProperty(PROJECT_PROPERTY, "cf"); //$NON-NLS-1$
            File report = reportFile();
            IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
            try
            {
                if (!project.exists() || !project.isAccessible())
                {
                    return retry("Project " + projectName + " is not available"); //$NON-NLS-1$
                }
                EditingChecker.run(project, report);
                return Status.OK_STATUS;
            }
            catch (Exception e)
            {
                return retry("Self test failed: " + e); //$NON-NLS-1$
            }
        }

        private IStatus retry(String message)
        {
            log(message);
            if (++attempt > 60)
            {
                return new Status(IStatus.ERROR, "org.mard.dt.editing.selftest", message); //$NON-NLS-1$
            }
            schedule(15000L);
            return Status.OK_STATUS;
        }

        private File reportFile()
        {
            String path = System.getProperty(REPORT_PROPERTY);
            if (path != null && !path.isBlank())
            {
                return new File(path);
            }
            return new File(ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile(),
                "edt-editing-selftest.tsv"); //$NON-NLS-1$
        }
    }
}
