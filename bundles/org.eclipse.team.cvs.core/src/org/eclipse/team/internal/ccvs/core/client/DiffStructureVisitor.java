package org.eclipse.team.internal.ccvs.core.client;
/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.resources.ICVSFile;
import org.eclipse.team.internal.ccvs.core.resources.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.resources.ResourceSyncInfo;

/**
 * The diff command needs to send a file structure to the server that differs somewhat from the canonical
 * format sent by other commands. Instead of sending new files as questionables this class sends
 * new files as modified and fakes them being added. The contents are sent to the server and are 
 * included in the returned diff report.
 */
class DiffStructureVisitor extends FileStructureVisitor {
	public DiffStructureVisitor(Session session, boolean modifiedOnly, boolean emptyFolders,
		IProgressMonitor monitor) {
		super(session, modifiedOnly, emptyFolders, monitor);
	}
	
	/**
	 * Send unmanaged files as modified with a default entry line.
	 */
	protected void sendFile(ICVSFile mFile, boolean sendQuestionable, String mode) throws CVSException {
		boolean binary = mode != null && mode.indexOf(ResourceSyncInfo.BINARY_TAG) != -1;
		boolean newFile = false;

		if (mFile.isManaged()) {
			session.sendEntry(mFile.getSyncInfo().getEntryLine(false));
		} else {
			ResourceSyncInfo info = new ResourceSyncInfo(mFile.getName(), ResourceSyncInfo.ADDED_REVISION, null, null, null, null);
			session.sendEntry(info.getEntryLine(false));
			newFile = true;
		}

		if (!mFile.exists()) {
			return;
		}

		if (mFile.isModified() || newFile) {
			session.sendModified(mFile, monitor, binary);
		} else {
			session.sendUnchanged(mFile.getName());
		}
	}			
}
