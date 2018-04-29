/*******************************************************************************
 * Copyright (c) 2018 Remain Software
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     wim.jongman@remainsoftware.com - initial API and implementation
 *******************************************************************************/
package org.eclipse.tips.examples.tipsframework;

import java.util.Date;

import org.eclipse.tips.core.IHtmlTip;
import org.eclipse.tips.core.Tip;
import org.eclipse.tips.core.TipImage;
import org.eclipse.tips.examples.DateUtil;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

public class Navigate2Tip extends Tip implements IHtmlTip {

	private TipImage fImage;

	public Navigate2Tip(String providerId) {
		super(providerId);
	}

	@Override
	public Date getCreationDate() {
		return DateUtil.getDateFromYYMMDD("09/01/2018");
	}

	@Override
	public String getSubject() {
		return "Navigate Tip 2";
	}

	@Override
	public String getHTML() {
		return "<h2>Navigating Tips</h2>You can activate other Tip Providers by clicking on the big icons below."
				+ "<br>"
				+ "You are currently looking at the Tips tips but as you can see there are other providers. Go ahead and"
				+ " select some of the other providers. If you click on the lightbulb below you will return to this tip.<br><br>";
	}

	@Override
	public TipImage getImage() {
		if (fImage == null) {
			try {
				Bundle bundle = FrameworkUtil.getBundle(getClass());
				fImage = new TipImage(bundle.getEntry("images/tips/navigate2.png")).setAspectRatio(650, 220, true);
			} catch (Exception e) {
//				getProvider().getManager().log(LogUtil.info(getClass(), e));
			}
		}
		return fImage;
	}

}