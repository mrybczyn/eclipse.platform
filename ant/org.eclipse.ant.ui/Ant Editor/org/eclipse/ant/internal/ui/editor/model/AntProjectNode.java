/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.ant.internal.ui.editor.model;

import org.apache.tools.ant.Project;
import org.eclipse.ant.internal.ui.model.AntUIImages;
import org.eclipse.ant.internal.ui.model.IAntUIConstants;
import org.eclipse.jface.resource.ImageDescriptor;


public class AntProjectNode extends AntElementNode {

	private Project fProject= null;
	
	public AntProjectNode(Project project) {
		super("project"); //$NON-NLS-1$
		fProject= project;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ant.internal.ui.editor.model.AntElementNode#getDisplayName()
	 */
	public String getLabel() {
		String projectName= fProject.getName();
		if (projectName == null) {
			projectName= "project"; //$NON-NLS-1$
		}
		return projectName;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.ant.internal.ui.editor.model.AntElementNode#getBaseImageDescriptor()
	 */
	protected ImageDescriptor getBaseImageDescriptor() {
		return AntUIImages.getImageDescriptor(IAntUIConstants.IMG_ANT_PROJECT);
	}
}
