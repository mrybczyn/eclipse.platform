package org.eclipse.ant.core;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

/*
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 1999 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "The Jakarta Project", "Ant", and "Apache Software
 *    Foundation" must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache"
 *    nor may "Apache" appear in their names without prior written
 *    permission of the Apache Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

import org.apache.tools.ant.taskdefs.Javac;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.Path;
import java.io.File;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.compilers.CompilerAdapter;

/**
 * Ant task which replaces the standard Ant Javac task.  This version of the task
 * uses a special compiler adapter factory which in turn uses the Ant plug-in's
 * object class registry to find the class to use for compiler adapters.  This is required
 * because of the platform's classloading strategy.
 * <p>
 * This task can be used as a direct replacement for the original Javac task
 * </p>
 * <p>
 * <b>Note:</b> This class/interface is part of an interim API that is still under 
 * development and expected to change significantly before reaching stability. 
 * It is being made available at this early stage to solicit feedback from pioneering 
 * adopters on the understanding that any code that uses this API will almost 
 * certainly be broken (repeatedly) as the API evolves.
 * </p>
 */

public class EclipseJavac extends Javac {
	
/**
 * Executes the task.
 * 
 * @exception BuildException thrown if a problem occurs during execution
 */
public void execute() throws BuildException {
	// first off, make sure that we've got a srcdir

	Path src = getSrcdir();
	if (src == null)
		throw new BuildException(Policy.bind("exception.missingSrcAttribute"), location);
	String[] list = src.list();
	if (list.length == 0)
		throw new BuildException(Policy.bind("exception.missingSrcAttribute"), location);

	File destDir = getDestdir();
	if (destDir != null && !destDir.isDirectory())
		throw new BuildException(Policy.bind("exception.missingDestinationDir",destDir.toString()), location);

	// scan source directories and dest directory to build up 
	// compile lists
	resetFileLists();
	for (int i = 0; i < list.length; i++) {
		File srcDir = (File) project.resolveFile(list[i]);
		if (!srcDir.exists()) {
			throw new BuildException(Policy.bind("exception.missingSourceDir",srcDir.getPath()), location);
		}

		DirectoryScanner ds = this.getDirectoryScanner(srcDir);

		String[] files = ds.getIncludedFiles();

		scanDir(srcDir, destDir != null ? destDir : srcDir, files);
	}

	// compile the source files

	String compiler = project.getProperty("build.compiler");
	if (compiler == null) {
		if (Project.getJavaVersion().startsWith("1.3")) {
			compiler = "modern";
		} else {
			compiler = "classic";
		}
	}

	if (compileList.length > 0) {

		CompilerAdapter adapter = EclipseCompilerAdapterFactory.getCompiler(compiler, this);
		log(Policy.bind("info.compiling"));

		// now we need to populate the compiler adapter
		adapter.setJavac(this);

		// finally, lets execute the compiler!!
		if (!adapter.execute()) {
			if (failOnError) {
				throw new BuildException(Policy.bind("error.compileFailed"), location);
			} else {
				log(Policy.bind("error.compileFailed"), Project.MSG_ERR);
			}
		}
	}
}
}
