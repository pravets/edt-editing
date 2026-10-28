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

import static com._1c.g5.v8.dt.metadata.mdclass.MdClassPackage.Literals.SUBSYSTEM;

import java.text.MessageFormat;
import java.io.IOException;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.xtext.naming.QualifiedName;
import org.mard.dt.editing.EditCallKind;
import org.mard.dt.editing.EditingSettings;
import org.mard.dt.editing.EditingSettingsContent;
import org.mard.dt.editing.EditingSettingsYamlReader;
import org.mard.dt.editing.IPathEditingService;

import com._1c.g5.v8.bm.core.IBmObject;
import com._1c.g5.v8.bm.core.IBmTransaction;
import com._1c.g5.v8.bm.integration.AbstractBmTask;
import com._1c.g5.v8.bm.integration.IBmModel;
import com._1c.g5.v8.dt.bsl.model.Module;
import com._1c.g5.v8.dt.common.git.GitUtils;
import com._1c.g5.v8.dt.core.filesystem.IProjectFileSystemSupport;
import com._1c.g5.v8.dt.core.filesystem.IProjectFileSystemSupportProvider;
import com._1c.g5.v8.dt.core.filesystem.IQualifiedNameFilePathConverter;
import com._1c.g5.v8.dt.core.lifecycle.ProjectContext;
import com._1c.g5.v8.dt.core.platform.IBmModelManager;
import com._1c.g5.v8.dt.core.platform.IDtProject;
import com._1c.g5.v8.dt.lifecycle.LifecycleParticipant;
import com._1c.g5.v8.dt.lifecycle.LifecyclePhase;
import com._1c.g5.v8.dt.lifecycle.LifecycleService;
import com._1c.g5.v8.dt.metadata.mdclass.BasicCommand;
import com._1c.g5.v8.dt.metadata.mdclass.BasicFeature;
import com._1c.g5.v8.dt.metadata.mdclass.BasicForm;
import com._1c.g5.v8.dt.metadata.mdclass.BasicTabularSection;
import com._1c.g5.v8.dt.metadata.mdclass.Column;
import com._1c.g5.v8.dt.metadata.mdclass.Cube;
import com._1c.g5.v8.dt.metadata.mdclass.Dimension;
import com._1c.g5.v8.dt.metadata.mdclass.DimensionTable;
import com._1c.g5.v8.dt.metadata.mdclass.EnumValue;
import com._1c.g5.v8.dt.metadata.mdclass.Field;
import com._1c.g5.v8.dt.metadata.mdclass.Function;
import com._1c.g5.v8.dt.metadata.mdclass.Method;
import com._1c.g5.v8.dt.metadata.mdclass.MdObject;
import com._1c.g5.v8.dt.metadata.mdclass.Operation;
import com._1c.g5.v8.dt.metadata.mdclass.Parameter;
import com._1c.g5.v8.dt.metadata.mdclass.Recalculation;
import com._1c.g5.v8.dt.metadata.mdclass.Resource;
import com._1c.g5.v8.dt.metadata.mdclass.Subsystem;
import com._1c.g5.v8.dt.metadata.mdclass.Table;
import com._1c.g5.v8.dt.metadata.mdclass.Template;
import com._1c.g5.v8.dt.metadata.mdclass.URLTemplate;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@LifecycleService(name = "ProjectPathEditingService")
@Singleton
public class ProjectPathEditingService
    implements IPathEditingService
{

    private static final int TOP_OBJECT_FIRST_SEGMENTS = 3;

    private static final IPath SUBSYSTEM_PATH = new Path("src/Subsystems"); //$NON-NLS-1$

    private final IProjectFileSystemSupportProvider fileSystemSupportProvider;

    private final IBmModelManager modelManager;

    private final IQualifiedNameFilePathConverter qualifiedNameFilePathConverter;

    private final Map<IProject, ProjectCache> projects = new ConcurrentHashMap<>();

    private final IResourceChangeListener resourceListener = new ResourceChangeListener();

    private static final boolean DEBUG_EDITING = Boolean.getBoolean("org.mard.dt.editing.debug"); //$NON-NLS-1$

    /** Logs every decision instead of one per unique key. */
    private static final boolean DEBUG_ALL = Boolean.getBoolean("org.mard.dt.editing.debug.all"); //$NON-NLS-1$

    /** Debug messages are logged once per unique decision to keep the log readable. */
    private static final Set<String> LOGGED_DEBUG_KEYS = Collections.synchronizedSet(new LinkedHashSet<>());

    private static final int MAX_DEBUG_KEYS = 500;

    private static void debugLog(String key, String message)
    {
        if (!DEBUG_EDITING)
            return;
        if (DEBUG_ALL || LOGGED_DEBUG_KEYS.size() < MAX_DEBUG_KEYS)
        {
            if (DEBUG_ALL || LOGGED_DEBUG_KEYS.add(key))
            {
                CorePlugin.log(CorePlugin.createWarningStatus(message));
            }
        }
    }

    @Inject
    public ProjectPathEditingService(IProjectFileSystemSupportProvider fileSystemSupportProvider,
        IBmModelManager modelManager, IQualifiedNameFilePathConverter qualifiedNameFilePathConverter)
    {
        this.fileSystemSupportProvider = fileSystemSupportProvider;
        this.modelManager = modelManager;
        this.qualifiedNameFilePathConverter = qualifiedNameFilePathConverter;
    }

    @Override
    public void activate()
    {
        ResourcesPlugin.getWorkspace().addResourceChangeListener(resourceListener);
    }

    @Override
    public void deactivate()
    {
        ResourcesPlugin.getWorkspace().removeResourceChangeListener(resourceListener);
    }

    /**
     * Initializes the extension.
     *
     * @param projectContext The project context that is being started
     */
    @LifecycleParticipant(phase = LifecyclePhase.RESOURCE_LOADING, dependsOn = IBmModelManager.SERVICE_NAME)
    public void init(ProjectContext projectContext)
    {
        IDtProject dtProject = projectContext.getProject();
        IProject project = dtProject.getWorkspaceProject();

        if (project != null)
        {
            dispose(project);
        }
    }

    @LifecycleParticipant(phase = LifecyclePhase.DISPOSING)
    public void dispose(ProjectContext lifecycleContext)
    {
        IDtProject dtProject = lifecycleContext.getProject();
        IProject project = dtProject.getWorkspaceProject();

        if (project != null)
        {
            dispose(project);
        }
    }

    private void dispose(IProject project)
    {
        ProjectCache cache = projects.remove(project);
        if (cache != null)
        {
            cache.uriCache.cleanUp();
        }
    }

    @Override
    public void invalidate(IProject project)
    {
        if (project != null)
        {
            dispose(project);
        }
    }

    @Override
    public boolean canEdit(IProject project, EObject eObject)
    {
        return canEdit(project, eObject, EditCallKind.CONTENT);
    }

    @Override
    public boolean canEdit(IProject project, EObject eObject, EditCallKind kind)
    {

        if (project == null || eObject == null || eObject.eIsProxy())
            return true;

        // BSL module code lives in a separate Module.bsl file, so the owner-based decision
        // does not apply directly; check both the module file path and the owner path.
        if (eObject instanceof Module module)
        {
            ProjectCache cache = projects.computeIfAbsent(project, this::createProjectCache);
            IProjectFileSystemSupport fileSystemSupport =
                fileSystemSupportProvider.getProjectFileSystemSupport(project);
            IPath modulePath = fileSystemSupport != null ? fileSystemSupport.getPath(eObject) : null;
            EObject owner = module.getOwner();
            IPath ownerPath = fileSystemSupport != null && owner != null && !owner.eIsProxy()
                ? fileSystemSupport.getPath(owner)
                : null;
            boolean moduleDecision = modulePath != null ? canEditWithPatterns(project, cache, modulePath) : true;
            boolean ownerDecision = ownerPath != null ? canEditWithPatterns(project, cache, ownerPath) : true;
            // An explicit rule for the module file itself wins over the owner's restriction.
            boolean result = isEnabledByPattern(cache, modulePath) || (moduleDecision && ownerDecision);
            debugLog("module|" + modulePath + "|" + ownerPath + "|" + result, //$NON-NLS-1$
                MessageFormat.format("canEdit module: modulePath={0}, ownerPath={1}, result={2}", //$NON-NLS-1$
                    modulePath, ownerPath, result));
            return result;
        }

        // Attributes and tabular sections share the owner's resource file, so they are checked
        // by their own 1C full name first; otherwise the owner's decision applies.
        String subObjectFqn = getObjectFullName(eObject);
        if (subObjectFqn == null && eObject instanceof IBmObject bmObject && bmObject.bmIsTop())
        {
            // Top objects without a generated name (forms, templates) are matched by
            // their canonical BM name, which is what the context menu copies for them.
            try
            {
                subObjectFqn = bmObject.bmGetFqn();
            }
            catch (RuntimeException e)
            {
                // not attached; fall through to path rules
            }
        }
        if (subObjectFqn != null)
        {
            ProjectCache cache = projects.computeIfAbsent(project, this::createProjectCache);
            List<String> fqns = fqnVariants(subObjectFqn);
            boolean result;
            boolean structural = kind == EditCallKind.STRUCTURAL;
            if (!structural && matchesAny(cache.getEnableFullnames(), fqns))
            {
                result = true;
            }
            else if (!structural && matchesAnyAncestor(cache.getEnableFullnames(), fqns))
            {
                // An enabled container opens its own content, e.g. an enabled tabular section
                // opens its attributes.
                result = true;
            }
            else if (matchesAny(cache.getDisableFullnames(), fqns))
            {
                result = false;
            }
            else if (matchesAnyAncestor(cache.getDisableFullnames(), fqns))
            {
                // A disabled container closes its own content, e.g. a disabled external
                // data source closes its tables and their fields.
                result = false;
            }
            else
            {
                IProjectFileSystemSupport fileSystemSupport =
                    fileSystemSupportProvider.getProjectFileSystemSupport(project);
                IPath path = resolvePath(fileSystemSupport, eObject);
                if (path == null && eObject instanceof IBmObject bmObject)
                {
                    // Objects without their own file (e.g. a template's spreadsheet content)
                    // inherit the decision of their top object instead of defaulting to allowed.
                    try
                    {
                        IBmObject top = bmObject.bmGetTopObject();
                        if (top != null && top != bmObject)
                        {
                            path = fileSystemSupport != null ? fileSystemSupport.getPath(top) : null;
                        }
                    }
                    catch (RuntimeException e)
                    {
                        // not attached; keep the default
                    }
                }
                result = path != null ? canEditWithPatterns(project, cache, path) : true;
            }
            debugLog("sub|" + eObject.eClass().getName() + "|" + subObjectFqn + "|" + result, //$NON-NLS-1$
                MessageFormat.format("canEdit sub-object: type={0}, fqn={1}, result={2}, enableFullnames={3}", //$NON-NLS-1$
                    eObject.eClass().getName(), subObjectFqn, result, cache.getEnableFullnames()));
            return result;
        }

        URI uri = EcoreUtil.getURI(eObject);
        if (uri == null)
            return true;

        String resourceUri = uri.trimFragment().toString();
        if (resourceUri == null)
            return true;

        ProjectCache cache = projects.computeIfAbsent(project, this::createProjectCache);

        try
        {
            return cache.getUriCache().get(resourceUri, () -> {
                IProjectFileSystemSupport fileSystemSupport =
                    fileSystemSupportProvider.getProjectFileSystemSupport(project);

                if (fileSystemSupport != null)
                {
                    IPath path = resolvePath(fileSystemSupport, eObject);
                    if (path != null)
                        return canEditWithPatterns(project, cache, path);
                }
                return true;
            });
        }
        catch (ExecutionException e)
        {
            CorePlugin.logError(e);
        }
        return true;
    }

    @Override
    public boolean canEdit(IProject project, IPath path)
    {

        if (project == null)
            return true;

        ProjectCache cache = projects.computeIfAbsent(project, this::createProjectCache);

        return canEditWithPatterns(project, cache, path);
    }

    /**
     * Resolves the resource path of the object. Objects without their own resource (for example
     * type descriptions) inherit the path of the closest ancestor.
     */
    public static IPath resolvePath(IProjectFileSystemSupport fileSystemSupport, EObject object)
    {
        if (fileSystemSupport == null || object == null)
            return null;

        IPath path = fileSystemSupport.getPath(object);
        EObject current = object;
        while (path == null)
        {
            current = current.eContainer();
            if (current == null || current.eIsProxy())
                break;
            path = fileSystemSupport.getPath(current);
        }
        return path;
    }

    /**
     * Matches a file FQN against a single enable.fullname pattern. File level patterns (form and
     * module) match exactly, everything else also covers its children.
     */
    public static boolean matchesEnablePattern(String pattern, String fqnText)
    {
        if (pattern == null || fqnText == null)
            return false;
        String trimmedPattern = pattern.trim();
        if (trimmedPattern.isEmpty())
            return false;
        if (fqnText.equals(trimmedPattern))
            return true;
        String strippedPattern = stripMarkers(trimmedPattern);
        String strippedFqn = stripMarkers(fqnText);
        if (strippedPattern == null || strippedFqn == null)
            return false;
        if (strippedFqn.equals(strippedPattern))
            return true;
        // A rule for a form or a module must not unlock its sibling files.
        boolean filePattern = trimmedPattern.endsWith(".Form") || trimmedPattern.endsWith(".Module"); //$NON-NLS-1$ //$NON-NLS-2$
        return !filePattern && strippedFqn.startsWith(strippedPattern + "."); //$NON-NLS-1$
    }

    private boolean isEnabledByPattern(ProjectCache cache, IPath path)
    {
        if (path == null || cache.getEnablePatterns().isEmpty())
            return false;
        QualifiedName fqn = qualifiedNameFilePathConverter.getFqn(path);
        if (fqn == null)
            return false;
        String fqnText = fqn.toString();
        for (String pattern : cache.getEnablePatterns())
        {
            if (matchesEnablePattern(pattern, fqnText))
                return true;
        }
        return false;
    }

    private boolean canEditWithPatterns(IProject project, ProjectCache cache, IPath path)
    {

        if (path == null)
            return true;

        boolean result = canEdit(cache, path);

        String fqnText = null;
        QualifiedName fqn = qualifiedNameFilePathConverter.getFqn(path);
        if (fqn != null)
        {
            fqnText = fqn.toString();
        }

        // Files of a disabled owner (e.g. the module of a disabled task's form) must stay
        // closed even when the path rules alone would allow them.
        if (result && fqnText != null && !cache.getDisablePatterns().isEmpty())
        {
            for (String pattern : cache.getDisablePatterns())
            {
                if (matchesEnablePattern(pattern, fqnText))
                {
                    result = false;
                    break;
                }
            }
        }

        if (!cache.getEnablePatterns().isEmpty() && fqnText != null)
        {
            for (String pattern : cache.getEnablePatterns())
            {
                if (matchesEnablePattern(pattern, fqnText))
                {
                    result = true;
                    break;
                }
            }
        }

        debugLog("path|" + path + "|" + result, //$NON-NLS-1$
            MessageFormat.format("canEdit path: path={0}, result={1}", path, result)); //$NON-NLS-1$
        return result;
    }

    @Override
    public boolean canEditInMerge(IProject project)
    {
        if (project == null)
            return true;

        ProjectCache cache = projects.computeIfAbsent(project, this::createProjectCache);

        return cache.canEditInMerge();
    }

    private void addAllPath(IProject project, EditingSettingsContent content, Set<IPath> paths)
    {

        if (content.getPath() != null && !content.getPath().isEmpty())
        {
            for (String line : content.getPath())
            {
                if (Path.isValidPosixPath(line))
                    paths.add(new Path(line));
            }
        }

        if (content.getSubsystem() != null && !content.getSubsystem().isEmpty())
        {

            IProjectFileSystemSupport fileSystemSupport =
                fileSystemSupportProvider.getProjectFileSystemSupport(project);

            IBmModel model = modelManager.getModel(project);

            if (fileSystemSupport != null && model != null)
            {
                addSubsystemPath(paths, content.getSubsystem(), model, fileSystemSupport);
            }
        }

        if (content.getFullname() != null && !content.getFullname().isEmpty())
        {
            IProjectFileSystemSupport fileSystemSupport =
                fileSystemSupportProvider.getProjectFileSystemSupport(project);

            IBmModel model = modelManager.getModel(project);

            if (fileSystemSupport != null && model != null)
            {
                addFullnamePath(paths, content.getFullname(), model, fileSystemSupport);
            }
        }

        if (content.getBranch() != null && !content.getBranch().isEmpty())
        {
            addBranches(paths, content.getBranch(), project);
        }
    }

    private void addBranches(Set<IPath> paths, List<String> branches, IProject project)
    {
        final Repository repository = GitUtils.getGitRepository(project);
        if (repository == null)
        {
            return;
        }

        IPath projectPath = project.getLocation();
        IPath srcPath = projectPath.append("src"); //$NON-NLS-1$
        IPath gitPath = new Path(repository.getWorkTree().getAbsolutePath());
        IPath configurationPath = srcPath.append("Configuration"); //$NON-NLS-1$

        try (final Git git = new Git(repository);
            final RevWalk rw = new RevWalk(repository);
            final TreeWalk tw = new TreeWalk(repository);)
        {
            List<Ref> remoteBranches = git.branchList().setListMode(ListMode.REMOTE).call();

            for (String shortBranchName : branches)
            {
                for (Ref remoteBranch : remoteBranches)
                {
                    if (shortBranchName.equals(repository.shortenRemoteBranchName(remoteBranch.getName())))
                    {
                        final Ref branchRef = repository.exactRef(remoteBranch.getName());
                        if (branchRef != null)
                        {
                            final RevCommit branchCommit = rw.parseCommit(branchRef.getObjectId());
                            tw.addTree(branchCommit.getTree());
                        }
                    }
                }
            }

            if (tw.getTreeCount() != 0)
            {
                tw.setRecursive(true);

                while (tw.next())
                {
                    IPath gitFilePath = gitPath.append(tw.getPathString());

                    if (srcPath.isPrefixOf(gitFilePath) && !configurationPath.isPrefixOf(gitFilePath)
                        && !gitFilePath.toFile().isDirectory())
                    {
                        IPath relativePath = gitFilePath.makeRelativeTo(projectPath);
                        paths.add(relativePath);
                    }
                }
            }
        }
        catch (IllegalStateException | IOException | GitAPIException e)
        {
            CorePlugin.logError(e);
        }
    }


    private void addFullnamePath(Set<IPath> paths, List<String> fullnames, IBmModel model,
        IProjectFileSystemSupport fileSystemSupport)
    {

        IBmTransaction transaction = model.getEngine().getCurrentTransaction();
        if (transaction != null)
        {
            addFullnamePath(paths, fullnames, fileSystemSupport, transaction);
        }
        else
        {
            model.executeReadonlyTask(new AbstractBmTask<Void>("Read paths by fullname") //$NON-NLS-1$
            {
                @Override
                public Void execute(IBmTransaction transaction, IProgressMonitor monitor)
                {

                    addFullnamePath(paths, fullnames, fileSystemSupport, transaction);
                    return null;
                }
            });
        }
    }

    private void addFullnamePath(Set<IPath> paths, List<String> fullnames, IProjectFileSystemSupport fileSystemSupport,
        IBmTransaction transaction)
    {
        for (String fullname : fullnames)
        {
            if (isSubObjectFqn(fullname))
            {
                // Sub-object rules are matched by full name, not by file path.
                continue;
            }

            int before = paths.size();
            IBmObject top = transaction.getTopObjectByFqn(fullname);
            if (top != null)
            {
                IPath path = fileSystemSupport.getPath(top);
                if (path != null)
                {
                    paths.add(path);
                }
            }
            else
            {
                // BSL modules are not BM top objects, resolve their paths from the FQN directly.
                List<IPath> modulePaths =
                    qualifiedNameFilePathConverter.getAllPossibleFilePaths(QualifiedName.create(fullname));
                if (modulePaths == null || modulePaths.isEmpty())
                {
                    // Form modules (<form FQN>.Module) have no direct FQN-to-path mapping;
                    // resolve the form path and use the sibling Module.bsl file.
                    if (fullname.endsWith(".Module")) //$NON-NLS-1$
                    {
                        String baseFqn = fullname.substring(0, fullname.length() - ".Module".length()); //$NON-NLS-1$
                        List<IPath> basePaths =
                            qualifiedNameFilePathConverter.getAllPossibleFilePaths(QualifiedName.create(baseFqn));
                        if (basePaths != null && !basePaths.isEmpty())
                        {
                            basePaths.stream().filter(Objects::nonNull)
                                .map(ProjectPathEditingService::moduleFilePath).forEach(paths::add);
                        }
                        else
                        {
                            // The path converter does not know form FQNs; resolve the form as a BM
                            // top object and take its sibling Module.bsl.
                            IBmObject formTop = transaction.getTopObjectByFqn(baseFqn);
                            IPath formPath = formTop != null ? fileSystemSupport.getPath(formTop) : null;
                            IPath modulePath = moduleFilePath(formPath);
                            if (modulePath != null)
                            {
                                paths.add(modulePath);
                            }
                        }
                    }
                }
                else
                {
                    modulePaths.stream().filter(Objects::nonNull).forEach(paths::add);
                }
            }

            if (DEBUG_EDITING)
            {
                debugLog("fqnpath|" + fullname, //$NON-NLS-1$
                    "fullname " + fullname + " resolved to paths: " + paths + " (size before " + before + ")"); //$NON-NLS-1$
            }
            if (DEBUG_EDITING && paths.size() == before)
            {
                debugLog("nopath|" + fullname, //$NON-NLS-1$
                    "fullname resolved to no paths: " + fullname); //$NON-NLS-1$
            }
        }
    }

    @Override
    public String getObjectFullName(EObject object)
    {
        if (object == null || object.eIsProxy())
            return null;

        // EDS tables/cubes are BM top objects themselves, so they have no EObject container;
        // their own canonical FQN (e.g. ExternalDataSource.X.Cube.Cube) is the sub-object name.
        if (object instanceof IBmObject self
            && (object instanceof Table || object instanceof Cube || object instanceof DimensionTable
                || object instanceof Function)
            && self.bmIsTop())
        {
            return self.bmGetFqn();
        }

        LinkedList<String> segments = new LinkedList<>();
        EObject container = collectSegments(object, segments);

        if (segments.isEmpty() || !(container instanceof IBmObject top) || !top.bmIsTop())
            return null;

        return buildFullName(top.bmGetFqn(), segments);
    }

    // Walks the sub-object chain (features, tabular sections, enum values, columns, commands,
    // external data source tables/cubes/functions) and collects the FQN suffix segments;
    // returns the outermost container.
    static EObject collectSegments(EObject object, LinkedList<String> segments)
    {
        EObject container = object;
        while (true)
        {
            // Top-level EDS tables/cubes are their own FQN base even though they are nested
            // sub-objects of the external data source.
            if (container instanceof IBmObject topObject && topObject.bmIsTop()
                && (container instanceof Table || container instanceof Cube
                    || container instanceof DimensionTable || container instanceof Function))
            {
                break;
            }
            if (container instanceof BasicForm form)
            {
                segments.addFirst("Form"); //-NLS-1$
                segments.addFirst(form.getName());
            }
            else if (container instanceof BasicFeature feature)
            {
                segments.addFirst("Attribute"); //$NON-NLS-1$
                segments.addFirst(feature.getName());
            }
            else if (container instanceof BasicTabularSection tabularSection)
            {
                segments.addFirst("TabularSection"); //$NON-NLS-1$
                segments.addFirst(tabularSection.getName());
            }
            else if (container instanceof Recalculation recalculation)
            {
                segments.addFirst("Recalculation"); //$NON-NLS-1$
                segments.addFirst(recalculation.getName());
            }
            else if (container instanceof EnumValue enumValue)
            {
                segments.addFirst("Value"); //$NON-NLS-1$
                segments.addFirst(enumValue.getName());
            }
            else if (container instanceof Column column)
            {
                segments.addFirst("Column"); //$NON-NLS-1$
                segments.addFirst(column.getName());
            }
            else if (container instanceof BasicCommand command && !(container instanceof IBmObject top && top.bmIsTop()))
            {
                segments.addFirst("Command"); //$NON-NLS-1$
                segments.addFirst(command.getName());
            }
            else if (container instanceof Template template)
            {
                segments.addFirst("Template"); //$NON-NLS-1$
                segments.addFirst(template.getName());
            }
            else if (container instanceof Table table)
            {
                segments.addFirst("Table"); //$NON-NLS-1$
                segments.addFirst(table.getName());
            }
            else if (container instanceof Cube cube)
            {
                segments.addFirst("Cube"); //$NON-NLS-1$
                segments.addFirst(cube.getName());
            }
            else if (container instanceof DimensionTable dimensionTable)
            {
                segments.addFirst("DimensionTable"); //$NON-NLS-1$
                segments.addFirst(dimensionTable.getName());
            }
            else if (container instanceof Function function)
            {
                segments.addFirst("Function"); //$NON-NLS-1$
                segments.addFirst(function.getName());
            }
            else if (container instanceof Field field)
            {
                segments.addFirst("Field"); //$NON-NLS-1$
                segments.addFirst(field.getName());
            }
            else if (container instanceof Dimension dimension)
            {
                segments.addFirst("Dimension"); //$NON-NLS-1$
                segments.addFirst(dimension.getName());
            }
            else if (container instanceof Resource resource)
            {
                segments.addFirst("Resource"); //$NON-NLS-1$
                segments.addFirst(resource.getName());
            }
            else if (container instanceof Method method)
            {
                segments.addFirst("Method"); //$NON-NLS-1$
                segments.addFirst(method.getName());
            }
            else if (container instanceof URLTemplate urlTemplate)
            {
                segments.addFirst("URLTemplate"); //$NON-NLS-1$
                segments.addFirst(urlTemplate.getName());
            }
            else if (container instanceof Operation operation)
            {
                segments.addFirst("Operation"); //$NON-NLS-1$
                segments.addFirst(operation.getName());
            }
            else if (container instanceof Parameter parameter)
            {
                segments.addFirst("Parameter"); //$NON-NLS-1$
                segments.addFirst(parameter.getName());
            }
            else
            {
                break;
            }
            container = ((EObject)container).eContainer();
        }
        return container;
    }

    public static String buildFullName(String baseFqn, List<String> segments)
    {
        if (baseFqn == null || baseFqn.isBlank() || segments.isEmpty())
            return null;

        return baseFqn + "." + String.join(".", segments); //$NON-NLS-1$ //$NON-NLS-2$
    }

    // Form module code file is the sibling Module.bsl of the form file.
    public static IPath moduleFilePath(IPath basePath)
    {
        if (basePath == null)
            return null;
        return basePath.removeLastSegments(1).append("Module.bsl"); //$NON-NLS-1$
    }

    private static final Set<String> SUB_OBJECT_MARKERS =
        Set.of("Attribute", "TabularSection", "Value", "Column", "Command", "Table", "Cube", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
            "DimensionTable", "Function", "Field", "Dimension", "Resource", "Template", "Method", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
            "URLTemplate", "Operation", "Parameter"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /**
     * Removes sub-object marker segments (Attribute, Template, Command, ...) from a full name, so that our
     * "name.Marker" convention and the canonical BM "Marker.name" order compare equal.
     *
     * @param fullname the full name, may be {@code null}
     * @return the marker-less full name, or {@code null}
     */
    public static String stripMarkers(String fullname)
    {
        if (fullname == null)
            return null;
        StringBuilder result = new StringBuilder();
        for (String segment : fullname.trim().split("\\.")) //$NON-NLS-1$
        {
            if (SUB_OBJECT_MARKERS.contains(segment))
                continue;
            if (result.length() > 0)
                result.append('.');
            result.append(segment);
        }
        return result.toString();
    }

    /**
     * Collects the full names of the enabled objects. Both whole objects and their sub-objects are
     * kept, because an enabled object opens its content.
     *
     * @param fullnames full names from the settings.
     * @return set of the enabled full names.
     */
    public static Set<String> collectEnabledFullnames(List<String> fullnames)
    {
        Set<String> result = new HashSet<>();
        if (fullnames == null)
        {
            return result;
        }
        for (String fullname : fullnames)
        {
            if (fullname != null && !fullname.isBlank())
            {
                result.add(fullname.trim());
            }
        }
        return result;
    }

    /**
     * Checks whether one of the ancestors of the given full name is enabled. An enabled container opens
     * its own content, e.g. an enabled tabular section opens its attributes.
     *
     * @param enableFullnames full names of the enabled objects.
     * @param fullname full name of the checked object.
     * @return true, if an ancestor of the object is enabled.
     */
    public static boolean isEnabledByAncestor(Set<String> enableFullnames, String fullname)
    {
        return hasAncestorMatch(enableFullnames, fullname);
    }

    /**
     * Checks whether one of the ancestors of the given full name is disabled. A disabled container
     * closes its own content, e.g. a disabled external data source closes its tables and fields.
     *
     * @param disableFullnames full names of the disabled objects.
     * @param fullname full name of the checked object.
     * @return true, if an ancestor of the object is disabled.
     */
    public static boolean isDisabledByAncestor(Set<String> disableFullnames, String fullname)
    {
        return hasAncestorMatch(disableFullnames, fullname);
    }

    private static boolean hasAncestorMatch(Set<String> fullnames, String fullname)
    {
        if (fullnames == null || fullnames.isEmpty() || fullname == null || fullname.isBlank())
            return false;

        String[] segments = fullname.trim().split("\\."); //$NON-NLS-1$
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < segments.length - 1; i++)
        {
            if (i > 0)
            {
                builder.append('.');
            }
            builder.append(segments[i]);
            if (i >= 1 && fullnames.contains(builder.toString()))
            {
                return true;
            }
        }
        return false;
    }

    // A form or template rule must cover both its nodes: the metadata node carries the
    // generated name (owner.FormT1.Form) while the content top object carries the canonical
    // marker-first name (owner.Form.FormT1.Form); derive the missing variant from either.
    static List<String> fqnVariants(String fullname)
    {
        if (fullname == null)
            return List.of();

        String[] segments = fullname.trim().split("\\."); //$NON-NLS-1$
        if (segments.length < 4)
            return List.of(fullname);

        String marker = segments[segments.length - 1];
        if ("Module".equals(marker) && segments.length >= 5)
        {
            // A form module rule may use either name variant of its form
            // (canonical owner.Form.Name.Form.Module or generated owner.Name.Form.Module).
            StringBuilder base = new StringBuilder();
            for (int i = 0; i < segments.length - 1; i++)
            {
                if (i > 0)
                    base.append('.');
                base.append(segments[i]);
            }
            List<String> variants = new java.util.ArrayList<>();
            for (String variant : fqnVariants(base.toString()))
                variants.add(variant + ".Module"); //$NON-NLS-1$
            return variants;
        }
        if (!"Form".equals(marker) && !"Template".equals(marker)) //$NON-NLS-1$ //$NON-NLS-2$
            return List.of(fullname);

        if (marker.equals(segments[segments.length - 3]))
        {
            String[] alternate = new String[segments.length - 1];
            for (int i = 0, j = 0; i < segments.length; i++)
            {
                if (i != segments.length - 3)
                {
                    alternate[j++] = segments[i];
                }
            }
            return List.of(fullname, String.join(".", alternate)); //$NON-NLS-1$
        }

        String[] alternate = new String[segments.length + 1];
        for (int i = 0, j = 0; i < segments.length; i++)
        {
            alternate[j++] = segments[i];
            if (i == segments.length - 3)
            {
                alternate[j++] = marker;
            }
        }
        return List.of(fullname, String.join(".", alternate)); //$NON-NLS-1$
    }

    private static boolean matchesAny(Set<String> fullnames, List<String> fqns)
    {
        for (String fqn : fqns)
        {
            if (fullnames.contains(fqn))
                return true;
        }
        return false;
    }

    private static boolean matchesAnyAncestor(Set<String> fullnames, List<String> fqns)
    {
        for (String fqn : fqns)
        {
            if (hasAncestorMatch(fullnames, fqn))
                return true;
        }
        return false;
    }

    public static boolean isSubObjectFqn(String fullname)
    {
        if (fullname == null)
            return false;
        String[] segments = fullname.trim().split("\\."); //$NON-NLS-1$
        // Sub-object FQNs carry a marker segment ("<name>.Attribute", "<name>.Column" etc.);
        // nested chains (cube dimension tables, tabular section attributes) put it mid-string.
        for (int i = 2; i < segments.length; i++)
        {
            String segment = segments[i];
            if ("Attribute".equals(segment) //$NON-NLS-1$
                || "TabularSection".equals(segment) //$NON-NLS-1$
                || "Value".equals(segment) //$NON-NLS-1$
                || "Column".equals(segment) //$NON-NLS-1$
                || "Command".equals(segment) //$NON-NLS-1$
                || "Table".equals(segment) //$NON-NLS-1$
                || "Cube".equals(segment) //$NON-NLS-1$
                || "DimensionTable".equals(segment) //$NON-NLS-1$
                || "Function".equals(segment) //$NON-NLS-1$
                || "Field".equals(segment) //$NON-NLS-1$
                || "Dimension".equals(segment) //$NON-NLS-1$
                || "Resource".equals(segment) //$NON-NLS-1$
                || "Template".equals(segment) //$NON-NLS-1$
                || "Method".equals(segment) //$NON-NLS-1$
                || "URLTemplate".equals(segment) //$NON-NLS-1$
                || "Operation".equals(segment) //$NON-NLS-1$
                || "Parameter".equals(segment)) //$NON-NLS-1$
            {
                return true;
            }
        }
        return false;
    }

    private void addSubsystemPath(Set<IPath> paths, List<String> subsystems, IBmModel model,
        IProjectFileSystemSupport fileSystemSupport)
    {

        IBmTransaction transaction = model.getEngine().getCurrentTransaction();
        if (transaction != null)
        {
            addSubsystemPath(paths, subsystems, fileSystemSupport, transaction);
        }
        else
        {
            model.executeReadonlyTask(new AbstractBmTask<Void>("Read subsystem content paths") //$NON-NLS-1$
            {
                @Override
                public Void execute(IBmTransaction transaction, IProgressMonitor monitor)
                {
                    addSubsystemPath(paths, subsystems, fileSystemSupport, transaction);
                    return null;
                }
            });
        }
    }

    private void addSubsystemPath(Set<IPath> paths, List<String> subsystems,
        IProjectFileSystemSupport fileSystemSupport, IBmTransaction transaction)
    {
        String preffix = SUBSYSTEM.getName();
        for (String subsystemName : subsystems)
        {
            StringBuilder sb = new StringBuilder();
            String[] segments = subsystemName.trim().split("\\."); //$NON-NLS-1$
            for (int i = 0; i < segments.length; i++)
            {
                String segment = segments[i];
                sb.append(preffix);
                sb.append("."); //$NON-NLS-1$
                sb.append(segment);
            }

            IBmObject subsystem = transaction.getTopObjectByFqn(sb.toString());
            if (subsystem instanceof Subsystem)
            {
                addSubsystemPath((Subsystem)subsystem, paths, fileSystemSupport);
            }
        }
    }

    private void addSubsystemPath(Subsystem subsystem, Set<IPath> paths, IProjectFileSystemSupport fileSystemSupport)
    {

        for (MdObject item : subsystem.getContent())
        {
            IPath path = fileSystemSupport.getPath(item);
            if (path != null && path.segmentCount() > TOP_OBJECT_FIRST_SEGMENTS)
            {
                int remove = path.segmentCount() - TOP_OBJECT_FIRST_SEGMENTS;
                paths.add(path.removeLastSegments(remove));
            }
        }

        for (Subsystem item : subsystem.getSubsystems())
        {
            addSubsystemPath(item, paths, fileSystemSupport);
        }

    }

    private boolean canEdit(ProjectCache cache, IPath path)
    {

        if (cache.getDisable().isEmpty())
            return true;

        if (!cache.getEnable().isEmpty()
            && (cache.getEnable().contains(path) || cache.getEnable().stream().anyMatch(p -> p.isPrefixOf(path))))
            return true;

        if (cache.getDisable().contains(path) || cache.getDisable().stream().anyMatch(p -> p.isPrefixOf(path)))
            return false;

        return true;
    }

    private ProjectCache createProjectCache(IProject project)
    {

        Comparator<IPath> comparator = (o1, o2) -> {
            if (o1.equals(o2))
                return 0;
            for (int i = 0; i < o1.segmentCount() && i < o2.segmentCount(); i++)
            {
                String p1 = o1.segments()[i];
                String p2 = o2.segments()[i];
                int compare = p1.compareTo(p2);
                if (compare != 0)
                    return compare;
            }
            return 1;
        };

        Set<IPath> disable = new ConcurrentSkipListSet<>(comparator);
        Set<IPath> enable = new ConcurrentSkipListSet<>(comparator);

        Set<String> enablePatterns = new HashSet<>();
        Set<String> disablePatterns = new HashSet<>();

        EditingSettings settings = loadEditingSettings(project);
        if (settings == null)
        {
            return new ProjectCache(disable, enable, true, Set.of(), Set.of(), Set.of(), Set.of());
        }

        Set<String> enableFullnames = new HashSet<>();
        if (settings.getEnable() != null && settings.getEnable().getFullname() != null)
        {
            // All enabled full names are kept: an enabled object opens its content, an enabled
            // sub-object opens its own content.
            enableFullnames.addAll(collectEnabledFullnames(settings.getEnable().getFullname()));
            // Allow editing of files that live under an enabled full name (templates, commands,
            // forms of a disabled owner): getFqn(path) uses the canonical marker-before-name order.
            settings.getEnable().getFullname().stream().filter(fqn -> fqn != null && !fqn.isBlank()).forEach(fqn -> {
                for (String variant : fqnVariants(fqn))
                {
                    String trimmed = variant.trim();
                    enablePatterns.add(trimmed);
                    String normalized = stripMarkers(trimmed);
                    if (normalized != null)
                        enablePatterns.add(normalized);
                }
            });
        }

        Set<String> disableFullnames = new HashSet<>();
        if (settings.getDisable() != null && settings.getDisable().getFullname() != null)
        {
            // All disabled full names are kept: a disabled container closes its own content,
            // e.g. a disabled external data source closes its tables and fields.
            settings.getDisable().getFullname().stream().filter(fqn -> fqn != null && !fqn.isBlank())
                .forEach(fqn -> {
                    disableFullnames.add(fqn.trim());
                    for (String variant : fqnVariants(fqn))
                    {
                        String trimmed = variant.trim();
                        disablePatterns.add(trimmed);
                        String normalized = stripMarkers(trimmed);
                        if (normalized != null)
                            disablePatterns.add(normalized);
                    }
                });
        }

        if (settings.getDisable() != null)
            addAllPath(project, settings.getDisable(), disable);

        if (!disable.isEmpty() && settings.getEnable() != null)
            addAllPath(project, settings.getEnable(), enable);

        // Remove disable path if there is higher enable (permissive) path
        for (IPath path : enable)
        {
            for (Iterator<IPath> iterator = disable.iterator(); iterator.hasNext();)
            {
                IPath iPath = iterator.next();
                if (path.isPrefixOf(iPath))
                    iterator.remove();
            }
        }

        boolean canEditInMerge = settings.getEnable() != null && settings.getEnable().isMerge();
        return new ProjectCache(disable, enable, canEditInMerge, enableFullnames, disableFullnames, enablePatterns,
            disablePatterns);
    }

    private EditingSettings loadEditingSettings(IProject project)
    {

        IFile file = project.getFile(SETTING_FILE_PATH);
        return EditingSettingsYamlReader.readOrNull(file);
    }

    private class ProjectCache
    {

        private final Cache<String, Boolean> uriCache =
            CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES).build();

        private final Set<IPath> disable;

        private final Set<IPath> enable;

        private final boolean canEditInMerge;

        private final Set<String> enableFullnames;

        private final Set<String> disableFullnames;

        private final Set<String> enablePatterns;

        private final Set<String> disablePatterns;

        public ProjectCache(Set<IPath> disable, Set<IPath> enable, boolean canEditInMerge,
            Set<String> enableFullnames, Set<String> disableFullnames, Set<String> enablePatterns,
            Set<String> disablePatterns)
        {
            if (disable == null || disable.isEmpty())
            {
                this.disable = Set.of();
            }
            else
            {
                this.disable = Set.copyOf(disable);
            }

            if (enable == null || enable.isEmpty() || this.getDisable().isEmpty())
            {
                this.enable = Set.of();
            }
            else
            {
                this.enable = Set.copyOf(enable);
            }
            this.canEditInMerge = canEditInMerge;
            this.enableFullnames = enableFullnames == null || enableFullnames.isEmpty()
                ? Set.of()
                : Set.copyOf(enableFullnames);
            this.disableFullnames = disableFullnames == null || disableFullnames.isEmpty()
                ? Set.of()
                : Set.copyOf(disableFullnames);
            this.enablePatterns = enablePatterns == null || enablePatterns.isEmpty()
                ? Set.of()
                : Set.copyOf(enablePatterns);
            this.disablePatterns = disablePatterns == null || disablePatterns.isEmpty()
                ? Set.of()
                : Set.copyOf(disablePatterns);

        }

        public Set<IPath> getDisable()
        {
            return disable;
        }

        public Set<IPath> getEnable()
        {
            return enable;
        }

        public Cache<String, Boolean> getUriCache()
        {
            return uriCache;
        }

        public boolean canEditInMerge()
        {
            return canEditInMerge;
        }

        public Set<String> getEnableFullnames()
        {
            return enableFullnames;
        }

        public Set<String> getDisableFullnames()
        {
            return disableFullnames;
        }

        public Set<String> getEnablePatterns()
        {
            return enablePatterns;
        }

        public Set<String> getDisablePatterns()
        {
            return disablePatterns;
        }

    }

    private class ResourceChangeListener
        implements IResourceChangeListener
    {

        @Override
        public void resourceChanged(IResourceChangeEvent event)
        {
            IResource res = event.getResource();

            if (event.getType() == IResourceChangeEvent.PRE_CLOSE || event.getType() == IResourceChangeEvent.PRE_DELETE)
            {
                if (res instanceof IProject)
                {
                    dispose((IProject)res);
                }
            }
            else if (event.getType() == IResourceChangeEvent.POST_CHANGE)
            {
                IResourceDelta delta = event.getDelta();
                if (delta != null)
                {
                    for (Iterator<Entry<IProject, ProjectCache>> iterator = projects.entrySet().iterator(); iterator
                        .hasNext();)
                    {
                        Entry<IProject, ProjectCache> entry = iterator.next();
                        IPath path = entry.getKey().getFullPath();
                        IResourceDelta projectDelta = delta.findMember(path.append(SETTING_FILE_PATH));
                        if (projectDelta != null
                            || (projectDelta = delta.findMember(path.append(SUBSYSTEM_PATH))) != null)
                        {
                            iterator.remove();
                        }
                    }
                }
            }
        }
    }

}
