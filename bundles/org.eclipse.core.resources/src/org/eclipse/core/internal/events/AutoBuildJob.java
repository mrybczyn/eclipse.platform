/**********************************************************************
 * Copyright (c) 2003, 2004 IBM Corporation and others. All rights reserved.   This
 * program and the accompanying materials are made available under the terms of
 * the Common Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.core.internal.events;

import org.eclipse.core.internal.jobs.JobManager;
import org.eclipse.core.internal.resources.ICoreConstants;
import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.internal.utils.Policy;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.Preferences.PropertyChangeEvent;
import org.eclipse.core.runtime.jobs.*;

/**
 * The job for performing workspace auto-builds, and pre- and post- autobuild
 * notification.  This job is run whenever the workspace changes regardless 
 * of whether autobuild is on or off.
 */
class AutoBuildJob extends Job implements Preferences.IPropertyChangeListener {
	private boolean avoidBuild = false;
	private boolean buildNeeded = false;
	private boolean forceBuild = false;
	private boolean isAutoBuilding = false;
	/**
	 * Indicates that another thread tried to modify the workspace during
	 * the autobuild.  The autobuild should be immediately rescheduled
	 * so that it will run as soon as the next workspace modification completes.
	 */
	private boolean interrupted = false;
	private long lastBuild = 0L;
	private Workspace workspace;
	private final IJobManager jobManager = Platform.getJobManager();

	AutoBuildJob(Workspace workspace) {
		super(ICoreConstants.MSG_EVENTS_BUILDING_0);
		setRule(workspace.getRoot());
		isAutoBuilding = workspace.isAutoBuilding();
		this.workspace = workspace;
		ResourcesPlugin.getPlugin().getPluginPreferences().addPropertyChangeListener(this);
	}

	/**
	 * The workspace description has changed.  Update autobuild state.
	 * @param wasAutoBuilding the old autobuild state
	 * @param isAutoBuilding the new autobuild state
	 */
	private void autoBuildChanged(boolean wasAutoBuilding, boolean isAutoBuilding) {
		this.isAutoBuilding = isAutoBuilding;
		//force a build if autobuild has been turned on
		if (!forceBuild && !wasAutoBuilding && isAutoBuilding) {
			forceBuild = true;
			build(false);
		}
	}

	/**
	 * Used to prevent auto-builds at the end of operations that contain
	 * explicit builds
	 */
	synchronized void avoidBuild() {
		avoidBuild = true;
	}

	public boolean belongsTo(Object family) {
		return family == ResourcesPlugin.FAMILY_AUTO_BUILD;
	}

	private void broadcastChanges(int type) throws CoreException {
		workspace.getNotificationManager().broadcastChanges(workspace.getElementTree(), type, false);
	}

	/**
	 * Instructs the build job that a build is required.  Ensure the build
	 * job is scheduled to run.
	 * @param needsBuild Whether a build is required, either due to 
	 * workspace change or other factor that invalidates the built state.
	 */
	synchronized void build(boolean needsBuild) {
		buildNeeded |= needsBuild;
		interrupted = false;
		long delay = Math.max(Policy.MIN_BUILD_DELAY, Policy.MAX_BUILD_DELAY + lastBuild - System.currentTimeMillis());
		int state = getState();
		if (Policy.DEBUG_NEEDS_BUILD)
			Policy.debug("Build requested, needsBuild: " + needsBuild + " state: " + JobManager.printState(state) + " delay: " + delay); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		switch (state) {
			case Job.SLEEPING :
				wakeUp(delay);
				break;
			case NONE :
				setSystem(!isAutoBuilding);
				schedule(delay);
				break;
		}
	}

	/**
	 * The autobuild job has been canceled.  There are two flavours of
	 * cancel, explicit user cancelation, and implicit interruption due to another
	 * thread trying to modify the workspace.  In the latter case, we must 
	 * make sure the build is immediately rescheduled if it was interrupted 
	 * by another thread, so that clients waiting to join autobuild will properly 
	 * continue waiting
	 * @return a status with severity <code>CANCEL</code>
	 */
	private synchronized IStatus canceled() {
		//regardless of the form of cancelation, the build state is not happy
		buildNeeded = true;
		return Status.CANCEL_STATUS;
	}

	/**
	 * Another thread is attempting to modify the workspace. Flag the auto-build
	 * as interrupted so that it will cancel and reschedule itself
	 */
	synchronized void interrupt() {
		//if already interrupted, do nothing
		if (interrupted)
			return;
		switch (getState()) {
			case NONE :
				return;
			case WAITING :
				//put the job to sleep if it is waiting to run
				interrupted = !sleep();
				break;
			case RUNNING :
				//make sure autobuild doesn't interrupt itself
				interrupted = jobManager.currentJob() != this;
				break;
		}
		//clear the autobuild avoidance flag if we were interrupted
		if (interrupted)
			avoidBuild = false;
	}

	private void doBuild(IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		monitor = Policy.monitorFor(monitor);
		try {
			monitor.beginTask(null, Policy.opWork);
			final ISchedulingRule rule = workspace.getRuleFactory().buildRule();
			try {
				workspace.prepareOperation(rule, monitor);
				workspace.beginOperation(true);
				broadcastChanges(IResourceChangeEvent.PRE_BUILD);
				if (shouldBuild()) 
					workspace.getBuildManager().build(IncrementalProjectBuilder.AUTO_BUILD, Policy.subMonitorFor(monitor, Policy.opWork));
				broadcastChanges(IResourceChangeEvent.POST_BUILD);
				buildNeeded = false;
			} finally {
				//building may close the tree, but we are still inside an
				// operation so open it
				if (workspace.getElementTree().isImmutable())
					workspace.newWorkingTree();
				workspace.endOperation(rule, false, Policy.subMonitorFor(monitor, Policy.buildWork));
			}
		} finally {
			monitor.done();
		}
	}
	
	/**
	 * Forces an autobuild to occur, even if nothing has changed since the last
	 * build. This is used to force a build after a clean.
	 */
	public void forceBuild() {
		forceBuild = true;
	}

	synchronized boolean isInterrupted() {
		if (interrupted)
			return true;
		//check if another job is blocked by the build job
		if (isBlocking())
			interrupted = true;
		return interrupted;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.Preferences.IPropertyChangeListener#propertyChange(org.eclipse.core.runtime.Preferences.PropertyChangeEvent)
	 */
	public void propertyChange(PropertyChangeEvent event) {
		if (!event.getProperty().equals(ResourcesPlugin.PREF_AUTO_BUILDING))
			return;
		Object oldValue = event.getOldValue();
		Object newValue = event.getNewValue();
		if (oldValue instanceof Boolean && newValue instanceof Boolean)
			autoBuildChanged(((Boolean) oldValue).booleanValue(), ((Boolean) newValue).booleanValue());
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.core.internal.jobs.InternalJob#run(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public IStatus run(IProgressMonitor monitor) {
		//synchronized in case build starts during checkCancel
		synchronized (this) {
			if (monitor.isCanceled())
				return canceled();
		}
		try {
			doBuild(monitor);
			lastBuild = System.currentTimeMillis();
			//if the build was successful then it should not be recorded as interrupted
			interrupted = false;
			return Status.OK_STATUS;
		} catch (OperationCanceledException e) {
			return canceled();
		} catch (CoreException sig) {
			return sig.getStatus();
		}
	}

	/**
	 * Returns true if a build is actually needed, and false otherwise.
	 */
	private synchronized boolean shouldBuild() {
		try {
			//if auto-build is off then we never run
			if (!workspace.isAutoBuilding())
				return false;
			//build if the workspace requires a build (description changes)
			if (forceBuild)
				return true;
			if (avoidBuild)
				return false;
			//return whether there have been any changes to the workspace tree.
			return buildNeeded;
		} finally {
			//regardless of the result, clear the build flags for next time
			forceBuild = avoidBuild = buildNeeded = false;
		}
	}
}