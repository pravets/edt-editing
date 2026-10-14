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
package org.mard.dt.editing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Updates the editing settings YAML content by adding {@code fullname} rules.
 * <p>
 * The update is textual: existing comments, unknown fields, ordering and line endings are
 * preserved. The content is only parsed to validate the structure and to check duplicates.
 * Unsupported structures are reported as {@link IllegalArgumentException} without changing the
 * content.
 *
 * @author Dmitriy Marmyshev
 */
public class EditingSettingsYamlUpdater
{
    private static final String DISABLE = "disable"; //$NON-NLS-1$

    private static final String ENABLE = "enable"; //$NON-NLS-1$

    private static final String FULLNAME = "fullname"; //$NON-NLS-1$

    private static final String DEFAULT_INDENT = "  "; //$NON-NLS-1$

    private static final Pattern SECTION_LINE = Pattern.compile("^(\\w+):\\s*(?:#.*)?$"); //$NON-NLS-1$

    private static final Pattern KEY_LINE = Pattern.compile("^(\\s+)([\\w.]+):\\s*(.*?)\\s*$"); //$NON-NLS-1$

    private static final Pattern ITEM_LINE = Pattern.compile("^(\\s+)-\\s.*$"); //$NON-NLS-1$

    private static final Pattern COMMENT_OR_BLANK = Pattern.compile("^\\s*(#.*)?$"); //$NON-NLS-1$

    /**
     * Adds the FQN to the {@code fullname} list of the section in the YAML content.
     *
     * @param content the current file content, may be {@code null} or blank to create a minimal
     *            document.
     * @param section the root section name, either {@code "disable"} or {@code "enable"},
     *            cannot be {@code null}.
     * @param fqn the fully qualified name to add, cannot be {@code null} or blank.
     * @return the updated content, or the same content instance if the FQN is already in the
     *         list.
     * @throws IllegalArgumentException if the content is not valid YAML, or the target
     *             structure is not supported, or arguments are wrong.
     */
    public static String appendFullname(String content, String section, String fqn)
    {
        if (!DISABLE.equals(section) && !ENABLE.equals(section))
            throw new IllegalArgumentException("Unknown section: " + section); //$NON-NLS-1$

        if (fqn == null || fqn.isBlank())
            throw new IllegalArgumentException("FQN is empty"); //$NON-NLS-1$

        if (content == null || content.isBlank())
            return minimal(section, fqn);

        if (validate(content, section, fqn))
        {
            // The FQN is already in the list, nothing to change.
            return content;
        }

        return insert(content, section, fqn);
    }

    private static boolean validate(String content, String section, String fqn)
    {
        Object root;
        try
        {
            root = new Yaml().load(content);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Invalid YAML: " + e.getMessage(), e); //$NON-NLS-1$
        }

        if (!(root instanceof Map))
            throw new IllegalArgumentException("YAML root is not a mapping"); //$NON-NLS-1$

        Object sec = ((Map<?, ?>)root).get(section);
        if (sec == null)
            return false;

        if (!(sec instanceof Map))
            throw new IllegalArgumentException("Section \"" + section + "\" is not a mapping"); //$NON-NLS-1$ //$NON-NLS-2$

        Object fullname = ((Map<?, ?>)sec).get(FULLNAME);
        if (fullname == null)
            return false;

        if (!(fullname instanceof List))
            throw new IllegalArgumentException("\"" + FULLNAME + "\" is not a list"); //$NON-NLS-1$ //$NON-NLS-2$

        for (Object item : (List<?>)fullname)
        {
            if (fqn.equals(item))
            {
                return true;
            }
        }
        return false;
    }

    private static String insert(String content, String section, String fqn)
    {
        String eol = content.contains("\r\n") ? "\r\n" : "\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        String[] all = content.split("\n", -1); //$NON-NLS-1$
        boolean trailingNewline = all.length > 0 && all[all.length - 1].isEmpty();
        String[] lines = trailingNewline ? Arrays.copyOf(all, all.length - 1) : all;

        int sectionIndex = -1;
        for (int i = 0; i < lines.length; i++)
        {
            Matcher m = SECTION_LINE.matcher(strip(lines[i]));
            if (m.matches() && section.equals(m.group(1)))
            {
                sectionIndex = i;
                break;
            }
        }

        if (sectionIndex < 0)
        {
            return appendSection(lines, trailingNewline, eol, section, fqn);
        }

        int blockEnd = blockEnd(lines, sectionIndex);

        FullnameBlock block = findFullnameBlock(lines, sectionIndex, blockEnd);

        String[] inserted;
        int at;
        if (block == null)
        {
            String childIndent = childIndent(lines, sectionIndex, blockEnd);
            inserted = new String[] { childIndent + FULLNAME + ":", childIndent + DEFAULT_INDENT + "- " + scalar(fqn) }; //$NON-NLS-1$ //$NON-NLS-2$
            at = blockEnd;
        }
        else if (block.unsupported)
        {
            throw new IllegalArgumentException("\"" + FULLNAME + "\" of \"" + section //$NON-NLS-1$ //$NON-NLS-2$
                + "\" is not a block list, add the rule manually"); //$NON-NLS-1$
        }
        else
        {
            String itemIndent = block.itemIndent != null ? block.itemIndent : block.keyIndent + DEFAULT_INDENT;
            inserted = new String[] { itemIndent + "- " + scalar(fqn) }; //$NON-NLS-1$
            at = block.lastItemIndex >= 0 ? block.lastItemIndex + 1 : block.keyIndex + 1;
        }

        if (eol.equals("\r\n")) //$NON-NLS-1$
        {
            for (int i = 0; i < inserted.length; i++)
            {
                inserted[i] += '\r';
            }
        }

        List<String> result = new ArrayList<>(Arrays.asList(lines).subList(0, at));
        for (String line : inserted)
        {
            result.add(line);
        }
        for (int i = at; i < lines.length; i++)
        {
            result.add(lines[i]);
        }

        StringBuilder sb = new StringBuilder(String.join("\n", result)); //$NON-NLS-1$
        if (trailingNewline && !result.isEmpty())
        {
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String minimal(String section, String fqn)
    {
        return section + ":\n" + DEFAULT_INDENT + FULLNAME + ":\n" + DEFAULT_INDENT + DEFAULT_INDENT + "- " //$NON-NLS-1$ //$NON-NLS-2$
            + scalar(fqn) + "\n"; //$NON-NLS-1$
    }

    private static String appendSection(String[] lines, boolean trailingNewline, String eol, String section, String fqn)
    {
        StringBuilder sb = new StringBuilder();
        if (lines.length > 0)
        {
            sb.append(String.join("\n", lines)); //$NON-NLS-1$
            if (trailingNewline)
            {
                sb.append('\n');
            }
            // Blank line between the existing content and the new section.
            sb.append('\n');
        }
        sb.append(section).append(':');
        if (eol.equals("\r\n")) //$NON-NLS-1$
        {
            sb.append('\r');
        }
        sb.append('\n');
        String[] block = new String[] { DEFAULT_INDENT + FULLNAME + ":", DEFAULT_INDENT + DEFAULT_INDENT + "- " + scalar(fqn) }; //$NON-NLS-1$ //$NON-NLS-2$
        for (String line : block)
        {
            sb.append(line);
            if (eol.equals("\r\n")) //$NON-NLS-1$
            {
                sb.append('\r');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static int blockEnd(String[] lines, int sectionIndex)
    {
        for (int i = sectionIndex + 1; i < lines.length; i++)
        {
            String line = strip(lines[i]);
            if (!line.isBlank() && !line.startsWith(" ") && !line.startsWith("\t")) //$NON-NLS-1$ //$NON-NLS-2$
                return i;
        }
        return lines.length;
    }

    private static class FullnameBlock
    {
        int keyIndex;

        String keyIndent;

        int lastItemIndex = -1;

        String itemIndent;

        boolean unsupported;
    }

    private static FullnameBlock findFullnameBlock(String[] lines, int sectionIndex, int blockEnd)
    {
        FullnameBlock best = null;
        for (int i = sectionIndex + 1; i < blockEnd; i++)
        {
            String line = strip(lines[i]);
            Matcher key = KEY_LINE.matcher(line);
            if (key.matches() && FULLNAME.equals(key.group(2))
                && (best == null || key.group(1).length() < best.keyIndent.length()))
            {
                best = new FullnameBlock();
                best.keyIndex = i;
                best.keyIndent = key.group(1);
                if (!key.group(3).isEmpty() && !key.group(3).startsWith("#")) //$NON-NLS-1$
                {
                    // Inline value, e.g. flow style list or mapping.
                    best.unsupported = true;
                    return best;
                }
                scanItems(lines, i + 1, blockEnd, best);
            }
        }
        return best;
    }

    private static void scanItems(String[] lines, int start, int blockEnd, FullnameBlock block)
    {
        for (int i = start; i < blockEnd; i++)
        {
            String line = strip(lines[i]);
            if (COMMENT_OR_BLANK.matcher(line).matches())
                continue;
            Matcher item = ITEM_LINE.matcher(line);
            if (item.matches())
            {
                String indent = item.group(1);
                if (indent.length() > block.keyIndent.length())
                {
                    block.lastItemIndex = i;
                    if (block.itemIndent == null)
                        block.itemIndent = indent;
                }
            }
            else
            {
                return;
            }
        }
    }

    private static String childIndent(String[] lines, int sectionIndex, int blockEnd)
    {
        for (int i = sectionIndex + 1; i < blockEnd; i++)
        {
            String line = strip(lines[i]);
            Matcher key = KEY_LINE.matcher(line);
            if (key.matches())
                return key.group(1);
        }
        return DEFAULT_INDENT;
    }

    private static String scalar(String fqn)
    {
        DumperOptions options = new DumperOptions();
        options.setWidth(Integer.MAX_VALUE);
        String dumped = new Yaml(options).dump(fqn);
        return dumped.endsWith("\n") ? dumped.substring(0, dumped.length() - 1) : dumped; //$NON-NLS-1$
    }

    private static String strip(String line)
    {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line; //$NON-NLS-1$
    }

    private EditingSettingsYamlUpdater()
    {
        throw new IllegalAccessError();
    }
}
