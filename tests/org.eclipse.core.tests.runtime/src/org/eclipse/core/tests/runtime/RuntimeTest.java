/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.tests.runtime;

import junit.framework.TestCase;
import org.eclipse.core.internal.utils.UniversalUniqueIdentifier;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.test.performance.Performance;
import org.eclipse.test.performance.PerformanceMeter;

/**
 * Common superclass for all runtime tests.
 */
public abstract class RuntimeTest extends TestCase {
	public static final String PI_RUNTIME_TESTS = RuntimeTestsPlugin.PI_RUNTIME_TESTS;

	/** counter for generating unique random filesystem locations */
	protected static int nextLocationCounter = 0;

	/**
	 * Constructor required by test framework.
	 */
	public RuntimeTest(String name) {
		super(name);
	}

	/**
	 * Constructor required by test framework.
	 */
	public RuntimeTest() {
		super();
	}

	/**
	 * Fails the test due to the given exception.
	 * @param message
	 * @param e
	 */
	public void fail(String message, Exception e) {
		fail(message + ": " + e);
	}

	/*
	 * Return the root directory for the temp dir.
	 */
	public IPath getTempDir() {
		return new Path(System.getProperty("java.io.tmpdir"));
	}

	/**
	 * Returns a unique location on disk.  It is guaranteed that no file currently
	 * exists at that location.  The returned location will be unique with respect 
	 * to all other locations generated by this method in the current session.  
	 * If the caller creates a folder or file at this location, they are responsible for 
	 * deleting it when finished.
	 */
	public IPath getRandomLocation() {
		//low order bits are current time, high order bits are static counter
		IPath parent = getTempDir();
		final long mask = 0x00000000FFFFFFFFL;
		long segment = (((long) ++nextLocationCounter) << 32) | (System.currentTimeMillis() & mask);
		IPath path = parent.append(Long.toString(segment));
		while (path.toFile().exists()) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				// ignore
			}
			segment = (((long) ++nextLocationCounter) << 32) | (System.currentTimeMillis() & mask);
			path = parent.append(Long.toString(segment));
		}
		return path;
	}

	public String getUniqueString() {
		return new UniversalUniqueIdentifier().toString();
	}

	public static void runPerformanceTest(TestCase testCase, Runnable operation, int iterations) {
		Performance perf = Performance.getDefault();
		PerformanceMeter meter = perf.createPerformanceMeter(perf.getDefaultScenarioId(testCase));
		try {
			for (int i = 0; i < iterations; i++) {
				meter.start();
				operation.run();
				meter.stop();
			}
			meter.commit();
			perf.assertPerformance(meter);
		} finally {
			meter.dispose();
		}
	}

}
