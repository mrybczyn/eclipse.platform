/*******************************************************************************
 * Copyright (c) 2006, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ua.tests.help.toc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.help.IToc;
import org.eclipse.help.internal.dynamic.DocumentWriter;
import org.eclipse.help.internal.toc.Toc;
import org.eclipse.help.internal.toc.TocAssembler;
import org.eclipse.help.internal.toc.TocContribution;
import org.eclipse.help.internal.toc.TocFile;
import org.eclipse.help.internal.toc.TocFileParser;
import org.eclipse.ua.tests.plugin.UserAssistanceTestPlugin;
import org.eclipse.ua.tests.util.XMLUtil;

import junit.framework.TestCase;

public class TocAssemblerTest extends TestCase {

	public void testAssemble() throws Exception {
		TocFileParser parser = new TocFileParser();
		TocContribution b = parser.parse(new TocFile(UserAssistanceTestPlugin.getPluginId(), "data/help/toc/assembler/b.xml", true, "en", null, null));
		TocContribution c = parser.parse(new TocFile(UserAssistanceTestPlugin.getPluginId(), "data/help/toc/assembler/c.xml", true, "en", null, null));
		TocContribution result_b_c = parser.parse(new TocFile(UserAssistanceTestPlugin.getPluginId(), "data/help/toc/assembler/result_b_c.xml", true, "en", null, null));
		
		TocAssembler assembler = new TocAssembler();
		List<TocContribution> contributions = new ArrayList<TocContribution>(Arrays.asList(b, c));
		contributions = assembler.assemble(contributions);
		assertEquals(1, contributions.size());
		String expected = serialize(result_b_c);
		String actual = serialize(contributions.get(0));
		XMLUtil.assertXMLEquals("Assembled TOC did not match expected result", expected, actual);

		TocContribution a = parser.parse(new TocFile(UserAssistanceTestPlugin.getPluginId(), "data/help/toc/assembler/a.xml", true, "en", null, null));
		b = parser.parse(new TocFile(UserAssistanceTestPlugin.getPluginId(), "data/help/toc/assembler/b.xml", true, "en", null, null));
		c = parser.parse(new TocFile(UserAssistanceTestPlugin.getPluginId(), "data/help/toc/assembler/c.xml", true, "en", null, null));
		TocContribution d = parser.parse(new TocFile(UserAssistanceTestPlugin.getPluginId(), "data/help/toc/assembler/d.xml", false, "en", null, null));
		TocContribution result_a_b_c_d = parser.parse(new TocFile(UserAssistanceTestPlugin.getPluginId(), "data/help/toc/assembler/result_a_b_c_d.xml", true, "en", null, null));

		contributions = new ArrayList<TocContribution>(Arrays.asList(a, b, c, d));
		contributions = assembler.assemble(contributions);
		assertEquals(1, contributions.size());
		
		expected = serialize(result_a_b_c_d);
		actual = serialize(contributions.get(0));
		XMLUtil.assertXMLEquals("Assembled TOC did not match expected result", expected, actual);
	}
	
	public void testInvalidLinkTo() throws Exception {
		TocFileParser parser = new TocFileParser();
		TocContribution linkTo1 = parser.parse(new TocFile(UserAssistanceTestPlugin.getPluginId(), "data/help/toc/assembler/linkTo1.xml", true, "en", null, null));
		TocContribution linkTo2 = parser.parse(new TocFile(UserAssistanceTestPlugin.getPluginId(), "data/help/toc/assembler/linkTo2.xml", true, "en", null, null));
		TocContribution linkTo3 = parser.parse(new TocFile(UserAssistanceTestPlugin.getPluginId(), "data/help/toc/assembler/linkTo3.xml", true, "en", null, null));
		
		TocAssembler assembler = new TocAssembler();
		List<TocContribution> contributions = new ArrayList<TocContribution>(Arrays.asList(linkTo1, linkTo2, linkTo3));
		contributions = assembler.assemble(contributions);
		assertEquals(3, contributions.size());
	}
	
	public void testHrefMap() throws Exception {
		TocFileParser parser = new TocFileParser();
		TocContribution b = parser.parse(new TocFile(UserAssistanceTestPlugin.getPluginId(), "data/help/toc/assembler/b.xml", true, "en", null, null));
		TocContribution c = parser.parse(new TocFile(UserAssistanceTestPlugin.getPluginId(), "data/help/toc/assembler/c.xml", true, "en", null, null));
		TocAssembler assembler = new TocAssembler();
		List<TocContribution> contributions = new ArrayList<TocContribution>(Arrays.asList(b, c));
		contributions = assembler.assemble(contributions);
		IToc toc =contributions.get(0).getToc();
		assertNotNull(toc.getTopic("/org.eclipse.ua.tests/B_topic3.html"));
		assertNotNull(toc.getTopic("/org.eclipse.ua.tests/C_topic.html"));
		assertNull(toc.getTopic("/org.eclipse.ua.tests/D_topic.html"));
	}

	private String serialize(TocContribution contribution) throws Exception {
		DocumentWriter writer = new DocumentWriter();
		return new String(writer.writeString((Toc)contribution.getToc(), true));
	}
}
