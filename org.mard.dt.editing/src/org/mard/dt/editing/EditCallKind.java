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

/**
 * Kind of the editing check, detected by the calling context in EDT.
 */
public enum EditCallKind
{
    /** Editing of the content of an already opened object: fields, forms, commands of an editor. */
    CONTENT,

    /** Structural operation: creation, deletion, moving, sorting, editing of owner properties. */
    STRUCTURAL,

    /** Any other check, e.g. enabling of menu items. */
    OTHER
}
