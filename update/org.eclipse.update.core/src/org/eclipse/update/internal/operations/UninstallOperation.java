/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.update.internal.operations;

import org.eclipse.core.runtime.*;
import org.eclipse.update.configuration.*;
import org.eclipse.update.core.*;
import org.eclipse.update.internal.core.*;
import org.eclipse.update.operations.*;

/**
 * Configure a feature.
 * ConfigOperation
 */
public class UninstallOperation extends FeatureOperation implements IUninstallFeatureOperation{

	public UninstallOperation(IConfiguredSite site, IFeature feature) {
		super(site, feature);
	}

	public void setTargetSite(IConfiguredSite targetSite) {
		this.targetSite = targetSite;
	}

	public boolean execute(IProgressMonitor pm, IOperationListener listener) throws CoreException {
		if (targetSite == null)
			targetSite = UpdateUtils.getConfigSite(feature, SiteManager.getLocalSite().getCurrentConfiguration());

			if (targetSite != null) {
				targetSite.remove(feature, pm);
			} else {
				// we should do something here
				String message =
					Policy.bind(
						"OperationsManager.error.uninstall", //$NON-NLS-1$
						feature.getLabel());
				IStatus status =
					new Status(
						IStatus.ERROR,
						UpdateUtils.getPluginId(),
						IStatus.OK,
						message,
						null);
				throw new CoreException(status);
			}


		markProcessed();
		if (listener != null)
			listener.afterExecute(this, null);

		boolean restartNeeded = SiteManager.getLocalSite().save();

		// notify the model
		OperationsManager.fireObjectChanged(feature, UNINSTALL);
		
		return restartNeeded;
	}

}
